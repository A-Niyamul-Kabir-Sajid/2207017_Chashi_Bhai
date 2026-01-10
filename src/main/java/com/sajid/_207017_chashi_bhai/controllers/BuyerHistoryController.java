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
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * BuyerHistoryController - Display completed purchases with analytics
 */
public class BuyerHistoryController {

    @FXML private Label lblTotalExpense;
    @FXML private Label lblMostBought;
    @FXML private Label lblFavoriteFarmers;
    @FXML private Label lblTotalAcceptedOrders;
    @FXML private ComboBox<String> cbFilterMonth;
    @FXML private ComboBox<String> cbFilterCrop;
    @FXML private ComboBox<String> cbSortBy;
    @FXML private Button btnApplyFilter;
    @FXML private Button btnExport;
    @FXML private VBox vboxHistoryList;
    @FXML private ProgressIndicator progressIndicator;

    // New (current FXML) table-based UI
    @FXML private TableView<HistoryRow> tableHistory;
    @FXML private TableColumn<HistoryRow, String> colDate;
    @FXML private TableColumn<HistoryRow, String> colFarmer;
    @FXML private TableColumn<HistoryRow, String> colCrop;
    @FXML private TableColumn<HistoryRow, String> colQuantity;
    @FXML private TableColumn<HistoryRow, String> colUnitPrice;
    @FXML private TableColumn<HistoryRow, String> colTotalPrice;
    @FXML private TableColumn<HistoryRow, String> colRating;

    private User currentUser;
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

        // Ensure sensible defaults (FXML may already provide items)
        if (cbFilterMonth != null && cbFilterMonth.getValue() == null && !cbFilterMonth.getItems().isEmpty()) {
            cbFilterMonth.getSelectionModel().select(0);
        }
        if (cbFilterCrop != null && cbFilterCrop.getValue() == null && !cbFilterCrop.getItems().isEmpty()) {
            cbFilterCrop.getSelectionModel().select(0);
        }
        
