# Production PIN Reset Guide

## Overview
This guide explains the production-ready PIN reset system using Firebase Firestore for OTP storage.

## Architecture

### Flow Diagram
```
User                    App                     Firestore               Firebase Auth
 |                       |                          |                        |
 |--Request Reset------->|                          |                        |
 |                       |---Store OTP------------->|                        |
 |                       |<--OTP ID-----------------                         |
 |                       |                          |                        |
 |<--"Contact Admin"-----|                          |                        |
 |                       |                          |                        |
[Admin views Firebase Console to see OTP]          |                        |
 |                       |                          |                        |
[Admin provides OTP to user via phone/SMS]         |                        |
 |                       |                          |                        |
 |--Enter OTP----------->|                          |                        |
 |                       |---Verify OTP------------>|                        |
 |                       |<--Valid/Invalid----------                         |
 |                       |                          |                        |
 |--Enter New PIN------->|                          |                        |
 |                       |---Update Password---------------------–>          |
 |                       |<--Success-------------------------------          |
 |                       |---Mark OTP Used--------->|                        |
 |<--"Reset Success"-----|                          |                        |
```

## Firestore Collection: `password_reset_otps`

### Document Structure
```json
{
  "phone": "8801712345678",
  "role": "farmer",
  "otp": "123456",
  "createdAt": 1705165200000,
  "expiresAt": 1705166100000,
  "used": false
}
```

### Document ID Format
`{phone}_{role}` 
Example: `8801712345678_farmer`

### Field Descriptions
- **phone**: User's phone number (11 digits)
- **role**: User type (`farmer` or `buyer`)
- **otp**: 6-digit random number
- **createdAt**: Timestamp when OTP was generated (milliseconds)
- **expiresAt**: Expiration timestamp (15 minutes from creation)
- **used**: Boolean flag to prevent reuse

## Admin Workflow

### Step 1: User Requests PIN Reset
1. User enters phone number and selects role
2. App generates 6-digit OTP
3. App stores OTP in Firestore collection `password_reset_otps`
4. App shows message: "OTP generated. Please contact admin for OTP."

### Step 2: Admin Views OTP in Firebase Console
1. Open Firebase Console: https://console.firebase.google.com/
2. Navigate to: **Firestore Database**
3. Select collection: **password_reset_otps**
4. Find document by ID: `{phone}_{role}`
   - Example: `8801712345678_farmer`
5. View OTP field value

### Step 3: Admin Provides OTP to User
- Admin calls/messages user with the 6-digit OTP
- User receives OTP: `123456`

### Step 4: User Enters OTP and Resets PIN
1. User enters OTP in app
2. App verifies OTP:
   - Checks if OTP matches
   - Checks if not expired (< 15 minutes)
   - Checks if not already used
3. If valid, user can set new PIN
4. App updates Firebase Auth password
5. App marks OTP as used in Firestore

## Security Features

### 1. OTP Expiration
- OTPs expire after **15 minutes**
- Expired OTPs cannot be used

### 2. Single Use
- Each OTP can only be used once
- After successful reset, OTP is marked as `used: true`

### 3. No SMS Gateway Required
- Manual OTP distribution by admin
- No cost for SMS services
- Secure communication channel

### 4. No Credentials in SQLite
- PINs stored only in Firebase Auth
- Local database has only profile data

## Implementation Code

### Generate OTP (Java)
```java
FirebaseService firebaseService = FirebaseService.getInstance();
String otp = firebaseService.requestPinReset(phone, role);
System.out.println("OTP generated: " + otp);
System.out.println("Admin can view in Firebase Console");
```

### Verify OTP (Java)
```java
boolean isValid = firebaseService.verifyPinResetOTP(phone, role, userEnteredOtp);
if (isValid) {
    // Allow user to set new PIN
    authService.updatePasswordViaAdmin(phone, newPin);
    firebaseService.markOTPAsUsed(phone, role);
}
```

