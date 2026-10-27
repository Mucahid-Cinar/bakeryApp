package com.example.bakeryapp;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

final class ReportHighlights {
    final StockRow highestRate, lowestRate, mostSold, mostRemaining;

    private ReportHighlights(StockRow highestRate, StockRow lowestRate,
                             StockRow mostSold, StockRow mostRemaining) {
        this.highestRate=highestRate; this.lowestRate=lowestRate;
        this.mostSold=mostSold; this.mostRemaining=mostRemaining;
    }

    static ReportHighlights from(List<StockRow> rows) {
        List<StockRow> valid=new ArrayList<>();
        for (StockRow row:rows) if (row.hasConfirmedStock() && row.produced>0) valid.add(row);
        if (valid.size()<2) return new ReportHighlights(null,null,null,null);
        return new ReportHighlights(rateLeader(valid,true),rateLeader(valid,false),
                countLeader(valid,true),countLeader(valid,false));
    }

    private static StockRow rateLeader(List<StockRow> rows, boolean highest) {
        StockRow best=null;
        boolean tied=false;
        for (StockRow row:rows) {
            if (best==null) { best=row; continue; }
            int comparison=BigInteger.valueOf(row.sold).multiply(BigInteger.valueOf(best.produced))
                    .compareTo(BigInteger.valueOf(best.sold).multiply(BigInteger.valueOf(row.produced)));
            if (!highest) comparison=-comparison;
            if (comparison>0) { best=row; tied=false; }
            else if (comparison==0) tied=true;
        }
        return tied?null:best;
    }

    private static StockRow countLeader(List<StockRow> rows, boolean sold) {
        StockRow best=null;
        boolean tied=false;
        for (StockRow row:rows) {
            if (best==null) { best=row; continue; }
            long value=sold?row.sold:row.remaining();
            long current=sold?best.sold:best.remaining();
            if (value>current) { best=row; tied=false; }
            else if (value==current) tied=true;
        }
        return tied?null:best;
    }

    boolean isEmpty() {
        return highestRate==null && lowestRate==null && mostSold==null && mostRemaining==null;
    }

    static String percent(long part, long whole) {
        return String.format(Dates.TURKISH,"%%%.1f",100.0*part/whole);
    }
}
