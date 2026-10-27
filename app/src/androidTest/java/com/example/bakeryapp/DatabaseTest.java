package com.example.bakeryapp;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.List;

public class DatabaseTest {
    private static final String TEST_DB_NAME = "BakeryStock-test.db";
    private Context context;
    private DBHelper db;
    @Before public void setup() {
        context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase(TEST_DB_NAME);
        db=new DBHelper(context,TEST_DB_NAME);
    }
    @After public void cleanup() { db.close(); context.deleteDatabase(TEST_DB_NAME); }
    private int bread() {
        assertTrue(db.insertBreadType("Ekşi maya",100));
        return db.getAllBreadTypes().get(0).getId();
    }
    @Test public void validatesStockAndRetainsHistory() {
        int id=bread(); String day=Dates.today();
        assertFalse(db.recordSale(id,day,1));
        assertTrue(db.setProduction(id,day,100));
        assertTrue(db.recordSale(id,day,60));
        assertFalse(db.recordSale(id,day,41));
        assertFalse(db.recordSale(id,day,0));
        assertFalse(db.recordSale(id,day,-1));
        assertFalse(db.setProduction(id,day,59));
        assertTrue(db.updateBreadType(id,"Yeni isim",200));
        StockRow row=db.report(day,day).get(0);
        assertEquals("Ekşi maya",row.name);
        assertEquals(100,row.produced); assertEquals(40,row.remaining());
        assertTrue(db.deleteBreadType(id));
        assertTrue(db.getAllBreadTypes().isEmpty());
        assertEquals(60,db.report(day,day).get(0).sold);
    }
    @Test public void noPhantomProductionOnMissingDays() {
        int id=bread(); String day=Dates.today();
        assertNull(db.firstRecordDate());
        assertTrue(db.setProduction(id,day,100));
        assertEquals(day,db.firstRecordDate());
        assertEquals(100,db.report(Dates.plusDays(day,-6),day).get(0).produced);
        assertTrue(db.report(Dates.plusDays(day,-1),Dates.plusDays(day,-1)).isEmpty());
    }
    @Test public void mistakenSaleCanBeUndoneAndIsKeptInHistory() {
        int id=bread(); String day=Dates.today();
        assertTrue(db.setProduction(id,day,100));
        assertTrue(db.recordSale(id,day,30));
        assertTrue(db.recordSale(id,day,50));
        List<SaleEntry> sales=db.sales(id,day);
        assertEquals(2,sales.size());
        assertEquals(50,sales.get(0).quantity);
        assertNotNull(sales.get(0).time());
        assertFalse(db.setProduction(id,day,40));
        assertTrue(db.cancelSale(sales.get(0).id));
        assertFalse(db.cancelSale(sales.get(0).id));
        assertEquals(30,db.report(day,day).get(0).sold);
        assertEquals(70,db.report(day,day).get(0).remaining());
        assertEquals(1,db.sales(id,day).size());
        assertTrue(db.setProduction(id,day,40));
        try(Cursor c=db.getReadableDatabase().rawQuery("SELECT quantity_sold FROM CancelledSale WHERE bread_type_id=? AND date=?",
                new String[]{String.valueOf(id),day})) { assertTrue(c.moveToFirst()); assertEquals(50,c.getInt(0)); }
    }
    @Test public void closedDayBlocksUndoAndLastUndoClearsEarlySellout() {
        int id=bread(); String day=Dates.today();
        assertTrue(db.setProduction(id,day,100));
        assertTrue(db.recordSale(id,day,100));
        assertTrue(db.setEarlySellout(id,day,true));
        long sale=db.sales(id,day).get(0).id;
        assertTrue(db.setDayStatus(day,DBHelper.DAY_COMPLETE));
        assertFalse(db.cancelSale(sale));
        assertEquals(100,db.report(day,day).get(0).sold);
        assertTrue(db.setDayStatus(day,DBHelper.DAY_OPEN));
        assertTrue(db.cancelSale(sale));
        assertEquals(0,db.report(day,day).get(0).sold);
        assertFalse(db.earlySellout(id,day));
    }
    @Test public void duplicateNamesFollowTurkishCase() {
        assertTrue(db.insertBreadType("Simit",50));
        assertFalse(db.insertBreadType("SİMİT",50));
        assertFalse(db.insertBreadType(" simit ",50));
        assertFalse(db.insertBreadType("SIMIT",50));
        assertFalse(db.insertBreadType("sımıt",50));
        assertTrue(db.insertBreadType("Islak kek",10));
        assertFalse(db.insertBreadType("ISLAK KEK",10));
    }
    @Test public void productionIsReplacementAndRepeatedSalesAreIncrements() {
        int id=bread(); String day=Dates.today();
        assertTrue(db.setProduction(id,day,100));
        assertTrue(db.recordSale(id,day,30));
        assertTrue(db.recordSale(id,day,20));
        assertTrue(db.setProduction(id,day,120));
        StockRow row=db.report(day,day).get(0);
        assertEquals(120,row.produced); assertEquals(50,row.sold); assertEquals(70,row.remaining());
        assertTrue(db.recordSale(id,day,70));
        assertFalse(db.recordSale(id,day,1));
        assertEquals(0,db.report(day,day).get(0).remaining());
    }
    @Test public void renameDoesNotSplitPeriodOrChangeOldDayName() {
        int id=bread(); String day=Dates.today(), previous=Dates.plusDays(day,-1);
        assertTrue(db.setProduction(id,previous,100));
        assertTrue(db.updateBreadType(id,"Yeni isim",200));
        assertTrue(db.setProduction(id,day,200));
        assertEquals(1,db.report(previous,day).size());
        assertEquals(300,db.report(previous,day).get(0).produced);
        assertEquals("Yeni isim",db.report(previous,day).get(0).name);
        assertEquals("Ekşi maya",db.report(previous,previous).get(0).name);
    }
    @Test public void invalidInputsAndFutureDatesNeverCreateMovements() {
        int id=bread(); String day=Dates.today(), future=Dates.plusDays(day,1);
        assertFalse(db.insertBreadType(" ",100));
        assertFalse(db.insertBreadType("Ekşi maya",100));
        assertFalse(db.setProduction(id,day,-1));
        assertFalse(db.setProduction(id,future,100));
        assertFalse(db.recordSale(id,future,1));
        assertFalse(db.setProduction(Integer.MAX_VALUE,day,100));
        assertTrue(db.report(day,future).isEmpty());
    }
    @Test public void importedEstimateMustBeConfirmedBeforeNewSales() {
        int id=bread(); String day=Dates.today();
        assertTrue(db.setProduction(id,day,100));
        db.getWritableDatabase().execSQL("UPDATE DailyStock SET estimated=1");
        assertFalse(db.recordSale(id,day,10));
        assertFalse(db.report(day,day).get(0).hasConfirmedStock());
        assertTrue(db.setProduction(id,day,100));
        assertTrue(db.recordSale(id,day,10));
        assertTrue(db.report(day,day).get(0).hasConfirmedStock());
    }
    @Test public void migrationKeepsSalesAndMarksEstimates() {
        db.close();
        try(SQLiteDatabase legacy=context.openOrCreateDatabase(TEST_DB_NAME,0,null)) {
            legacy.execSQL("CREATE TABLE BreadType(id INTEGER PRIMARY KEY,name TEXT,daily_total_quantity INTEGER)");
            legacy.execSQL("CREATE TABLE SalesRecord(id INTEGER PRIMARY KEY,bread_type_id INTEGER,date TEXT,quantity_sold INTEGER)");
            legacy.execSQL("INSERT INTO BreadType VALUES(1,'Ekmek',100)");
            legacy.execSQL("INSERT INTO SalesRecord VALUES(1,1,'2025-06-02',30)");
            legacy.setVersion(2);
        }
        db=new DBHelper(context,TEST_DB_NAME);
        StockRow row=db.report("2025-06-02","2025-06-02").get(0);
        assertEquals(30,row.sold); assertEquals(100,row.produced); assertTrue(row.estimated);
        try(Cursor c=db.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM SalesRecord",null)) {c.moveToFirst();assertEquals(1,c.getInt(0));}
    }
    @Test public void weekdayAdviceUsesConfirmedDailySales() {
        int id=bread();
        String monday=Dates.plusDays(Dates.weekStart(Dates.today()),-28);
        for(int week=0;week<4;week++) for(int offset=0;offset<6;offset++) {
            String day=Dates.plusDays(monday,week*7+offset);
            assertTrue(db.setProduction(id,day,40));
            assertTrue(db.recordSale(id,day,offset==0 || offset==3?10:30));
            assertTrue(db.setDayStatus(day,DBHelper.DAY_COMPLETE));
        }
        List<ProductionAdvisor.Advice> suggestions=ProductionAdvisor.assess(db.getAllBreadTypes(),
                db.adviceHistory(monday,Dates.plusDays(Dates.today(),-1)));
        assertEquals(2,suggestions.size());
        assertEquals(java.util.Calendar.MONDAY,suggestions.get(0).weekday);
        assertEquals(java.util.Calendar.THURSDAY,suggestions.get(1).weekday);
        String corruptedMonday=Dates.plusDays(Dates.weekStart(Dates.today()),-7);
        db.getWritableDatabase().execSQL("INSERT INTO SalesRecord(bread_type_id,date,quantity_sold) VALUES(?,?,?)",
                new Object[]{id,corruptedMonday,100});
        assertTrue(ProductionAdvisor.assess(db.getAllBreadTypes(),
                db.adviceHistory(monday,Dates.plusDays(Dates.today(),-1))).isEmpty());
    }
    @Test public void dayClosureExcludesLeaveDaysAndFreezesMovementsUntilReopened() {
        int id=bread(); String today=Dates.today(),yesterday=Dates.plusDays(today,-1);
        assertEquals(DBHelper.DAY_OPEN,db.dayStatus(today));
        assertTrue(db.setDayStatus(yesterday,DBHelper.DAY_NO_PRODUCTION));
        assertEquals(DBHelper.DAY_NO_PRODUCTION,db.dayStatus(yesterday));
        assertTrue(db.adviceHistory(yesterday,yesterday).isEmpty());
        assertEquals(DBHelper.DAY_NO_PRODUCTION,db.dailyReportForBread(id,yesterday,yesterday).get(0).dayStatus);
        assertFalse(db.setProduction(id,yesterday,20));
        assertTrue(db.setProduction(id,today,100));
        assertTrue(db.recordSale(id,today,60));
        assertFalse(db.setDayStatus(today,DBHelper.DAY_NO_PRODUCTION));
        assertTrue(db.setDayStatus(today,DBHelper.DAY_COMPLETE));
        assertFalse(db.recordSale(id,today,1));
        assertFalse(db.setProduction(id,today,99));
        assertTrue(db.setDayStatus(today,DBHelper.DAY_OPEN));
        assertTrue(db.recordSale(id,today,1));
    }
    @Test public void unclosedProductionDoesNotEnterAdviceHistoryAsCompleted() {
        int id=bread(); String day=Dates.plusDays(Dates.today(),-1);
        assertTrue(db.setProduction(id,day,40)); assertTrue(db.recordSale(id,day,10));
        assertFalse(db.adviceHistory(day,day).get(0).completed);
        assertTrue(db.setDayStatus(day,DBHelper.DAY_COMPLETE));
        assertTrue(db.adviceHistory(day,day).get(0).completed);
    }
    @Test public void weeklyPlanPrefillsButActualProductionAndHistoryStaySeparate() {
        int id=bread();
        String day=Dates.plusDays(Dates.today(),-1);
        int weekday=ProductionAdvisor.dayOfWeek(day);
        assertEquals(100,db.weeklyPlan(id)[weekday]);
        assertTrue(db.setDayPlan(id,weekday,80));
        assertEquals(80,db.weeklyPlan(id)[weekday]);
        assertTrue(db.setProduction(id,day,75));
        assertTrue(db.recordSale(id,day,30));
        assertEquals(80,db.recordedPlan(id,day));
        assertTrue(db.setDayPlan(id,weekday,60));
        assertEquals(60,db.weeklyPlan(id)[weekday]);
        assertEquals(80,db.recordedPlan(id,day));
        assertTrue(db.setProduction(id,day,78));
        assertEquals(80,db.recordedPlan(id,day));
        assertEquals(78,db.report(day,day).get(0).produced);
        List<DailyReportRecord> details=db.dailyReportForBread(id,Dates.plusDays(day,-1),day);
        assertEquals(2,details.size());
        assertFalse(details.get(0).hasProduction);
        assertEquals(Integer.valueOf(80),details.get(1).planned);
        assertEquals(78,details.get(1).produced);
        assertEquals(30,details.get(1).sold);
        assertEquals(48,details.get(1).remaining());
        assertTrue(db.setDayPlan(id,weekday,100));
        assertEquals(100,db.weeklyPlan(id)[weekday]);
        assertEquals(80,db.recordedPlan(id,day));
    }
    @Test public void versionThreeUpgradeKeepsProductionAndLeavesUnknownOldPlanBlank() {
        db.close(); context.deleteDatabase(TEST_DB_NAME);
        try(SQLiteDatabase previous=context.openOrCreateDatabase(TEST_DB_NAME,0,null)) {
            previous.execSQL("CREATE TABLE BreadType(id INTEGER PRIMARY KEY,name TEXT,daily_total_quantity INTEGER,archived INTEGER DEFAULT 0)");
            previous.execSQL("CREATE TABLE SalesRecord(id INTEGER PRIMARY KEY,bread_type_id INTEGER,date TEXT,quantity_sold INTEGER)");
            previous.execSQL("CREATE TABLE DailyStock(bread_type_id INTEGER,date TEXT,name TEXT,produced INTEGER,estimated INTEGER,PRIMARY KEY(bread_type_id,date))");
            previous.execSQL("INSERT INTO BreadType VALUES(1,'Çavdar',40,0)");
            previous.execSQL("INSERT INTO DailyStock VALUES(1,'2026-10-01','Çavdar',40,0)");
            previous.execSQL("INSERT INTO SalesRecord VALUES(1,1,'2026-10-01',10)");
            previous.setVersion(3);
        }
        db=new DBHelper(context,TEST_DB_NAME);
        assertEquals(30,db.report("2026-10-01","2026-10-01").get(0).remaining());
        assertEquals(-1,db.recordedPlan(1,"2026-10-01"));
        assertEquals(40,db.weeklyPlan(1)[java.util.Calendar.THURSDAY]);
    }
    @Test public void earlySelloutRequiresSalesAndPersistsInDailyReport() {
        int id=bread(); String date=Dates.today();
        assertFalse(db.setEarlySellout(id,date,true));
        assertTrue(db.setProduction(id,date,100));
        assertFalse(db.setEarlySellout(id,date,true));
        assertTrue(db.recordSale(id,date,80));
        assertTrue(db.setEarlySellout(id,date,true));
        assertTrue(db.earlySellout(id,date));
        assertTrue(db.dailyReportForBread(id,date,date).get(0).earlySellout);
        assertTrue(db.setEarlySellout(id,date,false));
        assertFalse(db.earlySellout(id,date));
    }
    @Test public void planChangesKeepPreviousAmountsAndIgnoreUnchangedSaves() {
        int id=bread(),weekday=java.util.Calendar.MONDAY;
        assertTrue(db.setDayPlan(id,weekday,80));
        assertTrue(db.setDayPlan(id,weekday,80));
        assertEquals(1,db.latestPlanChanges().size());
        assertEquals(100,db.latestPlanChanges().get(0).oldQuantity);
        assertEquals(80,db.latestPlanChanges().get(0).newQuantity);
        assertTrue(db.setDayPlan(id,weekday,100));
        assertTrue(db.latestPlanChanges().isEmpty());
    }
    @Test public void versionFourUpgradePreservesPlanAndAddsFollowUpStorage() {
        db.close(); context.deleteDatabase(TEST_DB_NAME);
        try(SQLiteDatabase previous=context.openOrCreateDatabase(TEST_DB_NAME,0,null)) {
            previous.execSQL("CREATE TABLE BreadType(id INTEGER PRIMARY KEY,name TEXT,daily_total_quantity INTEGER,archived INTEGER DEFAULT 0)");
            previous.execSQL("CREATE TABLE SalesRecord(id INTEGER PRIMARY KEY,bread_type_id INTEGER,date TEXT,quantity_sold INTEGER)");
            previous.execSQL("CREATE TABLE DailyStock(bread_type_id INTEGER,date TEXT,name TEXT,produced INTEGER,estimated INTEGER,planned INTEGER,PRIMARY KEY(bread_type_id,date))");
            previous.execSQL("CREATE TABLE BreadPlan(bread_type_id INTEGER,weekday INTEGER,quantity INTEGER,updated_on TEXT,PRIMARY KEY(bread_type_id,weekday))");
            previous.execSQL("INSERT INTO BreadType VALUES(1,'Rye',40,0)");
            previous.execSQL("INSERT INTO DailyStock VALUES(1,'2026-10-01','Rye',35,0,32)");
            previous.execSQL("INSERT INTO SalesRecord VALUES(1,1,'2026-10-01',10)");
            previous.execSQL("INSERT INTO BreadPlan VALUES(1,5,32,'2026-09-30')");
            previous.setVersion(4);
        }
        db=new DBHelper(context,TEST_DB_NAME);
        assertEquals(32,db.weeklyPlan(1)[java.util.Calendar.THURSDAY]);
        assertEquals(32,db.recordedPlan(1,"2026-10-01"));
        assertFalse(db.earlySellout(1,"2026-10-01"));
        assertEquals(10,db.report("2026-10-01","2026-10-01").get(0).sold);
        assertEquals("2026-10-01",db.firstRecordDate());
        assertEquals(DBHelper.DAY_OPEN,db.dayStatus("2026-10-01"));
        assertTrue(db.latestPlanChanges().isEmpty());
    }
    @Test public void versionFiveUpgradePreservesRecordsWithoutAssumingDaysWereClosed() {
        db.close(); context.deleteDatabase(TEST_DB_NAME);
        try(SQLiteDatabase previous=context.openOrCreateDatabase(TEST_DB_NAME,0,null)) {
            previous.execSQL("CREATE TABLE BreadType(id INTEGER PRIMARY KEY,name TEXT,daily_total_quantity INTEGER,archived INTEGER DEFAULT 0)");
            previous.execSQL("CREATE TABLE SalesRecord(id INTEGER PRIMARY KEY,bread_type_id INTEGER,date TEXT,quantity_sold INTEGER)");
            previous.execSQL("CREATE TABLE DailyStock(bread_type_id INTEGER,date TEXT,name TEXT,produced INTEGER,estimated INTEGER,planned INTEGER,sold_out_early INTEGER,PRIMARY KEY(bread_type_id,date))");
            previous.execSQL("CREATE TABLE BreadPlan(bread_type_id INTEGER,weekday INTEGER,quantity INTEGER,updated_on TEXT,PRIMARY KEY(bread_type_id,weekday))");
            previous.execSQL("CREATE TABLE PlanChange(id INTEGER PRIMARY KEY,bread_type_id INTEGER,weekday INTEGER,old_quantity INTEGER,new_quantity INTEGER,changed_on TEXT)");
            previous.execSQL("INSERT INTO BreadType VALUES(1,'Rye',40,0)");
            previous.execSQL("INSERT INTO DailyStock VALUES(1,'2026-10-01','Rye',35,0,32,1)");
            previous.execSQL("INSERT INTO SalesRecord VALUES(1,1,'2026-10-01',10)");
            previous.setVersion(5);
        }
        db=new DBHelper(context,TEST_DB_NAME);
        assertEquals(10,db.report("2026-10-01","2026-10-01").get(0).sold);
        assertTrue(db.earlySellout(1,"2026-10-01"));
        assertEquals(DBHelper.DAY_OPEN,db.dayStatus("2026-10-01"));
        assertFalse(db.adviceHistory("2026-10-01","2026-10-01").get(0).completed);
    }
    @Test public void versionSixUpgradeKeepsSalesWithoutTimesAndAllowsUndo() {
        db.close(); context.deleteDatabase(TEST_DB_NAME);
        String day=Dates.today();
        try(SQLiteDatabase previous=context.openOrCreateDatabase(TEST_DB_NAME,0,null)) {
            previous.execSQL("CREATE TABLE BreadType(id INTEGER PRIMARY KEY,name TEXT,daily_total_quantity INTEGER,archived INTEGER DEFAULT 0)");
            previous.execSQL("CREATE TABLE SalesRecord(id INTEGER PRIMARY KEY,bread_type_id INTEGER,date TEXT,quantity_sold INTEGER)");
            previous.execSQL("CREATE TABLE DailyStock(bread_type_id INTEGER,date TEXT,name TEXT,produced INTEGER,estimated INTEGER,planned INTEGER,sold_out_early INTEGER DEFAULT 0,PRIMARY KEY(bread_type_id,date))");
            previous.execSQL("CREATE TABLE BreadPlan(bread_type_id INTEGER,weekday INTEGER,quantity INTEGER,updated_on TEXT,PRIMARY KEY(bread_type_id,weekday))");
            previous.execSQL("CREATE TABLE PlanChange(id INTEGER PRIMARY KEY,bread_type_id INTEGER,weekday INTEGER,old_quantity INTEGER,new_quantity INTEGER,changed_on TEXT)");
            previous.execSQL("CREATE TABLE DayClose(date TEXT PRIMARY KEY,status INTEGER,closed_at TEXT)");
            previous.execSQL("INSERT INTO BreadType VALUES(1,'Çavdar',40,0)");
            previous.execSQL("INSERT INTO DailyStock VALUES(1,'"+day+"','Çavdar',40,0,40,0)");
            previous.execSQL("INSERT INTO SalesRecord VALUES(1,1,'"+day+"',10)");
            previous.execSQL("INSERT INTO SalesRecord VALUES(2,1,'"+day+"',25)");
            previous.setVersion(6);
        }
        db=new DBHelper(context,TEST_DB_NAME);
        List<SaleEntry> sales=db.sales(1,day);
        assertEquals(2,sales.size());
        assertNull(sales.get(0).time());
        assertTrue(db.cancelSale(sales.get(0).id));
        assertEquals(10,db.report(day,day).get(0).sold);
        assertTrue(db.recordSale(1,day,5));
        assertNotNull(db.sales(1,day).get(0).time());
    }
}
