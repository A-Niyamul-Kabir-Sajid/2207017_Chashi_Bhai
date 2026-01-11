package com.sajid._207017_chashi_bhai.controllers;

import com.sajid._207017_chashi_bhai.App;
import com.sajid._207017_chashi_bhai.models.User;
import com.sajid._207017_chashi_bhai.services.DatabaseService;
import com.sajid._207017_chashi_bhai.services.FirebaseSyncService;
import com.sajid._207017_chashi_bhai.services.OrderService;
import com.sajid._207017_chashi_bhai.utils.StatisticsCalculator;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

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
    @FXML private Label lblOrderId;
    @FXML private Label lblOrderDate;
    @FXML private Label lblPaymentStatus;
    @FXML private Label lblTotalAmount;

    // Status timeline
    @FXML private Label lblStatusRequestedAt;
    @FXML private Label lblStatusAcceptedAt;
    @FXML private Label lblStatusDeliveredAt;
    @FXML private Label lblStatusCompletedAt;
    
    // Crop details
    @FXML private ImageView imgCrop;
    @FXML private Label lblCropName;
    @FXML private Label lblProductCode;
    @FXML private Label lblCropId;
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

    @FXML private Button btnCallFarmer;
    @FXML private Button btnViewFarmerProfile;
    
    // Buyer details
    @FXML private Label lblBuyerName;
    @FXML private Label lblBuyerId;
    @FXML private Label lblBuyerPhone;
    @FXML private Label lblDeliveryAddress;

    @FXML private Button btnCallBuyer;
    @FXML private Button btnViewBuyerProfile;
    @FXML private Button btnChatBuyer;
    @FXML private Button btnChatFarmer;
    
    // Transport & Notes
    @FXML private Label lblTransport;
    @FXML private Label lblNotes;
    
    // Action buttons
    @FXML private HBox hboxFarmerActions;
    @FXML private HBox hboxBuyerActions;
    @FXML private Button btnAcceptOrder;
    @FXML private Button btnRejectOrder;
    @FXML private Button btnMarkDelivered;
    @FXML private Button btnMarkReceived;
    @FXML private Button btnCancelOrder;
    @FXML private Button btnRateOrder;

    private User currentUser;
    private int orderId;
    private String orderNumber;
    private int cropId;
    private int farmerId;
    private String farmerPhone;
    private String orderStatus;

    private int buyerId;
    private String buyerName;
    private String buyerPhone;

    private static class OrderDetailsRow {
        final int orderId;
        final String orderNumber;
        final int cropId;
        final int farmerId;
        final int buyerId;
        final String orderStatus;
        final String createdAt;
        final String acceptedAt;
        final String inTransitAt;
        final String completedAt;
        final String paymentStatus;
        final double totalAmount;
        final double quantityKg;
        final double pricePerKg;

        final String cropName;
        final String productCode;
        final String category;
        final String cropPhoto;

        final String farmerName;
        final String farmerPhone;
        final String farmerDistrict;
        final boolean farmerVerified;
        final String farmerPhoto;

        final String buyerName;
        final String buyerPhone;

        final String deliveryAddress;
        final String deliveryDistrict;
        final String deliveryUpazila;

        final String notes;
        final String transport;

        private OrderDetailsRow(int orderId, String orderNumber, int cropId, int farmerId, int buyerId,
                                String orderStatus, String createdAt, String acceptedAt, String inTransitAt, String completedAt,
                                String paymentStatus, double totalAmount, double quantityKg, double pricePerKg,
                                String cropName, String productCode, String category, String cropPhoto,
                                String farmerName, String farmerPhone, String farmerDistrict, boolean farmerVerified, String farmerPhoto,
                                String buyerName, String buyerPhone,
                                String deliveryAddress, String deliveryDistrict, String deliveryUpazila,
                                String notes, String transport) {
            this.orderId = orderId;
            this.orderNumber = orderNumber;
            this.cropId = cropId;
            this.farmerId = farmerId;
            this.buyerId = buyerId;
            this.orderStatus = orderStatus;
            this.createdAt = createdAt;
            this.acceptedAt = acceptedAt;
            this.inTransitAt = inTransitAt;
            this.completedAt = completedAt;
            this.paymentStatus = paymentStatus;
            this.totalAmount = totalAmount;
            this.quantityKg = quantityKg;
            this.pricePerKg = pricePerKg;
            this.cropName = cropName;
            this.productCode = productCode;
            this.category = category;
            this.cropPhoto = cropPhoto;
            this.farmerName = farmerName;
            this.farmerPhone = farmerPhone;
            this.farmerDistrict = farmerDistrict;
            this.farmerVerified = farmerVerified;
            this.farmerPhoto = farmerPhoto;
            this.buyerName = buyerName;
            this.buyerPhone = buyerPhone;
            this.deliveryAddress = deliveryAddress;
            this.deliveryDistrict = deliveryDistrict;
            this.deliveryUpazila = deliveryUpazila;
            this.notes = notes;
            this.transport = transport;
        }
    }

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
        
        DatabaseService.executeQueryAsync(
                sql,
                new Object[]{orderNum},
                rs -> {
                    OrderDetailsRow row = readSingleOrderRow(rs);
                    Platform.runLater(() -> {
                        if (row == null) {
                            showError("ত্রুটি", "অর্ডার লোড করতে ব্যর্থ হয়েছে।");
                            return;
                        }
                        populateOrderDetails(row);
                    });
                },
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
        
        DatabaseService.executeQueryAsync(
                sql,
                new Object[]{orderId},
                rs -> {
                    OrderDetailsRow row = readSingleOrderRow(rs);
                    Platform.runLater(() -> {
                        if (row == null) {
                            showError("ত্রুটি", "অর্ডার লোড করতে ব্যর্থ হয়েছে।");
                            return;
                        }
                        populateOrderDetails(row);
                    });
                },
                err -> Platform.runLater(() -> {
                    showError("ত্রুটি", "অর্ডার লোড করতে ব্যর্থ হয়েছে।");
                    err.printStackTrace();
                })
        );
    }

    private OrderDetailsRow readSingleOrderRow(java.sql.ResultSet rs) {
        try {
            if (!rs.next()) return null;

            String createdAt = safeString(rs, "created_at");
            String buyerNameResolved = pickFirstNonBlank(
                    safeString(rs, "buyer_name_db"),
                    safeString(rs, "buyer_name")
            );
            String buyerPhoneResolved = pickFirstNonBlank(
                    safeString(rs, "buyer_phone_db"),
                    safeString(rs, "buyer_phone")
            );

            return new OrderDetailsRow(
                    rs.getInt("id"),
                    safeString(rs, "order_number"),
                    rs.getInt("crop_id"),
                    rs.getInt("farmer_id"),
                    rs.getInt("buyer_id"),
                    safeString(rs, "status"),
                    createdAt,
                    safeString(rs, "accepted_at"),
                    safeString(rs, "in_transit_at"),
                    safeString(rs, "completed_at"),
                    safeString(rs, "payment_status"),
                    rs.getDouble("total_amount"),
                    rs.getDouble("quantity_kg"),
                    rs.getDouble("price_per_kg"),
                    safeString(rs, "crop_name"),
                    safeString(rs, "product_code"),
                    safeString(rs, "category"),
                    safeString(rs, "crop_photo"),
                    safeString(rs, "farmer_name"),
                    safeString(rs, "farmer_phone"),
                    safeString(rs, "farmer_district"),
                    rs.getBoolean("farmer_verified"),
                    safeString(rs, "farmer_photo"),
                    buyerNameResolved,
                    buyerPhoneResolved,
                    safeString(rs, "delivery_address"),
                    safeString(rs, "delivery_district"),
                    safeString(rs, "delivery_upazila"),
                    safeString(rs, "notes"),
                    safeString(rs, "transport")
            );
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void populateOrderDetails(OrderDetailsRow row) {
        orderId = row.orderId;
        orderNumber = row.orderNumber;
        cropId = row.cropId;
        farmerId = row.farmerId;
        buyerId = row.buyerId;
        farmerPhone = row.farmerPhone;
        orderStatus = row.orderStatus;
        buyerName = row.buyerName;
        buyerPhone = row.buyerPhone;

        // Order info
        lblOrderNumber.setText(orderNumber != null && !orderNumber.isBlank() ? orderNumber : "N/A");
        lblOrderStatus.setText(getStatusDisplay(orderStatus));
        if (lblOrderId != null) {
            lblOrderId.setText(String.valueOf(orderId));
        }

        String createdAt = row.createdAt;
        lblOrderDate.setText(createdAt != null && createdAt.length() >= 10 ? createdAt.substring(0, 10) : createdAt);
        lblPaymentStatus.setText(getPaymentStatusDisplay(row.paymentStatus));
        lblTotalAmount.setText(String.format("৳ %.2f", row.totalAmount));

        // Status timeline
        if (lblStatusRequestedAt != null) {
            lblStatusRequestedAt.setText(formatTimestampForTimeline(createdAt));
        }
        if (lblStatusAcceptedAt != null) {
            lblStatusAcceptedAt.setText(formatTimestampForTimeline(row.acceptedAt));
        }
        if (lblStatusDeliveredAt != null) {
            lblStatusDeliveredAt.setText(formatTimestampForTimeline(row.inTransitAt));
        }
        if (lblStatusCompletedAt != null) {
            lblStatusCompletedAt.setText(formatTimestampForTimeline(row.completedAt));
        }

        // Crop info
        lblCropName.setText(row.cropName);
        lblProductCode.setText(row.productCode);
        if (lblCropId != null) {
            lblCropId.setText("Crop ID: " + cropId);
        }
        lblCropCategory.setText(row.category);
        lblOrderedQuantity.setText(String.format("%.1f কেজি", row.quantityKg));
        lblPricePerKg.setText(String.format("৳ %.2f", row.pricePerKg));

        // Load crop photo
        if (row.cropPhoto != null && !row.cropPhoto.isEmpty()) {
            File photoFile = new File(row.cropPhoto);
            if (photoFile.exists()) {
                imgCrop.setImage(new Image(photoFile.toURI().toString()));
            }
        }

        // Farmer info
        lblFarmerName.setText(row.farmerName);
        lblFarmerId.setText("ID: " + farmerId);
        lblFarmerPhone.setText("📱 " + (farmerPhone != null ? farmerPhone : ""));
        lblFarmerDistrict.setText("📍 " + row.farmerDistrict);
        if (lblFarmerVerified != null) {
            lblFarmerVerified.setVisible(row.farmerVerified);
        }

        // Load farmer photo
        if (row.farmerPhoto != null && !row.farmerPhoto.isEmpty()) {
            File photoFile = new File(row.farmerPhoto);
            if (photoFile.exists()) {
                imgFarmer.setImage(new Image(photoFile.toURI().toString()));
            }
        }

        // Buyer info
        lblBuyerName.setText(buyerName);
        lblBuyerId.setText(String.valueOf(buyerId));
        lblBuyerPhone.setText(buyerPhone != null ? buyerPhone : "");

        String delivery = String.join(", ",
                row.deliveryAddress != null ? row.deliveryAddress : "",
                row.deliveryDistrict != null ? row.deliveryDistrict : "",
                row.deliveryUpazila != null ? row.deliveryUpazila : ""
        ).replaceAll("(, )+$", "");
        lblDeliveryAddress.setText(delivery);

        // Transport & Notes
        lblTransport.setText(row.transport != null && !row.transport.isBlank() ? row.transport : "N/A");
        lblNotes.setText(row.notes != null && !row.notes.isBlank() ? row.notes : "কোনো নোট নেই");

        setupActionButtons();
        setupCounterpartyActions();
    }

    private void setupCounterpartyActions() {
        int currentUserId = currentUser != null ? currentUser.getId() : -1;
        boolean isSelfFarmer = currentUserId == farmerId;
        boolean isSelfBuyer = currentUserId == buyerId;

        // Farmer section: allow everyone to view profile; hide call/chat if it's the same user.
        if (btnCallFarmer != null) {
            btnCallFarmer.setVisible(!isSelfFarmer);
            btnCallFarmer.setManaged(!isSelfFarmer);
        }
        if (btnChatFarmer != null) {
            btnChatFarmer.setVisible(!isSelfFarmer);
            btnChatFarmer.setManaged(!isSelfFarmer);
        }
        if (btnViewFarmerProfile != null) {
            btnViewFarmerProfile.setVisible(true);
            btnViewFarmerProfile.setManaged(true);
        }

        // Buyer section: allow everyone to view profile; hide call/chat if it's the same user.
        if (btnCallBuyer != null) {
            btnCallBuyer.setVisible(!isSelfBuyer);
            btnCallBuyer.setManaged(!isSelfBuyer);
        }
        if (btnChatBuyer != null) {
            btnChatBuyer.setVisible(!isSelfBuyer);
            btnChatBuyer.setManaged(!isSelfBuyer);
        }
        if (btnViewBuyerProfile != null) {
            btnViewBuyerProfile.setVisible(true);
            btnViewBuyerProfile.setManaged(true);
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
        } else if ("buyer".equals(role) && buyerId == currentUser.getId()) {
            hboxBuyerActions.setVisible(true);
            hboxBuyerActions.setManaged(true);
            
            switch (orderStatus) {
                case "new":
                    btnCancelOrder.setVisible(true);
                    if (btnMarkReceived != null) btnMarkReceived.setVisible(false);
                    btnRateOrder.setVisible(false);
                    break;
                case "accepted":
                    btnCancelOrder.setVisible(true);
                    if (btnMarkReceived != null) btnMarkReceived.setVisible(false);
                    btnRateOrder.setVisible(false);
                    break;
                case "in_transit":
                    btnCancelOrder.setVisible(false);
                    if (btnMarkReceived != null) btnMarkReceived.setVisible(true);
                    btnRateOrder.setVisible(false);
                    break;
                case "completed":
                    btnCancelOrder.setVisible(false);
                    if (btnMarkReceived != null) btnMarkReceived.setVisible(false);
                    btnRateOrder.setVisible(true);
                    break;
                default:
                    btnCancelOrder.setVisible(false);
                    if (btnMarkReceived != null) btnMarkReceived.setVisible(false);
                    btnRateOrder.setVisible(false);
            }
        } else {
            if (hboxFarmerActions != null) {
                hboxFarmerActions.setVisible(false);
                hboxFarmerActions.setManaged(false);
            }
            if (hboxBuyerActions != null) {
                hboxBuyerActions.setVisible(false);
                hboxBuyerActions.setManaged(false);
            }
        }
    }

    private String getStatusDisplay(String status) {
        if (status == null) return "❓ অজানা";
        switch (status) {
            case "new": return "🆕 অনুরোধ করা হয়েছে";
            case "accepted": return "✅ গৃহীত";
            case "rejected": return "❌ প্রত্যাখ্যাত";
            case "in_transit": return "🚚 পথে আছে";
            case "delivered": return "📦 ডেলিভারি সম্পন্ন";
            case "completed": return "✅ গ্রহণ করা হয়েছে";
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

    private String pickFirstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return "";
    }

    private String formatTimestampForTimeline(String ts) {
        if (ts == null || ts.isBlank()) return "—";
        // Common SQLite format: yyyy-MM-dd HH:mm:ss
        if (ts.length() >= 16) return ts.substring(0, 16);
        return ts;
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
            content.putString(String.valueOf(orderId));
            clipboard.setContent(content);
            showInfo("কপি সফল", "অর্ডার নম্বর কপি হয়েছে: " + orderId);
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
    private void onCallBuyer() {
        if (buyerPhone != null && !buyerPhone.isEmpty()) {
            try {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().browse(new URI("tel:" + buyerPhone));
                }
            } catch (Exception e) {
                showInfo("ফোন নম্বর", "কল করুন: " + buyerPhone);
            }
        } else {
            showInfo("ফোন নম্বর", "ক্রেতার ফোন নম্বর পাওয়া যায়নি");
        }
    }

    @FXML
    private void onViewFarmerProfile() {
        App.setCurrentViewedUserId(farmerId);
        App.loadScene("public-farmer-profile-view.fxml", "কৃষকের প্রোফাইল");
    }

    @FXML
    private void onViewBuyerProfile() {
        App.setCurrentViewedUserId(buyerId);
        App.loadScene("public-buyer-profile-view.fxml", "ক্রেতার প্রোফাইল");
    }

    @FXML
    private void onChatBuyer() {
        try {
            if (currentUser != null && buyerId == currentUser.getId()) {
                showInfo("Not Allowed", "You cannot chat with yourself.");
                return;
            }

            String name = buyerName != null && !buyerName.isBlank() ? buyerName : ("User " + buyerId);
            App.setPreviousScene("order-detail-view.fxml");
            App.showView("chat-conversation-view.fxml", controller -> {
                if (controller instanceof ChatConversationController) {
                    ChatConversationController chatController = (ChatConversationController) controller;
                    chatController.loadConversation(0, buyerId, name, (cropId > 0 ? cropId : null));
                }
            });
        } catch (Exception e) {
            showError("ত্রুটি", "চ্যাট খুলতে ব্যর্থ হয়েছে।");
            e.printStackTrace();
        }
    }

    @FXML
    private void onChatFarmer() {
        try {
            if (currentUser != null && farmerId == currentUser.getId()) {
                showInfo("Not Allowed", "You cannot chat with yourself.");
                return;
            }

            String name = lblFarmerName != null ? lblFarmerName.getText() : ("User " + farmerId);
            App.setPreviousScene("order-detail-view.fxml");
            App.showView("chat-conversation-view.fxml", controller -> {
                if (controller instanceof ChatConversationController) {
                    ChatConversationController chatController = (ChatConversationController) controller;
                    chatController.loadConversation(0, farmerId, name, (cropId > 0 ? cropId : null));
                }
            });
        } catch (Exception e) {
            showError("ত্রুটি", "চ্যাট খুলতে ব্যর্থ হয়েছে।");
            e.printStackTrace();
        }
    }

    @FXML
    private void onAcceptOrder() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("নিশ্চিত করুন");
        confirm.setHeaderText("অর্ডার গ্রহণ করবেন?");
        confirm.setContentText("অর্ডার গ্রহণ করলে ফসলের পরিমাণ কমে যাবে।");
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                OrderService.acceptOrderAsync(
                    orderId,
                    currentUser.getId(),
                    r -> {
                        if (r.ok) {
                            showInfo("সফল", r.message);
                            FirebaseSyncService.getInstance().syncOrderStatusToFirebase(orderId, "accepted", null);
                            loadOrderDetails();
                        } else {
                            showError("ত্রুটি", r.message);
                        }
                    },
                    err -> {
                        showError("ত্রুটি", "অর্ডার গ্রহণ করতে সমস্যা হয়েছে।");
                        err.printStackTrace();
                    }
                );
            }
        });
    }

    @FXML
    private void onRejectOrder() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("নিশ্চিত করুন");
        confirm.setHeaderText("আপনি কি এই অর্ডারটি প্রত্যাখ্যান করতে চান?");
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                OrderService.rejectOrderAsync(
                    orderId,
                    currentUser.getId(),
                    r -> {
                        if (r.ok) {
                            showInfo("সফল", r.message);
                            FirebaseSyncService.getInstance().syncOrderStatusToFirebase(orderId, "rejected", null);
                            loadOrderDetails();
                        } else {
                            showError("ত্রুটি", r.message);
                        }
                    },
                    err -> {
                        showError("ত্রুটি", "স্ট্যাটাস আপডেট করতে ব্যর্থ হয়েছে।");
                        err.printStackTrace();
                    }
                );
            }
        });
    }

    @FXML
    private void onMarkDelivered() {
        OrderService.markInTransitAsync(
            orderId,
            currentUser.getId(),
            r -> {
                if (r.ok) {
                    showInfo("সফল", r.message);
                    FirebaseSyncService.getInstance().syncOrderStatusToFirebase(orderId, "in_transit", null);
                    loadOrderDetails();
                } else {
                    showError("ত্রুটি", r.message);
                }
            },
            err -> {
                showError("ত্রুটি", "স্ট্যাটাস আপডেট করতে ব্যর্থ হয়েছে।");
                err.printStackTrace();
            }
        );
    }

    @FXML
    private void onMarkReceived() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("নিশ্চিত করুন");
        confirm.setHeaderText("পণ্য গ্রহণ নিশ্চিত করবেন?");
        confirm.setContentText("আপনি কি নিশ্চিত যে আপনি এই পণ্য পেয়েছেন?");
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                OrderService.markReceivedAsync(
                    orderId,
                    currentUser.getId(),
                    r -> {
                        if (r.ok) {
                            showInfo("সফল", r.message);
                            FirebaseSyncService.getInstance().syncOrderStatusToFirebase(orderId, "completed", null);
                            StatisticsCalculator.updateBuyerStatistics(currentUser.getId());
                            updateFarmerStats(orderId);
                            loadOrderDetails();
                        } else {
                            showError("ত্রুটি", r.message);
                        }
                    },
                    err -> {
                        showError("ত্রুটি", "স্ট্যাটাস আপডেট করতে ব্যর্থ হয়েছে।");
                        err.printStackTrace();
                    }
                );
            }
        });
    }

    private void updateFarmerStats(int orderId) {
        DatabaseService.executeQueryAsync(
            "SELECT farmer_id FROM orders WHERE id = ?",
            new Object[]{orderId},
            rs -> {
                try {
                    if (rs.next()) {
                        StatisticsCalculator.updateFarmerStatistics(rs.getInt("farmer_id"));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            },
            error -> error.printStackTrace()
        );
    }

    @FXML
    private void onCancelOrder() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("নিশ্চিত করুন");
        confirm.setHeaderText("আপনি কি এই অর্ডারটি বাতিল করতে চান?");
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                OrderService.cancelOrderAsync(
                    orderId,
                    currentUser.getId(),
                    r -> {
                        if (r.ok) {
                            showInfo("সফল", r.message);
                            FirebaseSyncService.getInstance().syncOrderStatusToFirebase(orderId, "cancelled", null);
                            loadOrderDetails();
                        } else {
                            showError("ত্রুটি", r.message);
                        }
                    },
                    err -> {
                        showError("ত্রুটি", "অর্ডার বাতিল করতে ব্যর্থ হয়েছে।");
                        err.printStackTrace();
                    }
                );
            }
        });
    }

    @FXML
    private void onRateOrder() {
        if (buyerId != currentUser.getId()) {
            showError("অ্যাক্সেস অস্বীকার", "শুধুমাত্র এই অর্ডারের ক্রেতা রেটিং দিতে পারবেন।");
            return;
        }
        if (!"completed".equals(orderStatus)) {
            showInfo("রেটিং", "শুধুমাত্র সম্পন্ন (completed) অর্ডারের জন্য রেটিং দেওয়া যাবে।");
            return;
        }

        openRatingDialog(orderId, farmerId, orderNumber,
            lblFarmerName != null ? lblFarmerName.getText() : ("User " + farmerId),
            lblCropName != null ? lblCropName.getText() : "");
    }

    private void openRatingDialog(int orderId, int farmerId, String orderNumber, String farmerName, String cropName) {
        try {
            FXMLLoader loader = new FXMLLoader(App.class.getResource("rate-order-dialog.fxml"));
            Scene scene = new Scene(loader.load());
            scene.getStylesheets().add(App.class.getResource("styles.css").toExternalForm());
            scene.getStylesheets().add(App.class.getResource("base.css").toExternalForm());
            scene.getStylesheets().add(App.class.getResource("components.css").toExternalForm());
            scene.getStylesheets().add(App.class.getResource("dashboard.css").toExternalForm());

            Stage dialog = new Stage();
            dialog.initOwner(App.getPrimaryStage());
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.setTitle("রেটিং দিন");
            dialog.setScene(scene);
            dialog.setResizable(false);

            Object controller = loader.getController();
            if (controller instanceof RateOrderDialogController) {
                RateOrderDialogController c = (RateOrderDialogController) controller;
                c.setDialogStage(dialog);
                c.setOrderDetails(orderId, farmerId, orderNumber, farmerName, cropName);
            }

            dialog.showAndWait();
            loadOrderDetails();
        } catch (Exception e) {
            e.printStackTrace();
            showError("ত্রুটি", "রেটিং ডায়ালগ খুলতে ব্যর্থ হয়েছে।");
        }
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
