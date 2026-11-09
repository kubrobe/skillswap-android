# SkillSwap (Android)

Trade skills, not bills. Students teach a skill they know and book lessons from other students.
Kotlin + Jetpack Compose (Material 3) + Room (SQLite cache) + MVVM. Always online: one Supabase database shared with the website.

## Screens (12)
Login · Register · Browse (search, categories, LIVE ticker, perfect matches) · Swipe to swap · Ask SkillSwap (voice) ·
Offer detail (times + reviews) · My lessons (I learn / I teach, check-in, cancel, rate) · Teach (publish a listing) ·
Profile (stats, level, time bank, badges, Telegram, language) · Community impact · Skill Galaxy · Achievements

## Database (6 tables, with relations)
users · skills · offers (user→skill) · time_slots (→offer) · bookings (→user, slot, offer) · reviews (→booking)

## Design and motion
Graphite & Gold theme · glass cards over a soft glow · floating liquid-glass tab bar with real blur (Haze) and a stretching gold lens · iOS-style push transitions with parallax · Poppins font · gradient headers · one card style (`AppCard`) · bronze/silver/gold levels ·
fade+slide screen transitions (250 ms) · press-scale on cards and chips · staggered list entrance · count-up statistics ·
confirm dialogs · animated booking success · light and dark theme follow the phone.
Compose animations follow the phone's "animation scale" setting, so "Remove animations" turns them off.

## Architecture
Screen (Compose) → SkillSwapViewModel (StateFlow) → SkillSwapRepository → DAO (Room) → SQLite
                                                                      ↘ ApiClient (HTTPS) → Express → PostgreSQL

## Key business rules (checked on the server, inside PostgreSQL transactions)
- Booking: not your own lesson, the slot is still free (`SELECT … FOR UPDATE`), you have a credit. All or nothing.
- Finishing a lesson needs the teacher's 4-digit check-in code (QR); the teacher then gets +1 credit.
- Cancel frees the time and refunds the credit. Only a finished lesson can be rated, once.
- Passwords: bcrypt on the server. The session token is encrypted on the phone (AES-GCM, Android Keystore).

## Backend (shared with the SkillSwap website)
The app talks to the same Express + PostgreSQL server as the website:
`https://skillswap-vqt7.onrender.com/api/mobile` (see `docs/MOBILE_API.md` in the website repo).

* Login returns a session token; the app sends `Authorization: Bearer <token>`.
* `GET /sync` downloads everything; the app writes it into Room in **one transaction** (`SyncDao.replaceAll`).
* Room is only a cache of the server data: screens read from it, so they are instant and update by themselves.
* Changes (book, cancel, done, rate, publish, edit profile) go to the server first, then the app re-syncs.
  The server checks all rules inside PostgreSQL transactions, so two phones can never book the same slot.
* No extra libraries: `HttpURLConnection` + `org.json` (both built into Android), calls run on `Dispatchers.IO`.
* Without internet the app shows a *No connection* screen with a retry button.

## Hackathon features
* 🪙 **Time bank**: teach 1 hour = +1 credit, learn 1 hour = −1. Wallet + history on Profile, balance in the header.
* ✨ **Perfect matches**: pick what you want to learn; the app shows people who teach it — two-way swaps first.
* 🔔 **Live updates**: book on one phone, the teacher's phone shows a banner instantly (Server-Sent Events).
* 🤖 **AI lesson plan** for every lesson (template fallback when no AI key).
* 🎥 **Video room** (Jitsi) and 📅 **Add to calendar** for every booked lesson.
* 🎉 Confetti when a lesson is booked or finished.
* ✅ **QR check-in**: the teacher shows a QR / 4-digit code, the learner scans it → no fake lessons, **✓ Trusted** badge.
* 🃏 **Swipe to swap**: card deck, right = I want to learn it; a two-way match shows *It's a swap!*.
* 🪙 **Flying coin**: −1 flies out when you book, +1 flies in (live) when someone checks in to your lesson.
* 🟢 **LIVE ticker**: what is happening on SkillSwap right now.
* 🌍 **Community impact**: lessons, hours, credits, rating, weekly chart with growth, top skills and an
  estimated "money saved" (lessons × average tutor price, labelled as an estimate).
* 🌐 **English · Oʻzbekcha · Русский**: switch on Login or Profile, the whole app changes at once.
* 🌌 **Skill Galaxy**: the community as a live star map — people are stars, lessons are curved lines with gold
  sparks flowing from teacher to learner; a force simulation lays it out on screen; a new lesson fires a comet.
* 🎙️ **Voice AI matchmaker**: say "gitara o'rganmoqchiman, dam olish kunlari" → the 3 best lessons with reasons
  (Claude when the server has `ANTHROPIC_API_KEY`, a three-language keyword matcher otherwise).
* ✈️ **Telegram bot**: booking alerts, a reminder 1 hour before each lesson, `/next`, `/credits` (Profile → Connect Telegram).
* 🏅 **Badges + leaderboard**: 8 badges from real lessons with a "Badge unlocked!" moment; top teachers this week
  and all time on an animated podium.

## Demo account
aziza@demo.uz / demo12345 (others: bobur@, dilnoza@, jasur@, nilufar@, kamola@, timur@demo.uz — same password).
The same accounts work on the website. Sample data lives in Supabase (db/09_sample_data.sql in the website repo).

## Run
1. Android Studio → File → Open → choose this folder (the one with settings.gradle.kts).
2. Wait for Gradle sync, press the green Run button.
3. Tests (18 unit tests): right-click `app/src/test/.../UtilsTest.kt` → Run.
4. Release APK: Build → Generate Signed App Bundle / APK → APK → release.
