package com.example.bakeryapp;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static org.junit.Assert.*;

public class FiveMonthFlowTest {
    @Test public void historyIsConsistentAndScreensOpen() {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        DBHelper helper = new DBHelper(app);
        try {
            DebugData.seedIfEmpty(helper);
            SQLiteDatabase db = helper.getReadableDatabase();
            assertEquals(6, helper.getAllBreadTypes().size());
            assertTrue(count(db, "DailyStock") > 550);
            assertTrue(count(db, "SalesRecord") > 1000);

            Calendar start = Calendar.getInstance();
            start.add(Calendar.MONTH, -5);
            String from = String.format(java.util.Locale.ROOT, "%04d-%02d-%02d",
                    start.get(Calendar.YEAR), start.get(Calendar.MONTH) + 1, start.get(Calendar.DAY_OF_MONTH));
            String today = Dates.today();
            List<StockRow> history = helper.report(from, today);
            assertEquals(7, history.size());
            ReportTotals total = ReportTotals.from(history);
            assertTrue(total.confirmedStock);
            assertFalse(total.inconsistent);
            assertTrue(total.produced > total.sold);
            assertTrue(total.salesPercent() >= 50 && total.salesPercent() <= 95);
            assertFalse(DebugData.seedIfEmpty(helper));
            List<ProductionAdvisor.Advice> suggestions=ProductionAdvisor.assess(helper.getAllBreadTypes(),
                    helper.adviceHistory(Dates.plusDays(today,-28),Dates.plusDays(today,-1)));
            assertEquals(2,suggestions.size());
            assertEquals("Çavdar",suggestions.get(0).breadName);
            assertEquals(java.util.Calendar.MONDAY,suggestions.get(0).weekday);
            assertEquals(java.util.Calendar.THURSDAY,suggestions.get(1).weekday);
            List<ProductionAdvisor.WeekChange> changes=ProductionAdvisor.weeklyChanges(helper.getAllBreadTypes(),
                    helper.adviceHistory(Dates.plusDays(today,-28),Dates.plusDays(today,-1)),today);
            assertTrue(changes.stream().anyMatch(change->change.breadName.equals("Susamlı") && change.percent<0));

            for (String day = from; day.compareTo(today) <= 0; day = Dates.plusDays(day, 1)) {
                ReportTotals daily = ReportTotals.from(helper.report(day, day));
                assertTrue(daily.confirmedStock);
                assertFalse(daily.inconsistent);
                assertTrue(daily.produced >= daily.sold);
            }
            String weekStart = Dates.weekStart(today);
            String weekEnd = Dates.plusDays(weekStart, 6);
            ReportTotals weekly = ReportTotals.from(helper.report(weekStart, weekEnd));
            long produced = 0, sold = 0;
            for (String day = weekStart; day.compareTo(weekEnd) <= 0; day = Dates.plusDays(day, 1)) {
                ReportTotals daily = ReportTotals.from(helper.report(day, day));
                produced += daily.produced;
                sold += daily.sold;
            }
            assertEquals(produced, weekly.produced);
            assertEquals(sold, weekly.sold);
            assertEquals(Integer.parseInt(today.substring(8)),
                    helper.dailyReportForBread(history.get(0).id,Dates.monthStart(today),today).size());
            assertTrue(ReportMessage.format(from,today,history).contains("Satış Raporu"));

            Intent intent = new Intent(app, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
            awaitRender(app);
            onView(withText(R.string.today_title)).check(matches(isDisplayed()));
            onView(withText(R.string.reports)).perform(click());
            awaitRender(app);
            onView(withText(R.string.report_title)).check(matches(isDisplayed()));
            onView(withText(R.string.week_starts_monday)).check(matches(isDisplayed()));
            onView(withText(R.string.choose_week)).check(matches(isDisplayed()));
            onView(withText(R.string.report_highlights)).perform(scrollTo()).check(matches(isDisplayed()));
            onView(withContentDescription(app.getString(R.string.open_bread_detail,"Baget"))).perform(scrollTo(),click());
            awaitRender(app);
            onView(withText(app.getString(R.string.bread_detail_title,"Baget"))).check(matches(isDisplayed()));
            onView(withText(Dates.display(Dates.weekStart(today),"EEE, dd MMM yyyy"))).check(matches(isDisplayed()));
            onView(withText(Dates.display(Dates.weekStart(today),"EEE, dd MMM yyyy"))).perform(click());
            onView(withText(R.string.reopen_day)).inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withText(R.string.cancel)).inRoot(isDialog()).perform(click());
            onView(withText(R.string.close)).perform(click());
            onView(withText(R.string.weekly)).perform(scrollTo(),click());
            awaitRender(app);
            onView(withText(R.string.week_trend)).perform(scrollTo()).check(matches(isDisplayed()));
            onView(withText(R.string.monthly)).perform(scrollTo(),click());
            awaitRender(app);
            onView(withText(R.string.choose_month)).check(matches(isDisplayed()));
            String month=Dates.display(today,"MMMM");
            String currentMonth=app.getString(R.string.this_month)+" · "+today.substring(0,4)+" · "+
                    month.substring(0,1).toUpperCase(new java.util.Locale("tr","TR"))+month.substring(1);
            onView(withText(currentMonth)).check(matches(isDisplayed()));
            onView(withText(R.string.share_whatsapp)).perform(scrollTo()).check(matches(isDisplayed()));
            onView(withText(R.string.custom_range)).perform(scrollTo(),click());
            awaitRender(app);
            onView(withText(Dates.display(today,"dd MMMM yyyy"))).perform(scrollTo(),click());
            String firstDate=helper.firstRecordDate();
            onView(isAssignableFrom(android.widget.DatePicker.class)).check((view,error)->{
                if(error!=null)throw error;
                String lowerBound=new java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.ROOT)
                        .format(new java.util.Date(((android.widget.DatePicker)view).getMinDate()));
                assertEquals(firstDate,lowerBound);
            });
            pressBack();
            onView(withText(R.string.advice_tab)).perform(click());
            awaitRender(app);
            onView(withText(R.string.weekly_change_section)).perform(scrollTo()).check(matches(isDisplayed()));
            onView(withText(R.string.weekday_advice_section)).perform(scrollTo()).check(matches(isDisplayed()));
        } finally {
            helper.close();
        }
    }

    private static long count(SQLiteDatabase db, String table) {
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + table, null)) {
            cursor.moveToFirst();
            return cursor.getLong(0);
        }
    }

    private static void awaitRender(Context app) {
        CountDownLatch done = new CountDownLatch(1);
        BakeryRepository.get(app).executor().execute(done::countDown);
        try {
            assertTrue("Screen refresh timed out", done.await(20, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
}
