package com.sajid._207017_chashi_bhai.controllers;

import com.sajid._207017_chashi_bhai.App;
import com.sajid._207017_chashi_bhai.models.User;
import com.sajid._207017_chashi_bhai.services.DatabaseService;
import com.sajid._207017_chashi_bhai.utils.DataSyncManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.sql.ResultSet;

/**
 * FarmerHistoryController - Display completed sales history and analytics
 * Features real-time sync with database polling
 */
public class FarmerHistoryController {

    @FXML private Label lblTotalIncome;
    @FXML private Label lblMostSold;
    @FXML private Label lblTotalOrders;
    @FXML private ComboBox<String> cbFilterMonth;
    @FXML private ComboBox<String> cbFilterCrop;
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

        // Initialize filters
        cbFilterMonth.getItems().addAll("সকল সময়", "এই মাস", "গত মাস", "গত ৩ মাস", "গত ৬ মাস", "এই বছর");
        cbFilterMonth.setValue("সকল সময়");
        
        loadCropFilter();
        loadSummaryStats();
        loadHistory();
        
        // Start real-time sync polling for history (every 30 seconds)
        syncManager.startPolling("history_" + currentUser.getId(), this::refreshHistory, 30);
    }

    private void loadCropFilter() {
        DatabaseService.executeQueryAsync(
            "SELECT DISTINCT name FROM crops WHERE farmer_id = ? ORDER BY name",
            new Object[]{currentUser.getId()},
            resultSet -> {
                Platform.runLater(() -> {
                    try {
                        cbFilterCrop.getItems().add("সকল ফসল");
                        while (resultSet.next()) {
                            cbFilterCrop.getItems().add(resultSet.getString("name"));
                        }
                        cbFilterCrop.setValue("সকল ফসল");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            },
            error -> error.printStackTrace()
        );
    }

    private void loadSummaryStats() {
        // Get total income
        DatabaseService.executeQueryAsync(
            "SELECT COALESCE(SUM(o.quantity_kg * o.price_per_kg), 0) as total_income " +
            "FROM orders o " +
            "WHERE o.farmer_id = ? AND o.status IN ('delivered', 'completed')",
            new Object[]{currentUser.getId()},
            resultSet -> {
                Platform.runLater(() -> {
                    try {
                        if (resultSet.next()) {
                            double income = resultSet.getDouble("total_income");
                            lblTotalIncome.setText(String.format("৳%.2f", income));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            },
            error -> error.printStackTrace()
        );

        // Get most sold crop
        DatabaseService.executeQueryAsync(
            "SELECT c.name as most_sold, COUNT(*) as count " +
            "FROM orders o " +
            "JOIN crops c ON o.crop_id = c.id " +
            "WHERE o.farmer_id = ? AND o.status IN ('delivered', 'completed') " +
            "GROUP BY c.id " +
            "ORDER BY count DESC LIMIT 1",
            new Object[]{currentUser.getId()},
            resultSet -> {
                Platform.runLater(() -> {
                    try {
                        if (resultSet.next()) {
                            String mostSold = resultSet.getString("most_sold");
                            lblMostSold.setText(mostSold != null ? mostSold : "N/A");
                        } else {
                            lblMostSold.setText("N/A");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            },
            error -> error.printStackTrace()
        );

        // Get total orders
        DatabaseService.executeQueryAsync(
            "SELECT COUNT(*) as total_orders " +
            "FROM orders o " +
            "WHERE o.farmer_id = ? AND o.status IN ('delivered', 'completed')",
            new Object[]{currentUser.getId()},
            resultSet -> {
                Platform.runLater(() -> {
                    try {
                        if (resultSet.next()) {
                            int totalOrders = resultSet.getInt("total_orders");
                            lblTotalOrders.setText(String.valueOf(totalOrders));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
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
        vboxHistoryList.getChildren().clear();

        String query = "SELECT o.*, c.name as crop_name, c.price_per_kg as price, 'কেজি' as unit, u.name as buyer_name, " +
                      "COALESCE(o.completed_at, o.delivered_at, o.created_at) as order_date " +
                      "FROM orders o " +
                      "JOIN crops c ON o.crop_id = c.id " +
                      "JOIN users u ON o.buyer_id = u.id " +
                      "WHERE o.farmer_id = ? AND o.status IN ('delivered', 'completed') " +
                      "ORDER BY COALESCE(o.completed_at, o.delivered_at, o.created_at) DESC";

        DatabaseService.executeQueryAsync(
            query,
            new Object[]{currentUser.getId()},
            resultSet -> {
                Platform.runLater(() -> {
                    try {
                        while (resultSet.next()) {
                            HBox historyCard = createHistoryCard(resultSet);
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

    private HBox createHistoryCard(ResultSet rs) throws Exception {
        int orderId = rs.getInt("id");
        String date = rs.getString("order_date");
        if (date != null && date.length() > 10) {
            date = date.substring(0, 10);
        }
        String buyerName = rs.getString("buyer_name");
        String cropName = rs.getString("crop_name");
        double quantity = rs.getDouble("quantity_kg");
        double price = rs.getDouble("price");
        String unit = rs.getString("unit");
        double totalPrice = quantity * price;
        String paymentStatus = rs.getString("payment_status");

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
                Platform.runLater(() -> {
                    try {
                        if (resultSet.next()) {
                            Alert alert = new Alert(Alert.AlertType.INFORMATION);
                            alert.setTitle("অর্ডার বিস্তারিত");
                            alert.setHeaderText("অর্ডার #" + orderId);
                            
                            double quantity = resultSet.getDouble("quantity_kg");
                            double price = resultSet.getDouble("price");
                            String unit = "কেজি";
                            
                            alert.setContentText(
                                "ফসল: " + resultSet.getString("crop_name") + "\n" +
                                "ক্রেতা: " + resultSet.getString("buyer_name") + "\n" +
                                "ফোন: " + resultSet.getString("buyer_phone") + "\n" +
                                "ঠিকানা: " + resultSet.getString("buyer_district") + "\n" +
                                "পরিমাণ: " + quantity + " " + unit + "\n" +
                                "দাম: ৳" + String.format("%.2f", price) + "/" + unit + "\n" +
                                "মোট আয়: ৳" + String.format("%.2f", quantity * price) + "\n" +
                                "অর্ডারের তারিখ: " + resultSet.getString("created_at") + "\n" +
                                "ডেলিভারির তারিখ: " + resultSet.getString("delivery_date")
                            );
                            alert.showAndWait();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
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
        loadSummaryStats();
        loadHistory();
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
