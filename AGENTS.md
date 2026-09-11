# OpenHands Android

Thin Cloud client. Agent runtime lives on https://app.all-hands.dev, not on-device.

## Build

```
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Needs JDK 17 + Android SDK. This workspace often has neither.

## Stack pinned 2026-09-11

AGP 9.4.0 (built-in Kotlin, do **not** apply `org.jetbrains.kotlin.android`), Gradle 9.6.0, Kotlin 2.4.20 Compose compiler plugin, Compose BOM 2026.09.00, compileSdk 37 (`platforms;android-37.0`), targetSdk 36, minSdk 26.

## Cloud API (1.59.1)

- Auth: Cloud OAuth device flow (`POST /oauth/device/authorize`, poll `POST /oauth/device/token`) or pasted API key. Headers: `Authorization: Bearer` and `X-Access-Token`
- Repos: `GET /api/v1/git/repositories/search?provider=github`
- Start: `POST /api/v1/app-conversations` → start-task
- Status: `GET /api/v1/app-conversations/start-tasks?ids=` then `GET /api/v1/app-conversations?ids=`
- Events: `GET /api/v1/conversation/{id}/events/search`
- Follow-up: `POST /api/v1/app-conversations/{id}/send-message` (`run: true`)
- Resume paused sandbox: `POST /api/v1/sandboxes/{id}/resume`
- Canvas: `{baseUrl}/canvas` in Custom Tabs
- Offline cache: last conversations + repos in `openhands_secure` (encrypted). Do not start tasks while offline.

## Security

API key in EncryptedSharedPreferences (`openhands_secure`). `allowBackup=false`. HTTPS only. No logging of the key. ESP is deprecated in security-crypto 1.1.0; keep it until a Tink+DataStore migration is worth the extra code. Recreate the prefs file if Keystore unwrap fails.

## Do not add

Hilt, Room, Retrofit, local agent runtime, certificate pinning (Let's Encrypt rotates).
