package com.sajid._207017_chashi_bhai.controllers;

import com.sajid._207017_chashi_bhai.App;
import com.sajid._207017_chashi_bhai.models.User;
import com.sajid._207017_chashi_bhai.services.DatabaseService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.sql.ResultSet;

/**
 * FarmerHistoryController - Display completed sales history and analytics
 */
public class FarmerHistoryController {

    @FXML private Label lblTotalIncome;
    @FXML private Label lblMostSold;
    @FXML private Label lblTotalOrders;
    @FXML private ComboBox<String> cbFilterMonth;
    @FXML private ComboBox<String> cbFilterCrop;
    @FXML private Button btnApplyFilter;
    @FXML private Button btnExport;
    @FXML private VBox vboxHistoryList;
    @FXML private ProgressIndicator progressIndicator;

    private User currentUser;

    @FXML
    public void initialize() {
        currentUser = App.getCurrentUser();
        
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
        DatabaseService.executeQueryAsync(
            "SELECT " +
            "(SELECT COALESCE(SUM(o.quantity_kg * o.price_per_kg), 0) FROM orders o " +
            " WHERE o.farmer_id = ? AND o.status IN ('delivered', 'completed')) as total_income, " +
            "(SELECT c.name FROM orders o " +
            " WHERE o.farmer_id = ? AND o.status IN ('delivered', 'completed') " +
            " JOIN crops c ON o.crop_id = c.id " +
            " GROUP BY c.id ORDER BY COUNT(*) DESC LIMIT 1) as most_sold, " +
            "(SELECT COUNT(*) FROM orders o " +
            " WHERE o.farmer_id = ? AND o.status IN ('delivered', 'completed')) as total_orders",
            new Object[]{currentUser.getId(), currentUser.getId(), currentUser.getId()},
            resultSet -> {
                Platform.runLater(() -> {
                    try {
                        if (resultSet.next()) {
                            double income = resultSet.getDouble("total_income");
                            String mostSold = resultSet.getString("most_sold");
                            int totalOrders = resultSet.getInt("total_orders");

                            lblTotalIncome.setText(String.format("৳%.2f", income));
                            lblMostSold.setText(mostSold != null ? mostSold : "N/A");
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

        String query = "SELECT o.*, c.name as crop_name, c.price_per_kg as price, 'কেজি' as unit, u.name as buyer_name " +
                      "FROM orders o " +
                      "JOIN crops c ON o.crop_id = c.id " +
                      "JOIN users u ON o.buyer_id = u.id " +
                      "WHERE o.farmer_id = ? AND o.status IN ('delivered', 'completed') " +
                      "ORDER BY o.updated_at DESC";

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
        String date = rs.getString("updated_at").substring(0, 10);
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
            "SELECT o.*, c.name as crop_name, c.price, c.unit, u.name as buyer_name, u.phone as buyer_phone, u.district as buyer_district " +
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
                            
                            double quantity = resultSet.getDouble("quantity");
                            double price = resultSet.getDouble("price");
                            String unit = resultSet.getString("unit");
                            
                            alert.setContentText(
                                "ফসল: " + resultSet.getString("crop_name") + "\n" +
                                "ক্রেতা: " + resultSet.getString("buyer_name") + "\n" +
                                "ফোন: " + resultSet.getString("buyer_phone") + "\n" +
                                "ঠিকানা: " + resultSet.getString("buyer_district") + "\n" +
                                "পরিমাণ: " + quantity + " " + unit + "\n" +
                                "দাম: ৳" + String.format("%.2f", price) + "/" + unit + "\n" +
                                "মোট আয়: ৳" + String.format("%.2f", quantity * price) + "\n" +
                                "অর্ডারের তারিখ: " + resultSet.getString("created_at") + "\n" +
                                "ডেলিভারির তারিখ: " + resultSet.getString("updated_at")
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

    @FXML
    private void onExport() {
        showInfo("Export", "রপ্তানি বৈশিষ্ট্য শীঘ্রই আসছে...");
    }

    @FXML
    private void onBack() {
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
