package com.example.bakeryapp;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class ReportMessageTest {
    @Test public void dailyMonthlyAndRangeMessagesUseTheSelectedPeriod() {
        StockRow rye=new StockRow(1,"Çavdar",40,10,false);
        String daily=ReportMessage.format("2026-10-27","2026-10-27",Collections.singletonList(rye));
        assertTrue(daily.contains("27 Ekim 2026"));
        assertTrue(daily.contains("Toplam üretim: 40"));
        assertTrue(daily.contains("Toplam satış: 10"));
        assertTrue(daily.contains("Kalan: 30"));
        String monthly=ReportMessage.format("2026-10-01","2026-10-27",Collections.singletonList(rye));
        assertTrue(monthly.contains("01 Ekim 2026 – 27 Ekim 2026"));
        String range=ReportMessage.format("2026-09-15","2026-10-03",Collections.singletonList(rye));
        assertTrue(range.contains("15 Eylül 2026 – 03 Ekim 2026"));
    }

    @Test public void largeCountsUseTurkishThousandsSeparator() {
        String message=ReportMessage.format("2026-10-01","2026-10-27",Collections.singletonList(
                new StockRow(1,"Somun",11066,8723,false)));
        assertTrue(message.contains("Toplam üretim: 11.066"));
        assertTrue(message.contains("Kalan: 2.343"));
    }
    @Test public void unverifiedStockDoesNotShareAFalseRemainder() {
        String message=ReportMessage.format("2026-10-27","2026-10-27",Arrays.asList(
                new StockRow(1,"Çavdar",40,10,false),new StockRow(2,"Eski",20,5,true)));
        assertTrue(message.contains("Kalan: hesaplanamadı"));
        assertTrue(message.contains("üretim tahmini"));
    }
    @Test public void sharedReportIncludesRateAndQuantityLeaders() {
        String message=ReportMessage.format("2026-10-19","2026-10-25",Arrays.asList(
                new StockRow(1,"Somun",40,30,false),new StockRow(2,"Çavdar",100,20,false),
                new StockRow(3,"Tam Buğday",120,80,false),new StockRow(4,"Baget",150,50,false)));
        assertTrue(message.contains("En yüksek satış oranı: Somun · %75,0"));
        assertTrue(message.contains("En düşük satış oranı: Çavdar · %20,0"));
        assertTrue(message.contains("En çok satılan: Tam Buğday · 80 adet"));
        assertTrue(message.contains("En çok kalan: Baget · 100 adet"));
    }
}
