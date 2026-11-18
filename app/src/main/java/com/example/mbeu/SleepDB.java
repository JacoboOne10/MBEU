package com.example.mbeu;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Color;
import java.text.ParseException;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

public class SleepDB extends SQLiteOpenHelper {

    private static final String DB_NAME = "sueno.db";
    private static final int DB_VERSION = 5;
    private static final SimpleDateFormat DATE_FMT =
            new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    // Colores y regla compartidos por la gráfica y el calendario
    public static final int GREEN = Color.parseColor("#4CAF50");
    public static final int ORANGE = Color.parseColor("#FF9800");
    public static final int RED = Color.parseColor("#F44336");
    public static final int CHILD_MAX_AGE = 12;  //niño
    public static final float CHILD_GREEN_LIMIT = -3f;     //21 (9 p.m.)
    public static final float ADULT_GREEN_LIMIT = -1f;     //23 (11 p.m.)
    public static final float ORANGE_EXTRA = 2f;

    /**Color de barra segin la hora en que se durmió y la edad.*/
    public static int bedtimeColor(float startHours, int age) {
        float greenLimit = (age <= CHILD_MAX_AGE) ? CHILD_GREEN_LIMIT : ADULT_GREEN_LIMIT;
        if (startHours <= greenLimit) return GREEN;
        if (startHours <= greenLimit + ORANGE_EXTRA) return ORANGE;
        return RED;
    }

