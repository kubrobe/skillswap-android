# Native video rooms — Phase 1: investigation and plan

Status: **implemented on branch `feature/video-rooms` in both repos; see section 8 for what is verified and what is not.** Covers both repos: `skillswap-android` (app) and `skillswap` (shared Express + Supabase backend).

---

## 1. What the codebase actually is

### Android (`skillswap-android`)
| Area | Finding |
|---|---|
| Build | One module `:app`. AGP 9.4.1, Gradle 9.6.1, Kotlin 2.3.20, KSP. compileSdk/targetSdk 36, **minSdk 24**, Java 17. Version catalog in `gradle/libs.versions.toml`, a few deps declared inline in `app/build.gradle.kts`. |
| UI | Jetpack Compose, BOM 2026.06.01, Material 3, `material-icons-core` only. Own icon set (68 line icons) via `IconText` / `SkillIcons`. |
| Architecture | MVVM. **One app-wide `SkillSwapViewModel`** (activity-scoped). Manual DI: `SkillSwapApp` lazily builds one `SkillSwapRepository(Room, SharedPreferences, ApiClient)`. No Hilt/Koin. |
| Navigation | `ui/AppNavHost.kt`, string routes in `object Routes`, `PUSHED` / `MODAL` transition sets, floating glass tab bar. |
| Networking | `data/remote/ApiClient.kt`: `HttpURLConnection` + `org.json`, no OkHttp/Retrofit (deliberate, written in the README). Base URL `https://skillswap-vqt7.onrender.com/api/mobile`. |
| Auth | Server session token, sent as `Authorization: Bearer`. Stored encrypted (AES-GCM, Android Keystore, `util/TokenCipher.kt`). 401 → `expireSession()`. |
| Data | Room is only a cache. `GET /sync` → `SyncDao.replaceAll` in one transaction. Changes go to the server first (`onServer { }`), then re-sync. |
| Real-time | **Server-Sent Events only** (`ApiClient.listen`, `SkillSwapRepository.runLiveUpdates`): `hello`, `changed`, `notice`, `credit`, `activity`. Reconnects with 2→30 s backoff. The stream is **closed in `onPause`** (`onAppPaused → stopLive`). No WebSocket, no FCM push. |
| Permissions | Manifest has only `INTERNET`. Check-in uses Google's code scanner (no camera permission). |
| Tests | 20 JVM unit tests (`UtilsTest.kt`, JUnit 4). No instrumented tests, no coroutines-test. |
| Design | **Graphite & Gold**, not navy/purple: `BrandStart #2B2E36`, `GoldPrimary #E0B44C`, dark bg `#111317`, Poppins, shapes 12/16/24 dp, glass cards, haptics, animations respect the system animation scale. |

### Backend (`skillswap`)
| Area | Finding |
|---|---|
| Stack | Node ≥ 20, Express 4, `pg`. Supabase PostgreSQL. Hosted on **Render free plan: one instance, sleeps after ~15 min idle, ~1 min cold start.** |
| Auth | `src/auth.js`: `sessions` table (SHA-256 of token, 7 days), `loadUser` reads cookie **or** Bearer header, `requireAuth`. Shared by website and app. |
| Mobile API | `src/mobile.js` (`/api/mobile/*`), documented in `docs/MOBILE_API.md`. |
| Live hub | `src/live.js`: in-memory `Map<userId, Set<res>>`, 20 s pings, `notify()` also forwards to Telegram (`src/telegram.js`). |
| Migrations | Idempotent SQL files in `db/`, applied at every start (`src/migrations.js`, last: `13_google.sql`). |
| Tests | `node:test` against a real Postgres (`tests/mobile.test.js` etc.). |

### The "session" a call belongs to
An accepted learning session is a **`slot_bookings` row with `status = 'BOOKED'`**: learner = `learner_id`, teacher = `user_offers.user_id`. Slots are **weekly** (`weekday` 1–7, `hour` 0–23) with no calendar date. `myBooking()` in `mobile.js` is the existing "is this person part of this booking" check: outsiders get 404.

### Voice Matchmaker: how it really works
`ui/screens/AskScreen.kt` launches the phone's own speech recogniser (`RecognizerIntent`, so the app has no microphone permission) → `POST /api/mobile/match` → `src/matchmaker.js` (Claude when `ANTHROPIC_API_KEY` is set, otherwise a 3-language keyword matcher) → 3 offer picks → Offer detail → **Book**. The person is identified by their session; the "match" ends in a normal booking.

