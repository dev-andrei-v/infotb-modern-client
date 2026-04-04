![InfoTB](icon.png)

# InfoTB

A **Kotlin Multiplatform (KMP) + Compose Multiplatform** public transit app for Bucharest (STB). Targets **Android**, **Desktop (Windows/macOS/Linux)**, and **iOS** from a single codebase.

---

## Project Structure

```
infotb-modern-client/
├── shared/                          # Pure KMP business logic (no UI)
│   └── src/
│       ├── commonMain/kotlin/ro/infotb/modern/
│       │   ├── data/
│       │   │   ├── model/           # Domain data classes (Line, Stop, Announcement, etc.)
│       │   │   ├── api/             # Ktor HTTP client, auth, protobuf decoding
│       │   │   ├── db/              # Room database, entities, DAOs
│       │   │   ├── repository/      # Data coordination layer
│       │   │   └── store/           # Persistent state (Room + multiplatform-settings)
│       │   ├── notification/        # Push notification engine & platform notifier
│       │   ├── util/                # Constants, i18n, formatters, polyline
│       │   └── di/                  # Koin dependency injection modules
│       ├── androidMain/             # Android-specific (DatabaseFactory, Platform)
│       ├── desktopMain/             # Desktop/JVM-specific (DatabaseFactory, Platform)
│       └── iosMain/                 # iOS-specific (DatabaseFactory, Platform)
│
├── composeApp/                      # Compose Multiplatform UI
│   └── src/
│       ├── commonMain/kotlin/ro/infotb/modern/ui/
│       │   ├── theme/               # Material 3 theme, colors
│       │   ├── navigation/          # Screen routes, NavItem definitions
│       │   ├── components/
│       │   │   ├── layout/          # AppShell (adaptive bottom nav / side rail)
│       │   │   └── shared/          # LineBadge, ArrivalTimeBadge, LeafletMap, etc.
│       │   ├── screens/             # Full-page composables (7 screens)
│       │   ├── viewmodel/           # ViewModels with StateFlow (5 VMs)
│       │   └── App.kt              # Root composable, NavHost setup
│       ├── androidMain/             # MainActivity, TbApplication, Manifest
│       ├── desktopMain/             # Desktop main() entry point
│       └── iosMain/                 # MainViewController for SwiftUI bridge
│
├── .github/workflows/               # GitHub Actions CI/CD (release APK build)
├── gradle/libs.versions.toml       # Version catalog
└── shared/schemas/                 # Room database schema exports
```

---

## Technology Stack

| Concern | Library | Version |
|---------|---------|---------|
| Language | Kotlin | 2.1.10 |
| UI Framework | Compose Multiplatform | 1.7.3 |
| Design System | Material 3 | - |
| HTTP Client | Ktor | 3.0.3 |
| Serialization | kotlinx.serialization | 1.7.3 |
| Coroutines | kotlinx.coroutines | 1.9.0 |
| Protobuf | Custom `ProtobufReader` | - |
| State | StateFlow + ViewModel | 2.8.4 |
| Database | Room KMP + SQLite | 2.7.0 |
| Key-Value Storage | multiplatform-settings | 1.3.0 |
| DI | Koin | 4.0.2 |
| Navigation | Navigation Compose | 2.8.0-alpha12 |

---

## Screens & Navigation

### Bottom Navigation (3 items)

| Tab | Screen | Route |
|-----|--------|-------|
| Dashboard | `DashboardScreen` | `/dashboard` |
| Lines | `LinesScreen` | `/lines` |
| Announcements | `AnnouncementsScreen` | `/announcements` |

### Detail Screens (navigated to from other screens)

| Screen | Route | Accessed From |
|--------|-------|---------------|
| `LineDetailScreen` | `/lines/{lineId}` | Lines tab |
| `StopDetailScreen` | `/stops/{stopId}?lineId=X&direction=Y` | Line detail / Dashboard widget |
| `SettingsScreen` | `/settings` | Gear icon on Dashboard header |

Settings includes theme, language, notification subscriptions management, and about section.

### Adaptive Layout

