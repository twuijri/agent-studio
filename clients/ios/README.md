# Hermes Studio Mobile — iOS

A native SwiftUI companion for [Hermes Studio](https://github.com/EKKOLearnAI/hermes-studio). It connects directly to the same REST and Socket.IO endpoints as Studio and keeps the bearer token in the iOS Keychain.

Current release: **1.4.0**. The public version is kept in sync with the Android app.

## Included

- Chats with streaming text, live reasoning, tool progress, attachments, voice input, downloadable agent files, pull-to-refresh and automatic last-message positioning.
- Group rooms with live people/agent messages and agent management.
- A mobile Kanban board with drag and drop, task creation, assignment and comments.
- Scheduled jobs, channels, skills, plugins, MCP servers, pets, memory and model management.
- Account, profiles, connection, appearance, Arabic RTL and all Studio configuration sections collected under More Settings.
- Hermes app icon and the short Home Screen name `H Studio`; the product name remains `Hermes Studio`.

## Connecting to Core Hub (M1)

- **QR pairing (recommended).** In Core Hub open *Settings → App connections → Create LAN pairing code* and scan it with **Scan QR code** on the login screen. The app calls `POST /api/auth/app-login` with a stable `device_code` (a UUID generated once and kept in the Keychain), the editable device name, `device_brand: Apple` and the hardware model. The token, its expiry and the connection id are stored together as one Keychain item.
- **Silent refresh.** `POST /api/auth/app-refresh` runs on launch when fewer than 7 days remain or the last refresh is older than 24 h, and once after any 401 (the failed request is retried with the new token). A 401 from the refresh itself signs the device out with a message. Decision logic: `AppTokenRefreshPolicy` (unit-tested).
- **Username/password** sign-in remains available under *Sign in with username and password*.
- **Canonical routes.** Sessions, messages, search, categories, usage, performance, group chat, files, STT, TTS and the REST chat-run endpoint use `/api/studio/*`. Profiles, config, models, skills, plugins, MCP, Kanban and jobs stay under `/api/hermes/*`. Bearer tokens are sent only in the `Authorization` header — never in a URL query — so downloads (agent files, workspace files, Kanban attachments) are fetched with `URLSession` and opened from a local copy.

## Voice input (M1)

- **Default: this device.** Apple Speech (`SFSpeechRecognizer` + `AVAudioEngine`) with partial results; the words appear live in the composer and the final text stays there. Nothing is sent automatically. Locale follows the app language (`ar-SA` / `en-US` / system).
- **Option: Core Hub server** (*Settings → Voice → Voice input*). The app checks `GET /api/studio/stt/profile-status` first and explains any `reason`, records 16 kHz mono 16-bit PCM WAV, and posts it to `POST /api/studio/stt/transcribe` as multipart (`provider`, optional `language`, file part `audio` = `voice.wav`). `no_speech_detected` and every other failure are shown in the banner.
- When on-device recognition is unavailable or its permission is denied, the app falls back to the server path for that attempt.
- Mic button states: idle → listening → transcribing → error.

Required Info.plist strings: `NSCameraUsageDescription`, `NSMicrophoneUsageDescription`, `NSSpeechRecognitionUsageDescription`.

## Branding and distribution

The install target includes every required iPhone and iPad icon size directly, generated from the 1024 px master. The App Store asset catalog is retained in `Design/Assets.xcassets`, and the editable vector master is `AppIcon.svg`. When preparing an App Store archive with a current Xcode release, add that catalog to the app target and select `AppIcon` as the App Icons Source.

## Install on a personal iPhone

1. Open `HermesStudio.xcodeproj` in Xcode.
2. Select the `HermesStudio` target, open **Signing & Capabilities**, and choose your Apple ID's Personal Team.
3. Connect the iPhone, choose it as the run destination and press Run.
4. If iOS asks, enable Developer Mode and trust the developer profile in **Settings > General > VPN & Device Management**.

A free Personal Team installation normally needs to be signed again after seven days. TestFlight and App Store distribution require the paid Apple Developer Program.

## Project structure notes

`HermesStudio.xcodeproj` uses Xcode 16 synchronized folders (`PBXFileSystemSynchronizedRootGroup`), so every `.swift` file under `HermesStudio/` and `HermesStudioTests/` is part of the matching target automatically; no `PBXBuildFile` entries are needed when adding files. Unit tests for pure logic (QR payload parsing, refresh policy, STT contract parsing) live in `HermesStudioTests/HermesStudioTests.swift`.