**So Voice Matchmaker creates no live session and uses no real-time channel.** Video rooms hook in *after* it (booking → lesson → room). It needs no changes. Adding `RECORD_AUDIO` for calls does not affect `RecognizerIntent`.

### Video today
"🎥 Video" on an upcoming lesson card opens **`https://meet.jit.si/SkillSwap-<bookingId>-<hash>` in the browser** (`util/Utils.kt` `jitsiUrl`, `LessonsScreen.kt` line ~152, also in the calendar event text line ~364). It needs no login, and the name can be worked out from the booking id and time. This is the external-website behaviour the brief forbids, so the native room **replaces** it.

---

## 2. What can be reused
- **Auth + membership:** `requireAuth`, Bearer tokens, the `myBooking()` access pattern ("outsiders get 404").
- **Live hub pattern** (`live.js`): per-user open streams, pings, cleanup on `close`. The room signalling hub copies this design.
- **`notify()`**: "Bobur is waiting for you in the Python video lesson" reaches the app banner **and Telegram** with no new infrastructure.
- **`ApiClient.request` / `ApiClient.listen`**: an SSE parser that already works through Render's proxy.
- **Repository `onServer` + 401 handling**, the i18n `Strings` (en/uz/ru), `AppCard`, `IconBubble`, `GradientHeader`, `MODAL` transitions, the haptics conventions.

## 3. What is missing
1. A WebRTC library (Google stopped publishing `org.webrtc` to Maven).
2. A signalling channel: SDP and ICE both ways, per room, alive while the call runs (the main SSE closes on pause).
3. Room records, membership, lifecycle and expiry on the server.
4. TURN with short-lived credentials made by the server.
5. Android permissions (camera, microphone, notifications), audio routing (earpiece/speaker/Bluetooth), a foreground service for calls in the background.
6. A way to surface "the other person is in the room" (no FCM today).
7. Test plumbing: `org.json` is a stub in JVM tests (`testImplementation("org.json:json")`), plus `kotlinx-coroutines-test`.

---

## 4. Recommended architecture

```
VideoRoomScreen (Compose) ─▶ VideoRoomViewModel ─▶ VideoRoomManager (app-scoped, in SkillSwapApp)
                                                   ├─ RoomStateMachine   (pure Kotlin, unit-tested)
                                                   ├─ SignalingClient    ─▶ room SSE stream + POST /signal
                                                   ├─ MediaTransport     ─▶ MeshTransport (1:1 now) / SfuTransport (later)
                                                   │     └─ PeerSession per remote participant (PeerConnection, ICE queue)
                                                   ├─ LocalMedia         (factory, EGL, camera, mic, switch/enable)
                                                   └─ CallAudioRouter    (AudioManager, speaker/earpiece/BT)
CallService (foreground, type microphone|camera): keeps the call alive in background, ongoing notification with Hang up
```

Key decisions:
- **WebRTC:** `io.getstream:stream-webrtc-android` (Apache 2.0, maintained libwebrtc build on Maven Central, standard `org.webrtc.*` API). Only the core artifact; video rendered with `SurfaceViewRenderer` inside `AndroidView`, so no extra UI libraries.
- **Signalling = SSE down + HTTPS POST up**, on a *dedicated room stream*. This is the smallest reliable option for this backend: it is already proven through Render's proxy, the app already parses it, and it adds **no dependency on either side**. Signalling sits behind a `SignalingClient` interface, so a WebSocket version could replace it later without touching WebRTC code.
- **Room state lives in the app scope** (next to the repository), not in `SkillSwapViewModel`, so rotation, leaving the screen, and the foreground service all see the same call.
- **The server stores rooms, never media or SDP.** Signals are relayed in memory and never written or logged.
- **Group-ready, not group-built:** participants are keyed by user id, every signal has `from`/`to`, and tracks are per participant. Mesh is used for 1:1 only; group calls should go to an SFU (Cloudflare Realtime SFU is the natural fit if Cloudflare TURN is used, LiveKit as the alternative) behind `MediaTransport`.

