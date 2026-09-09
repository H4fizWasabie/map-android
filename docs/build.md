# Build and release

## Local checks

From the repository root:

```sh
./gradlew test lint assembleDebug
./scripts/check-changelog.sh
```

With a running emulator or connected Android device, also run the local-media
transition regression gate:

```sh
./gradlew connectedDebugAndroidTest
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`.

## Release

Use a private release keystore held outside the repository. Build a signed release APK with semantic version `vX.Y.Z`, rename it to `MAP-vX.Y.Z-release.apk`, calculate SHA-256, and attach both to a manual GitHub Release.

Before release, run the automated checks and manually test camera/PDF export, music while locked, notifications, and local data on the phone.
