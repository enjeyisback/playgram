# Playgram

A video app for your Telegram channels: stream or download, resume where you left off, pick your own player.

Kotlin + Jetpack Compose, TDLib for Telegram, Media3 for playback.

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
