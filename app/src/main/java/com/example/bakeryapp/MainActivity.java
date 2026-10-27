package com.example.bakeryapp;

import android.app.DatePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textfield.TextInputEditText;
import java.util.*;
import java.util.concurrent.ExecutorService;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final String[] DAY_NAMES={"", "Pazar", "Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi"};
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private String loadedDate;
    private int todayStatus=DBHelper.DAY_OPEN;
    private final Runnable dayChanged = () -> {
        if (this.tab == 0) refresh();
        scheduleDayChange();
    };
    private DBHelper db;
    private ExecutorService worker;
    private Typeface display;
    private LinearLayout root,content,nav;
    private int tab=0, generation=0;
    private boolean weekly=true, monthly=false, customRange=false;
    private String rangeEnd=Dates.today();
    private String selectedDate=Dates.today();
    private String firstRecordDate=Dates.today();
    private List<BreadType> breads=new ArrayList<>();
    private List<StockRow> rows=new ArrayList<>();
    private List<ProductionAdvisor.Advice> advice=new ArrayList<>();
    private List<ProductionAdvisor.WeekChange> weekChanges=new ArrayList<>();
    private List<ProductionAdvisor.FollowUp> followUps=new ArrayList<>();
    private Map<Integer,int[]> weeklyPlans=new HashMap<>();
    private Map<Integer,Integer> recordedPlans=new HashMap<>();
    private Map<Integer,Boolean> earlySellouts=new HashMap<>();
    private final Map<String,Long> trend=new LinkedHashMap<>();

    @Override protected void attachBaseContext(Context base) {
        Locale.setDefault(Dates.TURKISH);
        Configuration config=new Configuration(base.getResources().getConfiguration());
        config.setLocale(Dates.TURKISH);
        super.attachBaseContext(base.createConfigurationContext(config));
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        BakeryRepository repository=BakeryRepository.get(this);
        db=repository.database();
        worker=repository.executor();
        display=ResourcesCompat.getFont(this,R.font.dm_serif_display);
        if(state!=null) { tab=state.getInt("tab"); weekly=state.getBoolean("weekly",true); monthly=state.getBoolean("monthly"); customRange=state.getBoolean("range"); rangeEnd=state.getString("rangeEnd",Dates.today()); selectedDate=state.getString("date",Dates.today()); }
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(color(R.color.canvas));
        setContentView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root,(v,insets)->{
            Insets bars=insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left,bars.top,bars.right,bars.bottom); return insets;
        });
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true);
        content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(24),dp(28),dp(24),dp(24));
        scroll.addView(content); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        nav=new LinearLayout(this); nav.setPadding(dp(12),dp(8),dp(12),dp(8)); nav.setBackgroundColor(color(R.color.surface)); root.addView(nav);
        navigation();
        DebugData.initialize(db,worker);
    }
    @Override protected void onResume() {
        super.onResume();
        refresh();
        scheduleDayChange();
    }
    @Override protected void onStop() {
        mainHandler.removeCallbacks(dayChanged);
        super.onStop();
    }
    private void scheduleDayChange() {
        mainHandler.removeCallbacks(dayChanged);
        Calendar next = Dates.calendar();
        next.add(Calendar.DATE,1);
        next.set(Calendar.HOUR_OF_DAY,0);
        next.set(Calendar.MINUTE,0);
        next.set(Calendar.SECOND,0);
        next.set(Calendar.MILLISECOND,0);
        mainHandler.postDelayed(dayChanged,Math.max(100,next.getTimeInMillis()-System.currentTimeMillis()+100));
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out); out.putInt("tab",tab); out.putBoolean("weekly",weekly); out.putBoolean("monthly",monthly); out.putBoolean("range",customRange); out.putString("rangeEnd",rangeEnd); out.putString("date",selectedDate);
    }
    @Override protected void onDestroy() {
        mainHandler.removeCallbacks(dayChanged);
        super.onDestroy();
    }
    private int color(int id) { return androidx.core.content.ContextCompat.getColor(this,id); }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    private void toast(int id) { if(!isDestroyed()) Snackbar.make(root,id,Snackbar.LENGTH_LONG).setAnchorView(nav).show(); }
    private GradientDrawable background(int fill,int radius) { GradientDrawable d=new GradientDrawable(); d.setColor(color(fill)); d.setCornerRadius(dp(radius)); return d; }
    private GradientDrawable panel(int radius) { GradientDrawable d=background(R.color.surface,radius); d.setStroke(dp(1),color(R.color.line)); return d; }
    private TextView text(CharSequence value,int size,int tint,boolean bold) {
        TextView t=new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color(tint));
        if(bold) t.setTypeface(size>=19 && display!=null?display:Typeface.create("sans-serif-medium",Typeface.NORMAL));
        t.setPadding(0,dp(4),0,dp(8)); return t;
    }
    private CharSequence brand() {
        String value=getString(R.string.brand);
        SpannableString styled=new SpannableString(value);
        int word=value.indexOf(' '), dot=value.indexOf('·');
        if(word>0) styled.setSpan(new ForegroundColorSpan(color(R.color.brand_red)),word+1,dot>word?dot:value.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if(dot>0) styled.setSpan(new ForegroundColorSpan(color(R.color.muted)),dot,value.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return styled;
    }
    private void breadTitle(LinearLayout parent,String name,int size) {
        LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon=new ImageView(this); icon.setImageResource(BreadIcon.of(name));
        icon.setImageTintList(ColorStateList.valueOf(color(R.color.bread_icon)));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams iconParams=new LinearLayout.LayoutParams(dp(30),dp(30)); iconParams.rightMargin=dp(12);
        row.addView(icon,iconParams); row.addView(text(name,size,R.color.ink,true),new LinearLayout.LayoutParams(0,-2,1));
        parent.addView(row);
    }
    private void illustration(LinearLayout parent) {
        ImageView art=new ImageView(this); art.setImageResource(R.drawable.illus_stone_oven);
        art.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(120)); params.bottomMargin=dp(8);
        parent.addView(art,params);
    }
    private void space(LinearLayout parent,int size) { View v=new View(this); parent.addView(v,new LinearLayout.LayoutParams(1,dp(size))); }
    private MaterialButton button(int title,Runnable click) {
        MaterialButton b=new MaterialButton(this); b.setText(title); b.setAllCaps(false); b.setCornerRadius(dp(16)); b.setMinHeight(dp(52)); b.setOnClickListener(v->click.run()); return b;
    }
    private void secondary(MaterialButton b) {
        b.setTextColor(color(R.color.bread_icon)); b.setBackgroundTintList(ColorStateList.valueOf(color(R.color.soft)));
    }
    private LinearLayout card(LinearLayout parent,boolean dark) {
        LinearLayout c=new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setPadding(dp(20),dp(18),dp(20),dp(18)); c.setBackground(dark?background(R.color.crust,24):panel(24));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.bottomMargin=dp(14); parent.addView(c,p); return c;
    }
    private void navigation() {
        nav.removeAllViews(); int[] labels={R.string.today,R.string.breads,R.string.reports,R.string.advice_tab};
        int[] icons={R.drawable.ic_nav_oven,R.drawable.ic_bread_loaf,R.drawable.ic_nav_ledger,R.drawable.ic_nav_wheat};
        for(int i=0;i<labels.length;i++) { final int index=i; MaterialButton b=button(labels[i],()->{tab=index; navigation(); refresh();});
            ColorStateList tint=ColorStateList.valueOf(color(tab==i?R.color.on_primary:R.color.muted));
            b.setTextColor(tint); b.setBackgroundTintList(ColorStateList.valueOf(color(tab==i?R.color.primary:R.color.surface)));
            b.setIconResource(icons[i]); b.setIconTint(tint); b.setIconGravity(MaterialButton.ICON_GRAVITY_TOP);
            b.setIconSize(dp(22)); b.setIconPadding(dp(2)); b.setPadding(dp(4),dp(6),dp(4),dp(6));
            nav.addView(b,new LinearLayout.LayoutParams(0,-2,1)); }
    }
    private String start() { return tab==2 && weekly?Dates.weekStart(selectedDate):tab==2 && monthly?Dates.monthStart(selectedDate):(tab==0?Dates.today():selectedDate); }
    private String end() {
        if(tab==2 && customRange) return rangeEnd;
        if(tab==2 && weekly) { String last=Dates.plusDays(start(),6); return last.compareTo(Dates.today())>0?Dates.today():last; }
        if(tab==2 && monthly) { String last=Dates.monthEnd(selectedDate); return last.compareTo(Dates.today())>0?Dates.today():last; }
        return start();
    }
    private void refresh() {
        final int request=++generation; final String from=start(),to=end(); final boolean showTrend=tab==2 && weekly, showAdvice=tab==3, showToday=tab==0;
        content.removeAllViews(); content.addView(text(getString(R.string.loading),16,R.color.muted,false));
        worker.execute(()->{
            try {
                List<BreadType> types=db.getAllBreadTypes(); List<StockRow> report=showAdvice?Collections.emptyList():db.report(from,to); Map<String,Long> days=new LinkedHashMap<>();
                String first=tab==2?db.firstRecordDate():null;
                int status=showToday?db.dayStatus(from):DBHelper.DAY_OPEN;
                Map<Integer,int[]> plans=new HashMap<>(); Map<Integer,Integer> snapshots=new HashMap<>();
                Map<Integer,Boolean> sellouts=new HashMap<>();
                for(BreadType type:types) {
                    plans.put(type.getId(),db.weeklyPlan(type.getId()));
                    if(showToday) { snapshots.put(type.getId(),db.recordedPlan(type.getId(),from));
                        sellouts.put(type.getId(),db.earlySellout(type.getId(),from)); }
                }
                List<ProductionAdvisor.Observation> history=showAdvice?db.adviceHistory(Dates.plusDays(Dates.today(),-28),Dates.plusDays(Dates.today(),-1)):Collections.emptyList();
                List<ProductionAdvisor.Advice> suggestions=showAdvice?ProductionAdvisor.assess(types,history):Collections.emptyList();
                List<ProductionAdvisor.WeekChange> changes=showAdvice?ProductionAdvisor.weeklyChanges(types,history,Dates.today()):Collections.emptyList();
                List<ProductionAdvisor.PlanChange> planChanges=showAdvice?db.latestPlanChanges():Collections.emptyList();
                List<ProductionAdvisor.Observation> followHistory=Collections.emptyList();
                if(!planChanges.isEmpty()) {
                    String earliest=planChanges.get(0).changedOn;
                    for(ProductionAdvisor.PlanChange change:planChanges)if(change.changedOn.compareTo(earliest)<0)earliest=change.changedOn;
                    followHistory=db.adviceHistory(Dates.plusDays(earliest,-35),Dates.plusDays(Dates.today(),-1));
                }
                List<ProductionAdvisor.FollowUp> outcomes=ProductionAdvisor.followUps(planChanges,followHistory,Dates.today());
                if(showTrend) for(int i=0;i<7;i++) { String date=Dates.plusDays(from,i); if(date.compareTo(to)>0)break; days.put(date,ReportTotals.from(db.report(date,date)).sold); }
                runOnUiThread(()->{ if(isDestroyed() || request!=generation)return; loadedDate=from; todayStatus=status; firstRecordDate=first==null?Dates.today():first; breads=types; rows=report; advice=suggestions; weekChanges=changes; followUps=outcomes; weeklyPlans=plans; recordedPlans=snapshots; earlySellouts=sellouts; trend.clear(); trend.putAll(days); render(); });
            } catch(Exception e) { Log.e(TAG,"Loading records failed",e); runOnUiThread(()->{ if(isDestroyed() || request!=generation)return; content.removeAllViews(); content.addView(text(getString(R.string.db_error),16,R.color.ink,false)); content.addView(button(R.string.retry,this::refresh)); }); }
        });
    }
    private void render() {
        content.removeAllViews(); TextView brand=text(brand(),15,R.color.ink,false); brand.setTypeface(display); brand.setLetterSpacing(.08f); content.addView(brand);
        int title=tab==0?R.string.today_title:tab==1?R.string.bread_title:tab==2?R.string.report_title:R.string.advice_title;
        content.addView(text(getString(title),32,R.color.ink,true)); space(content,16);
        if(tab==1) { renderBreads(); return; }
        if(tab==3) { renderAdvice(); return; }
        if(tab==2) {
            LinearLayout modes=new LinearLayout(this);
            MaterialButton daily=button(R.string.daily,()->{weekly=false;monthly=false;customRange=false;refresh();});
            MaterialButton week=button(R.string.weekly,()->{weekly=true;monthly=false;customRange=false;refresh();});
            MaterialButton month=button(R.string.monthly,()->{weekly=false;monthly=true;customRange=false;refresh();});
            if(weekly || monthly || customRange) secondary(daily);
            if(!weekly) secondary(week);
            if(!monthly) secondary(month);
            modes.addView(daily,new LinearLayout.LayoutParams(0,-2,1)); modes.addView(week,new LinearLayout.LayoutParams(0,-2,1)); modes.addView(month,new LinearLayout.LayoutParams(0,-2,1)); content.addView(modes);
            if(weekly || monthly)renderPeriodChoices();
            else {
                MaterialButton date=button(R.string.daily,this::pickDate);
                date.setText(Dates.display(selectedDate,"dd MMMM yyyy")); content.addView(date);
            }
            if(weekly) content.addView(text(getString(R.string.week_starts_monday),13,R.color.muted,false));
            MaterialButton range=button(R.string.custom_range,()->{
                weekly=false; monthly=false; customRange=true;
                if(rangeEnd.compareTo(selectedDate)<0)rangeEnd=selectedDate;
                refresh();
            });
            if(!customRange) secondary(range);
            content.addView(range);
            if(customRange) {
                MaterialButton last=button(R.string.range_end,()->pickDate(true));
                last.setText(getString(R.string.range_end)+": "+Dates.display(rangeEnd,"dd MMM yyyy"));
                content.addView(last);
            }
        } else content.addView(text(Dates.display(Dates.today(),"dd MMMM yyyy · EEEE"),14,R.color.muted,false));
        summary();
        if(tab==2) renderHighlights();
        if(tab==0) {
            renderDayClose();
            content.addView(text(getString(R.string.daily_stock),20,R.color.ink,true));
            if(breads.isEmpty()) empty();
            for(BreadType bread:breads) breadCard(bread,false);
            Set<Integer> activeIds=new HashSet<>();
            for(BreadType bread:breads)activeIds.add(bread.getId());
            for(StockRow row:rows)if(!activeIds.contains(row.id))reportCard(row,true);
        } else {
            content.addView(text(getString(R.string.report_note),13,R.color.muted,false));
            if(rows.isEmpty()) { LinearLayout c=card(content,false); illustration(c); c.addView(text(getString(R.string.empty_report),17,R.color.ink,false)); }
            for(StockRow row:rows)reportCard(row,false);
            if(weekly && !rows.isEmpty() && !ReportTotals.from(rows).inconsistent) { content.addView(text(getString(R.string.week_trend),20,R.color.ink,true));
                long max=1; for(long n:trend.values())max=Math.max(max,n);
                for(Map.Entry<String,Long> day:trend.entrySet()) { content.addView(text(Dates.display(day.getKey(),"EEE dd")+" · "+day.getValue(),13,R.color.muted,false)); ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); bar.setProgress((int)(100.0*day.getValue()/max)); bar.setProgressTintList(ColorStateList.valueOf(color(R.color.primary))); content.addView(bar,new LinearLayout.LayoutParams(-1,dp(8))); space(content,8); }
            }
            if(!rows.isEmpty()) content.addView(button(R.string.share_whatsapp,this::shareWhatsApp));
        }
    }
    private void renderDayClose() {
        LinearLayout c=card(content,false);
        c.addView(text(getString(R.string.day_close_title),19,R.color.ink,true));
        if(todayStatus==DBHelper.DAY_COMPLETE) {
            c.addView(text(getString(R.string.day_complete_note),14,R.color.primary,false));
            c.addView(button(R.string.reopen_day,()->confirmDayStatus(DBHelper.DAY_OPEN)));
        } else if(todayStatus==DBHelper.DAY_NO_PRODUCTION) {
            c.addView(text(getString(R.string.no_production_note),14,R.color.muted,false));
            c.addView(button(R.string.reopen_day,()->confirmDayStatus(DBHelper.DAY_OPEN)));
        } else {
            c.addView(text(getString(R.string.day_open_note),14,R.color.muted,false));
            boolean produced=false;
            for(StockRow row:rows)if(row.hasConfirmedStock() && row.produced>0) {produced=true;break;}
            int next=produced?DBHelper.DAY_COMPLETE:DBHelper.DAY_NO_PRODUCTION;
            c.addView(button(produced?R.string.complete_day:R.string.no_production_day,
                    ()->confirmDayStatus(next)));
        }
    }
    private int noProductionLabel(String date) {
        return date.equals(Dates.today())?R.string.no_production_day:R.string.mark_no_production;
    }
    private void confirmDayStatus(int status) {
        confirmDayStatus(Dates.today(),status,null);
    }
    private void confirmDayStatus(String date,int status,AlertDialog parent) {
        int title=status==DBHelper.DAY_COMPLETE?R.string.complete_day:
                status==DBHelper.DAY_NO_PRODUCTION?noProductionLabel(date):R.string.reopen_day;
        int message=status==DBHelper.DAY_COMPLETE?R.string.complete_day_help:
                status==DBHelper.DAY_NO_PRODUCTION?R.string.no_production_help:R.string.reopen_day_help;
        AlertDialog dialog=new MaterialAlertDialogBuilder(this).setTitle(title).setMessage(message)
                .setNegativeButton(R.string.cancel,null).setPositiveButton(title,null).create();
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(parent==null && !date.equals(Dates.today())) {dialog.dismiss();toast(R.string.day_changed);refresh();return;}
            save(dialog,()->db.setDayStatus(date,status),()->{if(parent!=null)parent.dismiss();});
        }));
        dialog.show();
    }
    private void renderPeriodChoices() {
        content.addView(text(getString(weekly?R.string.choose_week:R.string.choose_month),14,R.color.muted,true));
        HorizontalScrollView scroll=new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout choices=new LinearLayout(this);
        choices.setOrientation(LinearLayout.HORIZONTAL);
        scroll.addView(choices);
        String first=weekly?Dates.weekStart(firstRecordDate):Dates.monthStart(firstRecordDate);
        String current=weekly?Dates.weekStart(Dates.today()):Dates.monthStart(Dates.today());
        String selected=weekly?Dates.weekStart(selectedDate):Dates.monthStart(selectedDate);
        MaterialButton selectedButton=null;
        for(String period=first;period.compareTo(current)<=0;
                period=weekly?Dates.plusDays(period,7):Dates.plusDays(Dates.monthEnd(period),1)) {
            final String value=period;
            String label;
            if(weekly)label=getString(R.string.period,Dates.display(period,"dd MMM"),
                    Dates.display(Dates.plusDays(period,6),"dd MMM yyyy"));
            else {
                String month=Dates.display(period,"MMMM");
                label=period.substring(0,4)+" · "+month.substring(0,1).toUpperCase(Dates.TURKISH)+month.substring(1);
            }
            if(period.equals(current))label=getString(weekly?R.string.this_week:R.string.this_month)+" · "+label;
            MaterialButton choice=button(weekly?R.string.weekly:R.string.monthly,()->{selectedDate=value;refresh();});
            choice.setText(label);
            choice.setTextColor(color(period.equals(selected)?R.color.on_primary:R.color.ink));
            choice.setBackgroundTintList(ColorStateList.valueOf(color(period.equals(selected)?R.color.primary:R.color.surface)));
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-2,-2);params.rightMargin=dp(8);
            choices.addView(choice,params);
            if(period.equals(selected))selectedButton=choice;
        }
        content.addView(scroll);
        if(!first.equals(current))content.addView(text(getString(R.string.period_swipe_hint),12,R.color.muted,false));
        if(selectedButton!=null) {
            MaterialButton focus=selectedButton;
            scroll.post(()->scroll.scrollTo(Math.max(0,focus.getLeft()-dp(24)),0));
        }
    }
    private void renderHighlights() {
        ReportHighlights highlights=ReportHighlights.from(rows);
        if(highlights.isEmpty()) return;
        List<String[]> items=new ArrayList<>();
        if(highlights.highestRate!=null) items.add(new String[]{getString(R.string.highest_rate),
                highlights.highestRate.name,ReportHighlights.percent(highlights.highestRate.sold,highlights.highestRate.produced)});
        if(highlights.lowestRate!=null) items.add(new String[]{getString(R.string.lowest_rate),
                highlights.lowestRate.name,getString(R.string.rate_with_remaining,
                ReportHighlights.percent(highlights.lowestRate.sold,highlights.lowestRate.produced),
                ReportHighlights.percent(highlights.lowestRate.remaining(),highlights.lowestRate.produced))});
        if(highlights.mostSold!=null) items.add(new String[]{getString(R.string.most_sold),
                highlights.mostSold.name,getString(R.string.pieces,highlights.mostSold.sold)});
        if(highlights.mostRemaining!=null) items.add(new String[]{getString(R.string.most_remaining),
                highlights.mostRemaining.name,getString(R.string.pieces,highlights.mostRemaining.remaining())});
        content.addView(text(getString(R.string.report_highlights),20,R.color.ink,true));
        for(int i=0;i<items.size();i+=2) {
            LinearLayout pair=new LinearLayout(this);
            for(int j=i;j<Math.min(i+2,items.size());j++) {
                String[] item=items.get(j);
                LinearLayout tile=new LinearLayout(this); tile.setOrientation(LinearLayout.VERTICAL);
                tile.setMinimumHeight(dp(128)); tile.setPadding(dp(16),dp(14),dp(14),dp(12));
                tile.setBackground(panel(20));
                tile.addView(text(item[0],12,R.color.muted,true));
                tile.addView(text(item[1],17,R.color.ink,true));
                tile.addView(text(item[2],15,R.color.primary,true));
                LinearLayout.LayoutParams tileParams=new LinearLayout.LayoutParams(0,-2,1);
                if(j==i) tileParams.rightMargin=dp(8);
                pair.addView(tile,tileParams);
            }
            LinearLayout.LayoutParams pairParams=new LinearLayout.LayoutParams(-1,-2);
            pairParams.bottomMargin=dp(10);
            content.addView(pair,pairParams);
        }
    }
    private void renderAdvice() {
        LinearLayout banner=card(content,true);
        banner.addView(text(getString(R.string.advice_window),13,R.color.white,true));
        banner.addView(text(getString(R.string.advice_method),15,R.color.white,false));
        if(advice.isEmpty() && weekChanges.isEmpty() && followUps.isEmpty()) {
            LinearLayout c=card(content,false);
            illustration(c);
            c.addView(text(getString(R.string.no_advice),17,R.color.ink,false));
            return;
        }
        if(!followUps.isEmpty()) {
            content.addView(text(getString(R.string.plan_results_section),20,R.color.ink,true));
            for(ProductionAdvisor.FollowUp follow:followUps) {
                LinearLayout c=card(content,false);
                c.addView(text(getString(R.string.advice_heading,follow.change.breadName,DAY_NAMES[follow.change.weekday]),20,R.color.ink,true));
                c.addView(text(getString(R.string.plan_change_line,follow.change.oldQuantity,follow.change.newQuantity,
                        Dates.display(follow.change.changedOn,"dd MMM yyyy")),14,R.color.muted,false));
                int title=follow.outcome==ProductionAdvisor.Outcome.IMPROVED?R.string.plan_result_improved:
                        follow.outcome==ProductionAdvisor.Outcome.SELL_OUT?R.string.plan_result_sellout:
                        follow.outcome==ProductionAdvisor.Outcome.SALES_DOWN?R.string.plan_result_sales_down:
                        follow.outcome==ProductionAdvisor.Outcome.NOT_APPLIED?R.string.plan_result_not_applied:
                        follow.outcome==ProductionAdvisor.Outcome.NO_GAIN?R.string.plan_result_no_gain:
                        follow.outcome==ProductionAdvisor.Outcome.INSUFFICIENT_BASELINE?R.string.plan_result_no_baseline:R.string.plan_result_waiting;
                c.addView(text(getString(title),16,follow.outcome==ProductionAdvisor.Outcome.IMPROVED?R.color.primary:R.color.accent,true));
                c.addView(text(getString(R.string.plan_result_samples,follow.beforeDays,follow.afterDays),13,R.color.muted,false));
                if(follow.beforeDays>0 && follow.afterDays>0)c.addView(text(getString(R.string.plan_result_values,
                        follow.beforeProduced,follow.beforeSold,follow.beforeRemaining,
                        follow.afterProduced,follow.afterSold,follow.afterRemaining),14,R.color.muted,false));
            }
        }
        if(!weekChanges.isEmpty()) {
            content.addView(text(getString(R.string.weekly_change_section),20,R.color.ink,true));
            for(ProductionAdvisor.WeekChange change:weekChanges) {
                LinearLayout c=card(content,false);
                c.addView(text(change.breadName,20,R.color.ink,true));
                c.addView(text(getString(change.percent<0?R.string.sales_down:R.string.sales_up,Math.abs(change.percent)),22,
                        change.percent<0?R.color.accent:R.color.primary,true));
                c.addView(text(getString(R.string.weekly_change_evidence,change.previousSold,change.lastSold),14,R.color.muted,false));
            }
        }
        if(advice.isEmpty()) return;
        content.addView(text(getString(R.string.weekday_advice_section),20,R.color.ink,true));
        for(ProductionAdvisor.Advice item:advice) {
            String day=DAY_NAMES[item.weekday];
            LinearLayout c=card(content,false);
            c.addView(text(getString(R.string.advice_heading,item.breadName,day),20,R.color.ink,true));
            c.addView(text(getString(R.string.advice_evidence,item.samples,item.averageProduced,item.averageSold,item.otherAverageSold),14,R.color.muted,false));
            BreadType bread=null; for(BreadType candidate:breads)if(candidate.getId()==item.breadId)bread=candidate;
            int[] plan=weeklyPlans.get(item.breadId);
            int current=plan==null?item.averageProduced:plan[item.weekday];
            if(bread!=null && current<bread.getDailyTotalQuantity())
                c.addView(text(getString(R.string.plan_monitoring,day,current),15,R.color.primary,true));
            else {
                c.addView(text(getString(R.string.advice_action,day,item.breadName),15,R.color.primary,true));
                if(bread!=null) {
                    BreadType selected=bread;
                    MaterialButton action=button(R.string.edit_week_plan,()->editDayPlan(selected,item.weekday,item));
                    action.setContentDescription(getString(R.string.advice_plan_button,day,item.breadName));
                    c.addView(action);
                }
            }
        }
    }
    private void summary() {
        ReportTotals totals=ReportTotals.from(rows);
        LinearLayout c=card(content,true); c.addView(text(getString(R.string.remaining),14,R.color.white,false));
        c.addView(text(totals.confirmedStock?String.format(Dates.TURKISH,"%,d",totals.remaining()):getString(R.string.unknown_quantity),48,R.color.white,true));
        c.addView(text(totals.confirmedStock?getString(R.string.stock_detail,totals.produced,totals.sold,totals.remaining()):getString(R.string.unconfirmed_stock_detail,totals.produced,totals.sold),14,R.color.white,false));
        if(!totals.confirmedStock)c.addView(text(getString(R.string.unconfirmed_totals),13,R.color.white,false));
        if(totals.inconsistent)c.addView(text(getString(R.string.inconsistent_records),13,R.color.white,false));
        if(totals.hasSalesRate()) { int percent=totals.salesPercent(); ProgressBar p=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); p.setProgress(percent); p.setProgressTintList(ColorStateList.valueOf(color(R.color.wheat))); c.addView(p,new LinearLayout.LayoutParams(-1,dp(6))); c.addView(text(getString(R.string.rate,percent),12,R.color.white,false)); }
    }
    private String stockDetail(StockRow row) {
        return row.hasConfirmedStock()?getString(R.string.stock_detail,row.produced,row.sold,row.remaining()):getString(R.string.unconfirmed_stock_detail,row.produced,row.sold);
    }
    private void reportCard(StockRow row,boolean archived) {
        LinearLayout c=card(content,false);
        breadTitle(c,row.name,19);
        c.addView(text(stockDetail(row),14,R.color.muted,false));
        if(archived)c.addView(text(getString(R.string.archived_stock_note),12,R.color.muted,false));
        if(row.estimated)c.addView(text(getString(R.string.estimated),12,R.color.accent,false));
        if(row.inconsistent)c.addView(text(getString(R.string.inconsistent_records),12,R.color.accent,false));
        if(tab==2) {
            c.setClickable(true);
            c.setContentDescription(getString(R.string.open_bread_detail,row.name));
            c.setOnClickListener(v->openReportDetail(row));
            c.addView(text(getString(R.string.tap_for_daily_detail),12,R.color.primary,true));
        }
    }
    private void openReportDetail(StockRow bread) {
        String from=start(),to=end();
        worker.execute(()->{
            try {
                List<DailyReportRecord> days=db.dailyReportForBread(bread.id,from,to);
                runOnUiThread(()->{
                    if(isDestroyed() || tab!=2 || !from.equals(start()) || !to.equals(end()))return;
                    LinearLayout body=new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
                    body.setPadding(dp(18),dp(4),dp(18),0);
                    body.addView(text(getString(R.string.period,Dates.display(from,"dd MMM yyyy"),
                            Dates.display(to,"dd MMM yyyy")),14,R.color.muted,false));
                    ListView list=new ListView(this);
                    list.setDividerHeight(0);
                    list.setAdapter(detailAdapter(days));
                    int height=Math.min(dp(420),Math.min((int)(getResources().getDisplayMetrics().heightPixels*.55),
                            dp(Math.max(120,days.size()*120))));
                    body.addView(list,new LinearLayout.LayoutParams(-1,height));
                    AlertDialog detail=new MaterialAlertDialogBuilder(this)
                            .setTitle(getString(R.string.bread_detail_title,bread.name))
                            .setView(body)
                            .setPositiveButton(R.string.close,null).create();
                    list.setOnItemClickListener((parent,view,position,id)->
                            showDayActions(bread,days.get(position),detail));
                    detail.show();
                });
            } catch(Exception e) { Log.e(TAG,"Loading daily report failed",e); runOnUiThread(()->toast(R.string.db_error)); }
        });
    }
    private BaseAdapter detailAdapter(List<DailyReportRecord> days) {
        return new BaseAdapter() {
            @Override public int getCount() { return days.size(); }
            @Override public Object getItem(int position) { return days.get(position); }
            @Override public long getItemId(int position) { return position; }
            @Override public View getView(int position,View recycled,android.view.ViewGroup parent) {
                LinearLayout item;
                TextView date,details;
                if(recycled instanceof LinearLayout) {
                    item=(LinearLayout)recycled;
                    date=(TextView)item.getChildAt(0); details=(TextView)item.getChildAt(1);
                } else {
                    item=new LinearLayout(MainActivity.this); item.setOrientation(LinearLayout.VERTICAL);
                    item.setPadding(dp(20),dp(10),dp(20),dp(10));
                    date=text("",16,R.color.ink,true); details=text("",14,R.color.muted,false);
                    item.addView(date); item.addView(details);
                }
                DailyReportRecord day=days.get(position);
                date.setText(Dates.display(day.date,"EEE, dd MMM yyyy"));
                String detail;
                if(!day.hasProduction) detail=day.dayStatus==DBHelper.DAY_NO_PRODUCTION?
                        getString(R.string.no_production_note):day.sold==0?getString(R.string.no_day_record):
                        getString(R.string.detail_missing_production,day.sold);
                else {
                    detail=day.planned==null?"":getString(R.string.detail_planned,day.planned)+"\n";
                    detail+=getString(R.string.detail_values,day.produced,day.sold,
                            day.hasConfirmedStock()?String.format(Dates.TURKISH,"%,d",day.remaining()):getString(R.string.unknown_quantity));
                    if(day.estimated || day.inconsistent)detail+="\n"+getString(R.string.detail_unconfirmed);
                }
                if(day.earlySellout)detail+="\n"+getString(R.string.early_sellout_marked);
                if(day.hasProduction || day.dayStatus!=DBHelper.DAY_NO_PRODUCTION)
                    detail+="\n"+getString(day.dayStatus==DBHelper.DAY_COMPLETE?R.string.day_status_complete:
                            day.dayStatus==DBHelper.DAY_NO_PRODUCTION?R.string.no_production_note:R.string.day_status_open);
                detail+="\n"+getString(R.string.day_actions_hint);
                details.setText(detail);
                return item;
            }
        };
    }
    private void showDayActions(StockRow bread,DailyReportRecord day,AlertDialog detail) {
        List<String> labels=new ArrayList<>(); List<Runnable> actions=new ArrayList<>();
        if(day.dayStatus==DBHelper.DAY_OPEN) {
            int next=day.anyProduction?DBHelper.DAY_COMPLETE:DBHelper.DAY_NO_PRODUCTION;
            labels.add(getString(next==DBHelper.DAY_COMPLETE?R.string.complete_day:noProductionLabel(day.date)));
            actions.add(()->confirmDayStatus(day.date,next,detail));
        } else {
            labels.add(getString(R.string.reopen_day));
            actions.add(()->confirmDayStatus(day.date,DBHelper.DAY_OPEN,detail));
        }
        if(day.hasConfirmedStock() && day.sold>0) {
            labels.add(getString(day.earlySellout?R.string.clear_early_sellout:R.string.mark_early_sellout));
            actions.add(()->confirmEarlySellout(bread.id,bread.name,day.date,day.earlySellout,detail));
        }
        new MaterialAlertDialogBuilder(this).setTitle(Dates.display(day.date,"dd MMM yyyy"))
                .setItems(labels.toArray(new String[0]),(menu,index)->actions.get(index).run())
                .setNegativeButton(R.string.cancel,null).show();
    }
    private void empty() { LinearLayout c=card(content,false); illustration(c); c.addView(text(getString(R.string.no_bread_title),21,R.color.ink,true)); c.addView(text(getString(R.string.no_bread_body),15,R.color.muted,false)); c.addView(button(R.string.add_bread,()->editBread(null))); }
    private void renderBreads() {
        if(breads.isEmpty()) { empty(); return; }
        content.addView(button(R.string.add_bread,()->editBread(null))); space(content,12);
        for(BreadType b:breads)breadCard(b,true);
    }
    private void breadCard(BreadType b,boolean edit) {
        LinearLayout c=card(content,false); breadTitle(c,b.getName(),21); StockRow row=null; for(StockRow r:rows)if(r.id==b.getId())row=r;
        if(edit) {
            c.addView(text(getString(R.string.default_hint,b.getDailyTotalQuantity()),14,R.color.muted,false));
            int[] plan=weeklyPlans.get(b.getId());
            if(plan!=null) for(int day=1;day<=7;day++)if(plan[day]!=b.getDailyTotalQuantity())
                c.addView(text(getString(R.string.weekday_plan_line,DAY_NAMES[day],plan[day]),13,R.color.primary,false));
        } else {
            int[] plan=weeklyPlans.get(b.getId());
            int current=plan==null?b.getDailyTotalQuantity():plan[ProductionAdvisor.dayOfWeek(Dates.today())];
            int snapshot=recordedPlans.getOrDefault(b.getId(),-1);
            c.addView(text(getString(R.string.today_plan,snapshot>=0 && row!=null?snapshot:current),14,R.color.primary,true));
            c.addView(text(row==null?getString(R.string.no_stock):stockDetail(row),14,R.color.muted,false));
            if(row!=null && !row.hasConfirmedStock())c.addView(text(getString(R.string.unconfirmed_totals),12,R.color.accent,false));
            if(row!=null && row.hasConfirmedStock() && row.sold>0) {
                boolean marked=earlySellouts.getOrDefault(b.getId(),false);
                if(marked)c.addView(text(getString(R.string.early_sellout_marked),14,R.color.accent,true));
                MaterialButton sellout=button(marked?R.string.clear_early_sellout:R.string.mark_early_sellout,
                        ()->confirmEarlySellout(b.getId(),b.getName(),Dates.today(),marked,null));
                sellout.setContentDescription(getString(R.string.early_sellout_for_bread,b.getName()));
                secondary(sellout);
                c.addView(sellout);
            }
        }
        if(edit) { MaterialButton weekPlan=button(R.string.edit_week_plan,()->showWeeklyPlan(b));
            weekPlan.setContentDescription(getString(R.string.week_plan_for,b.getName())); c.addView(weekPlan);
            MaterialButton editButton=button(R.string.edit_bread,()->editBread(b));
            editButton.setContentDescription(getString(R.string.edit_bread_for,b.getName()));
            c.addView(editButton); }
        else { final StockRow current=row; LinearLayout actions=new LinearLayout(this);
            MaterialButton production=button(R.string.production_action,()->quantityDialog(b,current,false));
            production.setContentDescription(getString(R.string.production_for_bread,b.getName()));
            production.setEnabled(todayStatus==DBHelper.DAY_OPEN);
            actions.addView(production,new LinearLayout.LayoutParams(0,-2,1));
            MaterialButton sale=button(R.string.sale_action,()->quantityDialog(b,current,true));
            sale.setContentDescription(getString(R.string.sale_for_bread,b.getName()));
            sale.setEnabled(todayStatus==DBHelper.DAY_OPEN && row!=null && row.hasConfirmedStock() && row.remaining()>0);
            actions.addView(sale,new LinearLayout.LayoutParams(0,-2,1)); c.addView(actions);
            if(todayStatus==DBHelper.DAY_OPEN && row!=null && row.sold>0) {
                MaterialButton correct=button(R.string.correct_sales,()->showSales(b));
                secondary(correct);
                correct.setContentDescription(getString(R.string.correct_sales_for,b.getName()));
                c.addView(correct);
            } }
    }
    private void showSales(BreadType bread) {
        if(!Dates.today().equals(loadedDate)) { toast(R.string.day_changed); refresh(); return; }
        final String date=loadedDate;
        worker.execute(()->{
            try {
                List<SaleEntry> entries=db.sales(bread.getId(),date);
                runOnUiThread(()->{
                    if(isDestroyed() || tab!=0 || !date.equals(loadedDate))return;
                    if(entries.isEmpty()) { toast(R.string.no_sales_today); refresh(); return; }
                    String[] labels=new String[entries.size()];
                    for(int i=0;i<entries.size();i++)labels[i]=saleLabel(entries.get(i),entries.size()-i);
                    new MaterialAlertDialogBuilder(this).setTitle(getString(R.string.sales_list_title,bread.getName()))
                            .setItems(labels,(menu,index)->confirmCancelSale(bread,entries.get(index),labels[index]))
                            .setNegativeButton(R.string.close,null).show();
                });
            } catch(Exception e) { Log.e(TAG,"Loading sales failed",e); runOnUiThread(()->toast(R.string.db_error)); }
        });
    }
    private String saleLabel(SaleEntry sale,int order) {
        String time=sale.time();
        return time==null?getString(R.string.sale_entry_untimed,order,sale.quantity):getString(R.string.sale_entry,time,sale.quantity);
    }
    private void confirmCancelSale(BreadType bread,SaleEntry sale,String label) {
        AlertDialog dialog=new MaterialAlertDialogBuilder(this).setTitle(getString(R.string.cancel_sale_title,bread.getName()))
                .setMessage(getString(R.string.cancel_sale_help,label))
                .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.cancel_sale,null).create();
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(!Dates.today().equals(loadedDate)) { dialog.dismiss(); toast(R.string.day_changed); refresh(); return; }
            save(dialog,()->db.cancelSale(sale.id),()->toast(R.string.sale_cancelled),R.string.cancel_sale_failed);
        }));
        dialog.show();
    }
    private void showWeeklyPlan(BreadType bread) {
        int[] plan=weeklyPlans.get(bread.getId());
        if(plan==null)return;
        int[] order={Calendar.MONDAY,Calendar.TUESDAY,Calendar.WEDNESDAY,Calendar.THURSDAY,Calendar.FRIDAY,Calendar.SATURDAY,Calendar.SUNDAY};
        String[] days=new String[7];
        for(int i=0;i<7;i++)days[i]=getString(R.string.weekday_plan_line,DAY_NAMES[order[i]],plan[order[i]]);
        new MaterialAlertDialogBuilder(this).setTitle(getString(R.string.week_plan_title,bread.getName()))
                .setItems(days,(dialog,index)->editDayPlan(bread,order[index],null))
                .setNegativeButton(R.string.cancel,null).show();
    }
    private void editDayPlan(BreadType bread,int weekday,ProductionAdvisor.Advice suggestion) {
        int[] plan=weeklyPlans.get(bread.getId());
        if(plan==null)return;
        int current=plan[weekday];
        int draft=suggestion==null?current:ProductionAdvisor.draftQuantity(suggestion,current);
        LinearLayout form=form();
        form.addView(text(getString(suggestion==null?R.string.week_plan_help:R.string.plan_draft_help,draft),14,R.color.muted,false));
        TextInputEditText amount=field(form,R.string.planned_quantity,true,String.valueOf(draft));
        if(suggestion!=null) {
            amount.setTextColor(color(R.color.muted));
            amount.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
                @Override public void onTextChanged(CharSequence s,int start,int before,int count) { amount.setTextColor(color(R.color.ink)); }
                @Override public void afterTextChanged(Editable s) {}
            });
        }
        AlertDialog dialog=new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.day_plan_title,bread.getName(),DAY_NAMES[weekday]))
                .setView(form).setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.save,null).create();
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            Integer quantity=number(amount,false);
            if(quantity!=null)save(dialog,()->db.setDayPlan(bread.getId(),weekday,quantity));
        }));
        dialog.show();
    }
    private LinearLayout form() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(dp(24),dp(12),dp(24),dp(8)); return l; }
    private TextInputEditText field(LinearLayout parent,int hint,boolean number,String value) {
        TextInputLayout box=new TextInputLayout(this); box.setHint(getString(hint)); box.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        TextInputEditText input=new TextInputEditText(box.getContext()); input.setSingleLine(); input.setInputType(number?InputType.TYPE_CLASS_NUMBER:InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES); input.setContentDescription(getString(hint)); input.setText(value);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(number?10:DBHelper.MAX_NAME_LENGTH)});
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count) { box.setError(null); }
            @Override public void afterTextChanged(Editable s) {}
        });
        box.addView(input); parent.addView(box); space(parent,12); return input;
    }
    private void fieldError(TextInputEditText input,String message) {
        for(android.view.ViewParent p=input.getParent();p!=null;p=p.getParent())
            if(p instanceof TextInputLayout) { ((TextInputLayout)p).setError(message); input.requestFocus(); return; }
        input.setError(message);
    }
    private Integer number(TextInputEditText input,boolean positive) {
        try { int n=Integer.parseInt(String.valueOf(input.getText()).trim()); if(n<0 || (positive && n==0))throw new NumberFormatException(); return n; }
        catch(NumberFormatException ex) {fieldError(input,getString(positive?R.string.positive_number:R.string.invalid_number));return null;}
    }
    private interface Write { boolean run(); }
    private void setDialogBusy(AlertDialog dialog, boolean busy) {
        for (int which : new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL}) {
            Button button = dialog.getButton(which);
            if (button != null) button.setEnabled(!busy);
        }
        dialog.setCancelable(!busy);
    }
    private void save(AlertDialog dialog,Write write) {
        save(dialog,write,null);
    }
    private void save(AlertDialog dialog,Write write,Runnable onSaved) {
        save(dialog,write,onSaved,R.string.save_failed);
    }
    private void save(AlertDialog dialog,Write write,Runnable onSaved,int failure) {
        setDialogBusy(dialog,true);
        worker.execute(()->{ boolean success; try {success=write.run();} catch(Exception e){Log.e(TAG,"Saving record failed",e); success=false;} final boolean ok=success;
            runOnUiThread(()->{if(isDestroyed())return; setDialogBusy(dialog,false); if(ok){dialog.dismiss();if(onSaved!=null)onSaved.run();refresh();}
                else if(dialog.isShowing()) Toast.makeText(this,failure,Toast.LENGTH_LONG).show();
                else toast(failure);}); });
    }
    private void confirmEarlySellout(int breadId,String breadName,String date,boolean marked,AlertDialog parent) {
        AlertDialog dialog=new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.early_sellout_title,breadName,
                        getString(marked?R.string.clear_early_sellout:R.string.mark_early_sellout)))
                .setMessage(marked?R.string.clear_early_sellout_help:R.string.mark_early_sellout_help)
                .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.save,null).create();
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->
                save(dialog,()->db.setEarlySellout(breadId,date,!marked),()->{if(parent!=null)parent.dismiss();})));
        dialog.show();
    }
    private void editBread(BreadType bread) {
        LinearLayout form=form(); TextInputEditText name=field(form,R.string.bread_name,false,bread==null?"":bread.getName()); TextInputEditText quantity=field(form,R.string.default_quantity,true,bread==null?"":String.valueOf(bread.getDailyTotalQuantity()));
        MaterialAlertDialogBuilder builder=new MaterialAlertDialogBuilder(this).setTitle(bread==null?R.string.add_bread:R.string.edit_bread).setView(form).setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.save,null);
        if(bread!=null)builder.setNeutralButton(R.string.archive,(d,w)->archive(bread));
        AlertDialog dialog=builder.create(); dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String value=String.valueOf(name.getText()).trim(); if(value.isEmpty() || value.length()>DBHelper.MAX_NAME_LENGTH){fieldError(name,getString(R.string.invalid_name));return;}
            for(BreadType other:breads) if((bread==null || other.getId()!=bread.getId()) && DBHelper.nameKey(other.getName()).equals(DBHelper.nameKey(value))) {
                fieldError(name,getString(R.string.duplicate_name,other.getName())); return; }
            Integer n=number(quantity,false); if(n==null)return;
            save(dialog,()->bread==null?db.insertBreadType(value,n):db.updateBreadType(bread.getId(),value,n));
        })); dialog.show();
    }
    private void archive(BreadType b) {
        AlertDialog d=new MaterialAlertDialogBuilder(this).setTitle(R.string.archive).setMessage(R.string.archive_help).setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.archive,null).create();
        d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->save(d,()->db.deleteBreadType(b.getId()))));d.show();
    }
    private void quantityDialog(BreadType b,StockRow row,boolean sale) {
        if (!Dates.today().equals(loadedDate)) {
            toast(R.string.day_changed);
            refresh();
            return;
        }
        final String date=Dates.today(); LinearLayout form=form(); form.addView(text(getString(sale?R.string.sale_help:R.string.production_help),14,R.color.muted,false));
        int[] plan=weeklyPlans.get(b.getId());
        int planned=plan==null?b.getDailyTotalQuantity():plan[ProductionAdvisor.dayOfWeek(date)];
        TextInputEditText quantity=field(form,R.string.quantity,true,sale?"":String.valueOf(row==null?planned:row.produced));
        AlertDialog dialog=new MaterialAlertDialogBuilder(this).setTitle(b.getName()+" · "+getString(sale?R.string.sale_action:R.string.production_action)).setView(form).setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.save,null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if (!date.equals(Dates.today())) { dialog.dismiss(); toast(R.string.day_changed); refresh(); return; }
            Integer n=number(quantity,sale); if(n==null)return;
            if(sale && row!=null && row.hasConfirmedStock() && n>row.remaining()) { fieldError(quantity,getString(R.string.sale_over_stock,row.remaining())); return; }
            if(!sale && row!=null && n<row.sold) { fieldError(quantity,getString(R.string.production_below_sales,row.sold)); return; }
            save(dialog,()->sale?db.recordSale(b.getId(),date,n):db.setProduction(b.getId(),date,n));
        }));dialog.show();
    }
    private void pickDate() { pickDate(false); }
    private void pickDate(boolean last) {
        String[] parts=(last?rangeEnd:selectedDate).split("-");
        DatePickerDialog picker=new DatePickerDialog(this,(v,y,m,d)->{
            String value=String.format(Locale.ROOT,"%04d-%02d-%02d",y,m+1,d);
            if(value.compareTo(firstRecordDate)<0) { toast(R.string.before_first_record); return; }
            if(last && value.compareTo(selectedDate)<0) { toast(R.string.invalid_range); return; }
            if(last)rangeEnd=value;
            else {selectedDate=value; if(rangeEnd.compareTo(value)<0)rangeEnd=value;}
            refresh();
        },Integer.parseInt(parts[0]),Integer.parseInt(parts[1])-1,Integer.parseInt(parts[2]));
        String minimum=last && selectedDate.compareTo(firstRecordDate)>0?selectedDate:firstRecordDate;
        picker.getDatePicker().setMinDate(pickerMillis(minimum));
        picker.getDatePicker().setMaxDate(pickerMillis(Dates.today())); picker.show();
    }
    private static long pickerMillis(String date) {
        String[] part=date.split("-");
        return new GregorianCalendar(Integer.parseInt(part[0]),Integer.parseInt(part[1])-1,Integer.parseInt(part[2])).getTimeInMillis();
    }
    private void shareWhatsApp() {
        if(rows.isEmpty()) return;
        Intent send=new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT,ReportMessage.format(start(),end(),rows));
        for(String packageName:new String[]{"com.whatsapp","com.whatsapp.w4b"}) {
            try { startActivity(new Intent(send).setPackage(packageName)); return; }
            catch(ActivityNotFoundException ignored) {  }
        }
        new MaterialAlertDialogBuilder(this).setMessage(R.string.whatsapp_missing)
                .setNegativeButton(R.string.close,null)
                .setPositiveButton(R.string.share_other,(d,w)->startActivity(Intent.createChooser(send,getString(R.string.share_title))))
                .show();
    }
}
