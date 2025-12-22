# Chashi Bhai - FXML Files Summary

## Overview
Created 12 comprehensive JavaFX FXML files for the Chashi Bhai agricultural marketplace application, designed for low-literacy rural users in Bangladesh with bilingual (Bangla/English) UI.

---

## ✅ FARMER SCREENS (7 files)

### 1. **farmer-dashboard-view.fxml**
**Main farmer dashboard after login**
- Welcome message with verified badge
- 4 large action cards:
  - নতুন ফসল যোগ করুন (Post Crop) - Green
  - আমার ফসলসমূহ (My Crops) - Blue
  - আমার অর্ডারসমূহ (Orders) - Orange
  - বিক্রয় ইতিহাস (History) - Purple
- Quick stats section: Total Earnings, Active Listings, Pending Orders
- Top nav bar with app logo and profile button

**Key fx:ids:** `lblWelcome`, `lblVerifiedBadge`, `btnPostCrop`, `btnMyCrops`, `btnMyOrders`, `btnHistory`, `lblTotalEarnings`, `lblActiveListings`, `lblPendingOrders`

---

### 2. **farmer-profile-view.fxml**
**Complete farmer profile display**
- Circular profile photo with border
- Verified badge (✓ যাচাইকৃত কৃষক)
- Contact info: phone, location (district/upazila), farm type
- 3 stat cards: Years Farming, Total Sales, Rating (with star)
- Horizontal scrolling farm photos gallery
- Edit profile button (top right)

**Key fx:ids:** `imgProfilePhoto`, `lblFarmerName`, `lblVerifiedBadge`, `lblPhone`, `lblDistrict`, `lblUpazila`, `lblFarmType`, `lblYearsFarming`, `lblTotalSales`, `lblRating`, `hboxFarmPhotos`, `btnEditProfile`

---

### 3. **post-crop-view.fxml**
**Form to post new crop listing**
- Fields: Crop Name, Category (ComboBox), Price + Unit, Quantity, Harvest Date (DatePicker), District (ComboBox with 8 divisions), Transport options
- 5 photo upload placeholders with click-to-add functionality
- Large bilingual "✓ পোস্ট করুন" submit button
- Description text area (optional)
- Error label for validation messages

**Key fx:ids:** `txtCropName`, `cbCategory`, `txtPrice`, `cbUnit`, `txtQuantity`, `dpAvailableDate`, `cbDistrict`, `cbTransport`, `txtDescription`, `imgPhoto1-5`, `lblError`, `btnPostCrop`

---

### 4. **my-crops-view.fxml**
**List of farmer's active and sold crops**
- Filter tabs: All, Active, Sold, Expired
- Scrollable VBox with repeated crop cards
- Each card shows: photo, name, price, quantity, date, status badge
- Action buttons per card: Edit, Delete, Statistics
- Empty state with "Add First Crop" button

**Key fx:ids:** `btnFilterAll/Active/Sold/Expired`, `vboxCropsList`, `vboxEmptyState`, `btnAddNew`

---

### 5. **edit-crop-view.fxml**
**Edit existing crop listing** (same structure as post-crop)
- Pre-filled form fields
- Photo change/add functionality
- Update and Cancel buttons
- Title: "ফসলের তথ্য সম্পাদনা করুন"

**Key fx:ids:** Same as post-crop-view + `btnUpdate`, `btnCancel`

---

### 6. **farmer-orders-view.fxml**
**Incoming buyer orders/requests**
- Filter tabs: All, New, Accepted, In Transit, Delivered
- Order cards with colored borders based on status
- Each card: buyer name, crop, quantity, price, date, location
- Status-specific action buttons:
  - New: Accept, Contact, Reject
  - Accepted: Mark In Transit, Contact, Details
  - In Transit: Mark Delivered, Contact
- Empty state message

**Key fx:ids:** `btnFilterAll/New/Accepted/InTransit/Delivered`, `vboxOrdersList`, `vboxEmptyState`, `btnRefresh`

---

### 7. **farmer-history-view.fxml**
**Selling transaction history**
- 3 summary cards at top: Total Income, Most Sold Crop, Total Orders (with colored backgrounds)
- Filter dropdowns: Month range, Crop type, Apply/Export buttons
- Scrollable history list with columns: Date, Buyer, Crop, Quantity, Price, Payment Status
- Each row has view details button
- Payment status badges: Complete (green), Pending (orange)

**Key fx:ids:** `lblTotalIncome`, `lblMostSold`, `lblTotalOrders`, `cbFilterMonth`, `cbFilterCrop`, `btnApplyFilter`, `btnExport`, `vboxHistoryList`

