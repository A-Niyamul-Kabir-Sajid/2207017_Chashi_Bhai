package com.sajid._207017_chashi_bhai.controllers;

import com.sajid._207017_chashi_bhai.App;
import com.sajid._207017_chashi_bhai.models.User;
import com.sajid._207017_chashi_bhai.services.DatabaseService;
import com.sajid._207017_chashi_bhai.utils.DataSyncManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.util.Optional;

/**
 * BuyerOrdersController - Track buyer's active orders
 * Features real-time sync with database polling
 */
public class BuyerOrdersController {

    @FXML private Button btnFilterAll;
    @FXML private Button btnFilterPending;
    @FXML private Button btnFilterConfirmed;
    @FXML private Button btnFilterInTransit;
    @FXML private Button btnFilterDelivered;
    @FXML private VBox vboxOrdersList;
    @FXML private VBox vboxEmptyState;
    @FXML private Button btnRefresh;
    @FXML private ProgressIndicator progressIndicator;

    private User currentUser;
    private String currentFilter = "all";
    private DataSyncManager syncManager;

    @FXML
    public void initialize() {
        currentUser = App.getCurrentUser();
        syncManager = DataSyncManager.getInstance();
        
        if (currentUser == null || !"buyer".equals(currentUser.getRole())) {
            showError("অ্যাক্সেস অস্বীকার", "শুধুমাত্র ক্রেতারা এই পেজ দেখতে পারবেন।");
            App.loadScene("login-view.fxml", "Login");
            return;
        }
        
        setActiveFilter(btnFilterAll);
        currentFilter = "all";
        
        loadOrders(currentFilter);
        
        // Start real-time sync polling for orders (every 15 seconds)
        syncManager.startOrdersSync(currentUser.getId(), () -> loadOrders(currentFilter));
    }

    @FXML
    private void onFilterAll() {
        setActiveFilter(btnFilterAll);
        currentFilter = "all";
        loadOrders(currentFilter);
    }

    @FXML
    private void onFilterPending() {
        setActiveFilter(btnFilterPending);
        currentFilter = "pending";
        loadOrders(currentFilter);
    }

    @FXML
    private void onFilterConfirmed() {
        setActiveFilter(btnFilterConfirmed);
        currentFilter = "accepted";
        loadOrders(currentFilter);
    }

    @FXML
    private void onFilterInTransit() {
        setActiveFilter(btnFilterInTransit);
        currentFilter = "in_transit";
        loadOrders(currentFilter);
    }

    @FXML
    private void onFilterDelivered() {
        setActiveFilter(btnFilterDelivered);
        currentFilter = "delivered";
        loadOrders(currentFilter);
    }

    private void setActiveFilter(Button activeButton) {
        btnFilterAll.getStyleClass().remove("filter-active");
        btnFilterPending.getStyleClass().remove("filter-active");
        btnFilterConfirmed.getStyleClass().remove("filter-active");
        btnFilterInTransit.getStyleClass().remove("filter-active");
        btnFilterDelivered.getStyleClass().remove("filter-active");
        
        activeButton.getStyleClass().add("filter-active");
    }

    private void loadOrders(String filter) {
        if (progressIndicator != null) {
            progressIndicator.setVisible(true);
        }
        vboxOrdersList.getChildren().clear();

        String query = "SELECT o.*, c.name as crop_name, c.price_per_kg as price, " +
                      "u.name as farmer_name, u.phone as farmer_phone, u.is_verified, " +
                      "(SELECT photo_path FROM crop_photos WHERE crop_id = c.id ORDER BY photo_order LIMIT 1) as crop_photo " +
                      "FROM orders o " +
                      "JOIN crops c ON o.crop_id = c.id " +
                      "JOIN users u ON c.farmer_id = u.id " +
                      "WHERE o.buyer_id = ?";
        
        if (!"all".equals(filter)) {
            query += " AND o.status = ?";
        }
        query += " ORDER BY o.created_at DESC";

        Object[] params = "all".equals(filter) ? 
            new Object[]{currentUser.getId()} : 
            new Object[]{currentUser.getId(), filter};

        DatabaseService.executeQueryAsync(
            query,
            params,
            resultSet -> {
                Platform.runLater(() -> {
                    try {
                        boolean hasResults = false;
                        while (resultSet.next()) {
                            hasResults = true;
                            VBox orderCard = createOrderCardFromResultSet(resultSet);
                            vboxOrdersList.getChildren().add(orderCard);
                        }

                        vboxEmptyState.setVisible(!hasResults);
                        vboxOrdersList.setVisible(hasResults);
                    } catch (Exception e) {
                        e.printStackTrace();
                        showError("ত্রুটি", "অর্ডার লোড করতে ব্যর্থ হয়েছে।");
                    } finally {
                        if (progressIndicator != null) {
                            progressIndicator.setVisible(false);
                        }
                    }
                });
            },
            error -> {
                Platform.runLater(() -> {
                    if (progressIndicator != null) {
                        progressIndicator.setVisible(false);
                    }
                    showError("ডাটাবেস ত্রুটি", "অর্ডার লোড করতে সমস্যা হয়েছে।");
                    error.printStackTrace();
                });
            }
        );
    }