## Firebase Console Access

### URL
https://console.firebase.google.com/

### Navigation Path
1. Select Project: **testfirebase-12671** (or your project)
2. Click: **Firestore Database** (left sidebar)
3. Click: **password_reset_otps** collection
4. Click document with user's phone number

### Example Document View
```
Document ID: 8801712345678_farmer
─────────────────────────────────────
phone:      "8801712345678"
role:       "farmer"
otp:        "742539"        ← Share this with user
createdAt:  1705165200000
expiresAt:  1705166100000   ← Check if expired
used:       false           ← Check if already used
```

## Error Handling

### OTP Not Found
**Error**: "No OTP request found"
**Cause**: User hasn't requested reset or document deleted
**Solution**: Request new OTP

### OTP Expired
**Error**: "OTP expired"
**Cause**: More than 15 minutes since generation
**Solution**: Request new OTP

### OTP Already Used
**Error**: "OTP already used"
**Cause**: Same OTP used for previous reset
**Solution**: Request new OTP

### Invalid OTP
**Error**: "Invalid OTP"
**Cause**: Incorrect digits entered
**Solution**: Re-enter correct OTP or contact admin

## Best Practices

### 1. Regular Cleanup
Delete old OTPs (> 24 hours) periodically:
- Use Firebase Console manually
- Or create Cloud Function for auto-cleanup

### 2. Rate Limiting
Prevent abuse by limiting OTP requests:
- Max 3 requests per phone per hour
- Implement in app logic or Firebase Rules

### 3. Audit Trail
Log all PIN reset attempts:
- Store in separate Firestore collection
- Include: phone, timestamp, success/failure

### 4. Secure Communication
When providing OTP to user:
- Verify user identity first
- Use secure channel (direct call preferred)
- Don't share via public channels

## Migration from Old System

### Current State
- PINs stored in SQLite `users` table
- Direct PIN update in database
- No OTP verification

### Migration Steps

1. **Update Database Schema**
   ```sql
   -- Run: remove_pin_migration.sql
   -- Removes PIN column from users table
   ```

2. **Update Signup**
   - Remove SQLite PIN insertion
   - Store only in Firebase Auth

3. **Update Login**
   - Remove SQLite PIN verification
   - Authenticate only via Firebase Auth

4. **Update PIN Reset**
   - Implement OTP flow
   - Update Firebase Auth password

5. **Test Flow**
   - Request OTP → View in Console → Enter OTP → Reset PIN

## Troubleshooting

### Issue: OTP not appearing in Firebase
**Check**:
1. Firebase Service initialized correctly
2. PROJECT_ID matches Firebase project
3. Network connectivity to Firestore API
4. Check app logs for errors

### Issue: OTP verification fails
**Check**:
1. Document ID format: `{phone}_{role}`
2. OTP digits match exactly (case-sensitive)
3. OTP not expired (< 15 minutes)
4. OTP not already used

### Issue: Firebase Auth update fails
**Check**:
1. FirebaseAuthService configured correctly
2. API key valid
3. User exists in Firebase Auth
4. Network connectivity

## Future Enhancements

### 1. Automated SMS
- Integrate SMS gateway (e.g., Twilio)
- Auto-send OTP to user's phone
- No manual admin intervention

### 2. Firebase Admin SDK
- Use Admin SDK for password updates
- No need to recreate accounts
- Direct password change

### 3. Cloud Functions
- Auto-cleanup expired OTPs
- Rate limiting enforcement
- Audit log generation

### 4. Multi-channel OTP
- SMS + Email
- In-app notification
- WhatsApp integration

## Support

For issues or questions:
1. Check Firebase Console logs
2. Review app console output
3. Verify Firestore security rules
4. Contact development team

---

**Last Updated**: January 13, 2026
**Version**: 1.0
**System**: Chashi Bhai - Production PIN Reset
