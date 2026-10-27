package com.example.bakeryapp;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

final class DebugData {
    private static final String TAG = "DebugData";
    private static final String[] NAMES = {
            "Somun Ekmek", "Tam Buğday", "Ekşi Mayalı", "Çavdar", "Susamlı", "Kepekli", "Baget"
    };
    private static final int[] DAILY = {180, 65, 48, 38, 75, 42, 54};

    private DebugData() {}

    static void initialize(DBHelper helper, ExecutorService worker) {
        worker.execute(() -> {
            try {
                seedIfEmpty(helper);
            } catch (Exception e) {
                Log.e(TAG, "Could not create sample records", e);
            }
        });
    }

    static boolean seedIfEmpty(DBHelper helper) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            try (Cursor count = db.rawQuery(
                    "SELECT (SELECT COUNT(*) FROM BreadType) + (SELECT COUNT(*) FROM DailyStock) + (SELECT COUNT(*) FROM SalesRecord)", null)) {
                count.moveToFirst();
                if (count.getLong(0) != 0) {
                    completeSyntheticHistory(db);
                    db.setTransactionSuccessful();
                    return false;
                }
            }

            long[] ids = new long[NAMES.length];
            for (int i = 0; i < NAMES.length; i++) {
                ContentValues type = new ContentValues();
                type.put("name", NAMES[i]);
                type.put("daily_total_quantity", DAILY[i]);
                ids[i] = db.insertOrThrow("BreadType", null, type);
            }

            Calendar day = Calendar.getInstance();
            day.add(Calendar.MONTH, -5);
            String end = Dates.today();
            String lastMonday = Dates.plusDays(Dates.weekStart(end),-7);
            String lastSunday = Dates.plusDays(lastMonday,6);
            SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
            int index = 0;
            while (true) {
                String date = iso.format(day.getTime());
                if (date.compareTo(end) > 0) break;
                int weekday = day.get(Calendar.DAY_OF_WEEK);
                if (weekday != Calendar.SUNDAY) {
                    for (int bread = 0; bread < NAMES.length; bread++) {
                        if (bread == 5 && index >= 68) continue;
                        if (bread == 6 && index < 72) continue;
                        int variation = (index * 7 + bread * 11) % 17 - 8;
                        int production = DAILY[bread] + variation + (weekday == Calendar.SATURDAY ? 15 : 0);
                        int rate = 71 + (index * 3 + bread * 7) % 25;
                        if (index % 31 == 0) rate = 52;
                        if (bread == 3 && (weekday == Calendar.MONDAY || weekday == Calendar.THURSDAY))
                            rate = 25 + index % 5;
                        if (bread == 4 && date.compareTo(lastMonday)>=0 && date.compareTo(lastSunday)<=0)
                            rate = 48;
                        int sold = production * rate / 100;

                        ContentValues stock = new ContentValues();
                        stock.put("bread_type_id", ids[bread]);
                        stock.put("date", date);
                        stock.put("name", NAMES[bread]);
                        stock.put("produced", production);
                        stock.put("estimated", 0);
                        db.insertOrThrow("DailyStock", null, stock);

                        int morning = sold / 3;
                        insertSale(db, ids[bread], date, morning);
                        insertSale(db, ids[bread], date, sold - morning);
                    }
                }
                index++;
                day.add(Calendar.DATE, 1);
            }
            ContentValues archived = new ContentValues();
            archived.put("archived", 1);
            db.update("BreadType", archived, "id=?", new String[]{Long.toString(ids[5])});
            completeSyntheticHistory(db);
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    private static void completeSyntheticHistory(SQLiteDatabase db) {
        try(Cursor c=db.rawQuery("SELECT (SELECT COUNT(*) FROM DailyStock),(SELECT COUNT(*) FROM DayClose)",null)) {
            c.moveToFirst();
            if(c.getLong(0)<500 || c.getLong(1)!=0)return;
        }
        db.execSQL("INSERT INTO DayClose(date,status,closed_at) SELECT DISTINCT date,1,? FROM DailyStock WHERE date<?",
                new Object[]{Dates.today(),Dates.today()});
    }

    private static void insertSale(SQLiteDatabase db, long id, String date, int amount) {
        if (amount <= 0) return;
        ContentValues sale = new ContentValues();
        sale.put("bread_type_id", id);
        sale.put("date", date);
        sale.put("quantity_sold", amount);
        db.insertOrThrow("SalesRecord", null, sale);
    }
}
