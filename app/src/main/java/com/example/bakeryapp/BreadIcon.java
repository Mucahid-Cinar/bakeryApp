package com.example.bakeryapp;

final class BreadIcon {
    private static final String[] BAGUETTE={"baget","francala","fransız"};
    private static final String[] RING={"simit","açma","halka"};
    private static final String[] FLAT={"pide","lavaş","bazlama","yufka","gömme"};
    private BreadIcon() {}

    static int of(String name) {
        String key=name==null?"":name.toLowerCase(Dates.TURKISH);
        if(containsAny(key,BAGUETTE)) return R.drawable.ic_bread_baguette;
        if(containsAny(key,RING)) return R.drawable.ic_bread_ring;
        if(containsAny(key,FLAT)) return R.drawable.ic_bread_flat;
        return R.drawable.ic_bread_loaf;
    }

    private static boolean containsAny(String key,String[] words) {
        for(String word:words) if(key.contains(word)) return true;
        return false;
    }
}
