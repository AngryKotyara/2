from pathlib import Path
base=Path("aura-diagnostics/project")
src=base/"app/src/main/java/com/auratv/diagnostics"
(src/"FirmwareClues.java").write_text(
    Path("aura-diagnostics/patch/FirmwareClues.java").read_text(encoding="utf-8"),encoding="utf-8")
inv=src/"FirmwareInvestigator.java"
s=inv.read_text(encoding="utf-8")
def repl(text,old,new):
    assert text.count(old)==1, ("Patch anchor missing or ambiguous: "+old[:100])
    return text.replace(old,new,1)
s=repl(s,"root.put(\"tool_version\",\"0.7.0\");","root.put(\"tool_version\",\"0.8.0\");")
s=repl(s,"public static int stages(){return STAGES.length;}",
       "public static int stages(){return STAGES.length+1;}")
s=repl(s,'if(t==null) return "unknown";','if(t==null) return "unknown";') if 'if(t==null) return "unknown";' in s else s
s=repl(s,'String t=v.trim().toLowerCase(Locale.ROOT);',
         'String t=v.trim().toLowerCase(Locale.ROOT);\n        if(t.equals("unknown")||t.equals("null")||t.equals("n/a"))return "unknown";')
s=repl(s,'''        if(t.startsWith("cat: ")||t.startsWith("ls: ")||t.startsWith("sha256sum: ")''',
'''        // A single failing wildcard must not hide a successful file listing.
        if(t.contains("\\n")){
            boolean useful=false, failed=false;
            for(String line:v.split("\\\\r?\\\\n")){
                String x=line.trim().toLowerCase(Locale.ROOT);
                if(x.isEmpty())continue;
                if(x.startsWith("ls: ")||x.startsWith("cat: ")
                    ||x.startsWith("sha256sum: "))failed=true;
                else useful=true;
            }
            if(useful && failed)return "partial";
        }
        if(t.startsWith("cat: ")||t.startsWith("ls: ")||t.startsWith("sha256sum: ")''')
s=repl(s,'return x!=null && "available".equals(x.optString("status"))?x.optString("value",""):"";',
       'return x!=null && ("available".equals(x.optString("status"))||"partial".equals(x.optString("status")))?x.optString("value",""):"";')
s=repl(s,'int available=0,denied=0,unknown=0,unavailable=0,error=0,truncated=0;',
       'int available=0,denied=0,unknown=0,unavailable=0,error=0,truncated=0,partial=0;')
s=repl(s,'else if("truncated".equals(st))truncated++;',
       'else if("truncated".equals(st))truncated++;\n                else if("partial".equals(st))partial++;')
s=repl(s,'progress.update(i,STAGES.length,stage.name);','progress.update(i,stages(),stage.name);')
s=repl(s,'progress.update(i+1,STAGES.length,stage.name+" — завершено");',
       'progress.update(i+1,stages(),stage.name+" — завершено");')
s=repl(s,'        root.put("phases",phases);',
'''        progress.update(STAGES.length,stages(),"Анализ fstab, OTA и системных разделов");
        root.put("firmware_clues",FirmwareClues.scan(adb));
        progress.update(stages(),stages(),"Шестой этап завершён");
        root.put("phases",phases);''')
s=repl(s,'summary.put("truncated",truncated);','summary.put("truncated",truncated);\n        summary.put("partial",partial);')
# Only v0.8 class is used in final assembly; original v0.7 patch remains unchanged.
inv.write_text(s,encoding="utf-8")
activity=src/"MainActivity.java"
a=activity.read_text(encoding="utf-8")
a=repl(a,'status.setText("Firmware Investigator · 5 этапов выполнено");',
       'status.setText("Firmware Investigator · 6 этапов выполнено");')
a=repl(a,'count.setText("5 / 5 этапов");','count.setText("6 / 6 этапов");')
a=repl(a,'JSONObject backup=facts==null?null:facts.optJSONObject("raw_backup_rights");',
'''JSONObject backup=facts==null?null:facts.optJSONObject("raw_backup_rights");
                        JSONObject fclues=report.optJSONObject("firmware_clues");
                        JSONObject fsummary=fclues==null?null:fclues.optJSONObject("summary");''')
a=repl(a,'+ "\\nБэкап: "+(backup==null?"нет данных":backup.optString("value"))',
''' + "\\nБэкап: "+(backup==null?"нет данных":backup.optString("value"))
                            + "\\nНайден OTA APK: "+(fsummary!=null&&fsummary.optBoolean("upgrade_apk_located"))
                            + "\\nДоступен fstab: "+(fsummary!=null&&fsummary.optBoolean("fstab_readable"))''')
activity.write_text(a,encoding="utf-8")
gradle=base/"app/build.gradle"
g=gradle.read_text(encoding="utf-8")
assert "versionCode 8" in g and "versionName '0.7.0'" in g
g=g.replace("versionCode 8","versionCode 9").replace("versionName '0.7.0'","versionName '0.8.0'")
gradle.write_text(g,encoding="utf-8")
for b in ["adb reboot","fastboot ","dd if=","su -c","setprop ","remount","flashing unlock","pm install"]:
    assert b not in (inv.read_text()+ (src/"FirmwareClues.java").read_text()), "Unsafe investigator command: "+b
print("AURA Firmware Investigator v0.8 patched successfully")