    private VBox createDummyOrderCard(int orderId, String cropName, String farmerName,
                                      String farmerPhone, boolean isVerified, double quantity,
                                      double price, String unit, String status, 
                                      String paymentStatus, String createdAt) {
        VBox card = new VBox(15);
        card.getStyleClass().addAll("buyer-order-card", "order-" + status.replace("_", "-"));
        card.setPadding(new Insets(15));

        HBox mainBox = new HBox(15);

        // Crop image placeholder
        ImageView imageView = new ImageView();
        imageView.setFitWidth(100);
        imageView.setFitHeight(100);
        imageView.setPreserveRatio(true);
        imageView.setStyle("-fx-background-color: #e0e0e0;");

        // Order details
        VBox detailsBox = new VBox(8);
        detailsBox.setPrefWidth(400);
        
        Label lblOrderId = new Label("অর্ডার #" + orderId);
        lblOrderId.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #888;");
        
        Label lblCrop = new Label("🌾 " + cropName);
        lblCrop.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        
        HBox farmerBox = new HBox(8);
        Label lblFarmer = new Label("কৃষক: " + farmerName);
        lblFarmer.setStyle("-fx-font-size: 14px;");
        if (isVerified) {
            Label lblVerified = new Label("✓");
            lblVerified.setStyle("-fx-text-fill: #4CAF50; -fx-font-weight: bold;");
            farmerBox.getChildren().addAll(lblFarmer, lblVerified);
        } else {
            farmerBox.getChildren().add(lblFarmer);
        }
        
        Label lblQuantity = new Label(String.format("পরিমাণ: %.1f %s", quantity, unit));
        lblQuantity.setStyle("-fx-font-size: 14px;");
        
        double totalPrice = quantity * price;
        Label lblPrice = new Label(String.format("মোট: ৳%.2f", totalPrice));
        lblPrice.setStyle("-fx-font-size: 16px; -fx-text-fill: #4CAF50; -fx-font-weight: bold;");
        
        Label lblDate = new Label("অর্ডারের তারিখ: " + createdAt);
        lblDate.setStyle("-fx-font-size: 12px; -fx-text-fill: #888;");
        
        HBox statusBox = new HBox(10);
        Label lblStatus = new Label(getStatusText(status));
        lblStatus.getStyleClass().add("status-badge");
        
        Label lblPayment = new Label(getPaymentStatusText(paymentStatus));
        lblPayment.getStyleClass().add(paymentStatus != null && paymentStatus.equals("completed") ? "payment-complete" : "payment-pending");
        
        statusBox.getChildren().addAll(lblStatus, lblPayment);

        detailsBox.getChildren().addAll(lblOrderId, lblCrop, farmerBox, lblQuantity, lblPrice, lblDate, statusBox);

        // Action buttons
        VBox actionsBox = new VBox(10);
        actionsBox.setPrefWidth(180);
        actionsBox.getChildren().addAll(getActionButtons(orderId, status, farmerPhone));

        mainBox.getChildren().addAll(imageView, detailsBox, actionsBox);

        // Progress bar for in-transit orders
        if ("in_transit".equals(status)) {
            ProgressBar progressBar = new ProgressBar(0.75);
            progressBar.setMaxWidth(Double.MAX_VALUE);
            Label lblProgress = new Label("🚚 ডেলিভারি চলছে...");
            lblProgress.setStyle("-fx-font-size: 12px; -fx-text-fill: #FF9800;");
            card.getChildren().addAll(mainBox, progressBar, lblProgress);
        } else {
            card.getChildren().add(mainBox);
        }

        return card;
    }

