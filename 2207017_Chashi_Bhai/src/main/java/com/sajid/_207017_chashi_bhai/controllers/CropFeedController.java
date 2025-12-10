package com.sajid._207017_chashi_bhai.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public class CropFeedController {

    @FXML
    private ListView<String> cropListView;

    @FXML
    private TextArea cropDetailsArea;

    @FXML
    private TextField searchField;

    @FXML
    private VBox cropFeedContainer;

    @FXML
    public void initialize() {
        // Initialize the crop feed with available crops
        loadCrops();
    }

    private void loadCrops() {
        // Load crops into the ListView (this is just a placeholder)
        cropListView.getItems().addAll("Wheat", "Rice", "Corn", "Barley");
    }

    @FXML
    private void onCropSelected() {
        // Display details of the selected crop
        String selectedCrop = cropListView.getSelectionModel().getSelectedItem();
        if (selectedCrop != null) {
            cropDetailsArea.setText("Details about " + selectedCrop);
        }
    }

    @FXML
    private void onSearch() {
        // Filter crops based on the search field
        String searchText = searchField.getText().toLowerCase();
        cropListView.getItems().clear();
        if (searchText.isEmpty()) {
            loadCrops();
        } else {
            if ("wheat".contains(searchText)) cropListView.getItems().add("Wheat");
            if ("rice".contains(searchText)) cropListView.getItems().add("Rice");
            if ("corn".contains(searchText)) cropListView.getItems().add("Corn");
            if ("barley".contains(searchText)) cropListView.getItems().add("Barley");
        }
    }
}