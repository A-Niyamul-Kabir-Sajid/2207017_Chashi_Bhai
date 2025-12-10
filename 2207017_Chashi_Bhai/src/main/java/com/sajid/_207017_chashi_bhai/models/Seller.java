package com.sajid._207017_chashi_bhai.models;

public class Seller extends User {
    private String shopName;
    private String businessLicense;

    public Seller(String username, String password, String shopName, String businessLicense) {
        super(username, password);
        this.shopName = shopName;
        this.businessLicense = businessLicense;
    }

    public String getShopName() {
        return shopName;
    }

    public void setShopName(String shopName) {
        this.shopName = shopName;
    }

    public String getBusinessLicense() {
        return businessLicense;
    }

    public void setBusinessLicense(String businessLicense) {
        this.businessLicense = businessLicense;
    }

    @Override
    public String toString() {
        return "Seller{" +
                "shopName='" + shopName + '\'' +
                ", businessLicense='" + businessLicense + '\'' +
                ", username='" + getUsername() + '\'' +
                '}';
    }
}