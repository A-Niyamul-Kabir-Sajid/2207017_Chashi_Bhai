@echo off
REM Firebase Connection Test Script for Windows
REM Run this from VS Code Terminal or Command Prompt

echo ========================================
echo Firebase Cloud Connection Test
echo ========================================
echo.

REM Check if credentials file exists
if not exist "firebase-credentials.json" (
    echo [ERROR] firebase-credentials.json not found!
    echo.
    echo Please follow these steps:
    echo 1. Go to: https://console.firebase.google.com/
    echo 2. Project Settings ^> Service Accounts
    echo 3. Generate New Private Key
    echo 4. Save as: firebase-credentials.json in project root
    echo.
    pause
    exit /b 1
)

echo [OK] Found firebase-credentials.json
echo.

REM Check if Firebase DB URL is set
if "%FIREBASE_DB_URL%"=="" (
    echo [WARNING] FIREBASE_DB_URL environment variable not set
    echo.
    set /p FIREBASE_DB_URL="Enter your Firebase Database URL (https://YOUR-PROJECT.firebaseio.com): "
    echo.
)

echo [INFO] Using Database URL: %FIREBASE_DB_URL%
echo.

REM Compile project
echo [INFO] Compiling project...
call mvn -q clean compile
if errorlevel 1 (
    echo [ERROR] Compilation failed!
    pause
    exit /b 1
)

echo [OK] Compilation successful
echo.

REM Run test
echo [INFO] Testing Firebase connection...
echo ========================================
java -cp "target/classes;target/dependency/*" com.sajid._207017_chashi_bhai.services.FirebaseCloudConnectionTest firebase-credentials.json "%FIREBASE_DB_URL%"
echo ========================================
echo.

if errorlevel 1 (
    echo [ERROR] Firebase connection test failed!
    echo.
    echo Troubleshooting:
    echo 1. Check your credentials file
    echo 2. Verify Database URL
    echo 3. Check Firebase Console rules
    echo 4. See FIREBASE_SETUP.md for details
) else (
    echo [SUCCESS] Firebase is connected and working!
    echo.
    echo Your app can now use:
    echo - Local SQLite ^(DatabaseService^)
    echo - Cloud Firebase ^(FirebaseCloudService^)
)

echo.
pause
