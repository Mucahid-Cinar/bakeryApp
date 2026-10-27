package com.example.bakeryapp;

public final class StockRow {
    public final int id;
    public final String name;
    public final long produced, sold;
    public final boolean estimated;
    public final boolean inconsistent;
    public StockRow(int id, String name, long produced, long sold, boolean estimated) {
        this(id,name,produced,sold,estimated,false);
    }
    public StockRow(int id, String name, long produced, long sold, boolean estimated, boolean inconsistent) {
        this.id=id; this.name=name; this.produced=produced; this.sold=sold; this.estimated=estimated;
        this.inconsistent=inconsistent || produced<0 || sold<0 || sold>produced;
    }
    public boolean hasConfirmedStock() { return !estimated && !inconsistent; }
    public long remaining() {
        if (!hasConfirmedStock()) throw new IllegalStateException("Stock is not confirmed");
        return Math.subtractExact(produced,sold);
    }
}
