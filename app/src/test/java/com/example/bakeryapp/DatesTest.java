package com.example.bakeryapp;

import org.junit.Test;
import java.util.Locale;
import java.util.TimeZone;
import static org.junit.Assert.*;

public class DatesTest {
    @Test public void weekStartsMondayAcrossYearBoundary() {
        assertEquals("2025-12-29", Dates.weekStart("2026-01-04"));
        assertEquals("2026-01-05", Dates.weekStart("2026-01-05"));
    }
    @Test public void leapDayAndMonthBoundaries() {
        assertEquals("2024-02-29", Dates.plusDays("2024-02-28",1));
        assertEquals("2024-03-01", Dates.plusDays("2024-02-29",1));
    }
    @Test(expected=IllegalArgumentException.class) public void invalidDayRejected() {
        Dates.validate("2026-02-29");
    }
    @Test(expected=IllegalArgumentException.class) public void trailingInputRejected() {
        Dates.validate("2026-01-01oops");
    }
    @Test public void isoDatesIndependentOfLocaleAndDaylightSaving() {
        Locale previous=Locale.getDefault(); TimeZone zone=TimeZone.getDefault();
        try {
            Locale.setDefault(new Locale("ar")); TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"));
            assertEquals("2026-03-30", Dates.plusDays("2026-03-29",1));
            assertEquals("2026-10-26", Dates.plusDays("2026-10-25",1));
        } finally { Locale.setDefault(previous); TimeZone.setDefault(zone); }
    }
    @Test public void aggregateCountsDoNotOverflowInteger() {
        StockRow row=new StockRow(1,"Ekmek",4_000_000_000L,3_000_000_000L,false);
        assertEquals(1_000_000_000L,row.remaining());
    }
    @Test public void everyDayInWeekResolvesToSameMonday() {
        for(int i=0;i<7;i++) assertEquals("2026-10-19",Dates.weekStart(Dates.plusDays("2026-10-19",i)));
        assertEquals("2026-10-26",Dates.weekStart("2026-10-26"));
    }
    @Test public void monthAndYearTransitionsKeepChronologicalOrdering() {
        assertEquals("2026-01-01",Dates.plusDays("2025-12-31",1));
        assertEquals("2025-12-31",Dates.plusDays("2026-01-01",-1));
        assertEquals("2026-11-01",Dates.plusDays("2026-10-31",1));
    }
    @Test public void displayUsesTurkishMonthAndWeekdayOnEnglishDevice() {
        Locale previous=Locale.getDefault();
        try {
            Locale.setDefault(Locale.ENGLISH);
            assertEquals("27 Ekim 2026 · Salı",Dates.display("2026-10-27","dd MMMM yyyy · EEEE"));
        } finally { Locale.setDefault(previous); }
    }
    @Test public void weekdayIgnoresDeviceTimeZone() {
        TimeZone zone=TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
            assertEquals(java.util.Calendar.TUESDAY,Dates.dayOfWeek("2026-10-27"));
            assertEquals(java.util.Calendar.SUNDAY,Dates.dayOfWeek("2026-03-29"));
            assertEquals("2026-10-26",Dates.weekStart("2026-11-01"));
        } finally { TimeZone.setDefault(zone); }
    }
    @Test public void turkeyIsUtcPlusThreeAllYear() {
        assertEquals(3*60*60*1000,Dates.TURKEY.getRawOffset());
        assertFalse(Dates.TURKEY.useDaylightTime());
        assertEquals(3*60*60*1000,Dates.TURKEY.getOffset(java.util.Date.UTC(126,0,15,12,0,0)));
    }
    @Test public void recordTimeUsesIsoLayout() {
        assertTrue(Dates.now().matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
        assertEquals("14:32",new SaleEntry(1,5,"2026-10-27 14:32:05").time());
        assertNull(new SaleEntry(1,5,null).time());
    }
    @Test public void monthlyRangeHandlesLeapYear() {
        assertEquals("2024-02-01",Dates.monthStart("2024-02-18"));
        assertEquals("2024-02-29",Dates.monthEnd("2024-02-18"));
        assertEquals("2026-04-30",Dates.monthEnd("2026-04-01"));
    }
}
