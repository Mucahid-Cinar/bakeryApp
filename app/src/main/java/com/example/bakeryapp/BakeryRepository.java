package com.example.bakeryapp;

import android.content.Context;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class BakeryRepository {
    private static BakeryRepository instance;
    private final DBHelper database;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private BakeryRepository(Context context) {
        database = new DBHelper(context.getApplicationContext());
    }

    public static synchronized BakeryRepository get(Context context) {
        if (instance == null) instance = new BakeryRepository(context);
        return instance;
    }

    public DBHelper database() { return database; }
    public ExecutorService executor() { return executor; }
}
