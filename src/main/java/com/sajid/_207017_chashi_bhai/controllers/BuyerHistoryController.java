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
 * BuyerHistoryController - Display completed purchases with analytics
 */
public class BuyerHistoryController {

    @FXML private Label lblTotalExpense;
    @FXML private Label lblMostBought;
    @FXML private Label lblFavoriteFarmers;
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
        
        if (currentUser == null || !"buyer".equals(currentUser.getRole())) {
            showError("অ্যাক্সেস অস্বীকার", "শুধুমাত্র ক্রেতারা এই পেজ দেখতে পারবেন।");
            App.loadScene("login-view.fxml", "Login");
            return;
        }

        // Initialize filters
        cbFilterMonth.getItems().addAll("সকল সময়", "এই মাস", "গত মাস", "গত ৩ মাস", "গত ৬ মাস", "এই বছর");
        cbFilterMonth.setValue("সকল সময়");
        cbFilterCrop.getItems().add("সকল ফসল");
        cbFilterCrop.setValue("সকল ফসল");

        loadSummaryStats();
        loadHistory();
        loadCropFilter();
    }

    private void loadSummaryStats() {
        DatabaseService.executeQueryAsync(
            "SELECT " +
            "(SELECT COALESCE(SUM(o.quantity_kg * o.price_per_kg), 0) FROM orders o " +
            " WHERE o.buyer_id = ? AND o.status IN ('delivered', 'completed')) as total_expense, " +
            "(SELECT c.name FROM orders o " +
            " JOIN crops c ON o.crop_id = c.id " +
            " WHERE o.buyer_id = ? AND o.status IN ('delivered', 'completed') " +
            " GROUP BY c.name ORDER BY COUNT(*) DESC LIMIT 1) as most_bought, " +
            "(SELECT COUNT(DISTINCT c.farmer_id) FROM orders o " +
            " JOIN crops c ON o.crop_id = c.id " +
            " WHERE o.buyer_id = ? AND o.status IN ('delivered', 'completed')) as favorite_farmers, " +
            "(SELECT COUNT(*) FROM orders WHERE buyer_id = ? AND status IN ('delivered', 'completed')) as total_orders",
            new Object[]{currentUser.getId(), currentUser.getId(), currentUser.getId(), currentUser.getId()},
            resultSet -> {
                Platform.runLater(() -> {
                    try {
                        if (resultSet.next()) {
                            double expense = resultSet.getDouble("total_expense");
                            String mostBought = resultSet.getString("most_bought");
                            int favoriteFarmers = resultSet.getInt("favorite_farmers");
                            int totalOrders = resultSet.getInt("total_orders");

                            lblTotalExpense.setText(String.format("৳%.2f", expense));
                            lblMostBought.setText(mostBought != null ? mostBought : "N/A");
                            lblFavoriteFarmers.setText(String.valueOf(favoriteFarmers));
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

    private void loadCropFilter() {
        DatabaseService.executeQueryAsync(
            "SELECT DISTINCT c.name FROM orders o " +
            "JOIN crops c ON o.crop_id = c.id " +
            "WHERE o.buyer_id = ? AND o.status = 'delivered' " +
            "ORDER BY c.name",
            new Object[]{currentUser.getId()},
            resultSet -> {
                Platform.runLater(() -> {
                    try {
                        while (resultSet.next()) {
                            cbFilterCrop.getItems().add(resultSet.getString("name"));
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

        String query = "SELECT o.*, c.name as crop_name, c.price_per_kg as price, 'কেজি' as unit, " +
                      "u.name as farmer_name, u.is_verified, " +
                      "(SELECT rating FROM ratings WHERE order_id = o.id LIMIT 1) as my_rating, " +
                      "(SELECT rating FROM reviews WHERE order_id = o.id AND reviewer_id = ? LIMIT 1) as my_review_rating " +
                      "FROM orders o " +
                      "JOIN crops c ON o.crop_id = c.id " +
                      "JOIN users u ON c.farmer_id = u.id " +
                      "WHERE o.buyer_id = ? AND o.status IN ('delivered', 'completed') " +
                      "ORDER BY o.updated_at DESC";

        DatabaseService.executeQueryAsync(
            query,
            new Object[]{currentUser.getId(), currentUser.getId()},
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
            "SELECT crop_id, quantity FROM orders WHERE id = ?",
            new Object[]{orderId},
            resultSet -> {
                Platform.runLater(() -> {
                    try {
                        if (resultSet.next()) {
                            int cropId = resultSet.getInt("crop_id");
                            App.setCurrentCropId(cropId);
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
