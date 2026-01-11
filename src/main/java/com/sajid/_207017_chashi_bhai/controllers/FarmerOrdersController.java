package com.sajid._207017_chashi_bhai.controllers;

import com.sajid._207017_chashi_bhai.App;
import com.sajid._207017_chashi_bhai.models.User;
import com.sajid._207017_chashi_bhai.services.DatabaseService;
import com.sajid._207017_chashi_bhai.services.FirebaseSyncService;
import com.sajid._207017_chashi_bhai.services.OrderService;
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
import java.util.ArrayList;
import java.util.List;
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
        final String buyerName;
        final String buyerPhone;
        final String buyerDistrict;
        final double quantity;
        final double price;
        final String status;
        final String createdAt;
        final String photoPath;

        private OrderRow(int orderId, String cropName, String buyerName, String buyerPhone,
                         String buyerDistrict, double quantity, double price, String status,
                         String createdAt, String photoPath) {
            this.orderId = orderId;
            this.cropName = cropName;
            this.buyerName = buyerName;
            this.buyerPhone = buyerPhone;
            this.buyerDistrict = buyerDistrict;
            this.quantity = quantity;
            this.price = price;
            this.status = status;
            this.createdAt = createdAt;
            this.photoPath = photoPath;
        }
    }

    @FXML
    public void initialize() {
        currentUser = App.getCurrentUser();
        syncManager = DataSyncManager.getInstance();
        
        if (currentUser == null || !"farmer".equals(currentUser.getRole())) {
            showError("অ্যাক্সেস অস্বীকার", "শুধুমাত্র কৃষকরা এই পেজ দেখতে পারবেন।");
            App.loadScene("login-view.fxml", "Login");
            return;
        }
        
        // Initialize sort dropdown with default selection
        if (cbSortBy != null) {
            cbSortBy.getSelectionModel().select(0); // Default: Newest First
            cbSortBy.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null) {
                    loadOrders(currentFilter);
                }
            });
        }

        if (btnRefresh != null) {
            btnRefresh.setOnAction(e -> onRefresh());
        }

        loadOrders(currentFilter);
        
        // Start real-time sync polling for orders (every 15 seconds)
        syncManager.startOrdersSync(currentUser.getId(), this::refreshOrders);
    }

    private void refreshOrders() {
        FirebaseSyncService.getInstance().syncFarmerOrdersFromFirebase(
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
    private void onFilterNew() {
        setActiveFilter(btnFilterNew);
        currentFilter = "new";
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
        currentFilter = "completed";
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
                                    resultSet.getString("buyer_name"),
                                    resultSet.getString("buyer_phone"),
                                    resultSet.getString("buyer_district"),
                                    resultSet.getDouble("quantity_kg"),
                                    resultSet.getDouble("price"),
                                    resultSet.getString("status"),
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
                                HBox orderCard = createOrderCardFromRow(row);
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

    private HBox createOrderCardFromRow(OrderRow row) {
        String safeStatus = row.status != null ? row.status : "new";

        HBox card = new HBox(15);
        card.getStyleClass().addAll("order-card", "order-" + safeStatus.replace("_", "-"));
        card.setPadding(new Insets(15));

        // Crop image
        ImageView imageView = new ImageView();
        imageView.setFitWidth(90);
        imageView.setFitHeight(90);
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

        Label lblBuyer = new Label("ক্রেতা: " + (row.buyerName != null ? row.buyerName : ""));
        lblBuyer.setStyle("-fx-font-size: 14px;");

        Label lblPhone = new Label("📞 " + (row.buyerPhone != null ? row.buyerPhone : ""));
        lblPhone.setStyle("-fx-font-size: 14px;");

        Label lblLocation = new Label("📍 " + (row.buyerDistrict != null ? row.buyerDistrict : ""));
        lblLocation.setStyle("-fx-font-size: 14px;");

        Label lblQuantity = new Label(String.format("পরিমাণ: %.1f %s", row.quantity, "কেজি"));
        lblQuantity.setStyle("-fx-font-size: 14px;");

        double totalPrice = row.quantity * row.price;
        Label lblPrice = new Label(String.format("মোট: ৳%.2f", totalPrice));
        lblPrice.setStyle("-fx-font-size: 16px; -fx-text-fill: #4CAF50; -fx-font-weight: bold;");

        String dateText = row.createdAt;
        if (dateText != null && dateText.length() >= 10) {
            dateText = dateText.substring(0, 10);
        }
        Label lblDate = new Label("তারিখ: " + (dateText != null ? dateText : "—"));
        lblDate.setStyle("-fx-font-size: 12px; -fx-text-fill: #888;");

        Label lblStatus = new Label(getStatusText(safeStatus));
        lblStatus.getStyleClass().add("status-badge");

        detailsBox.getChildren().addAll(lblOrderId, lblCrop, lblBuyer, lblPhone, lblLocation, lblQuantity, lblPrice, lblDate, lblStatus);

        // Action buttons
        VBox actionsBox = new VBox(10);
        actionsBox.setPrefWidth(180);
        actionsBox.getChildren().addAll(getActionButtons(row.orderId, safeStatus, row.buyerPhone));

        card.getChildren().addAll(imageView, detailsBox, actionsBox);
        return card;
    }

    private VBox getActionButtons(int orderId, String status, String buyerPhone) {
        VBox actionsBox = new VBox(10);

        Button btnDetails = new Button("📄 বিস্তারিত");
        btnDetails.getStyleClass().add("button-secondary");
        btnDetails.setMaxWidth(Double.MAX_VALUE);
        btnDetails.setOnAction(e -> showOrderDetails(orderId));
        
        switch (status) {
            case "new":
                Button btnAccept = new Button("✓ গ্রহণ করুন");
                btnAccept.getStyleClass().add("button-success");
                btnAccept.setMaxWidth(Double.MAX_VALUE);
                btnAccept.setOnAction(e -> acceptOrder(orderId));
                
                Button btnContact = new Button("📞 যোগাযোগ");
                btnContact.getStyleClass().add("button-info");
                btnContact.setMaxWidth(Double.MAX_VALUE);
                btnContact.setOnAction(e -> contactBuyer(buyerPhone));
                
                Button btnReject = new Button("✗ প্রত্যাখ্যান");
                btnReject.getStyleClass().add("button-danger");
                btnReject.setMaxWidth(Double.MAX_VALUE);
                btnReject.setOnAction(e -> rejectOrder(orderId));
                
                actionsBox.getChildren().addAll(btnAccept, btnContact, btnReject, btnDetails);
                break;
                
            case "accepted":
                Button btnInTransit = new Button("🚚 ডেলিভারির জন্য দিন");
                btnInTransit.getStyleClass().add("button-success");
                btnInTransit.setMaxWidth(Double.MAX_VALUE);
                btnInTransit.setOnAction(e -> markInTransit(orderId));
                
                Button btnContactAccepted = new Button("📞 যোগাযোগ");
                btnContactAccepted.getStyleClass().add("button-info");
                btnContactAccepted.setMaxWidth(Double.MAX_VALUE);
                btnContactAccepted.setOnAction(e -> contactBuyer(buyerPhone));
                
                actionsBox.getChildren().addAll(btnInTransit, btnContactAccepted, btnDetails);
                break;
                
            case "in_transit":
                Button btnContactTransit = new Button("📞 যোগাযোগ");
                btnContactTransit.getStyleClass().add("button-info");
                btnContactTransit.setMaxWidth(Double.MAX_VALUE);
                btnContactTransit.setOnAction(e -> contactBuyer(buyerPhone));
                
                actionsBox.getChildren().addAll(btnContactTransit, btnDetails);
                break;
                
            case "delivered":
            case "completed":
                Button btnViewDelivered = new Button("👁 দেখুন");
                btnViewDelivered.getStyleClass().add("button-secondary");
                btnViewDelivered.setMaxWidth(Double.MAX_VALUE);
                btnViewDelivered.setOnAction(e -> showOrderDetails(orderId));
                
                actionsBox.getChildren().add(btnViewDelivered);
                break;

            case "rejected":
            case "cancelled":
            default:
                Button btnDelete = new Button("🗑 ডিলিট");
                btnDelete.getStyleClass().add("button-danger");
                btnDelete.setMaxWidth(Double.MAX_VALUE);
                btnDelete.setOnAction(e -> deleteOrder(orderId));

                actionsBox.getChildren().addAll(btnDelete, btnDetails);
                break;
        }
        
        return actionsBox;
    }

    private String getStatusText(String status) {
        switch (status) {
            case "new": return "🔔 নতুন অর্ডার";
            case "accepted": return "✓ গৃহীত";
            case "in_transit": return "🚚 ডেলিভারির পথে";
            case "delivered": return "✅ গ্রহণ করা হয়েছে";
            case "rejected": return "✗ প্রত্যাখ্যান";
            case "completed": return "✅ গ্রহণ করা হয়েছে";
            case "cancelled": return "✗ বাতিল";
            default: return status;
        }
    }

    private void acceptOrder(int orderId) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("নিশ্চিত করুন");
        confirm.setHeaderText("অর্ডার গ্রহণ করবেন?");
        confirm.setContentText("অর্ডার গ্রহণ করলে ফসলের পরিমাণ কমে যাবে।");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            OrderService.acceptOrderAsync(
                orderId,
                currentUser.getId(),
                r -> {
                    if (r.ok) {
                        showSuccess("সফল", r.message);
                        refreshOrders();
                        FirebaseSyncService.getInstance().syncOrderStatusToFirebase(orderId, "accepted", null);
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
    }

    private void rejectOrder(int orderId) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("নিশ্চিত করুন");
        confirm.setHeaderText("অর্ডার প্রত্যাখ্যান করবেন?");
        confirm.setContentText("আপনি কি নিশ্চিত?");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            OrderService.rejectOrderAsync(
                orderId,
                currentUser.getId(),
                r -> {
                    if (r.ok) {
                        showSuccess("সফল", r.message);
                        refreshOrders();
                        FirebaseSyncService.getInstance().syncOrderStatusToFirebase(orderId, "rejected", null);
                    } else {
                        showError("ত্রুটি", r.message);
                    }
                },
                err -> {
                    showError("ত্রুটি", "অর্ডার প্রত্যাখ্যান করতে সমস্যা হয়েছে।");
                    err.printStackTrace();
                }
            );
        }
    }

    private void markInTransit(int orderId) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("নিশ্চিত করুন");
        confirm.setHeaderText("ডেলিভারির জন্য দেবেন?");
        confirm.setContentText("অর্ডারের স্ট্যাটাস 'ডেলিভারির পথে' হবে।");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            OrderService.markInTransitAsync(
                orderId,
                currentUser.getId(),
                r -> {
                    if (r.ok) {
                        showSuccess("সফল", r.message);
                        refreshOrders();
                        FirebaseSyncService.getInstance().syncOrderStatusToFirebase(orderId, "in_transit", null);
                    } else {
                        showError("ত্রুটি", r.message);
                    }
                },
                err -> {
                    showError("ত্রুটি", "স্ট্যাটাস আপডেট করতে সমস্যা হয়েছে।");
                    err.printStackTrace();
                }
            );
        }
    }

    private void deleteOrder(int orderId) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("নিশ্চিত করুন");
        confirm.setHeaderText("অর্ডার ডিলিট করবেন?");
        confirm.setContentText("এই অর্ডারটি ডিলিট করলে আর ফেরত আনা যাবে না।");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            OrderService.deleteOrderAsync(
                orderId,
                currentUser.getId(),
                r -> {
                    if (r.ok) {
                        showSuccess("সফল", r.message);
                        refreshOrders();
                    } else {
                        showError("ত্রুটি", r.message);
                    }
                },
                err -> {
                    showError("ত্রুটি", "অর্ডার ডিলিট করতে সমস্যা হয়েছে।");
                    err.printStackTrace();
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
        App.setCurrentOrderId(orderId);
        App.setPreviousScene("farmer-orders-view.fxml");
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
