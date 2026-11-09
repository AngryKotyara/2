from pathlib import Path
base=Path("aura-diagnostics/project")
java=base/"app/src/main/java/com/auratv/diagnostics"
(java/"FirmwareInvestigator.java").write_text(Path("aura-diagnostics/patch/FirmwareInvestigator.java").read_text(encoding="utf-8"), encoding="utf-8")
activity=java/"MainActivity.java"
s=activity.read_text(encoding="utf-8")
def replace(old,new):
    global s
    assert s.count(old)==1, f"Missing/duplicate v0.7 activity anchor: {old[:100]}"
    s=s.replace(old,new,1)
replace("private Button scanButton, saveButton, shareButton, backupButton;",
        "private Button scanButton, saveButton, shareButton, backupButton, investigatorButton;")
replace("scanButton.setOnClickListener(v -> scan());",'''scanButton.setOnClickListener(v -> scan());
        connectCard.addView(space(12));
        investigatorButton=actionButton("FIRMWARE INVESTIGATOR · собрать пазл", CARD, WHITE);
        connectCard.addView(investigatorButton);
        investigatorButton.setOnClickListener(v -> runInvestigator());''')
replace('    private void scan() {',
        Path("aura-diagnostics/patch/investigator_ui.txt").read_text(encoding="utf-8")+"\n    private void scan() {")
replace("scanButton.setEnabled(false); saveButton.setEnabled(false); shareButton.setEnabled(false);",
        "investigatorButton.setEnabled(false); scanButton.setEnabled(false); saveButton.setEnabled(false); shareButton.setEnabled(false);")
# The normal scan can end with success or connection error:
replace("scanButton.setEnabled(true); saveButton.setEnabled(true); shareButton.setEnabled(true);",
        "investigatorButton.setEnabled(true); scanButton.setEnabled(true); saveButton.setEnabled(true); shareButton.setEnabled(true);")
replace('''                    scanButton.setEnabled(true);
                });
            }
        });
    }

    static boolean isLanIpv4''','''                    scanButton.setEnabled(true);
                    investigatorButton.setEnabled(true);
                });
            }
        });
    }

    static boolean isLanIpv4''')
replace('Все проверки только для чтения:', 'Проверки Investigator только для чтения:')
activity.write_text(s,encoding="utf-8")
gradle=base/"app/build.gradle"
g=gradle.read_text(encoding="utf-8")
assert "versionCode 7" in g and "versionName '0.6.0'" in g
g=g.replace("versionCode 7","versionCode 8").replace("versionName '0.6.0'","versionName '0.7.0'")
gradle.write_text(g,encoding="utf-8")
# Fail the CI if any unexpected high-risk command is added to Investigator.
inv=(java/"FirmwareInvestigator.java").read_text(encoding="utf-8")
for bad in ["adb reboot","fastboot ","dd if=","su -c","setprop ","remount","flashing unlock","pm install"]:
    assert bad not in inv, "Unsafe investigator code: "+bad
print("AURA Firmware Investigator v0.7 patched into Android app")