    private VBox createOrderCardFromResultSet(java.sql.ResultSet rs) throws Exception {
        int orderId = rs.getInt("id");
        String cropName = rs.getString("crop_name");
        String farmerName = rs.getString("farmer_name");
        String farmerPhone = rs.getString("farmer_phone");
        boolean isVerified = rs.getBoolean("is_verified");
        double quantity = rs.getDouble("quantity_kg");
        double price = rs.getDouble("price");
        String unit = "কেজি";
        String status = rs.getString("status");
        String paymentStatus = rs.getString("payment_status");
        String createdAt = rs.getString("created_at");
        String photoPath = rs.getString("crop_photo");

        VBox card = new VBox(15);
        card.getStyleClass().addAll("buyer-order-card", "order-" + status.replace("_", "-"));
        card.setPadding(new Insets(15));

        HBox mainBox = new HBox(15);

        // Crop image
        ImageView imageView = new ImageView();
        imageView.setFitWidth(100);
        imageView.setFitHeight(100);
        imageView.setPreserveRatio(true);
        if (photoPath != null && !photoPath.isEmpty()) {
            File photoFile = new File(photoPath);
            if (photoFile.exists()) {
                imageView.setImage(new Image(photoFile.toURI().toString()));
            }
        }

        // Order details
        VBox detailsBox = new VBox(8);
        detailsBox.setPrefWidth(400);
        
        Label lblOrderId = new Label("অর্ডার #" + orderId);
        lblOrderId.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #888;");
        
        Label lblCrop = new Label("🌾 " + cropName);
        lblCrop.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        
        HBox farmerBox = new HBox(8);
        Label lblFarmer = new Label("কৃষক: " + farmerName);
        lblFarmer.setStyle("-fx-font-size: 14px;");
        if (isVerified) {
            Label lblVerified = new Label("✓");
            lblVerified.setStyle("-fx-text-fill: #4CAF50; -fx-font-weight: bold;");
            farmerBox.getChildren().addAll(lblFarmer, lblVerified);
        } else {
            farmerBox.getChildren().add(lblFarmer);
        }
        
        Label lblQuantity = new Label(String.format("পরিমাণ: %.1f %s", quantity, unit));
        lblQuantity.setStyle("-fx-font-size: 14px;");
        
        double totalPrice = quantity * price;
        Label lblPrice = new Label(String.format("মোট: ৳%.2f", totalPrice));
        lblPrice.setStyle("-fx-font-size: 16px; -fx-text-fill: #4CAF50; -fx-font-weight: bold;");
        
        Label lblDate = new Label("অর্ডারের তারিখ: " + createdAt.substring(0, 10));
        lblDate.setStyle("-fx-font-size: 12px; -fx-text-fill: #888;");
        
        HBox statusBox = new HBox(10);
        Label lblStatus = new Label(getStatusText(status));
        lblStatus.getStyleClass().add("status-badge");
        
        Label lblPayment = new Label(getPaymentStatusText(paymentStatus));
        lblPayment.getStyleClass().add(paymentStatus != null && paymentStatus.equals("completed") ? "payment-complete" : "payment-pending");
        
        statusBox.getChildren().addAll(lblStatus, lblPayment);

        detailsBox.getChildren().addAll(lblOrderId, lblCrop, farmerBox, lblQuantity, lblPrice, lblDate, statusBox);

        // Action buttons
        VBox actionsBox = new VBox(10);
        actionsBox.setPrefWidth(180);
        actionsBox.getChildren().addAll(getActionButtons(orderId, status, farmerPhone));

        mainBox.getChildren().addAll(imageView, detailsBox, actionsBox);

        // Progress bar for in-transit orders
        if ("in_transit".equals(status)) {
            ProgressBar progressBar = new ProgressBar(0.75);
            progressBar.setMaxWidth(Double.MAX_VALUE);
            Label lblProgress = new Label("🚚 ডেলিভারি চলছে...");
            lblProgress.setStyle("-fx-font-size: 12px; -fx-text-fill: #FF9800;");
            card.getChildren().addAll(mainBox, progressBar, lblProgress);
        } else {
            card.getChildren().add(mainBox);
        }

        return card;
    }

