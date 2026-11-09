package com.auratv.diagnostics;

import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * AURA Deep Scan: 15 additional *read-only* scenarios.
 * No root / su / fastboot / flash / dumps of block device contents.
 * An indirect observation NEVER means unlocked or safe to flash.
 */
public final class DeepScan {
    private DeepScan() {}

    public interface Progress { void update(int index, int total, String label); }

    private static final class Probe {
        final String source, command;
        final int limit;
        final boolean direct;
        Probe(String source, String command, int limit, boolean direct) {
            this.source=source; this.command=command; this.limit=limit; this.direct=direct;
        }
    }
    private static Probe d(String source, String command, int max) { return new Probe(source,command,max,true); }
    private static Probe i(String source, String command, int max) { return new Probe(source,command,max,false); }

    private static final class Scenario {
        final String key, title, caution;
        final Probe[] probes;
        Scenario(String key,String title,String caution,Probe... probes) {
            this.key=key; this.title=title; this.caution=caution; this.probes=probes;
        }
    }
    private static final Scenario[] SCENARIOS={
      new Scenario("device_tree_model", "Модель Device Tree",
          "Код платы из Android не заменяет проверенный Device Tree.",
          d("sysfs DT model","cat /sys/firmware/devicetree/base/model",2048),
          i("Android board","getprop ro.product.board",1024)),
      new Scenario("device_tree_compatible","Совместимость Device Tree",
          "Имя аппаратной платформы не заменяет список DT compatible.",
          d("sysfs DT compatible","cat /sys/firmware/devicetree/base/compatible",4096),
          i("Android hardware","getprop ro.hardware",1024)),
      new Scenario("vendor_hal","Аппаратные HAL",
          "Реестр HAL показывает объявленные интерфейсы, не доказывает совместимость бинарных драйверов.",
          d("HAL service registry","lshal",16000),
          i("VINTF vendor manifest","cat /vendor/etc/vintf/manifest.xml",12000)),
      new Scenario("boot_cmdline","Параметры загрузки",
          "Значение ro.boot.hardware не показывает все аргументы ядра.",
          d("kernel bootargs sysfs","cat /sys/firmware/devicetree/base/chosen/bootargs",6000),
          i("ro.boot.hardware","getprop ro.boot.hardware",1024)),
      new Scenario("partitions","Таблица блочных разделов",
          "Список устройств sysfs не равнозначен проверенной карте разделов и адресов записи.",
          d("sysfs partition entries","ls /sys/block/mmcblk0/mmcblk0p*",12000),
          i("block device classes","ls /sys/block",4000)),
      new Scenario("named_partitions","Именованные разделы",
          "Имена и симлинки разделов нужно сверить с заводским образом.",
          d("Amlogic eMMC by-name","ls -l /dev/block/platform/ffe07000.emmc/by-name",12000),
          i("eMMC platform paths","ls /dev/block/platform",5000)),
      new Scenario("bootdevice_partitions","Путь загрузочного устройства",
          "Наличие каталога платформы не подтверждает физический носитель загрузчика.",
          d("platform boot device","ls -l /dev/block/platform/ffe07000.emmc",12000),
          i("block nodes","ls /dev/block",5000)),
      new Scenario("verified_boot","Android Verified Boot",
          "Состояние нельзя определить из отсутствующего ro.boot.verifiedbootstate.",
          d("verifiedbootstate","getprop ro.boot.verifiedbootstate",1024),
          i("boot vbmeta state","getprop ro.boot.vbmeta.device_state",1024)),
      new Scenario("flash_locked","Блокировка загрузчика",
          "Результаты getprop не разрешают разблокировку или прошивку.",
          d("boot flash locked","getprop ro.boot.flash.locked",1024),
          i("vbmeta device state","getprop ro.boot.vbmeta.device_state",1024)),
      new Scenario("vbmeta_state","VBMeta / подпись образов",
          "Сведения ОС не заменяют чтение и криптографическую проверку подписанных образов.",
          d("vbmeta device state","getprop ro.boot.vbmeta.device_state",1024),
          i("verity mode","getprop ro.boot.veritymode",1024)),
      new Scenario("verity_mode","dm-verity",
          "Устройство dm-* само по себе не доказывает включённую проверку целостности.",
          d("verity mode","getprop ro.boot.veritymode",1024),
          i("device-mapper nodes","ls /sys/block/dm-*",4500)),
      new Scenario("ab_updates","Схема A/B",
          "Отсутствие свойства A/B не является доказательством схемы single-slot.",
          d("bootctl slots","bootctl get-number-slots",1024),
          i("A/B update property","getprop ro.build.ab_update",1024)),
      new Scenario("slot_suffix","Слот загрузки",
          "Номер слота из Android не подтверждает доступность безопасного отката.",
          d("bootctl current slot","bootctl get-current-slot",1024),
          i("slot suffix","getprop ro.boot.slot_suffix",1024)),
      new Scenario("graphics","Видеотракт и композиция",
          "Работа стандартного дисплея не доказывает корректность HDR / DRM / HDMI в новой ОС.",
          d("SurfaceFlinger display list","dumpsys SurfaceFlinger --list",14000),
          i("DisplayManager modes","dumpsys display",16000)),
      new Scenario("audio_policy","Маршрутизация звука",
          "Наличие AudioService не подтверждает работу SPDIF / HDMI ARC / многоканального звука.",
          d("Audio policy (larger budget)","dumpsys media.audio_policy",110000),
          i("AudioService","dumpsys audio",19000))
    };

