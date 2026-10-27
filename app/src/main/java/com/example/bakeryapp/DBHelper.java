package com.example.bakeryapp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;

public class DBHelper extends SQLiteOpenHelper {
    public static final String DB_NAME = "BakeryStock.db";
    public static final int DB_VERSION = 7;
    public static final int DAY_OPEN=0, DAY_COMPLETE=1, DAY_NO_PRODUCTION=2;
    public static final int MAX_NAME_LENGTH=80;
    public static String nameKey(String name) {
        return name.trim().toLowerCase(Dates.TURKISH).replace('ı','i');
    }
    public DBHelper(Context context) { super(context, DB_NAME, null, DB_VERSION); }
    DBHelper(Context context, String databaseName) { super(context, databaseName, null, DB_VERSION); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE BreadType (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, daily_total_quantity INTEGER NOT NULL CHECK(daily_total_quantity>=0), archived INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE SalesRecord (id INTEGER PRIMARY KEY AUTOINCREMENT, bread_type_id INTEGER NOT NULL, date TEXT NOT NULL, quantity_sold INTEGER NOT NULL CHECK(quantity_sold>0))");
        createStock(db);
        createPlans(db);
        createFollowUp(db);
        createDayClose(db);
        createSaleHistory(db);
    }
    private void createStock(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE DailyStock (bread_type_id INTEGER NOT NULL, date TEXT NOT NULL, name TEXT NOT NULL, produced INTEGER NOT NULL CHECK(produced>=0), estimated INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(bread_type_id,date))");
        db.execSQL("CREATE INDEX sales_date_bread ON SalesRecord(date,bread_type_id)");
        db.execSQL("CREATE INDEX stock_date ON DailyStock(date)");
    }
    private void createPlans(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE BreadPlan (bread_type_id INTEGER NOT NULL, weekday INTEGER NOT NULL CHECK(weekday BETWEEN 1 AND 7), quantity INTEGER NOT NULL CHECK(quantity>=0), updated_on TEXT NOT NULL, PRIMARY KEY(bread_type_id,weekday))");
        db.execSQL("ALTER TABLE DailyStock ADD COLUMN planned INTEGER CHECK(planned>=0)");
    }
    private void createFollowUp(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE DailyStock ADD COLUMN sold_out_early INTEGER NOT NULL DEFAULT 0 CHECK(sold_out_early IN (0,1))");
        db.execSQL("CREATE TABLE PlanChange (id INTEGER PRIMARY KEY AUTOINCREMENT, bread_type_id INTEGER NOT NULL, weekday INTEGER NOT NULL, old_quantity INTEGER NOT NULL, new_quantity INTEGER NOT NULL, changed_on TEXT NOT NULL)");
        db.execSQL("CREATE INDEX plan_change_bread_day ON PlanChange(bread_type_id,weekday,id)");
    }
    private void createDayClose(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE DayClose (date TEXT PRIMARY KEY, status INTEGER NOT NULL CHECK(status IN (1,2)), closed_at TEXT NOT NULL)");
    }
    private void createSaleHistory(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE SalesRecord ADD COLUMN recorded_at TEXT");
        db.execSQL("CREATE TABLE CancelledSale (id INTEGER PRIMARY KEY AUTOINCREMENT, sale_id INTEGER NOT NULL, bread_type_id INTEGER NOT NULL, date TEXT NOT NULL, quantity_sold INTEGER NOT NULL, recorded_at TEXT, cancelled_at TEXT NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE BreadType ADD COLUMN archived INTEGER NOT NULL DEFAULT 0");
            createStock(db);
            db.execSQL("INSERT INTO DailyStock(bread_type_id,date,name,produced,estimated) SELECT s.bread_type_id,s.date,COALESCE(b.name,'#' || s.bread_type_id),MAX(0,COALESCE(b.daily_total_quantity,0)),1 FROM SalesRecord s LEFT JOIN BreadType b ON s.bread_type_id=b.id GROUP BY s.bread_type_id,s.date");
        }
        if (oldVersion < 4) createPlans(db);
        if (oldVersion < 5) createFollowUp(db);
        if (oldVersion < 6) createDayClose(db);
        if (oldVersion < 7) createSaleHistory(db);
    }
    public int dayStatus(String date) {
        Dates.validate(date);
        try(Cursor c=getReadableDatabase().rawQuery("SELECT status FROM DayClose WHERE date=?",new String[]{date})) {
            return c.moveToFirst()?c.getInt(0):DAY_OPEN;
        }
    }
    public boolean setDayStatus(String date,int status) {
        Dates.validate(date);
        if(date.compareTo(Dates.today())>0 || status<DAY_OPEN || status>DAY_NO_PRODUCTION)return false;
        SQLiteDatabase database=getWritableDatabase(); database.beginTransaction();
        try {
            if(status==DAY_COMPLETE) {
                List<StockRow> rows=report(date,date);
                boolean produced=false;
                for(StockRow row:rows) {if(!row.hasConfirmedStock())return false; if(row.produced>0)produced=true;}
                if(!produced)return false;
            }
            if(status==DAY_NO_PRODUCTION) {
                try(Cursor c=database.rawQuery("SELECT 1 FROM DailyStock WHERE date=? AND produced>0 UNION SELECT 1 FROM SalesRecord WHERE date=? LIMIT 1",
                        new String[]{date,date})) {if(c.moveToFirst())return false;}
            }
            if(status!=DAY_OPEN) {
                try(Cursor c=database.rawQuery("SELECT 1 FROM SalesRecord s LEFT JOIN DailyStock d ON d.bread_type_id=s.bread_type_id AND d.date=s.date WHERE s.date=? AND (d.bread_type_id IS NULL OR s.quantity_sold IS NULL OR s.quantity_sold<=0) LIMIT 1",
                        new String[]{date})) {if(c.moveToFirst())return false;}
            }
            if(status==DAY_OPEN)database.delete("DayClose","date=?",new String[]{date});
            else {
                ContentValues values=new ContentValues(); values.put("date",date); values.put("status",status);
                values.put("closed_at",Dates.today());
                database.insertWithOnConflict("DayClose",null,values,SQLiteDatabase.CONFLICT_REPLACE);
            }
            database.setTransactionSuccessful(); return true;
        } finally {database.endTransaction();}
    }
    public int[] weeklyPlan(int id) {
        SQLiteDatabase database=getReadableDatabase();
        int[] plan=new int[8];
        try(Cursor c=database.rawQuery("SELECT daily_total_quantity FROM BreadType WHERE id=? AND archived=0",new String[]{String.valueOf(id)})) {
            if(!c.moveToFirst()) return null;
            for(int day=1;day<=7;day++) plan[day]=c.getInt(0);
        }
        try(Cursor c=database.rawQuery("SELECT weekday,quantity FROM BreadPlan WHERE bread_type_id=?",new String[]{String.valueOf(id)})) {
            while(c.moveToNext()) plan[c.getInt(0)]=c.getInt(1);
        }
        return plan;
    }
    public boolean setDayPlan(int id,int weekday,int quantity) {
        if(weekday<1 || weekday>7 || quantity<0) return false;
        SQLiteDatabase database=getWritableDatabase(); database.beginTransaction();
        try {
            int base;
            try(Cursor c=database.rawQuery("SELECT daily_total_quantity FROM BreadType WHERE id=? AND archived=0",new String[]{String.valueOf(id)})) {
                if(!c.moveToFirst()) return false;
                base=c.getInt(0);
            }
            int old=base;
            try(Cursor c=database.rawQuery("SELECT quantity FROM BreadPlan WHERE bread_type_id=? AND weekday=?",
                    new String[]{String.valueOf(id),String.valueOf(weekday)})) {
                if(c.moveToFirst()) old=c.getInt(0);
            }
            if(old==quantity) { database.setTransactionSuccessful(); return true; }
            String[] key={String.valueOf(id),String.valueOf(weekday)};
            if(quantity==base) database.delete("BreadPlan","bread_type_id=? AND weekday=?",key);
            else {
                ContentValues values=new ContentValues(); values.put("bread_type_id",id);
                values.put("weekday",weekday); values.put("quantity",quantity); values.put("updated_on",Dates.today());
                database.insertWithOnConflict("BreadPlan",null,values,SQLiteDatabase.CONFLICT_REPLACE);
            }
            ContentValues change=new ContentValues(); change.put("bread_type_id",id); change.put("weekday",weekday);
            change.put("old_quantity",old); change.put("new_quantity",quantity); change.put("changed_on",Dates.today());
            database.insertOrThrow("PlanChange",null,change);
            database.setTransactionSuccessful(); return true;
        } finally { database.endTransaction(); }
    }
    private int plannedQuantity(SQLiteDatabase database,int id,String date) {
        int weekday=ProductionAdvisor.dayOfWeek(date);
        try(Cursor c=database.rawQuery("SELECT COALESCE((SELECT quantity FROM BreadPlan WHERE bread_type_id=? AND weekday=?),daily_total_quantity) FROM BreadType WHERE id=? AND archived=0",
                new String[]{String.valueOf(id),String.valueOf(weekday),String.valueOf(id)})) {
            if(!c.moveToFirst()) throw new IllegalArgumentException("Unknown bread type");
            return c.getInt(0);
        }
    }
    public int recordedPlan(int id,String date) {
        Dates.validate(date);
        try(Cursor c=getReadableDatabase().rawQuery("SELECT planned FROM DailyStock WHERE bread_type_id=? AND date=?",new String[]{String.valueOf(id),date})) {
            return c.moveToFirst() && !c.isNull(0)?c.getInt(0):-1;
        }
    }
    public boolean earlySellout(int id,String date) {
        Dates.validate(date);
        try(Cursor c=getReadableDatabase().rawQuery("SELECT sold_out_early FROM DailyStock WHERE bread_type_id=? AND date=?",
                new String[]{String.valueOf(id),date})) { return c.moveToFirst() && c.getInt(0)!=0; }
    }
    public boolean setEarlySellout(int id,String date,boolean marked) {
        Dates.validate(date);
        if(date.compareTo(Dates.today())>0)return false;
        SQLiteDatabase database=getWritableDatabase(); database.beginTransaction();
        try {
            if(marked) {
                try(Cursor c=database.rawQuery("SELECT produced FROM DailyStock WHERE bread_type_id=? AND date=? AND estimated=0",
                        new String[]{String.valueOf(id),date})) {
                    if(!c.moveToFirst() || c.getLong(0)<=0 || sold(database,id,date)<=0)return false;
                }
            }
            ContentValues values=new ContentValues(); values.put("sold_out_early",marked?1:0);
            boolean ok=database.update("DailyStock",values,"bread_type_id=? AND date=? AND estimated=0",
                    new String[]{String.valueOf(id),date})>0;
            if(ok)database.setTransactionSuccessful();
            return ok;
        } finally { database.endTransaction(); }
    }
    public List<ProductionAdvisor.PlanChange> latestPlanChanges() {
        List<ProductionAdvisor.PlanChange> changes=new ArrayList<>();
        String sql="SELECT p.bread_type_id,b.name,p.weekday,p.old_quantity,p.new_quantity,p.changed_on " +
                "FROM PlanChange p JOIN BreadType b ON b.id=p.bread_type_id AND b.archived=0 " +
                "WHERE p.id=(SELECT MAX(newer.id) FROM PlanChange newer WHERE newer.bread_type_id=p.bread_type_id AND newer.weekday=p.weekday) " +
                "AND p.new_quantity<p.old_quantity ORDER BY b.name,p.weekday";
        try(Cursor c=getReadableDatabase().rawQuery(sql,null)) {
            while(c.moveToNext())changes.add(new ProductionAdvisor.PlanChange(c.getInt(0),c.getString(1),
                    c.getInt(2),c.getInt(3),c.getInt(4),c.getString(5)));
        }
        return changes;
    }
    public ArrayList<BreadType> getAllBreadTypes() {
        ArrayList<BreadType> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,name,daily_total_quantity FROM BreadType WHERE archived=0 ORDER BY name COLLATE LOCALIZED", null)) {
            while(c.moveToNext()) result.add(new BreadType(c.getInt(0),c.getString(1),c.getInt(2)));
        }
        return result;
    }
    private boolean saveType(int id, String name, int quantity) {
        if (name == null || name.trim().isEmpty() || name.trim().length() > MAX_NAME_LENGTH || quantity < 0) return false;
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            try (Cursor c = db.rawQuery("SELECT id,name FROM BreadType WHERE archived=0 AND id<>?", new String[]{String.valueOf(id)})) {
                String key = nameKey(name);
                while(c.moveToNext()) if (nameKey(c.getString(1)).equals(key)) return false;
            }
            ContentValues values = new ContentValues(); values.put("name",name.trim()); values.put("daily_total_quantity",quantity);
            boolean ok = id == 0 ? db.insertOrThrow("BreadType",null,values)>0 : db.update("BreadType",values,"id=? AND archived=0",new String[]{String.valueOf(id)})>0;
            db.setTransactionSuccessful(); return ok;
        } finally { db.endTransaction(); }
    }
    public boolean insertBreadType(String name,int quantity) { return saveType(0,name,quantity); }
    public boolean updateBreadType(int id,String name,int quantity) { return saveType(id,name,quantity); }
    public boolean deleteBreadType(int id) {
        ContentValues values = new ContentValues(); values.put("archived",1);
        return getWritableDatabase().update("BreadType",values,"id=? AND archived=0",new String[]{String.valueOf(id)})>0;
    }
    public boolean setProduction(int id,String date,int quantity) {
        Dates.validate(date); if (quantity < 0 || date.compareTo(Dates.today())>0) return false;
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            if(dayStatus(date)!=DAY_OPEN)return false;
            if (hasInvalidSales(db,id,date) || quantity < sold(db,id,date)) return false;
            String name;
            try (Cursor c = db.rawQuery("SELECT name FROM BreadType WHERE id=? AND archived=0",new String[]{String.valueOf(id)})) {
                if(!c.moveToFirst()) return false; name=c.getString(0);
            }
            int planned=plannedQuantity(db,id,date);
            try(Cursor c=db.rawQuery("SELECT planned FROM DailyStock WHERE bread_type_id=? AND date=?",new String[]{String.valueOf(id),date})) {
                if(c.moveToFirst() && !c.isNull(0)) planned=c.getInt(0);
            }
            ContentValues values=new ContentValues(); values.put("produced",quantity); values.put("estimated",0); values.put("planned",planned);
            if(db.update("DailyStock",values,"bread_type_id=? AND date=?",new String[]{String.valueOf(id),date})==0) {
                values.put("bread_type_id",id); values.put("date",date); values.put("name",name); db.insertOrThrow("DailyStock",null,values);
            }
            db.setTransactionSuccessful(); return true;
        } finally { db.endTransaction(); }
    }
    private long sold(SQLiteDatabase db,int id,String date) {
        try(Cursor c=db.rawQuery("SELECT COALESCE(SUM(quantity_sold),0) FROM SalesRecord WHERE bread_type_id=? AND date=?",new String[]{String.valueOf(id),date})) { c.moveToFirst(); return c.getLong(0); }
    }
    private boolean hasInvalidSales(SQLiteDatabase db,int id,String date) {
        try(Cursor c=db.rawQuery("SELECT 1 FROM SalesRecord WHERE bread_type_id=? AND date=? AND (quantity_sold IS NULL OR quantity_sold<=0) LIMIT 1",new String[]{String.valueOf(id),date})) {
            return c.moveToFirst();
        }
    }
    public boolean insertSalesRecord(int id,int quantity) { return recordSale(id,Dates.today(),quantity); }
    public boolean recordSale(int id,String date,int quantity) {
        Dates.validate(date); if(quantity<=0 || date.compareTo(Dates.today())>0) return false;
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try {
            if(dayStatus(date)!=DAY_OPEN)return false;
            long produced;
            try(Cursor c=db.rawQuery("SELECT d.produced FROM DailyStock d JOIN BreadType b ON b.id=d.bread_type_id WHERE d.bread_type_id=? AND d.date=? AND b.archived=0 AND d.estimated=0",new String[]{String.valueOf(id),date})) {
                if(!c.moveToFirst()) return false; produced=c.getLong(0);
            }
            if(hasInvalidSales(db,id,date) || quantity>produced-sold(db,id,date)) return false;
            ContentValues v=new ContentValues(); v.put("bread_type_id",id); v.put("date",date); v.put("quantity_sold",quantity);
            v.put("recorded_at",Dates.now());
            db.insertOrThrow("SalesRecord",null,v); db.setTransactionSuccessful(); return true;
        } finally { db.endTransaction(); }
    }
    public List<SaleEntry> sales(int id,String date) {
        Dates.validate(date);
        List<SaleEntry> entries=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,quantity_sold,recorded_at FROM SalesRecord WHERE bread_type_id=? AND date=? ORDER BY id DESC",
                new String[]{String.valueOf(id),date})) {
            while(c.moveToNext()) entries.add(new SaleEntry(c.getLong(0),c.getInt(1),c.isNull(2)?null:c.getString(2)));
        }
        return entries;
    }
    public boolean cancelSale(long saleId) {
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try {
            int id; String date; int quantity; String recordedAt;
            try(Cursor c=db.rawQuery("SELECT bread_type_id,date,quantity_sold,recorded_at FROM SalesRecord WHERE id=?",new String[]{String.valueOf(saleId)})) {
                if(!c.moveToFirst()) return false;
                id=c.getInt(0); date=c.getString(1); quantity=c.getInt(2); recordedAt=c.isNull(3)?null:c.getString(3);
            }
            if(dayStatus(date)!=DAY_OPEN) return false;
            ContentValues cancelled=new ContentValues(); cancelled.put("sale_id",saleId); cancelled.put("bread_type_id",id);
            cancelled.put("date",date); cancelled.put("quantity_sold",quantity); cancelled.put("recorded_at",recordedAt);
            cancelled.put("cancelled_at",Dates.now());
            db.insertOrThrow("CancelledSale",null,cancelled);
            db.delete("SalesRecord","id=?",new String[]{String.valueOf(saleId)});
            if(sold(db,id,date)<=0) {
                ContentValues values=new ContentValues(); values.put("sold_out_early",0);
                db.update("DailyStock",values,"bread_type_id=? AND date=?",new String[]{String.valueOf(id),date});
            }
            db.setTransactionSuccessful(); return true;
        } finally { db.endTransaction(); }
    }
    public List<StockRow> report(String start,String end) {
        Dates.validate(start); Dates.validate(end); if(start.compareTo(end)>0) throw new IllegalArgumentException("Invalid range");
        List<StockRow> rows=new ArrayList<>();
        String sql="SELECT d.bread_type_id,(SELECT n.name FROM DailyStock n WHERE n.bread_type_id=d.bread_type_id AND n.date BETWEEN ? AND ? ORDER BY n.date DESC LIMIT 1) name,SUM(d.produced) produced,SUM(COALESCE(s.sold,0)) sold,MAX(d.estimated) estimated,MAX(CASE WHEN d.produced<0 OR COALESCE(s.sold,0)<0 OR COALESCE(s.sold,0)>d.produced OR COALESCE(s.invalid,0)>0 THEN 1 ELSE 0 END) inconsistent FROM DailyStock d LEFT JOIN (SELECT bread_type_id,date,SUM(quantity_sold) sold,MAX(CASE WHEN quantity_sold IS NULL OR quantity_sold<=0 THEN 1 ELSE 0 END) invalid FROM SalesRecord GROUP BY bread_type_id,date) s ON s.bread_type_id=d.bread_type_id AND s.date=d.date WHERE d.date BETWEEN ? AND ? GROUP BY d.bread_type_id ORDER BY name COLLATE LOCALIZED,d.bread_type_id";
        try(Cursor c=getReadableDatabase().rawQuery(sql,new String[]{start,end,start,end})) {
            while(c.moveToNext()) rows.add(new StockRow(c.getInt(0),c.getString(1),c.getLong(2),c.getLong(3),c.getInt(4)!=0,c.getInt(5)!=0));
        }
        return rows;
    }
    public List<DailyReportRecord> dailyReportForBread(int id,String start,String end) {
        Dates.validate(start); Dates.validate(end);
        if(start.compareTo(end)>0) throw new IllegalArgumentException("Invalid range");
        Map<String,DailyReportRecord> found=new HashMap<>();
        String sql="SELECT days.date,d.produced,d.planned,d.estimated,COALESCE(s.sold,0),COALESCE(s.invalid,0),d.sold_out_early " +
                "FROM (SELECT date FROM DailyStock WHERE bread_type_id=? UNION SELECT date FROM SalesRecord WHERE bread_type_id=?) days " +
                "LEFT JOIN DailyStock d ON d.bread_type_id=? AND d.date=days.date " +
                "LEFT JOIN (SELECT date,SUM(quantity_sold) sold,MAX(CASE WHEN quantity_sold IS NULL OR quantity_sold<=0 THEN 1 ELSE 0 END) invalid " +
                "FROM SalesRecord WHERE bread_type_id=? GROUP BY date) s ON s.date=days.date " +
                "WHERE days.date BETWEEN ? AND ? ORDER BY days.date";
        String key=String.valueOf(id);
        try(Cursor c=getReadableDatabase().rawQuery(sql,new String[]{key,key,key,key,start,end})) {
            while(c.moveToNext()) {
                boolean hasProduction=!c.isNull(1);
                Integer planned=c.isNull(2)?null:c.getInt(2);
                long produced=hasProduction?c.getLong(1):0, sold=c.getLong(4);
                found.put(c.getString(0),new DailyReportRecord(c.getString(0),planned,produced,sold,
                        hasProduction,hasProduction && c.getInt(3)!=0,c.getInt(5)!=0,hasProduction && c.getInt(6)!=0,DAY_OPEN,false));
            }
        }
        List<DailyReportRecord> days=new ArrayList<>();
        Map<String,Integer> statuses=new HashMap<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT date,status FROM DayClose WHERE date BETWEEN ? AND ?",
                new String[]{start,end})) {while(c.moveToNext())statuses.put(c.getString(0),c.getInt(1));}
        Set<String> productionDays=new HashSet<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT DISTINCT date FROM DailyStock WHERE date BETWEEN ? AND ? AND produced>0",
                new String[]{start,end})) {while(c.moveToNext())productionDays.add(c.getString(0));}
        for(String day=start;day.compareTo(end)<=0;day=Dates.plusDays(day,1)) {
            DailyReportRecord recorded=found.get(day);
            int status=statuses.getOrDefault(day,DAY_OPEN);
            boolean anyProduction=productionDays.contains(day);
            days.add(recorded==null?new DailyReportRecord(day,null,0,0,false,false,false,false,status,anyProduction):
                    new DailyReportRecord(day,recorded.planned,recorded.produced,recorded.sold,
                            recorded.hasProduction,recorded.estimated,recorded.inconsistent,recorded.earlySellout,status,anyProduction));
        }
        return days;
    }
    public ArrayList<String> getSalesDates() {
        ArrayList<String> dates=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT date FROM DailyStock UNION SELECT date FROM SalesRecord ORDER BY date DESC",null)) { while(c.moveToNext()) dates.add(c.getString(0)); }
        return dates;
    }
    public String firstRecordDate() {
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT MIN(date) FROM (SELECT date FROM DailyStock UNION ALL SELECT date FROM SalesRecord)",null)) {
            return c.moveToFirst() && !c.isNull(0)?c.getString(0):null;
        }
    }
    public List<ProductionAdvisor.Observation> adviceHistory(String start, String end) {
        Dates.validate(start); Dates.validate(end);
        if(start.compareTo(end)>0) throw new IllegalArgumentException("Invalid range");
        List<ProductionAdvisor.Observation> days=new ArrayList<>();
        String sql="SELECT d.bread_type_id,d.date,d.produced,COALESCE(s.sold,0),d.estimated,COALESCE(s.invalid,0),d.sold_out_early,COALESCE(c.status,0) " +
                "FROM DailyStock d JOIN BreadType b ON b.id=d.bread_type_id AND b.archived=0 " +
                "LEFT JOIN DayClose c ON c.date=d.date " +
                "LEFT JOIN (SELECT bread_type_id,date,SUM(quantity_sold) sold," +
                "MAX(CASE WHEN quantity_sold IS NULL OR quantity_sold<=0 THEN 1 ELSE 0 END) invalid " +
                "FROM SalesRecord GROUP BY bread_type_id,date) s ON s.bread_type_id=d.bread_type_id AND s.date=d.date " +
                "WHERE d.date BETWEEN ? AND ? ORDER BY d.bread_type_id,d.date";
        try (Cursor c=getReadableDatabase().rawQuery(sql,new String[]{start,end})) {
            while(c.moveToNext()) days.add(new ProductionAdvisor.Observation(
                    c.getInt(0),c.getString(1),c.getLong(2),c.getLong(3),c.getInt(4)!=0,c.getInt(5)!=0,c.getInt(6)!=0,c.getInt(7)==DAY_COMPLETE));
        }
        String orphanSql="SELECT DISTINCT s.bread_type_id FROM SalesRecord s " +
                "JOIN BreadType b ON b.id=s.bread_type_id AND b.archived=0 " +
                "LEFT JOIN DailyStock d ON d.bread_type_id=s.bread_type_id AND d.date=s.date " +
                "WHERE s.date BETWEEN ? AND ? AND d.bread_type_id IS NULL";
        try (Cursor c=getReadableDatabase().rawQuery(orphanSql,new String[]{start,end})) {
            while(c.moveToNext()) days.add(new ProductionAdvisor.Observation(c.getInt(0),start,0,0,false,true,false,false));
        }
        return days;
    }
}
