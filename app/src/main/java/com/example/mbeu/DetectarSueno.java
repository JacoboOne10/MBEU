package com.example.mbeu;
import android.Manifest;
import android.app.Activity;
import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class DetectarSueno {

    // Ajustes de la detección. Para probar rápido: MIN_SLEEP_MINUTES = 3 y WINDOW_START_HOUR = 0
    private static final int MIN_SLEEP_MINUTES = 90; //minimo hora y media
    private static final int MAX_SLEEP_HOURS = 13; //demasiado tiempo durmiendo
    private static final int BLIP_MINUTES = 2;  //si lo usa menos de 2min no cuenta como que despertó
    private static final int WINDOW_START_HOUR = 7; //desde las 7
    private static final int WINDOW_END_HOUR = 5;
    private static final long LOOKBACK_HOURS = 36; //revisa hasta 36h de historial

    /*permisos*/
    public static boolean hasUsageAccess(Context context) {
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        int mode;
        if (Build.VERSION.SDK_INT >= 29) {
            mode = appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), context.getPackageName());
        } else {
            mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), context.getPackageName());
        }
        if (mode == AppOpsManager.MODE_DEFAULT) {
            return context.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    /** Abre la pantalla de Ajustes donde se concede el permiso. */
    public static void openUsageSettings(Activity activity) {
        activity.startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
    }

    /** Revisa el historial de pantalla y guarda el sueño que encuentre. Devuelve cuántos tramos guardó. */
    public static int detectAndSave(Context context) {
        if (Build.VERSION.SDK_INT < 28 || !hasUsageAccess(context)) return 0;

        UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        long now = System.currentTimeMillis();
        UsageEvents events = usm.queryEvents(now - LOOKBACK_HOURS * 3600000L, now);

        // 1) Periodos con la pantalla realmente encendida: {inicio, fin}
        List<long[]> screenOn = new ArrayList<>();
        UsageEvents.Event e = new UsageEvents.Event();
        long onStart = -1;
        while (events.hasNextEvent()) {
            events.getNextEvent(e);
            if (e.getEventType() == UsageEvents.Event.SCREEN_INTERACTIVE) {
                onStart = e.getTimeStamp();
            } else if (e.getEventType() == UsageEvents.Event.SCREEN_NON_INTERACTIVE && onStart != -1) {
                if (e.getTimeStamp() - onStart >= BLIP_MINUTES * 60000L) {
                    screenOn.add(new long[]{onStart, e.getTimeStamp()});
                }
                onStart = -1;
            }
        }
        if (onStart != -1) {
            screenOn.add(new long[]{onStart, now});    // la pantalla está encendida ahora mismo
        }

        // 2) Cada hueco entre dos periodos encendidos es un posible sueño
        SleepDB db = new SleepDB(context);
        int saved = 0;
        for (int i = 0; i + 1 < screenOn.size(); i++) {
            long sleepMs = screenOn.get(i)[1];         // la pantalla se apagó
            long wakeMs = screenOn.get(i + 1)[0];      // la pantalla volvió a encenderse
            long minutes = (wakeMs - sleepMs) / 60000L;

            if (minutes >= MIN_SLEEP_MINUTES
                    && minutes <= MAX_SLEEP_HOURS * 60L
                    && startsInWindow(sleepMs)) {
                if (db.addSleep(sleepMs, wakeMs)) saved++;
            }
        }
        return saved;
    }

    private static boolean startsInWindow(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        int hour = c.get(Calendar.HOUR_OF_DAY);
        return hour >= WINDOW_START_HOUR || hour < WINDOW_END_HOUR;
    }
}
