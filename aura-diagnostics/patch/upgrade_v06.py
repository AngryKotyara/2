from pathlib import Path
base=Path("aura-diagnostics/project")
src=base/"app/src/main/java/com/auratv/diagnostics"
adb=src/"AdbClient.java"
s=adb.read_text()
needle='    private void send(int cmd, int arg0, int arg1, byte[] data) throws IOException {'
assert s.count(needle)==1
s=s.replace(needle,Path("aura-diagnostics/patch/backup_stream.txt").read_text(encoding="utf-8")+"\n"+needle,1)
adb.write_text(s)
(src/"FullBackup.java").write_text(Path("aura-diagnostics/patch/FullBackup.java").read_text(encoding="utf-8"))
activity=src/"MainActivity.java"
s=activity.read_text()
assert 'private Button scanButton, saveButton, shareButton;' in s
s=s.replace('private Button scanButton, saveButton, shareButton;','private Button scanButton, saveButton, shareButton, backupButton;',1)
needle='''        shareButton.setOnClickListener(v -> shareTxt());'''
assert needle in s
s=s.replace(needle,needle+'''
        backupButton = actionButton("ПОЛНЫЙ БЭКАП · проверить доступ", CARD, WHITE);
        LinearLayout.LayoutParams backupLp = new LinearLayout.LayoutParams(-1, -2);
        backupLp.setMargins(0, dp(18), 0, 0);
        base.addView(backupButton, backupLp);
        backupButton.setOnClickListener(v -> checkBackupAccess());
''',1)
start='    private void exportTxt() {'
assert start in s
s=s.replace(start,Path("aura-diagnostics/patch/backup_ui.txt").read_text(encoding="utf-8")+"\n"+start,1)

on_result='''        if (request == FULL_BACKUP_DIR_REQUEST && result == Activity.RESULT_OK
                && data != null && data.getData() != null) {
            Uri tree=data.getData();
            try {
                getContentResolver().takePersistableUriPermission(
                    tree, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch(Exception ignored) { /* Permission lasts for this app session. */ }
            confirmFullBackup(tree);
            return;
        }
'''
old='''        super.onActivityResult(request, result, data);'''
assert s.count(old)==1
s=s.replace(old,old+"\n"+on_result,1)
s=s.replace(
    "Все проверки только для чтения: приложение не устанавливает прошивку, не делает резервные копии и не разблокирует загрузчик.",
    "Диагностика только читает данные. Полный образ eMMC возможен только с root в Recovery, по отдельному подтверждению. Приложение не прошивает телевизор и не разблокирует загрузчик."
)
activity.write_text(s)

activity.write_text(s)
gradle=base/"app/build.gradle"
s=gradle.read_text()
assert "versionCode 6" in s and "versionName '0.5.0'" in s
s=s.replace("versionCode 6","versionCode 7").replace("versionName '0.5.0'","versionName '0.6.0'")
gradle.write_text(s)
print("Applied AURA backup v0.6.0")
