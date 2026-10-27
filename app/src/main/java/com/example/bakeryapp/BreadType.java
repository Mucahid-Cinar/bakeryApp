package com.example.bakeryapp;

public class BreadType {
    private int id;
    private String name;
    private int dailyTotalQuantity;

    public BreadType(int id, String name, int dailyTotalQuantity) {
        this.id = id;
        this.name = name;
        this.dailyTotalQuantity = dailyTotalQuantity;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getDailyTotalQuantity() {
        return dailyTotalQuantity;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDailyTotalQuantity(int quantity) {
        this.dailyTotalQuantity = quantity;
    }
}