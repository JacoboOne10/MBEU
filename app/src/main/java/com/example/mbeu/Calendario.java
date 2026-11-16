package com.example.mbeu;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Map;
import android.content.Intent;

public class Calendario extends AppCompatActivity implements View.OnClickListener {
    private Button back;
    private static final String[] WEEKDAYS = {"Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"};

    private SleepDB dbHelper;
    private GridLayout grid;
    private TextView tvMonth;
    private final Calendar shown = Calendar.getInstance();   // mes que se está mostrando (día 1)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calendar);
        back = (Button) findViewById(R.id.calendarioBackButton);
        back.setOnClickListener(this);

        dbHelper = new SleepDB(this);
        grid = findViewById(R.id.gridCalendar);
        tvMonth = findViewById(R.id.tvMonth);

        // Empezamos en el día 1 del mes actual, a las 00:00
        shown.set(Calendar.DAY_OF_MONTH, 1);
        shown.set(Calendar.HOUR_OF_DAY, 0);
        shown.set(Calendar.MINUTE, 0);
        shown.set(Calendar.SECOND, 0);
        shown.set(Calendar.MILLISECOND, 0);

        ((Button) findViewById(R.id.btnPrevMonth)).setOnClickListener(v -> {
            shown.add(Calendar.MONTH, -1);
            drawMonth();
        });
        ((Button) findViewById(R.id.btnNextMonth)).setOnClickListener(v -> {
            shown.add(Calendar.MONTH, 1);
            drawMonth();
        });

        drawMonth();
    }

    private void drawMonth() {
        int year = shown.get(Calendar.YEAR);
        int month = shown.get(Calendar.MONTH);

        // Título: "Noviembre 2026"
        SimpleDateFormat fmt = new SimpleDateFormat("MMMM yyyy", new Locale("es", "MX"));
        String title = fmt.format(shown.getTime());
        tvMonth.setText(title.substring(0, 1).toUpperCase() + title.substring(1));

        grid.removeAllViews();

        // Fila con los nombres de los días
        for (String name : WEEKDAYS) {
            TextView header = new TextView(this);
            header.setText(name);
            header.setTextSize(12);
            header.setGravity(Gravity.CENTER);
            grid.addView(header, cellParams(dp(44), ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        Calendar day = (Calendar) shown.clone();
        int offset = (day.get(Calendar.DAY_OF_WEEK) + 5) % 7;     // lunes = 0 ... domingo = 6
        int daysInMonth = day.getActualMaximum(Calendar.DAY_OF_MONTH);

        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        long todayMidnight = today.getTimeInMillis();

        // Color de cada día registrado de este mes
        int age = SleepDB.profile(this).getInt("edad", 20);
        Map<Integer, Integer> colors = dbHelper.getMonthColors(year, month, age);

        // Huecos antes del día 1
        for (int i = 0; i < offset; i++) {
            grid.addView(new Space(this), cellParams(dp(44), dp(44)));
        }

        for (int d = 1; d <= daysInMonth; d++) {
            day.set(Calendar.DAY_OF_MONTH, d);
            boolean isFuture = day.getTimeInMillis() > todayMidnight;

            TextView tv = new TextView(this);
            tv.setText(String.valueOf(d));
            tv.setGravity(Gravity.CENTER);

            if (isFuture) {
                tv.setAlpha(0.35f);//dias blquados
            } else if (colors.containsKey(d)) {
                GradientDrawable circle = new GradientDrawable();
                circle.setShape(GradientDrawable.OVAL);
                circle.setColor(colors.get(d));
                tv.setBackground(circle);
                tv.setTextColor(Color.WHITE);

                final int clickedDay = d;
                tv.setOnClickListener(v -> {
                    Calendar clicked = (Calendar) shown.clone();
                    clicked.set(Calendar.DAY_OF_MONTH, clickedDay);

                    Intent intent = new Intent(this, Semana.class);
                    intent.putExtra("dayMillis", clicked.getTimeInMillis());
                    startActivity(intent);
                });
            }

            grid.addView(tv, cellParams(dp(44), dp(44)));
        }
    }

    /** Parámetros de cada celda: columnas del mismo ancho, contenido centrado. */
    private GridLayout.LayoutParams cellParams(int width, int height) {
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1, GridLayout.CENTER),
                GridLayout.spec(GridLayout.UNDEFINED, 1, GridLayout.CENTER, 1f));
        lp.width = width;
        lp.height = height;
        lp.setMargins(0, dp(2), 0, dp(2));
        return lp;
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }

    @Override
    public void onClick(View v) {
        Intent intent = new Intent(this, Inicio.class);
        startActivity(intent);
        finish();
    }
}