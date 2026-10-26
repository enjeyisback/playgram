# Playgram

A video app for your Telegram channels: stream or download, resume where you left off, pick your own player.

Kotlin + Jetpack Compose, TDLib for Telegram, Media3 for playback.

## Download

Get the latest APK from the [Releases page](https://github.com/enjeyisback/playgram/releases/latest)
([direct link to v1.0.3](https://github.com/enjeyisback/playgram/releases/download/v1.0.3/Playgram-1.0.3.apk), 65 MB).

1. Open the link on your Android phone (Android 8.0 or newer) and download `Playgram-<version>.apk`.
2. Tap the file and allow "install unknown apps" for your browser when Android asks.
3. If Google Play Protect warns about an unverified app, choose to install anyway.

Once installed, Playgram tells you inside the app (Settings > Updates) when a new release is out.

## Build

1. Create `local.properties` next to `settings.gradle.kts`:
   ```
   sdk.dir=<path to Android SDK>
   TG_API_ID=<your Telegram api_id>
   TG_API_HASH=<your Telegram api_hash>
   UPDATE_REPO=enjeyisback/playgram
   ```
   Get the Telegram keys at https://my.telegram.org.
2. Put the native libraries (`libtdjni.so`, libmpv/ffmpeg) in `app/src/main/jniLibs/<abi>/`.
3. `./gradlew assembleDebug`

## Updates

The app checks the latest GitHub release of `UPDATE_REPO`. Tag = version (`v1.0.1`), the first `.apk` asset is the update.
