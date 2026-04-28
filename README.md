# SafetyAI 🛡️

SafetyAI is a modern Android application designed to enhance personal security through intelligent monitoring and automated emergency responses. It provides real-time location tracking and secures audio evidence during critical situations.

## Key Features

-   **One-Tap SOS**: Instantly activate emergency protocols with a large, accessible button.
-   **Voice-Activated SOS**: Hands-free triggering by shouting "Help" (detects multiple occurrences).
-   **Real-Time Dashboard**: Emergency contacts receive a link to a live web dashboard showing:
    -   Current GPS location (mapped via Google Maps).
    -   Encrypted audio evidence captured during the alert.
-   **AI Monitoring**: Periodically checks for high-risk areas and provides perimeter alerts.
-   **Fall Detection**: Uses accelerometer data to detect potential accidents and prompt for safety.
-   **Android 11+ Compatibility**: Specialized audio recording implementation that works seamlessly on modern Android versions (Android 11, 12, 13, and 14) by leveraging app-specific storage.

## Technical Highlights

### Audio Evidence System
The app captures high-quality AAC audio during an SOS event. Unlike many safety apps, SafetyAI is optimized for modern Android Scoped Storage:
-   **Storage**: Uses `getExternalFilesDir()` to ensure reliable recording on Android 11+ without complex permission loops.
-   **Cloud Integration**: Automatically uploads audio clips to a secure cloud link (via Catbox API) to ensure evidence is preserved even if the phone is lost or destroyed.
-   **Microphone Management**: Intelligently handles microphone hardware release between Voice AI and Evidence Recording to prevent "Device Busy" errors.

### Emergency Communication
-   **SMS Integration**: Uses `SmsManager` to send native background text messages to up to 3 emergency contacts.
-   **Live Tracking**: Generates dynamic dashboard links with embedded coordinates and audio evidence parameters.

## Permissions Required

To function effectively, the app requires:
-   `ACCESS_FINE_LOCATION`: For accurate GPS tracking.
-   `RECORD_AUDIO`: For voice trigger and evidence collection.
-   `SEND_SMS`: To alert your emergency contacts.
-   `INTERNET`: To sync location data and upload audio evidence.

## Installation & Setup

1.  **Clone the Project**: Download or clone the repository into Android Studio.
2.  **Gradle Sync**: Ensure all dependencies (Google Play Services, Material Components) are synced.
3.  **Set Contacts**: Open the app settings to configure your emergency phone numbers.
4.  **Permissions**: Grant all requested permissions on the first launch to enable background monitoring.

## Privacy Note
SafetyAI only records audio when a user-triggered or AI-detected SOS event occurs. All local recordings are stored in the app's private directory and cloud uploads are intended solely for your designated emergency contacts.
