package com.example.bakeryapp;

import org.junit.Test;
import static org.junit.Assert.*;

public class BreadIconTest {
    @Test public void turkishNamesPickMatchingDrawing() {
        assertEquals(R.drawable.ic_bread_baguette,BreadIcon.of("Baget"));
        assertEquals(R.drawable.ic_bread_baguette,BreadIcon.of("FRANCALA"));
        assertEquals(R.drawable.ic_bread_ring,BreadIcon.of("SİMİT"));
        assertEquals(R.drawable.ic_bread_ring,BreadIcon.of("Peynirli açma"));
        assertEquals(R.drawable.ic_bread_flat,BreadIcon.of("Ramazan PİDESİ"));
        assertEquals(R.drawable.ic_bread_flat,BreadIcon.of("Köy bazlaması"));
    }
    @Test public void unknownOrMissingNamesGetALoaf() {
        assertEquals(R.drawable.ic_bread_loaf,BreadIcon.of("Ekşi maya"));
        assertEquals(R.drawable.ic_bread_loaf,BreadIcon.of("#12"));
        assertEquals(R.drawable.ic_bread_loaf,BreadIcon.of(null));
    }
}