        // Initialize sort dropdown with default selection
        if (cbSortBy != null) {
            if (cbSortBy.getSelectionModel().getSelectedItem() == null && !cbSortBy.getItems().isEmpty()) {
                cbSortBy.getSelectionModel().select(0);
            }
            cbSortBy.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null) {
                    loadHistory();
                }
            });
        }

        // Wire buttons in case FXML doesn't
        if (btnApplyFilter != null) {
            btnApplyFilter.setOnAction(e -> loadHistory());
        }
        if (btnExport != null) {
            btnExport.setOnAction(e -> onExport());
        }

        // Set up table columns (new UI)
        if (tableHistory != null) {
            if (colDate != null) colDate.setCellValueFactory(new PropertyValueFactory<>("date"));
            if (colFarmer != null) colFarmer.setCellValueFactory(new PropertyValueFactory<>("farmer"));
            if (colCrop != null) colCrop.setCellValueFactory(new PropertyValueFactory<>("crop"));
            if (colQuantity != null) colQuantity.setCellValueFactory(new PropertyValueFactory<>("quantity"));
            if (colUnitPrice != null) colUnitPrice.setCellValueFactory(new PropertyValueFactory<>("unitPrice"));
            if (colTotalPrice != null) colTotalPrice.setCellValueFactory(new PropertyValueFactory<>("totalPrice"));
            if (colRating != null) colRating.setCellValueFactory(new PropertyValueFactory<>("rating"));
        }

        loadSummaryStats();
        loadHistory();
        loadCropFilter();

        // Start real-time sync polling for history (every 30 seconds)
        syncManager.startPolling("buyer_history_" + currentUser.getId(), this::refreshHistory, 30);
    }

    private void refreshHistory() {
        FirebaseSyncService.getInstance().syncBuyerOrdersFromFirebase(
            currentUser.getId(),
            () -> {
                loadSummaryStats();
                loadHistory();
            }
        );
    }

    private void loadSummaryStats() {
        // Load total expense and total orders from user statistics
        DatabaseService.executeQueryAsync(
            "SELECT total_expense, total_buyer_orders, most_bought_crop FROM users WHERE id = ?",
            new Object[]{currentUser.getId()},
            resultSet -> {
                try {
                    if (resultSet.next()) {
                        // Read data BEFORE Platform.runLater
                        double expense = resultSet.getDouble("total_expense");
                        int totalOrders = resultSet.getInt("total_buyer_orders");
                        String mostBought = resultSet.getString("most_bought_crop");
                        
                        Platform.runLater(() -> {
                            if (lblTotalExpense != null) {
                                lblTotalExpense.setText(String.format("৳%.2f", expense));
                            }
                            if (lblTotalAcceptedOrders != null) {
                                lblTotalAcceptedOrders.setText(String.valueOf(totalOrders));
                            }
                            if (lblMostBought != null) {
                                lblMostBought.setText(mostBought != null ? mostBought : "N/A");
                            }
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            },
            error -> error.printStackTrace()
        );
        
        // Load favorite farmers count
        DatabaseService.executeQueryAsync(
            "SELECT COUNT(DISTINCT c.farmer_id) as favorite_farmers FROM orders o " +
            "JOIN crops c ON o.crop_id = c.id " +
            "WHERE o.buyer_id = ? AND o.status IN ('delivered', 'completed')",
            new Object[]{currentUser.getId()},
            resultSet -> {
                try {
                    if (resultSet.next()) {
                        int favoriteFarmers = resultSet.getInt("favorite_farmers");
                        
                        Platform.runLater(() -> {
                            if (lblFavoriteFarmers != null) {
                                lblFavoriteFarmers.setText(String.valueOf(favoriteFarmers));
                            }
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            },
            error -> error.printStackTrace()
        );
    }

    private void loadCropFilter() {
        DatabaseService.executeQueryAsync(
            "SELECT DISTINCT c.name FROM orders o " +
            "JOIN crops c ON o.crop_id = c.id " +
            "WHERE o.buyer_id = ? AND o.status IN ('delivered', 'completed') " +
            "ORDER BY c.name",
            new Object[]{currentUser.getId()},
            resultSet -> {
                Platform.runLater(() -> {
                    try {
                        if (cbFilterCrop != null) {
                            String selected = cbFilterCrop.getValue();
                            cbFilterCrop.getItems().clear();
                            cbFilterCrop.getItems().add("সব ফসল (All Crops)");
                            cbFilterCrop.getSelectionModel().select(0);
                            if (selected != null && !selected.contains("All") && !selected.contains("সব")) {
                                // selection will be restored if it exists in the new list
                            }
                        }
                        while (resultSet.next()) {
                            if (cbFilterCrop != null) {
                                cbFilterCrop.getItems().add(resultSet.getString("name"));
                            }
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

        if (vboxHistoryList != null) {
            vboxHistoryList.getChildren().clear();
        }
        if (tableHistory != null) {
            tableHistory.getItems().clear();
        }

        String effectiveDateExpr = "COALESCE(o.completed_at, o.delivered_at, o.updated_at, o.created_at)";

        String query = "SELECT o.id, o.crop_id, o.quantity_kg, o.updated_at, " +
                  effectiveDateExpr + " as effective_date, " +
                      "c.name as crop_name, c.price_per_kg as price, " +
                      "u.name as farmer_name, u.is_verified, " +
                      "(SELECT rating FROM reviews WHERE order_id = o.id AND reviewer_id = ? LIMIT 1) as my_review_rating " +
                      "FROM orders o " +
                      "JOIN crops c ON o.crop_id = c.id " +
                      "JOIN users u ON c.farmer_id = u.id " +
                      "WHERE o.buyer_id = ? AND o.status IN ('delivered', 'completed') ";

        List<Object> params = new ArrayList<>();
        params.add(currentUser.getId()); // reviewer_id
        params.add(currentUser.getId()); // buyer_id

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
                query += " ORDER BY (o.quantity_kg * c.price_per_kg) DESC";
            } else if (sortOption.contains("Low to High") || sortOption.contains("কম থেকে বেশি")) {
                query += " ORDER BY (o.quantity_kg * c.price_per_kg) ASC";
            } else {
                query += " ORDER BY " + effectiveDateExpr + " DESC";
            }
        } else {
            query += " ORDER BY " + effectiveDateExpr + " DESC";
        }

        DatabaseService.executeQueryAsync(
            query,
            params.toArray(),
            resultSet -> {
                Platform.runLater(() -> {
                    try {
                        while (resultSet.next()) {
                            if (tableHistory != null) {
                                tableHistory.getItems().add(createHistoryRow(resultSet));
                            } else if (vboxHistoryList != null) {
                                HBox historyCard = createHistoryCard(resultSet);
                                vboxHistoryList.getChildren().add(historyCard);
                            }
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

    private HistoryRow createHistoryRow(ResultSet rs) throws Exception {
        String effectiveDate = rs.getString("effective_date");
        String date = (effectiveDate != null && effectiveDate.length() >= 10) ? effectiveDate.substring(0, 10) : "";

        String farmerName = rs.getString("farmer_name");
        boolean isVerified = rs.getBoolean("is_verified");
        String farmer = farmerName != null ? farmerName : "";
        if (isVerified && !farmer.isBlank()) {
            farmer = farmer + " ✓";
        }

        String cropName = rs.getString("crop_name");
        double quantity = rs.getDouble("quantity_kg");
        double unitPrice = rs.getDouble("price");
        double total = quantity * unitPrice;

        Object ratingObj = rs.getObject("my_review_rating");
        String rating = "-";
        if (ratingObj != null) {
            rating = String.valueOf(((Number) ratingObj).intValue());
        }

        return new HistoryRow(
            date,
            cropName != null ? cropName : "",
            farmer,
            String.format("%.1f কেজি", quantity),
            String.format("৳%.2f", unitPrice),
            String.format("৳%.2f", total),
            rating
        );
    }

    public static class HistoryRow {
        private final String date;
        private final String crop;
        private final String farmer;
        private final String quantity;
        private final String unitPrice;
        private final String totalPrice;
        private final String rating;

        public HistoryRow(String date, String crop, String farmer, String quantity, String unitPrice, String totalPrice, String rating) {
            this.date = date;
            this.crop = crop;
            this.farmer = farmer;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.totalPrice = totalPrice;
            this.rating = rating;
        }

        public String getDate() { return date; }
        public String getCrop() { return crop; }
        public String getFarmer() { return farmer; }
        public String getQuantity() { return quantity; }
        public String getUnitPrice() { return unitPrice; }
        public String getTotalPrice() { return totalPrice; }
        public String getRating() { return rating; }
    }

    private HBox createHistoryCard(ResultSet rs) throws Exception {
        int orderId = rs.getInt("id");
        String date = rs.getString("updated_at").substring(0, 10);
        String farmerName = rs.getString("farmer_name");
        boolean isVerified = rs.getBoolean("is_verified");
        String cropName = rs.getString("crop_name");
        double quantity = rs.getDouble("quantity_kg");
        double price = rs.getDouble("price");
        String unit = "কেজি"; // All crops measured in kg
        double totalPrice = quantity * price;
        Object myRatingObj = rs.getObject("my_review_rating");
        Integer myRating = myRatingObj != null ? ((Number) myRatingObj).intValue() : null;

        HBox card = new HBox(20);
        card.getStyleClass().add("history-card");
        card.setPadding(new Insets(15));

        // Date column
        VBox dateBox = new VBox(5);
        dateBox.setPrefWidth(120);
        Label lblDate = new Label(date);
        lblDate.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        dateBox.getChildren().add(lblDate);

        // Farmer column
        VBox farmerBox = new VBox(5);
        farmerBox.setPrefWidth(180);
        Label lblFarmerTitle = new Label("কৃষক:");
        lblFarmerTitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #888;");
        
        HBox farmerNameBox = new HBox(5);
        Label lblFarmer = new Label(farmerName);
        lblFarmer.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        if (isVerified) {
            Label lblVerified = new Label("✓");
            lblVerified.getStyleClass().add("verified-badge-tiny");
            lblVerified.setStyle("-fx-text-fill: #4CAF50; -fx-font-weight: bold;");
            farmerNameBox.getChildren().addAll(lblFarmer, lblVerified);
        } else {
            farmerNameBox.getChildren().add(lblFarmer);
        }
        farmerBox.getChildren().addAll(lblFarmerTitle, farmerNameBox);

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
        Label lblPriceTitle = new Label("মোট খরচ:");
        lblPriceTitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #888;");
        Label lblPrice = new Label(String.format("৳%.2f", totalPrice));
        lblPrice.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #4CAF50;");
        priceBox.getChildren().addAll(lblPriceTitle, lblPrice);

        // Rating column
        VBox ratingBox = new VBox(5);
        ratingBox.setPrefWidth(120);
        Label lblRatingTitle = new Label("রেটিং:");
        lblRatingTitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #888;");
        
        if (myRating != null) {
            StringBuilder stars = new StringBuilder();
            for (int i = 0; i < myRating; i++) {
                stars.append("★");
            }
            for (int i = myRating; i < 5; i++) {
                stars.append("☆");
            }
            Label lblRating = new Label(stars.toString());
            lblRating.setStyle("-fx-font-size: 16px; -fx-text-fill: #FFA726;");
            ratingBox.getChildren().addAll(lblRatingTitle, lblRating);
        } else {
            Button btnRate = new Button("রেটিং দিন");
            btnRate.getStyleClass().add("button-secondary");
            btnRate.setOnAction(e -> showRatingDialog(orderId));
            ratingBox.getChildren().addAll(lblRatingTitle, btnRate);
        }

        // Action buttons
        VBox actionBox = new VBox(5);
        actionBox.setPrefWidth(100);
        
        HBox iconBox = new HBox(10);
        Button btnView = new Button("👁");
        btnView.getStyleClass().add("icon-button");
        btnView.setOnAction(e -> showOrderDetails(orderId));
        
        Button btnReorder = new Button("🔁");
        btnReorder.getStyleClass().add("icon-button");
        btnReorder.setOnAction(e -> reorder(orderId));
        
        iconBox.getChildren().addAll(btnView, btnReorder);
        actionBox.getChildren().add(iconBox);

        card.getChildren().addAll(dateBox, farmerBox, cropBox, quantityBox, priceBox, ratingBox, actionBox);
        return card;
    }

    private void showOrderDetails(int orderId) {
        DatabaseService.executeQueryAsync(
            "SELECT o.*, c.name as crop_name, c.price_per_kg as price, c.farmer_id, " +
            "u.name as farmer_name, u.phone as farmer_phone " +
            "FROM orders o " +
            "JOIN crops c ON o.crop_id = c.id " +
            "JOIN users u ON c.farmer_id = u.id " +
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
                            String unit = "কেজি"; // All crops measured in kg
                            
                            alert.setContentText(
                                "ফসল: " + resultSet.getString("crop_name") + "\n" +
                                "কৃষক: " + resultSet.getString("farmer_name") + "\n" +
                                "ফোন: " + resultSet.getString("farmer_phone") + "\n" +
                                "পরিমাণ: " + quantity + " " + unit + "\n" +
                                "দাম: ৳" + String.format("%.2f", price) + "/" + unit + "\n" +
                                "মোট খরচ: ৳" + String.format("%.2f", quantity * price) + "\n" +
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

    private void showRatingDialog(int orderId) {
        Dialog<Integer> dialog = new Dialog<>();
        dialog.setTitle("রেটিং দিন");
        dialog.setHeaderText("এই অর্ডারের জন্য রেটিং দিন");

        ButtonType submitButton = new ButtonType("জমা দিন", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(submitButton, ButtonType.CANCEL);

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        Label lblQuestion = new Label("আপনার অভিজ্ঞতা কেমন ছিল?");
        
        HBox starsBox = new HBox(10);
        ToggleGroup ratingGroup = new ToggleGroup();
        for (int i = 1; i <= 5; i++) {
            final int rating = i;
            ToggleButton star = new ToggleButton(String.valueOf(i) + " ★");
            star.setToggleGroup(ratingGroup);
            star.setUserData(rating);
            starsBox.getChildren().add(star);
        }

        TextArea txtComment = new TextArea();
        txtComment.setPromptText("মন্তব্য লিখুন (ঐচ্ছিক)");
        txtComment.setPrefRowCount(3);

        content.getChildren().addAll(lblQuestion, starsBox, new Label("মন্তব্য:"), txtComment);
        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == submitButton) {
                Toggle selected = ratingGroup.getSelectedToggle();
                if (selected != null) {
                    return (Integer) selected.getUserData();
                }
            }
            return null;
        });

        dialog.showAndWait().ifPresent(rating -> {
            String comment = txtComment.getText().trim();
            submitRating(orderId, rating, comment);
        });
    }

    private void submitRating(int orderId, int rating, String comment) {
        // Get farmer_id from order
        DatabaseService.executeQueryAsync(
            "SELECT c.farmer_id FROM orders o JOIN crops c ON o.crop_id = c.id WHERE o.id = ?",
            new Object[]{orderId},
            resultSet -> {
                try {
                    if (resultSet.next()) {
                        int farmerId = resultSet.getInt("farmer_id");
                        
                        DatabaseService.executeUpdateAsync(
                            "INSERT INTO ratings (order_id, buyer_id, farmer_id, rating, comment, created_at) " +
                            "VALUES (?, ?, ?, ?, ?, datetime('now'))",
                            new Object[]{orderId, currentUser.getId(), farmerId, rating, comment},
                            rows -> {
                                Platform.runLater(() -> {
                                    showSuccess("সফল!", "আপনার রেটিং জমা দেওয়া হয়েছে।");
                                    loadHistory();
                                });
                            },
                            error -> {
                                Platform.runLater(() -> {
                                    showError("ত্রুটি", "রেটিং জমা দিতে সমস্যা হয়েছে।");
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

    private void reorder(int orderId) {
        DatabaseService.executeQueryAsync(
            "SELECT crop_id, quantity_kg FROM orders WHERE id = ?",
            new Object[]{orderId},
            resultSet -> {
                Platform.runLater(() -> {
                    try {
                        if (resultSet.next()) {
                            int cropId = resultSet.getInt("crop_id");
                            App.setCurrentCropId(cropId);
                            App.setPreviousScene("buyer-history-view.fxml");
                            App.loadScene("crop-detail-view.fxml", "ফসলের বিস্তারিত");
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
        loadHistory();
    }

    @FXML
    private void onExport() {
        showInfo("Export", "রপ্তানি বৈশিষ্ট্য শীঘ্রই আসছে...");
    }

    @FXML
    private void onBack() {
        if (syncManager != null && currentUser != null) {
            syncManager.stopPolling("buyer_history_" + currentUser.getId());
        }
        App.loadScene("buyer-dashboard-view.fxml", "Dashboard");
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
