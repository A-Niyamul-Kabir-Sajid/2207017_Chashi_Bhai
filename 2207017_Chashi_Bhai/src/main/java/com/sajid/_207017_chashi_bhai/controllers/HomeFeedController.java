package com.sajid._207017_chashi_bhai.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.ListView;

public class HomeFeedController {

    @FXML
    private ListView<String> feedListView;

    @FXML
    public void initialize() {
        // Load the home feed content here
        loadHomeFeed();
    }

    private void loadHomeFeed() {
        // Example data for the home feed
        feedListView.getItems().addAll("Crop 1", "Crop 2", "Crop 3", "Crop 4");
    }
}