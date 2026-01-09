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
 * FarmerOrdersController - Manage incoming buyer orders
 * Features real-time sync with database polling
 */
public class FarmerOrdersController {

    @FXML private Button btnFilterAll;
    @FXML private Button btnFilterNew;
    @FXML private Button btnFilterAccepted;
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
        
        if (currentUser == null || !"farmer".equals(currentUser.getRole())) {
            showError("অ্যাক্সেস অস্বীকার", "শুধুমাত্র কৃষকরা এই পেজ দেখতে পারবেন।");
            App.loadScene("login-view.fxml", "Login");
            return;
        }

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
    private void onFilterNew() {
        setActiveFilter(btnFilterNew);
        currentFilter = "pending";
        loadOrders(currentFilter);
    }

    @FXML
    private void onFilterAccepted() {
        setActiveFilter(btnFilterAccepted);
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
        btnFilterNew.getStyleClass().remove("filter-active");
        btnFilterAccepted.getStyleClass().remove("filter-active");
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
                      "u.name as buyer_name, u.phone as buyer_phone, u.district as buyer_district, " +
                      "(SELECT photo_path FROM crop_photos WHERE crop_id = c.id ORDER BY photo_order LIMIT 1) as crop_photo " +
                      "FROM orders o " +
                      "JOIN crops c ON o.crop_id = c.id " +
                      "JOIN users u ON o.buyer_id = u.id " +
                      "WHERE c.farmer_id = ?";
        
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
                            HBox orderCard = createOrderCardFromResultSet(resultSet);
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

    private HBox createDummyOrderCard(int orderId, String cropName, String buyerName, 
                                      String buyerPhone, String buyerDistrict, double quantity, 
                                      double price, String unit, String status, String createdAt) {
        HBox card = new HBox(15);
        card.getStyleClass().addAll("order-card", "order-" + status.replace("_", "-"));
        card.setPadding(new Insets(15));

        // Crop image placeholder
        ImageView imageView = new ImageView();
        imageView.setFitWidth(90);
        imageView.setFitHeight(90);
        imageView.setPreserveRatio(true);
        imageView.setStyle("-fx-background-color: #e0e0e0;");

        // Order details
        VBox detailsBox = new VBox(8);
        detailsBox.setPrefWidth(400);
        
        Label lblOrderId = new Label("অর্ডার #" + orderId);
        lblOrderId.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #888;");
        
        Label lblCrop = new Label("🌾 " + cropName);
        lblCrop.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        
        Label lblBuyer = new Label("ক্রেতা: " + buyerName);
        lblBuyer.setStyle("-fx-font-size: 14px;");
        
        Label lblPhone = new Label("📞 " + buyerPhone);
        lblPhone.setStyle("-fx-font-size: 14px;");
        
        Label lblLocation = new Label("📍 " + buyerDistrict);
        lblLocation.setStyle("-fx-font-size: 14px;");
        
        Label lblQuantity = new Label(String.format("পরিমাণ: %.1f %s", quantity, unit));
        lblQuantity.setStyle("-fx-font-size: 14px;");
        
        double totalPrice = quantity * price;
        Label lblPrice = new Label(String.format("মোট: ৳%.2f", totalPrice));
        lblPrice.setStyle("-fx-font-size: 16px; -fx-text-fill: #4CAF50; -fx-font-weight: bold;");
        
        Label lblDate = new Label("তারিখ: " + createdAt);
        lblDate.setStyle("-fx-font-size: 12px; -fx-text-fill: #888;");
        
        Label lblStatus = new Label(getStatusText(status));
        lblStatus.getStyleClass().add("status-badge");

        detailsBox.getChildren().addAll(lblOrderId, lblCrop, lblBuyer, lblPhone, lblLocation, lblQuantity, lblPrice, lblDate, lblStatus);

        // Action buttons
        VBox actionsBox = new VBox(10);
        actionsBox.setPrefWidth(180);
        actionsBox.getChildren().addAll(getActionButtons(orderId, status, buyerPhone));

        card.getChildren().addAll(imageView, detailsBox, actionsBox);
        return card;
    }

    private HBox createOrderCardFromResultSet(java.sql.ResultSet rs) throws Exception {
        int orderId = rs.getInt("id");
        String cropName = rs.getString("crop_name");
        String buyerName = rs.getString("buyer_name");
        String buyerPhone = rs.getString("buyer_phone");
        String buyerDistrict = rs.getString("buyer_district");
        double quantity = rs.getDouble("quantity_kg");
        double price = rs.getDouble("price");
        String unit = "কেজি";
        String status = rs.getString("status");
        String createdAt = rs.getString("created_at");
        String photoPath = rs.getString("crop_photo");

        HBox card = new HBox(15);
        card.getStyleClass().addAll("order-card", "order-" + status.replace("_", "-"));
        card.setPadding(new Insets(15));

        // Crop image
        ImageView imageView = new ImageView();
        imageView.setFitWidth(90);
        imageView.setFitHeight(90);
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
        
        Label lblBuyer = new Label("ক্রেতা: " + buyerName);
        lblBuyer.setStyle("-fx-font-size: 14px;");
        
        Label lblPhone = new Label("📞 " + buyerPhone);
        lblPhone.setStyle("-fx-font-size: 14px;");
        
        Label lblLocation = new Label("📍 " + buyerDistrict);
        lblLocation.setStyle("-fx-font-size: 14px;");
        
        Label lblQuantity = new Label(String.format("পরিমাণ: %.1f %s", quantity, unit));
        lblQuantity.setStyle("-fx-font-size: 14px;");
        
        double totalPrice = quantity * price;
        Label lblPrice = new Label(String.format("মোট: ৳%.2f", totalPrice));
        lblPrice.setStyle("-fx-font-size: 16px; -fx-text-fill: #4CAF50; -fx-font-weight: bold;");
        
        Label lblDate = new Label("তারিখ: " + createdAt.substring(0, 10));
        lblDate.setStyle("-fx-font-size: 12px; -fx-text-fill: #888;");
        
        Label lblStatus = new Label(getStatusText(status));
        lblStatus.getStyleClass().add("status-badge");

        detailsBox.getChildren().addAll(lblOrderId, lblCrop, lblBuyer, lblPhone, lblLocation, lblQuantity, lblPrice, lblDate, lblStatus);

        // Action buttons
        VBox actionsBox = new VBox(10);
        actionsBox.setPrefWidth(180);
        actionsBox.getChildren().addAll(getActionButtons(orderId, status, buyerPhone));

        card.getChildren().addAll(imageView, detailsBox, actionsBox);
        return card;
    }

    private VBox getActionButtons(int orderId, String status, String buyerPhone) {
        VBox actionsBox = new VBox(10);
        
        switch (status) {
            case "new":
            case "pending":
                Button btnAccept = new Button("✓ গ্রহণ করুন");
                btnAccept.getStyleClass().add("button-success");
                btnAccept.setMaxWidth(Double.MAX_VALUE);
                btnAccept.setOnAction(e -> updateOrderStatus(orderId, "accepted"));
                
                Button btnContact = new Button("📞 যোগাযোগ");
                btnContact.getStyleClass().add("button-info");
                btnContact.setMaxWidth(Double.MAX_VALUE);
                btnContact.setOnAction(e -> contactBuyer(buyerPhone));
                
                Button btnReject = new Button("✗ প্রত্যাখ্যান");
                btnReject.getStyleClass().add("button-danger");
                btnReject.setMaxWidth(Double.MAX_VALUE);
                btnReject.setOnAction(e -> updateOrderStatus(orderId, "rejected"));
                
                actionsBox.getChildren().addAll(btnAccept, btnContact, btnReject);
                break;
                
            case "accepted":
                Button btnInTransit = new Button("🚚 পাঠানো হচ্ছে");
                btnInTransit.getStyleClass().add("button-success");
                btnInTransit.setMaxWidth(Double.MAX_VALUE);
                btnInTransit.setOnAction(e -> updateOrderStatus(orderId, "in_transit"));
                
                Button btnContactAccepted = new Button("📞 যোগাযোগ");
                btnContactAccepted.getStyleClass().add("button-info");
                btnContactAccepted.setMaxWidth(Double.MAX_VALUE);
                btnContactAccepted.setOnAction(e -> contactBuyer(buyerPhone));
                
                Button btnDetails = new Button("📄 বিস্তারিত");
                btnDetails.getStyleClass().add("button-secondary");
                btnDetails.setMaxWidth(Double.MAX_VALUE);
                btnDetails.setOnAction(e -> showOrderDetails(orderId));
                
                actionsBox.getChildren().addAll(btnInTransit, btnContactAccepted, btnDetails);
                break;
                
            case "in_transit":
                Button btnDeliver = new Button("✓ ডেলিভার সম্পূর্ণ");
                btnDeliver.getStyleClass().add("button-success");
                btnDeliver.setMaxWidth(Double.MAX_VALUE);
                btnDeliver.setOnAction(e -> updateOrderStatus(orderId, "delivered"));
                
                Button btnContactTransit = new Button("📞 যোগাযোগ");
                btnContactTransit.getStyleClass().add("button-info");
                btnContactTransit.setMaxWidth(Double.MAX_VALUE);
                btnContactTransit.setOnAction(e -> contactBuyer(buyerPhone));
                
                actionsBox.getChildren().addAll(btnDeliver, btnContactTransit);
                break;
                
            case "delivered":
                Button btnViewDelivered = new Button("👁 দেখুন");
                btnViewDelivered.getStyleClass().add("button-secondary");
                btnViewDelivered.setMaxWidth(Double.MAX_VALUE);
                btnViewDelivered.setOnAction(e -> showOrderDetails(orderId));
                
                actionsBox.getChildren().add(btnViewDelivered);
                break;
        }
        
        return actionsBox;
    }

    private String getStatusText(String status) {
        switch (status) {
            case "new":
            case "pending": return "🔔 নতুন অর্ডার";
            case "accepted": return "✓ গৃহীত";
            case "in_transit": return "🚚 পাঠানো হচ্ছে";
            case "delivered": return "✓ ডেলিভার হয়েছে";
            case "rejected": return "✗ প্রত্যাখ্যান";
            case "completed": return "✓ সম্পূর্ণ";
            case "cancelled": return "✗ বাতিল";
            default: return status;
        }
    }

    private void updateOrderStatus(int orderId, String newStatus) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("নিশ্চিত করুন");
        confirm.setHeaderText("স্ট্যাটাস পরিবর্তন করবেন?");
        confirm.setContentText("আপনি কি এই অর্ডারের স্ট্যাটাস \"" + getStatusText(newStatus) + "\" এ পরিবর্তন করতে চান?");

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

    private void contactBuyer(String phone) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("যোগাযোগ করুন");
        alert.setHeaderText("ক্রেতার সাথে যোগাযোগ করুন");
        alert.setContentText("Phone: " + phone);
        
        ButtonType callButton = new ButtonType("📞 কল করুন");
        ButtonType whatsappButton = new ButtonType("💬 WhatsApp");
        ButtonType cancelButton = new ButtonType("বাতিল", ButtonBar.ButtonData.CANCEL_CLOSE);
        
        alert.getButtonTypes().setAll(callButton, whatsappButton, cancelButton);
        
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent()) {
            if (result.get() == callButton) {
                openPhone(phone);
            } else if (result.get() == whatsappButton) {
                openWhatsApp(phone);
            }
        }
    }

    private void openPhone(String phone) {
        try {
            Desktop.getDesktop().browse(new URI("tel:" + phone));
        } catch (Exception e) {
            showInfo("Phone", "ফোন নম্বর: " + phone);
        }
    }

    private void openWhatsApp(String phone) {
        try {
            String cleanPhone = phone.replaceAll("[^0-9]", "");
            Desktop.getDesktop().browse(new URI("https://wa.me/" + cleanPhone));
        } catch (Exception e) {
            showInfo("WhatsApp", "WhatsApp: " + phone);
        }
    }

    private void showOrderDetails(int orderId) {
        // TODO: Replace with actual database call later
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("অর্ডার বিস্তারিত");
        alert.setHeaderText("অর্ডার #" + orderId);
        alert.setContentText(
            "ফসল: তাজা টমেটো\n" +
            "ক্রেতা: রহিম মিয়া\n" +
            "ফোন: 01712345678\n" +
            "পরিমাণ: 50.0 কেজি\n" +
            "স্ট্যাটাস: নতুন অর্ডার\n" +
            "তারিখ: 2025-12-20"
        );
        alert.showAndWait();
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
        App.loadScene("farmer-dashboard-view.fxml", "Dashboard");
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
