# Focused Swift checks

Run from the repository root on macOS with Xcode selected:

```sh
swiftc -module-cache-path /tmp/infotb-swift-module-cache iosApp/iosApp/App/NearbyStopSelection.swift iosApp/tests/NearbyStopSelectionTests.swift -o /tmp/nearby-stop-tests
/tmp/nearby-stop-tests
swiftc -module-cache-path /tmp/infotb-swift-module-cache iosApp/iosApp/App/RouteLaneLayout.swift iosApp/tests/RouteLaneLayoutTests.swift -o /tmp/route-lane-tests
/tmp/route-lane-tests
```

The cache checks use the simulator Shared framework. After building the iOS app, substitute the installed simulator SDK version below and use a booted simulator ID:

```sh
xcrun swiftc -target arm64-apple-ios17.0-simulator -sdk /Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator27.2.sdk -module-cache-path /tmp/infotb-swift-module-cache -F shared/build/xcode-frameworks/Debug/iphonesimulator27.2 -framework Shared iosApp/iosApp/App/StopLocationCache.swift iosApp/iosApp/App/NearbyStopGrouping.swift iosApp/tests/StopLocationCacheTests.swift -o /tmp/stop-location-cache-tests
xcrun simctl spawn booted /tmp/stop-location-cache-tests
```

The cache check uses an isolated UserDefaults suite and removes it when finished. It covers TTL expiration, relaunch persistence, offline fallback, usage-prioritized eviction, coverage invalidation, removal of obsolete stops, and station grouping with preservation of platforms and distinct serving lines.

The membership decoder regression is a shared Kotlin test:

```sh
./gradlew :shared:desktopTest --tests '*StopLinesDecoderTest'
```
