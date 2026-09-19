# Core Hub — iOS

The native SwiftUI client of [Core Hub](https://github.com/twuijri/core-hub) (twuijri's fork of Hermes Studio). It talks to the same `/api/studio/*` REST routes and Socket.IO namespaces as the web client and keeps the bearer token in the iOS Keychain.

The version follows the core (`package.json` at the repository root); see `docs/mobile/PLAN.md`.

## Design system (M2)

`HermesStudio/Theme/CoreHubTokens.swift` is the single source of the "Pure Ink" design system from `docs/mobile/DESIGN-SPEC.md`:

- `CoreHubTokens.PaletteHex.light/.dark` — the raw hex values (unit-tested); `CoreHubTokens.Palette.*` — dynamic `Color`s that follow the effective colour scheme (system, or the user's light/dark choice through `preferredColorScheme`). State formulas: `hover` = accent @ 6 %, `selected` = accent @ 12 %, `inputBorderIdle` = accent @ 18 %.
- `Typography` (14 base; 16/600 titles; 13 nav/session; 12 author; 11 meta; 10/600 uppercase group headers; monospaced code; inputs never below 16 pt), `Radius` (6/8/10/14/18/999/4/5), `Shadow` (card, composer light/dark, focused), `Motion` (150/250 ms), `Layout` (sidebar 240, header 60, drawer ≤ 300, …).
- The app tint is `Palette.accent`; every view touched in M2 uses tokens only.

Icons: `Theme/IconPath.swift` parses the 24-viewBox SVG path data written in the spec (`M/m L/l H/h V/v C/c A/a Z` plus circles and rounded rects; arcs become cubic curves) and `Theme/CoreHubIcons.swift` lists the rail, segment and header icons drawn with stroke 1.8 and round caps. Directional icons (`chevronForward`, `back`, `chat`, `workflow`, `history`) mirror in RTL. `CoreHubMarkView` draws the vector Core Hub mark.

Agent avatars: `Theme/AgentAvatar.swift` maps a session's runtime id to the bundled assets `Agent-*` (copied from `packages/client/public/coding-agents/`; SVGs are used as vector imagesets, large PNGs were downscaled to 256 px) exactly like the web's `chat-agent-avatar.ts`.

## Navigation (M2)

`Features/RootShell.swift` replaces the old tab bar with the web's mobile layout: a navigation bar with a hamburger and an off-canvas drawer (`Features/SidebarDrawer.swift`, 250 ms slide, 40 % scrim, swipe to close, edge swipe to open):

1. Primary rail — New Chat, Search, Device connections, Agent Manager (super-admin only), Models.
2. Conversation switch — Chat · Group Chat · Workflow · History (`ConversationMode`).
3. The list of the current mode — sessions (`Features/SessionListView.swift`), group rooms or workflows.
4. Footer — profile selector, model selector (default model for new chats), Sign Out + username chip, connection dot from `GET /health`, "Core Hub v{server version}", language and theme switches, and the gear that swaps the drawer to the settings list (Logs, Usage, Performance (sa), Skills Usage, Theme, Pets, Profiles (sa), Settings) with a Back row.

The Settings page (`Features/SettingsView.swift`) follows the web order — Current Account, Account Management (sa, placeholder), Webhooks (sa), Display, Proxy, Compression, Privacy, Models — and keeps every previous screen reachable under Advanced (Agent, Memory, Session reset, Approvals, Skill approvals, Voice, Gateway auto-start, Ekko, Runtime Versions) and Workspace tools (Global Agent, Files, Journey, Scheduled Jobs, Kanban, Channels, Skills, Plugins, MCP, Connections).

Session list rows follow `SessionListItem.vue`: pin, unread dot, title with per-string direction, time (`SessionTimeFormatter`: same day → `HH:mm`, else "Sep 18"), 18 pt agent avatar, profile chip and category tag; long-press context menu (rename, pin, category, archive, session settings, delete), swipe to delete/archive and a 50 % ✕. Groups: RECENT (count 1–100, default 10, gear to change) → Pinned → categories → Uncategorized (`SessionGrouping`, `SessionBrowserPrefs`; pins, collapse state and the recent count are local to the device like the web's localStorage prefs).

The chat header shows the title (16/600, per-string direction) with the workspace chip (last path segment) and a ⋯ menu (refresh, new conversation, new conversation with agent, fork, rename, session settings, archive, delete).

## Chat (M3)

`Features/ConversationView.swift` owns one conversation; everything below it lives in `Features/Chat/`.

- **Socket.** `Core/SocketIO.swift` keeps one `/chat-run` connection open for the whole conversation (`auth: { token }`, `query: { profile, platform: 'ios' }` — the platform registers the phone as a mobile device target), emits `app.resume` on every connect, reconnects with exponential backoff (≤ 30 s) and immediately when the app returns to the foreground. `ChatSocket.events(for:json:sessionID:)` is the pure server→client mapping (`message.delta/interim`, `reasoning.*`, `tool.started/completed/failed` with truncation flags, `subagent.*`, `run.*`, `approval.*`, `clarify.*`, `compression.*`, `abort.*`, `usage.updated`, `session.command`, `session.title/workspace/settings.updated`, `resumed`, `location.requested`, `calendar/reminder/health.requested`). Client→server: `run`, `abort`, `approval.respond`, `clarify.respond`, `insert/steer/cancel_queued_run`, `location.respond`; calendar/reminder/health requests are answered `denied` for now.
- **State.** `Core/ChatStream.swift` — `ChatStreamState` and the pure `ChatRunReducer` (lines of kind user/assistant/system/command/error/interaction, interim text, thinking timestamps, tool upserts, queue, compression, abort, peer messages, settings, location), plus `ToolSummary`, `ThinkingFormat`, `ReferenceQuote`, `ContextUsageFormat` and `ReasoningEffortOption`. All unit-tested in `HermesStudioTests/ChatParityTests.swift`.
- **Rows.** `MessageRow.swift` (user ≤ 75 %, assistant with 22 pt agent avatar ≤ 80 %, system with a 3 pt warning border, command, error, streaming dots, attachment chips), `ToolSummaryCard.swift` (30 pt header, chevron, wrench, "N tools", ≤ 3 names + N, ✓/•••/Error; 11 pt mono lines with Thinking/Arguments/Result), `ThinkingBlock.swift` (💭 Thinking · Observed {duration} · {count} chars), `MessageActionRow.swift` (speech play/pause, copy, reference, fork, time), `InteractionCard.swift` (approval choices / clarification answer inline in the stream), `ChatBanners.swift` (queued runs, compression, abort, reconnecting, workspace changes).
- **Composer.** `ChatComposer.swift`: radius-18 card, min 150 pt, reference chip, attachment strip with progress and cancel, 16 pt input with per-string direction (never auto-focused), toolbar [+ attach (camera / photo library / files)] [🧠 reasoning] [⚙ Voice mode · Show tool calls · Push] [model ≤ 190 pt] … [mic 30] [send / stop 30] and a queue button while a run streams; context indicator top-end ("45.0k / 256.0k · remaining 211.0k", amber above 80 %). Labels collapse to icons under 380 pt.
- **Attachments.** `Core/AppUploads.swift`: chunked `/api/studio/app-uploads` (client id, ≤ 256 KiB raw chunks, complete → `{ name, path }` used in the content block, DELETE on cancel, 50 MB cap). Pickers in `AttachmentPickers.swift`.
- **Media.** `Core/MediaLinks.swift` + `MediaPlayers.swift`: absolute paths and `device://<id>/<path>` links stream from `/api/studio/files/download?path=…` with the bearer header (`AVURLAsset` + `AVURLAssetHTTPHeaderFieldsKey`); video (mp4/webm/mov/m4v) and audio (mp3/wav/ogg/m4a/aac/flac) play inline, other files are download cards, device files carry the "On the device" badge.
- **Speech.** `Core/MessageSpeaker.swift`: `POST /api/studio/tts/synthesize` (audio/mpeg) per message with play/pause, optional auto-play of replies (Voice mode), `AVSpeechSynthesizer` fallback when the server call fails.
- **Consent.** `LocationConsentSheet.swift` + `Core/LocationConsent.swift`: `location.requested` → sheet with the purpose → one CoreLocation fix → `location.respond` (`success` with WGS-84 coordinates, `denied`, or `error`). Requires `NSLocationWhenInUseUsageDescription`.

## Connecting to Core Hub (M1)

- **QR pairing (recommended).** In Core Hub open *Settings → App connections → Create LAN pairing code* and scan it with **Scan QR code** on the login screen. The app calls `POST /api/auth/app-login` with a stable `device_code` (a UUID generated once and kept in the Keychain), the editable device name, `device_brand: Apple` and the hardware model. The token, its expiry and the connection id are stored together as one Keychain item.
- **Silent refresh.** `POST /api/auth/app-refresh` runs on launch when fewer than 7 days remain or the last refresh is older than 24 h, and once after any 401 (the failed request is retried with the new token). A 401 from the refresh itself signs the device out with a message. Decision logic: `AppTokenRefreshPolicy` (unit-tested).
- **Username/password** sign-in remains available under *Sign in with username and password*.
- **Canonical routes.** Sessions, messages, search, categories, usage, performance, group chat, files, STT, TTS and the REST chat-run endpoint use `/api/studio/*`. Profiles, config, models, skills, plugins, MCP, Kanban and jobs stay under `/api/hermes/*`. Bearer tokens are sent only in the `Authorization` header — never in a URL query — so downloads (agent files, workspace files, Kanban attachments) are fetched with `URLSession` and opened from a local copy.

## Voice input (M1)

- **Default: this device.** Apple Speech (`SFSpeechRecognizer` + `AVAudioEngine`) with partial results; the words appear live in the composer and the final text stays there. Nothing is sent automatically. Locale follows the app language (`ar-SA` / `en-US` / system).
- **Option: Core Hub server** (*Settings → Display → Voice input*). The app checks `GET /api/studio/stt/profile-status` first and explains any `reason`, records 16 kHz mono 16-bit PCM WAV, and posts it to `POST /api/studio/stt/transcribe` as multipart (`provider`, optional `language`, file part `audio` = `voice.wav`). `no_speech_detected` and every other failure are shown in the banner.
- When on-device recognition is unavailable or its permission is denied, the app falls back to the server path for that attempt.
- Mic button states: idle → listening → transcribing → error.

Required Info.plist strings: `NSCameraUsageDescription`, `NSMicrophoneUsageDescription`, `NSSpeechRecognitionUsageDescription`, `NSPhotoLibraryUsageDescription`, `NSLocationWhenInUseUsageDescription`.

## Branding and distribution

Display name **Core Hub** (`CFBundleDisplayName`, both locales). The app icon is the Core Hub mark (`packages/client/public/core-hub-mark.svg`, #101010) at 60 % width on the splash colour #f7f7f4: `swift Scripts/generate_app_icons.swift HermesStudio/Resources Design/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png` regenerates every size on macOS (the checked-in PNGs were rasterised from the same geometry with PIL on Linux). `AppIcon.svg` is a copy of the vector master. The in-app logo (`AppMark`) prefers the server's custom logo and otherwise draws the mark; `CoreHubLogo` holds `logo.png`.

The install target includes every iPhone and iPad icon size directly; the App Store asset catalog is retained in `Design/Assets.xcassets`. When preparing an App Store archive, add that catalog to the app target and select `AppIcon` as the App Icons Source.

## Arabic / RTL

`environment(\.layoutDirection)` follows the app language. Content (session titles, chat titles, messages) resolves direction per string (`DirectionalText`, `MarkdownText.layoutDirection`); code, paths and model ids are forced LTR (`TechnicalText`); spacing is logical (leading/trailing) and directional icons mirror.

## Install on a personal iPhone

1. Open `HermesStudio.xcodeproj` in Xcode.
2. Select the `HermesStudio` target, open **Signing & Capabilities**, and choose your Apple ID's Personal Team.
3. Connect the iPhone, choose it as the run destination and press Run.
4. If iOS asks, enable Developer Mode and trust the developer profile in **Settings > General > VPN & Device Management**.

A free Personal Team installation normally needs to be signed again after seven days. TestFlight and App Store distribution require the paid Apple Developer Program.

## Project structure notes

`HermesStudio.xcodeproj` uses Xcode 16 synchronized folders (`PBXFileSystemSynchronizedRootGroup`), so every `.swift` file under `HermesStudio/` and `HermesStudioTests/` is part of the matching target automatically; no `PBXBuildFile` entries are needed when adding files. Unit tests for pure logic live in `HermesStudioTests/` (`HermesStudioTests.swift`: contracts, QR pairing, refresh policy, STT; `CoreHubDesignTests.swift`: tokens, icon path parser, session grouping, time formatter, avatar mapping, browser prefs; `ChatParityTests.swift`: socket event mapping, stream reducer, tool/thinking/context formatting, chunked uploads, media links, location payload).
