package com.sajid._207017_chashi_bhai.controllers;

import com.sajid._207017_chashi_bhai.App;
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
                           double quantity, String unit, double price, String photoPath) {
        this.cropId = id;
        
        // Set labels
        nameLabel.setText(name);
        categoryLabel.setText(category);
        farmerLabel.setText("Farmer: " + farmerName);
        remainingLabel.setText(String.format("Remaining: %.1f %s", quantity, unit));
        priceLabel.setText(String.format("৳ %.2f", price));
        
        // Load image
        if (photoPath != null && !photoPath.isEmpty()) {
            File photoFile = new File(photoPath);
            if (photoFile.exists()) {
                imageView.setImage(new Image(photoFile.toURI().toString()));
            }
        }
    }

    @FXML
    private void onViewClicked() {
        if (cropId > 0) {
            App.setCurrentCropId(cropId);
            App.setCurrentOrderId(-1); // Clear order context - show full quantity
            App.loadScene("crop-detail-view.fxml", "ফসলের বিস্তারিত");
        }
    }

    public VBox getRoot() {
        return root;
    }
}
