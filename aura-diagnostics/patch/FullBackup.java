package com.auratv.diagnostics;

import android.content.ContentResolver;
import android.net.Uri;
import android.provider.DocumentsContract;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * AURA raw eMMC backup. Never writes to any TV block device.
 * Requires ADB shell UID 0 in RECOVERY, not a live Android system.
 * Copies physical eMMC user area and eMMC hardware boot areas 0/1.
 * RPMB is NOT accessible: this cannot be called a guaranteed factory restore.
 */
public final class FullBackup {
    public interface Progress { void update(String label, long finishedBytes, long totalBytes); }
    public static final long CHUNK = 64L * 1024L * 1024L;

    public static final class Disk {
        public final String name, source;
        public final long bytes;
        Disk(String n,long b) { name=n; source="/dev/block/"+n; bytes=b; }
    }
    public static final class Plan {
        public boolean allowed;
        public String explanation = "";
        public String device = "", mode = "", user = "", twrp = "";
        public Disk[] disks;
        public long total;
    }
    private FullBackup() {}
    private static String sh(AdbClient adb,String command) throws Exception {
        return adb.shell(command,2048).trim();
    }
    private static boolean equalsOneOf(String a,String... options){
        for(String o:options) if(o.equalsIgnoreCase(a))return true;
        return false;
    }
    private static long sectors(AdbClient adb,String name) throws Exception {
        String result=sh(adb,"cat /sys/class/block/"+name+"/size");
        if(!result.matches("[0-9]+"))throw new IOException("No readable eMMC geometry for "+name+": "+result);
        return Math.multiplyExact(Long.parseLong(result),512L);
    }
    public static Plan inspect(AdbClient adb) throws Exception {
        Plan plan = new Plan();
        plan.user=sh(adb,"id -u");
        plan.device=sh(adb,"getprop ro.product.device");
        plan.mode=sh(adb,"getprop ro.bootmode");
        plan.twrp=sh(adb,"getprop ro.twrp.version");
        String bootComplete=sh(adb,"getprop sys.boot_completed");
        if(!"jobs".equals(plan.device)) {
            plan.explanation="Обнаружена модель '"+plan.device+"', нужна платформа jobs. Бэкап блокирован."; return plan;
        }
        if(!"0".equals(plan.user)){
            plan.explanation="Нет прав на полный образ: ADB UID="+plan.user+" (требуется 0/root из Recovery). " +
                "Текущий Android не позволяет читать загрузочные разделы. Никаких файлов создано не будет."; return plan;
        }
        if(!(equalsOneOf(plan.mode,"recovery") || (!plan.twrp.isEmpty() && !"1".equals(bootComplete)))
                || "1".equals(bootComplete)) {
            plan.explanation="Образ нельзя копировать на работающем Android: нужна подтверждённая среда Recovery " +
                "и отсутствие boot_completed=1. Сейчас ro.bootmode="+plan.mode+"."; return plan;
        }
        String[] names={"mmcblk0","mmcblk0boot0","mmcblk0boot1"};
        Disk[] disks = new Disk[names.length];
        long total=0;
        for(int i=0;i<names.length;i++){
            String n=names[i];
            String ok=sh(adb,"test -r /dev/block/"+n+" && echo READABLE || echo DENIED");
            if(!"READABLE".equals(ok)){
                plan.explanation="Раздел /dev/block/"+n+" недоступен. Для полной копии eMMC требуется читать все три области.";
                return plan;
            }
            String first=sh(adb,"dd if=/dev/block/"+n+" bs=512 count=1 2>/dev/null | wc -c");
            if(!"512".equals(first.trim())){
                plan.explanation="Невозможно прочитать начальный сектор /dev/block/"+n+". Возвращено: "+first;
                return plan;
            }
            long size=sectors(adb,n);
            if(size<=0 || size>512L*1024L*1024L*1024L) {
                plan.explanation="Неверный размер "+n+": "+size; return plan;
            }
            disks[i]=new Disk(n,size);
            total=Math.addExact(total,size);
        }
        plan.disks=disks;
        plan.total=total;
        plan.allowed=true;
        plan.explanation="Доступен режим чтения сырых образов eMMC (пользовательская область + boot0 + boot1), "+
             "всего "+formatSize(total)+". RPMB, аппаратные ключи и гарантия восстановления НЕ входят.";
        return plan;
    }
    public static String formatSize(long bytes) {
        return String.format(Locale.US,"%.2f ГиБ",bytes/1073741824.0);
    }
    private static String sha(byte[] digest){
        StringBuilder s=new StringBuilder();
        for(byte b:digest)s.append(String.format(Locale.ROOT,"%02x",b&255));
        return s.toString();
    }
    private static String hashSaved(ContentResolver resolver,Uri uri) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream is=resolver.openInputStream(uri)){
            if(is==null)throw new IOException("Cannot reopen saved image chunk");
            DigestInputStream dis=new DigestInputStream(is,digest);
            byte[] b=new byte[65536];
            while(dis.read(b)!=-1) { }
        }
        return sha(digest.digest());
    }
    private static void saveText(ContentResolver resolver,Uri folder,String name,String value) throws Exception {
        Uri uri=DocumentsContract.createDocument(resolver,folder,"application/json",name);
        if(uri==null)throw new IOException("Cannot create "+name);
        try(OutputStream o=resolver.openOutputStream(uri,"w")){
            if(o==null)throw new IOException("Cannot write "+name);
            o.write(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            o.flush();
        }
    }

    public static JSONObject copy(ContentResolver resolver,Uri tree,AdbClient adb,Plan plan,Progress progress)
            throws Exception {
        if(!plan.allowed || plan.disks==null || plan.disks.length!=3)
            throw new SecurityException("AURA backup preflight not passed.");
        // Verify permissions AGAIN at the moment of the backup.
        Plan now=inspect(adb);
        if(!now.allowed || now.total!=plan.total)
            throw new SecurityException("Recovery permissions or eMMC geometry changed; backup aborted.");

        Uri root=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.ROOT).format(new Date());
        Uri dest=DocumentsContract.createDocument(resolver,root,DocumentsContract.Document.MIME_TYPE_DIR,
                "AURA_eMMC_"+stamp);
        if(dest==null)throw new IOException("Cannot create backup directory");
        JSONObject manifest=new JSONObject();
        manifest.put("format","AURA_RAW_EMMC_CHUNKS_V1");
        manifest.put("device",plan.device);
        manifest.put("source","recovery_ro");
        manifest.put("total_bytes",plan.total);
        manifest.put("rpmB_included",false);
        manifest.put("restore_tested",false);
        manifest.put("warning","NOT A COMPLETE CLONE OF RPMB OR HARDWARE KEYS. DO NOT FLASH WITHOUT RECOVERY TEST.");
        JSONArray images=new JSONArray();
        manifest.put("images",images);
        long base=0;
        for(Disk disk:plan.disks){
            JSONObject section=new JSONObject();
            section.put("block_device",disk.source);
            section.put("length_bytes",disk.bytes);
            JSONArray chunks=new JSONArray();
            section.put("chunks",chunks);
            images.put(section);
            long pos=0; int index=0;
            while(pos<disk.bytes){
                final long length=Math.min(CHUNK,disk.bytes-pos);
                final long already=base+pos;
                String name=String.format(Locale.ROOT,"%s.img.part%04d",disk.name,index++);
                Uri part=DocumentsContract.createDocument(resolver,dest,"application/octet-stream",name);
                if(part==null)throw new IOException("Cannot create "+name+"; backup incomplete.");
                MessageDigest digest=MessageDigest.getInstance("SHA-256");
                long offsetMb=pos/(1024*1024);
                long blocks=(length+1024*1024-1)/(1024*1024);
                String command="dd if="+disk.source+" bs=1048576 skip="+offsetMb+" count="+blocks+" 2>/dev/null";
                try(OutputStream raw=resolver.openOutputStream(part,"w")){
                    if(raw==null)throw new IOException("Storage provider refused "+name);
                    DigestOutputStream out=new DigestOutputStream(raw,digest);
                    adb.execToStream(command,out,length,bytes->
                        progress.update("Копируется "+name,already+bytes,plan.total));
                    out.flush();
                }
                String sum=sha(digest.digest());
                String saved=hashSaved(resolver,part);
                if(!sum.equals(saved)){
                    throw new IOException("Saved file SHA-256 mismatch: "+name+"; backup INCOMPLETE");
                }
                JSONObject c=new JSONObject();
                c.put("file",name);
                c.put("offset",pos);
                c.put("length",length);
                c.put("sha256",sum);
                chunks.put(c);
                pos+=length;
            }
            base+=disk.bytes;
        }
        // Written last, after exact byte counts and readback SHA-256 on ALL chunks.
        manifest.put("complete",true);
        saveText(resolver,dest,"MANIFEST_COMPLETE.json",manifest.toString(2));
        progress.update("Данные прочитаны и проверены. RPMB отсутствует.",plan.total,plan.total);
        return manifest;
    }
}
