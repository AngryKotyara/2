package com.auratv.diagnostics;

import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * AURA Firmware Investigator 0.7: five read-only evidence gathering stages.
 * No reboot, root, partition reads, flashing, device changes or remote code execution.
 * Never treats missing boot properties as proof of an unlocked bootloader.
 */
public final class FirmwareInvestigator {
    public interface Progress { void update(int done, int total, String stage); }
    private static final class Probe {
        final String key, source, command;
        final int limit;
        Probe(String k,String s,String c,int l){key=k;source=s;command=c;limit=l;}
    }
    private static Probe p(String k,String c){return new Probe(k,"adb-shell",c,4096);}
    private static Probe p(String k,String c,int limit){return new Probe(k,"adb-shell",c,limit);}
    private static final class Stage {
        final String id,name;
        final Probe[] probes;
        Stage(String id,String name,Probe... p){this.id=id;this.name=name;this.probes=p;}
    }
    private static final Stage[] STAGES={
      new Stage("identity","Идентификация аппаратной платформы",
        p("model","getprop ro.product.model"),
        p("device","getprop ro.product.device"),
        p("board","getprop ro.product.board"),
        p("hardware","getprop ro.hardware"),
        p("board_platform","getprop ro.board.platform"),
        p("build_fingerprint","getprop ro.build.fingerprint"),
        p("vendor_fingerprint","getprop ro.vendor.build.fingerprint"),
        p("android_version","getprop ro.build.version.release"),
        p("build_number","getprop ro.build.display.id"),
        p("kernel","uname -a")),
      new Stage("emmc","Геометрия и схема разделов eMMC",
        p("mmc_user_sectors","cat /sys/class/block/mmcblk0/size"),
        p("mmc_boot0_sectors","cat /sys/class/block/mmcblk0boot0/size"),
        p("mmc_boot1_sectors","cat /sys/class/block/mmcblk0boot1/size"),
        p("emmc_model","cat /sys/class/block/mmcblk0/device/name"),
        p("block_nodes","ls /sys/class/block",7000),
        p("partition_names","ls -l /dev/block/platform/ffe07000.emmc/by-name",12000),
        p("mounts","cat /proc/mounts | grep -E ' /(system|vendor|product|odm|data|cache) |/dev/block' ",14000),
        p("fstab_names","ls /vendor/etc/fstab* /odm/etc/fstab*",5000)),
      new Stage("trust","Загрузчик и цепочка доверия",
        p("adb_uid","id -u"),
        p("selinux","getenforce"),
        p("verified_boot","getprop ro.boot.verifiedbootstate"),
        p("flash_locked","getprop ro.boot.flash.locked"),
        p("vbmeta_device_state","getprop ro.boot.vbmeta.device_state"),
        p("verity_mode","getprop ro.boot.veritymode"),
        p("slot_suffix","getprop ro.boot.slot_suffix"),
        p("ab_update","getprop ro.build.ab_update"),
        p("bootloader_version","getprop ro.bootloader"),
        p("bootmode","getprop ro.bootmode")),
      new Stage("file_fingerprints","SHA-256 доступных системных файлов",
        p("system_build_prop","sha256sum /system/build.prop"),
        p("vendor_build_prop","sha256sum /vendor/build.prop"),
        p("vendor_vintf","sha256sum /vendor/etc/vintf/manifest.xml"),
        p("gralloc","sha256sum /vendor/lib/hw/gralloc.amlogic.so"),
        p("hwcomposer","sha256sum /vendor/lib/hw/hwcomposer.amlogic.so"),
        p("tv_input","sha256sum /vendor/lib/hw/tv_input.amlogic.so"),
        p("hdmi_cec","sha256sum /vendor/lib/hw/hdmi_cec.amlogic.so"),
        p("audio_primary","sha256sum /vendor/lib/hw/audio.primary.xiaomi.so")),
      new Stage("ota","Идентификаторы обновлений и Recovery",
        p("incremental","getprop ro.build.version.incremental"),
        p("vendor_patch","getprop ro.vendor.build.security_patch"),
        p("update_packages","pm list packages | grep -iE 'ota|updat|upgrad|recover'",9000),
        p("recovery_dir","ls -la /cache/recovery",3500),
        p("ota_dir","ls -la /data/ota_package",3500),
        p("recovery_fstab","ls -l /vendor/etc/recovery.fstab /etc/recovery.fstab",3500))
    };
    private FirmwareInvestigator() { }
    public static int stages(){return STAGES.length;}
    private static String status(String v,int limit){
        if(v==null || v.trim().isEmpty())return "unknown";
        String t=v.trim().toLowerCase(Locale.ROOT);
        if(t.startsWith("cat: ")||t.startsWith("ls: ")||t.startsWith("sha256sum: ")
           ||t.startsWith("sh: ")||t.startsWith("/system/bin/sh: ")
           ||t.startsWith("grep: ")) {
            if(t.contains("permission denied")||t.contains("operation not permitted"))
                return "access_denied";
            if(t.contains("no such file")||t.contains("not found")||t.contains("inaccessible"))
                return "unavailable";
        }
        if(v.length()>=limit-8)return "truncated";
        return "available";
    }
    private static JSONObject exec(AdbClient adb,Probe p) throws Exception {
        JSONObject value=new JSONObject();
        value.put("source",p.source);
        value.put("query",p.command);
        try {
            String raw=adb.shell(p.command,p.limit);
            value.put("status",status(raw,p.limit));
            value.put("value",DiagnosticRunner.redact(raw));
        } catch(Exception e) {
            value.put("status","error");
            value.put("value",DiagnosticRunner.redact(
                    e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage())));
        }
        return value;
    }
    private static String str(JSONObject all,String stage,String key){
        JSONObject step=all.optJSONObject(stage);
        JSONObject x=step==null?null:step.optJSONObject(key);
        return x!=null && "available".equals(x.optString("status"))?x.optString("value",""):"";
    }
    private static JSONObject fact(String status,String value,String basis,String limitation) throws Exception {
        JSONObject f=new JSONObject();
        f.put("status",status);
        f.put("value",value);
        f.put("evidence",basis);
        f.put("limitation",limitation);
        return f;
    }
    private static void addNext(JSONArray next,String priority,String description,String reason) throws Exception {
        JSONObject n=new JSONObject();n.put("priority",priority);n.put("action",description);n.put("reason",reason);next.put(n);
    }
    public static JSONObject scan(AdbClient adb,Progress progress) throws Exception {
        JSONObject root=new JSONObject();
        root.put("project","AURA TV OS");
        root.put("tool_version","0.7.0");
        root.put("kind","read_only_evidence_puzzle");
        root.put("created",new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ",Locale.US).format(new Date()));
        root.put("device_changes",false);
        root.put("flash_ready",false);
        root.put("warning","Отпечатки и OTA не заменяют реальный backup. Отсутствие ro.boot.* НЕ подтверждает разблокировку. Сырые разделы и RPMB не копируются.");
        JSONObject observations=new JSONObject();
        JSONObject phases=new JSONObject();
        int available=0,denied=0,unknown=0,unavailable=0,error=0,truncated=0;
        for(int i=0;i<STAGES.length;i++){
            Stage stage=STAGES[i];
            progress.update(i,STAGES.length,stage.name);
            JSONObject values=new JSONObject();
            int successes=0;
            for(Probe probe:stage.probes){
                JSONObject item=exec(adb,probe);
                String st=item.optString("status");
                if("available".equals(st)){available++;successes++;}
                else if("access_denied".equals(st))denied++;
                else if("unknown".equals(st))unknown++;
                else if("unavailable".equals(st))unavailable++;
                else if("truncated".equals(st))truncated++;
                else error++;
                values.put(probe.key,item);
            }
            observations.put(stage.id,values);
            JSONObject step=new JSONObject();
            step.put("label",stage.name);
            step.put("available",successes);
            step.put("total",stage.probes.length);
            phases.put(stage.id,step);
            progress.update(i+1,STAGES.length,stage.name+" — завершено");
        }
        // Adapt only with additional READ-ONLY queries if the initial observations lack evidence.
        JSONObject adaptive=new JSONObject();
        if(str(observations,"trust","verified_boot").isEmpty()) {
            adaptive.put("vbmeta_digest",exec(adb,p("vbmeta_digest","getprop ro.boot.vbmeta.digest")));
            adaptive.put("build_tags",exec(adb,p("build_tags","getprop ro.build.tags")));
        }
        if(str(observations,"emmc","fstab_names").isEmpty()) {
            adaptive.put("alternate_fstab",exec(adb,p("alternate_fstab","ls /fstab* /system/etc/fstab* /vendor/etc/*fstab*",6000)));
        }
        if(str(observations,"ota","update_packages").isEmpty()) {
            adaptive.put("package_candidates",exec(adb,p("package_candidates","pm list packages | grep -iE 'mitv|xiaomi|ota'",7000)));
        }
        root.put("phases",phases);
        root.put("observations",observations);
        root.put("adaptive",adaptive);
        JSONObject facts=new JSONObject();
        String model=str(observations,"identity","model");
        String device=str(observations,"identity","device");
        String hardware=str(observations,"identity","hardware");
        String fp=str(observations,"identity","build_fingerprint");
        boolean known="jobs".equals(device) && "amlogic".equalsIgnoreCase(hardware);
        facts.put("platform",fact(known?"consistent":"hypothesis",
              model+" / "+device+" / "+hardware,
              "ro.product.model, ro.product.device, ro.hardware",
              "Программные свойства не подтверждают физическую ревизию платы."));
        String geometry=str(observations,"emmc","mmc_user_sectors");
        if(geometry.matches("[0-9]{1,16}")) {
            try {
                long bytes=Math.multiplyExact(Long.parseLong(geometry),512L);
                facts.put("emmc_user_area",fact("confirmed",bytes+" bytes",
                      "/sys/class/block/mmcblk0/size (units: 512-byte sectors)",
                      "Данные по ёмкости не дают права читать/записывать разделы."));
            } catch(Exception ignored) {
                facts.put("emmc_user_area",fact("hypothesis",geometry,"sysfs size","Невозможно проверить числовой размер."));
            }
        } else {
            facts.put("emmc_user_area",fact("blocked","unknown","/sys/class/block/mmcblk0/size",
              "Размер не получен; нужен другой источник."));
        }
        String partitions=str(observations,"emmc","partition_names");
        facts.put("partition_map",fact(partitions.contains("bootloader")&&partitions.contains("vbmeta")?
              "consistent":"hypothesis",partitions.contains("bootloader")?"Имена разделов обнаружены":"Не подтверждены",
              "/dev/block/platform/ffe07000.emmc/by-name",
              "Символьные ссылки не подтверждают размеры, полный дамп или совместимость заводского образа."));
        String lock=str(observations,"trust","flash_locked");
        facts.put("bootloader_lock",fact("unknown",lock.isEmpty()?"Неизвестно":"Android сообщает: "+lock,
              "ro.boot.flash.locked","Для подтверждения необходим загрузчик/доверенный сервисный источник. Даже непустой getprop не даёт разрешения на прошивку."));
        String verified=str(observations,"trust","verified_boot");
        facts.put("verified_boot",fact("unknown",
              verified.isEmpty()?"Неизвестно":"Android сообщает: "+verified,
              "ro.boot.verifiedbootstate","Строка свойства не является криптографическим подтверждением цепочки доверия."));
        int sha=0;
        JSONObject hashes=observations.optJSONObject("file_fingerprints");
        JSONArray hashedFiles=new JSONArray();
        java.util.Iterator<String> keys=hashes.keys();
        while(keys.hasNext()){
            String k=keys.next();
            JSONObject v=hashes.optJSONObject(k);
            if(v!=null && "available".equals(v.optString("status")) &&
                v.optString("value","").matches("(?is)^[a-f0-9]{64}\\s+.*")) {
                sha++; hashedFiles.put(k);
            }
        }
        facts.put("file_hashes",fact(sha>0?"confirmed":"blocked",
                Integer.toString(sha)+" confirmed SHA-256 files",
                "sha256sum of explicitly selected read-only OS paths",
                "Хеш идентифицирует байты файла, но не раскрывает закрытый файл или содержимое загрузчика."));
        String uid=str(observations,"trust","adb_uid");
        facts.put("raw_backup_rights",fact("2000".equals(uid)?"confirmed":"unknown",
              "0".equals(uid)?"uid=0 (не подтверждает наличие Recovery)":("uid="+(uid.isEmpty()?"unknown":uid)),
              "id -u","Полный побайтовый backup потребует привилегированного Recovery и отдельной проверки способности читать eMMC."));
        JSONObject summary=new JSONObject();
        summary.put("evidence_available",available);
        summary.put("access_denied",denied);
        summary.put("unknown",unknown);
        summary.put("unavailable",unavailable);
        summary.put("error",error);
        summary.put("truncated",truncated);
        summary.put("verified_file_hashes",sha);
        summary.put("hashed_files",hashedFiles);
        summary.put("device_identity_match",known);
        summary.put("flash_ready",false);
        JSONArray next=new JSONArray();
        if(!known) addNext(next,"high","Сверить шильдик устройства и ревизию платы",
                              "Программные идентификаторы не дают устойчивого совпадения.");
        if(!fp.isEmpty())addNext(next,"high","Найти точный оригинальный OTA по fingerprint и build ID",
                              "Сравнить содержимое OTA с доступными SHA-256; ничего не прошивать.");
        if(sha==0)addNext(next,"medium","Исследовать доступность sha256sum и файлов vendor",
                              "Нет ни одного проверенного файлового отпечатка.");
        if(lock.isEmpty())addNext(next,"high","Определить защиту загрузчика через совместимый заводской образ и доверенный сервисный режим",
                               "ADB не выдаёт состояние блокировки; нельзя считать его unlocked.");
        if(!geometry.matches("[0-9]{1,16}"))addNext(next,"medium","Уточнить размер eMMC из sysfs или аппаратного паспорта",
                                "Стандартная геометрия недоступна.");
        if(!"0".equals(uid))addNext(next,"high","Восстановить проверенный способ временной загрузки Recovery для полного бэкапа",
                               "Нынешний ADB-shell не имеет права чтения сырых разделов.");
        addNext(next,"critical","До любой записи обеспечить заводской образ, полную проверенную копию и восстановление",
                               "Отпечатки и сопоставления не позволяют восстановить уникальные данные или ключи.");
        root.put("facts",facts);
        root.put("summary",summary);
        root.put("next_read_only_steps",next);
        return root;
    }
}
