package com.sajid._207017_chashi_bhai.controllers;

import com.sajid._207017_chashi_bhai.App;
import com.sajid._207017_chashi_bhai.models.User;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.util.Optional;

/**
 * CropDetailController - Detailed crop view with photo carousel and farmer profile
 */
public class CropDetailController {

    @FXML private ImageView imgMainPhoto;
    @FXML private Button btnPrevPhoto;
    @FXML private Button btnNextPhoto;
    @FXML private HBox hboxThumbnails;
    @FXML private Label lblCropName;
    @FXML private Label lblCropPrice;
    @FXML private Label lblProductCode;
    @FXML private Button btnCopyCode;
    @FXML private Label lblCategory;
    @FXML private Label lblQuantity;
    @FXML private Label lblHarvestDate;
    @FXML private Label lblLocation;
    @FXML private Label lblTransport;
    @FXML private Label lblDescription;
    @FXML private ImageView imgFarmerPhoto;
    @FXML private Label lblFarmerName;
    @FXML private Label lblFarmerRating;
    @FXML private Label lblTotalReviews;
    @FXML private Label lblFarmerYears;
    @FXML private Label lblFarmerSales;
    @FXML private Label lblFarmerDistrict;
    @FXML private Button btnCall;
    @FXML private Button btnWhatsApp;
    @FXML private Button btnOrder;
    @FXML private Button btnFavorite;

    private User currentUser;
    private int cropId;
    private String productCode;
    private int farmerId;
    private String farmerPhone;
    private double cropPrice;
    private String cropUnit;
    private String[] photoPaths;
    private int currentPhotoIndex = 0;

    @FXML
    public void initialize() {
        currentUser = App.getCurrentUser();
        cropId = App.getCurrentCropId();
        System.out.println("Loading crop details for cropId: " + cropId);
        
        if (currentUser == null || !"buyer".equals(currentUser.getRole())) {
            showError("অ্যাক্সেস অস্বীকার", "শুধুমাত্র ক্রেতারা এই পেজ দেখতে পারবেন।");
            App.loadScene("login-view.fxml", "Login");
            return;
        }

        loadCropDetails();
        loadCropPhotos();
    }

    private void loadCropDetails() {
        // TODO: Replace with actual database call later
        // Hardcoded data for UI testing
        String name = "তাজা টমেটো";
        productCode = "CRP-20260108-0001"; // This will come from database
        String category = "সবজি";
        cropPrice = 45.0;
        cropUnit = "কেজি";
        double quantity = 500.0;
        String harvestDate = "2025-12-25";
        String district = "যশোর";
        String transport = "নিজস্ব পরিবহন ব্যবস্থা আছে";
        String description = "টাজা এবং উন্নতমানের টমেটো। রাসায়নিক মুক্ত। সরাসরি খামার থেকে।";

        lblCropName.setText(name);
        lblCropPrice.setText(String.format("৳%.2f/%s", cropPrice, cropUnit));
        lblProductCode.setText(productCode != null ? productCode : "N/A");
        lblCategory.setText(category);
        lblQuantity.setText(String.format("%.1f %s", quantity, cropUnit));
        lblHarvestDate.setText(harvestDate);
        lblLocation.setText(district);
        lblTransport.setText(transport);
        lblDescription.setText(description);

        // Farmer details (hardcoded)
        farmerId = 1;
        System.out.println("Farmer ID: " + farmerId);
        String farmerName = "আব্দুল করিম";
        farmerPhone = "01712345678";
        String farmerDistrict = "যশোর";
        boolean isVerified = true;
        int yearsFarming = 8;
        int totalSales = 145;
        double avgRating = 4.7;
        int totalReviews = 38;

        lblFarmerName.setText(farmerName + (isVerified ? " ✓" : ""));
        lblFarmerRating.setText(String.format("★ %.1f", avgRating));
        lblTotalReviews.setText("(" + totalReviews + " রিভিউ)");
        lblFarmerYears.setText(yearsFarming + " বছর");
        lblFarmerSales.setText(totalSales + " বিক্রয়");
        lblFarmerDistrict.setText(farmerDistrict);
    }

    private void loadCropPhotos() {
        // TODO: Replace with actual database call later
        // Hardcoded: No photos for now
        photoPaths = new String[0];
        loadThumbnails();
        btnPrevPhoto.setDisable(true);
        btnNextPhoto.setDisable(true);
    }

    private void loadPhoto(int index) {
        if (photoPaths != null && index >= 0 && index < photoPaths.length) {
            currentPhotoIndex = index;
            File photoFile = new File(photoPaths[index]);
            if (photoFile.exists()) {
                imgMainPhoto.setImage(new Image(photoFile.toURI().toString()));
            }

            // Enable/disable navigation buttons
            btnPrevPhoto.setDisable(index == 0);
            btnNextPhoto.setDisable(index == photoPaths.length - 1);
        }
    }

