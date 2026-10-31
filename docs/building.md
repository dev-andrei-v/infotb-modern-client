# Building & releasing

## Requirements

- **JDK 17–21.** Gradle 8.11 does not run on newer JDKs; point `JAVA_HOME` at 17 or 21.
- **Android SDK** (compile SDK 36) for the Android app.
- **macOS with Xcode** for the iOS app (deployment target iOS 17; Liquid Glass shows on iOS 26+).

## Android

```bash
./gradlew :composeApp:assembleDebug
# APK: composeApp/build/outputs/apk/debug/composeApp-debug.apk
```

Min SDK 26 (Android 8.0), target SDK 35, application id `ro.infotb.modern`. Release builds are signed with the keystore given in `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`, falling back to the debug keystore.

## Desktop

```bash
./gradlew :composeApp:run                    # run
./gradlew :composeApp:createDistributable    # package (Dmg / Msi / Deb)
```

## iOS

1. Open `iosApp/iosApp.xcodeproj` in Xcode.
2. Pick a simulator or your iPhone and press **Cmd+R**.

The Xcode build runs `./gradlew :shared:embedAndSignAppleFrameworkForXcode` itself, so there's no separate Kotlin step. To build the framework on its own:

```bash
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

To run on a device, set your team under *Signing & Capabilities* for both the `iosApp` and `InfoTBLiveActivity` targets.

From the command line:

```bash
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
  CODE_SIGNING_ALLOWED=NO build
```

### Troubleshooting

- **`Java heap space` / OOM in Kotlin/Native**: raise `kotlin.native.jvmArgs` and `org.gradle.jvm.args` in `gradle.properties`, and stop stale daemons with `./gradlew --stop`.
- **Gradle lock errors**: another Gradle process is running; `./gradlew --stop`, then retry.
- **Odd Kotlin/Native or KSP failures**: clear the cache with `rm -rf ~/.konan` and rebuild.
- **Xcode "database is locked"**: two builds touched the same DerivedData at once. Clean the build folder (Shift+Cmd+K) and build again.

## Versioning

The version lives in four places; bump them together:

- `composeApp/build.gradle.kts`: `versionName`, `versionCode`
- `iosApp/iosApp/Info.plist` and `iosApp/InfoTBLiveActivity/Info.plist`: `CFBundleShortVersionString`, `CFBundleVersion`
- `iosApp/iosApp.xcodeproj/project.pbxproj`: `MARKETING_VERSION`, `CURRENT_PROJECT_VERSION` (Live Activity target)

## CI and releases

| Workflow | Runs on | Produces |
|---|---|---|
| `ci.yml` | every pull request | debug APK and an unsigned debug IPA as build artifacts |
| `release-apk.yml` | tags `v*` | release APK attached to a GitHub Release |
| `release-ipa.yml` | tags `v*` (or manual) | unsigned IPA attached to the GitHub Release |

To release, push a tag such as `v0.2.0`.

## iOS sideloading

The IPA from a release is unsigned. Sideloading tools such as [iLoader](https://github.com/nab138/iloader) or Sideloadly sign it with your Apple ID.

The Dynamic Island / Live Activity lives in an app extension, and not every tool signs extensions. When it isn't signed, the app falls back to regular notifications.

| Install method | Dynamic Island | Notifications |
|---|---|---|
| Xcode (free or paid account) | Yes | Yes |
| [iLoader](https://github.com/nab138/iloader) | Yes | Yes |
| Sideloadly | No | Yes (fallback) |

Apps signed with a free Apple ID expire after 7 days and need re-signing.
