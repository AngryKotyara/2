from pathlib import Path
base=Path("aura-diagnostics/project")
java=base/"app/src/main/java/com/auratv/diagnostics"
(java/"FirmwareExporter.java").write_text(
    Path("aura-diagnostics/patch/FirmwareExporter.java").read_text(encoding="utf-8"),
    encoding="utf-8")
p=java/"MainActivity.java"
s=p.read_text(encoding="utf-8")
def replace(old,new):
    global s
    assert s.count(old)==1, f"Unexpected v0.9 UI anchor: {old[:120]}"
    s=s.replace(old,new,1)
replace("private Button scanButton, saveButton, shareButton, backupButton, investigatorButton;",
        "private Button scanButton, saveButton, shareButton, backupButton, investigatorButton, exportButton;")
replace("investigatorButton.setOnClickListener(v -> runInvestigator());",
'''investigatorButton.setOnClickListener(v -> runInvestigator());
        connectCard.addView(space(12));
        exportButton=actionButton("ЭКСПОРТ APK И FSTAB · на телефон", CARD, WHITE);
        connectCard.addView(exportButton);
        exportButton.setOnClickListener(v -> chooseFirmwareExport());''')
replace('    private void runInvestigator() {',
        Path("aura-diagnostics/patch/export_ui.txt").read_text(encoding="utf-8")
        +"\n    private void runInvestigator() {")
replace("        if (request == FULL_BACKUP_DIR_REQUEST && result == Activity.RESULT_OK",
'''        if (request == FIRMWARE_ZIP_REQUEST && result == Activity.RESULT_OK
                && data != null && data.getData() != null) {
            startFirmwareExport(data.getData());
            return;
        }
        if (request == FULL_BACKUP_DIR_REQUEST && result == Activity.RESULT_OK''')
p.write_text(s,encoding="utf-8")
gradle=base/"app/build.gradle"
g=gradle.read_text(encoding="utf-8")
assert "versionCode 9" in g and "versionName '0.8.0'" in g
g=g.replace("versionCode 9","versionCode 10").replace("versionName '0.8.0'","versionName '0.9.0'")
gradle.write_text(g,encoding="utf-8")
src=(java/"FirmwareExporter.java").read_text(encoding="utf-8")
for bad in ["adb reboot","fastboot ","dd if=","su -c","setprop ","remount","flashing unlock","pm install"]:
    assert bad not in src, "Unsafe firmware exporter operation "+bad
print("AURA v0.9: on-phone ZIP file export integrated successfully")
