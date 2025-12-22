# CSS Classes Audit Report - Chashi Bhai
**Generated:** December 23, 2025

## Executive Summary
This report identifies all CSS classes used in FXML files and verifies their existence in the new modular CSS structure.

---

## 🔍 CSS Classes Found in FXML Files

### ✅ Classes FOUND in CSS Files

#### From base.css:
- `root`
- `scroll-pane`
- `nav-bar`
- `app-title`
- `page-title`
- `back-button`
- `label-title`
- `label-subtitle`
- `label-description`
- `label-error`
- `label-success`
- `label-info`
- `label-footer`
- `icon-small`
- `separator`
- `form-container`
- `timer-label`
- `debug-label`

#### From components.css:
- `button`
- `button-primary`
- `button-secondary`
- `button-danger`
- `button-info`
- `button-transparent`
- `button-link`
- `btn-primary-large`
- `icon-button`
- `text-field`
- `password-field`
- `otp-field`
- `form-input`
- `field-label`
- `error-text`
- `combo-box`
- `date-picker`
- `text-area`
- `card`
- `card-light`
- `feature-card`
- `verified-badge`
- `verified-badge-small`
- `verified-badge-tiny`
- `verified-text`
- `progress-indicator`

#### From auth.css:
- `role-button`
- `selected-farmer`
- `selected-buyer`
- `welcome-title`
- `welcome-subtitle`

#### From dashboard.css:
- `dashboard-card`
- `card-green`
- `card-blue`
- `card-orange`
- `card-purple`
- `card-icon`
- `card-title`
- `card-subtitle`
- `stat-card`
- `stat-icon`
- `stat-value`
- `stat-label`
- `stat-sublabel`
- `profile-name`
- `profile-photo-border`
- `info-icon`
- `info-text`
- `info-separator`
- `summary-card`
- `summary-icon`
- `summary-value`
- `summary-label`
- `summary-sublabel`
- `action-button-large`

#### From marketplace.css:
- `filter-bar`
- `filter-tab`
- `filter-active`
- `filter-label`
- `filter-combo`
- `filter-checkbox`
- `search-container`
- `search-input`
- `search-icon`
- `quick-search`
- `crop-card`
- `crop-name`
- `crop-price`
- `crop-quantity`
- `crop-date`
- `crop-location`
- `crop-transport`
- `crop-image`
- `crop-feed-card`
- `crop-feed-name`
- `crop-feed-image`
- `farmer-name`
- `farmer-rating`
- `crop-info-label`
- `crop-info-value`
- `status-active`
- `status-sold`
- `order-card`
- `order-new`
- `order-accepted`
- `order-transit`
- `order-delivered`
- `order-status-new`
- `order-status-accepted`
- `order-status-transit`
- `order-crop-name`
- `order-label`
- `order-value`
- `order-value-price`
- `order-id`
- `order-date`
- `order-location`
- `buyer-order-card`
- `buyer-order-status-pending`
- `buyer-order-status-transit`
- `btn-payment`
- `order-pending`
- `history-card`
- `history-label`
- `history-value`
- `history-value-price`
- `payment-complete`
- `payment-pending`
- `rating-display`
- `btn-rate-small`
- `empty-icon`
- `empty-text`
- `empty-subtext`
- `photo-upload-box`
- `photo-add-icon`
- `photo-label`

#### From tables.css:
- `table-view`
- `data-table`

---

## ❌ CSS Classes MISSING from CSS Files

### Critical Missing Classes (Used in multiple files):

1. **welcome-view.fxml specific:**
   - `welcome-top-section`
   - `transparent-container`
   - `icon-large`
   - `icon-medium`
   - `title-small`
   - `welcome-middle-section`
   - `title-medium`
   - `text-muted`
   - `btn-size-13`
   - `welcome-bottom-section`
   - `field-label-small`
   - `text-muted-small`
   - `feature-card-welcome`
   - `welcome-footer`

2. **signup-view.fxml specific:**
   - `title-large`
   - `subtitle-bengali`
   - `role-btn-farmer`
   - `role-btn-buyer`
   - `btn-continue`
   - `link-login`

3. **login-view.fxml specific:**
   - `role-btn-login`
   - `link-signup`

4. **otp-verification-view.fxml specific:**
   - `otp-header`
   - `otp-subtitle`
   - `otp-phone-display`
   - `otp-instruction`
   - `otp-button`
   - `otp-resend-button`

5. **create-pin-view.fxml / reset-pin-view.fxml:**
   - `info-box`
   - `text-muted-tiny`
   - `btn-reset`
   - `link-back`

6. **farmer-dashboard-view.fxml / buyer-dashboard-view.fxml:**
   - `icon-medium`
   - `dashboard-container`
   - `stats-container`
   - `section-title`

7. **buyer-dashboard-view.fxml specific:**
   - `action-icon`
   - `action-title`
   - `action-subtitle`
   - `price-ticker-container`
   - `ticker-scroll`
   - `price-item`
   - `ticker-icon`
   - `ticker-label`
   - `ticker-price`
   - `featured-scroll`
   - `featured-crop-card`
   - `featured-image`
   - `featured-crop-name`
   - `featured-farmer`
   - `featured-price`
   - `featured-location`

8. **crop-feed-view.fxml specific:**
   - `price-slider`