### Signalling protocol (v1)
Phone → server `POST /api/mobile/rooms/:roomId/signal`
```json
{ "id": "uuid", "to": 42, "type": "offer|answer|candidate|media|bye", "payload": { } }
```
Server → phone (room SSE `GET /api/mobile/rooms/:roomId/events`):
`room-state {you, participants[{userId,name,role}], startedAt, expiresAt}`, `peer-joined`, `peer-left {reason}`, `signal {id, from, type, payload}`, `room-ended {reason}`.

Rules:
- **Who offers:** the person already in the room offers to the newcomer. Joins are ordered by the single server process, so two people pressing "Join" at once cannot both offer. Renegotiation and ICE restarts use *perfect negotiation* (newcomer = polite) as a second safety net against glare.
- **Early candidates** are queued per peer until `setRemoteDescription` finishes, then flushed.
- **Duplicates:** every message has an `id`, and the receiver ignores ids it has seen.
- **Validation:** sender and receiver must be members of the room, there is a type whitelist, a 16 KB limit and a per-user rate limit.
- **Reconnect:** a dropped stream gets a 30 s grace period before `peer-left`. ICE `disconnected` > 5 s or `failed` → the offerer runs an ICE restart; after 3 failures → FAILED with Retry.

### Lifecycle
`IDLE → CREATING → WAITING (alone) → CONNECTING → CONNECTED ⇄ RECONNECTING → ENDING → ENDED`, and `FAILED` from any state. `JOINING` covers a second person entering a room that already exists. Server-side end reasons: `left`, `ended_by_user`, `peer_timeout`, `booking_cancelled`, `booking_done`, `expired`, `empty`. A room ends when it has been empty for 2 min, after a 3 h maximum, or when its booking stops being BOOKED. After that, joining is refused with a reason.

### Backend changes
- `db/14_video_rooms.sql`: `video_rooms` (uuid id, booking_id FK, created_by, status ACTIVE/ENDED, timestamps, end_reason; partial unique index: one ACTIVE room per booking) + `video_room_participants` (room, user, joined_at, left_at).
- `src/rooms.js` routes:
  - `POST /bookings/:id/room`: create or return the active room. Booking must be BOOKED and you must be its learner or teacher.
  - `GET /rooms/:id/events`: join and stream.
  - `POST /rooms/:id/signal`, `/leave`, `/end`.
  - `GET /ice`: STUN + fresh TURN credentials.
  - `/sync` also gets `rooms[]`, so a lesson card can show "Bobur is waiting · Join".
- `notify()` the other participant when someone opens the room (banner + Telegram), plus a `room` event on the main stream.
- Cancel/complete of a booking ends its room.
- Tests in `tests/rooms.test.js`: create/join, outsider 404, cancelled booking refused, signal goes only to the addressee, size/type validation, disconnect → peer-left after grace, cancel → room-ended.

### TURN
Recommended: **Cloudflare Realtime TURN**. The server keeps the key and calls `generate-ice-servers` with a short TTL; the app only ever sees temporary credentials. Free for the first 1,000 GB a month, then $0.05/GB. Render env vars: `TURN_KEY_ID`, `TURN_KEY_API_TOKEN`. Without them the server returns STUN only and the app says the connection may fail on some networks.

### Android changes
- Manifest: `CAMERA`, `RECORD_AUDIO`, `MODIFY_AUDIO_SETTINGS`, `POST_NOTIFICATIONS` (33+), `BLUETOOTH_CONNECT` (31+, only for headset routing), `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CAMERA`, `FOREGROUND_SERVICE_MICROPHONE`; `uses-feature camera required=false`; the `CallService` declaration.
- Permissions flow: an explanation sheet before the system prompt; permanently denied → "Open settings"; mic denied → video without sound (clearly marked); camera denied → audio-only with an avatar tile; permissions revoked mid-call → that track stops with a message.
- Background: on leaving the app the camera pauses (the other side sees "Camera paused") and audio continues through `CallService`. Leaving the call stops the service. Swiping the app away → the service sends `leave` and releases everything.
- R8 keep rules for `org.webrtc`; ABI filters (arm64-v8a, armeabi-v7a, x86_64 for the emulator) to control APK size.
- Entry points: the lesson card "Video" button opens the native room (Jitsi code and URL removed, calendar text changed to "Video lesson in the SkillSwap app"); a live "Join · Bobur is waiting" state on the card.
- UI: the dark graphite call screen with the remote video full-bleed, a draggable local preview that snaps to corners, name + skill, timer, connection chip, one bottom row (mic, camera, flip, audio route, red hang-up) using the app's own icons, auto-hiding controls, and waiting / connecting / reconnecting / ended / failed states. Strings in en/uz/ru.

