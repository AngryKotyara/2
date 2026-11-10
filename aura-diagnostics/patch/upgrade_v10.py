from pathlib import Path
base=Path("aura-diagnostics/project")
path=base/"app/src/main/java/com/auratv/diagnostics/AdbClient.java"
source=path.read_text(encoding="utf-8")
original=Path("aura-diagnostics/patch/backup_stream.txt").read_text(encoding="utf-8")
new=Path("aura-diagnostics/patch/backup_stream_v10.txt").read_text(encoding="utf-8")
assert source.count(original)==1, "Unexpected ADB binary streaming method: require manual verification"
source=source.replace(original,new,1)
path.write_text(source,encoding="utf-8")
gradle=base/"app/build.gradle"
g=gradle.read_text(encoding="utf-8")
assert "versionCode 10" in g and "versionName '0.9.0'" in g
g=g.replace("versionCode 10","versionCode 11").replace("versionName '0.9.0'","versionName '1.0.0'")
gradle.write_text(g,encoding="utf-8")
print("AURA v1.0: robust ADB binary streaming with delayed closed-stream control packets")
