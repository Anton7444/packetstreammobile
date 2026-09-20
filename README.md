# PacketStream Mobile

A Flutter Android app for viewing PacketStream account balance and the last 14 days of bandwidth. It includes an optional home-screen widget and keeps credentials inside the platform WebView.

## Build

```powershell
$flutter = 'C:\Users\Anton\Downloads\windows-sender\windows-sender\flutter_sdk\flutter\bin\flutter.bat'
& $flutter pub get
& $flutter build apk --release --split-per-abi
& $flutter build apk --release
```

The split build creates device-specific APKs for `arm64-v8a`, `armeabi-v7a`, and `x86_64`. The regular release build creates a universal APK. Release APKs are attached to GitHub releases.

This project is for personal sideloading and is not signed for Google Play distribution.
