from pathlib import Path
root=Path("aura-diagnostics/project")
java=root/"app/src/main/java/com/auratv/diagnostics"
(java/"DeepScan.java").write_text(Path("aura-diagnostics/patch/DeepScan.java").read_text(encoding="utf-8"), encoding="utf-8")

runner=java/"DiagnosticRunner.java"
s=runner.read_text(encoding="utf-8")
def change(old,new):
    global s
    assert s.count(old)==1, "Unexpected DiagnosticRunner pattern: " + old[:70]
    s=s.replace(old,new,1)
change('root.put("tool_version", "0.4.1");','root.put("tool_version", "0.5.0");')
change('public static int count() { return COMMANDS.length; }','public static int count() { return COMMANDS.length + DeepScan.count(); }')
change('progress.update(i + 1, COMMANDS.length, item[1]);','progress.update(i + 1, count(), item[1]);')
change('''        root.put("results", results);
        root.put("checks", checks);
        root.put("errors", errors);
        root.put("readiness", FirmwareAssessment.evaluate(results, checks));''',
'''        JSONObject deep = DeepScan.scan(adb,
            (i, total, label) -> progress.update(COMMANDS.length + i, count(), "Deep Scan · " + label));
        root.put("results", results);
        root.put("checks", checks);
        root.put("deep_scan", deep);
        root.put("errors", errors);
        JSONObject readiness = FirmwareAssessment.evaluate(results, checks);
        readiness.put("deep_scan_summary", deep.optJSONObject("summary"));
        root.put("readiness", readiness);''')
runner.write_text(s,encoding="utf-8")

activity=java/"MainActivity.java"
s=activity.read_text(encoding="utf-8")
old='''                                + "\n\nГОТОВНОСТЬ: пока не готов к безопасной прошивке"
                                + "\nПолный список обязательных проверок сохранён в TXT-отчёте.");'''
# The file contains escaped string forms, not physical newlines inside Java literals.
old=old.replace('"\n\nГОТОВНОСТЬ','"\\n\\nГОТОВНОСТЬ').replace('"\nПолный','"\\nПолный')
assert old in s, "Missing UI status string"
new='''                                + "\\n\\nDeep Scan: " + report.optJSONObject("deep_scan").optJSONObject("summary").optInt("direct") + " напрямую, "
                                + report.optJSONObject("deep_scan").optJSONObject("summary").optInt("indirect") + " косвенно"
                                + "\\nНе подтверждены: " + report.optJSONObject("deep_scan").optJSONObject("summary").optInt("unknown")
                                + ", доступ закрыт: " + report.optJSONObject("deep_scan").optJSONObject("summary").optInt("access_denied")
                                + "\\n\\nГОТОВНОСТЬ: пока не готов к безопасной прошивке"
                                + "\\nПодробные результаты Deep Scan сохранены в TXT-отчёте.");'''
s=s.replace(old,new,1)
s=s.replace('scanButton = actionButton("Начать диагностику", PURPLE, WHITE);',
            'scanButton = actionButton("Начать диагностику + Deep Scan", PURPLE, WHITE);',1)
activity.write_text(s,encoding="utf-8")

gradle=root/"app/build.gradle"
s=gradle.read_text(encoding="utf-8")
assert "versionCode 5" in s and "versionName '0.4.1'" in s
s=s.replace("versionCode 5","versionCode 6",1).replace("versionName '0.4.1'","versionName '0.5.0'",1)
gradle.write_text(s,encoding="utf-8")
print("Applied Deep Scan v0.5.0")
