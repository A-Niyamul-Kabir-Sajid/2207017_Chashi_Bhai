package com.sajid._207017_chashi_bhai.controllers;

import com.sajid._207017_chashi_bhai.App;
import com.sajid._207017_chashi_bhai.models.User;
import com.sajid._207017_chashi_bhai.services.DatabaseService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;

/**
 * OrderDetailController - Display comprehensive order details
 * Accessible by both farmers and buyers
 */
public class OrderDetailController {

    @FXML private Label lblOrderNumber;
    @FXML private Label lblOrderStatus;
    @FXML private Label lblOrderDate;
    @FXML private Label lblPaymentStatus;
    @FXML private Label lblTotalAmount;
    
    // Crop details
    @FXML private ImageView imgCrop;
    @FXML private Label lblCropName;
    @FXML private Label lblProductCode;
    @FXML private Label lblCropCategory;
    @FXML private Label lblOrderedQuantity;
    @FXML private Label lblPricePerKg;
    
    // Farmer details
    @FXML private ImageView imgFarmer;
    @FXML private Label lblFarmerName;
    @FXML private Label lblFarmerVerified;
    @FXML private Label lblFarmerId;
    @FXML private Label lblFarmerPhone;
    @FXML private Label lblFarmerDistrict;
    
    // Buyer details
    @FXML private Label lblBuyerName;
    @FXML private Label lblBuyerId;
    @FXML private Label lblBuyerPhone;
    @FXML private Label lblDeliveryAddress;
    
    // Transport & Notes
    @FXML private Label lblTransport;
    @FXML private Label lblNotes;
    
    // Action buttons
    @FXML private HBox hboxFarmerActions;
    @FXML private HBox hboxBuyerActions;
    @FXML private Button btnAcceptOrder;
    @FXML private Button btnRejectOrder;
    @FXML private Button btnMarkDelivered;
    @FXML private Button btnCancelOrder;
    @FXML private Button btnRateOrder;

    private User currentUser;
    private int orderId;
    private String orderNumber;
    private int cropId;
    private int farmerId;
    private String farmerPhone;
    private String orderStatus;

    @FXML
    public void initialize() {
        currentUser = App.getCurrentUser();
        orderId = App.getCurrentOrderId();
        
        if (currentUser == null) {
            showError("অ্যাক্সেস অস্বীকার", "দয়া করে লগইন করুন।");
            App.loadScene("login-view.fxml", "Login");
            return;
        }

        if (orderId <= 0) {
            showError("ত্রুটি", "অর্ডারের তথ্য খুঁজে পাওয়া যায়নি।");
            onBack();
            return;
        }

        loadOrderDetails();
    }

    /**
     * Load order from database by order ID or order number
     */
    public void loadOrderByNumber(String orderNum) {
        String sql = "SELECT o.*, c.name as crop_name, c.product_code, c.category, " +
                    "(SELECT photo_path FROM crop_photos WHERE crop_id = c.id ORDER BY photo_order LIMIT 1) as crop_photo, " +
                    "f.name as farmer_name, f.phone as farmer_phone, f.district as farmer_district, f.is_verified as farmer_verified, f.profile_photo as farmer_photo, " +
                    "b.name as buyer_name, b.phone as buyer_phone_db " +
                    "FROM orders o " +
                    "JOIN crops c ON o.crop_id = c.id " +
                    "JOIN users f ON o.farmer_id = f.id " +
                    "JOIN users b ON o.buyer_id = b.id " +
                    "WHERE o.order_number = ?";
        
        DatabaseService.executeQueryAsync(sql, new Object[]{orderNum},
            rs -> Platform.runLater(() -> populateOrderDetails(rs)),
            err -> Platform.runLater(() -> {
                showError("ত্রুটি", "অর্ডার লোড করতে ব্যর্থ হয়েছে।");
                err.printStackTrace();
            })
        );
    }

    private void loadOrderDetails() {
        String sql = "SELECT o.*, c.name as crop_name, c.product_code, c.category, " +
                    "c.price_per_kg as unit_price, " +
                    "(SELECT photo_path FROM crop_photos WHERE crop_id = c.id ORDER BY photo_order LIMIT 1) as crop_photo, " +
                    "f.name as farmer_name, f.phone as farmer_phone, f.district as farmer_district, f.is_verified as farmer_verified, f.profile_photo as farmer_photo, " +
                    "b.name as buyer_name_db, b.phone as buyer_phone_db " +
                    "FROM orders o " +
                    "JOIN crops c ON o.crop_id = c.id " +
                    "JOIN users f ON o.farmer_id = f.id " +
                    "JOIN users b ON o.buyer_id = b.id " +
                    "WHERE o.id = ?";
        
        DatabaseService.executeQueryAsync(sql, new Object[]{orderId},
            rs -> Platform.runLater(() -> populateOrderDetails(rs)),
            err -> Platform.runLater(() -> {
                showError("ত্রুটি", "অর্ডার লোড করতে ব্যর্থ হয়েছে।");
                err.printStackTrace();
            })
        );
    }

