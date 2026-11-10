from pathlib import Path
base=Path("aura-diagnostics/project")
java=base/"app/src/main/java/com/auratv/diagnostics"
src=java/"FirmwareExporter.java"
s=src.read_text(encoding="utf-8")
old='''        new Target("DroidLogicTvInput_arm64.vdex", "/product/app/DroidLogicTvInput/oat/arm64/DroidLogicTvInput.vdex")
    };'''
new='''        new Target("DroidLogicTvInput_arm64.vdex", "/product/app/DroidLogicTvInput/oat/arm64/DroidLogicTvInput.vdex"),
        // Original OTA code reads appVersion from this properties file.
        new Target("vendor_build_info", "/vendor/app/build_info"),
        new Target("system_vendor_build_info", "/system/vendor/app/build_info")
    };'''
assert s.count(old)==1
s=s.replace(old,new,1)

old='''        report.put("version","1.2.0");'''
assert s.count(old)==1
s=s.replace(old,'''        report.put("version","1.3.0");''',1)

needle='''        report.put("files",files);
        org.json.JSONObject locations=new org.json.JSONObject();'''
replacement='''        report.put("files",files);
        // No network request; no device ID, serial, tokens, MAC or other unique data.
        // Read current build state and the exact full OTA decision's read-only operands.
        org.json.JSONObject localOtaState=new org.json.JSONObject();
        for(String[] probe:new String[][]{
            {"current_incremental","getprop ro.build.version.incremental"},
            {"current_android_version","getprop ro.build.version.release"},
            {"build_type","getprop ro.build.type"},
            {"product_name","getprop ro.product.name"},
            {"build_id","getprop ro.build.id"},
            {"ota_last_version","settings get global upgrade.last.version"}
        }){
            try {
                String response=adb.shell(probe[1],1200).trim();
                localOtaState.put(probe[0],DiagnosticRunner.redact(response));
            } catch(Exception e){
                localOtaState.put(probe[0],"Unavailable: "+e.getClass().getSimpleName());
            }
        }
        String latest=localOtaState.optString("ota_last_version");
        String current=localOtaState.optString("current_incremental");
        if(latest.matches("[0-9]{1,9}") && current.matches("[0-9]{1,9}")) {
            long lastVersion=Long.parseLong(latest),currentVersion=Long.parseLong(current);
            localOtaState.put("need_full_package_inferred", lastVersion > currentVersion);
            localOtaState.put("decision_basis","Recovered Util.needUpgradeFullPackage: lastVersion > currentVersion");
        } else {
            localOtaState.put("need_full_package_inferred","unknown");
            localOtaState.put("decision_basis","One or both input values could not be obtained; not proof of false.");
        }
        localOtaState.put("selected_ota_host","unknown: requires ProductSubCode and OperatorID");
        report.put("local_ota_state",localOtaState);
        org.json.JSONObject locations=new org.json.JSONObject();'''
assert s.count(needle)==1
s=s.replace(needle,replacement,1)
src.write_text(s,encoding="utf-8")
act=java/"MainActivity.java"
a=act.read_text(encoding="utf-8")
a=a.replace('''"ЭКСПОРТ APK + ODEX/VDEX · на телефон"''',
            '''"OTA-ПАСПОРТ + APK/ODEX · на телефон"''')
act.write_text(a,encoding="utf-8")
gfile=base/"app/build.gradle";g=gfile.read_text(encoding="utf-8")
assert "versionCode 13" in g and "versionName '1.2.0'" in g
gfile.write_text(g.replace("versionCode 13","versionCode 14").replace("versionName '1.2.0'","versionName '1.3.0'"),encoding="utf-8")
assert "settings get global upgrade.last.version" in src.read_text()
assert "getprop ro.product.name" in src.read_text()
assert "getprop ro.serialno" not in src.read_text()
print("AURA OTA local evidence v1.3 integrated, read-only only.")
