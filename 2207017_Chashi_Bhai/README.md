# 2207017 Chashi Bhai Project

## Overview
The 2207017 Chashi Bhai project is a JavaFX application designed to facilitate interactions between buyers, sellers, and admins in an agricultural marketplace. The application provides various functionalities, including user sign-up, transaction history, and crop feeds.

## Project Structure
```
2207017_Chashi_Bhai
├── src
│   └── main
│       ├── java
│       │   └── com
│       │       └── sajid
│       │           └── _207017_chashi_bhai
│       │               ├── HelloApplication.java
│       │               ├── controllers
│       │               │   ├── WelcomeController.java
│       │               │   ├── BuyerSignUpController.java
│       │               │   ├── SellerSignUpController.java
│       │               │   ├── AdminSignUpController.java
│       │               │   ├── HomeFeedController.java
│       │               │   ├── TransactionHistoryController.java
│       │               │   └── CropFeedController.java
│       │               ├── models
│       │               │   ├── User.java
│       │               │   ├── Buyer.java
│       │               │   ├── Seller.java
│       │               │   ├── Admin.java
│       │               │   ├── Transaction.java
│       │               │   └── Crop.java
│       │               └── services
│       │                   ├── AuthService.java
│       │                   ├── TransactionService.java
│       │                   └── CropService.java
│       └── resources
│           └── com
│               └── sajid
│                   └── _207017_chashi_bhai
│                       ├── welcome-view.fxml
│                       ├── buyer-signup-view.fxml
│                       ├── seller-signup-view.fxml
│                       ├── admin-signup-view.fxml
│                       ├── home-feed-view.fxml
│                       ├── transaction-history-view.fxml
│                       ├── crop-feed-view.fxml
│                       └── styles.css
├── pom.xml
└── README.md
```

## Features
- **Welcome Page**: A landing page for users to navigate to sign-up or log in.
- **Sign-Up Pages**: Separate sign-up pages for buyers, sellers, and admins, each with form validation.
- **Home Feed**: A feed displaying relevant content and updates for users.
- **Transaction History**: A page for users to view their past transactions.
- **Crop Feed**: A page displaying available crops for users to browse.

## Technologies Used
- Java
- JavaFX
- FXML
- Maven

## Getting Started
1. Clone the repository.
2. Navigate to the project directory.
3. Build the project using Maven.
4. Run the application.

## Contributing
Contributions are welcome! Please open an issue or submit a pull request for any enhancements or bug fixes.