package com.sajid._207017_chashi_bhai.controllers;

import com.sajid._207017_chashi_bhai.App;
import com.sajid._207017_chashi_bhai.models.User;
import com.sajid._207017_chashi_bhai.services.DatabaseService;
// import com.sajid._207017_chashi_bhai.services.FirebaseSyncService; // Removed - using REST API now
import com.sajid._207017_chashi_bhai.utils.DataSyncManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.util.Callback;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.sql.ResultSet;
import java.util.Collections;
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
    @FXML private TableColumn<HistoryRow, Void> colAction;

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

        // Ensure schema pieces exist (older DBs may miss these)
        ensureReviewsTableExists();

        // Set up table columns (new UI)
        if (tableHistory != null) {
            if (colDate != null) colDate.setCellValueFactory(new PropertyValueFactory<>("date"));
            if (colFarmer != null) colFarmer.setCellValueFactory(new PropertyValueFactory<>("farmer"));
            if (colCrop != null) colCrop.setCellValueFactory(new PropertyValueFactory<>("crop"));
            if (colQuantity != null) colQuantity.setCellValueFactory(new PropertyValueFactory<>("quantity"));
            if (colUnitPrice != null) colUnitPrice.setCellValueFactory(new PropertyValueFactory<>("unitPrice"));
            if (colTotalPrice != null) colTotalPrice.setCellValueFactory(new PropertyValueFactory<>("totalPrice"));
            if (colRating != null) colRating.setCellValueFactory(new PropertyValueFactory<>("rating"));

            if (colAction != null) {
                colAction.setCellFactory(new Callback<>() {
                    @Override
                    public TableCell<HistoryRow, Void> call(TableColumn<HistoryRow, Void> param) {
                        return new TableCell<>() {
                            private final Button btn = new Button("👁");

                            {
                                btn.getStyleClass().add("button-secondary");
                                btn.setMaxWidth(Double.MAX_VALUE);
                                btn.setTooltip(new Tooltip("বিস্তারিত দেখুন"));
                                btn.setOnAction(e -> {
                                    HistoryRow row = getTableView().getItems().get(getIndex());
                                    if (row != null) {
                                        showOrderDetails(row.getOrderId());
                                    }
                                });
                            }

                            @Override
                            protected void updateItem(Void item, boolean empty) {
                                super.updateItem(item, empty);
                                setGraphic(empty ? null : btn);
                            }
                        };
                    }
                });
            }

            tableHistory.setRowFactory(tv -> {
                TableRow<HistoryRow> row = new TableRow<>();
                row.setOnMouseClicked(event -> {
                    if (event.getClickCount() == 2 && !row.isEmpty()) {
                        HistoryRow item = row.getItem();
                        if (item != null) {
                            showOrderDetails(item.getOrderId());
                        }
                    }
                });
                return row;
            });

            tableHistory.setOnKeyPressed(event -> {
                if (event.getCode() == javafx.scene.input.KeyCode.ENTER) {
                    HistoryRow selected = tableHistory.getSelectionModel().getSelectedItem();
                    if (selected != null) {
                        showOrderDetails(selected.getOrderId());
                    }
                }
            });
        }

        loadSummaryStats();
        loadHistory();
        loadCropFilter();

        // Start real-time sync polling for history (every 30 seconds)
        syncManager.startPolling("buyer_history_" + currentUser.getId(), this::refreshHistory, 30);

        // Initial sync to pull latest remote changes immediately
        refreshHistory();
    }

    private void refreshHistory() {
        // TODO: Implement REST API sync for buyer orders
        // FirebaseSyncService has been removed - using REST API now
        // For now, just reload from local SQLite
        loadSummaryStats();
        loadHistory();
    }

    private void loadSummaryStats() {
        // Compute from live orders/reviews so UI always reflects DB state.

        // (1) Total expense + most bought crop (exclude cancelled/rejected)
        DatabaseService.executeQueryAsync(
            "SELECT " +
                "COALESCE(SUM(o.total_amount), 0) as total_expense, " +
                "(SELECT c.name " +
                    "FROM orders o2 " +
                    "JOIN crops c ON o2.crop_id = c.id " +
                    "WHERE o2.buyer_id = ? AND o2.status NOT IN ('cancelled','rejected') " +
                    "GROUP BY c.id " +
                    "ORDER BY COUNT(*) DESC " +
                    "LIMIT 1) as most_bought_crop " +
            "FROM orders o " +
            "WHERE o.buyer_id = ? AND o.status NOT IN ('cancelled','rejected')",
            new Object[]{currentUser.getId(), currentUser.getId()},
            resultSet -> {
                double totalExpense = 0.0;
                String mostBought = "N/A";
                try {
                    if (resultSet.next()) {
                        totalExpense = resultSet.getDouble("total_expense");
                        String value = resultSet.getString("most_bought_crop");
                        if (value != null && !value.isBlank()) {
                            mostBought = value;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                final double finalTotalExpense = totalExpense;
                final String finalMostBought = mostBought;
                Platform.runLater(() -> {
                    if (lblTotalExpense != null) {
                        lblTotalExpense.setText(String.format("৳%.2f", finalTotalExpense));
                    }
                    if (lblMostBought != null) {
                        lblMostBought.setText(finalMostBought);
                    }
                });
            },
            error -> error.printStackTrace()
        );

        // (2) Total orders (all except cancelled/rejected)
        DatabaseService.executeQueryAsync(
            "SELECT COUNT(*) as total_orders " +
                "FROM orders o " +
                "WHERE o.buyer_id = ? " +
                "AND o.status NOT IN ('cancelled','rejected')",
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

        // (3) Favorite farmers count (optional label; not in all FXML versions)
        DatabaseService.executeQueryAsync(
            "SELECT COUNT(DISTINCT o.farmer_id) as favorite_farmers " +
                "FROM orders o " +
                "WHERE o.buyer_id = ? AND o.status NOT IN ('cancelled','rejected')",
            new Object[]{currentUser.getId()},
            resultSet -> {
                int favoriteFarmers = 0;
                try {
                    if (resultSet.next()) {
                        favoriteFarmers = resultSet.getInt("favorite_farmers");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                final int finalFavoriteFarmers = favoriteFarmers;
                Platform.runLater(() -> {
                    if (lblFavoriteFarmers != null) {
                        lblFavoriteFarmers.setText(String.valueOf(finalFavoriteFarmers));
                    }
                });
            },
            error -> error.printStackTrace()
        );
    }

    private void loadCropFilter() {
        DatabaseService.executeQueryAsync(
            "SELECT DISTINCT c.name FROM orders o " +
            "JOIN crops c ON o.crop_id = c.id " +
            "WHERE o.buyer_id = ? AND o.status NOT IN ('cancelled','rejected') " +
            "ORDER BY c.name",
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
                            if (cbFilterCrop != null) {
                                String selected = cbFilterCrop.getValue();
                                cbFilterCrop.getItems().clear();
                                cbFilterCrop.getItems().add("সব ফসল (All Crops)");
                                cbFilterCrop.getItems().addAll(crops);

                                if (selected != null && cbFilterCrop.getItems().contains(selected)) {
                                    cbFilterCrop.getSelectionModel().select(selected);
                                } else {
                                    cbFilterCrop.getSelectionModel().select(0);
                                }
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

        String query = "SELECT o.id, o.crop_id, o.quantity_kg, o.price_per_kg, o.total_amount, " +
                      effectiveDateExpr + " as effective_date, " +
                      "c.name as crop_name, " +
                      "u.name as farmer_name, u.is_verified, " +
                      "(SELECT rating FROM reviews WHERE order_id = o.id AND reviewer_id = ? LIMIT 1) as my_review_rating " +
                      "FROM orders o " +
                      "JOIN crops c ON o.crop_id = c.id " +
                      "JOIN users u ON o.farmer_id = u.id " +
                      "WHERE o.buyer_id = ? ";

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
                query += " ORDER BY o.total_amount DESC";
            } else if (sortOption.contains("Low to High") || sortOption.contains("কম থেকে বেশি")) {
                query += " ORDER BY o.total_amount ASC";
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
                try {
                    java.util.List<HistoryRow> tableRows = new java.util.ArrayList<>();
                    java.util.List<HistoryCardData> cardRows = new java.util.ArrayList<>();
                    while (resultSet.next()) {
                        tableRows.add(createHistoryRow(resultSet));
                        cardRows.add(extractCardData(resultSet));
                    }

                    Platform.runLater(() -> {
                        try {
                            if (tableHistory != null) {
                                tableHistory.getItems().addAll(tableRows);
                            } else if (vboxHistoryList != null) {
                                for (HistoryCardData data : cardRows) {
                                    HBox historyCard = createHistoryCard(data);
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
        double unitPrice = rs.getDouble("price_per_kg");
        double total = rs.getDouble("total_amount");

        Object ratingObj = rs.getObject("my_review_rating");
        String rating = "-";
        if (ratingObj != null) {
            rating = String.valueOf(((Number) ratingObj).intValue());
        }

        int orderId = rs.getInt("id");

        return new HistoryRow(
            orderId,
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
        private final int orderId;
        private final String date;
        private final String crop;
        private final String farmer;
        private final String quantity;
        private final String unitPrice;
        private final String totalPrice;
        private final String rating;

        public HistoryRow(int orderId, String date, String crop, String farmer, String quantity, String unitPrice, String totalPrice, String rating) {
            this.orderId = orderId;
            this.date = date;
            this.crop = crop;
            this.farmer = farmer;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.totalPrice = totalPrice;
            this.rating = rating;
        }

        public int getOrderId() { return orderId; }
        public String getDate() { return date; }
        public String getCrop() { return crop; }
        public String getFarmer() { return farmer; }
        public String getQuantity() { return quantity; }
        public String getUnitPrice() { return unitPrice; }
        public String getTotalPrice() { return totalPrice; }
        public String getRating() { return rating; }
    }

    private static final class HistoryCardData {
        final int orderId;
        final String date;
        final String farmerName;
        final boolean isVerified;
        final String cropName;
        final double quantity;
        final double price;
        final Integer myRating;

        HistoryCardData(int orderId, String date, String farmerName, boolean isVerified,
                        String cropName, double quantity, double price, Integer myRating) {
            this.orderId = orderId;
            this.date = date;
            this.farmerName = farmerName;
            this.isVerified = isVerified;
            this.cropName = cropName;
            this.quantity = quantity;
            this.price = price;
            this.myRating = myRating;
        }
    }

    private HistoryCardData extractCardData(ResultSet rs) throws Exception {
        int orderId = rs.getInt("id");
        String dateValue = rs.getString("effective_date");
        String date = (dateValue != null && dateValue.length() >= 10) ? dateValue.substring(0, 10) : "—";
        String farmerName = rs.getString("farmer_name");
        boolean isVerified = rs.getBoolean("is_verified");
        String cropName = rs.getString("crop_name");
        double quantity = rs.getDouble("quantity_kg");
        double price = rs.getDouble("price_per_kg");
        Object myRatingObj = rs.getObject("my_review_rating");
        Integer myRating = myRatingObj != null ? ((Number) myRatingObj).intValue() : null;

        return new HistoryCardData(orderId, date, farmerName, isVerified, cropName, quantity, price, myRating);
    }

    private HBox createHistoryCard(HistoryCardData data) throws Exception {
        int orderId = data.orderId;
        String date = data.date;
        String farmerName = data.farmerName;
        boolean isVerified = data.isVerified;
        String cropName = data.cropName;
        double quantity = data.quantity;
        double price = data.price;
        String unit = "কেজি"; // All crops measured in kg
        double totalPrice = quantity * price;
        Integer myRating = data.myRating;

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
            "SELECT o.id, o.quantity_kg, o.price_per_kg, o.total_amount, o.created_at, " +
                "COALESCE(o.completed_at, o.delivered_at, o.updated_at, o.created_at) as effective_date, " +
                "c.name as crop_name, " +
                "u.name as farmer_name, u.phone as farmer_phone " +
            "FROM orders o " +
            "JOIN crops c ON o.crop_id = c.id " +
            "JOIN users u ON o.farmer_id = u.id " +
            "WHERE o.id = ? AND o.buyer_id = ?",
            new Object[]{orderId, currentUser.getId()},
            resultSet -> {
                boolean found = false;
                String cropName = null;
                String farmerName = null;
                String farmerPhone = null;
                String createdAt = null;
                String effectiveDate = null;
                double quantity = 0.0;
                double unitPrice = 0.0;
                double totalAmount = 0.0;
                try {
                    if (resultSet.next()) {
                        found = true;
                        cropName = resultSet.getString("crop_name");
                        farmerName = resultSet.getString("farmer_name");
                        farmerPhone = resultSet.getString("farmer_phone");
                        createdAt = resultSet.getString("created_at");
                        effectiveDate = resultSet.getString("effective_date");
                        quantity = resultSet.getDouble("quantity_kg");
                        unitPrice = resultSet.getDouble("price_per_kg");
                        totalAmount = resultSet.getDouble("total_amount");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                final boolean finalFound = found;
                final String finalCropName = cropName;
                final String finalFarmerName = farmerName;
                final String finalFarmerPhone = farmerPhone;
                final String finalCreatedAt = createdAt;
                final String finalEffectiveDate = effectiveDate;
                final double finalQuantity = quantity;
                final double finalUnitPrice = unitPrice;
                final double finalTotalAmount = totalAmount;
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
                        "কৃষক: " + (finalFarmerName != null ? finalFarmerName : "") + "\n" +
                        "ফোন: " + (finalFarmerPhone != null ? finalFarmerPhone : "") + "\n" +
                        "পরিমাণ: " + String.format("%.1f", finalQuantity) + " " + unit + "\n" +
                        "দাম: ৳" + String.format("%.2f", finalUnitPrice) + "/" + unit + "\n" +
                        "মোট খরচ: ৳" + String.format("%.2f", finalTotalAmount) + "\n" +
                        "অর্ডারের তারিখ: " + (finalCreatedAt != null ? finalCreatedAt : "") + "\n" +
                        "ডেলিভারির/সম্পন্নের তারিখ: " + (finalEffectiveDate != null ? finalEffectiveDate : "")
                    );

                    ButtonType viewDetails = new ButtonType("👁 বিস্তারিত দেখুন", ButtonBar.ButtonData.OK_DONE);
                    ButtonType close = new ButtonType("বন্ধ", ButtonBar.ButtonData.CANCEL_CLOSE);
                    alert.getButtonTypes().setAll(viewDetails, close);

                    alert.showAndWait().ifPresent(choice -> {
                        if (choice == viewDetails) {
                            App.setCurrentOrderId(orderId);
                            App.setPreviousScene("buyer-history-view.fxml");
                            App.loadScene("order-detail-view.fxml", "অর্ডার বিস্তারিত");
                        }
                    });
                });
            },
            error -> error.printStackTrace()
        );
    }

    private void ensureReviewsTableExists() {
        // Older local DBs may be missing the reviews table (was previously called ratings in some code).
        String createTable =
            "CREATE TABLE IF NOT EXISTS reviews (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "order_id INTEGER NOT NULL, " +
                "reviewer_id INTEGER NOT NULL, " +
                "reviewee_id INTEGER NOT NULL, " +
                "rating INTEGER NOT NULL CHECK(rating >= 1 AND rating <= 5), " +
                "comment TEXT, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE, " +
                "FOREIGN KEY (reviewer_id) REFERENCES users(id) ON DELETE CASCADE, " +
                "FOREIGN KEY (reviewee_id) REFERENCES users(id) ON DELETE CASCADE, " +
                "UNIQUE(order_id, reviewer_id)" +
            ")";

        DatabaseService.executeUpdateAsync(createTable, new Object[]{}, rows -> {
            // no-op
        }, err -> {
            // If this fails, history will still work, just without rating info.
            err.printStackTrace();
        });
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
        // Store ratings in the schema-supported `reviews` table.
        DatabaseService.executeQueryAsync(
            "SELECT farmer_id FROM orders WHERE id = ? AND buyer_id = ?",
            new Object[]{orderId, currentUser.getId()},
            resultSet -> {
                int farmerId = 0;
                boolean found = false;
                try {
                    if (resultSet.next()) {
                        farmerId = resultSet.getInt("farmer_id");
                        found = true;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (!found) {
                    Platform.runLater(() -> showError("ত্রুটি", "অর্ডারটি পাওয়া যায়নি।"));
                    return;
                }

                DatabaseService.executeUpdateAsync(
                    "INSERT OR REPLACE INTO reviews (order_id, reviewer_id, reviewee_id, rating, comment, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, datetime('now'))",
                    new Object[]{orderId, currentUser.getId(), farmerId, rating, comment},
                    rows -> Platform.runLater(() -> {
                        showSuccess("সফল!", "আপনার রেটিং জমা দেওয়া হয়েছে।");
                        loadHistory();
                    }),
                    error -> Platform.runLater(() -> {
                        showError("ত্রুটি", "রেটিং জমা দিতে সমস্যা হয়েছে।");
                        error.printStackTrace();
                    })
                );
            },
            error -> error.printStackTrace()
        );
    }

    private void reorder(int orderId) {
        DatabaseService.executeQueryAsync(
            "SELECT crop_id, quantity_kg FROM orders WHERE id = ?",
            new Object[]{orderId},
            resultSet -> {
                int cropId = 0;
                boolean found = false;
                try {
                    if (resultSet.next()) {
                        cropId = resultSet.getInt("crop_id");
                        found = true;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                final int finalCropId = cropId;
                final boolean finalFound = found;
                Platform.runLater(() -> {
                    if (!finalFound) {
                        showError("ত্রুটি", "অর্ডারটি পাওয়া যায়নি।");
                        return;
                    }
                    App.setCurrentCropId(finalCropId);
                    App.setPreviousScene("buyer-history-view.fxml");
                    App.loadScene("crop-detail-view.fxml", "ফসলের বিস্তারিত");
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
