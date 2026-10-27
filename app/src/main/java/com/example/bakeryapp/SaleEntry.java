package com.example.bakeryapp;

final class SaleEntry {
    final long id;
    final int quantity;
    final String recordedAt;

    SaleEntry(long id,int quantity,String recordedAt) {
        this.id=id; this.quantity=quantity; this.recordedAt=recordedAt;
    }

    String time() { return recordedAt==null || recordedAt.length()<16?null:recordedAt.substring(11,16); }
}
