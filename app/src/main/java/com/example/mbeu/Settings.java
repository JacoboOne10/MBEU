package com.example.mbeu;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class Settings extends AppCompatActivity {
    private SharedPreferences prefs;
    private EditText etName;
    private EditText etAge;
    private RadioGroup rgSchedule;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = SleepDB.profile(this);
        etName = findViewById(R.id.etName);
        etAge = findViewById(R.id.etAge);
        swNotifications = findViewById(R.id.swNotifications);

        showSummary();

        swNotifications.setChecked(prefs.getBoolean("notificaciones", true));
        swNotifications.setOnCheckedChangeListener((button, isChecked) -> {
            prefs.edit().putBoolean("notificaciones", isChecked).apply();
            if (isChecked && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        });

        findViewById(R.id.settingsBackButton).setOnClickListener(v -> finish());

        findViewById(R.id.btnEdit).setOnClickListener(v -> {
            fillForm();
            findViewById(R.id.layoutSummary).setVisibility(View.GONE);
            findViewById(R.id.layoutEdit).setVisibility(View.VISIBLE);
        });
        // mantener presionados para mostrar notificaciones
        //de noche
        findViewById(R.id.tvSettingsTitle).setOnLongClickListener(v -> {
            showTestNotification(true);
            return true;
        });
        findViewById(R.id.btnExportCsv).setOnClickListener(v ->
                csvSaver.launch("historial_sueno.csv"));       // este es el nombre que se sugiere
        //de dia
        findViewById(R.id.tvNotifications).setOnLongClickListener(v -> {
            showTestNotification(false);
            return true;
        });

        findViewById(R.id.btnCancel).setOnClickListener(v -> showSummary());
        //guardar
        findViewById(R.id.btnSave).setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) {
                etName.setError("Escribe tu nombre");
                return;
            }
            int age;
            try {
                age = Integer.parseInt(etAge.getText().toString().trim());
            } catch (NumberFormatException e) {
                etAge.setError("Escribe tu edad");
                return;
            }
            if (age < 1 || age > 120) {
                etAge.setError("Escribe una edad entre 1 y 120");
                return;
            }

            prefs.edit()
                    .putString("nombre", name)
                    .putInt("edad", age)
                    .apply();
            Toast.makeText(this, "Cambios guardados", Toast.LENGTH_SHORT).show();
            showSummary();
        });

        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            SleepDB.session(this).edit().clear().apply();   // el perfil se conserva
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });

        findViewById(R.id.btnUsageAccess).setOnClickListener(v -> DetectarSueno.openUsageSettings(this));
    }

    private void showSummary() {
        ((TextView) findViewById(R.id.tvSummaryName))
                .setText("Nombre: " + prefs.getString("nombre", ""));
        ((TextView) findViewById(R.id.tvSummaryAge))
                .setText("Edad: " + prefs.getInt("edad", 20));

        findViewById(R.id.layoutEdit).setVisibility(View.GONE);
        findViewById(R.id.layoutSummary).setVisibility(View.VISIBLE);
    }

    private void fillForm() {
        etName.setText(prefs.getString("nombre", ""));
        etName.setError(null);
        etAge.setText(String.valueOf(prefs.getInt("edad", 20)));
        etAge.setError(null);
    }
    @Override
    protected void onResume() {
        super.onResume();
        TextView status = findViewById(R.id.tvUsageStatus);
        if (Build.VERSION.SDK_INT < 28) {
            status.setText("No disponible en esta versión de Android");
        } else if (DetectarSueno.hasUsageAccess(this)) {
            status.setText("Estado: activada");
        } else {
            status.setText("Estado: desactivada");
        }
    }
    private SwitchCompat swNotifications;

    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted) {
                    swNotifications.setChecked(false);      // también guarda "no" en las preferencias
                    Toast.makeText(this, "Sin el permiso no podemos enviarte recordatorios",
                            Toast.LENGTH_LONG).show();
                }
            });
    private void showTestNotification(boolean evening) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            Notificaciones.showRandom(this, evening);
        }
    }
    private final ActivityResultLauncher<String> csvSaver =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("text/csv"), uri -> {
                if (uri == null) return;                       // la persona canceló
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null) throw new IOException("No se pudo abrir el archivo");
                    String csv = new SleepDB(this).buildCsv();
                    out.write(csv.getBytes(StandardCharsets.UTF_8));
                    Toast.makeText(this, "Archivo guardado", Toast.LENGTH_SHORT).show();
                } catch (IOException e) {
                    Toast.makeText(this, "No se pudo guardar el archivo", Toast.LENGTH_SHORT).show();
                }
            });
}