---

## 5. Dependencies
| Side | Added | Why |
|---|---|---|
| Android | `io.getstream:stream-webrtc-android` (1.3.x, latest stable checked at implementation) | libwebrtc |
| Android tests | `org.json:json`, `kotlinx-coroutines-test` | real JSON + coroutine tests on the JVM |
| Backend | none | SSE + `fetch` (Node 20) are enough |

## 6. Risks
1. **I cannot compile the Android app in my workspace:** Google Maven, Maven Central and Gradle downloads are blocked there. Either those domains get allowed, or each phase is compiled in Android Studio and the errors are sent back. Backend tests *can* run here (local Postgres 16).
2. **Without TURN, many calls will fail** on mobile networks behind carrier NAT.
3. **Render free plan:** a deploy or restart drops in-memory signalling. Media that is already connected keeps flowing and the phones re-join; the room record survives in the database. The first call after idle waits for the ~1 min cold start.
4. **No push notifications:** the invitee only sees "waiting in the room" if the app is open, or through Telegram. A real ringing call screen needs FCM (a Firebase project), which is outside this plan.
5. **APK size** grows by roughly 10–15 MB per CPU type.
6. **Android 14+ foreground-service rules:** the service must start while the app is visible, with permissions already granted.
7. **Audio quality on low-end phones** (echo, Bluetooth routing): only testable on real devices.
8. **Product gap:** QR check-in assumes meeting in person. An online lesson needs a decision later (for example, the teacher shows the code on camera).

## 7. Phases
1. ✅ Investigation (this document).

Phases 2–6 are done (section 8); phase 7 needs the Android build and two phones.
2. **Backend:** migration, `rooms.js`, ICE endpoint, `/sync` rooms, cancel/complete hooks, tests, `MOBILE_API.md`.
3. **Android media foundation:** dependency, `LocalMedia`, `PeerSession`, ICE queue, audio router, pure state machine + protocol codec with unit tests.
4. **Android signalling + room manager:** `SignalingClient`, `VideoRoomManager`, reconnect/expiry handling, `CallService`.
5. **UI:** `VideoRoomScreen`, permissions flow, all states, en/uz/ru strings.
6. **Integration:** lesson card entry + live "waiting" state, Jitsi removed, README/docs updated; Voice Matchmaker regression check.
7. **Verification:** build, two physical phones (checklist), fixes.

Work goes on a `feature/video-rooms` branch in each repo; nothing touches `main` until it is tested.

---

## 8. Implementation status (2026-11-15)

