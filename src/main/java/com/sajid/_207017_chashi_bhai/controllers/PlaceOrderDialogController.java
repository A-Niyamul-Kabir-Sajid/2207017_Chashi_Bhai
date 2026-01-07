package com.sajid._207017_chashi_bhai.controllers;

import com.sajid._207017_chashi_bhai.App;
import com.sajid._207017_chashi_bhai.models.User;
import com.sajid._207017_chashi_bhai.services.DatabaseService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * PlaceOrderDialogController - Dialog for placing an order
 */
public class PlaceOrderDialogController {

    @FXML private Label lblCropName;
    @FXML private Label lblPrice;
    @FXML private Label lblAvailable;
    @FXML private TextField txtQuantity;
    @FXML private TextArea txtAddress;
    @FXML private TextField txtDistrict;
    @FXML private TextField txtUpazila;
    @FXML private ComboBox<String> cmbPaymentMethod;
    @FXML private TextArea txtNotes;
    @FXML private Label lblTotalPrice;
    @FXML private Label lblError;

    private int cropId;
    private int farmerId;
    private String cropName;
    private double pricePerKg;
    private double availableQuantity;
    private User currentUser;
    private Stage dialogStage;
    private boolean orderPlaced = false;

    public void initialize() {
        currentUser = App.getCurrentUser();
        
        // Add listener to quantity field to update total price
        txtQuantity.textProperty().addListener((obs, oldVal, newVal) -> updateTotalPrice());
        
        // Set default payment method
        cmbPaymentMethod.getSelectionModel().selectFirst();
    }

    /**
     * Set crop details for the order
     */
    public void setCropDetails(int cropId, int farmerId, String cropName, double pricePerKg, double availableQuantity) {
        this.cropId = cropId;
        this.farmerId = farmerId;
        this.cropName = cropName;
        this.pricePerKg = pricePerKg;
        this.availableQuantity = availableQuantity;

        lblCropName.setText(cropName);
        lblPrice.setText(String.format("৳%.2f/কেজি", pricePerKg));
        lblAvailable.setText(String.format("%.1f কেজি", availableQuantity));
        
        // Pre-fill buyer's district if available
        if (currentUser != null && currentUser.getDistrict() != null) {
            txtDistrict.setText(currentUser.getDistrict());
        }
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    public boolean isOrderPlaced() {
        return orderPlaced;
    }

    private void updateTotalPrice() {
        try {
            String quantityText = txtQuantity.getText().trim();
            if (!quantityText.isEmpty()) {
                double quantity = Double.parseDouble(quantityText);
                double total = quantity * pricePerKg;
                lblTotalPrice.setText(String.format("৳ %.2f", total));
                lblError.setVisible(false);
            } else {
                lblTotalPrice.setText("৳ 0.00");
            }
        } catch (NumberFormatException e) {
            lblTotalPrice.setText("৳ 0.00");
        }
    }

    @FXML
    private void onConfirm() {
        // Validate input
        String quantityText = txtQuantity.getText().trim();
        String address = txtAddress.getText().trim();
        String district = txtDistrict.getText().trim();
        String upazila = txtUpazila.getText().trim();
        String paymentMethod = cmbPaymentMethod.getValue();
        String notes = txtNotes.getText().trim();

        if (quantityText.isEmpty()) {
            showError("পরিমাণ লিখুন");
            return;
        }

        double quantity;
        try {
            quantity = Double.parseDouble(quantityText);
            if (quantity <= 0) {
                showError("সঠিক পরিমাণ লিখুন (০ এর বেশি)");
                return;
            }
            if (quantity > availableQuantity) {
                showError(String.format("সর্বোচ্চ %.1f কেজি অর্ডার করতে পারবেন", availableQuantity));
                return;
            }
        } catch (NumberFormatException e) {
            showError("সঠিক সংখ্যা লিখুন");
            return;
        }

        if (address.isEmpty()) {
            showError("ডেলিভারি ঠিকানা লিখুন");
            return;
        }

        if (district.isEmpty()) {
            showError("জেলা লিখুন");
            return;
        }

        if (paymentMethod == null || paymentMethod.isEmpty()) {
            showError("পেমেন্ট পদ্ধতি নির্বাচন করুন");
            return;
        }

        // Generate order number
        String orderNumber = generateOrderNumber();
        double totalAmount = quantity * pricePerKg;

        // Insert order into database
        String insertSql = "INSERT INTO orders (order_number, crop_id, farmer_id, buyer_id, quantity_kg, " +
                          "price_per_kg, total_amount, delivery_address, delivery_district, delivery_upazila, " +
                          "buyer_phone, buyer_name, status, payment_status, payment_method, notes) " +
                          "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'new', 'pending', ?, ?)";

        Object[] params = {
            orderNumber,
            cropId,
            farmerId,
            currentUser.getId(),
            quantity,
            pricePerKg,
            totalAmount,
            address,
            district,
            upazila.isEmpty() ? null : upazila,
            currentUser.getPhone(),
            currentUser.getName(),
            paymentMethod,
            notes.isEmpty() ? null : notes
        };

        DatabaseService.executeUpdateAsync(insertSql, params,
            rowsAffected -> {
                Platform.runLater(() -> {
                    orderPlaced = true;
                    showSuccess("সফল!", "আপনার অর্ডার সফলভাবে সম্পন্ন হয়েছে। কৃষক শীঘ্রই যোগাযোগ করবেন।\nঅর্ডার নম্বর: " + orderNumber);
                    
                    // Create notification for farmer
                    createNotification(farmerId, "নতুন অর্ডার", 
                        currentUser.getName() + " " + quantity + " কেজি " + cropName + " অর্ডার করেছেন।");
                    
                    if (dialogStage != null) {
                        dialogStage.close();
                    }
                });
            },
            error -> {
                Platform.runLater(() -> {
                    showError("অর্ডার করতে ব্যর্থ হয়েছে। আবার চেষ্টা করুন।");
                    error.printStackTrace();
                });
            }
        );
    }

    @FXML
    private void onCancel() {
        if (dialogStage != null) {
            dialogStage.close();
        }
    }

    private String generateOrderNumber() {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        int random = (int) (Math.random() * 9000) + 1000;
        return "ORD-" + date + "-" + random;
    }

    private void createNotification(int userId, String title, String message) {
        String sql = "INSERT INTO notifications (user_id, title, message, type) VALUES (?, ?, ?, 'order')";
        DatabaseService.executeUpdateAsync(sql, new Object[]{userId, title, message}, 
            result -> {}, 
            error -> error.printStackTrace()
        );
    }

    private void showError(String message) {
        lblError.setText("⚠ " + message);
        lblError.setVisible(true);
    }

    private void showSuccess(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
