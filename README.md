# SkillSwap (Android)

Trade skills, not bills. Students teach a skill they know and book lessons from other students.
Kotlin + Jetpack Compose (Material 3) + Room (SQLite) + MVVM. Works fully offline.

## Screens (7)
Login · Register · Browse (search + category filter) · Offer detail (times + reviews) ·
My lessons (I learn / I teach, upcoming + history, cancel, done, rate) · Teach (publish a listing) ·
Profile (stats, reputation, member level, edit profile)

## Database (6 tables, with relations)
users · skills · offers (user→skill) · time_slots (→offer) · bookings (→user, slot, offer) · reviews (→booking)

## Design and motion
Poppins font bundled in the app · gradient headers · one card style (`AppCard`) · bronze/silver/gold levels ·
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
* Screens always read from Room, so the app keeps working **offline** with the last saved data.
* Changes (book, cancel, done, rate, publish, edit profile) go to the server first, then the app re-syncs.
  The server checks all rules inside PostgreSQL transactions, so two phones can never book the same slot.
* No extra libraries: `HttpURLConnection` + `org.json` (both built into Android), calls run on `Dispatchers.IO`.
* Without internet, login falls back to accounts saved on the phone (the offline demo data).

## Demo account
aziza@demo.uz / demo12345 (others: bobur@, dilnoza@, jasur@, nilufar@demo.uz — same password).
The same accounts work on the website.

## Run
1. Android Studio → File → Open → choose this folder (the one with settings.gradle.kts).
2. Wait for Gradle sync, press the green Run button.
3. Tests: right-click `app/src/test/.../UtilsTest.kt` → Run.
4. Release APK: Build → Generate Signed App Bundle / APK → APK → release.
