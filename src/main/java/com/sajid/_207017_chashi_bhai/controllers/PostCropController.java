package com.sajid._207017_chashi_bhai.controllers;

import com.sajid._207017_chashi_bhai.App;
import com.sajid._207017_chashi_bhai.models.User;
import com.sajid._207017_chashi_bhai.services.DatabaseService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * PostCropController - Create new crop listing with photo uploads
 */
public class PostCropController {

    @FXML private TextField txtCropName;
    @FXML private ComboBox<String> cbCategory;
    @FXML private TextField txtPrice;
    @FXML private ComboBox<String> cbUnit;
    @FXML private TextField txtQuantity;
    @FXML private DatePicker dpAvailableDate;
    @FXML private ComboBox<String> cbDistrict;
    @FXML private ComboBox<String> cbTransport;
    @FXML private TextArea txtDescription;
    @FXML private Label lblError;
    @FXML private Button btnPostCrop;
    
    @FXML private ImageView imgPhoto1;
    @FXML private ImageView imgPhoto2;
    @FXML private ImageView imgPhoto3;
    @FXML private ImageView imgPhoto4;
    @FXML private ImageView imgPhoto5;

    private User currentUser;
    private List<File> selectedPhotos = new ArrayList<>();

    @FXML
    public void initialize() {
        currentUser = App.getCurrentUser();
        
        if (currentUser == null || !"farmer".equals(currentUser.getRole())) {
            showError("অ্যাক্সেস অস্বীকার", "শুধুমাত্র কৃষকরা ফসল পোস্ট করতে পারবেন।");
            App.loadScene("login-view.fxml", "Login");
            return;
        }

        // Initialize ComboBoxes
        cbCategory.getItems().addAll("শস্য (Crops)", "সবজি (Vegetables)", "ফল (Fruits)", "মসলা (Spices)", "ডাল (Pulses)");
        cbUnit.getItems().addAll("কেজি (kg)", "মণ (maund)", "টন (ton)", "পিস (piece)");
        cbDistrict.getItems().addAll("ঢাকা", "চট্টগ্রাম", "রাজশাহী", "খুলনা", "বরিশাল", "সিলেট", "রংপুর", "ময়মনসিংহ");
        cbTransport.getItems().addAll("বিক্রেতা সরবরাহ করবেন", "ক্রেতা নিয়ে যাবেন", "আলোচনা সাপেক্ষ");

        // Set default date to today
        dpAvailableDate.setValue(LocalDate.now());
        
        // Initialize 5 photo slots
        selectedPhotos = new ArrayList<>(5);
        for (int i = 0; i < 5; i++) {
            selectedPhotos.add(null);
        }
    }

    @FXML
    private void onAddPhoto1() { selectPhoto(0, imgPhoto1); }
    
    @FXML
    private void onAddPhoto2() { selectPhoto(1, imgPhoto2); }
    
    @FXML
    private void onAddPhoto3() { selectPhoto(2, imgPhoto3); }
    
    @FXML
    private void onAddPhoto4() { selectPhoto(3, imgPhoto4); }
    
    @FXML
    private void onAddPhoto5() { selectPhoto(4, imgPhoto5); }

