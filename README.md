# PacketStream Mobile

A Flutter Android app for viewing PacketStream account balance and the last 14 days of bandwidth. It includes an optional home-screen widget and keeps credentials inside the platform WebView.

## Build

```powershell
flutter pub get
flutter test
flutter build apk --release
```

The split build creates device-specific APKs with `flutter build apk --release --split-per-abi`. Release APKs are attached to GitHub releases.

Release builds are unsigned unless `android/key.properties` and its referenced keystore are provided locally. Signing secrets and keystores are ignored by Git; CI builds debug APKs and does not require signing credentials.

This project is for personal sideloading and is not signed for Google Play distribution.

Built with [Codex](https://github.com/codex).
