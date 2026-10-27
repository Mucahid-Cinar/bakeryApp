package com.example.bakeryapp;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class ReportTotalsTest {
    @Test public void computesProducedMinusSold() {
        ReportTotals totals=ReportTotals.from(Arrays.asList(
                new StockRow(1,"A",100,60,false), new StockRow(2,"B",50,20,false)));
        assertEquals(150,totals.produced);
        assertEquals(80,totals.sold);
        assertEquals(70,totals.remaining());
        assertEquals(53,totals.salesPercent());
    }
    @Test public void usesWeightedRateRatherThanAverage() {
        ReportTotals totals=ReportTotals.from(Arrays.asList(
                new StockRow(1,"A",100,100,false), new StockRow(2,"B",900,0,false)));
        assertEquals(10,totals.salesPercent());
    }
    @Test public void zeroProductionHasNoUndefinedPercentage() {
        ReportTotals totals=ReportTotals.from(Collections.singletonList(new StockRow(1,"A",0,0,false)));
        assertEquals(0,totals.remaining());
        assertFalse(totals.hasSalesRate());
        assertFalse(ReportTotals.from(Collections.emptyList()).hasSalesRate());
    }
    @Test public void soldOutHasZeroRemainingAndHundredPercent() {
        ReportTotals totals=ReportTotals.from(Collections.singletonList(new StockRow(1,"A",100,100,false)));
        assertEquals(0,totals.remaining());
        assertEquals(100,totals.salesPercent());
    }
    @Test public void estimatedProductionDoesNotCreateConfirmedRemainingOrRate() {
        ReportTotals totals=ReportTotals.from(Arrays.asList(
                new StockRow(1,"A",100,60,false),new StockRow(2,"B",50,20,true)));
        assertEquals(80,totals.sold);
        assertFalse(totals.confirmedStock);
        assertFalse(totals.hasSalesRate());
    }
    @Test public void negativeOrOversoldRowsAreFlagged() {
        for(StockRow row:Arrays.asList(new StockRow(1,"A",0,10,true),
                new StockRow(1,"A",100,-2,false),new StockRow(1,"A",-1,0,false))) {
            assertTrue(row.inconsistent);
            assertFalse(ReportTotals.from(Collections.singletonList(row)).hasSalesRate());
        }
    }
    @Test public void dailyInconsistencyRemainsVisibleEvenWhenPeriodTotalLooksNormal() {
        StockRow row=new StockRow(1,"A",110,20,false,true);
        ReportTotals totals=ReportTotals.from(Collections.singletonList(row));
        assertTrue(totals.inconsistent);
        assertFalse(totals.confirmedStock);
    }
    @Test public void totalsBeyondIntegerLimitRemainExact() {
        ReportTotals totals=ReportTotals.from(Arrays.asList(
                new StockRow(1,"A",2_147_483_647L,1_000_000_000L,false),
                new StockRow(2,"B",2_147_483_647L,1_000_000_000L,false)));
        assertEquals(4_294_967_294L,totals.produced);
        assertEquals(2_294_967_294L,totals.remaining());
        assertEquals(47,totals.salesPercent());
    }
    @Test(expected=IllegalStateException.class) public void rejectsUnknownRemaining() {
        ReportTotals.from(Collections.singletonList(new StockRow(1,"A",0,10,true))).remaining();
    }
    @Test(expected=IllegalStateException.class) public void rowAlsoRejectsUnconfirmedRemaining() {
        new StockRow(1,"A",100,20,true).remaining();
    }
    @Test(expected=IllegalStateException.class) public void rejectsZeroDenominator() {
        ReportTotals.from(Collections.emptyList()).salesPercent();
    }
    @Test(expected=ArithmeticException.class) public void failsExplicitlyInsteadOfWrappingAnOverflow() {
        ReportTotals.from(Arrays.asList(new StockRow(1,"A",Long.MAX_VALUE,0,false),new StockRow(2,"B",1,0,false)));
    }
}