    private VBox getActionButtons(int orderId, String status, String farmerPhone) {
        VBox actionsBox = new VBox(10);
        
        switch (status) {
            case "new":
            case "pending":
                Button btnPay = new Button("💳 পেমেন্ট করুন");
                btnPay.getStyleClass().add("button-success");
                btnPay.setMaxWidth(Double.MAX_VALUE);
                btnPay.setOnAction(e -> showInfo("পেমেন্ট", "পেমেন্ট গেটওয়ে শীঘ্রই আসছে..."));
                
                Button btnContact = new Button("📞 যোগাযোগ");
                btnContact.getStyleClass().add("button-info");
                btnContact.setMaxWidth(Double.MAX_VALUE);
                btnContact.setOnAction(e -> contactFarmer(farmerPhone));
                
                Button btnCancel = new Button("✗ বাতিল");
                btnCancel.getStyleClass().add("button-danger");
                btnCancel.setMaxWidth(Double.MAX_VALUE);
                btnCancel.setOnAction(e -> cancelOrder(orderId));
                
                actionsBox.getChildren().addAll(btnPay, btnContact, btnCancel);
                break;
                
            case "in_transit":
                Button btnConfirm = new Button("✓ ডেলিভারি নিশ্চিত");
                btnConfirm.getStyleClass().add("button-success");
                btnConfirm.setMaxWidth(Double.MAX_VALUE);
                btnConfirm.setOnAction(e -> updateOrderStatus(orderId, "delivered"));
                
                Button btnContactTransit = new Button("📞 যোগাযোগ");
                btnContactTransit.getStyleClass().add("button-info");
                btnContactTransit.setMaxWidth(Double.MAX_VALUE);
                btnContactTransit.setOnAction(e -> contactFarmer(farmerPhone));
                
                Button btnTrack = new Button("📍 ট্র্যাক করুন");
                btnTrack.getStyleClass().add("button-secondary");
                btnTrack.setMaxWidth(Double.MAX_VALUE);
                btnTrack.setOnAction(e -> showInfo("ট্র্যাকিং", "ট্র্যাকিং সিস্টেম শীঘ্রই আসছে..."));
                
                actionsBox.getChildren().addAll(btnConfirm, btnContactTransit, btnTrack);
                break;
                
            case "delivered":
                Button btnRate = new Button("⭐ রেটিং দিন");
                btnRate.getStyleClass().add("button-success");
                btnRate.setMaxWidth(Double.MAX_VALUE);
                btnRate.setOnAction(e -> showRatingDialog(orderId));
                
                Button btnContactDelivered = new Button("📞 যোগাযোগ");
                btnContactDelivered.getStyleClass().add("button-info");
                btnContactDelivered.setMaxWidth(Double.MAX_VALUE);
                btnContactDelivered.setOnAction(e -> contactFarmer(farmerPhone));
                
                Button btnReorder = new Button("🔁 পুনরায় অর্ডার");
                btnReorder.getStyleClass().add("button-secondary");
                btnReorder.setMaxWidth(Double.MAX_VALUE);
                btnReorder.setOnAction(e -> reorder(orderId));
                
                actionsBox.getChildren().addAll(btnRate, btnContactDelivered, btnReorder);
                break;
                
            default:
                Button btnView = new Button("👁 দেখুন");
                btnView.getStyleClass().add("button-secondary");
                btnView.setMaxWidth(Double.MAX_VALUE);
                btnView.setOnAction(e -> showOrderDetails(orderId));
                
                actionsBox.getChildren().add(btnView);
        }
        
        return actionsBox;
    }

