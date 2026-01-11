package com.sajid._207017_chashi_bhai.controllers;

import com.sajid._207017_chashi_bhai.App;
import com.sajid._207017_chashi_bhai.models.User;
import com.sajid._207017_chashi_bhai.services.DatabaseService;
import com.sajid._207017_chashi_bhai.services.FirebaseSyncService;
import com.sajid._207017_chashi_bhai.utils.DataSyncManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * FarmerHistoryController - Display completed sales history and analytics
 * Features real-time sync with database polling
 */
public class FarmerHistoryController {

    @FXML private Label lblTotalIncome;
    @FXML private Label lblMostSold;
    @FXML private Label lblTotalAcceptedOrders;
    @FXML private ComboBox<String> cbFilterMonth;
    @FXML private ComboBox<String> cbFilterCrop;
    @FXML private ComboBox<String> cbSortBy;
    @FXML private Button btnApplyFilter;
    @FXML private Button btnExport;
    @FXML private Button btnBack;
    @FXML private VBox vboxHistoryList;
    @FXML private ProgressIndicator progressIndicator;

    private User currentUser;
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

        // Initialize filters (FXML may already provide items)
        if (cbFilterMonth != null && cbFilterMonth.getItems().isEmpty()) {
            cbFilterMonth.getItems().addAll("সব সময় (All Time)", "এই মাস (This Month)", "গত মাস (Last Month)", "গত ৩ মাস (Last 3 Months)", "এই বছর (This Year)");
        }
        if (cbFilterMonth != null && cbFilterMonth.getValue() == null && !cbFilterMonth.getItems().isEmpty()) {
            cbFilterMonth.getSelectionModel().select(0);
        }
        
