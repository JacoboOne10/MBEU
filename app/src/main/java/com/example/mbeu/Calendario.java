package com.example.mbeu;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.github.mikephil.charting.charts.CandleStickChart;
import com.github.mikephil.charting.charts.LineChart;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Calendario extends AppCompatActivity implements View.OnClickListener {
    private Button back;
    private static final String[] WEEKDAYS = {"Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"};

    private SleepDB dbHelper;
    private GridLayout grid;
    private TextView tvMonth;
    private TextView tvWeekRange;
    private Button btnPrevWeek, btnNextWeek;
    private CandleStickChart barChart;
    private LineChart lineChart;
    private final Calendar shown = Calendar.getInstance();   // mes que se está mostrando (día 1)
    private long selectedDayMillis = System.currentTimeMillis();   // semana que muestran las gráficas

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calendar);
        back = (Button) findViewById(R.id.calendarioBackButton);
        back.setOnClickListener(this);

        dbHelper = new SleepDB(this);
        grid = findViewById(R.id.gridCalendar);
        tvMonth = findViewById(R.id.tvMonth);
        tvWeekRange = findViewById(R.id.tvWeekRange);
        btnPrevWeek = findViewById(R.id.btnPrevWeek);
        btnNextWeek = findViewById(R.id.btnNextWeek);
        barChart = findViewById(R.id.barChart);
        lineChart = findViewById(R.id.lineChart);

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

        // Cambiar de semana en el desglose semanal
        btnPrevWeek.setOnClickListener(v -> shiftWeek(-7));
        btnNextWeek.setOnClickListener(v -> shiftWeek(7));
    }

    // Se dibuja aquí (y no solo en onCreate) para que todo se actualice al volver
    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    /** Redibuja el calendario y las gráficas de la semana elegida. */
    private void refresh() {
        drawMonth();
        drawWeek();
    }

    /** Mueve la semana de las gráficas 7 días hacia atrás o hacia adelante. */
    private void shiftWeek(int days) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(selectedDayMillis);
        c.add(Calendar.DAY_OF_YEAR, days);
        selectedDayMillis = c.getTimeInMillis();
        drawWeek();
    }

    /** Gráficas del desglose semanal (debajo del calendario) de la semana elegida. */
    private void drawWeek() {
        GraficasSemana.draw(this, barChart, lineChart, selectedDayMillis);

        // Texto con la semana: "16 nov - 22 nov"
        Calendar monday = Calendar.getInstance();
        monday.setTimeInMillis(selectedDayMillis);
        monday.setFirstDayOfWeek(Calendar.MONDAY);
        monday.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        Calendar sunday = (Calendar) monday.clone();
        sunday.add(Calendar.DAY_OF_YEAR, 6);
        SimpleDateFormat fmt = new SimpleDateFormat("d MMM", new Locale("es", "MX"));
        tvWeekRange.setText(fmt.format(monday.getTime()) + " - " + fmt.format(sunday.getTime()));

        // No hay semanas futuras: la flecha derecha se apaga en la semana actual
        Calendar now = Calendar.getInstance();
        int sundayKey = sunday.get(Calendar.YEAR) * 10000
                + (sunday.get(Calendar.MONTH) + 1) * 100 + sunday.get(Calendar.DAY_OF_MONTH);
        int todayKey = now.get(Calendar.YEAR) * 10000
                + (now.get(Calendar.MONTH) + 1) * 100 + now.get(Calendar.DAY_OF_MONTH);
        boolean canGoNext = sundayKey < todayKey;
        btnNextWeek.setEnabled(canGoNext);
        btnNextWeek.setAlpha(canGoNext ? 1f : 0.4f);
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
                // Toque: se abre el cuadro para editar o eliminar las horas de ese día
                tv.setOnClickListener(v -> {
                    Calendar clicked = (Calendar) shown.clone();
                    clicked.set(Calendar.DAY_OF_MONTH, clickedDay);
                    showDay(clicked);
                });
            }

            grid.addView(tv, cellParams(dp(44), dp(44)));
        }
    }

    /** Cuadro del día: a qué hora dormiste y despertaste, con botones para editar o eliminar. */
    private void showDay(Calendar day) {
        List<long[]> nights = dbHelper.getSleepsForDay(day.getTimeInMillis());
        if (nights.isEmpty()) {
            Toast.makeText(this, "Sin registro este día", Toast.LENGTH_SHORT).show();
            return;
        }

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_dia_sueno, null);
        String title = new SimpleDateFormat("EEEE d 'de' MMMM", new Locale("es", "MX"))
                .format(day.getTime());
        ((TextView) view.findViewById(R.id.tvDayTitle))
                .setText(title.substring(0, 1).toUpperCase() + title.substring(1));
        LinearLayout container = view.findViewById(R.id.nightsContainer);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(view)
                .setBackground(ContextCompat.getDrawable(this, R.drawable.bg_dialog))
                .create();

        for (long[] night : nights) {
            View row = LayoutInflater.from(this).inflate(R.layout.night_item, container, false);
            float hours = (night[2] - night[1]) / 3600000f;
            ((TextView) row.findViewById(R.id.tvNightDay))
                    .setText("Dormiste a las " + formatTime(night[1]));
            ((TextView) row.findViewById(R.id.tvNightTimes)).setText(
                    "Despertaste a las " + formatTime(night[2])
                            + "  ·  " + String.format(Locale.getDefault(), "%.1f h", hours));

            // Después de editar o eliminar se redibujan el calendario y las gráficas
            row.findViewById(R.id.btnEdit).setOnClickListener(v -> {
                dialog.dismiss();
                ConfirmarSueno.edit(this, night, this::refresh);
            });
            row.findViewById(R.id.btnDelete).setOnClickListener(v -> {
                dialog.dismiss();
                ConfirmarSueno.delete(this, night, this::refresh);
            });
            container.addView(row);
        }

        view.findViewById(R.id.btnCerrar).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private String formatTime(long millis) {
        return new SimpleDateFormat("h:mm a", new Locale("es", "MX")).format(new Date(millis));
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