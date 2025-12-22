package com.sajid._207017_chashi_bhai.models;

/**
 * Crop model - Represents a crop listing
 */
public class Crop {
    private int id;
    private int farmerId;
    private String name;
    private String category;
    private double price;
    private String unit;
    private double quantity;
    private String harvestDate;
    private String district;
    private String transportInfo;
    private String description;
    private String status; // "active", "sold", "expired"
    private String createdAt;

    // For joined queries
    private String farmerName;
    private boolean farmerVerified;

    // Constructors
    public Crop() {}

    public Crop(int id, String name, double price, String unit) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.unit = unit;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getFarmerId() {
        return farmerId;
    }

    public void setFarmerId(int farmerId) {
        this.farmerId = farmerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public double getQuantity() {
        return quantity;
    }

    public void setQuantity(double quantity) {
        this.quantity = quantity;
    }

    public String getHarvestDate() {
        return harvestDate;
    }

    public void setHarvestDate(String harvestDate) {
        this.harvestDate = harvestDate;
    }

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public String getTransportInfo() {
        return transportInfo;
    }

    public void setTransportInfo(String transportInfo) {
        this.transportInfo = transportInfo;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getFarmerName() {
        return farmerName;
    }

    public void setFarmerName(String farmerName) {
        this.farmerName = farmerName;
    }

    public boolean isFarmerVerified() {
        return farmerVerified;
    }

    public void setFarmerVerified(boolean farmerVerified) {
        this.farmerVerified = farmerVerified;
    }

    @Override
    public String toString() {
        return "Crop{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", category='" + category + '\'' +
                ", price=" + price +
                ", unit='" + unit + '\'' +
                ", quantity=" + quantity +
                ", status='" + status + '\'' +
                '}';
    }
}
