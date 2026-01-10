package com.sajid._207017_chashi_bhai.controllers;

import com.sajid._207017_chashi_bhai.App;
import com.sajid._207017_chashi_bhai.models.User;
import com.sajid._207017_chashi_bhai.services.DatabaseService;
import com.sajid._207017_chashi_bhai.utils.StatisticsCalculator;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

/**
 * RateOrderDialogController - Dialog for buyers to rate farmers after order completion
 */
public class RateOrderDialogController {

    @FXML private Label lblOrderNumber;
    @FXML private Label lblFarmerName;
    @FXML private Label lblCropName;
    @FXML private Button btnStar1, btnStar2, btnStar3, btnStar4, btnStar5;
    @FXML private Label lblRatingText;
    @FXML private TextArea txtComment;
    @FXML private Label lblError;

    private int orderId;
    private int farmerId;
    private User currentUser;
    private Stage dialogStage;
    private int selectedRating = 0;
    private boolean ratingSubmitted = false;

    public void initialize() {
        currentUser = App.getCurrentUser();
        resetStars();
    }

    /**
     * Set order details for rating
     */
    public void setOrderDetails(int orderId, int farmerId, String orderNumber, String farmerName, String cropName) {
        this.orderId = orderId;
        this.farmerId = farmerId;
        lblOrderNumber.setText(orderNumber);
        lblFarmerName.setText(farmerName);
        lblCropName.setText(cropName);
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    public boolean isRatingSubmitted() {
        return ratingSubmitted;
    }

    @FXML
    private void onStar1() { setRating(1); }
    @FXML
    private void onStar2() { setRating(2); }
    @FXML
    private void onStar3() { setRating(3); }
    @FXML
    private void onStar4() { setRating(4); }
    @FXML
    private void onStar5() { setRating(5); }

    private void setRating(int rating) {
        selectedRating = rating;
        updateStarDisplay();
        
        String[] ratingTexts = {
            "",
            "খুব খারাপ / Very Poor",
            "খারাপ / Poor",
            "গড় / Average",
            "ভালো / Good",
            "চমৎকার / Excellent"
        };
        lblRatingText.setText(ratingTexts[rating]);
        lblError.setVisible(false);
    }

    private void updateStarDisplay() {
        resetStars();
        Button[] stars = {btnStar1, btnStar2, btnStar3, btnStar4, btnStar5};
        for (int i = 0; i < selectedRating; i++) {
            stars[i].setStyle("-fx-font-size: 32px; -fx-background-color: transparent; -fx-cursor: hand; -fx-text-fill: #FFD700;");
        }
    }

    private void resetStars() {
        Button[] stars = {btnStar1, btnStar2, btnStar3, btnStar4, btnStar5};
        for (Button star : stars) {
            star.setStyle("-fx-font-size: 32px; -fx-background-color: transparent; -fx-cursor: hand; -fx-opacity: 0.3;");
        }
    }

    @FXML
    private void onSubmit() {
        if (currentUser == null) {
            showError("দয়া করে লগইন করুন");
            return;
        }

        // Validate rating
        if (selectedRating == 0) {
            showError("দয়া করে রেটিং দিন");
            return;
        }

        String comment = txtComment.getText().trim();

        // Verify order ownership + status first
        String orderSql = "SELECT status, buyer_id, farmer_id FROM orders WHERE id = ?";
        DatabaseService.executeQueryAsync(orderSql, new Object[]{orderId},
            rs -> {
                try {
                    if (!rs.next()) {
                        Platform.runLater(() -> showError("অর্ডার খুঁজে পাওয়া যায়নি।"));
                        return;
                    }
                    String status = rs.getString("status");
                    int buyerId = rs.getInt("buyer_id");
                    int dbFarmerId = rs.getInt("farmer_id");

                    if (buyerId != currentUser.getId()) {
                        Platform.runLater(() -> showError("শুধুমাত্র এই অর্ডারের ক্রেতা রেটিং দিতে পারবেন।"));
                        return;
                    }
                    if (!"completed".equals(status)) {
                        Platform.runLater(() -> showError("শুধুমাত্র সম্পন্ন (completed) অর্ডারের জন্য রেটিং দেওয়া যাবে।"));
                        return;
                    }
                    if (farmerId <= 0) {
                        farmerId = dbFarmerId;
                    }

                    // Check if review already exists
                    String checkSql = "SELECT id FROM reviews WHERE order_id = ? AND reviewer_id = ?";
                    DatabaseService.executeQueryAsync(checkSql, new Object[]{orderId, currentUser.getId()},
                        rs2 -> {
                            try {
                                if (rs2.next()) {
                                    Platform.runLater(() -> showError("এই অর্ডারের জন্য আপনি ইতিমধ্যে রেটিং দিয়েছেন।"));
                                } else {
                                    insertReview(comment);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                                Platform.runLater(() -> showError("রেটিং চেক করতে ব্যর্থ হয়েছে।"));
                            }
                        },
                        error -> {
                            Platform.runLater(() -> showError("ডাটাবেস ত্রুটি।"));
                            error.printStackTrace();
                        }
                    );
                } catch (Exception e) {
                    e.printStackTrace();
                    Platform.runLater(() -> showError("অর্ডার যাচাই করতে ব্যর্থ হয়েছে।"));
                }
            },
            error -> {
                Platform.runLater(() -> showError("ডাটাবেস ত্রুটি।"));
                error.printStackTrace();
            }
        );
    }

    private void insertReview(String comment) {
        String insertSql = "INSERT INTO reviews (order_id, reviewer_id, reviewee_id, rating, comment) " +
                          "VALUES (?, ?, ?, ?, ?)";

        Object[] params = {
            orderId,
            currentUser.getId(),
            farmerId,
            selectedRating,
            comment.isEmpty() ? null : comment
        };

        DatabaseService.executeUpdateAsync(insertSql, params,
            rowsAffected -> {
                Platform.runLater(() -> {
                    ratingSubmitted = true;
                    showSuccess("সফল!", "আপনার রেটিং সফলভাবে জমা হয়েছে। ধন্যবাদ!");
                    
                    // Update farmer rating statistics
                    StatisticsCalculator.updateFarmerStatistics(farmerId);
                    
                    // Create notification for farmer
                    createNotification(farmerId, "নতুন রেটিং", 
                        currentUser.getName() + " আপনাকে " + selectedRating + " ⭐ রেটিং দিয়েছেন।");
                    
                    if (dialogStage != null) {
                        dialogStage.close();
                    }
                });
            },
            error -> {
                Platform.runLater(() -> {
                    String msg = error.getMessage() != null ? error.getMessage() : "";
                    if (msg.contains("UNIQUE") || msg.contains("unique")) {
                        showError("এই অর্ডারের জন্য আপনি ইতিমধ্যে রেটিং দিয়েছেন।");
                    } else {
                        showError("রেটিং জমা দিতে ব্যর্থ হয়েছে। আবার চেষ্টা করুন।");
                    }
                });
                error.printStackTrace();
            }
        );
    }

    @FXML
    private void onCancel() {
        if (dialogStage != null) {
            dialogStage.close();
        }
    }

    private void createNotification(int userId, String title, String message) {
        String sql = "INSERT INTO notifications (user_id, title, message, type) VALUES (?, ?, ?, 'review')";
        DatabaseService.executeUpdateAsync(sql, new Object[]{userId, title, message}, 
            result -> {}, 
            error -> error.printStackTrace()
        );
    }

    private void showError(String message) {
        lblError.setText("⚠ " + message);
        lblError.setVisible(true);
    }

    private void showSuccess(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
