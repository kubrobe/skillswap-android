# SkillSwap (Android)

Trade skills, not bills. Students teach a skill they know and book lessons from other students.
Kotlin + Jetpack Compose (Material 3) + Room (SQLite cache) + MVVM. Always online: one Supabase database shared with the website.

## Screens (7)
Login · Register · Browse (search + category filter) · Offer detail (times + reviews) ·
My lessons (I learn / I teach, upcoming + history, cancel, done, rate) · Teach (publish a listing) ·
Profile (stats, reputation, member level, edit profile)

## Database (6 tables, with relations)
users · skills · offers (user→skill) · time_slots (→offer) · bookings (→user, slot, offer) · reviews (→booking)

## Design and motion
Glass design: drifting colour glow behind translucent glass cards with light edges · floating glass tab bar with a spring capsule · iOS-style push transitions with parallax · Poppins font · gradient headers · one card style (`AppCard`) · bronze/silver/gold levels ·
fade+slide screen transitions (250 ms) · press-scale on cards and chips · staggered list entrance · count-up statistics ·
confirm dialogs · animated booking success · light and dark theme follow the phone.
Compose animations follow the phone's "animation scale" setting, so "Remove animations" turns them off.

## Architecture
Screen (Compose) → SkillSwapViewModel (StateFlow) → SkillSwapRepository → DAO (Room) → SQLite
                                                                      ↘ ApiClient (HTTPS) → Express → PostgreSQL

## Key business rules (in one Room @Transaction)
- Booking a time: check not own lesson, no clash, take the slot (`UPDATE … WHERE isBooked = 0`), save booking. All or nothing.
- Cancel frees the time again. Only a finished lesson can be rated, once.
- Passwords: salted SHA-256 hash (not plain text).

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

## Demo account
aziza@demo.uz / demo12345 (others: bobur@, dilnoza@, jasur@, nilufar@, kamola@, timur@demo.uz — same password).
The same accounts work on the website. Sample data lives in Supabase (db/09_sample_data.sql in the website repo).

## Run
1. Android Studio → File → Open → choose this folder (the one with settings.gradle.kts).
2. Wait for Gradle sync, press the green Run button.
3. Tests: right-click `app/src/test/.../UtilsTest.kt` → Run.
4. Release APK: Build → Generate Signed App Bundle / APK → APK → release.
