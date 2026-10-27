package com.example.bakeryapp;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class ReportHighlightsTest {
    @Test public void rateAndQuantityRankingsUseDifferentMeasures() {
        ReportHighlights result=ReportHighlights.from(Arrays.asList(
                new StockRow(1,"Somun",40,30,false),
                new StockRow(2,"Çavdar",100,20,false),
                new StockRow(3,"Tam Buğday",120,80,false),
                new StockRow(4,"Baget",150,50,false)));
        assertEquals("Somun",result.highestRate.name);
        assertEquals("Çavdar",result.lowestRate.name);
        assertEquals("Tam Buğday",result.mostSold.name);
        assertEquals("Baget",result.mostRemaining.name);
        assertEquals("%75,0",ReportHighlights.percent(result.highestRate.sold,result.highestRate.produced));
    }

    @Test public void unconfirmedZeroProductionAndTiesDoNotBecomeFalseLeaders() {
        ReportHighlights result=ReportHighlights.from(Arrays.asList(
                new StockRow(1,"A",40,20,false),new StockRow(2,"B",80,40,false),
                new StockRow(3,"Tahmini",1000,900,true),new StockRow(4,"Boş",0,0,false)));
        assertNull(result.highestRate);
        assertNull(result.lowestRate);
        assertEquals("B",result.mostSold.name);
        assertEquals("B",result.mostRemaining.name);
        assertTrue(ReportHighlights.from(Collections.singletonList(new StockRow(1,"A",40,20,false))).isEmpty());
    }
}
