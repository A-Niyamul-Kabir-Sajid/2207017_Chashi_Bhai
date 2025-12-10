package com.sajid._207017_chashi_bhai.models;

public class Crop {
    private String name;
    private String type;
    private boolean isAvailable;

    public Crop(String name, String type, boolean isAvailable) {
        this.name = name;
        this.type = type;
        this.isAvailable = isAvailable;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isAvailable() {
        return isAvailable;
    }

    public void setAvailable(boolean available) {
        isAvailable = available;
    }
}