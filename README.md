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

## Key business rules (in one Room @Transaction)
- Booking a time: check not own lesson, no clash, take the slot (`UPDATE … WHERE isBooked = 0`), save booking. All or nothing.
- Cancel frees the time again. Only a finished lesson can be rated, once.
- Passwords: salted SHA-256 hash (not plain text).

## Demo account
aziza@demo.uz / demo123 (others: bobur@, dilnoza@, jasur@, nilufar@demo.uz — same password)

## Run
1. Android Studio → File → Open → choose this folder (the one with settings.gradle.kts).
2. Wait for Gradle sync, press the green Run button.
3. Tests: right-click `app/src/test/.../UtilsTest.kt` → Run.
4. Release APK: Build → Generate Signed App Bundle / APK → APK → release.
