package com.example.mbeu;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.util.Random;

public class Notificaciones {

    private static final String CHANNEL_ID = "sleep_reminders";

    //Recordatorios para la noche
    private static final String[][] EVENING = {
            {"Hora de ir bajando el ritmo",
                    "Una rutina relajante, como leer o escuchar música suave, le avisa a tu cuerpo que es hora de descansar."},
            {"Recuerda dejar las pantallas",
                    "Intenta apagarlas una o dos horas antes de dormir. La luz de las pantallas, sobre todo la azul, puede retrasar la hormona que te ayuda a dormir."},
            {"Prepara tu cuarto para dormir",
                    "Un cuarto oscuro, silencioso y fresco te ayuda a dormirte más fácil y a descansar mejor."},
            {"Cena ligero esta noche",
                    "Una comida pesada o el alcohol cerca de la hora de dormir pueden interrumpir tu descanso."},
            {"Mañana te lo vas a agradecer",
                    "Dormir y despertar a la misma hora todos los días, incluso en fin de semana, ayuda a que tu cuerpo se regule solo."},
            {"¿Ya es casi tu hora de dormir?",
                    "Cada noche bien dormida suma a tu racha, así como suma a tu salud"}
    };

    // Para el día
    private static final String[][] DAYTIME = {
            {"¿Has estado durmiendo bien?",
                    "Verifica tu calendario del sueño y mira cómo te ha ido esta semana."},
            {"¿Has estado durmiendo bien?",
                    "Dormir tus horas ideales ayuda a tu memoria, a tu ánimo y a tu concentración durante el día."},
            {"Dale luz a tu mañana",
                    "Salir a la luz natural por la mañana ayuda a que tu reloj interno sepa cuándo es de día y cuándo toca dormir."},
            {"Cuida tu café de la tarde",
                    "La cafeína puede quitarte el sueño muchas horas después de tomarla. Intenta evitarla desde la tarde."},
            {"Muévete hoy",
                    "Hacer ejercicio durante el día te ayuda a dormir mejor, pero evita el ejercicio intenso justo antes de acostarte."},
            {"Si una noche no puedes dormir",
                    "Si llevas un rato en la cama sin sueño, levántate y haz algo tranquilo hasta que lo sientas. Dar vueltas suele hacerlo más difícil."}
    };

    //Crea el canal de notificaciones
    public static void createChannel(Context context) {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Recordatorios de sueño", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Consejos y recordatorios para dormir mejor");
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);
    }

    //mensaje al azar de noche
    public static void showRandom(Context context, boolean evening) {
        String[][] list = evening ? EVENING : DAYTIME;
        String[] message = list[new Random().nextInt(list.length)];
        show(context, message[0], message[1]);
    }

    @SuppressLint("MissingPermission")
    public static void show(Context context, String title, String body) {
        //permisos para android 13
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        createChannel(context);

        //abrir la app con la noti
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body)) //deslizar para ver completa
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManagerCompat.from(context)
                .notify((int) (System.currentTimeMillis() / 1000), builder.build());
    }
    private static final int EVENING_START_HOUR = 20;   // los mensajes de la noche salen desde las 8 p.m.

    public static void showForNow(Context context) {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        List<String[]> pool = new ArrayList<>(Arrays.asList(DAYTIME));
        if (hour >= EVENING_START_HOUR) {
            pool.addAll(Arrays.asList(EVENING));
        }
        String[] message = pool.get(new Random().nextInt(pool.size()));
        show(context, message[0], message[1]);
    }
}