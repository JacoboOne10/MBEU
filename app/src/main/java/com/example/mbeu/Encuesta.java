package com.example.mbeu;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class Encuesta extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_encuesta);

        EditText etName = findViewById(R.id.etName);
        EditText etAge = findViewById(R.id.etAge);

        findViewById(R.id.btnSave).setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String ageText = etAge.getText().toString().trim();

            if (name.isEmpty()) {
                etName.setError("Escribe tu nombre");
                return;
            }
            int age;
            try {
                age = Integer.parseInt(ageText);
            } catch (NumberFormatException e) {
                etAge.setError("Escribe tu edad");
                return;
            }
            if (age < 1 || age > 120) {
                etAge.setError("Escribe una edad entre 1 y 120");
                return;
            }
            SharedPreferences prefs = SleepDB.profile(this);
            prefs.edit()
                    .putString("nombre", name)
                    .putInt("edad", age)
                    .apply();
            startActivity(new Intent(this, Inicio.class));
            finish();
        });
    }
}