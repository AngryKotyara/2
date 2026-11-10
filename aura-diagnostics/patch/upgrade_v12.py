from pathlib import Path
base=Path("aura-diagnostics/project")
src=base/"app/src/main/java/com/auratv/diagnostics"
p=src/"FirmwareExporter.java"
s=p.read_text(encoding="utf-8")
old='''        new Target("DroidLogicTvInput.apk", "/product/app/DroidLogicTvInput/DroidLogicTvInput.apk")
    };'''
new='''        new Target("DroidLogicTvInput.apk", "/product/app/DroidLogicTvInput/DroidLogicTvInput.apk"),
        // Pre-optimized Java bytecode is stored separately on some vendor ROMs.
        // Missing/unreadable candidates will be recorded as skipped.
        new Target("MiTVUpgrade_arm.odex", "/vendor/app/MiTVUpgrade/oat/arm/MiTVUpgrade.odex"),
        new Target("MiTVUpgrade_arm.vdex", "/vendor/app/MiTVUpgrade/oat/arm/MiTVUpgrade.vdex"),
        new Target("MiTVUpgrade_arm64.odex", "/vendor/app/MiTVUpgrade/oat/arm64/MiTVUpgrade.odex"),
        new Target("MiTVUpgrade_arm64.vdex", "/vendor/app/MiTVUpgrade/oat/arm64/MiTVUpgrade.vdex"),
        new Target("DroidLogicTvInput_arm.odex", "/product/app/DroidLogicTvInput/oat/arm/DroidLogicTvInput.odex"),
        new Target("DroidLogicTvInput_arm.vdex", "/product/app/DroidLogicTvInput/oat/arm/DroidLogicTvInput.vdex"),
        new Target("DroidLogicTvInput_arm64.odex", "/product/app/DroidLogicTvInput/oat/arm64/DroidLogicTvInput.odex"),
        new Target("DroidLogicTvInput_arm64.vdex", "/product/app/DroidLogicTvInput/oat/arm64/DroidLogicTvInput.vdex")
    };'''
assert s.count(old)==1
s=s.replace(old,new)
assert s.count('report.put("version","0.9.0");')==1
s=s.replace('report.put("version","0.9.0");','report.put("version","1.2.0");')
needle='''        report.put("files",files);
        List<Item> toCopy=new ArrayList<>();'''
assert s.count(needle)==1
s=s.replace(needle,'''        report.put("files",files);
        org.json.JSONObject locations=new org.json.JSONObject();
        for(String folder:new String[]{
            "/vendor/app/MiTVUpgrade",
            "/vendor/app/MiTVUpgrade/oat",
            "/vendor/app/MiTVUpgrade/oat/arm",
            "/vendor/app/MiTVUpgrade/oat/arm64",
            "/product/app/DroidLogicTvInput",
            "/product/app/DroidLogicTvInput/oat",
            "/product/app/DroidLogicTvInput/oat/arm",
            "/product/app/DroidLogicTvInput/oat/arm64"
        }){
            try {
                String response=adb.shell("ls -la "+folder,4000);
                locations.put(folder,DiagnosticRunner.redact(response));
            } catch(Exception e) {
                locations.put(folder,"Unavailable: "+e.getClass().getSimpleName());
            }
        }
        report.put("optimized_code_locations",locations);
        List<Item> toCopy=new ArrayList<>();''')
p.write_text(s,encoding="utf-8")
activity=src/"MainActivity.java"
s=activity.read_text(encoding="utf-8")
s=s.replace(
'''exportButton=actionButton("ЭКСПОРТ APK И FSTAB · на телефон", CARD, WHITE);''',
'''exportButton=actionButton("ЭКСПОРТ APK + ODEX/VDEX · на телефон", CARD, WHITE);''')
s=s.replace(
'''Создадим ZIP из доступных оригинальных файлов Xiaomi: MiTVUpgrade.apk, ''',
'''Создадим ZIP из оригинальных MiTVUpgrade.apk, ''')
s=s.replace(
'''+"fstab.amlogic и DroidLogicTvInput.apk.\\n\\n"''',
'''+"fstab.amlogic, DroidLogicTvInput.apk и доступных ODEX/VDEX.\\n\\n"''')
s=s.replace('''"AURA_TV_Original_Firmware_Files.zip"''',
''' "AURA_TV_Optimized_Code_v1_2.zip" ''')
# Reusable share provider's original filename is kept for compatibility.
activity.write_text(s,encoding="utf-8")

# Keep SHA-256 validation mandatory; never silently accept missing/unhashed file.
assert 'getHash(adb,t.path)' in s or 'getHash(adb,t.path)' in p.read_text()
assert 'adb.execToStream(' in p.read_text()

gradle=base/"app/build.gradle"
g=gradle.read_text(encoding="utf-8")
assert "versionCode 12" in g and "versionName '1.1.0'" in g
gradle.write_text(g.replace("versionCode 12","versionCode 13").replace(
    "versionName '1.1.0'","versionName '1.2.0'"),encoding="utf-8")
print("AURA 1.2: optional ODEX/VDEX and directory discovery, never writes TV.")