| Part | Status |
|---|---|
| Backend: rooms, membership, signalling relay, grace period, expiry, cancel/check-in closing, `/ice`, `/sync` rooms, live `room` event + waiting notice | **Implemented and tested**: 11 new server tests, 62/62 pass on a fresh database |
| TURN (Cloudflare) | **Implemented, waiting for configuration**: `TURN_KEY_ID` + `TURN_KEY_API_TOKEN` in Render (steps in the backend's `docs/VIDEO_ROOMS.md`). Until then STUN only, and the app shows a hint |
| Android call logic: protocol, state machine, room manager (offer/answer rules, early candidates, duplicates, glare, reconnects, ICE restarts, timeouts, cleanup) | **Implemented and unit-tested**: 35 JVM tests, all pass; a mutation check proved they fail when dedupe, ICE restart or cleanup is broken |
| Android WebRTC layer (`LocalMedia`, `PeerSession`), audio routing, foreground service, call screen, permissions, lesson-card entry, navigation | **Implemented, not yet compiled**: this workspace cannot download the Android SDK, WebRTC or Gradle, so these files were reviewed by hand only |
| Two-phone test | **Not done**: needs a build and two real phones (checklist below) |
| Group lessons | **Not implemented** (by design): protocol and `CallMedia`/`PeerLink` are ready for an SFU later |
| Push notifications for incoming calls when the app is closed | **Not implemented**: needs FCM; today the invitee gets an in-app banner, a live "Join" on the lesson card and a Telegram message |

### Files
**Android, new:** `call/Protocol.kt`, `call/CallMachine.kt`, `call/MediaTransport.kt`, `call/SignalingClient.kt`, `call/ServerSignaling.kt`,
`call/VideoRoomManager.kt`, `call/LocalMedia.kt`, `call/PeerSession.kt`, `call/CallAudioRouter.kt`, `call/CallService.kt`,
`ui/call/VideoRoomScreen.kt`, `ui/call/VideoSurface.kt`, `ui/call/VideoRoomViewModel.kt`, `res/drawable/ic_call_notification.xml`,
tests `call/ProtocolAndMachineTest.kt`, `call/VideoRoomManagerTest.kt`.
**Android, changed:** `AndroidManifest.xml` (permissions, service), `build.gradle.kts` + `libs.versions.toml` (WebRTC, test libs, ABI filters),
`proguard-rules.pro`, `SkillSwapApp.kt`, `MainActivity.kt`, `ApiClient.kt`, `SkillSwapRepository.kt`, `SkillSwapViewModel.kt`, `AppNavHost.kt`,
`LessonsScreen.kt` (Jitsi removed), `Strings.kt` (en/uz/ru), `SkillIcons.kt` (8 call icons), `Utils.kt` + `UtilsTest.kt` (Jitsi helper removed), `strings.xml`, README.
**Backend, new:** `db/14_video_rooms.sql`, `src/rooms.js`, `tests/rooms.test.js`, `docs/VIDEO_ROOMS.md`.
**Backend, changed:** `server.js`, `src/live.js`, `src/mobile.js`, `src/migrations.js`, `db/migrate.js`, `package.json`, `render.yaml`, `.env.example`, `docs/MOBILE_API.md`.

### Dependencies added
`io.getstream:stream-webrtc-android:1.3.10` (app); `org.json:json`, `kotlinx-coroutines-test` (tests only). Backend: none.

### Before testing
1. Merge/deploy the backend branch (Render applies `14_video_rooms.sql` by itself at start-up).
2. Set up Cloudflare TURN (backend `docs/VIDEO_ROOMS.md`). Check `/api/mobile/health` shows `"turn": true`.
3. Build the app branch in Android Studio and fix anything the compiler finds (the WebRTC/UI files were not compiled yet).

### Two-phone checklist (two real phones, two accounts with a booked lesson between them)
- [ ] Teacher opens the lesson → "Waiting for …"; learner's phone shows the banner and "Join · … is waiting" on the card
- [ ] Learner joins → both see and hear each other within a few seconds
- [ ] Mute/unmute both ways (the other side shows "… is muted"); camera off/on (the other side shows the initial)
- [ ] Switch front/back camera; drag the self-view to each corner
- [ ] Speaker ↔ earpiece; plug in headphones / connect Bluetooth during the call
- [ ] One phone switches Wi-Fi → mobile data during the call: video comes back without leaving
- [ ] Both phones on mobile data (needs TURN)
- [ ] Press Home: sound goes on, the camera pauses, the notification is shown; tap it → back in the call
- [ ] Lock the screen during the call; unlock
- [ ] Leave on one phone → the other sees "… left the lesson"; "End for both" ends it for both
- [ ] Swipe the app away from recent apps during a call → the other side sees "left" within ~20 s
- [ ] Cancel the lesson during a call → both calls end with "This lesson was cancelled"
- [ ] Deny camera → "Join with sound only" works; deny with "don't ask again" → "Open Settings" and back
- [ ] 10 calls in a row: no growing memory, the camera light always turns off after a call
- [ ] Voice Matchmaker (Ask SkillSwap) still works

### Known limitations
- Presence is in memory on one Render instance: a deploy or restart drops room streams (phones reconnect; media already flowing continues).
- The first call after the free server slept waits for its ~1 minute cold start.
- No ringing call screen when the app is closed (needs FCM).
- The self-view's rounded corners depend on the phone (a video surface cannot always be clipped).
- APK grows by roughly 10–15 MB per CPU type (ABI filters keep three).
- QR check-in still assumes meeting in person; for an online lesson the teacher can show the code on camera.
