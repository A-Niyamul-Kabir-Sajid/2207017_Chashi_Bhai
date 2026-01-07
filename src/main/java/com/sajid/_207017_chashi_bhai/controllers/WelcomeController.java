package com.sajid._207017_chashi_bhai.controllers;

import com.sajid._207017_chashi_bhai.App;
import javafx.fxml.FXML;

public class WelcomeController {

    @FXML
    protected void onLoginClick() {
        System.out.println("Login button clicked - navigating to login page");
        App.loadScene("login-view.fxml", "Login - Chashi Bhai");
    }

    @FXML
    protected void onSignupClick() {
        System.out.println("Signup button clicked - navigating to signup page");
        App.loadScene("signup-view.fxml", "Sign Up - Chashi Bhai");
    }
}
