package com.example.bakeryapp;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class ProductionAdvisorTest {
    private final BreadType rye=new BreadType(1,"Çavdar",40);

    private List<ProductionAdvisor.Observation> history(int weeks, boolean lowEveryDay) {
        List<ProductionAdvisor.Observation> days=new ArrayList<>();
        String monday="2026-07-27";
        for (int week=0;week<weeks;week++) for (int offset=0;offset<6;offset++) {
            int sold=lowEveryDay || offset==0 || offset==3 ? 10 : 30;
            days.add(new ProductionAdvisor.Observation(1,Dates.plusDays(monday,week*7+offset),40,sold,false,false));
        }
        return days;
    }

    @Test public void threeWeeksOfMondayAndThursdayShortfallProducesTwoSuggestions() {
        List<ProductionAdvisor.Advice> advice=ProductionAdvisor.assess(Collections.singletonList(rye),history(3,false));
        assertEquals(2,advice.size());
        assertEquals(java.util.Calendar.MONDAY,advice.get(0).weekday);
        assertEquals(java.util.Calendar.THURSDAY,advice.get(1).weekday);
        assertEquals(40,advice.get(0).averageProduced);
        assertEquals(10,advice.get(0).averageSold);
        assertEquals(26,advice.get(0).otherAverageSold);
        assertEquals(32,ProductionAdvisor.draftQuantity(advice.get(0),40));
    }

    @Test public void tooLittleDataOrGenerallyLowSalesGivesNoWeekdayAdvice() {
        assertTrue(ProductionAdvisor.assess(Collections.singletonList(rye),history(2,false)).isEmpty());
        assertTrue(ProductionAdvisor.assess(Collections.singletonList(rye),history(3,true)).isEmpty());
    }
    @Test public void unfinishedDaysNeverCountAsLowDemand() {
        List<ProductionAdvisor.Observation> days=new ArrayList<>();
        for(ProductionAdvisor.Observation day:history(4,false))days.add(new ProductionAdvisor.Observation(
                day.breadId,day.date,day.produced,day.sold,false,false,false,false));
        assertTrue(ProductionAdvisor.assess(Collections.singletonList(rye),days).isEmpty());
        assertTrue(ProductionAdvisor.weeklyChanges(Collections.singletonList(rye),days,"2026-08-25").isEmpty());
    }

    @Test public void alreadyReducedProductionGivesNoAdvice() {
        List<ProductionAdvisor.Observation> days=new ArrayList<>();
        for (ProductionAdvisor.Observation day:history(3,false)) {
            boolean low=day.sold==10;
            days.add(new ProductionAdvisor.Observation(1,day.date,low?12:40,day.sold,false,false));
        }
        assertTrue(ProductionAdvisor.assess(Collections.singletonList(rye),days).isEmpty());
    }

    @Test public void estimatedOrInconsistentHistorySuppressesAdvice() {
        List<ProductionAdvisor.Observation> days=history(3,false);
        ProductionAdvisor.Observation first=days.remove(0);
        days.add(new ProductionAdvisor.Observation(1,first.date,40,10,true,false));
        assertTrue(ProductionAdvisor.assess(Collections.singletonList(rye),days).isEmpty());
        days.remove(days.size()-1);
        days.add(new ProductionAdvisor.Observation(1,first.date,40,50,false,true));
        assertTrue(ProductionAdvisor.assess(Collections.singletonList(rye),days).isEmpty());
    }
    @Test public void meaningfulWeekOverWeekChangeUsesCompletedMatchingDays() {
        List<ProductionAdvisor.Observation> days=new ArrayList<>();
        for (int week=0;week<2;week++) for (int offset=0;offset<6;offset++) {
            String date=Dates.plusDays("2026-10-12",week*7+offset);
            days.add(new ProductionAdvisor.Observation(1,date,40,week==0?32:18,false,false));
        }
        List<ProductionAdvisor.WeekChange> changes=ProductionAdvisor.weeklyChanges(
                Collections.singletonList(rye),days,"2026-10-27");
        assertEquals(1,changes.size());
        assertEquals(192,changes.get(0).previousSold);
        assertEquals(108,changes.get(0).lastSold);
        assertTrue(changes.get(0).percent<=-40);
    }
    @Test public void smallChangeOrProductionChangeDoesNotShowTrend() {
        List<ProductionAdvisor.Observation> small=new ArrayList<>(),differentProduction=new ArrayList<>();
        for (int week=0;week<2;week++) for (int offset=0;offset<6;offset++) {
            String date=Dates.plusDays("2026-10-12",week*7+offset);
            small.add(new ProductionAdvisor.Observation(1,date,40,week==0?30:27,false,false));
            differentProduction.add(new ProductionAdvisor.Observation(1,date,week==0?40:25,week==0?30:18,false,false));
        }
        assertTrue(ProductionAdvisor.weeklyChanges(Collections.singletonList(rye),small,"2026-10-27").isEmpty());
        assertTrue(ProductionAdvisor.weeklyChanges(Collections.singletonList(rye),differentProduction,"2026-10-27").isEmpty());
    }
    @Test public void increaseIsReportedWithoutUsingTheIncompleteCurrentWeek() {
        List<ProductionAdvisor.Observation> days=new ArrayList<>();
        for (int week=0;week<2;week++) for (int offset=0;offset<6;offset++)
            days.add(new ProductionAdvisor.Observation(1,Dates.plusDays("2026-10-12",week*7+offset),40,
                    week==0?18:32,false,false));
        days.add(new ProductionAdvisor.Observation(1,"2026-10-26",40,0,false,false));
        List<ProductionAdvisor.WeekChange> changes=ProductionAdvisor.weeklyChanges(
                Collections.singletonList(rye),days,"2026-10-27");
        assertEquals(1,changes.size());
        assertEquals(108,changes.get(0).previousSold);
        assertEquals(192,changes.get(0).lastSold);
        assertTrue(changes.get(0).percent>0);
    }
    @Test public void savedReductionNeedsThreeMatchingDaysAndDoesNotCallSelloutSuccess() {
        ProductionAdvisor.PlanChange change=new ProductionAdvisor.PlanChange(1,"Çavdar",
                java.util.Calendar.MONDAY,40,32,"2026-09-14");
        List<ProductionAdvisor.Observation> days=new ArrayList<>();
        for(int week=1;week<=4;week++)days.add(new ProductionAdvisor.Observation(1,
                Dates.plusDays(change.changedOn,-week*7),40,10,false,false));
        for(int week=1;week<=2;week++)days.add(new ProductionAdvisor.Observation(1,
                Dates.plusDays(change.changedOn,week*7),32,10,false,false));
        List<ProductionAdvisor.PlanChange> changes=Collections.singletonList(change);
        assertEquals(ProductionAdvisor.Outcome.WAITING,
                ProductionAdvisor.followUps(changes,days,"2026-10-20").get(0).outcome);
        days.add(new ProductionAdvisor.Observation(1,"2026-10-05",32,10,false,false,false,false));
        assertEquals(ProductionAdvisor.Outcome.WAITING,
                ProductionAdvisor.followUps(changes,days,"2026-10-20").get(0).outcome);
        days.remove(days.size()-1);
        days.add(new ProductionAdvisor.Observation(1,"2026-10-05",32,10,false,false));
        ProductionAdvisor.FollowUp result=ProductionAdvisor.followUps(changes,days,"2026-10-20").get(0);
        assertEquals(ProductionAdvisor.Outcome.IMPROVED,result.outcome);
        assertEquals(30,result.beforeRemaining);
        assertEquals(22,result.afterRemaining);
        days.add(new ProductionAdvisor.Observation(1,"2026-10-12",32,32,false,false,true));
        assertEquals(ProductionAdvisor.Outcome.SELL_OUT,
                ProductionAdvisor.followUps(changes,days,"2026-10-20").get(0).outcome);
        assertTrue(ProductionAdvisor.assess(Collections.singletonList(rye),days).isEmpty());
    }
}