    private void populateOrderDetails(java.sql.ResultSet rs) {
        try {
            if (rs.next()) {
                orderId = rs.getInt("id");
                orderNumber = rs.getString("order_number");
                cropId = rs.getInt("crop_id");
                farmerId = rs.getInt("farmer_id");
                farmerPhone = rs.getString("farmer_phone");
                orderStatus = rs.getString("status");
                
                // Order info
                lblOrderNumber.setText(orderNumber != null ? orderNumber : "N/A");
                lblOrderStatus.setText(getStatusDisplay(orderStatus));
                lblOrderDate.setText(safeString(rs, "created_at").substring(0, 10));
                lblPaymentStatus.setText(getPaymentStatusDisplay(safeString(rs, "payment_status")));
                lblTotalAmount.setText(String.format("৳ %.2f", rs.getDouble("total_amount")));
                
                // Crop info
                lblCropName.setText(safeString(rs, "crop_name"));
                lblProductCode.setText(safeString(rs, "product_code"));
                lblCropCategory.setText(safeString(rs, "category"));
                lblOrderedQuantity.setText(String.format("%.1f কেজি", rs.getDouble("quantity_kg")));
                lblPricePerKg.setText(String.format("৳ %.2f", rs.getDouble("price_per_kg")));
                
                // Load crop photo
                String cropPhotoPath = rs.getString("crop_photo");
                if (cropPhotoPath != null && !cropPhotoPath.isEmpty()) {
                    File photoFile = new File(cropPhotoPath);
                    if (photoFile.exists()) {
                        imgCrop.setImage(new Image(photoFile.toURI().toString()));
                    }
                }
                
                // Farmer info
                lblFarmerName.setText(safeString(rs, "farmer_name"));
                lblFarmerId.setText("ID: " + farmerId);
                lblFarmerPhone.setText("📱 " + farmerPhone);
                lblFarmerDistrict.setText("📍 " + safeString(rs, "farmer_district"));
                boolean farmerVerified = rs.getBoolean("farmer_verified");
                if (lblFarmerVerified != null) {
                    lblFarmerVerified.setVisible(farmerVerified);
                }
                
                // Load farmer photo
                String farmerPhotoPath = rs.getString("farmer_photo");
                if (farmerPhotoPath != null && !farmerPhotoPath.isEmpty()) {
                    File photoFile = new File(farmerPhotoPath);
                    if (photoFile.exists()) {
                        imgFarmer.setImage(new Image(photoFile.toURI().toString()));
                    }
                }
                
                // Buyer info
                lblBuyerName.setText(safeString(rs, "buyer_name"));
                lblBuyerId.setText(String.valueOf(rs.getInt("buyer_id")));
                lblBuyerPhone.setText(safeString(rs, "buyer_phone"));
                lblDeliveryAddress.setText(safeString(rs, "delivery_address") + ", " + 
                                          safeString(rs, "delivery_district") + ", " + 
                                          safeString(rs, "delivery_upazila"));
                
                // Transport & Notes
                lblTransport.setText(safeString(rs, "transport") != null ? safeString(rs, "transport") : "N/A");
                lblNotes.setText(safeString(rs, "notes") != null ? safeString(rs, "notes") : "কোনো নোট নেই");
                
                // Show appropriate action buttons based on role and order status
                setupActionButtons();
            }
        } catch (Exception e) {
            e.printStackTrace();
            showError("ত্রুটি", "অর্ডার তথ্য পার্স করতে ব্যর্থ হয়েছে।");
        }
    }

    private void setupActionButtons() {
        String role = currentUser.getRole();
        
        if ("farmer".equals(role) && farmerId == currentUser.getId()) {
            hboxFarmerActions.setVisible(true);
            hboxFarmerActions.setManaged(true);
            
            // Show/hide buttons based on status
            switch (orderStatus) {
                case "new":
                case "pending":
                    btnAcceptOrder.setVisible(true);
                    btnRejectOrder.setVisible(true);
                    btnMarkDelivered.setVisible(false);
                    break;
                case "accepted":
                    btnAcceptOrder.setVisible(false);
                    btnRejectOrder.setVisible(false);
                    btnMarkDelivered.setVisible(true);
                    break;
                default:
                    hboxFarmerActions.setVisible(false);
                    hboxFarmerActions.setManaged(false);
            }
        } else if ("buyer".equals(role)) {
            hboxBuyerActions.setVisible(true);
            hboxBuyerActions.setManaged(true);
            
            switch (orderStatus) {
                case "new":
                case "pending":
                    btnCancelOrder.setVisible(true);
                    btnRateOrder.setVisible(false);
                    break;
                case "delivered":
                case "completed":
                    btnCancelOrder.setVisible(false);
                    btnRateOrder.setVisible(true);
                    break;
                default:
                    btnCancelOrder.setVisible(false);
                    btnRateOrder.setVisible(false);
            }
        }
    }