        // Initialize sort dropdown with default selection
        if (cbSortBy != null) {
            if (cbSortBy.getSelectionModel().getSelectedItem() == null && !cbSortBy.getItems().isEmpty()) {
                cbSortBy.getSelectionModel().select(0); // Default: Newest First
            }
            cbSortBy.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null) {
                    loadHistory();
                }
            });
        }

        if (btnApplyFilter != null) {
            btnApplyFilter.setOnAction(e -> loadHistory());
        }
        
        loadCropFilter();
        loadSummaryStats();
        loadHistory();
        
        // Start real-time sync polling for history (every 30 seconds)
        syncManager.startPolling("history_" + currentUser.getId(), this::refreshHistory, 30);

        // Initial sync to align with remote updates on first load
        refreshHistory();
    }

    private void loadCropFilter() {
        DatabaseService.executeQueryAsync(
            "SELECT DISTINCT name FROM crops WHERE farmer_id = ? ORDER BY name",
            new Object[]{currentUser.getId()},
            resultSet -> {
                try {
                    List<String> crops = new ArrayList<>();
                    while (resultSet.next()) {
                        String name = resultSet.getString("name");
                        if (name != null && !name.isBlank()) {
                            crops.add(name);
                        }
                    }

                    Platform.runLater(() -> {
                        try {
                            if (cbFilterCrop == null) {
                                return;
                            }
                            String selected = cbFilterCrop.getValue();
                            cbFilterCrop.getItems().clear();
                            cbFilterCrop.getItems().add("সব ফসল (All Crops)");
                            cbFilterCrop.getItems().addAll(crops);

                            if (selected != null && cbFilterCrop.getItems().contains(selected)) {
                                cbFilterCrop.getSelectionModel().select(selected);
                            } else {
                                cbFilterCrop.getSelectionModel().select(0);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            },
            error -> error.printStackTrace()
        );
    }

    private void loadSummaryStats() {
        // Get total income (completed/delivered)
        DatabaseService.executeQueryAsync(
            "SELECT COALESCE(SUM(o.total_amount), 0) as total_income " +
                "FROM orders o " +
                "WHERE o.farmer_id = ? AND o.status IN ('delivered', 'completed')",
            new Object[]{currentUser.getId()},
            resultSet -> {
                double income = 0.0;
                try {
                    if (resultSet.next()) {
                        income = resultSet.getDouble("total_income");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                final double finalIncome = income;
                Platform.runLater(() -> {
                    if (lblTotalIncome != null) {
                        lblTotalIncome.setText(String.format("৳%.2f", finalIncome));
                    }
                });
            },
            error -> error.printStackTrace()
        );

        // Get most sold crop (by quantity)
        DatabaseService.executeQueryAsync(
            "SELECT c.name as most_sold, COALESCE(SUM(o.quantity_kg), 0) as qty " +
                "FROM orders o " +
                "JOIN crops c ON o.crop_id = c.id " +
                "WHERE o.farmer_id = ? AND o.status IN ('delivered', 'completed') " +
                "GROUP BY c.id " +
                "ORDER BY qty DESC LIMIT 1",
            new Object[]{currentUser.getId()},
            resultSet -> {
                String mostSold = "N/A";
                try {
                    if (resultSet.next()) {
                        String name = resultSet.getString("most_sold");
                        if (name != null && !name.isBlank()) {
                            mostSold = name;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                final String finalMostSold = mostSold;
                Platform.runLater(() -> {
                    if (lblMostSold != null) {
                        lblMostSold.setText(finalMostSold);
                    }
                });
            },
            error -> error.printStackTrace()
        );

        // Get total accepted orders (pipeline statuses)
        DatabaseService.executeQueryAsync(
            "SELECT COUNT(*) as total_orders " +
            "FROM orders o " +
            "WHERE o.farmer_id = ? AND o.status IN ('accepted','shipped','in_transit','delivered','completed')",
            new Object[]{currentUser.getId()},
            resultSet -> {
                int totalOrders = 0;
                try {
                    if (resultSet.next()) {
                        totalOrders = resultSet.getInt("total_orders");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                final int finalTotalOrders = totalOrders;
                Platform.runLater(() -> {
                    if (lblTotalAcceptedOrders != null) {
                        lblTotalAcceptedOrders.setText(String.valueOf(finalTotalOrders));
                    }
                });
            },
            error -> error.printStackTrace()
        );
    }

    private void loadHistory() {
        if (progressIndicator != null) {
            progressIndicator.setVisible(true);
        }
        if (vboxHistoryList != null) {
            vboxHistoryList.getChildren().clear();
        }

        String effectiveDateExpr = "COALESCE(o.completed_at, o.delivered_at, o.updated_at, o.created_at)";

        String query = "SELECT o.id, o.quantity_kg, o.price_per_kg, o.total_amount, o.payment_status, " +
                      effectiveDateExpr + " as order_date, " +
                      "c.name as crop_name, " +
                      "u.name as buyer_name " +
                      "FROM orders o " +
                      "JOIN crops c ON o.crop_id = c.id " +
                      "JOIN users u ON o.buyer_id = u.id " +
                      "WHERE o.farmer_id = ? AND o.status IN ('delivered', 'completed') ";

        List<Object> params = new ArrayList<>();
        params.add(currentUser.getId());

        // Month filter (based on effective date)
        String month = cbFilterMonth != null ? cbFilterMonth.getValue() : null;
        if (month != null) {
            if (month.contains("This Month") || month.contains("এই মাস")) {
                query += " AND date(" + effectiveDateExpr + ") >= date('now','start of month')";
            } else if (month.contains("Last Month") || month.contains("গত মাস")) {
                query += " AND date(" + effectiveDateExpr + ") >= date('now','start of month','-1 month')";
                query += " AND date(" + effectiveDateExpr + ") < date('now','start of month')";
            } else if (month.contains("Last 3") || month.contains("গত ৩")) {
                query += " AND date(" + effectiveDateExpr + ") >= date('now','-3 months')";
            } else if (month.contains("This Year") || month.contains("এই বছর")) {
                query += " AND date(" + effectiveDateExpr + ") >= date('now','start of year')";
            }
        }

        // Crop filter
        String crop = cbFilterCrop != null ? cbFilterCrop.getValue() : null;
        if (crop != null && !crop.isBlank() && !crop.contains("All") && !crop.contains("সব")) {
            query += " AND c.name = ?";
            params.add(crop);
        }
        
        // Apply sorting based on user selection
        String sortOption = cbSortBy != null ? cbSortBy.getSelectionModel().getSelectedItem() : null;
        if (sortOption != null) {
            if (sortOption.contains("High to Low") || sortOption.contains("বেশি থেকে কম")) {
                query += " ORDER BY o.total_amount DESC";
            } else if (sortOption.contains("Low to High") || sortOption.contains("কম থেকে বেশি")) {
                query += " ORDER BY o.total_amount ASC";
            } else {
                // Default: Newest First
                query += " ORDER BY " + effectiveDateExpr + " DESC";
            }
        } else {
            query += " ORDER BY " + effectiveDateExpr + " DESC";
        }

        DatabaseService.executeQueryAsync(
            query,
            params.toArray(),
            resultSet -> {
                try {
                    java.util.List<HistoryCardData> cardRows = new java.util.ArrayList<>();
                    while (resultSet.next()) {
                        cardRows.add(extractCardData(resultSet));
                    }

                    Platform.runLater(() -> {
                        try {
                            for (HistoryCardData data : cardRows) {
                                HBox historyCard = createHistoryCard(data);
                                vboxHistoryList.getChildren().add(historyCard);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            showError("ত্রুটি", "ইতিহাস লোড করতে ব্যর্থ হয়েছে।");
                        } finally {
                            if (progressIndicator != null) {
                                progressIndicator.setVisible(false);
                            }
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    Platform.runLater(() -> {
                        if (progressIndicator != null) {
                            progressIndicator.setVisible(false);
                        }
                        showError("ত্রুটি", "ইতিহাস লোড করতে ব্যর্থ হয়েছে।");
                    });
                }
            },
            error -> {
                Platform.runLater(() -> {
                    if (progressIndicator != null) {
                        progressIndicator.setVisible(false);
                    }
                    showError("ডাটাবেস ত্রুটি", "ইতিহাস লোড করতে সমস্যা হয়েছে।");
                    error.printStackTrace();
                });
            }
        );
    }

    private static final class HistoryCardData {
        final int orderId;
        final String date;
        final String buyerName;
        final String cropName;
        final double quantity;
        final double price;
        final String paymentStatus;

        HistoryCardData(int orderId, String date, String buyerName, String cropName,
                        double quantity, double price, String paymentStatus) {
            this.orderId = orderId;
            this.date = date;
            this.buyerName = buyerName;
            this.cropName = cropName;
            this.quantity = quantity;
            this.price = price;
            this.paymentStatus = paymentStatus;
        }
    }

    private HistoryCardData extractCardData(ResultSet rs) throws Exception {
        int orderId = rs.getInt("id");
        String date = rs.getString("order_date");
        if (date != null && date.length() > 10) {
            date = date.substring(0, 10);
        }
        String buyerName = rs.getString("buyer_name");
        String cropName = rs.getString("crop_name");
        double quantity = rs.getDouble("quantity_kg");
        double price = rs.getDouble("price_per_kg");
        String paymentStatus = rs.getString("payment_status");

        return new HistoryCardData(orderId, date, buyerName, cropName, quantity, price, paymentStatus);
    }

    private HBox createHistoryCard(HistoryCardData data) throws Exception {
        int orderId = data.orderId;
        String date = data.date;
        String buyerName = data.buyerName;
        String cropName = data.cropName;
        double quantity = data.quantity;
        double price = data.price;
        String paymentStatus = data.paymentStatus;
        String unit = "কেজি"; // All crops measured in kg
        double totalPrice = quantity * price;

        HBox card = new HBox(20);
        card.getStyleClass().add("history-card");
        card.setPadding(new Insets(15));

        // Date column
        VBox dateBox = new VBox(5);
        dateBox.setPrefWidth(120);
        Label lblDate = new Label(date);
        lblDate.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        dateBox.getChildren().add(lblDate);

        // Buyer column
        VBox buyerBox = new VBox(5);
        buyerBox.setPrefWidth(150);
        Label lblBuyerTitle = new Label("ক্রেতা:");
        lblBuyerTitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #888;");
        Label lblBuyer = new Label(buyerName);
        lblBuyer.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        buyerBox.getChildren().addAll(lblBuyerTitle, lblBuyer);

        // Crop column
        VBox cropBox = new VBox(5);
        cropBox.setPrefWidth(150);
        Label lblCropTitle = new Label("ফসল:");
        lblCropTitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #888;");
        Label lblCrop = new Label(cropName);
        lblCrop.setStyle("-fx-font-size: 14px;");
        cropBox.getChildren().addAll(lblCropTitle, lblCrop);

        // Quantity column
        VBox quantityBox = new VBox(5);
        quantityBox.setPrefWidth(120);
        Label lblQuantityTitle = new Label("পরিমাণ:");
        lblQuantityTitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #888;");
        Label lblQuantity = new Label(String.format("%.1f %s", quantity, unit));
        lblQuantity.setStyle("-fx-font-size: 14px;");
        quantityBox.getChildren().addAll(lblQuantityTitle, lblQuantity);

        // Price column
        VBox priceBox = new VBox(5);
        priceBox.setPrefWidth(120);
        Label lblPriceTitle = new Label("মোট আয়:");
        lblPriceTitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #888;");
        Label lblPrice = new Label(String.format("৳%.2f", totalPrice));
        lblPrice.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #4CAF50;");
        priceBox.getChildren().addAll(lblPriceTitle, lblPrice);

        // Payment status
        VBox statusBox = new VBox(5);
        statusBox.setPrefWidth(120);
        Label lblStatusTitle = new Label("পেমেন্ট:");
        lblStatusTitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #888;");
        Label lblStatus = new Label(paymentStatus != null && paymentStatus.equals("completed") ? "✓ সম্পূর্ণ" : "⏳ পেন্ডিং");
        lblStatus.getStyleClass().add(paymentStatus != null && paymentStatus.equals("completed") ? "payment-complete" : "payment-pending");
        statusBox.getChildren().addAll(lblStatusTitle, lblStatus);

        // Action button
        VBox actionBox = new VBox(5);
        actionBox.setPrefWidth(100);
        Button btnView = new Button("👁 দেখুন");
        btnView.getStyleClass().add("button-secondary");
        btnView.setMaxWidth(Double.MAX_VALUE);
        btnView.setOnAction(e -> showOrderDetails(orderId));
        actionBox.getChildren().add(btnView);

        card.getChildren().addAll(dateBox, buyerBox, cropBox, quantityBox, priceBox, statusBox, actionBox);
        return card;
    }

    private void showOrderDetails(int orderId) {
        DatabaseService.executeQueryAsync(
            "SELECT o.*, c.name as crop_name, c.price_per_kg as price, u.name as buyer_name, u.phone as buyer_phone, u.district as buyer_district, " +
            "COALESCE(o.completed_at, o.delivered_at, o.created_at) as delivery_date " +
            "FROM orders o " +
            "JOIN crops c ON o.crop_id = c.id " +
            "JOIN users u ON o.buyer_id = u.id " +
            "WHERE o.id = ?",
            new Object[]{orderId},
            resultSet -> {
                boolean found = false;
                String cropName = null;
                String buyerName = null;
                String buyerPhone = null;
                String buyerDistrict = null;
                String createdAt = null;
                String deliveryDate = null;
                double quantity = 0.0;
                double price = 0.0;

                try {
                    if (resultSet.next()) {
                        found = true;
                        cropName = resultSet.getString("crop_name");
                        buyerName = resultSet.getString("buyer_name");
                        buyerPhone = resultSet.getString("buyer_phone");
                        buyerDistrict = resultSet.getString("buyer_district");
                        createdAt = resultSet.getString("created_at");
                        deliveryDate = resultSet.getString("delivery_date");
                        quantity = resultSet.getDouble("quantity_kg");
                        price = resultSet.getDouble("price");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                final boolean finalFound = found;
                final String finalCropName = cropName;
                final String finalBuyerName = buyerName;
                final String finalBuyerPhone = buyerPhone;
                final String finalBuyerDistrict = buyerDistrict;
                final String finalCreatedAt = createdAt;
                final String finalDeliveryDate = deliveryDate;
                final double finalQuantity = quantity;
                final double finalPrice = price;

                Platform.runLater(() -> {
                    if (!finalFound) {
                        showError("পাওয়া যায়নি", "অর্ডারটি পাওয়া যায়নি।");
                        return;
                    }

                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                    alert.setTitle("অর্ডার বিস্তারিত");
                    alert.setHeaderText("অর্ডার #" + orderId);

                    String unit = "কেজি";
                    alert.setContentText(
                        "ফসল: " + (finalCropName != null ? finalCropName : "") + "\n" +
                        "ক্রেতা: " + (finalBuyerName != null ? finalBuyerName : "") + "\n" +
                        "ফোন: " + (finalBuyerPhone != null ? finalBuyerPhone : "") + "\n" +
                        "ঠিকানা: " + (finalBuyerDistrict != null ? finalBuyerDistrict : "") + "\n" +
                        "পরিমাণ: " + String.format("%.1f", finalQuantity) + " " + unit + "\n" +
                        "দাম: ৳" + String.format("%.2f", finalPrice) + "/" + unit + "\n" +
                        "মোট আয়: ৳" + String.format("%.2f", finalQuantity * finalPrice) + "\n" +
                        "অর্ডারের তারিখ: " + (finalCreatedAt != null ? finalCreatedAt : "") + "\n" +
                        "ডেলিভারির/সম্পন্নের তারিখ: " + (finalDeliveryDate != null ? finalDeliveryDate : "")
                    );

                    ButtonType viewDetails = new ButtonType("👁 বিস্তারিত দেখুন", ButtonBar.ButtonData.OK_DONE);
                    ButtonType close = new ButtonType("বন্ধ", ButtonBar.ButtonData.CANCEL_CLOSE);
                    alert.getButtonTypes().setAll(viewDetails, close);

                    alert.showAndWait().ifPresent(choice -> {
                        if (choice == viewDetails) {
                            App.setCurrentOrderId(orderId);
                            App.setPreviousScene("farmer-history-view.fxml");
                            App.loadScene("order-detail-view.fxml", "অর্ডার বিস্তারিত");
                        }
                    });
                });
            },
            error -> error.printStackTrace()
        );
    }

    @FXML
    private void onApplyFilter() {
        // Apply filters and reload
        loadHistory();
    }

    private void refreshHistory() {
        FirebaseSyncService.getInstance().syncFarmerOrdersFromFirebase(
            currentUser.getId(),
            () -> {
                loadSummaryStats();
                loadHistory();
            }
        );
    }

    @FXML
    private void onExport() {
        showInfo("Export", "রপ্তানি বৈশিষ্ট্য শীঘ্রই আসছে...");
    }

    @FXML
    private void onBack() {
        // Stop polling when leaving the view
        if (syncManager != null && currentUser != null) {
            syncManager.stopPolling("history_" + currentUser.getId());
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

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
