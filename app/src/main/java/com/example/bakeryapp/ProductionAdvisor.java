package com.example.bakeryapp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ProductionAdvisor {
    static final class Observation {
        final int breadId;
        final String date;
        final long produced, sold;
        final boolean estimated, invalid, earlySellout, completed;

        Observation(int breadId, String date, long produced, long sold, boolean estimated, boolean invalid) {
            this(breadId,date,produced,sold,estimated,invalid,false,true);
        }
        Observation(int breadId, String date, long produced, long sold, boolean estimated, boolean invalid, boolean earlySellout) {
            this(breadId,date,produced,sold,estimated,invalid,earlySellout,true);
        }
        Observation(int breadId, String date, long produced, long sold, boolean estimated, boolean invalid,
                    boolean earlySellout,boolean completed) {
            this.breadId=breadId; this.date=date; this.produced=produced; this.sold=sold;
            this.estimated=estimated; this.invalid=invalid; this.earlySellout=earlySellout;this.completed=completed;
        }
    }

    static final class PlanChange {
        final int breadId, weekday, oldQuantity, newQuantity;
        final String breadName, changedOn;
        PlanChange(int breadId,String breadName,int weekday,int oldQuantity,int newQuantity,String changedOn) {
            this.breadId=breadId; this.breadName=breadName; this.weekday=weekday;
            this.oldQuantity=oldQuantity; this.newQuantity=newQuantity; this.changedOn=changedOn;
        }
    }
    enum Outcome { WAITING, INSUFFICIENT_BASELINE, SELL_OUT, NOT_APPLIED, IMPROVED, SALES_DOWN, NO_GAIN }
    static final class FollowUp {
        final PlanChange change;
        final int beforeDays, afterDays;
        final long beforeProduced, beforeSold, beforeRemaining, afterProduced, afterSold, afterRemaining;
        final Outcome outcome;
        FollowUp(PlanChange change,List<Observation> before,List<Observation> after,Outcome outcome) {
            this.change=change; this.beforeDays=before.size(); this.afterDays=after.size(); this.outcome=outcome;
            long bp=0,bs=0,ap=0,as=0;
            for(Observation day:before) {bp+=day.produced;bs+=day.sold;}
            for(Observation day:after) {ap+=day.produced;as+=day.sold;}
            beforeProduced=average(bp,beforeDays); beforeSold=average(bs,beforeDays);
            afterProduced=average(ap,afterDays); afterSold=average(as,afterDays);
            beforeRemaining=average(bp-bs,beforeDays); afterRemaining=average(ap-as,afterDays);
        }
    }

    static final class Advice {
        final int breadId;
        final String breadName;
        final int weekday, samples, averageProduced, averageSold, otherAverageSold, maxSold;

        Advice(int breadId, String breadName, int weekday, int samples, int averageProduced, int averageSold,
               int otherAverageSold, int maxSold) {
            this.breadId=breadId;
            this.breadName=breadName; this.weekday=weekday; this.samples=samples;
            this.averageProduced=averageProduced; this.averageSold=averageSold;
            this.otherAverageSold=otherAverageSold; this.maxSold=maxSold;
        }
    }

    static final class WeekChange {
        final String breadName, weekStart;
        final long previousSold, lastSold;
        final int percent;

        WeekChange(String breadName, String weekStart, long previousSold, long lastSold) {
            this.breadName=breadName; this.weekStart=weekStart;
            this.previousSold=previousSold; this.lastSold=lastSold;
            this.percent=(int)Math.round(100.0*(lastSold-previousSold)/previousSold);
        }
    }

    private static final class Totals {
        int count, lowDays;
        long produced, sold, maxSold;
        void add(Observation day) {
            count++; produced+=day.produced; sold+=day.sold;
            maxSold=Math.max(maxSold,day.sold);
            if (day.sold * 100 <= day.produced * 60) lowDays++;
        }
    }

    private ProductionAdvisor() {}

    static List<Advice> assess(List<BreadType> active, List<Observation> history) {
        Map<Integer,List<Observation>> byBread=new HashMap<>();
        for (Observation day:history) byBread.computeIfAbsent(day.breadId,k->new ArrayList<>()).add(day);
        List<Advice> result=new ArrayList<>();
        for (BreadType bread:active) {
            List<Observation> days=byBread.get(bread.getId());
            if (days==null) continue;
            Totals[] weekday=new Totals[8];
            for (int i=1;i<=7;i++) weekday[i]=new Totals();
            boolean unreliable=false;
            for (Observation day:days) {
                if(!day.completed)continue;
                if (day.estimated || day.invalid || day.produced<0 || day.sold<0 || day.sold>day.produced ||
                        day.earlySellout || (day.produced>0 && day.sold==day.produced)) {
                    unreliable=true; break;
                }
                if (day.produced>0) weekday[dayOfWeek(day.date)].add(day);
            }
            if (unreliable) continue;
            for (int target=1;target<=7;target++) {
                Totals own=weekday[target];
                if (own.count<3 || own.lowDays*4<own.count*3) continue;
                long otherProduced=0, otherSold=0;
                int otherCount=0, comparisonDays=0;
                for (int d=1;d<=7;d++) if (d!=target) {
                    Totals other=weekday[d];
                    otherProduced+=other.produced; otherSold+=other.sold; otherCount+=other.count;
                    if (other.count>=2) comparisonDays++;
                }
                if (otherCount<9 || comparisonDays<3 || otherProduced==0) continue;
                double averageProduced=(double)own.produced/own.count;
                double averageSold=(double)own.sold/own.count;
                double otherAverageProduced=(double)otherProduced/otherCount;
                double otherAverageSold=(double)otherSold/otherCount;
                if (averageProduced<otherAverageProduced*.8 ||
                        averageSold>otherAverageSold*.6 ||
                        (double)own.sold/own.produced>.55 ||
                        (double)otherSold/otherProduced<.60 ||
                        averageProduced-averageSold<Math.max(8,averageProduced*.25)) continue;
                result.add(new Advice(bread.getId(),bread.getName(),target,own.count,
                        (int)Math.round(averageProduced),(int)Math.round(averageSold),
                        (int)Math.round(otherAverageSold),(int)own.maxSold));
            }
        }
        return result;
    }

    static int draftQuantity(Advice advice,int currentPlan) {
        int gradual=(int)Math.ceil(currentPlan*.8);
        int demand=(int)Math.min(Integer.MAX_VALUE,Math.ceil(advice.maxSold*1.2));
        return Math.min(currentPlan,Math.max(gradual,demand));
    }

    private static long average(long total,int count) { return count==0?0:Math.round((double)total/count); }
    static List<FollowUp> followUps(List<PlanChange> changes,List<Observation> history,String today) {
        List<FollowUp> result=new ArrayList<>();
        for(PlanChange change:changes) {
            List<Observation> before=new ArrayList<>(),after=new ArrayList<>();
            String earliest=Dates.plusDays(change.changedOn,-35), latest=Dates.plusDays(change.changedOn,35);
            for(Observation day:history) {
                if(day.breadId!=change.breadId || day.date.compareTo(earliest)<0 ||
                        day.date.compareTo(latest)>0 || day.date.compareTo(today)>=0 ||
                        dayOfWeek(day.date)!=change.weekday || !day.completed || day.estimated || day.invalid ||
                        day.produced<=0 || day.sold<0 || day.sold>day.produced)continue;
                if(day.date.compareTo(change.changedOn)<0) {
                    before.add(day);
                    if(before.size()>4)before.remove(0);
                } else if(day.date.compareTo(change.changedOn)>0 && after.size()<4) after.add(day);
            }
            Outcome outcome;
            if(before.size()<3)outcome=Outcome.INSUFFICIENT_BASELINE;
            else if(hasSellout(before))outcome=Outcome.INSUFFICIENT_BASELINE;
            else if(hasSellout(after))outcome=Outcome.SELL_OUT;
            else if(after.size()<3)outcome=Outcome.WAITING;
            else {
                long bp=sumProduced(before),bs=sumSold(before),ap=sumProduced(after),as=sumSold(after);
                long br=bp-bs,ar=ap-as;
                if(ap*before.size()>bp*after.size()*.95)outcome=Outcome.NOT_APPLIED;
                else if(as*before.size()<bs*after.size()*.9)outcome=Outcome.SALES_DOWN;
                else if(ar*before.size()<=br*after.size()*.8)outcome=Outcome.IMPROVED;
                else outcome=Outcome.NO_GAIN;
            }
            result.add(new FollowUp(change,before,after,outcome));
        }
        return result;
    }
    private static boolean hasSellout(List<Observation> days) {
        for(Observation day:days)if(day.earlySellout || day.sold==day.produced)return true;
        return false;
    }
    private static long sumProduced(List<Observation> days) {long sum=0;for(Observation day:days)sum+=day.produced;return sum;}
    private static long sumSold(List<Observation> days) {long sum=0;for(Observation day:days)sum+=day.sold;return sum;}

    static List<WeekChange> weeklyChanges(List<BreadType> active, List<Observation> history, String today) {
        String currentMonday=Dates.weekStart(today);
        String lastMonday=Dates.plusDays(currentMonday,-7);
        String previousMonday=Dates.plusDays(currentMonday,-14);
        Map<Integer,Map<String,Observation>> byBread=new HashMap<>();
        for (Observation day:history) {
            if (day.completed && day.date.compareTo(previousMonday)>=0 && day.date.compareTo(currentMonday)<0)
                byBread.computeIfAbsent(day.breadId,k->new HashMap<>()).put(day.date,day);
        }
        List<WeekChange> result=new ArrayList<>();
        for (BreadType bread:active) {
            Map<String,Observation> days=byBread.get(bread.getId());
            if (days==null) continue;
            boolean unreliable=false;
            for (Observation day:days.values()) if (day.estimated || day.invalid || day.produced<0 ||
                    day.sold<0 || day.sold>day.produced) { unreliable=true; break; }
            if (unreliable) continue;
            int matched=0;
            long previousProduced=0,lastProduced=0,previousSold=0,lastSold=0;
            for (int offset=0;offset<7;offset++) {
                Observation before=days.get(Dates.plusDays(previousMonday,offset));
                Observation recent=days.get(Dates.plusDays(lastMonday,offset));
                if (before==null || recent==null || before.produced<=0 || recent.produced<=0) continue;
                matched++;
                previousProduced+=before.produced; lastProduced+=recent.produced;
                previousSold+=before.sold; lastSold+=recent.sold;
            }
            if (matched<4 || previousProduced==0 || previousSold<20 ||
                    Math.abs(lastProduced-previousProduced)>previousProduced*.15 ||
                    Math.abs(lastSold-previousSold)<Math.max(20,previousSold*.25)) continue;
            result.add(new WeekChange(bread.getName(),lastMonday,previousSold,lastSold));
        }
        return result;
    }

    static int dayOfWeek(String date) {
        return Dates.dayOfWeek(date);
    }
}