    /** Color de cada día con registro en un mes. Clave = número del día (1 a 31).
     *  month va como en Calendar: 0 = enero ... 11 = diciembre. Los días sin registro no aparecen. */
    public Map<Integer, Integer> getMonthColors(int year, int month, int age) {
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(year, month, 1);
        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        String first = DATE_FMT.format(cal.getTime());
        cal.set(Calendar.DAY_OF_MONTH, daysInMonth);
        String last = DATE_FMT.format(cal.getTime());

        Map<Integer, Integer> colors = new HashMap<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT date, MIN(sleep_time) FROM sleep_data "
                        + "WHERE date BETWEEN ? AND ? GROUP BY date",
                new String[]{first, last});
        while (c.moveToNext()) {
            try {
                String date = c.getString(0);
                Date midnight = DATE_FMT.parse(date);
                float start = (c.getLong(1) - midnight.getTime()) / 3600000f;
                int dayOfMonth = Integer.parseInt(date.substring(8, 10));
                colors.put(dayOfMonth, bedtimeColor(start, age));
            } catch (ParseException e) {
                // fecha mal escrita en la base: se ignora ese registro
            }
        }
        c.close();
        return colors;
    }
    public SleepDB(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE sleep_data ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "date TEXT NOT NULL,"
                + "sleep_time INTEGER NOT NULL,"
                + "wake_time INTEGER NOT NULL)");
        insertSampleData(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS sleep_data");
        onCreate(db);
    }
    private void insertSampleData(SQLiteDatabase db) {
        // Índice = hace cuántos días (0 = hoy, 1 = ayer...).
        // {hora de dormir, hora de despertar} en horas respecto a la medianoche del día en que despertó.
        float[][] nights = {
                {-1.5f, 6.5f},   // hoy        cumple
                {-2f, 6.5f},     // ayer       cumple
                {-1.2f, 6.4f},   // hace 2     cumple
                {-1f, 6.5f},     // hace 3     cumple
                {1f, 9f},        // hace 4     no cumple: se durmió tarde
                {-1.5f, 6.5f},   // hace 5     cumple
                {-0.5f, 6f},     // hace 6     no cumple: tarde y pocas horas
                {-1f, 8f},       // hace 7     (se reemplaza abajo por una noche partida)
                {2.5f, 6f},      // hace 8     no cumple
                {-2f, 7f},       // hace 9     cumple
                {0.2f, 4.2f},    // hace 10    no cumple
                {-1.5f, 7f},     // hace 11    cumple
                {3f, 7.5f},      // hace 12    no cumple
                {-1f, 8f}        // hace 13    cumple
        };

        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);

        for (int daysAgo = 0; daysAgo < nights.length; daysAgo++) {
            Calendar day = (Calendar) today.clone();
            day.add(Calendar.DAY_OF_YEAR, -daysAgo);
            long midnight = day.getTimeInMillis();
            String date = DATE_FMT.format(day.getTime());

            if (daysAgo == 7) {
                // Noche con una interrupción: dos tramos
                insertRow(db, date, ms(midnight, -1f), ms(midnight, 2f));
                insertRow(db, date, ms(midnight, 2.7f), ms(midnight, 8f));
            } else {
                long wakeMs = ms(midnight, nights[daysAgo][1]);
                if (wakeMs > System.currentTimeMillis()) continue;   // hoy: no inventar un despertar que aún no pasa
                insertRow(db, date, ms(midnight, nights[daysAgo][0]), wakeMs);
            }
        }
    }

    private long ms(long midnight, float hours) {
        return midnight + (long) (hours * 3600000.0);
    }

    private long insertRow(SQLiteDatabase db, String date, long sleepMs, long wakeMs) {
        ContentValues v = new ContentValues();
        v.put("date", date);
        v.put("sleep_time", sleepMs);
        v.put("wake_time", wakeMs);
        return db.insert("sleep_data", null, v);
    }

    // Devuelve semana (7 vals) con el total de horas de cada día, 0 si no hay datos
    public float[] getHoursForWeek(long mondayMillis) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(mondayMillis);
        String[] days = new String[7];
        for (int i = 0; i < 7; i++) {
            days[i] = DATE_FMT.format(cal.getTime());
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }

        Map<String, Float> totals = new HashMap<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT date, SUM(wake_time - sleep_time) / 3600000.0 "
                        + "FROM sleep_data WHERE date BETWEEN ? AND ? GROUP BY date",
                new String[]{days[0], days[6]});
        while (c.moveToNext()) {
            totals.put(c.getString(0), c.getFloat(1));
        }
        c.close();

        float[] result = new float[7];
        for (int i = 0; i < 7; i++) {
            Float h = totals.get(days[i]);
            result[i] = (h == null) ? 0f : h;
        }
        return result;
    }

    /** Cada tramo de sueño de la semana como {índiceDía (0 = lunes), inicio, fin}.
     *  Inicio y fin van en horas respecto a la medianoche del día en que despertó
     *  (negativo = antes de las 0:00). */
    public List<float[]> getSegmentsForWeek(long mondayMillis) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(mondayMillis);
        String[] days = new String[7];
        long[] midnights = new long[7];
        Map<String, Integer> dayIndex = new HashMap<>();
        for (int i = 0; i < 7; i++) {
            days[i] = DATE_FMT.format(cal.getTime());
            midnights[i] = cal.getTimeInMillis();
            dayIndex.put(days[i], i);
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }

        List<float[]> result = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT date, sleep_time, wake_time FROM sleep_data "
                        + "WHERE date BETWEEN ? AND ? ORDER BY date, sleep_time",
                new String[]{days[0], days[6]});
        while (c.moveToNext()) {
            int i = dayIndex.get(c.getString(0));
            float start = (c.getLong(1) - midnights[i]) / 3600000f;
            float end = (c.getLong(2) - midnights[i]) / 3600000f;
            result.add(new float[]{i, start, end});
        }
        c.close();
        return result;
    }

    /** Noches de la semana como {id, inicio, fin} en milisegundos, en orden. */
    public List<long[]> getSleepsForWeek(long mondayMillis) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(mondayMillis);
        String first = DATE_FMT.format(cal.getTime());
        cal.add(Calendar.DAY_OF_YEAR, 6);
        String last = DATE_FMT.format(cal.getTime());

        List<long[]> result = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, sleep_time, wake_time FROM sleep_data "
                        + "WHERE date BETWEEN ? AND ? ORDER BY sleep_time",
                new String[]{first, last});
        while (c.moveToNext()) {
            result.add(new long[]{c.getLong(0), c.getLong(1), c.getLong(2)});
        }
        c.close();
        return result;
    }

    /** Noches de un día (el día en que despertó) como {id, inicio, fin} en milisegundos. */
    public List<long[]> getSleepsForDay(long dayMillis) {
        List<long[]> result = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, sleep_time, wake_time FROM sleep_data "
                        + "WHERE date = ? ORDER BY sleep_time",
                new String[]{DATE_FMT.format(new Date(dayMillis))});
        while (c.moveToNext()) {
            result.add(new long[]{c.getLong(0), c.getLong(1), c.getLong(2)});
        }
        c.close();
        return result;
    }

    /** Horas de sueño ideales (mínimo) según la edad. */
    public static float idealHours(int age) {
        if (age <= 2) return 11;
        if (age <= 5) return 10;
        if (age <= 13) return 9;
        if (age <= 17) return 8;
        return 7;
    }
    /*Racha */
    public int getStreak(int age) {
        Map<String, Boolean> passes = new HashMap<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT date, MIN(sleep_time), SUM(wake_time - sleep_time) "
                        + "FROM sleep_data GROUP BY date", null);
        while (c.moveToNext()) {
            try {
                String date = c.getString(0);
                long midnight = DATE_FMT.parse(date).getTime();
                float start = (c.getLong(1) - midnight) / 3600000f;   // primera hora de dormir
                float hours = c.getLong(2) / 3600000f;                // total del día
                boolean ok = bedtimeColor(start, age) == GREEN && hours >= idealHours(age);
                passes.put(date, ok);
            } catch (ParseException e) {
            }
        }
        c.close();
        //dias previos, hoy no rompe la racha
        Calendar day = Calendar.getInstance();
        if (!passes.containsKey(DATE_FMT.format(day.getTime()))) {
            day.add(Calendar.DAY_OF_YEAR, -1);   // hoy todavía no tiene registro: se cuenta desde ayer
        }

        int streak = 0;
        while (true) {
            Boolean ok = passes.get(DATE_FMT.format(day.getTime()));
            if (ok == null || !ok) break;        // sin registro o no cumplió: se acaba la racha
            streak++;
            day.add(Calendar.DAY_OF_YEAR, -1);
        }
        return streak;
    }
    /** Correo e info de la cuenta */
    public static SharedPreferences session(Context context) {
        return context.getSharedPreferences("sesion", Context.MODE_PRIVATE);
    }
    public static SharedPreferences profile(Context context) {
        String email = session(context).getString("email", "").toLowerCase();
        return context.getSharedPreferences("perfil_" + email, Context.MODE_PRIVATE);
    }
    /*Detectar sueño por partes*/
    /** Guarda un sueño detectado. Devuelve el id del registro, o -1 si se traslapa con otro. */
    public long addSleep(long sleepMs, long wakeMs) {
        SQLiteDatabase db = getWritableDatabase();
        if (overlaps(db, sleepMs, wakeMs, -1)) return -1;
        return insertRow(db, DATE_FMT.format(new Date(wakeMs)), sleepMs, wakeMs);
    }

    /** Cambia las horas de un registro. Devuelve false si se traslapa con otro registro. */
    public boolean updateSleep(long id, long sleepMs, long wakeMs) {
        SQLiteDatabase db = getWritableDatabase();
        if (overlaps(db, sleepMs, wakeMs, id)) return false;
        ContentValues v = new ContentValues();
        v.put("date", DATE_FMT.format(new Date(wakeMs)));
        v.put("sleep_time", sleepMs);
        v.put("wake_time", wakeMs);
        return db.update("sleep_data", v, "id = ?", new String[]{String.valueOf(id)}) > 0;
    }

    /** Borra un registro de sueño. */
    public void deleteSleep(long id) {
        getWritableDatabase().delete("sleep_data", "id = ?", new String[]{String.valueOf(id)});
    }

    /** ¿Hay otro registro (distinto de ignoreId) que se traslape con este horario? */
    private boolean overlaps(SQLiteDatabase db, long sleepMs, long wakeMs, long ignoreId) {
        Cursor c = db.rawQuery(
                "SELECT 1 FROM sleep_data WHERE sleep_time < ? AND wake_time > ? AND id != ? LIMIT 1",
                new String[]{String.valueOf(wakeMs), String.valueOf(sleepMs), String.valueOf(ignoreId)});
        boolean result = c.moveToFirst();
        c.close();
        return result;
    }

    /*CSV*/
    public String buildCsv() {
        SimpleDateFormat dateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        StringBuilder sb = new StringBuilder("fecha,hora_dormir,hora_despertar,horas_totales\n");

        Cursor c = getReadableDatabase().rawQuery(
                "SELECT date, sleep_time, wake_time FROM sleep_data ORDER BY sleep_time", null);
        while (c.moveToNext()) {
            long sleep = c.getLong(1);
            long wake = c.getLong(2);
            sb.append(c.getString(0)).append(',')
                    .append(dateTime.format(new Date(sleep))).append(',')
                    .append(dateTime.format(new Date(wake))).append(',')
                    .append(String.format(Locale.US, "%.2f", (wake - sleep) / 3600000.0))
                    .append('\n');
        }
        c.close();
        return sb.toString();
    }
}