- **< 840dp** (phones): Bottom `NavigationBar`
- **>= 840dp** (tablets, desktop): Side `NavigationRail`

---

## API Architecture

### Base URL
```
https://info.stb.ro/api/web/v2-6
```

### Authentication
1. Generate a UUID (`appId`) on first launch, persist it
2. POST `/proxy/user/auth` with `App-key` header
3. Server returns `userInfo` token
4. Token sent as `User-Info` header on all subsequent requests
5. On HTTP 401, clear token and re-authenticate

### Endpoints

| Method | Path | Format | Returns |
|--------|------|--------|---------|
| GET | `/lines?lang=ro` | Protobuf | `List<Line>` |
| GET | `/lines/{id}?lang=ro` | Protobuf | `LineDetail` |
| GET | `/lines/{id}/direction/{dir}?lang=ro` | Protobuf | `LineDetail` |
| GET | `/lines/stop?stop_id=X` | Protobuf | `StopInfo` |
| GET | `/lines/stops/{id}` | Protobuf | `StopDetails` |
| GET | `/lines/v2/{id}/vehicles/{dir}?lang=ro` | Protobuf | `List<VehiclePosition>` |
| GET | `/lines/v2/home/stops/{id}` | Protobuf | `List<Stop>` |
| GET | `/notifications?limit=50&offset=0` | JSON | `List<Announcement>` |
| POST | `/proxy/user/auth` | JSON | Auth token |

### Protobuf Decoding

The STB API returns binary Protocol Buffers (proto2). Instead of codegen (Wire), a custom lightweight `ProtobufReader` manually decodes the wire format:

```
Wire Type 0 → Varint (int32, int64, bool)
Wire Type 1 → 64-bit fixed (double)
Wire Type 2 → Length-delimited (string, bytes, embedded message)
Wire Type 5 → 32-bit fixed (float)
```

### Refresh Intervals

| Data | Interval | Reason |
|------|----------|--------|
| Lines list | 5 min stale | Rarely changes |
| Stop arrivals | 30 sec | Real-time tracking |
| Vehicle positions | 10 sec | Live map updates |
| Announcements | 5 min stale | Infrequent updates |

---

## Data Layer

### Room Database Schema

```
stb.db
├── widgets                    (id, stopId, stopName, size, groupName, visibleLines, hidden, sortOrder)
├── favorites                  (lineId)
├── notification_subscriptions (id, stopId, stopName, lineId, lineName, lineColor, direction, mode, thresholdMinutes, enabled)
└── dashboard_groups           (name, sortOrder, hidden)
```

### Stores

| Store | State | Backed By |
|-------|-------|-----------|
| `PreferencesStore` | theme, locale | multiplatform-settings |
| `FavoritesStore` | favorite line IDs | Room `favorites` table |
| `DashboardStore` | widgets, groups | Room `widgets` + `dashboard_groups` tables |
| `NotificationStore` | subscriptions | Room `notification_subscriptions` table |

### ViewModels

| ViewModel | Screen | Data Sources |
|-----------|--------|--------------|
| `DashboardViewModel` | Dashboard | `TransitRepository`, `DashboardStore` |
| `LinesViewModel` | Lines | `TransitRepository`, `FavoritesStore` |
| `LineDetailViewModel` | Line Detail | `TransitRepository` |
| `StopDetailViewModel` | Stop Detail | `TransitRepository` |
| `AnnouncementsViewModel` | Announcements | `TransitRepository` |

Notifications and Settings screens use stores directly (no ViewModel).

### What is NOT cached in SQLite

- **Lines list** — fetched fresh on launch (routes can change)
- **Announcements** — always from API
- **Arrival times** — real-time data, never stale-cached

---

## Domain Models

| Model | Description |
|-------|-------------|
| `Line` | Transit line (id, name, color, type, direction) |
| `Stop` | Stop location (id, name, lat/lng) |
| `Arrival` | Arrival time data (seconds, timetable flag) |
| `Vehicle` | Live vehicle position |
| `Announcement` | Service announcement with affected lines |
| `Direction` | TUR / RETUR enum |
| `TransportType` | BUS, TRAM, TROLLEYBUS |
| `Organization` | Transit operator |

