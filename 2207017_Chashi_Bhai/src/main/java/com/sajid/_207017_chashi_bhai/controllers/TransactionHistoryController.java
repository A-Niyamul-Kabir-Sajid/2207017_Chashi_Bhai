package com.sajid._207017_chashi_bhai.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;

public class TransactionHistoryController {

    @FXML
    private ListView<String> transactionListView;

    @FXML
    private TextArea transactionDetailsTextArea;

    // Method to initialize the controller
    @FXML
    public void initialize() {
        loadTransactionHistory();
    }

    // Method to load transaction history
    private void loadTransactionHistory() {
        // This method will fetch and display the transaction history
        // For now, we can add some dummy data
        transactionListView.getItems().addAll("Transaction 1", "Transaction 2", "Transaction 3");
    }

    // Method to handle selection of a transaction
    @FXML
    private void handleTransactionSelection() {
        String selectedTransaction = transactionListView.getSelectionModel().getSelectedItem();
        if (selectedTransaction != null) {
            displayTransactionDetails(selectedTransaction);
        }
    }

    // Method to display details of the selected transaction
    private void displayTransactionDetails(String transaction) {
        // This method will display the details of the selected transaction
        // For now, we will just show a placeholder message
        transactionDetailsTextArea.setText("Details for " + transaction);
    }
}