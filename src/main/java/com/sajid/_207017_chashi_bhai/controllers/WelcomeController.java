package com.sajid._207017_chashi_bhai.controllers;

import com.sajid._207017_chashi_bhai.App;
import javafx.fxml.FXML;

public class WelcomeController {

    @FXML
    protected void onLoginClick() {
        App.loadScene("login-view.fxml", "Login");
    }

    @FXML
    protected void onSignupClick() {
        App.loadScene("signup-view.fxml", "Sign Up");
    }
}