9. **crop-detail-view.fxml specific:**
   - `photo-carousel-container`
   - `main-crop-photo`
   - `carousel-button`
   - `thumbnail-photo`
   - `thumbnail-active`
   - `detail-section`
   - `detail-crop-name`
   - `detail-price`
   - `detail-unit`
   - `detail-label`
   - `detail-value`
   - `detail-description`
   - `farmer-profile-card`
   - `section-subtitle`
   - `farmer-photo-border`
   - `farmer-photo`
   - `farmer-detail-name`
   - `farmer-rating-text`
   - `farmer-review-count`
   - `farmer-stat-value`
   - `farmer-stat-label`
   - `btn-action-call`
   - `btn-action-whatsapp`
   - `btn-action-order`
   - `farm-photos-section`
   - `farm-photo-small`

10. **post-crop-view.fxml / edit-crop-view.fxml:**
    - `hint-text`
    - `btn-sm`

11. **farmer-orders-view.fxml specific:**
    - `order-image`
    - `button-success`

12. **buyer-orders-view.fxml specific:**
    - `buyer-order-status-delivered`
    - `delivery-progress`
    - `btn-confirm-delivery`
    - `btn-rate`

13. **farmer-profile-view.fxml / buyer-profile-view.fxml:**
    - `profile-container`
    - `profile-header`
    - `stat-icon-small`
    - `section-container`
    - `photo-scroll`
    - `farm-photo`

14. **buyer-profile-view.fxml specific:**
    - `crop-tag`

---

## 📊 Statistics

- **Total Unique CSS Classes Used in FXML:** ~180+
- **Classes Found in CSS Files:** ~120
- **Classes Missing from CSS Files:** ~60+
- **Coverage:** ~67%

---

## 🔥 Priority Missing Classes to Add

### High Priority (Used in Core Features):
```css
/* Welcome Screen */
.welcome-top-section { }
.transparent-container { }
.icon-large { }
.icon-medium { }
.title-medium { }
.text-muted { }
.welcome-middle-section { }
.welcome-bottom-section { }
.welcome-footer { }

/* Auth Forms */
.title-large { }
.subtitle-bengali { }
.role-btn-farmer { }
.role-btn-buyer { }
.role-btn-login { }
.btn-continue { }
.link-login { }
.link-signup { }

/* OTP */
.otp-header { }
.otp-subtitle { }
.otp-phone-display { }
.otp-instruction { }
.otp-button { }
.otp-resend-button { }

/* PIN */
.info-box { }
.text-muted-tiny { }
.btn-reset { }
.link-back { }

/* Dashboard Common */
.dashboard-container { }
.stats-container { }
.section-title { }
.action-icon { }
.action-title { }
.action-subtitle { }
```

### Medium Priority (Feature Specific):
```css
/* Buyer Dashboard */
.price-ticker-container { }
.ticker-scroll { }
.ticker-icon { }
.ticker-label { }
.ticker-price { }
.featured-scroll { }
.featured-crop-card { }
.featured-image { }
.featured-crop-name { }
.featured-farmer { }
.featured-price { }
.featured-location { }

/* Crop Detail */
.photo-carousel-container { }
.main-crop-photo { }
.carousel-button { }
.thumbnail-photo { }
.thumbnail-active { }
.detail-section { }
.detail-crop-name { }
.detail-price { }
.detail-unit { }
.detail-label { }
.detail-value { }
.farmer-profile-card { }
.section-subtitle { }
.farmer-detail-name { }
.farmer-rating-text { }
.farmer-review-count { }
.farmer-stat-value { }
.farmer-stat-label { }
.btn-action-call { }
.btn-action-whatsapp { }
.btn-action-order { }
```

### Low Priority (Polish/Enhancement):
```css
/* Orders */
.order-image { }
.button-success { }
.buyer-order-status-delivered { }
.delivery-progress { }
.btn-confirm-delivery { }
.btn-rate { }

/* Profile */
.profile-container { }
.profile-header { }
.section-container { }
.photo-scroll { }
.farm-photo { }
.crop-tag { }

/* Misc */
.hint-text { }
.btn-sm { }
.price-slider { }
```

---

## 🎯 Recommendations

1. **Immediate Action Required:**
   - Add ALL missing auth-related classes (welcome, login, signup, OTP, PIN)
   - Add dashboard container and layout classes
   - Add basic typography classes (title-large, text-muted, etc.)

2. **Next Steps:**
   - Add marketplace detail page classes (crop-detail-view)
   - Add buyer dashboard specific classes (price ticker, featured crops)
   - Add profile-related classes

3. **Long-term:**
   - Add order management specific classes
   - Add photo gallery and carousel classes
   - Refine and standardize class naming conventions

4. **Consider:**
   - Creating a `typography.css` for text styles (title-large, text-muted, etc.)
   - Creating a `layouts.css` for container classes
   - Creating a `effects.css` for carousel, scrolls, animations

---

## 📝 Notes

- Many classes follow good naming patterns (e.g., `btn-*`, `order-*`, `farmer-*`)
- Some classes might be redundant or could be consolidated
- Bengali text classes (subtitle-bengali) should be in separate file
- Icon classes need standardization (icon-small, icon-medium, icon-large)
- Consider using CSS variables for repeated values

---

**End of Report**
