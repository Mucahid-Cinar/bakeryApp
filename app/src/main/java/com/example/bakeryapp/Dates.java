package com.example.bakeryapp;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class Dates {
    public static final Locale TURKISH=new Locale("tr","TR");
    public static final TimeZone TURKEY=TimeZone.getTimeZone("GMT+03:00");
    private Dates() {}
    private static SimpleDateFormat format(String pattern,Locale locale) {
        SimpleDateFormat format=new SimpleDateFormat(pattern,locale);
        format.setTimeZone(TURKEY);
        format.setLenient(false);
        return format;
    }
    private static SimpleDateFormat iso() { return format("yyyy-MM-dd",Locale.ROOT); }
    private static Date parse(String value) {
        if(value==null || !value.matches("\\d{4}-\\d{2}-\\d{2}")) throw new IllegalArgumentException("Invalid date");
        try { return iso().parse(value); } catch(ParseException e) { throw new IllegalArgumentException("Invalid date",e); }
    }
    public static Calendar calendar() { return Calendar.getInstance(TURKEY,TURKISH); }
    public static void validate(String value) { parse(value); }
    public static String today() { return iso().format(new Date()); }
    public static String now() { return format("yyyy-MM-dd HH:mm:ss",Locale.ROOT).format(new Date()); }
    public static String plusDays(String value,int days) {
        Calendar c=calendar(); c.setTime(parse(value)); c.add(Calendar.DATE,days);
        return iso().format(c.getTime());
    }
    public static String weekStart(String value) {
        Calendar c=calendar(); c.setTime(parse(value));
        return plusDays(value,-((c.get(Calendar.DAY_OF_WEEK)+5)%7));
    }
    public static int dayOfWeek(String value) {
        Calendar c=calendar(); c.setTime(parse(value));
        return c.get(Calendar.DAY_OF_WEEK);
    }
    public static String monthStart(String value) {
        validate(value);
        return value.substring(0,7)+"-01";
    }
    public static String monthEnd(String value) {
        Calendar c=calendar(); c.setTime(parse(monthStart(value)));
        c.add(Calendar.MONTH,1); c.add(Calendar.DATE,-1);
        return iso().format(c.getTime());
    }
    public static String display(String value,String pattern) {
        return format(pattern,TURKISH).format(parse(value));
    }
}
