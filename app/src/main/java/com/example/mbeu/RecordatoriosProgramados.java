package com.example.mbeu;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

public class RecordatoriosProgramados extends Worker {

    private static final String WORK_NAME = "sleep_reminders";
    private static final int MORNING_HOUR = 8;
    private static final int EVENING_HOUR = 20;

    public RecordatoriosProgramados(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    /** Esto es lo que se ejecuta cada vez que toca un aviso. */
    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        // Solo si hay sesión iniciada y las notificaciones están activadas en su perfil
        if (SleepDB.session(context).contains("email")
                && SleepDB.profile(context).getBoolean("notificaciones", true)) {
            Notificaciones.showForNow(context);
        }
        return Result.success();
    }

    /** Programa los avisos: cada 12 horas, a las 8:00 y a las 20:00. Se puede llamar las veces que sea. */
    public static void schedule(Context context) {
        PeriodicWorkRequest request =
                new PeriodicWorkRequest.Builder(RecordatoriosProgramados.class, 12, TimeUnit.HOURS)
                        .setInitialDelay(delayToNextSlot(), TimeUnit.MILLISECONDS)
                        .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    /** Milisegundos que faltan para las próximas 8:00 o 20:00. */
    private static long delayToNextSlot() {
        Calendar now = Calendar.getInstance();
        Calendar next = (Calendar) now.clone();
        next.set(Calendar.MINUTE, 0);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);

        int hour = now.get(Calendar.HOUR_OF_DAY);
        if (hour < MORNING_HOUR) {
            next.set(Calendar.HOUR_OF_DAY, MORNING_HOUR);
        } else if (hour < EVENING_HOUR) {
            next.set(Calendar.HOUR_OF_DAY, EVENING_HOUR);
        } else {
            next.set(Calendar.HOUR_OF_DAY, MORNING_HOUR);
            next.add(Calendar.DAY_OF_YEAR, 1);
        }
        return next.getTimeInMillis() - now.getTimeInMillis();
    }
}
