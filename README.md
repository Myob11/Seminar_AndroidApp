# SecureChat for Android

SecureChat is an Android messaging app for private, one-to-one conversations. It combines friend discovery and requests with client-side encryption, so message content is encrypted on the device before it is sent to the backend.

## Features

- Create an account and manage your profile picture.
- Search for users, send friend requests, and accept or decline incoming requests.
- Find friends and existing direct conversations from the main screen.
- Send and receive encrypted text messages in direct conversations.
- Refresh the active conversation every three seconds while its chat screen is open.
- Choose a light or dark theme and manage account settings.

## Message security

SecureChat encrypts message text on the client with AES-256-GCM. Conversation keys are wrapped for each participant using RSA-OAEP with SHA-256. Private keys are encrypted for local storage using Android Keystore; an encrypted backup can be unlocked with the account’s recovery secret.

The backend handles authentication, friend and conversation data, and encrypted message payloads. As with any end-to-end encryption design, protect your recovery secret: it is needed to restore your private key on another device.

## Requirements

- Android Studio with Android SDK Platform 36
- JDK 17 or newer to run the Gradle build
- Android 13 (API 33) or newer for installation

The project uses Gradle 9.6 via the included Gradle Wrapper.

## Build

Open the project in Android Studio and let Gradle sync, then run the `:app` configuration on a device or emulator.

To build a debug APK from a terminal:

```bash
# macOS or Linux
./gradlew :app:assembleDebug

# Windows
gradlew.bat :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Backend

The app currently connects to the SecureChat API at [backend.kbnet.si](https://backend.kbnet.si/). The base URL is defined in `app/src/main/java/com/example/seminarandroidapp/api/ApiClient.java`.

## Project details

- **Platform:** Android
- **Language:** Java
- **UI:** Android Views and XML layouts
- **Networking:** Retrofit and OkHttp
- **Minimum Android version:** API 33
- **Target / compile SDK:** API 36
