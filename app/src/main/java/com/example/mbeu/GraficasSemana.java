package com.example.mbeu;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;

import com.github.mikephil.charting.charts.CandleStickChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.CandleData;
import com.github.mikephil.charting.data.CandleDataSet;
import com.github.mikephil.charting.data.CandleEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Dibuja las gráficas del desglose semanal debajo del calendario:
 * barras con el horario de cada noche y líneas con las horas dormidas por día.
 */
public class GraficasSemana {
    private static final int GREEN = SleepDB.GREEN;
    private static final int ORANGE = SleepDB.ORANGE;
    private static final int RED = SleepDB.RED;
    private static final String[] DAYS = {"Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"};

    /** Limite de horas para cada grupo de edad */
    private static float[] getThresholds(int age) {
        float idealMin = SleepDB.idealHours(age);
        return new float[]{idealMin, idealMin - 3};
    }

    private static void addGroup(CandleData data, List<CandleEntry> entries,
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

    /** Dibuja la semana (de lunes a domingo) que contiene el día dayMillis. */
    public static void draw(Context context, CandleStickChart barChart, LineChart lineChart,
                            long dayMillis) {
        SleepDB dbHelper = new SleepDB(context);

        // Lunes de esa semana a las 00:00
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(dayMillis);
        cal.setFirstDayOfWeek(Calendar.MONDAY);
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        List<float[]> segments = dbHelper.getSegmentsForWeek(cal.getTimeInMillis());

        // Semana sin registros: se limpian las gráficas y se avisa
        if (segments.isEmpty()) {
            for (com.github.mikephil.charting.charts.Chart<?> chart
                    : new com.github.mikephil.charting.charts.Chart<?>[]{barChart, lineChart}) {
                chart.clear();
                chart.setNoDataText("Sin registros esta semana");
                chart.setNoDataTextColor(Color.WHITE);
                chart.invalidate();
            }
            return;
        }

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

        int age = SleepDB.profile(context).getInt("edad", 20);
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
        xAxis.setValueFormatter(new IndexAxisValueFormatter(DAYS));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setAxisMinimum(-0.5f);
        xAxis.setAxisMaximum(6.5f);
        xAxis.setTextColor(Color.WHITE);

        // Eje Y: hora del reloj, de 20:00 a 12:00 (0 = medianoche)
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

        // Línea punteada en las 0:00 (se quitan las anteriores para no repetirla al redibujar)
        yAxis.removeAllLimitLines();
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
        lineX.setValueFormatter(new IndexAxisValueFormatter(DAYS));
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
}