---

## ✅ BUYER SCREENS (5 files)

### 8. **buyer-dashboard-view.fxml**
**Main buyer dashboard**
- Welcome message
- Large search bar: "ফসল খুঁজুন..." with search button
- 3 quick action buttons:
  - সকল ফসল (Browse All) - Green
  - আমার অর্ডারসমূহ (My Orders) - Blue
  - ক্রয় ইতিহাস (History) - Purple
- Live price ticker: horizontal scroll with today's market prices for Rice, Potato, Tomato, Corn
- Featured crops carousel (4 cards with images, farmer name, price, location)

**Key fx:ids:** `lblWelcome`, `txtSearch`, `btnSearch`, `btnBrowseAll`, `btnMyOrders`, `btnHistory`, `hboxPriceTicker`, `hboxFeaturedCrops`

---

### 9. **crop-feed-view.fxml**
**Main browsing feed with filters**
- Top: quick search field
- Filter bar: Crop Type, District, Price Range (slider), Verified Only checkbox, Apply/Reset buttons
- Scrollable crop cards (horizontal layout):
  - Large crop image (140x140)
  - Crop name, farmer (with verified badge), rating
  - Price, quantity, harvest date, location, transport info
  - Action buttons: View Details, Contact
- Empty state with retry message

**Key fx:ids:** `txtQuickSearch`, `cbFilterCropType`, `cbFilterDistrict`, `sliderPriceMin`, `lblPriceRange`, `chkVerifiedOnly`, `btnApplyFilter`, `btnResetFilter`, `vboxCropFeed`, `vboxEmptyState`

---

### 10. **crop-detail-view.fxml**
**Detailed crop view**
- Large photo carousel (700x450) with prev/next arrows
- Thumbnail gallery below main photo
- Left column: Crop details (name, price, category, quantity, harvest date, location, transport, description)
- Right column:
  - Farmer profile card (photo, name, verified, rating, years, sales, district, view profile link)
  - 3 big action buttons: Call, WhatsApp, Order
  - Farm photos section (2 images)
- Favorite button (heart icon, top right)

**Key fx:ids:** `imgMainPhoto`, `btnPrevPhoto`, `btnNextPhoto`, `hboxThumbnails`, `lblCropName`, `lblCropPrice`, `lblCategory`, `lblQuantity`, `lblHarvestDate`, `lblLocation`, `lblTransport`, `lblDescription`, `imgFarmerPhoto`, `lblFarmerName`, `lblFarmerRating`, `lblTotalReviews`, `lblFarmerYears`, `lblFarmerSales`, `lblFarmerDistrict`, `btnCall`, `btnWhatsApp`, `btnOrder`, `btnFavorite`

---

### 11. **buyer-orders-view.fxml**
**Buyer's active orders**
- Filter tabs: All, Pending Payment, Confirmed, In Transit, Delivered
- Order cards with colored borders
- Each card: crop image, name, farmer (verified), quantity, total price, date
- Status-specific buttons:
  - Pending: Make Payment, Contact, Cancel
  - In Transit: Confirm Delivery, Contact, Track (with progress bar)
  - Delivered: Rate Order, Contact, Reorder
- Empty state: "Browse Crops" button

**Key fx:ids:** `btnFilterAll/Pending/Confirmed/InTransit/Delivered`, `vboxOrdersList`, `vboxEmptyState`, `btnRefresh`

---

### 12. **buyer-history-view.fxml**
**Purchase history**
- 4 summary cards: Total Expense, Most Bought Crop, Favorite Farmers, Total Orders (color-coded)
- Filter section: Time period, Crop type, Apply/Export buttons
- History table: Date, Farmer (verified), Crop, Quantity, Price, Rating
- Each row: View Details, Reorder icons
- Rate button for unrated orders

**Key fx:ids:** `lblTotalExpense`, `lblMostBought`, `lblFavoriteFarmers`, `lblTotalOrders`, `cbFilterMonth`, `cbFilterCrop`, `btnApplyFilter`, `btnExport`, `vboxHistoryList`

---

## 🎨 CSS Classes Used

### Layout & Structure
- `.root`, `.nav-bar`, `.card`, `.dashboard-container`, `.profile-container`, `.form-container`, `.section-container`

### Typography
- `.app-title`, `.page-title`, `.welcome-title`, `.profile-name`, `.section-title`, `.label-title`, `.field-label`, `.stat-label`

