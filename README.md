# NotreMock

> **Bilingual Documentation:** **English** | [فارسی (Persian)](README.fa.md)

**NotreMock** is a lightweight, privacy-first, open-source Android Mock Location application built with Kotlin and Jetpack Compose. Designed for **99% of Android devices (Android 7.0+ / API 24+)** with a clean, minimal UI — while also providing clearly separated **Shizuku** and **Root / LSPosed** capabilities for advanced users.

---

## Key Features

- **Minimal One-Tap Home UI & Home Screen Widget**: Instantly resume your last selected city & privacy settings with one tap from the app, **Home Screen Widget**, or **Quick Settings Tile**.
- **Guided Setup Tour & Live Diagnostics**: Automatic step-by-step onboarding with brand-specific instructions (**Samsung, Xiaomi/HyperOS, Huawei/Honor, Oppo/Realme/OnePlus, Stock**) for unlocking Developer Options, setting Mock Location, and preventing background battery killing—plus live pinpointing of exact setup blockers.
- **Offline World Cities Database**: ~2,684 world national capitals and provincial/state capitals (including all 31 Iranian provincial capitals) searchable in both **English** and **Persian** — fast, lightweight, and 100% offline.
- **Zero VPN Conflict**: Does **not** occupy Android's `VpnService` slot. Run any VPN (V2Ray, WireGuard, OpenVPN, etc.) simultaneously while all apps read your mocked GPS location.
- **Smart App Bypass (Non-Root Auto-Pause)**: Uses standard `UsageStatsManager` (`Usage Access`) only while mocking is active to pause mock GPS automatically when you open bypassed apps (e.g., Neshan, Balad, Google Maps, Waze, Snapp, Tapsi) and resume when you leave. No Accessibility Service required.
- **Kill Switch & Leak Watchdog**:
  - Instant **Kill Switch** in the app, notification shade, and **Quick Settings Tile** (stop completely or jump to a Safe Fallback City).
  - **Watchdog Alert**: Sounds a high-priority warning if Android revokes mock permission while active.
- **Boot Auto-Start**: Automatically restores your last mock location when the device restarts.
- **Privacy & Leak Auditor**: Live privacy score checking Mock App status, TimeZone match, Auto-TimeZone, Wi-Fi/Bluetooth background scanning, cellular/SIM exposure, and an **opt-in** IP/VPN country consistency check (disabled by default for 100% offline use).
- **Natural GPS Realism**: Configurable GPS drift (jitter), random city-radius offset, update interval, and altitude.
- **Bilingual (English & Persian RTL)**: In-app language switcher and full RTL layout support.

---

## Non-Root vs Shizuku vs Root (LSPosed) Capabilities

Android's security sandbox enforces strict limits on non-root apps. NotreMock is designed primarily for **standard non-root devices**, while isolating pro/root tools in a dedicated section:

| Capability | Standard Android (No Root) | Shizuku (No Root + Wireless Debugging) | Root + LSPosed |
| :--- | :---: | :---: | :---: |
| System-wide Mock GPS + Fused Location | ✅ Full | ✅ Full | ✅ Full |
| Simultaneous VPN Compatibility | ✅ Full | ✅ Full | ✅ Full |
| Boot Auto-Start & Quick Settings Tile | ✅ Full | ✅ Full | ✅ Full |
| Foreground App Auto-Pause (Neshan, Balad, Maps) | ✅ Via Accessibility | ✅ Via Accessibility | ✅ True Per-App |
| Background App Bypass (e.g. Find My Device) | ❌ OS Limitation | ❌ OS Limitation | ✅ True Per-App |
| Automatic System TimeZone Switch | ⚠️ Quick Link / Manual | ✅ Automatic | ✅ Automatic / Per-App |
| Hide / Spoof SIM Info (`MCC/MNC`, `Cell ID`) | ❌ OS Limitation (Use Airplane + Wi-Fi) | ❌ OS Limitation | ✅ Full Per-App Spoof |
| Hide `isMock()` / `isFromMockProvider()` Flag | ❌ OS Limitation | ❌ OS Limitation | ✅ Hidden |

---

## Quick Setup (Non-Root)

1. Download the latest APK from **GitHub Releases** (or build via GitHub Actions).
2. Enable **Developer Options**: `Settings → About Phone → Tap Build Number 7 times`.
3. Inside **Developer Options**, tap **Select mock location app** and choose **NotreMock**.
4. Open NotreMock, grant **Location** permission (required by Android 14+ for location foreground services) and exempt it from **Battery Optimization**.
5. Pick a city and tap **Start**.

### Tips for Maximum Privacy
- Match your **VPN country** with the mocked city's country.
- Turn **OFF** `Wi-Fi Scanning` and `Bluetooth Scanning` in Android Location Settings.
- Turn **OFF** `Automatic TimeZone` and match the city's TimeZone (or enable Shizuku mode in Advanced Settings).
- For zero cellular/SIM leaks on non-root devices, enable **Airplane Mode + Wi-Fi**.

---

## Build & CI/CD

You do not need Android Studio locally — **GitHub Actions** automatically builds, unit-tests, and lints every push, and publishes signed APKs on `v*` tags:

```bash
# Local build (requires JDK 17 + Android SDK)
./gradlew testDebugUnitTest assembleDebug
```

To create a release on GitHub:
```bash
git tag v1.0.0
git push origin v1.0.0
```
*(Optionally configure `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, and `SIGNING_KEY_PASSWORD` in GitHub Repository Secrets for a custom release key; otherwise CI safely signs with a fallback key so APKs remain installable.)*

---

## Architecture

- `app/src/main/assets/cities.tsv` — Curated GeoNames capitals & provincial/state capitals dataset (~2,684 cities).
- `app/src/main/java/com/notremock/app/core/location/` — `MockLocationEngine`, `MockState`.
- `app/src/main/java/com/notremock/app/core/privacy/` — `PrivacyAuditor`, `TimeZoneController`, `SystemIntentHelper`.
- `app/src/main/java/com/notremock/app/service/` — `MockForegroundService`, `MockTileService`, `AppMonitorAccessibilityService`.
- `app/src/main/java/com/notremock/app/xposed/` — Embedded LSPosed module (`NotreMockXposedEntry`).
- `xposed-api/` — Compile-only Xposed stub module (adds 0 bytes to APK).

## Contributing

Contributions, new city translations, and privacy improvements are welcome! Open an issue or submit a Pull Request.

## License

[MIT](LICENSE)
