package com.example.bakeryapp;

import java.util.List;

public final class ReportTotals {
    public final long produced;
    public final long sold;
    public final boolean confirmedStock;
    public final boolean inconsistent;

    private ReportTotals(long produced,long sold,boolean confirmedStock,boolean inconsistent) {
        this.produced=produced;
        this.sold=sold;
        this.confirmedStock=confirmedStock;
        this.inconsistent=inconsistent;
    }

    public static ReportTotals from(List<StockRow> rows) {
        long produced=0, sold=0;
        boolean confirmed=true, inconsistent=false;
        for (StockRow row:rows) {
            produced=Math.addExact(produced,row.produced);
            sold=Math.addExact(sold,row.sold);
            confirmed &= row.hasConfirmedStock();
            inconsistent |= row.inconsistent;
        }
        return new ReportTotals(produced,sold,confirmed,inconsistent);
    }

    public long remaining() {
        if (!confirmedStock) throw new IllegalStateException("Stock is not confirmed");
        return Math.subtractExact(produced,sold);
    }

    public boolean hasSalesRate() { return confirmedStock && produced>0; }

    public int salesPercent() {
        if (!hasSalesRate()) throw new IllegalStateException("Sales rate is undefined");
        return (int)Math.round(100.0*sold/produced);
    }
}
