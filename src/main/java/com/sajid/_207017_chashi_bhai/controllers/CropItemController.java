package com.sajid._207017_chashi_bhai.controllers;

import com.sajid._207017_chashi_bhai.App;
import com.sajid._207017_chashi_bhai.utils.ImageBase64Util;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;

import java.io.File;

/**
 * CropItemController - Controller for individual crop card in the feed
 */
public class CropItemController {

    @FXML private VBox root;
    @FXML private ImageView imageView;
    @FXML private Label nameLabel;
    @FXML private Label categoryLabel;
    @FXML private Label farmerLabel;
    @FXML private Label remainingLabel;
    @FXML private Label priceLabel;
    @FXML private Button viewButton;

    private int cropId;

    /**
     * Set the crop data for this card
     */
    public void setCropData(int id, String name, String category, String farmerName, 
                           double quantity, String unit, double price, String photoPath, String photoBase64) {
        this.cropId = id;
        
        // Set labels
        nameLabel.setText(name);
        categoryLabel.setText(category);
        farmerLabel.setText("Farmer: " + farmerName);
        remainingLabel.setText(String.format("Remaining: %.1f %s", quantity, unit));
        priceLabel.setText(String.format("৳ %.2f", price));
        
        // Load image - try Base64 first, then file path
        Image image = null;
        if (photoBase64 != null && !photoBase64.isEmpty()) {
            System.out.println("[CropItem] Loading image from Base64 (length: " + photoBase64.length() + ")");
            image = ImageBase64Util.base64ToImage(photoBase64);
            if (image != null) {
                System.out.println("[CropItem] ✓ Base64 image loaded successfully");
            } else {
                System.out.println("[CropItem] ❌ Failed to load Base64 image");
            }
        }
        if (image == null && photoPath != null && !photoPath.isEmpty()) {
            System.out.println("[CropItem] Trying to load from file path: " + photoPath);
            File photoFile = new File(photoPath);
            if (photoFile.exists()) {
                image = new Image(photoFile.toURI().toString());
                System.out.println("[CropItem] ✓ File image loaded");
            } else {
                System.out.println("[CropItem] ❌ File not found: " + photoPath);
            }
        }
        if (image != null) {
            imageView.setImage(image);
            System.out.println("[CropItem] Image set to ImageView (" + image.getWidth() + "x" + image.getHeight() + ")");
        } else {
            System.out.println("[CropItem] ⚠️ No image available for crop " + id);
        }
    }

    @FXML
    private void onViewClicked() {
        if (cropId > 0) {
            App.setCurrentCropId(cropId);
            App.setCurrentOrderId(-1); // Clear order context - show full quantity
            App.setPreviousScene("crop-feed-view.fxml");
            App.loadScene("crop-detail-view.fxml", "ফসলের বিস্তারিত");
        }
    }

    public VBox getRoot() {
        return root;
    }
}