---

## Internationalization

All UI text goes through `t(key, locale)`. Two locales supported: Romanian (`ro`) and English (`en`).

```kotlin
Text(t("nav.dashboard", locale))  // "Panou" or "Dashboard"
```

---

## Theme

Material 3 with system/light/dark mode support. User preference stored via `PreferencesStore`.

### Transport Type Colors

| Type | Color |
|------|-------|
| BUS | Blue `#1E88E5` |
| TRAM | Red `#E53935` |
| TROLLEYBUS | Green `#43A047` |

### Semantic Colors

- **Green** — Real-time GPS arrival data
- **Amber/Blue** — Scheduled timetable data
- **Red** — Delete actions, alerts

---

## Platform-Specific Code

Minimal platform code via `expect`/`actual`:

| Concern | Android | Desktop | iOS |
|---------|---------|---------|-----|
| Database | Room + Context | Room + File path | Room + NSFileManager |
| HTTP Engine | OkHttp | Java (java.net.http) | Darwin (URLSession) |
| Settings | SharedPreferences | java.util.prefs | NSUserDefaults |
| Entry Point | `MainActivity` | `main()` + `Window` | `MainViewController` |

---

## Build & Run

### Android
```bash
./gradlew :composeApp:assembleDebug
# APK at composeApp/build/outputs/apk/debug/
```

### Desktop
```bash
./gradlew :composeApp:run
# Or create distributable:
./gradlew :composeApp:createDistributable
```

### iOS

**Prerequisites:**
- macOS with Xcode installed
- JDK 17 (e.g. `brew install openjdk@17`)
- At least 16GB RAM recommended (Kotlin/Native compiler needs ~4GB)

**Steps:**

1. Clone the repo and make Gradle executable:
   ```bash
   git clone <repo-url>
   cd infotb-modern-client
   chmod +x gradlew
   ```

2. Build the Kotlin framework (first build takes a while):
   ```bash
   ./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
   ```

3. Open the Xcode project:
   ```bash
   open iosApp/iosApp.xcodeproj
   ```

4. In Xcode, select an iOS Simulator target and press **Cmd+R** to build and run.

**Troubleshooting:**

- **OOM / `Java heap space`**: Ensure `gradle.properties` has `org.gradle.jvm.args=-Xmx4096M` and `kotlin.native.jvmArgs=-Xmx4096M`. Kill stale daemons with `./gradlew --stop`.
- **Gradle lock file error**: Another Gradle process is running. Run `./gradlew --stop && pkill -f gradle`, then retry.
- **`CADisableMinimumFrameDurationOnPhone` crash**: The `Info.plist` must contain `<key>CADisableMinimumFrameDurationOnPhone</key><true/>`. This is already configured.
- **KSP / `ClassNotFoundException`**: Clean the Kotlin/Native cache with `rm -rf ~/.konan` and rebuild.

### Build Config

- Android: Min SDK 26, Target SDK 35, Compile SDK 36, App ID `ro.infotb.modern`
- Desktop: JVM 17, native distributions (Dmg, Msi, Deb)
- iOS: X64, Arm64, Simulator Arm64

---

## CI/CD

GitHub Actions workflow (`.github/workflows/release-apk.yml`):
- Triggers on tag push matching `v*`
- Sets up JDK 17 (Temurin) and Gradle
- Builds release APK
- Creates a GitHub Release with auto-generated release notes and the APK attached

---

## Dependencies Graph

```
:composeApp (UI)
    ├── :shared (Business Logic)
    ├── Compose Multiplatform (Runtime, Foundation, Material3)
    ├── Navigation Compose
    ├── Koin Compose
    └── Lifecycle ViewModel

:shared (Logic)
    ├── Ktor Client (HTTP)
    ├── kotlinx.serialization (JSON)
    ├── kotlinx.coroutines (Async)
    ├── Room KMP + SQLite (Structured persistence)
    ├── multiplatform-settings (Key-value persistence)
    ├── Koin Core (DI)
    └── UUID (App ID generation)
```
