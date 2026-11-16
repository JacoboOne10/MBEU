package com.example.mbeu;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Calendar;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.formatter.ValueFormatter;
import android.graphics.Paint;
import android.view.View;
import android.widget.Button;

import com.github.mikephil.charting.charts.CandleStickChart;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.data.CandleData;
import com.github.mikephil.charting.data.CandleDataSet;
import com.github.mikephil.charting.data.CandleEntry;
import java.util.Locale;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

public class Semana extends AppCompatActivity implements View.OnClickListener {
    private static final int GREEN = SleepDB.GREEN;
    private static final int ORANGE = SleepDB.ORANGE;
    private static final int RED = SleepDB.RED;
    private Button btnAtras;
    /** Limite de horas para cada grupo de edad */
    private float[] getThresholds(int age) {
        float idealMin = SleepDB.idealHours(age);
        return new float[]{idealMin, idealMin - 3};
    }

    private void addGroup(CandleData data, List<CandleEntry> entries,
                          List<Integer> textColors, int color) {
        if (entries.isEmpty()) return;
        CandleDataSet set = new CandleDataSet(entries, "");
        set.setIncreasingColor(color);
        set.setDecreasingColor(color);
        set.setNeutralColor(color);
        set.setIncreasingPaintStyle(Paint.Style.FILL);
        set.setDecreasingPaintStyle(Paint.Style.FILL);
        set.setShadowColorSameAsCandle(true);
        set.setBarSpace(0.2f);
        set.setValueTextColors(textColors);        // un color por barra, en el mismo orden
        set.setValueTextSize(10f);
        set.setValueFormatter(new ValueFormatter() {
            @Override
            public String getCandleLabel(CandleEntry candleEntry) {
                Object d = candleEntry.getData();
                return d == null ? "" : d.toString();
            }
        });
        data.addDataSet(set);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_semana);

        btnAtras = (Button) findViewById(R.id.semanaBackButton);
        btnAtras.setOnClickListener(this);

        CandleStickChart barChart = findViewById(R.id.barChart);
        SleepDB dbHelper = new SleepDB(this);

// Lunes de esta semana a las 00:00
        Calendar cal = Calendar.getInstance();
        long dayMillis = getIntent().getLongExtra("dayMillis", -1L);
        if (dayMillis != -1L) cal.setTimeInMillis(dayMillis);   // semana del día tocado
        cal.setFirstDayOfWeek(Calendar.MONDAY);
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        List<float[]> segments = dbHelper.getSegmentsForWeek(cal.getTimeInMillis());

// Por día: total de horas, primera hora de dormir y fin del último tramo
        float[] totals = new float[7];
        float[] firstStart = new float[7];
        float[] lastEnd = new float[7];
        boolean[] hasSegment = new boolean[7];
        for (float[] s : segments) {
            int d = (int) s[0];
            totals[d] += s[2] - s[1];
            if (!hasSegment[d]) {
                firstStart[d] = s[1];
                lastEnd[d] = s[2];
                hasSegment[d] = true;
            } else {
                firstStart[d] = Math.min(firstStart[d], s[1]);
                lastEnd[d] = Math.max(lastEnd[d], s[2]);
            }
        }

        int age = SleepDB.profile(this).getInt("edad", 20);
        float[] limits = getThresholds(age);

// Barras agrupadas por color; cada grupo guarda también el color del texto de sus barras
        List<CandleEntry> greenEntries = new ArrayList<>();
        List<CandleEntry> orangeEntries = new ArrayList<>();
        List<CandleEntry> redEntries = new ArrayList<>();
        List<Integer> greenText = new ArrayList<>();
        List<Integer> orangeText = new ArrayList<>();
        List<Integer> redText = new ArrayList<>();

        for (float[] s : segments) {
            int d = (int) s[0];
            String label = (s[2] == lastEnd[d])
                    ? String.format(Locale.getDefault(), "%.1f", totals[d]) : "";
            CandleEntry e = new CandleEntry(d, s[1], s[2], s[1], s[2], label);

            // Color del texto: según las horas dormidas
            int hoursColor;
            if (totals[d] >= limits[0]) hoursColor = GREEN;
            else if (totals[d] >= limits[1]) hoursColor = ORANGE;
            else hoursColor = RED;

            // Color de la barra: según la hora en que se durmió
            int barColor = SleepDB.bedtimeColor(firstStart[d], age);
            if (barColor == GREEN) {
                greenEntries.add(e);
                greenText.add(hoursColor);
            } else if (barColor == ORANGE) {
                orangeEntries.add(e);
                orangeText.add(hoursColor);
            } else {
                redEntries.add(e);
                redText.add(hoursColor);
            }
        }

