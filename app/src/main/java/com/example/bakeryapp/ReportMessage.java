package com.example.bakeryapp;

import java.util.List;

final class ReportMessage {
    private ReportMessage() {}

    static String format(String start, String end, List<StockRow> rows) {
        Dates.validate(start); Dates.validate(end);
        if (start.compareTo(end)>0) throw new IllegalArgumentException("Invalid range");
        ReportTotals totals=ReportTotals.from(rows);
        StringBuilder message=new StringBuilder("Setbaşı Fırını · Satış Raporu\n");
        message.append(Dates.display(start,"dd MMMM yyyy"));
        if (!start.equals(end)) message.append(" – ").append(Dates.display(end,"dd MMMM yyyy"));
        message.append("\n\nToplam üretim: ").append(count(totals.produced))
                .append("\nToplam satış: ").append(count(totals.sold))
                .append("\nKalan: ").append(totals.confirmedStock?count(totals.remaining()):"hesaplanamadı");
        for (StockRow row:rows) {
            message.append("\n\n").append(row.name.replace('\n',' ').replace('\r',' '))
                    .append("\nÜretim: ").append(count(row.produced))
                    .append(" · Satış: ").append(count(row.sold))
                    .append(" · Kalan: ").append(row.hasConfirmedStock()?count(row.remaining()):"—");
            if (row.estimated) message.append(" (üretim tahmini)");
            if (row.inconsistent) message.append(" (kayıt kontrol edilmeli)");
        }
        ReportHighlights highlights=ReportHighlights.from(rows);
        if (!highlights.isEmpty()) {
            message.append("\n\nÖne çıkanlar");
            if (highlights.highestRate!=null) message.append("\nEn yüksek satış oranı: ")
                    .append(clean(highlights.highestRate.name)).append(" · ")
                    .append(ReportHighlights.percent(highlights.highestRate.sold,highlights.highestRate.produced));
            if (highlights.lowestRate!=null) message.append("\nEn düşük satış oranı: ")
                    .append(clean(highlights.lowestRate.name)).append(" · ")
                    .append(ReportHighlights.percent(highlights.lowestRate.sold,highlights.lowestRate.produced))
                    .append(" satış, ")
                    .append(ReportHighlights.percent(highlights.lowestRate.remaining(),highlights.lowestRate.produced))
                    .append(" kalan");
            if (highlights.mostSold!=null) message.append("\nEn çok satılan: ")
                    .append(clean(highlights.mostSold.name)).append(" · ").append(count(highlights.mostSold.sold)).append(" adet");
            if (highlights.mostRemaining!=null) message.append("\nEn çok kalan: ")
                    .append(clean(highlights.mostRemaining.name)).append(" · ").append(count(highlights.mostRemaining.remaining())).append(" adet");
        }
        message.append("\n\nYalnızca kayıtlı üretim günleri dahildir. Kalanlar ertesi güne devredilmez.");
        return message.toString();
    }

    private static String clean(String name) { return name.replace('\n',' ').replace('\r',' '); }
    private static String count(long value) { return String.format(Dates.TURKISH,"%,d",value); }
}
