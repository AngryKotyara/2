from pathlib import Path
import xml.etree.ElementTree as ET

base=Path("aura-diagnostics/project")
app=base/"app"
java=app/"src/main/java/com/auratv/diagnostics"
(java/"ExportShareProvider.java").write_text(
    Path("aura-diagnostics/patch/ExportShareProvider.java").read_text(encoding="utf-8"),
    encoding="utf-8"
)
activity=java/"MainActivity.java"
s=activity.read_text(encoding="utf-8")
oldsrc=Path("aura-diagnostics/patch/export_ui.txt").read_text(encoding="utf-8")
start=oldsrc.index('    private void shareFirmwareZip(Uri uri) {')
end=oldsrc.index('    private void startFirmwareExport(Uri uri)',start)
old=oldsrc[start:end]
new=Path("aura-diagnostics/patch/export_share_ui_v11.txt").read_text(encoding="utf-8")
assert s.count(old)==1, "Unable to locate old SAF URI share method"
s=s.replace(old,new+"\n",1)

needle='''                    org.json.JSONArray files=report.optJSONArray("files");'''
assert s.count(needle)==1
s=s.replace(needle,'''                    long exportedBytes=cacheAndValidateExport(uri);
'''+needle,1)
s=s.replace(
    'status.setText("ZIP создан и проверен по SHA-256");',
    'status.setText("ZIP сохранён и проверен · "+exportedBytes+" байт");',
    1
)
s=s.replace(
    '''                        details.setText("Файлов сохранено: "+ok''',
    '''                        shareZipButton.setEnabled(true);
                        details.setText("Размер проверенного ZIP: "+exportedBytes+" байт"
                            +"\\nФайлов сохранено: "+ok''',
    1
)
s=s.replace(
    '''                            .setMessage("Архив содержит "+ok+" файл(а/ов). Пропущено: "+skip''',
    '''                            .setMessage("Размер на телефоне: "+exportedBytes+" байт."
                                +"\\nАрхив содержит "+ok+" файл(а/ов). Пропущено: "+skip''',
    1
)
needle='''        workers.execute(()->{
            try {
                AdbKey key=AdbKey.loadOrCreate(getFilesDir());
                try(AdbClient adb=new AdbClient()){
                    adb.connect(firmwareExportHost'''
assert s.count(needle)==1
s=s.replace(needle,'''        workers.execute(()->{
            try {
                ExportShareProvider.cachedZip(this).delete();
                main.post(()->shareZipButton.setEnabled(false));
                AdbKey key=AdbKey.loadOrCreate(getFilesDir());
                try(AdbClient adb=new AdbClient()){
                    adb.connect(firmwareExportHost''',1)

needle='''private Button scanButton, saveButton, shareButton, backupButton, investigatorButton, exportButton;'''
assert s.count(needle)==1
s=s.replace(needle,
'''private Button scanButton, saveButton, shareButton, backupButton, investigatorButton, exportButton, shareZipButton;''',1)
needle='''exportButton.setOnClickListener(v -> chooseFirmwareExport());'''
assert s.count(needle)==1
s=s.replace(needle,'''exportButton.setOnClickListener(v -> chooseFirmwareExport());
        connectCard.addView(space(12));
        shareZipButton=actionButton("ОТПРАВИТЬ ПОСЛЕДНИЙ ZIP · проверка размера", CARD, WHITE);
        connectCard.addView(shareZipButton);
        shareZipButton.setEnabled(ExportShareProvider.cachedZip(this).length()>=512);
        shareZipButton.setOnClickListener(v -> shareFirmwareZip(null));''',1)

activity.write_text(s,encoding="utf-8")

manifest=app/"src/main/AndroidManifest.xml"
ET.register_namespace("android","http://schemas.android.com/apk/res/android")
t=ET.parse(manifest)
root=t.getroot()
application=root.find('application')
assert application is not None
android="{http://schemas.android.com/apk/res/android}"
authority="com.auratv.diagnostics.export"
assert not any(authority==p.get(android+"authorities") for p in application.findall("provider"))
provider=ET.SubElement(application,"provider")
provider.set(android+"name",".ExportShareProvider")
provider.set(android+"authorities",authority)
provider.set(android+"exported","false")
provider.set(android+"grantUriPermissions","true")
t.write(manifest,encoding="utf-8",xml_declaration=True)

gradle=app/"build.gradle"
g=gradle.read_text(encoding="utf-8")
assert "versionCode 11" in g and "versionName '1.0.0'" in g
g=g.replace("versionCode 11","versionCode 12").replace("versionName '1.0.0'","versionName '1.1.0'")
gradle.write_text(g,encoding="utf-8")

# Defensive build-time source checks
provider_source=(java/"ExportShareProvider.java").read_text(encoding="utf-8")
assert 'MODE_READ_ONLY' in provider_source and 'OpenableColumns.SIZE' in provider_source
assert 'grantUriPermissions' in manifest.read_text(encoding="utf-8")
assert 'setClipData' in s and 'cacheAndValidateExport(uri)' in s
print("v1.1: archive size and SHA-256 checked; share via read-only private URI with size metadata.")
