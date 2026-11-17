package com.example.mbeu;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import android.app.TimePickerDialog;
import android.content.Context;
import android.text.format.DateFormat;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Pregunta a la persona si el sueño que se detectó es correcto.
 * Opciones: confirmarlo, ajustar las horas o descartarlo.
 * Si cierra el diálogo sin elegir, el sueño se queda como está (se toma como correcto).
 */
public class ConfirmarSueno {

    private static final long MIN_DURATION_MS = 30 * 60000L;       // 30 minutos
    private static final long MAX_DURATION_MS = 16 * 3600000L;     // 16 horas

    private final Context context;
    private final List<long[]> pending;     // cada uno: {id, inicio, fin}
    private final Runnable onFinished;
    private int index = 0;

    /** Muestra uno por uno los sueños detectados. Al terminar llama a onFinished. */
    public static void show(Context context, List<long[]> sleeps, Runnable onFinished) {
        if (sleeps.isEmpty()) return;
        new ConfirmarSueno(context, sleeps, onFinished).next();
    }

    private ConfirmarSueno(Context context, List<long[]> pending, Runnable onFinished) {
        this.context = context;
        this.pending = pending;
        this.onFinished = onFinished;
    }

    /** Pasa al siguiente sueño; si ya no hay más, avisa que terminó. */
    private void next() {
        if (index >= pending.size()) {
            if (onFinished != null) onFinished.run();
            return;
        }
        ask(pending.get(index));
    }

    private void advance() {
        index++;
        next();
    }

    /** Pregunta principal: ¿dormiste de X a Y? */
    private void ask(long[] sleep) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_confirmar_sueno, null);
        ((TextView) view.findViewById(R.id.tvHoras))
                .setText(formatTime(sleep[1]) + "  →  " + formatTime(sleep[2]));
        ((TextView) view.findViewById(R.id.tvDuracion))
                .setText("Duración: " + formatDuration(sleep[2] - sleep[1]));

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setView(view)
                .setBackground(ContextCompat.getDrawable(context, R.drawable.bg_dialog))
                .setOnCancelListener(d -> advance())      // cerrar el diálogo = aceptar
                .create();

        view.findViewById(R.id.btnSi).setOnClickListener(v -> {
            dialog.dismiss();
            advance();
        });
        view.findViewById(R.id.btnAjustar).setOnClickListener(v -> {
            dialog.dismiss();
            pickSleepTime(sleep);
        });
        view.findViewById(R.id.btnNo).setOnClickListener(v -> {
            dialog.dismiss();
            discard(sleep);
        });
        dialog.show();
    }

    private String formatDuration(long millis) {
        long minutes = millis / 60000L;
        if (minutes < 60) return minutes + " min";
        long h = minutes / 60;
        long m = minutes % 60;
        return m == 0 ? h + " h" : h + " h " + m + " min";
    }

    /** "No dormí": borra el registro y lo recuerda para no detectarlo otra vez. */
    private void discard(long[] sleep) {
        new SleepDB(context).deleteSleep(sleep[0]);
        DetectarSueno.discard(context, sleep[1]);
        Toast.makeText(context, "Registro eliminado", Toast.LENGTH_SHORT).show();
        advance();
    }

    /** Paso 1 de ajustar: la hora a la que se durmió. */
    private void pickSleepTime(long[] sleep) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(sleep[1]);
        TimePickerDialog picker = new TimePickerDialog(context,
                (view, hour, minute) -> pickWakeTime(sleep, hour, minute),
                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE),
                DateFormat.is24HourFormat(context));
        picker.setTitle("¿A qué hora te dormiste?");
        picker.setOnCancelListener(dialog -> ask(sleep));      // si cancela, vuelve a la pregunta
        picker.show();
    }

    /** Paso 2 de ajustar: la hora a la que despertó. */
    private void pickWakeTime(long[] sleep, int sleepHour, int sleepMinute) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(sleep[2]);
        TimePickerDialog picker = new TimePickerDialog(context,
                (view, hour, minute) -> applyChange(sleep, sleepHour, sleepMinute, hour, minute),
                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE),
                DateFormat.is24HourFormat(context));
        picker.setTitle("¿A qué hora despertaste?");
        picker.setOnCancelListener(dialog -> ask(sleep));
        picker.show();
    }

    /** Calcula las fechas completas con las horas elegidas, las valida y las guarda. */
    private void applyChange(long[] sleep, int sleepHour, int sleepMinute, int wakeHour, int wakeMinute) {
        // El despertar se queda el mismo día que el original
        Calendar wake = Calendar.getInstance();
        wake.setTimeInMillis(sleep[2]);
        wake.set(Calendar.HOUR_OF_DAY, wakeHour);
        wake.set(Calendar.MINUTE, wakeMinute);
        wake.set(Calendar.SECOND, 0);
        wake.set(Calendar.MILLISECOND, 0);

        // La hora de dormir es la más cercana ANTES del despertar
        // (así 11:30 p. m. cae el día anterior y 12:15 a. m. el mismo día)
        Calendar bed = (Calendar) wake.clone();
        bed.set(Calendar.HOUR_OF_DAY, sleepHour);
        bed.set(Calendar.MINUTE, sleepMinute);
        if (bed.getTimeInMillis() >= wake.getTimeInMillis()) {
            bed.add(Calendar.DAY_OF_YEAR, -1);
        }

        long sleepMs = bed.getTimeInMillis();
        long wakeMs = wake.getTimeInMillis();
        long duration = wakeMs - sleepMs;

        String error = null;
        if (wakeMs > System.currentTimeMillis()) {
            error = "La hora de despertar no puede ser en el futuro";
        } else if (duration < MIN_DURATION_MS || duration > MAX_DURATION_MS) {
            error = "El sueño debe durar entre 30 minutos y 16 horas";
        } else if (!new SleepDB(context).updateSleep(sleep[0], sleepMs, wakeMs)) {
            error = "Ese horario se traslapa con otro registro";
        }

        if (error != null) {
            Toast.makeText(context, error, Toast.LENGTH_LONG).show();
            ask(sleep);                                        // el registro sigue como estaba
            return;
        }
        Toast.makeText(context, "Horas actualizadas", Toast.LENGTH_SHORT).show();
        advance();
    }

    private String formatTime(long millis) {
        return new java.text.SimpleDateFormat("h:mm a", new Locale("es", "MX")).format(new java.util.Date(millis));
    }
}