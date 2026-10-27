package com.example.bakeryapp;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.*;

public class OperationsFlowTest {
    @Test public void createProduceSellCloseReopenAndInspectReport() {
        Context app=InstrumentationRegistry.getInstrumentation().getTargetContext();
        DBHelper helper=new DBHelper(app);
        String name="Köy Ekmeği",today=Dates.today();
        int originalStatus=helper.dayStatus(today);
        int id=0;
        try {
            assertTrue(helper.setDayStatus(today,DBHelper.DAY_OPEN));
            Intent intent=new Intent(app,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
            awaitRender(app);

            onView(withText(R.string.breads)).perform(click()); awaitRender(app);
            onView(withText(R.string.add_bread)).perform(click());
            onView(withContentDescription(app.getString(R.string.bread_name))).inRoot(isDialog())
                    .perform(replaceText(name),closeSoftKeyboard());
            onView(withContentDescription(app.getString(R.string.default_quantity))).inRoot(isDialog())
                    .perform(replaceText("20"),closeSoftKeyboard());
            onView(withText(R.string.save)).inRoot(isDialog()).perform(click()); awaitRender(app);
            for(BreadType bread:helper.getAllBreadTypes())if(name.equals(bread.getName()))id=bread.getId();
            assertTrue(id>0);

            onView(withText(R.string.today)).perform(click()); awaitRender(app);
            onView(withContentDescription(app.getString(R.string.production_for_bread,name)))
                    .perform(scrollTo(),click());
            onView(withContentDescription(app.getString(R.string.quantity))).inRoot(isDialog())
                    .perform(replaceText("20"),closeSoftKeyboard());
            onView(withText(R.string.save)).inRoot(isDialog()).perform(click()); awaitRender(app);
            onView(withContentDescription(app.getString(R.string.sale_for_bread,name)))
                    .perform(scrollTo(),click());
            onView(withContentDescription(app.getString(R.string.quantity))).inRoot(isDialog())
                    .perform(replaceText("5"),closeSoftKeyboard());
            onView(withText(R.string.save)).inRoot(isDialog()).perform(click()); awaitRender(app);
            boolean foundSale=false;
            for(StockRow row:helper.report(today,today))if(row.id==id) {
                assertEquals(5,row.sold);foundSale=true;
            }
            assertTrue(foundSale);

            onView(withContentDescription(app.getString(R.string.early_sellout_for_bread,name)))
                    .perform(scrollTo(),click());
            onView(withText(R.string.save)).inRoot(isDialog()).perform(click()); awaitRender(app);
            assertTrue(helper.earlySellout(id,today));
            onView(withContentDescription(app.getString(R.string.early_sellout_for_bread,name)))
                    .perform(scrollTo(),click());
            onView(withText(R.string.save)).inRoot(isDialog()).perform(click()); awaitRender(app);
            assertFalse(helper.earlySellout(id,today));

            onView(withText(R.string.complete_day)).perform(scrollTo(),click());
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click()); awaitRender(app);
            assertEquals(DBHelper.DAY_COMPLETE,helper.dayStatus(today));
            assertFalse(helper.recordSale(id,today,1));
            onView(withText(R.string.reopen_day)).perform(scrollTo(),click());
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click()); awaitRender(app);
            assertEquals(DBHelper.DAY_OPEN,helper.dayStatus(today));

            onView(withText(R.string.reports)).perform(click()); awaitRender(app);
            onView(withText(R.string.daily)).perform(click()); awaitRender(app);
            onView(withContentDescription(app.getString(R.string.open_bread_detail,name)))
                    .perform(scrollTo(),click()); awaitRender(app);
            onView(withText(containsString("Üretilen: 20 · Satılan: 5"))).check(matches(isDisplayed()));
            onView(withText(R.string.close)).inRoot(isDialog()).perform(click());
            onView(withText(R.string.share_whatsapp)).perform(scrollTo(),click());
            onView(withText(R.string.whatsapp_missing)).inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withText(R.string.close)).inRoot(isDialog()).perform(click());

            onView(withText(R.string.breads)).perform(click()); awaitRender(app);
            onView(withContentDescription(app.getString(R.string.edit_bread_for,name)))
                    .perform(scrollTo(),click());
            onView(withText(R.string.archive)).inRoot(isDialog()).perform(click());
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click()); awaitRender(app);
            assertFalse(helper.getAllBreadTypes().stream().anyMatch(bread->name.equals(bread.getName())));
            boolean archivedHistoryFound=false;
            for(StockRow row:helper.report(today,today))if(row.id==id)archivedHistoryFound=true;
            assertTrue(archivedHistoryFound);
        } finally {
            helper.setDayStatus(today,DBHelper.DAY_OPEN);
            SQLiteDatabase db=helper.getWritableDatabase();
            if(id==0)try(Cursor c=db.rawQuery("SELECT id FROM BreadType WHERE name=?",new String[]{name})) {
                if(c.moveToFirst())id=c.getInt(0);
            }
            db.beginTransaction();
            try {
                if(id>0) {
                    String[] key={String.valueOf(id)};
                    db.delete("SalesRecord","bread_type_id=?",key);
                    db.delete("DailyStock","bread_type_id=?",key);
                    db.delete("BreadPlan","bread_type_id=?",key);
                    db.delete("PlanChange","bread_type_id=?",key);
                    db.delete("BreadType","id=?",key);
                }
                db.setTransactionSuccessful();
            } finally {db.endTransaction();}
            if(originalStatus!=DBHelper.DAY_OPEN)helper.setDayStatus(today,originalStatus);
            helper.close();
        }
    }
    private static void awaitRender(Context app) {
        CountDownLatch done=new CountDownLatch(1);
        BakeryRepository.get(app).executor().execute(done::countDown);
        try {assertTrue(done.await(20,TimeUnit.SECONDS));}
        catch(InterruptedException ex) {Thread.currentThread().interrupt();throw new AssertionError(ex);}
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
}
