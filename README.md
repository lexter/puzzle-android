# Slide Puzzle (Android)

Native Kotlin + Jetpack Compose rewrite of the Compose Multiplatform app in `../puzzle`. Same features and behavior: 4×4 to 10×10 grids, time attack and free play, a per-size top-10 leaderboard, and offline LAN races against Android or iPhone.

## Build

```bash
./gradlew :app:assembleDebug
```

```bash
./gradlew :app:testDebugUnitTest
```

Unit tests, including UI tests, run on the JVM with Robolectric. The app uses the same application ID and the same DataStore file as the old app, so an installed copy keeps its settings and scores.

## Layout (`app/src/main/java/com/lexterlabra/puzzle`)

- `game`: board model, shuffle, clock.
- `p2p`: wire framing, JSON messages, plain `java.net` sockets, NSD discovery (`_slidepuzzle._tcp`), and the match state machine.
- `data`: DataStore-backed settings and scores.
- `ui`: Compose screens and the Candy Pop glass theme.

## Git hooks

```bash
scripts/install-hooks.sh
```

- **pre-commit**: ktlint (`./gradlew :app:ktlintCheck`, rules in `.editorconfig`) when Kotlin files are staged, plus `scripts/privacy-guard.sh`. Files with both staged and unstaged changes must be fully staged or stashed first, so the check sees exactly what's committed.
- **pre-push**: compiles the app, unit tests and instrumented tests.

Skip once with `--no-verify`.

## CI (`.github/workflows/ci.yml`)

Runs on pushes to `main`, pull requests and manual dispatch.

1. **Code integrity review**: gitleaks over the full history, `scripts/privacy-guard.sh` (blocks the device name, hardware/advertising IDs, logging, cleartext traffic, world-readable and external storage), mobsfscan (OWASP MASVS rules; justified exceptions in `.mobsf`), ktlint, and Android lint with its security checks promoted to errors.
2. **Build and unit test**: `assembleDebug` and the Robolectric unit tests.
3. **On-device tests**: `connectedDebugAndroidTest` (`app/src/androidTest`) on the newest Google APIs x86_64 emulator image (`EMULATOR_API_LEVEL` in the workflow).
4. **Firebase App Distribution**: on `main` and manual runs, after 1–3 pass: a signed release APK, `versionCode` = run number. Skipped with a notice until configured.

| Name | Kind | Value |
| --- | --- | --- |
| `ANDROID_KEYSTORE_BASE64` | secret | Release keystore (.jks), base64-encoded. Keep the same one forever: testers can only update over builds signed with it |
| `ANDROID_KEYSTORE_PASSWORD` | secret | Keystore password |
| `ANDROID_KEY_ALIAS` | secret | Key alias |
| `ANDROID_KEY_PASSWORD` | secret | Key password |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | secret | Google service account key with the Firebase App Distribution Admin role |
| `FIREBASE_ANDROID_APP_ID` | variable | Firebase Android app ID (`1:…:android:…`) |
| `FIREBASE_TESTER_GROUPS` | variable | Optional; comma-separated group aliases (default `testers`) |

Locally, a release build is signed when `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` and `ANDROID_KEY_PASSWORD` are set; otherwise it's unsigned.
