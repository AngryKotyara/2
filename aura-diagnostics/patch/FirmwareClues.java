package com.auratv.diagnostics;

import org.json.JSONObject;
import org.json.JSONArray;
import java.util.Locale;

/**
 * v0.8 targeted stage: read-only fstab / OTA / package provenance.
 * Never executes commands from the TV. Runtime APK path is syntax-validated.
 */
public final class FirmwareClues {
    private FirmwareClues(){}
    private static String classify(String text, int limit) {
        if(text==null || text.trim().isEmpty())return "unknown";
        String t=text.trim();
        if(t.equalsIgnoreCase("unknown")||t.equalsIgnoreCase("null"))return "unknown";
        boolean useful=false, denied=false, missing=false;
        for(String line:text.split("\\r?\\n")){
            String s=line.trim().toLowerCase(Locale.ROOT);
            if(s.isEmpty())continue;
            boolean isErr=s.startsWith("cat: ")||s.startsWith("ls: ")||
                s.startsWith("sha256sum: ")||s.startsWith("sh: ")||
                s.startsWith("/system/bin/sh: ")||s.startsWith("grep: ");
            if(isErr){
                if(s.contains("permission denied")||s.contains("operation not permitted"))denied=true;
                else missing=true;
            }else useful=true;
        }
        if(useful&&(denied||missing))return "partial";
        if(denied)return "access_denied";
        if(missing)return "unavailable";
        if(t.length()>=limit-8)return "truncated";
        return "available";
    }
    private static JSONObject query(AdbClient adb,String command,int limit) throws Exception{
        JSONObject result=new JSONObject();
        result.put("query",command);
        try{
            String raw=adb.shell(command,limit);
            result.put("status",classify(raw,limit));
            result.put("value",DiagnosticRunner.redact(raw));
        }catch(Exception e){
            result.put("status","error");
            result.put("value",DiagnosticRunner.redact(e.getClass().getSimpleName()+": "+e.getMessage()));
        }
        return result;
    }
    private static void record(JSONObject all,AdbClient adb,String key,String cmd,int limit) throws Exception{
        all.put(key,query(adb,cmd,limit));
    }
    private static String get(JSONObject all,String key){
        JSONObject x=all.optJSONObject(key);
        if(x==null)return "";
        String st=x.optString("status","");
        return "available".equals(st)||"partial".equals(st)?x.optString("value",""):"";
    }
    public static JSONObject scan(AdbClient adb) throws Exception {
        JSONObject root=new JSONObject();
        JSONObject probes=new JSONObject();
        record(probes,adb,"vendor_fstab","cat /vendor/etc/fstab.amlogic",17000);
        record(probes,adb,"vendor_fstab_sha256","sha256sum /vendor/etc/fstab.amlogic",1800);
        record(probes,adb,"xiaomi_upgrade_apk","pm path com.xiaomi.mitv.upgrade",3000);
        record(probes,adb,"xiaomi_upgrade_version",
            "dumpsys package com.xiaomi.mitv.upgrade | grep -E 'versionCode=|versionName=|codePath=|firstInstallTime=|lastUpdateTime='",4500);
        record(probes,adb,"tvinput_apk","pm path com.droidlogic.tvinput",2000);
        record(probes,adb,"block_mountinfo",
            "cat /proc/self/mountinfo | grep -E '/dev/block|/vendor|/product|/odm|/system'",11500);
        record(probes,adb,"block_node_permissions","ls -l /dev/block",8500);
        record(probes,adb,"emmc_uevent","cat /sys/class/block/mmcblk0/uevent",2000);
        record(probes,adb,"ota_app_candidates",
            "ls /system/priv-app | grep -iE 'upgrade|ota|xiaomi'",3000);
        record(probes,adb,"android_security_patch","getprop ro.build.version.security_patch",1200);
        record(probes,adb,"vendor_product_model","getprop ro.product.vendor.model",1200);

        String paths=get(probes,"xiaomi_upgrade_apk");
        for(String line:paths.split("\\r?\\n")){
            if(!line.startsWith("package:"))continue;
            String path=line.substring(8).trim();
            if(path.matches("^/[A-Za-z0-9_+./-]+\\.apk$")){
                record(probes,adb,"xiaomi_upgrade_apk_sha256","sha256sum "+path,3000);
                break;
            }
        }
        int good=0,partial=0,denied=0,missing=0,unknown=0;
        JSONArray sha=new JSONArray();
        java.util.Iterator<String> keys=probes.keys();
        while(keys.hasNext()){
            String key=keys.next();
            JSONObject obj=probes.optJSONObject(key);
            String status=obj.optString("status");
            if(status.equals("available"))good++;
            else if(status.equals("partial"))partial++;
            else if(status.equals("access_denied"))denied++;
            else if(status.equals("unknown"))unknown++;
            else missing++;
            String value=obj.optString("value");
            if(key.contains("sha256")&&value.matches("(?s)^[a-fA-F0-9]{64}\\s+.*"))sha.put(key);
        }
        JSONObject summary=new JSONObject();
        summary.put("available",good);
        summary.put("partial",partial);
        summary.put("access_denied",denied);
        summary.put("missing_or_error",missing);
        summary.put("unknown",unknown);
        summary.put("verified_file_hashes",sha);
        summary.put("fstab_readable",get(probes,"vendor_fstab").contains("/dev/block"));
        summary.put("upgrade_apk_located",paths.contains("package:"));
        summary.put("flash_ready",false);
        summary.put("next_step","Сопоставить fstab и SHA-256 с оригинальным OTA. Для чтения eMMC нужен привилегированный Recovery.");
        root.put("checks",probes);
        root.put("summary",summary);
        root.put("warning","Это сведения об установленной системе, не бэкап загрузчика и не разрешение прошивать.");
        return root;
    }
}
