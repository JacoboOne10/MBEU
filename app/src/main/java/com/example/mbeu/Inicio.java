package com.example.mbeu;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.widget.TextView;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import java.util.List;

public class Inicio extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences prefs = SleepDB.profile(this);
        if (!prefs.contains("edad")) {
            startActivity(new Intent(this, Encuesta.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_inicio);
        if (Build.VERSION.SDK_INT >= 33
                && prefs.getBoolean("notificaciones", true)
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
        //Menu
        findViewById(R.id.cardCalendar).setOnClickListener(v ->
                startActivity(new Intent(this, Calendario.class)));

        findViewById(R.id.cardMeditation).setOnClickListener(v ->
                startActivity(new Intent(this, Meditacion.class)));

        findViewById(R.id.cardMusic).setOnClickListener(v ->
                startActivity(new Intent(this, Musica.class)));

        findViewById(R.id.cardSettings).setOnClickListener(v ->
                startActivity(new Intent(this, Settings.class)));
    }
    @Override
    protected void onResume() {
        super.onResume();
        updateHeader();
        // Si se detectó sueño nuevo, la persona lo confirma, lo ajusta o lo descarta
        List<long[]> detected = DetectarSueno.detectAndSave(this);
        if (!detected.isEmpty()) {
            updateHeader();                                   // racha con lo recién detectado
            ConfirmarSueno.show(this, detected, this::updateHeader);   // y otra vez al terminar
        }
        RecordatoriosProgramados.schedule(this);
    }

    /** Pone el nombre y la racha de días durmiendo adecuadamente. */
    private void updateHeader() {
        SharedPreferences prefs = SleepDB.profile(this);
        ((TextView) findViewById(R.id.username)).setText(prefs.getString("nombre", "Usuario"));
        int age = prefs.getInt("edad", 20);
        int streak = new SleepDB(this).getStreak(age);
        ((TextView) findViewById(R.id.tvStreak)).setText(String.valueOf(streak));
        ((TextView) findViewById(R.id.textView7)).setText(streak == 1 ? "Día" : "Días");
    }

    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted) {
                    // Sin permiso, el interruptor de Configuración queda apagado
                    SleepDB.profile(this).edit().putBoolean("notificaciones", false).apply();
                }
            });
}
