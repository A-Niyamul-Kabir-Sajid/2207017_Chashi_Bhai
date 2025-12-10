package com.sajid._207017_chashi_bhai.models;

public class Admin extends User {
    private String adminId;

    public Admin(String username, String password, String adminId) {
        super(username, password);
        this.adminId = adminId;
    }

    public String getAdminId() {
        return adminId;
    }

    public void setAdminId(String adminId) {
        this.adminId = adminId;
    }

    // Additional methods specific to Admin can be added here
}