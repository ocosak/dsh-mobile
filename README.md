# DSH Mobile — native Android client + dsh plugin (v1)

**iOS-style, native Android APK (option B) for remotely using
DeepSeek Harness (dsh). This is NOT a WebView wrapper of the official
Web GUI.** Screens are native Jetpack Compose, styled iOS-like:
large titles, grouped cards, iOS-style navigation, SF-style system
typography, safe-area-aware padding, light/dark aware.

> **Honest scope:** v1 is a working native shell + pairing +
> sessions/chat/approval UI + a host plugin skeleton. It is **not**
> full desktop parity, and it has **not yet been verified against a
> real dsh host**. The session/chat endpoints are adapter
> placeholders (see "Verification" below). Do not claim otherwise.

## Latest-iOS styling — how it is implemented in Compose

UI quality is a top priority for v1, following the **latest iOS**
design language (Liquid Glass era), adapted to Android Compose —
no native iOS blur API is claimed or used:

- **Liquid Glass surfaces:** `GlassCard` (in
  `ui/screens/IosComponents.kt`) uses a translucent surface
  (`IosGlass.surface`, ~72–88% opacity), a specular top-edge
  highlight (vertical white gradient fading to transparent),
  a 1dp translucent border, soft shadows, and 24dp radii.
  The floating tab bar and the chat composer use the same
  stronger glass token at 28dp/24dp.
- **Floating glass tab bar:** `MainActivity` renders the bottom
  navigation inside a floating, rounded-28dp glass Surface with
  16dp side insets and bottom inset padding. Scaffold still
  reserves its height, so content (including the chat composer)
  never slides underneath it.
- **Large-title navigation:** every screen uses `LargeTitle`
  (34sp bold) with a secondary subtitle line, grouped sections
  with uppercase section labels, and inset grouped lists.
- **Geometry & typography:** cards/bars at 20–28dp, buttons at
  14dp with 44dp+ minimum height, SF-style system typography
  hierarchy (34/28/20/17/16/13sp), iOS blue accent
  (`#007AFF` light / `#0A84FF` dark), light **and** dark themes
  via `IosGlass` tokens resolved per theme.
- **Interaction details:** 44dp+ touch targets everywhere,
  16sp+ inputs (`IosTextField`) so fields are never cramped,
  ripple/press states from Material buttons/clickables, safe-area
  handling via Scaffold padding + screen insets, and swipe-left
  to delete on session rows (with a visible Delete action).
- **Chat as modern iOS messaging:** bubbles aligned by role,
  grouped consecutive messages (2dp gap, flattened joining
  corner), tool-call cards in the same glass language, and
  approval as a bottom sheet-style glass card with a drag
  handle — not a raw Android dialog.
- **Empty / loading / error states:** `EmptyState`,
  `LoadingState`, and `ErrorState` in the same glass language
  (sessions empty state, chat empty state, session load errors).

## Architecture (text diagram)

```
Android phone (DSH Mobile, native Compose)
  │  Pair screen: paste server URL + one-time pairing token
  │  Settings: server URL, model, clear credentials
  │  Sessions / Chat: native lists + streaming messages + tool cards
  │  Credential: EncryptedSharedPreferences, header-only
  ▼
  https / LAN http (user-chosen)
  │  x-dsh-remote-device: <device credential>   ← never logged, never in URL
  ▼
dsh host (your computer, `dsh web`, default http://127.0.0.1:3080)
  ├── dsh-mobile-remote plugin (plugin/)
  │     ├── /api/pair/status   (minimal, public)
  │     ├── /api/pair/accept   (loopback-only control plane)
  │     ├── /api/pair/revoke   (this device)
  │     └── /remote/*          (gated proxy → loopback dsh, header consumed)
  └── dsh itself (sessions, agent, tools, files, shell)
        ▲
        └── LAN forwarder OR manual tunnel (operator-run, optional)
            Never expose dsh directly to the public internet.
```

## Security — read this first

dsh can run shell commands and edit files. A paired device is a
**full-control credential**: it can drive the host agent exactly
like the local desktop does.

- Pairing token: time-limited (10 min default), single active token;
  refreshing invalidates the old one. It is a one-time grant.
- Device sessions: revocable per device, or revoke-all from the host.
  Revocation takes effect on the next request.