    private void selectPhoto(int index, ImageView imageView) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("ছবি নির্বাচন করুন");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif")
        );
        
        File file = fileChooser.showOpenDialog(btnPostCrop.getScene().getWindow());
        if (file != null) {
            selectedPhotos.set(index, file);
            imageView.setImage(new Image(file.toURI().toString()));
            imageView.setFitWidth(120);
            imageView.setFitHeight(120);
            imageView.setPreserveRatio(true);
        }
    }

    @FXML
    private void onPostCrop() {
        lblError.setText("");
        
        // Validate fields
        if (!validateFields()) {
            return;
        }

        btnPostCrop.setDisable(true);
        lblError.setText("আপলোড হচ্ছে...");

        // Get form data
        String cropName = txtCropName.getText().trim();
        String category = cbCategory.getValue();
        double price = Double.parseDouble(txtPrice.getText().trim());
        String unit = cbUnit.getValue();
        double quantity = Double.parseDouble(txtQuantity.getText().trim());
        LocalDate harvestDate = dpAvailableDate.getValue();
        String district = cbDistrict.getValue();
        String transport = cbTransport.getValue();
        String description = txtDescription.getText().trim();

        // Insert crop into database
        DatabaseService.executeUpdateAsync(
            "INSERT INTO crops (farmer_id, name, category, price, unit, quantity, harvest_date, district, transport_info, description, status, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'active', datetime('now'))",
            new Object[]{currentUser.getId(), cropName, category, price, unit, quantity, harvestDate.toString(), district, transport, description},
            rowsAffected -> {
                if (rowsAffected > 0) {
                    // Get the last inserted crop ID
                    DatabaseService.executeQueryAsync(
                        "SELECT last_insert_rowid() as crop_id",
                        new Object[]{},
                        resultSet -> {
                            try {
                                if (resultSet.next()) {
                                    int cropId = resultSet.getInt("crop_id");
                                    // Save photos
                                    savePhotos(cropId);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                                Platform.runLater(() -> {
                                    btnPostCrop.setDisable(false);
                                    showError("ত্রুটি", "ফসল সংরক্ষণে সমস্যা হয়েছে।");
                                });
                            }
                        },
                        error -> {
                            Platform.runLater(() -> {
                                btnPostCrop.setDisable(false);
                                showError("ত্রুটি", "ফসল ID পেতে সমস্যা হয়েছে।");
                            });
                        }
                    );
                } else {
                    Platform.runLater(() -> {
                        btnPostCrop.setDisable(false);
                        lblError.setText("ফসল যোগ করতে ব্যর্থ হয়েছে।");
                    });
                }
            },
            error -> {
                Platform.runLater(() -> {
                    btnPostCrop.setDisable(false);
                    showError("ডাটাবেস ত্রুটি", "ফসল সংরক্ষণ করতে সমস্যা হয়েছে।");
                    error.printStackTrace();
                });
            }
        );
    }

    private void savePhotos(int cropId) {
        // Create directory for crop photos
        Path photosDir = Paths.get("data/crop_photos/" + cropId);
        try {
            Files.createDirectories(photosDir);
        } catch (IOException e) {
            e.printStackTrace();
        }

        for (int i = 0; i < selectedPhotos.size(); i++) {
            File photo = selectedPhotos.get(i);
            if (photo != null) {
                try {
                    // Copy file to app directory
                    String fileName = "photo_" + (i + 1) + "_" + System.currentTimeMillis() + getFileExtension(photo);
                    Path destination = photosDir.resolve(fileName);
                    Files.copy(photo.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
                    
                    // Save to database
                    DatabaseService.executeUpdateAsync(
                        "INSERT INTO crop_photos (crop_id, photo_path, photo_order) VALUES (?, ?, ?)",
                        new Object[]{cropId, destination.toString(), i + 1},
                        rows -> {
                            // Success
                        },
                        error -> error.printStackTrace()
                    );
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        Platform.runLater(() -> {
            showSuccess("সফল!", "আপনার ফসল সফলভাবে পোস্ট করা হয়েছে।");
            App.loadScene("my-crops-view.fxml", "আমার ফসলসমূহ");
        });
    }

    private String getFileExtension(File file) {
        String name = file.getName();
        int lastDot = name.lastIndexOf('.');
        return lastDot > 0 ? name.substring(lastDot) : "";
    }

    private boolean validateFields() {
        if (txtCropName.getText().trim().isEmpty()) {
            lblError.setText("ফসলের নাম লিখুন");
            return false;
        }
        if (cbCategory.getValue() == null) {
            lblError.setText("ক্যাটাগরি নির্বাচন করুন");
            return false;
        }
        try {
            double price = Double.parseDouble(txtPrice.getText().trim());
            if (price <= 0) {
                lblError.setText("সঠিক দাম লিখুন");
                return false;
            }
        } catch (NumberFormatException e) {
            lblError.setText("সঠিক দাম লিখুন");
            return false;
        }
        if (cbUnit.getValue() == null) {
            lblError.setText("একক নির্বাচন করুন");
            return false;
        }
        try {
            double quantity = Double.parseDouble(txtQuantity.getText().trim());
            if (quantity <= 0) {
                lblError.setText("সঠিক পরিমাণ লিখুন");
                return false;
            }
        } catch (NumberFormatException e) {
            lblError.setText("সঠিক পরিমাণ লিখুন");
            return false;
        }
        if (dpAvailableDate.getValue() == null) {
            lblError.setText("তারিখ নির্বাচন করুন");
            return false;
        }
        if (cbDistrict.getValue() == null) {
            lblError.setText("জেলা নির্বাচন করুন");
            return false;
        }
        if (cbTransport.getValue() == null) {
            lblError.setText("পরিবহন নির্বাচন করুন");
            return false;
        }
        if (selectedPhotos.stream().noneMatch(photo -> photo != null)) {
            lblError.setText("অন্তত একটি ছবি যোগ করুন");
            return false;
        }
        return true;
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

    private void showSuccess(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
