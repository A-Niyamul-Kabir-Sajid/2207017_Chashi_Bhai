package com.sajid._207017_chashi_bhai.controllers;

import com.sajid._207017_chashi_bhai.App;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;

import java.io.File;

/**
 * ItemMyCropFarmerController - Controller for individual farmer crop card
 */
public class ItemMyCropFarmerController {

    @FXML private HBox root;
    @FXML private ImageView cropImage;
    @FXML private Label lblCropName;
    @FXML private Label lblPrice;
    @FXML private Label lblQuantity;
    @FXML private Label lblDate;
    @FXML private Label lblDistrict;
    @FXML private Label lblStatus;
    @FXML private Button btnEdit;
    @FXML private Button btnDelete;

    private int cropId;
    private String cropName;
    private Runnable onDeleteCallback;

    @FXML
    public void initialize() {
        // Make the entire card clickable to view crop details
        if (root != null) {
            root.setOnMouseClicked(event -> {
                // Don't trigger if clicking on buttons
                if (event.getTarget() instanceof Button) {
                    return;
                }
                onViewCrop();
            });
            root.setStyle(root.getStyle() + "; -fx-cursor: hand;");
        }
    }

    /**
     * Set crop data for this card
     */
    public void setCropData(int id, String name, String category, double price, String unit, 
                           double quantity, String date, String district, String status, 
                           String photoPath, Runnable onDelete) {
        this.cropId = id;
        this.cropName = name;
        this.onDeleteCallback = onDelete;
        
        // Set labels
        lblCropName.setText(name);
        lblPrice.setText(String.format("৳ %.2f / %s", price, unit));
        lblQuantity.setText(String.format("%.1f %s উপলব্ধ", quantity, unit));
        lblDate.setText("পোস্ট: " + (date != null ? date : "N/A"));
        lblDistrict.setText("জেলা: " + district);
        
        // Set status
        setStatus(status);
        
        // Load image
        if (photoPath != null && !photoPath.isEmpty()) {
            File photoFile = new File(photoPath);
            if (photoFile.exists()) {
                cropImage.setImage(new Image(photoFile.toURI().toString()));
            }
        }
    }

    private void setStatus(String status) {
        lblStatus.getStyleClass().clear();
        lblStatus.getStyleClass().add("status-label");
        
        switch (status) {
            case "active":
                lblStatus.setText("✓ সক্রিয়");
                lblStatus.getStyleClass().add("status-active");
                break;
            case "sold":
                lblStatus.setText("✓ বিক্রীত");
                lblStatus.getStyleClass().add("status-sold");
                break;
            case "expired":
                lblStatus.setText("✗ মেয়াদোত্তীর্ণ");
                lblStatus.getStyleClass().add("status-expired");
                break;
            case "deleted":
                lblStatus.setText("✗ মুছে ফেলা");
                lblStatus.getStyleClass().add("status-deleted");
                break;
            default:
                lblStatus.setText("—");
                break;
        }
    }

    @FXML
    private void onEditClicked() {
        if (cropId > 0) {
            App.setCurrentCropId(cropId);
            App.loadScene("edit-crop-view.fxml", "ফসল সম্পাদনা");
        }
    }

    @FXML
    private void onViewCrop() {
        if (cropId > 0) {
            App.setCurrentCropId(cropId);
            App.setCurrentOrderId(-1); // Clear order context - show full quantity
            App.loadScene("crop-detail-view.fxml", "ফসলের বিস্তারিত");
        }
    }

    @FXML
    private void onDeleteClicked() {
        if (onDeleteCallback != null) {
            onDeleteCallback.run();
        }
    }

    public HBox getRoot() {
        return root;
    }
}
