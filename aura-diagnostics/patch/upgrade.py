from pathlib import Path

root = Path("aura-diagnostics/project")
activity = root / "app/src/main/java/com/auratv/diagnostics/MainActivity.java"
s = activity.read_text(encoding="utf-8")

def change(old, new):
    global s
    assert s.count(old) == 1, "Expected one occurrence of: " + old
    s = s.replace(old, new, 1)

change("import android.content.Intent;",
       "import android.content.Intent;\nimport android.content.ClipData;\nimport android.net.Uri;\nimport java.io.File;\nimport java.io.FileOutputStream;")
change('"Сохранить JSON-отчёт"', '"Сохранить TXT-отчёт"')
change('"Поделиться отчётом"', '"Поделиться TXT-файлом"')
change("saveButton.setOnClickListener(v -> exportJson());",
       "saveButton.setOnClickListener(v -> exportTxt());")
change("shareButton.setOnClickListener(v -> shareJson());",
       "shareButton.setOnClickListener(v -> shareTxt());")
change("private void exportJson() {", "private void exportTxt() {")
change('intent.setType("application/json");', 'intent.setType("text/plain");')
change('"AURA_TV_diagnostics.json"', '"AURA_TV_diagnostics.txt"')
change("stream.write(lastReport.getBytes(StandardCharsets.UTF_8));",
       "stream.write(textReport().getBytes(StandardCharsets.UTF_8));")

start = s.index("    private void shareJson() {")
end = s.index("    private LinearLayout card()", start)
s = s[:start] + r'''    /** Full JSON payload saved as .txt for attachment in ChatGPT. */
    private String textReport() {
        if (lastReport == null) return "";
        try {
            JSONObject report = new JSONObject(lastReport);
            JSONObject results = report.optJSONObject("results");
            if (results != null) {
                String cpu = results.optString("cpu", "");
                results.put("cpu", cpu.replaceAll("(?m)(Serial\\s*:\\s*)[0-9A-Fa-f]{10,}", "$1[СКРЫТО]"));
            }
            return "AURA TV OS / ДИАГНОСТИКА\nФормат: TXT, структура: JSON\n\n" + report.toString(2);
        } catch (Exception ignored) {
            return lastReport;
        }
    }

    private void shareTxt() {
        if (lastReport == null) return;
        try {
            File directory = new File(getCacheDir(), "reports");
            if (!directory.isDirectory() && !directory.mkdirs()) {
                throw new java.io.IOException("Не удалось создать папку отчётов");
            }
            File file = new File(directory, "AURA_TV_diagnostics.txt");
            try (FileOutputStream output = new FileOutputStream(file)) {
                output.write(textReport().getBytes(StandardCharsets.UTF_8));
            }
            Uri uri = new Uri.Builder()
                    .scheme("content")
                    .authority(getPackageName() + ".reports")
                    .appendPath("report.txt")
                    .build();
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.setClipData(ClipData.newRawUri("AURA_TV_diagnostics.txt", uri));
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "Отправить TXT-отчёт AURA"));
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка отправки: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
''' + s[end:]
activity.write_text(s, encoding="utf-8")

provider = root / "app/src/main/java/com/auratv/diagnostics/ReportContentProvider.java"
provider.write_text(Path("aura-diagnostics/patch/ReportContentProvider.java").read_text(encoding="utf-8"), encoding="utf-8")

manifest = root / "app/src/main/AndroidManifest.xml"
s = manifest.read_text(encoding="utf-8")
assert s.count('        <activity android:name=".MainActivity"') == 1
s = s.replace('        <activity android:name=".MainActivity"',
'''        <provider android:name=".ReportContentProvider"
                  android:authorities="com.auratv.diagnostics.reports"
                  android:exported="false"
                  android:grantUriPermissions="true"/>
        <activity android:name=".MainActivity"''', 1)
manifest.write_text(s, encoding="utf-8")

gradle = root / "app/build.gradle"
s = gradle.read_text(encoding="utf-8")
assert "versionCode 2" in s and "versionName '0.2.0'" in s
s = s.replace("versionCode 2", "versionCode 3").replace("versionName '0.2.0'", "versionName '0.3.0'")
gradle.write_text(s, encoding="utf-8")

runner = root / "app/src/main/java/com/auratv/diagnostics/DiagnosticRunner.java"
s = runner.read_text(encoding="utf-8")
assert '"tool_version", "0.1.0"' in s
runner.write_text(s.replace('"tool_version", "0.1.0"', '"tool_version", "0.3.0"'), encoding="utf-8")
print("AURA Diagnostics TXT/share patch applied")
