package com.example.bakeryapp;

final class DailyReportRecord {
    final String date;
    final Integer planned;
    final long produced, sold;
    final boolean hasProduction, estimated, inconsistent, earlySellout;
    final int dayStatus;
    final boolean anyProduction;

    DailyReportRecord(String date,Integer planned,long produced,long sold,
                      boolean hasProduction,boolean estimated,boolean inconsistent,boolean earlySellout,int dayStatus,
                      boolean anyProduction) {
        this.date=date; this.planned=planned; this.produced=produced; this.sold=sold;
        this.hasProduction=hasProduction; this.estimated=estimated;
        this.earlySellout=earlySellout;
        this.dayStatus=dayStatus;
        this.anyProduction=anyProduction;
        this.inconsistent=inconsistent || sold<0 || (hasProduction && sold>produced) || (!hasProduction && sold!=0);
    }

    boolean hasConfirmedStock() { return hasProduction && !estimated && !inconsistent; }
    long remaining() {
        if(!hasConfirmedStock()) throw new IllegalStateException("Unconfirmed stock");
        return Math.subtractExact(produced,sold);
    }
}
