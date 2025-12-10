package com.sajid._207017_chashi_bhai.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class WelcomeController {

    @FXML
    private VBox welcomeBox;

    @FXML
    private Button buyerSignUpButton;

    @FXML
    private Button sellerSignUpButton;

    @FXML
    private Button adminSignUpButton;

    @FXML
    private Label welcomeLabel;

    @FXML
    public void initialize() {
        welcomeLabel.setText("Welcome to the Chashi Bhai Application!");
    }

    @FXML
    private void handleBuyerSignUp() {
        // Logic to navigate to buyer sign-up page
    }

    @FXML
    private void handleSellerSignUp() {
        // Logic to navigate to seller sign-up page
    }

    @FXML
    private void handleAdminSignUp() {
        // Logic to navigate to admin sign-up page
    }
}