# 🌾 Chashi Bhai - Farmer-Buyer Marketplace

[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://www.oracle.com/java/)
[![JavaFX](https://img.shields.io/badge/JavaFX-21.0.6-blue.svg)](https://openjfx.io/)
[![SQLite](https://img.shields.io/badge/SQLite-3.45-green.svg)](https://www.sqlite.org/)
[![Firebase](https://img.shields.io/badge/Firebase-9.2.0-yellow.svg)](https://firebase.google.com/)

A JavaFX desktop application connecting farmers directly with buyers, eliminating middlemen and ensuring fair prices for agricultural products in Bangladesh.

## 📋 Table of Contents
- [Features](#features)
- [Technology Stack](#technology-stack)
- [Project Structure](#project-structure)
- [Database Schema](#database-schema)
- [Installation](#installation)
- [Usage](#usage)
- [Screenshots](#screenshots)
- [Contributing](#contributing)
- [License](#license)

## ✨ Features

### For Farmers (কৃষক)
- 📝 Post crops with photos, quantity, and pricing
- 📊 Track inventory and available quantities
- 📦 Manage orders from buyers
- 💬 Direct chat with buyers
- 📈 View sales history and statistics
- ⭐ Receive ratings and reviews

### For Buyers (ক্রেতা)
- 🔍 Browse crops by category, location, and price
- 🛒 Place orders directly with farmers
- 💬 Chat with farmers before purchasing
- 📍 Filter by district and upazila
- 📱 Track order status in real-time
- ⭐ Rate and review farmers

### General Features
- 🔐 Secure phone + PIN authentication
- 🌐 Bilingual interface (Bengali & English)
- 💾 Offline-first with SQLite
- ⚡ Real-time chat with Firebase
- 📊 Automatic inventory management
- 🔔 Push notifications
- 📱 Responsive UI design

## 🛠️ Technology Stack

### Frontend
- **JavaFX 21.0.6** - Modern UI framework
- **FXML** - Declarative UI design
- **CSS** - Custom styling

### Backend
- **SQLite 3.45** - Local database for offline functionality
- **Firebase Realtime Database** - Real-time chat and notifications
- **JDBC** - Database connectivity

### Build & Deployment
- **Maven** - Dependency management
- **Java 25** - Programming language

### Libraries
- **BCrypt** - Password hashing
- **Gson** - JSON processing
- **Firebase Admin SDK** - Firebase integration

## 📁 Project Structure

```
2207017_Chashi_Bhai/
├── src/
│   └── main/
│       ├── java/com/sajid/_207017_chashi_bhai/
│       │   ├── App.java                    # Main application class
│       │   ├── Launcher.java               # Entry point
│       │   ├── controllers/                # FXML controllers
│       │   │   ├── BuyerDashboardController.java
│       │   │   ├── FarmerDashboardController.java
│       │   │   ├── CropFeedController.java
│       │   │   ├── ChatListController.java
│       │   │   ├── ChatConversationController.java
│       │   │   └── ...
│       │   ├── models/                     # Data models
│       │   │   └── User.java
│       │   ├── services/                   # Business logic
│       │   │   ├── DatabaseService.java
│       │   │   ├── DatabaseInitializer.java
│       │   │   └── FirebaseService.java
│       │   └── utils/                      # Utility classes
│       └── resources/com/sajid/_207017_chashi_bhai/
│           ├── *.fxml                      # FXML view files
│           ├── *.css                       # Stylesheets
│           └── image/                      # Images and icons
├── data/
│   └── chashi_bhai.db                      # SQLite database (auto-generated)
├── database_schema.sql                     # Database schema
├── firebase_structure.json                 # Firebase structure
├── pom.xml                                 # Maven configuration
├── SETUP_GUIDE.md                         # Setup instructions
├── README_DATABASE.md                     # Database documentation
└── README.md                              # This file
```

## 🗄️ Database Schema

### Main Tables

#### 1. **users** - User accounts
```sql
- id (PK)
- phone (unique)
- pin
- name
- role (farmer/buyer)
- district, upazila, village
- is_verified
- profile_photo
- created_at, updated_at
```

#### 2. **crops** - Farmer's products
```sql
- id (PK)
- farmer_id (FK → users)
- name, category
- initial_quantity_kg      # Original amount
- available_quantity_kg    # Current available (auto-updated)
- price_per_kg
- description
- district, upazila, village
- harvest_date
- status (active/sold/expired/deleted)
- created_at, updated_at
```

#### 3. **orders** - Purchase orders
```sql
- id (PK)
- order_number (unique)
- crop_id (FK → crops)
- farmer_id (FK → users)
- buyer_id (FK → users)
- quantity_kg
- price_per_kg
- total_amount
- delivery_address, delivery_district
- buyer_phone, buyer_name
- status (new/accepted/in_transit/delivered/completed)
- payment_status, payment_method
- created_at, accepted_at, delivered_at
```

#### 4. **conversations** & **messages** - Chat system
```sql
conversations:
- id (PK)
- user1_id, user2_id (FK → users)
- crop_id (optional, FK → crops)
- last_message, last_message_time
- unread_count_user1, unread_count_user2

messages:
- id (PK)
- conversation_id (FK → conversations)
- sender_id, receiver_id (FK → users)
- message_text
- message_type (text/image/file)
- attachment_path
- is_read, read_at
```

#### 5. **crop_photos** - Product images
```sql
- id (PK)
- crop_id (FK → crops)
- photo_path
- photo_order
```

#### 6. **order_history** - Order tracking
```sql
- id (PK)
- order_id (FK → orders)
- status
- changed_by (FK → users)
- notes
- created_at
```

#### 7. **reviews** - Ratings & feedback
```sql
- id (PK)
- order_id (FK → orders)
- reviewer_id, reviewee_id (FK → users)
- rating (1-5)
- comment
- created_at
```

#### 8. **notifications** - Push notifications
```sql
- id (PK)
- user_id (FK → users)
- title, message
- type (order/chat/review/system)
- related_id
- is_read
- created_at
```

### Automatic Features (SQL Triggers)

✅ **Auto-reduce quantity** when order is accepted
✅ **Auto-restore quantity** when order is cancelled
✅ **Auto-mark as "sold"** when quantity reaches 0
✅ **Auto-log** all order status changes
✅ **Auto-update** conversation last message

### Database Views

- `v_crop_listings` - Crops with farmer info
- `v_order_details` - Orders with complete details
- `v_conversation_list` - Conversations with user details

**Full schema**: See [database_schema.sql](database_schema.sql)

## 📥 Installation

### Prerequisites
- **Java 25** or higher
- **Maven 3.6+**
- **Git**
- **DB Browser for SQLite** (optional, for database viewing)
- **Firebase Account** (for real-time features)

### Step 1: Clone Repository
```bash
git clone https://github.com/A-Niyamul-Kabir-Sajid/2207017_Chashi_Bhai.git
cd 2207017_Chashi_Bhai
```

### Step 2: Install Dependencies
```bash
mvn clean install
```

### Step 3: Setup Firebase (Optional)
1. Create a Firebase project at https://console.firebase.google.com/
2. Enable Realtime Database
3. Download `firebase-credentials.json`
4. Place it in `src/main/resources/`
5. Update database URL in `FirebaseService.java`

**Detailed setup**: See [SETUP_GUIDE.md](SETUP_GUIDE.md)

### Step 4: Run Application
```bash
mvn javafx:run
```

Or use your IDE:
- **IntelliJ IDEA**: Run `Launcher.java`
- **Eclipse**: Run `Launcher.java`
- **VS Code**: Run `Launcher.java`

## 🚀 Usage

### First Time Setup

1. **Run the application** - Database auto-initializes
2. **Test login** with sample accounts:
   ```
   Farmer:
   Phone: 01711111111
   PIN: 1234
   
   Buyer:
   Phone: 01722222222
   PIN: 1234
   ```

### For Farmers

1. **Login** with your phone number and PIN
2. **Post a crop**:
   - Click "নতুন যোগ করুন" (Add New)
   - Fill in crop details (name, quantity, price, photos)
   - Click "পোস্ট করুন" (Post)
3. **Manage orders**:
   - View incoming orders
   - Accept/reject orders
   - Update order status
4. **Chat with buyers**:
   - Navigate to Messages
   - Reply to buyer inquiries

### For Buyers

1. **Login** with your phone number and PIN
2. **Browse crops**:
   - View all available crops
   - Filter by category/location
   - Search by name
3. **Place order**:
   - Click on a crop
   - Enter quantity
   - Add delivery details
   - Submit order
4. **Chat with farmer**:
   - Click "যোগাযোগ করুন" (Contact)
   - Ask questions before ordering

### Order Flow

```
1. Buyer places order → Status: "new"
2. Farmer accepts → Status: "accepted" (quantity reduced)
3. Farmer ships → Status: "in_transit"
4. Delivery complete → Status: "delivered"
5. Buyer confirms → Status: "completed"
```

## 📸 Screenshots

> Add screenshots here after application is ready

## 🔧 Development

### Database Location
```
data/chashi_bhai.db
```

### View Database
Use **DB Browser for SQLite**:
```bash
# Download from: https://sqlitebrowser.org/
# Open: data/chashi_bhai.db
```

### Run Tests
```bash
mvn test
```

### Build JAR
```bash
mvn clean package
```

### Create Executable
```bash
mvn javafx:jlink
```

## 🐛 Known Issues & Limitations

- [ ] Firebase credentials required for chat (workaround: use SQLite-only mode)
- [ ] No automatic image compression (large images may slow down app)
- [ ] Limited to Bangladesh districts (can be extended)
- [ ] No payment gateway integration (cash on delivery only)

## 🗑️ Unnecessary Files

The following files/folders can be safely deleted:

### Can Delete:
- `tempCodeRunnerFile.java` - Temporary file
- `out/` folder - Old build outputs
- `.idea/` folder - IntelliJ IDEA settings (auto-generated)
- `.vscode/` folder - VS Code settings (personal preference)
- `target/` folder - Maven build outputs (regenerated on build)
- `BUTTON_VERIFICATION_REPORT.md` - Development documentation
- `CONTROLLER_IMPLEMENTATION_GUIDE.md` - Development documentation
- `CSS_AUDIT_REPORT.md` - Development documentation
- `FXML_FILES_SUMMARY.md` - Development documentation

### Should Keep:
- `data/` folder - Contains database
- `src/` folder - Source code
- `pom.xml` - Maven configuration
- `database_schema.sql` - Database schema
- `firebase_structure.json` - Firebase structure
- `SETUP_GUIDE.md` - Setup instructions
- `README_DATABASE.md` - Database documentation
- `README.md` - This file
- `.git/` - Git repository
- `.gitignore` - Git ignore rules
- `mvnw`, `mvnw.cmd`, `.mvn/` - Maven wrapper (for consistent builds)

## 📝 To-Do

- [ ] Add authentication with OTP
- [ ] Implement image compression
- [ ] Add payment gateway integration
- [ ] Create mobile version (Android/iOS)
- [ ] Add push notifications
- [ ] Implement advanced search filters
- [ ] Add crop recommendations
- [ ] Create farmer analytics dashboard
- [ ] Add multi-language support
- [ ] Implement data export feature

## 🤝 Contributing

Contributions are welcome! Please follow these steps:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 👨‍💻 Author

**A. Niyamul Kabir Sajid**
- Student ID: 2207017
- Institution: KUET (Khulna University of Engineering & Technology)
- GitHub: [@A-Niyamul-Kabir-Sajid](https://github.com/A-Niyamul-Kabir-Sajid)

## 🙏 Acknowledgments

- JavaFX community for excellent documentation
- SQLite for reliable local database
- Firebase for real-time capabilities
- All contributors and testers

## 📞 Support

For issues and questions:
- **GitHub Issues**: [Create an issue](https://github.com/A-Niyamul-Kabir-Sajid/2207017_Chashi_Bhai/issues)
- **Email**: Contact through GitHub profile

---

**Made with ❤️ for Bangladeshi farmers**
