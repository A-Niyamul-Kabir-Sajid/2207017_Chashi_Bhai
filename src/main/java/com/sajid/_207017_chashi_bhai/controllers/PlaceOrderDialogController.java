package com.sajid._207017_chashi_bhai.controllers;

import com.sajid._207017_chashi_bhai.App;
import com.sajid._207017_chashi_bhai.models.User;
import com.sajid._207017_chashi_bhai.services.DatabaseService;
import com.sajid._207017_chashi_bhai.services.NotificationService;
// import com.sajid._207017_chashi_bhai.services.FirebaseSyncService; // Removed - using REST API now
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
    @FXML private Label lblCropId;
    @FXML private Label lblFarmerInfo;
    @FXML private Label lblPrice;
    @FXML private Label lblAvailable;
    @FXML private TextField txtQuantity;
    @FXML private TextArea txtAddress;
    @FXML private TextField txtDistrict;
    @FXML private ComboBox<String> cmbDistrict;
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
        if (lblCropId != null) {
            lblCropId.setText(String.valueOf(cropId));
        }
        if (lblFarmerInfo != null) {
            lblFarmerInfo.setText("ID: " + farmerId);
            loadFarmerSummary(farmerId);
        }
        lblPrice.setText(String.format("৳%.2f/কেজি", pricePerKg));
        lblAvailable.setText(String.format("%.1f কেজি", availableQuantity));
        
        // Pre-fill buyer's district if available
        if (currentUser != null && currentUser.getDistrict() != null) {
            if (txtDistrict != null) {
                txtDistrict.setText(currentUser.getDistrict());
            }
            if (cmbDistrict != null) {
                cmbDistrict.getSelectionModel().select(currentUser.getDistrict());
            }
        }
    }

    private void loadFarmerSummary(int farmerId) {
        String sql = "SELECT name, phone FROM users WHERE id = ?";
        DatabaseService.executeQueryAsync(sql, new Object[]{farmerId},
                rs -> {
                    try {
                        if (rs.next()) {
                            String name = rs.getString("name");
                            String phone = rs.getString("phone");
                            Platform.runLater(() -> {
                                if (lblFarmerInfo != null) {
                                    StringBuilder sb = new StringBuilder();
                                    sb.append("ID: ").append(farmerId);
                                    if (phone != null && !phone.isBlank()) sb.append(" | 📱 ").append(phone);
                                    if (name != null && !name.isBlank()) sb.append(" | ").append(name);
                                    lblFarmerInfo.setText(sb.toString());
                                }
                            });
                        }
                    } catch (Exception ignored) {
                    }
                },
                err -> {
                    // ignore: farmer summary is optional UI
                }
        );
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
        String district = "";
        if (cmbDistrict != null && cmbDistrict.getValue() != null) {
            district = cmbDistrict.getValue().trim();
        } else if (txtDistrict != null) {
            district = txtDistrict.getText().trim();
        }
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
        final String orderNumber = generateOrderNumber();
        final double totalAmount = quantity * pricePerKg;
        
        // Make variables final for lambda usage
        final String finalAddress = address;
        final String finalDistrict = district;
        final String finalUpazila = upazila;
        final String finalPaymentMethod = paymentMethod;
        final String finalNotes = notes;
        final double finalQuantity = quantity;
        final String finalCropName = cropName;

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
            finalAddress,
            finalDistrict,
            finalUpazila.isEmpty() ? null : finalUpazila,
            currentUser.getPhone(),
            currentUser.getName(),
            finalPaymentMethod,
            finalNotes.isEmpty() ? null : finalNotes
        };

        DatabaseService.executeUpdateAsync(insertSql, params,
            rowsAffected -> {
                Platform.runLater(() -> {
                    orderPlaced = true;
                    showSuccess("সফল!", "আপনার অর্ডার সফলভাবে সম্পন্ন হয়েছে। কৃষক শীঘ্রই যোগাযোগ করবেন।\nঅর্ডার নম্বর: " + orderNumber);

                    // Sync order to Firebase (REST API) and create notification
                    DatabaseService.executeQueryAsync(
                        "SELECT id FROM orders WHERE order_number = ?",
                        new Object[]{orderNumber},
                        rs -> {
                            try {
                                if (rs.next()) {
                                    int orderId = rs.getInt("id");
                                    
                                    // Create notification for farmer using NotificationService
                                    NotificationService.getInstance().notifyFarmerNewOrder(
                                        farmerId, orderId, currentUser.getName(), 
                                        finalCropName, finalQuantity, "কেজি"
                                    );
                                    
                                    // Prepare order data for Firebase
                                    java.util.Map<String, Object> orderData = new java.util.HashMap<>();
                                    orderData.put("order_number", orderNumber);
                                    orderData.put("crop_id", cropId);
                                    orderData.put("farmer_id", farmerId);
                                    orderData.put("buyer_id", currentUser.getId());
                                    orderData.put("quantity_kg", finalQuantity);
                                    orderData.put("price_per_kg", pricePerKg);
                                    orderData.put("total_amount", totalAmount);
                                    orderData.put("delivery_address", finalAddress);
                                    orderData.put("delivery_district", finalDistrict);
                                    orderData.put("delivery_upazila", finalUpazila.isEmpty() ? "" : finalUpazila);
                                    orderData.put("buyer_phone", currentUser.getPhone());
                                    orderData.put("buyer_name", currentUser.getName());
                                    orderData.put("status", "new");
                                    orderData.put("payment_status", "pending");
                                    orderData.put("payment_method", finalPaymentMethod);
                                    orderData.put("notes", finalNotes.isEmpty() ? "" : finalNotes);
                                    orderData.put("created_at", System.currentTimeMillis());
                                    
                                    // Sync to Firebase
                                    com.sajid._207017_chashi_bhai.services.FirebaseService.getInstance().saveOrder(
                                        String.valueOf(orderId),
                                        orderData,
                                        () -> System.out.println("✅ Order synced to Firebase: " + orderNumber),
                                        err -> System.err.println("⚠️ Firebase sync failed (order saved locally): " + err.getMessage())
                                    );
                                }
                            } catch (Exception e) {
                                System.err.println("⚠️ Failed to sync order to Firebase: " + e.getMessage());
                            }
                        },
                        err -> {
                            System.err.println("⚠️ Could not retrieve order ID for sync: " + err.getMessage());
                        }
                    );
                    
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
