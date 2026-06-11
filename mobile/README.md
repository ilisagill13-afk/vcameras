# US Visa Bot — Android App (Canada)

React Native (Expo) app for checking and auto-booking US visa appointments from Canada.

## Setup

```bash
cd mobile
npm install
```

## Run on Android (Dev)

```bash
# Option 1: Expo Go app (fastest)
npx expo start
# Scan QR code with Expo Go app on your Android phone

# Option 2: USB debugging
npx expo run:android
```

## Build APK (Install directly on phone)

```bash
npm install -g eas-cli
eas login          # Expo account required (free)
eas build --platform android --profile preview
# Download the .apk from the link shown and install on phone
```

## Features

| Feature | Description |
|---------|-------------|
| AIS Login | Connect your ais.usvisa-info.com account |
| Live Slots | Real-time slot availability for 7 Canadian cities |
| One-tap Book | Book any slot with a single tap |
| Auto-Book | Monitor automatically books first available slot |
| Notifications | Push notification when slots are found or booked |
| Background Check | App checks every 5 min while open |

## Canadian Consulates Supported

- Calgary (ID: 89)
- Halifax (ID: 90)
- Montreal (ID: 91)
- Ottawa (ID: 92)
- Quebec City (ID: 93)
- Toronto (ID: 94)
- Vancouver (ID: 95)
