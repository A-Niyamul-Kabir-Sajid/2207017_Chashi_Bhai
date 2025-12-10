package com.sajid._207017_chashi_bhai.models;

public class Buyer extends User {
    private String buyerId;
    private String address;
    private String phoneNumber;

    public Buyer(String username, String password, String buyerId, String address, String phoneNumber) {
        super(username, password);
        this.buyerId = buyerId;
        this.address = address;
        this.phoneNumber = phoneNumber;
    }

    public String getBuyerId() {
        return buyerId;
    }

    public void setBuyerId(String buyerId) {
        this.buyerId = buyerId;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
}