### Buttons
- `.button-primary`, `.button-secondary`, `.back-button`, `.icon-button`, `.dashboard-card`, `.btn-primary-large`, `.btn-continue`, `.button-danger`, `.button-info`, `.button-success`

### Status & Badges
- `.verified-badge`, `.verified-badge-small`, `.verified-badge-tiny`, `.status-active`, `.status-sold`, `.buyer-badge`, `.payment-complete`, `.payment-pending`

### Cards & Components
- `.stat-card`, `.summary-card`, `.crop-card`, `.order-card`, `.history-card`, `.featured-crop-card`, `.address-card`, `.photo-upload-box`

### Colors
- `.card-green`, `.card-blue`, `.card-orange`, `.card-purple` (for themed cards)

### Filters & Tabs
- `.filter-bar`, `.filter-tab`, `.filter-active`, `.filter-label`

### Empty States
- `.empty-icon`, `.empty-text`, `.empty-subtext`

---

## 📝 Key Features

### Design Principles
✅ **Low-literacy friendly:** Large fonts (14-24sp), clear icons, plenty of spacing  
✅ **Bilingual:** All labels in Bangla + English  
✅ **Color-coded:** Green (primary/farmers), Blue (buyers), Orange (pending), Purple (history)  
✅ **Consistent navigation:** Top bar on every screen with back button, logo, and profile  
✅ **Responsive:** Uses GridPane, VBox, HBox, BorderPane with proper constraints  
✅ **Verified badges:** Prominent green checkmarks for trusted farmers  
✅ **Unicode icons:** Using emoji for maximum compatibility (🌾,📦,📞,✓,etc.)

### Interaction Patterns
- **Cards as buttons:** Large clickable dashboard cards with hover effects
- **Status indicators:** Color-coded borders and badges
- **Multi-step filters:** ComboBoxes + sliders + checkboxes
- **Photo management:** Click-to-upload placeholders, carousel for viewing
- **Progress tracking:** Status tabs + progress bars for orders
- **Empty states:** Friendly messages with action buttons

---

## 🔧 Implementation Notes

### Controllers Needed
Each FXML requires a corresponding controller:
1. `FarmerDashboardController`
2. `FarmerProfileController`
3. `PostCropController`
4. `MyCropsController`
5. `EditCropController`
6. `FarmerOrdersController`
7. `FarmerHistoryController`
8. `BuyerDashboardController`
9. `CropFeedController`
10. `CropDetailController`
11. `BuyerOrdersController`
12. `BuyerHistoryController`

### Model Classes
- `Farmer`, `Buyer`, `Crop`, `Order`, `Transaction`

### Services
- `AuthService`, `CropService`, `OrderService`, `TransactionService`, `ImageService`

### CSS Files
- Main: `styles.css` (existing)
- Additional: `additional-styles.css` (newly created with 700+ lines)

---

## 🚀 Next Steps

1. **Create Controllers:** Implement all 12 controller classes with proper event handlers
2. **Wire Navigation:** Connect all `onAction` methods for screen transitions
3. **Add Validation:** Phone, price, quantity, date validation in forms
4. **Implement Photo Upload:** FileChooser integration for image selection
5. **Database Integration:** Connect to backend for CRUD operations
6. **Add Animations:** Fade-in transitions, card hover effects
7. **Localization:** Consider i18n for more languages if needed
8. **Testing:** Test on different screen sizes and resolutions

---

## 📦 Files Created

### FXML Files (12):
1. `farmer-dashboard-view.fxml`
2. `farmer-profile-view.fxml` (enhanced)
3. `post-crop-view.fxml`
4. `my-crops-view.fxml`
5. `edit-crop-view.fxml`
6. `farmer-orders-view.fxml`
7. `farmer-history-view.fxml`
8. `buyer-dashboard-view.fxml`
9. `crop-feed-view.fxml`
10. `crop-detail-view.fxml`
11. `buyer-orders-view.fxml`
12. `buyer-history-view.fxml`

### CSS Files (1):
- `additional-styles.css` (comprehensive styling for all new components)

### Existing Files (kept):
- `buyer-profile-view.fxml` (simple form - can be enhanced later)
- `styles.css` (existing base styles)

---

## 📱 Screenshots Concepts

Each screen is designed for:
- **Desktop:** 1024x700 minimum
- **Tablet:** Responsive layout with scrolling
- **Mobile-first approach:** Touch-friendly buttons (48dp+), large text

---

**Total Implementation:** 12 complete FXML files + comprehensive CSS + this documentation

All files follow JavaFX best practices with proper imports, fx:ids, and bilingual UI suitable for Bangladeshi agricultural marketplace users. 🌾
