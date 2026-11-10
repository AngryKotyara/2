package com.auratv.diagnostics;

import android.content.ContentResolver;
import android.net.Uri;
import android.provider.DocumentsContract;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.DigestOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Read-only, phone-side ZIP export of three identified Xiaomi TV firmware files.
 * No TV writes, no privilege changes, no arbitrary paths or commands.
 */
public final class FirmwareExporter {
    public interface Progress { void update(String stage, long processed, long total); }
    private static final long MAX_FILE = 256L * 1024L * 1024L;
    private static final long TOTAL_CAP = 512L * 1024L * 1024L;

    private static final class Target {
        final String filename, path;
        Target(String n, String p) {filename=n;path=p;}
    }
    private static final Target[] FILES = {
        new Target("MiTVUpgrade.apk", "/vendor/app/MiTVUpgrade/MiTVUpgrade.apk"),
        new Target("fstab.amlogic", "/vendor/etc/fstab.amlogic"),
        new Target("DroidLogicTvInput.apk", "/product/app/DroidLogicTvInput/DroidLogicTvInput.apk")
    };
    private static final class Item {
        final Target source;
        String expectedHash;
        long size;
        JSONObject manifest;
        Item(Target t){source=t;}
    }
    private FirmwareExporter() {}

    private static String hex(byte[] in) {
        StringBuilder sb=new StringBuilder();
        for(byte b:in)sb.append(String.format(Locale.US,"%02x",b&255));
        return sb.toString();
    }
    private static String remote(AdbClient adb,String command) throws Exception {
        return adb.shell(command,1800).trim();
    }
    private static String getHash(AdbClient adb,String path) throws Exception {
        String value=remote(adb,"sha256sum "+path);
        String[] lines=value.split("\\r?\\n");
        if(lines.length!=1)return null;
        String s=lines[0].trim();
        if(s.length()<65)return null;
        String h=s.substring(0,64);
        if(!h.matches("[0-9a-fA-F]{64}"))return null;
        if(!s.substring(64).trim().equals(path))return null;
        return h.toLowerCase(Locale.ROOT);
    }
    private static Long size(AdbClient adb,String path) throws Exception {
        String s=remote(adb,"stat -c %s "+path);
        if(!s.matches("[0-9]+")) {
            // Android may not expose stat -c; fallback is a read-only stream count.
            s=remote(adb,"wc -c < "+path);
        }
        if(!s.matches("[0-9]+"))return null;
        try {return Long.parseLong(s);}catch(Exception e){return null;}
    }
    private static JSONObject reportEntry(Target t,String state,String detail) throws Exception{
        JSONObject o=new JSONObject();
        o.put("name",t.filename);
        o.put("remote_path",t.path);
        o.put("status",state);
        o.put("detail",detail);
        return o;
    }
    private static void zipJson(ZipOutputStream zip,String name,JSONObject data) throws Exception{
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        zip.closeEntry();
    }
    private static void verifyLocal(ContentResolver resolver,Uri uri,JSONObject report) throws Exception {
        JSONObject found=new JSONObject();
        try(InputStream input=resolver.openInputStream(uri)){
            if(input==null)throw new IOException("Не удаётся снова открыть экспортированный ZIP.");
            try(ZipInputStream z=new ZipInputStream(input)){
                ZipEntry entry;
                byte[] buf=new byte[65536];
                while((entry=z.getNextEntry())!=null){
                    if(entry.isDirectory())continue;
                    if(found.has(entry.getName()))throw new IOException("Повторная запись ZIP: "+entry.getName());
                    MessageDigest md=MessageDigest.getInstance("SHA-256");
                    long bytes=0; int n;
                    while((n=z.read(buf))!=-1){md.update(buf,0,n);bytes+=n;}
                    JSONObject v=new JSONObject();
                    v.put("bytes",bytes);
                    v.put("sha256",hex(md.digest()));
                    found.put(entry.getName(),v);
                    z.closeEntry();
                }
            }
        }
        JSONArray files=report.getJSONArray("files");
        for(int i=0;i<files.length();i++){
            JSONObject f=files.getJSONObject(i);
            if(!"saved".equals(f.optString("status")))continue;
            JSONObject v=found.optJSONObject(f.getString("name"));
            if(v==null || v.optLong("bytes",-1)!=f.getLong("size")
                || !f.getString("sha256").equals(v.optString("sha256"))) {
                throw new IOException("Ошибка проверки сохранённого ZIP: "+f.getString("name"));
            }
        }
        if(found.optJSONObject("AURA_EXPORT_MANIFEST.json")==null)
            throw new IOException("Отсутствует итоговый файл манифеста в ZIP.");
    }
    public static JSONObject export(ContentResolver resolver,Uri destination,
                                    AdbClient adb,Progress progress) throws Exception {
        JSONObject report=new JSONObject();
        report.put("tool","AURA Firmware Exporter");
        report.put("version","0.9.0");
        report.put("source","Xiaomi TV ADB shell (read-only)");
        report.put("device",remote(adb,"getprop ro.product.device"));
        report.put("build",remote(adb,"getprop ro.build.display.id"));
        report.put("disclaimer","This is not an eMMC backup. Device-unique partitions, RPMB, keys and bootloader are NOT included.");
        JSONArray files=new JSONArray();
        report.put("files",files);
        List<Item> toCopy=new ArrayList<>();
        long total=0;
        progress.update("Проверяем доступ к заводским файлам",0,1);
        for(Target t:FILES){
            Item it=new Item(t);
            try {
                Long length=size(adb,t.path);
                String hash=getHash(adb,t.path);
                if(length==null || length<1 || length>MAX_FILE || hash==null){
                    files.put(reportEntry(t,"skipped",
                        "Нет прав чтения, размера или удалённой SHA-256. Источник не скопирован."));
                    continue;
                }
                it.size=length;
                it.expectedHash=hash;
                it.manifest=reportEntry(t,"pending","Файл доступен для чтения.");
                it.manifest.put("size",length);
                it.manifest.put("remote_sha256",hash);
                files.put(it.manifest);
                toCopy.add(it);
                total=Math.addExact(total,length);
            } catch(Exception e){
                files.put(reportEntry(t,"skipped",
                    "Не удалось проверить источник: "+e.getClass().getSimpleName()));
            }
        }
        if(toCopy.isEmpty())throw new IOException(
            "Файлы недоступны для чтения через ADB-shell. ZIP не создавался.");
        if(total>TOTAL_CAP)throw new IOException("Суммарный объём превышает предел экспорта 512 МиБ.");

        boolean success=false;
        try {
            try(OutputStream raw=resolver.openOutputStream(destination,"w")){
                if(raw==null)throw new IOException("Не удалось открыть ZIP для записи.");
                try(ZipOutputStream zip=new ZipOutputStream(raw)){
                    zip.setLevel(java.util.zip.Deflater.BEST_SPEED);
                    long done=0;
                    for(Item it:toCopy){
                        final long previous=done;
                        final long expected=it.size;
                        final String filename=it.source.filename;
                        progress.update("Копируем "+filename,done,total);
                        MessageDigest digest=MessageDigest.getInstance("SHA-256");
                        zip.putNextEntry(new ZipEntry(filename));
                        // DigestOutputStream does not close ZipOutputStream.
                        DigestOutputStream sink=new DigestOutputStream(zip,digest);
                        adb.execToStream("cat "+it.source.path,sink,expected,
                            bytes->progress.update("Копируем "+filename,previous+bytes,
                                                   Math.max(1,previous+expected)));
                        sink.flush();
                        zip.closeEntry();
                        String actual=hex(digest.digest());
                        if(!actual.equals(it.expectedHash))
                            throw new IOException("SHA-256 не совпала при чтении: "+filename);
                        it.manifest.put("sha256",actual);
                        it.manifest.put("status","saved");
                        it.manifest.put("detail","Побайтовая копия совпала с SHA-256 на телевизоре.");
                        done+=expected;
                    }
                    report.put("transferred_bytes",done);
                    report.put("complete_for_readable_files",true);
                    report.put("full_emmc_backup",false);
                    zipJson(zip,"AURA_EXPORT_MANIFEST.json",report);
                }
            }
            progress.update("Перепроверяем ZIP на телефоне",total,total);
            verifyLocal(resolver,destination,report);
            success=true;
            progress.update("Файлы сохранены, контрольные суммы совпали",total,total);
            return report;
        } finally {
            if(!success) {
                try {DocumentsContract.deleteDocument(resolver,destination);}
                catch(Exception ignored) {}
            }
        }
    }
}
