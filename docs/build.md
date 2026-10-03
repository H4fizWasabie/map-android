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

Run this gate on a disposable AVD. The connected test runner uninstalls
`app.map.android` afterward, which clears its private databases and preferences.

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`.

## Personal build

For a sideloaded phone install, `./gradlew assemblePersonal` builds a release-shrunk APK signed with the debug key at `app/build/outputs/apk/personal/app-personal.apk`. It installs over the debug app with `adb install -r` and keeps its data. Debug stays the build for development and the connected tests. R8 shrinking is not covered by the connected tests, so check music playback, OCR, and PDF viewing on the phone after installing it, and reinstall the debug APK if anything misbehaves.

## Release

Use a private release keystore held outside the repository. Build a signed release APK with semantic version `vX.Y.Z`, rename it to `MAP-vX.Y.Z-release.apk`, calculate SHA-256, and attach both to a manual GitHub Release.

Before release, run the automated checks and manually test camera/PDF export, music while locked, notifications, and local data on the phone.