    public static int count() { return SCENARIOS.length; }

    public static JSONObject scan(AdbClient adb, Progress progress) throws Exception {
        JSONObject all = new JSONObject();
        int direct = 0, indirect = 0, blocked = 0, unknown = 0;
        JSONArray unresolved = new JSONArray();
        for(int index=0;index<SCENARIOS.length;index++) {
            Scenario scenario=SCENARIOS[index];
            progress.update(index+1, SCENARIOS.length,scenario.title);
            JSONObject row=new JSONObject();
            row.put("label",scenario.title);
            row.put("limitation",scenario.caution);
            JSONArray attempts=new JSONArray();
            String result="unknown";
            for(Probe probe:scenario.probes){
                JSONObject attempt=new JSONObject();
                attempt.put("source",probe.source);
                String state;
                String response="";
                try {
                    response=adb.shell(probe.command,probe.limit);
                    state=classify(response,probe.limit);
                } catch(Exception e){
                    state="error";
                    response=e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage());
                }
                attempt.put("status",state);
                attempt.put("value",DiagnosticRunner.redact(response));
                attempts.put(attempt);
                if("available".equals(state)) {
                    result=probe.direct?"direct":"indirect";
                    row.put("source",probe.source);
                    row.put("evidence",DiagnosticRunner.redact(response));
                    break;
                }
                if("access_denied".equals(state) && "unknown".equals(result)) result="access_denied";
            }
            row.put("status",result);
            row.put("attempts",attempts);
            all.put(scenario.key,row);
            if("direct".equals(result)) direct++;
            else if("indirect".equals(result)) indirect++;
            else {
                unresolved.put(scenario.key);
                if("access_denied".equals(result)) blocked++;
                else unknown++;
            }
        }
        JSONObject summary=new JSONObject();
        summary.put("total",SCENARIOS.length);
        summary.put("direct",direct);
        summary.put("indirect",indirect);
        summary.put("access_denied",blocked);
        summary.put("unknown",unknown);
        summary.put("unresolved",unresolved);
        summary.put("flash_ready",false);
        summary.put("warning","Косвенные сведения не дают права прошивать. Нужны заводской образ, полный backup и проверенное восстановление.");
        JSONObject answer=new JSONObject();
        answer.put("checks",all);
        answer.put("summary",summary);
        return answer;
    }

    private static String classify(String raw,int limit){
        if(raw==null || raw.trim().isEmpty()) return "unknown";
        String s=raw.trim().toLowerCase(Locale.ROOT);
        if(s.contains("permission denied")||s.contains("operation not permitted")||s.contains("access denied"))
            return "access_denied";
        if(s.contains("not found")||s.contains("no such file")||s.contains("unknown command")
             ||s.contains("not recognized")||s.contains("not available")||s.contains("cannot access")
             ||s.contains("inaccessible")||s.contains("can't find service"))
            return "unavailable";
        if(raw.getBytes(StandardCharsets.UTF_8).length>=limit-8) return "truncated";
        return "available";
    }
}