    private String getStatusDisplay(String status) {
        if (status == null) return "❓ অজানা";
        switch (status) {
            case "new": return "🆕 নতুন";
            case "pending": return "⏳ অপেক্ষমাণ";
            case "accepted": return "✅ গৃহীত";
            case "rejected": return "❌ প্রত্যাখ্যাত";
            case "in_transit": return "🚚 পরিবহনে";
            case "delivered": return "📦 ডেলিভারি সম্পন্ন";
            case "completed": return "✅ সম্পন্ন";
            case "cancelled": return "❌ বাতিল";
            default: return "❓ " + status;
        }
    }

    private String getPaymentStatusDisplay(String status) {
        if (status == null) return "বকেয়া";
        switch (status) {
            case "pending": return "বকেয়া";
            case "partial": return "আংশিক পরিশোধ";
            case "paid": return "পরিশোধিত";
            case "refunded": return "ফেরত";
            default: return status;
        }
    }

    private String safeString(java.sql.ResultSet rs, String col) {
        try { return rs.getString(col); } catch (Exception e) { return ""; }
    }

    @FXML
    private void onBack() {
        String role = currentUser.getRole();
        if ("farmer".equals(role)) {
            App.loadScene("farmer-orders-view.fxml", "আমার অর্ডারসমূহ");
        } else {
            App.loadScene("buyer-orders-view.fxml", "আমার অর্ডারসমূহ");
        }
    }

    @FXML
    private void onCopyOrderNumber() {
        try {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(orderNumber);
            clipboard.setContent(content);
            showInfo("কপি সফল", "অর্ডার নম্বর কপি হয়েছে: " + orderNumber);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void onViewCrop() {
        App.setCurrentCropId(cropId);
        App.setCurrentOrderId(orderId); // Pass order context to show ordered quantity
        App.setPreviousScene("order-detail-view.fxml");
        App.loadScene("crop-detail-view.fxml", "ফসলের বিস্তারিত");
    }

    @FXML
    private void onCallFarmer() {
        if (farmerPhone != null && !farmerPhone.isEmpty()) {
            try {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().browse(new URI("tel:" + farmerPhone));
                }
            } catch (Exception e) {
                showInfo("ফোন নম্বর", "কল করুন: " + farmerPhone);
            }
        }
    }

    @FXML
    private void onViewFarmerProfile() {
        App.setCurrentViewedUserId(farmerId);
        App.loadScene("public-farmer-profile-view.fxml", "কৃষকের প্রোফাইল");
    }

    @FXML
    private void onAcceptOrder() {
        updateOrderStatus("accepted", "✅ অর্ডার গৃহীত হয়েছে!");
    }

    @FXML
    private void onRejectOrder() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("নিশ্চিত করুন");
        confirm.setHeaderText("আপনি কি এই অর্ডারটি প্রত্যাখ্যান করতে চান?");
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                updateOrderStatus("rejected", "❌ অর্ডার প্রত্যাখ্যাত হয়েছে।");
            }
        });
    }

    @FXML
    private void onMarkDelivered() {
        updateOrderStatus("delivered", "📦 ডেলিভারি সম্পন্ন!");
    }

    @FXML
    private void onCancelOrder() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("নিশ্চিত করুন");
        confirm.setHeaderText("আপনি কি এই অর্ডারটি বাতিল করতে চান?");
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                updateOrderStatus("cancelled", "❌ অর্ডার বাতিল হয়েছে।");
            }
        });
    }

    @FXML
    private void onRateOrder() {
        App.setCurrentOrderId(orderId);
        App.loadScene("rate-order-dialog.fxml", "রেটিং দিন");
    }

    private void updateOrderStatus(String newStatus, String successMessage) {
        String sql = "UPDATE orders SET status = ?, updated_at = datetime('now') WHERE id = ?";
        DatabaseService.executeUpdateAsync(sql, new Object[]{newStatus, orderId},
            rows -> Platform.runLater(() -> {
                if (rows > 0) {
                    showInfo("সফল", successMessage);
                    loadOrderDetails(); // Refresh
                }
            }),
            err -> Platform.runLater(() -> {
                showError("ত্রুটি", "স্ট্যাটাস আপডেট করতে ব্যর্থ হয়েছে।");
                err.printStackTrace();
            })
        );
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
