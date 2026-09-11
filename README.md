# OpenHands Android

Thin native client for [OpenHands Cloud](https://app.all-hands.dev/) and [Agent Canvas](https://app.all-hands.dev/canvas).

The agent does **not** run on the phone. Cloud owns the sandbox and the loop. Closing or killing the app cannot stop a started task.

Checked against live Cloud API `1.59.1` and docs on **2026-09-11**.

## What it does

- Sign in with Cloud OAuth device flow (`POST /oauth/device/authorize` + `/oauth/device/token`) or paste an API key. The key is stored in `EncryptedSharedPreferences` (Android Keystore). Backups of that file are disabled.
- Pick a GitHub repo from `GET /api/v1/git/repositories/search` or type `owner/name`.
- `POST /api/v1/app-conversations` starts a conversation (`run: true`).
- WorkManager polls start-tasks + conversation status while you are away and posts a notification on **status change only**.
- Offline: last conversation list + repo list stay on device. Starting a new task needs the network.
- Chat and follow-ups use `POST /api/v1/app-conversations/{id}/send-message`.
- Canvas / full Cloud UI open in Chrome Custom Tabs (`https://app.all-hands.dev/canvas`).

## Stack (2026-09-11)

| Piece | Version |
| --- | --- |
| AGP | 9.4.0 (built-in Kotlin; no `kotlin-android` plugin) |
| Gradle | 9.6.0 |
| Kotlin | 2.4.20 (Compose compiler plugin) |
| Compose BOM | 2026.09.00 |
| compileSdk | 37 (`platforms;android-37.0`) |
| targetSdk | 36 |
| minSdk | 26 |
| WorkManager | 2.11.2 |
| security-crypto | 1.1.0 |
| OkHttp | 5.5.0 |
| GitHub Actions | checkout v7.0.1, setup-java v6.0.1, upload-artifact v7.0.1, setup-android v4.0.1, action-gh-release v3.0.3 |

No Hilt, no Room, no Retrofit. One Activity, `org.json`, OkHttp.

## Build

Needs JDK 17 and Android SDK (`platforms;android-37.0` — AGP looks for `platforms/android-37`). Open in Android Studio Panda / Quail (2026) or:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

**Sign in:** tap **Sign in with Cloud** (browser OAuth, same device flow as the CLI) or paste a key from `https://app.all-hands.dev/settings/api-keys`.

## Releases

Push a version tag. GitHub Actions builds the debug APK and publishes a GitHub Release:

```bash
git tag v0.2.0
git push origin v0.2.0
```

## Why the agent survives leaving the app

1. Start returns a **start-task**. Cloud provisions the sandbox.
2. The phone only keeps `{startTaskId, conversationId}` locally.
3. WorkManager (network-required, 15 min periodic + 90 s one-shot while jobs exist) asks Cloud for `sandbox_status` / `execution_status`.
4. Terminal states: `finished` / `error` / `stuck` / `deleting`, or sandbox `ERROR` / `MISSING`.
5. Tapping a notification opens that conversation (`singleTop` + `conversation_id` extra).

If a sandbox is `PAUSED` (Cloud concurrency limits), resume from the conversation screen or the web UI.

## Security

- HTTPS only, no cleartext.
- API key never logged, never backed up, wiped on sign-out.
- Auth headers: `Authorization: Bearer` and `X-Access-Token` (both accepted by Cloud 1.59.1).
- Release builds minify + shrink resources.

## Layout

```
app/src/main/java/dev/openhands/android/
  data/       API, models, encrypted store
  work/       TaskWatchWorker
  notify/     status notifications
  ui/         Compose screens
```