        CandleData data = new CandleData();
        addGroup(data, greenEntries, greenText, GREEN);
        addGroup(data, orangeEntries, orangeText, ORANGE);
        addGroup(data, redEntries, redText, RED);
        barChart.setData(data);

// Eje X: los 7 días siempre
        XAxis xAxis = barChart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(
                new String[]{"Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"}));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setAxisMinimum(-0.5f);
        xAxis.setAxisMaximum(6.5f);
        xAxis.setTextColor(Color.WHITE);

// Eje Y: hora del reloj, de 18:00 a 14:00 (0 = medianoche)
        YAxis yAxis = barChart.getAxisLeft();
        yAxis.setInverted(true);          // 20:00 arriba, 12:00 abajo
        yAxis.setAxisMinimum(-4f);        // 20:00
        yAxis.setAxisMaximum(12f);        // 12:00
        yAxis.setLabelCount(9, true);     // una marca cada 2 horas
        yAxis.setTextColor(Color.WHITE);
        yAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int hour = (((int) value % 24) + 24) % 24;
                return hour + ":00";
            }
        });

// Línea punteada en las 0:00
        LimitLine midnight = new LimitLine(0f);
        midnight.setLineColor(Color.WHITE);
        midnight.setLineWidth(1f);
        midnight.enableDashedLine(12f, 12f, 0f);
        yAxis.addLimitLine(midnight);
        yAxis.setDrawLimitLinesBehindData(true);

        barChart.getAxisRight().setEnabled(false);
        barChart.getDescription().setEnabled(false);
        barChart.getLegend().setEnabled(false);

// Sin zoom ni arrastre
        barChart.setScaleEnabled(false);
        barChart.setPinchZoom(false);
        barChart.setDoubleTapToZoomEnabled(false);
        barChart.setDragEnabled(false);
        barChart.setHighlightPerTapEnabled(false);    // sin la cruz al tocar
        barChart.setHighlightPerDragEnabled(false);
        barChart.setExtraTopOffset(20f);              // espacio arriba para el total de horas

        barChart.invalidate();

        // ---------- Gráfica de líneas: horas dormidas por día ----------
        LineChart lineChart = findViewById(R.id.lineChart);

        List<Entry> lineEntries = new ArrayList<>();
        for (int d = 0; d < 7; d++) {
            if (hasSegment[d]) {                          // los días sin registro no ponen punto
                lineEntries.add(new Entry(d, totals[d]));
            }
        }

        LineDataSet lineSet = new LineDataSet(lineEntries, "Horas dormidas");
        lineSet.setColor(Color.WHITE);
        lineSet.setCircleColor(Color.WHITE);
        lineSet.setLineWidth(2f);
        lineSet.setDrawValues(false);                     // sin números sobre los puntos
        lineChart.setData(new LineData(lineSet));

        XAxis lineX = lineChart.getXAxis();
        lineX.setValueFormatter(new IndexAxisValueFormatter(
                new String[]{"Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"}));
        lineX.setPosition(XAxis.XAxisPosition.BOTTOM);
        lineX.setGranularity(1f);
        lineX.setAxisMinimum(-0.5f);
        lineX.setAxisMaximum(6.5f);
        lineX.setTextColor(Color.WHITE);

        YAxis lineY = lineChart.getAxisLeft();
        lineY.setAxisMinimum(0f);
        lineY.setGranularity(1f);
        lineY.setTextColor(Color.WHITE);
        lineY.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return (int) value + " h";
            }
        });

        lineChart.getAxisRight().setEnabled(false);
        lineChart.getDescription().setEnabled(false);
        lineChart.getLegend().setEnabled(false);
        lineChart.setScaleEnabled(false);
        lineChart.setPinchZoom(false);
        lineChart.setDoubleTapToZoomEnabled(false);
        lineChart.setDragEnabled(false);
        lineChart.setHighlightPerTapEnabled(false);
        lineChart.invalidate();
    }

    @Override
    public void onClick(View v) {
        Intent intent = new Intent(this, Calendario.class);
        startActivity(intent);
        finish();
    }
}
