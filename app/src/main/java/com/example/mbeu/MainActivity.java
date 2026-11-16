package com.example.mbeu;

import android.os.Bundle;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;


public class MainActivity extends AppCompatActivity {
    //Cuentas hardcode
    private static final String[] EMAILS = {"john.doe@email.com", "maria.lopez@email.com"};
    private static final String[] PASSWORDS = {"Pass123$", "Sueno123$"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (SleepDB.session(this).contains("email")) {
            goHome();
            return;
        }

        EditText etEmail = findViewById(R.id.email);
        EditText etPassword = findViewById(R.id.editTextTextPassword);

        findViewById(R.id.btnLogin).setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString();

            if (email.isEmpty()) {
                etEmail.setError("Escribe tu correo");
                return;
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEmail.setError("Escribe un correo válido");
                return;
            }
            if (password.isEmpty()) {
                etPassword.setError("Escribe tu contraseña");
                return;
            }

            for (int i = 0; i < EMAILS.length; i++) {
                if (EMAILS[i].equalsIgnoreCase(email) && PASSWORDS[i].equals(password)) {
                    SleepDB.session(this).edit().putString("email", EMAILS[i]).apply();
                    goHome();
                    return;
                }
            }
            Toast.makeText(this, "Correo o contraseña incorrectos", Toast.LENGTH_SHORT).show();
        });
    }
    private void goHome() {
        startActivity(new Intent(this, Inicio.class));
        finish();
    }
}