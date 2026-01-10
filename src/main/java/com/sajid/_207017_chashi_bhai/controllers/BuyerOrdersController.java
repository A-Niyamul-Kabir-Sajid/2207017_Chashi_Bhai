package com.sajid._207017_chashi_bhai.controllers;

import com.sajid._207017_chashi_bhai.App;
import com.sajid._207017_chashi_bhai.models.User;
import com.sajid._207017_chashi_bhai.services.DatabaseService;
import com.sajid._207017_chashi_bhai.services.FirebaseSyncService;
import com.sajid._207017_chashi_bhai.utils.DataSyncManager;
import com.sajid._207017_chashi_bhai.utils.StatisticsCalculator;
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
import java.util.ArrayList;
import java.util.List;
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
    @FXML private ComboBox<String> cbSortBy;
    @FXML private VBox vboxOrdersList;
    @FXML private VBox vboxEmptyState;
    @FXML private Button btnRefresh;
    @FXML private ProgressIndicator progressIndicator;

    private User currentUser;
    private String currentFilter = "all";
    private DataSyncManager syncManager;

    private static class OrderRow {
        final int orderId;
        final String cropName;
        final String farmerName;
        final String farmerPhone;
        final boolean isVerified;
        final double quantity;
        final double price;
        final String status;
        final String paymentStatus;
        final String createdAt;
        final String photoPath;

        private OrderRow(int orderId, String cropName, String farmerName, String farmerPhone,
                         boolean isVerified, double quantity, double price, String status,
                         String paymentStatus, String createdAt, String photoPath) {
            this.orderId = orderId;
            this.cropName = cropName;
            this.farmerName = farmerName;
            this.farmerPhone = farmerPhone;
            this.isVerified = isVerified;
            this.quantity = quantity;
            this.price = price;
            this.status = status;
            this.paymentStatus = paymentStatus;
            this.createdAt = createdAt;
            this.photoPath = photoPath;
        }
    }

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
        
        // Initialize sort dropdown with default selection
        if (cbSortBy != null) {
            cbSortBy.getSelectionModel().select(0); // Default: Newest First
            cbSortBy.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null) {
                    loadOrders(currentFilter);
                }
            });
        }
        
        loadOrders(currentFilter);
        
        // Start real-time sync polling for orders (every 15 seconds)
        syncManager.startOrdersSync(currentUser.getId(), this::refreshOrders);
    }

    private void refreshOrders() {
        FirebaseSyncService.getInstance().syncBuyerOrdersFromFirebase(
            currentUser.getId(),
            () -> loadOrders(currentFilter)
        );
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
        currentFilter = "new";
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
        currentFilter = "completed";
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
            if ("completed".equals(filter)) {
                query += " AND o.status IN ('completed','delivered')";
            } else {
                query += " AND o.status = ?";
            }
        }
        
        // Apply sorting based on user selection
        String sortOption = cbSortBy != null ? cbSortBy.getSelectionModel().getSelectedItem() : null;
        if (sortOption != null) {
            if (sortOption.contains("High to Low") || sortOption.contains("বেশি থেকে কম")) {
                query += " ORDER BY (o.quantity_kg * c.price_per_kg) DESC";
            } else if (sortOption.contains("Low to High") || sortOption.contains("কম থেকে বেশি")) {
                query += " ORDER BY (o.quantity_kg * c.price_per_kg) ASC";
            } else {
                // Default: Newest First
                query += " ORDER BY o.created_at DESC";
            }
        } else {
            query += " ORDER BY o.created_at DESC";
        }

        Object[] params = "all".equals(filter) || "completed".equals(filter) ?
            new Object[]{currentUser.getId()} :
            new Object[]{currentUser.getId(), filter};

        DatabaseService.executeQueryAsync(
                query,
                params,
                resultSet -> {
                    // IMPORTANT: Read ResultSet on DB thread (connection closes after callback).
                    List<OrderRow> rows = new ArrayList<>();
                    try {
                        while (resultSet.next()) {
                            rows.add(new OrderRow(
                                    resultSet.getInt("id"),
                                    resultSet.getString("crop_name"),
                                    resultSet.getString("farmer_name"),
                                    resultSet.getString("farmer_phone"),
                                    resultSet.getBoolean("is_verified"),
                                    resultSet.getDouble("quantity_kg"),
                                    resultSet.getDouble("price"),
                                    resultSet.getString("status"),
                                    resultSet.getString("payment_status"),
                                    resultSet.getString("created_at"),
                                    resultSet.getString("crop_photo")
                            ));
                        }
                    } catch (Exception e) {
                        Platform.runLater(() -> {
                            if (progressIndicator != null) {
                                progressIndicator.setVisible(false);
                            }
                            e.printStackTrace();
                            showError("ত্রুটি", "অর্ডার লোড করতে ব্যর্থ হয়েছে।");
                        });
                        return;
                    }

                    Platform.runLater(() -> {
                        try {
                            boolean hasResults = !rows.isEmpty();
                            for (OrderRow row : rows) {
                                VBox orderCard = createOrderCardFromRow(row);
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
                error -> Platform.runLater(() -> {
                    if (progressIndicator != null) {
                        progressIndicator.setVisible(false);
                    }
                    showError("ডাটাবেস ত্রুটি", "অর্ডার লোড করতে সমস্যা হয়েছে।");
                    error.printStackTrace();
                })
        );
    }

    private VBox createOrderCardFromRow(OrderRow row) throws Exception {
        VBox card = new VBox(15);
        String safeStatus = row.status != null ? row.status : "new";
        card.getStyleClass().addAll("buyer-order-card", "order-" + safeStatus.replace("_", "-"));
        card.setPadding(new Insets(15));

        HBox mainBox = new HBox(15);

        // Crop image
        ImageView imageView = new ImageView();
        imageView.setFitWidth(100);
        imageView.setFitHeight(100);
        imageView.setPreserveRatio(true);
        if (row.photoPath != null && !row.photoPath.isEmpty()) {
            File photoFile = new File(row.photoPath);
            if (photoFile.exists()) {
                imageView.setImage(new Image(photoFile.toURI().toString()));
            }
        }

        // Order details
        VBox detailsBox = new VBox(8);
        detailsBox.setPrefWidth(400);

        Label lblOrderId = new Label("অর্ডার #" + row.orderId);
        lblOrderId.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #888;");

        Label lblCrop = new Label("🌾 " + (row.cropName != null ? row.cropName : ""));
        lblCrop.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        HBox farmerBox = new HBox(8);
        Label lblFarmer = new Label("কৃষক: " + (row.farmerName != null ? row.farmerName : ""));
        lblFarmer.setStyle("-fx-font-size: 14px;");
        if (row.isVerified) {
            Label lblVerified = new Label("✓");
            lblVerified.setStyle("-fx-text-fill: #4CAF50; -fx-font-weight: bold;");
            farmerBox.getChildren().addAll(lblFarmer, lblVerified);
        } else {
            farmerBox.getChildren().add(lblFarmer);
        }

        Label lblQuantity = new Label(String.format("পরিমাণ: %.1f %s", row.quantity, "কেজি"));
        lblQuantity.setStyle("-fx-font-size: 14px;");

        double totalPrice = row.quantity * row.price;
        Label lblPrice = new Label(String.format("মোট: ৳%.2f", totalPrice));
        lblPrice.setStyle("-fx-font-size: 16px; -fx-text-fill: #4CAF50; -fx-font-weight: bold;");

        String dateText = row.createdAt;
        if (dateText != null && dateText.length() >= 10) {
            dateText = dateText.substring(0, 10);
        }
        Label lblDate = new Label("অর্ডারের তারিখ: " + (dateText != null ? dateText : "—"));
        lblDate.setStyle("-fx-font-size: 12px; -fx-text-fill: #888;");

        HBox statusBox = new HBox(10);
        Label lblStatus = new Label(getStatusText(safeStatus));
        lblStatus.getStyleClass().add("status-badge");

        Label lblPayment = new Label(getPaymentStatusText(row.paymentStatus));
        lblPayment.getStyleClass().add(row.paymentStatus != null && row.paymentStatus.equals("paid") ? "payment-complete" : "payment-pending");

        statusBox.getChildren().addAll(lblStatus, lblPayment);

        detailsBox.getChildren().addAll(lblOrderId, lblCrop, farmerBox, lblQuantity, lblPrice, lblDate, statusBox);

        // Action buttons
        VBox actionsBox = new VBox(10);
        actionsBox.setPrefWidth(180);
        actionsBox.getChildren().addAll(getActionButtons(row.orderId, safeStatus, row.farmerPhone));

        mainBox.getChildren().addAll(imageView, detailsBox, actionsBox);

        // Progress bar for in-transit orders
        if ("in_transit".equals(safeStatus)) {
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

        Button btnDetails = new Button("📄 বিস্তারিত");
        btnDetails.getStyleClass().add("button-secondary");
        btnDetails.setMaxWidth(Double.MAX_VALUE);
        btnDetails.setOnAction(e -> showOrderDetails(orderId));
        
        switch (status) {
            case "new":
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
                
                actionsBox.getChildren().addAll(btnPay, btnContact, btnCancel, btnDetails);
                break;

            case "accepted":
                Button btnContactAccepted = new Button("📞 যোগাযোগ");
                btnContactAccepted.getStyleClass().add("button-info");
                btnContactAccepted.setMaxWidth(Double.MAX_VALUE);
                btnContactAccepted.setOnAction(e -> contactFarmer(farmerPhone));

                actionsBox.getChildren().addAll(btnContactAccepted, btnDetails);
                break;
                
            case "in_transit":
                Button btnConfirm = new Button("✅ পণ্য পেয়েছি");
                btnConfirm.getStyleClass().add("button-success");
                btnConfirm.setMaxWidth(Double.MAX_VALUE);
                btnConfirm.setOnAction(e -> updateOrderStatus(orderId, "completed"));
                
                Button btnContactTransit = new Button("📞 যোগাযোগ");
                btnContactTransit.getStyleClass().add("button-info");
                btnContactTransit.setMaxWidth(Double.MAX_VALUE);
                btnContactTransit.setOnAction(e -> contactFarmer(farmerPhone));
                
                Button btnTrack = new Button("📍 ট্র্যাক করুন");
                btnTrack.getStyleClass().add("button-secondary");
                btnTrack.setMaxWidth(Double.MAX_VALUE);
                btnTrack.setOnAction(e -> showInfo("ট্র্যাকিং", "ট্র্যাকিং সিস্টেম শীঘ্রই আসছে..."));
                
                actionsBox.getChildren().addAll(btnConfirm, btnContactTransit, btnTrack, btnDetails);
                break;
                
            case "delivered":
            case "completed":
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
                
                actionsBox.getChildren().addAll(btnRate, btnContactDelivered, btnReorder, btnDetails);
                break;

            case "rejected":
            case "cancelled":
                actionsBox.getChildren().add(btnDetails);
                break;
                
            default:
                actionsBox.getChildren().add(btnDetails);
        }
        
        return actionsBox;
    }

    private String getStatusText(String status) {
        switch (status) {
            case "new": return "🆕 অনুরোধ করা হয়েছে";
            case "accepted": return "✓ গৃহীত";
            case "in_transit": return "🚚 পথে আছে";
            case "delivered": return "✅ গ্রহণ করা হয়েছে";
            case "cancelled": return "✗ বাতিল";
            case "completed": return "✅ গ্রহণ করা হয়েছে";
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
        confirm.setHeaderText("পণ্য গ্রহণ নিশ্চিত করবেন?");
        confirm.setContentText("আপনি কি নিশ্চিত যে আপনি এই পণ্য পেয়েছেন?");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            // First get farmer_id from the order
            DatabaseService.executeQueryAsync(
                "SELECT c.farmer_id FROM orders o JOIN crops c ON o.crop_id = c.id WHERE o.id = ?",
                new Object[]{orderId},
                rs -> {
                    try {
                        if (rs.next()) {
                            int farmerId = rs.getInt("farmer_id");
                            
                            // Update order status + timestamps
                            String updateSql;
                            Object[] updateParams;
                            if ("completed".equals(newStatus)) {
                                updateSql = "UPDATE orders SET status = ?, completed_at = datetime('now'), updated_at = datetime('now') WHERE id = ?";
                                updateParams = new Object[]{newStatus, orderId};
                            } else {
                                updateSql = "UPDATE orders SET status = ?, updated_at = datetime('now') WHERE id = ?";
                                updateParams = new Object[]{newStatus, orderId};
                            }

                            DatabaseService.executeUpdateAsync(
                                updateSql,
                                updateParams,
                                rowsAffected -> {
                                    Platform.runLater(() -> {
                                        if (rowsAffected > 0) {
                                            showSuccess("সফল", "অর্ডার স্ট্যাটাস আপডেট করা হয়েছে।");
                                            refreshOrders();

                                            // Best-effort cloud sync
                                            FirebaseSyncService.getInstance().syncOrderToFirebase(orderId);
                                            FirebaseSyncService.getInstance().syncOrderStatusToFirebase(orderId, newStatus, null);
                                            
                                            // Update statistics if order completed
                                            if ("completed".equals(newStatus)) {
                                                StatisticsCalculator.updateFarmerStatistics(farmerId);
                                                StatisticsCalculator.updateBuyerStatistics(currentUser.getId());
                                            }
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
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                },
                error -> error.printStackTrace()
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
        App.setCurrentOrderId(orderId);
        App.setPreviousScene("buyer-orders-view.fxml");
        App.loadScene("order-detail-view.fxml", "অর্ডার বিস্তারিত");
    }

    @FXML
    private void onRefresh() {
        refreshOrders();
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