- Device credential: stored on Android only in
  EncryptedSharedPreferences. It is sent only as the
  `x-dsh-remote-device` header, never logged, never put in a URL —
  except the one-time grant flow, which the reference design itself
  uses (`/pair-app?grant=<one-time>`).
- No API key, password, tunnel token, or real server URL is baked
  into this repo. All of those are entered in the app's settings.
- LAN: keep the dsh web server on loopback and use a runtime LAN
  forwarder, or start `dsh web --host <lan-address>`. Do not use
  `--host 0.0.0.0` without pairing + revocation.
- Tunnel: v1 does **not** bundle Cloudflare or a fixed relay.
  Options: (a) same-LAN only, (b) a manual tunnel you run
  (e.g. `cloudflared tunnel --url http://127.0.0.1:3080`, then set
  its URL as the app's server URL), or (c) use the reference
  plugin `@linxin666/dsh-remote-web-ui`
  (https://github.com/zhu1090093659/dsh-web) for one-click
  tunnelling — that plugin serves the official Web GUI, not this
  native app, so its tunnel is a transport you can reuse, not a
  drop-in for this app's `/remote` contract until the adapter is
  verified.

Design reference (security model only, code not copied):
https://github.com/zhu1090093659/dsh-web/blob/dev/packages%2Fdsh-remote-web-ui%2FREADME.zh.md
— QR/time-limited pairing, per-device sessions, gated `/remote`,
LAN forwarding, optional Cloudflare tunnel.

dsh upstream: https://github.com/deepseek-ai/deepseek-harness
(dsh is in developer preview; breaking changes are possible).
Plugin ecosystem reference:
https://github.com/awesome-dsh-plugin/awesome-dsh-plugin

## Install the plugin on a dsh host

```bash
# Verify the composed plugin tree first (does not boot):
dsh --profile web --dump-config

# Install (after verifying the cordis patch shape for your dsh version):
dsh plugin --profile web add dsh-mobile-remote
dsh web
```

Then open the host's Mobile remote settings card and mint a
pairing token. See `plugin/README.md` for the adapter-needed items.

## Pair the APK

1. Install the debug APK (below) on Android.
2. Open the app → **Pair** tab.
3. Enter the server base URL: LAN address
   (e.g. `http://192.168.1.10:3080`) or your tunnel URL.
4. Paste the pairing token from the host panel. Tap **Pair**.
   v1 pairing is **paste-based**; QR scanning is a roadmap item
   and is labelled as such in the app.
5. Go to **Sessions** → create a session → open it → chat.
   Tool actions that need approval show Approve / Deny cards —
   approving lets the host agent run that action.
6. To disconnect: **Pair → Disconnect / revoke this device**,
   or **Settings → Clear credentials** (then also revoke on the
   host so the host stops accepting that device).

## Build the APK

Requirements: JDK 17, Android SDK (platforms + build-tools for
API 34). The project targets minSdk 26, targetSdk 34.

### GitHub Actions (recommended if local memory is low)

`.github/workflows/android.yml` builds on every push to `main`,
on pull requests, and on manual dispatch:

- Runner: Ubuntu, Temurin JDK 17, Android SDK, Gradle 8.7
- Output: `android/app/build/outputs/apk/debug/app-debug.apk`,
  uploaded as the `dsh-mobile-debug-apk` artifact
- On a `v*` tag, the same APK is also published to the GitHub
  Release for that tag

Download path after a green run: repo → **Actions** → the run →
**Artifacts** → `dsh-mobile-debug-apk`. Do not commit
`android/local.properties` (it contains a machine-specific SDK
path); it is in `.gitignore`.

```bash
cd android
# On a normal dev machine, generate the stock wrapper first:
gradle wrapper --gradle-version 8.7
./gradlew assembleDebug
# ... or, with Gradle 8.7 already installed, run directly:
gradle assembleDebug
# APK: android/app/build/outputs/apk/debug/app-debug.apk
```

If you installed the SDK under `~/workspace/android-sdk`
(see build notes), set:

```bash
export ANDROID_HOME=~/workspace/android-sdk
export ANDROID_SDK_ROOT=~/workspace/android-sdk
```

## Build status in this sandbox (honest)

- Plugin TypeScript **typechecks clean** (`tsc --noEmit`, Node 24).
- **No debug APK was produced in this sandbox.** The one build
  attempt stopped before Kotlin compilation at Gradle daemon
  startup: `FAILURE: Build failed with an exception. / Could not
  receive a message from the daemon.` The daemon process itself
  started and logged "Daemon server started", but the Gradle
  client could not receive its message. Environment at the time:
  7.7 GB RAM total, ~225 MB free, no swap; JDK 17.0.21 and a
  manually assembled Android SDK (platform-tools, platforms;
  android-34, build-tools 34.0.0) under `~/workspace/android-sdk`
  were installed and verified. The stock `gradlew` was not
  generated here for the same daemon reason, so it is intentionally
  **not** shipped as a broken script — generate it on a dev
  machine with `gradle wrapper` (see above).
- Because compilation never ran, Kotlin/Compose compile errors
  (if any — e.g. the `SwipeToDismissBox` API in the installed
  Material3 version) are **not yet ruled out**. The first build on
  a machine with enough memory is the real compile check.

## What v1 does

- [x] iOS-style native UI: Pair / Sessions / Chat / Settings tabs
- [x] Pairing (paste token), pairing status, disconnect/revoke
- [x] Sessions list / create / delete (rename via API interface)
- [x] Native chat: message list, streaming text, tool-call cards
- [x] Approval prompt UI (approve / deny)
- [x] Model picker placeholder (free-text, host-controlled names)
- [x] Attach button wired to the upload adapter
- [x] Encrypted credential storage, header-only transport
- [x] Single `ApiEndpoints` file isolating every endpoint path
- [x] Plugin skeleton: pairing, devices (0600, atomic), routes,
      gated `/remote` concept, revoke-all, settings schema

## Build status in this sandbox (honest)

- Plugin TypeScript **typechecks clean** (`tsc --noEmit`, Node 24).
- **No debug APK was produced in this sandbox.** The one build
  attempt stopped before Kotlin compilation at Gradle daemon
  startup: `FAILURE: Build failed with an exception. / Could not
  receive a message from the daemon.` The daemon process itself
  started and logged "Daemon server started", but the Gradle
  client could not receive its message. Environment at the time:
  7.7 GB RAM total, ~225 MB free, no swap; JDK 17.0.21 and a
  manually assembled Android SDK (platform-tools, platforms;
  android-34, build-tools 34.0.0) under `~/workspace/android-sdk`
  were installed and verified. The stock `gradlew` was not
  generated here for the same daemon reason, so it is intentionally
  **not** shipped as a broken script — generate it on a dev
  machine with `gradle wrapper` (see above).
- Because compilation never ran, Kotlin/Compose compile errors
  (if any — e.g. the `SwipeToDismissBox` API in the installed
  Material3 version) are **not yet ruled out**. The first build on
  a machine with enough memory is the real compile check.

## What v1 does NOT do (roadmap, not v1 claims)

- Full desktop parity (terminal, skill centre, task board, git
  graph, plan review, usage stats, desktop-only tool UIs, etc.)
- QR code scanning (paste only in v1)
- Cloudflare one-click tunnel / fixed relay / fixed hostname
- Verified end-to-end chat against a real dsh host
- Verified file upload (endpoint + multipart shape unconfirmed —
  the app says so in-app instead of faking success)
- Session rename UI (API exists; no rename row in v1 UI)
- Offline / background sync, push notifications

## Verification steps (user with a real dsh host must do)

1. On the host, run `dsh --profile web --dump-config` and confirm
   the plugin layer lands; fix `plugin/cordis.patch.yml` and the
   `ctx` adapter in `plugin/src/index.ts` for your dsh version.
2. Start `dsh web`, mint a pairing token from the host panel,
   and complete a pairing round-trip from the app on the same LAN.
3. Confirm `/api/pair/status` returns the minimal payload, and
   that an unpaired device gets `403 unpaired` on `/remote/*`.
4. Map each function in `DshRemoteApi` to the host's real session/
   chat route in `android/.../data/ApiEndpoints.kt` only. Verify:
   list, create, rename, delete, streaming chat, tool-call events,
   approval round-trip, upload.
5. Revoke the test device from the host and confirm the next
   app request fails (and that the app shows the error, not stale data).
6. Only then try a tunnel: start a manual tunnel, set its URL in
   the app, re-pair if the host requires it, and repeat steps 3–5.
7. Never test revocation or tunnelling on a host holding
   credentials, private repos, or production data until step 5 passes.