    private String getStatusText(String status) {
        switch (status) {
            case "new":
            case "pending": return "⏳ পেন্ডিং";
            case "accepted": return "✓ গৃহীত";
            case "in_transit": return "🚚 পাঠানো হচ্ছে";
            case "delivered": return "✓ ডেলিভার হয়েছে";
            case "cancelled": return "✗ বাতিল";
            case "completed": return "✓ সম্পূর্ণ";
            default: return status;
        }
    }

    private String getPaymentStatusText(String paymentStatus) {
        if (paymentStatus == null || "pending".equals(paymentStatus)) {
            return "💳 পেমেন্ট পেন্ডিং";
        } else if ("completed".equals(paymentStatus)) {
            return "✓ পেমেন্ট সম্পূর্ণ";
        }
        return paymentStatus;
    }

    private void updateOrderStatus(int orderId, String newStatus) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("নিশ্চিত করুন");
        confirm.setHeaderText("ডেলিভারি নিশ্চিত করবেন?");
        confirm.setContentText("আপনি কি নিশ্চিত যে এই অর্ডার ডেলিভার হয়েছে?");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            DatabaseService.executeUpdateAsync(
                "UPDATE orders SET status = ?, updated_at = datetime('now') WHERE id = ?",
                new Object[]{newStatus, orderId},
                rowsAffected -> {
                    Platform.runLater(() -> {
                        if (rowsAffected > 0) {
                            showSuccess("সফল", "অর্ডার স্ট্যাটাস আপডেট করা হয়েছে।");
                            loadOrders(currentFilter);
                        }
                    });
                },
                error -> {
                    Platform.runLater(() -> {
                        showError("ত্রুটি", "স্ট্যাটাস আপডেট করতে সমস্যা হয়েছে।");
                        error.printStackTrace();
                    });
                }
            );
        }
    }

    private void cancelOrder(int orderId) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("নিশ্চিত করুন");
        confirm.setHeaderText("অর্ডার বাতিল করবেন?");
        confirm.setContentText("আপনি কি নিশ্চিত যে এই অর্ডার বাতিল করতে চান?");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            updateOrderStatus(orderId, "cancelled");
        }
    }

    private void showRatingDialog(int orderId) {
        showInfo("রেটিং", "রেটিং সিস্টেম শীঘ্রই আসছে...");
    }

    private void reorder(int orderId) {
        showInfo("পুনরায় অর্ডার", "পুনরায় অর্ডার বৈশিষ্ট্য শীঘ্রই আসছে...");
    }

    private void contactFarmer(String phone) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("যোগাযোগ করুন");
        alert.setHeaderText("কৃষকের সাথে যোগাযোগ করুন");
        alert.setContentText("Phone: " + phone);
        
        ButtonType callButton = new ButtonType("📞 কল করুন");
        ButtonType whatsappButton = new ButtonType("💬 WhatsApp");
        ButtonType cancelButton = new ButtonType("বাতিল", ButtonBar.ButtonData.CANCEL_CLOSE);
        
        alert.getButtonTypes().setAll(callButton, whatsappButton, cancelButton);
        
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent()) {
            if (result.get() == callButton) {
                try {
                    Desktop.getDesktop().browse(new URI("tel:" + phone));
                } catch (Exception e) {
                    showInfo("Phone", "ফোন নম্বর: " + phone);
                }
            } else if (result.get() == whatsappButton) {
                try {
                    String cleanPhone = phone.replaceAll("[^0-9]", "");
                    Desktop.getDesktop().browse(new URI("https://wa.me/" + cleanPhone));
                } catch (Exception e) {
                    showInfo("WhatsApp", "WhatsApp: " + phone);
                }
            }
        }
    }

    private void showOrderDetails(int orderId) {
        showInfo("অর্ডার বিস্তারিত", "অর্ডার #" + orderId);
    }

    @FXML
    private void onRefresh() {
        loadOrders(currentFilter);
    }

    @FXML
    private void onBack() {
        // Stop polling when leaving the view
        if (syncManager != null && currentUser != null) {
            syncManager.stopPolling("orders_" + currentUser.getId());
        }
        App.loadScene("buyer-dashboard-view.fxml", "Dashboard");
    }

    @FXML
    private void onBrowseCrops() {
        App.loadScene("crop-feed-view.fxml", "সকল ফসল");
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showSuccess(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
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