    private void loadThumbnails() {
        hboxThumbnails.getChildren().clear();
        for (int i = 0; i < photoPaths.length; i++) {
            final int photoIndex = i;
            File photoFile = new File(photoPaths[i]);
            if (photoFile.exists()) {
                ImageView thumbnail = new ImageView(new Image(photoFile.toURI().toString()));
                thumbnail.setFitWidth(80);
                thumbnail.setFitHeight(80);
                thumbnail.setPreserveRatio(true);
                thumbnail.setStyle("-fx-cursor: hand; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.3), 5, 0, 0, 0);");
                thumbnail.setOnMouseClicked(e -> loadPhoto(photoIndex));
                hboxThumbnails.getChildren().add(thumbnail);
            }
        }
    }

    @FXML
    private void onPrevPhoto() {
        if (currentPhotoIndex > 0) {
            loadPhoto(currentPhotoIndex - 1);
        }
    }

    @FXML
    private void onNextPhoto() {
        if (photoPaths != null && currentPhotoIndex < photoPaths.length - 1) {
            loadPhoto(currentPhotoIndex + 1);
        }
    }

    @FXML
    private void onCall() {
        try {
            Desktop.getDesktop().browse(new URI("tel:" + farmerPhone));
        } catch (Exception e) {
            showInfo("কল করুন", "ফোন নম্বর: " + farmerPhone);
        }
    }

    @FXML
    private void onWhatsApp() {
        try {
            String cleanPhone = farmerPhone.replaceAll("[^0-9]", "");
            if (!cleanPhone.startsWith("880")) {
                cleanPhone = "880" + cleanPhone;
            }
            Desktop.getDesktop().browse(new URI("https://wa.me/" + cleanPhone));
        } catch (Exception e) {
            showInfo("WhatsApp", "WhatsApp: " + farmerPhone);
        }
    }

    @FXML
    private void onOrder() {
        // Show quantity dialog
        TextInputDialog dialog = new TextInputDialog("1");
        dialog.setTitle("অর্ডার করুন");
        dialog.setHeaderText("আপনি কত পরিমাণ কিনতে চান?");
        dialog.setContentText("পরিমাণ (" + cropUnit + "):");

        Optional<String> result = dialog.showAndWait();
        if (result.isPresent()) {
            try {
                double quantity = Double.parseDouble(result.get());
                if (quantity <= 0) {
                    showError("ত্রুটি", "সঠিক পরিমাণ লিখুন।");
                    return;
                }

                double totalPrice = quantity * cropPrice;
                
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.setTitle("অর্ডার নিশ্চিত করুন");
                confirm.setHeaderText("অর্ডার বিস্তারিত");
                confirm.setContentText(
                    "পরিমাণ: " + quantity + " " + cropUnit + "\n" +
                    "মূল্য: ৳" + String.format("%.2f", totalPrice) + "\n\n" +
                    "আপনি কি এই অর্ডার নিশ্চিত করতে চান?"
                );

                Optional<ButtonType> confirmResult = confirm.showAndWait();
                if (confirmResult.isPresent() && confirmResult.get() == ButtonType.OK) {
                    placeOrder(quantity);
                }
            } catch (NumberFormatException e) {
                showError("ত্রুটি", "সঠিক পরিমাণ লিখুন।");
            }
        }
    }

    private void placeOrder(double quantity) {
        // TODO: Replace with actual database call later
        showSuccess("সফল!", "আপনার অর্ডার সফলভাবে সম্পন্ন হয়েছে। কৃষক শীঘ্রই যোগাযোগ করবেন।");
        // App.loadScene("buyer-orders-view.fxml", "আমার অর্ডারসমূহ");
    }

    @FXML
    private void onToggleFavorite() {
        showInfo("প্রিয়", "প্রিয় বৈশিষ্ট্য শীঘ্রই আসছে...");
    }

    @FXML
    private void onCopyProductCode() {
        if (productCode == null || productCode.isEmpty()) {
            showInfo("কোড নেই", "এই পণ্যের কোড পাওয়া যায়নি।");
            return;
        }
        try {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(productCode);
            clipboard.setContent(content);
            showInfo("কপি সফল", "পণ্য কোড কপি হয়েছে: " + productCode);
        } catch (Exception e) {
            showError("ত্রুটি", "কোড কপি করতে ব্যর্থ হয়েছে।");
            e.printStackTrace();
        }
    }

    @FXML
    private void onViewFarmerProfile() {
        showInfo("কৃষকের প্রোফাইল", "প্রোফাইল ভিউ শীঘ্রই আসছে...");
    }

    @FXML
    private void onBack() {
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
