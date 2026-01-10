package com.sajid._207017_chashi_bhai.models;

/**
 * User model - Represents a user (farmer, buyer, or admin)
 */
public class User {
    private int id;
    private String name;
    private String phone;
    private String role; // "farmer", "buyer", "admin"
    private String district;
    private String upazila;
    private String farmType;
    private String profilePhoto;
    private boolean isVerified;
    private String createdAt;
    
    // Farmer statistics
    private int totalAcceptedOrders;
    private String mostSoldCrop;
    private double totalIncome;
    private double rating;
    
    // Buyer statistics
    private int totalBuyerOrders;
    private String mostBoughtCrop;
    private double totalExpense;

    // Constructors
    public User() {}

    public User(int id, String name, String phone, String role) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.role = role;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public String getUpazila() {
        return upazila;
    }

    public void setUpazila(String upazila) {
        this.upazila = upazila;
    }

    public String getFarmType() {
        return farmType;
    }

    public void setFarmType(String farmType) {
        this.farmType = farmType;
    }

    public String getProfilePhoto() {
        return profilePhoto;
    }

    public void setProfilePhoto(String profilePhoto) {
        this.profilePhoto = profilePhoto;
    }

    public boolean isVerified() {
        return isVerified;
    }

    public void setVerified(boolean verified) {
        isVerified = verified;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUserId() {
        return "USR" + String.format("%06d", id);
    }

    public int getTotalAcceptedOrders() {
        return totalAcceptedOrders;
    }

    public void setTotalAcceptedOrders(int totalAcceptedOrders) {
        this.totalAcceptedOrders = totalAcceptedOrders;
    }

    public String getMostSoldCrop() {
        return mostSoldCrop;
    }

    public void setMostSoldCrop(String mostSoldCrop) {
        this.mostSoldCrop = mostSoldCrop;
    }

    public double getTotalIncome() {
        return totalIncome;
    }

    public void setTotalIncome(double totalIncome) {
        this.totalIncome = totalIncome;
    }

    public double getRating() {
        return rating;
    }

    public void setRating(double rating) {
        this.rating = rating;
    }

    public int getTotalBuyerOrders() {
        return totalBuyerOrders;
    }

    public void setTotalBuyerOrders(int totalBuyerOrders) {
        this.totalBuyerOrders = totalBuyerOrders;
    }

    public String getMostBoughtCrop() {
        return mostBoughtCrop;
    }

    public void setMostBoughtCrop(String mostBoughtCrop) {
        this.mostBoughtCrop = mostBoughtCrop;
    }

    public double getTotalExpense() {
        return totalExpense;
    }

    public void setTotalExpense(double totalExpense) {
        this.totalExpense = totalExpense;
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", phone='" + phone + '\'' +
                ", role='" + role + '\'' +
                ", isVerified=" + isVerified +
                '}';
    }
}
