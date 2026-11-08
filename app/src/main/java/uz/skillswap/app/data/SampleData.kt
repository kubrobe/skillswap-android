package uz.skillswap.app.data

import uz.skillswap.app.util.Security

// Inserted the first time the app starts (when the skills table is empty).
// Demo password for every demo member: demo123

class SeedOffer(
    val teacherEmail: String, val skill: String, val level: String,
    val description: String, val slots: List<Pair<Int, Int>>
)

class SeedLesson(
    val learnerEmail: String, val teacherEmail: String, val skill: String,
    val slotIndex: Int, val rating: Int, val comment: String
)

object SampleData {
    val skills = listOf(
        Skill(name = "Python basics", category = "Tech", emoji = "🐍"),
        Skill(name = "SQL and databases", category = "Tech", emoji = "🗄️"),
        Skill(name = "Web development", category = "Tech", emoji = "💻"),
        Skill(name = "Excel and data analysis", category = "Tech", emoji = "📊"),
        Skill(name = "IELTS writing", category = "Languages", emoji = "✍️"),
        Skill(name = "Korean for beginners", category = "Languages", emoji = "🇰🇷"),
        Skill(name = "Russian conversation", category = "Languages", emoji = "🗣️"),
        Skill(name = "UI design in Figma", category = "Design", emoji = "🎨"),
        Skill(name = "Presentation design", category = "Design", emoji = "🖼️"),
        Skill(name = "Guitar", category = "Music", emoji = "🎸"),
        Skill(name = "Cooking plov", category = "Life", emoji = "🍛"),
        Skill(name = "Chess", category = "Life", emoji = "♟️")
    )

    private fun member(name: String, email: String, bio: String) =
        User(fullName = name, email = email, passwordHash = Security.hashPassword("demo123", email), bio = bio)

    val users = listOf(
        member("Aziza Karimova", "aziza@demo.uz", "Data student who loves teaching Python."),
        member("Bobur Rakhimov", "bobur@demo.uz", "Backend developer and weekend guitarist."),
        member("Dilnoza Yusupova", "dilnoza@demo.uz", "UI designer. Happy to trade design for coding."),
        member("Jasur Tursunov", "jasur@demo.uz", "IELTS 7.5, writing coach."),
        member("Nilufar Saidova", "nilufar@demo.uz", "Loves cooking and chess.")
    )

    // day: 1 = Monday ... 7 = Sunday, hour = start of the lesson
    val offers = listOf(
        SeedOffer("aziza@demo.uz", "Python basics", "Advanced", "Variables, loops and a first mini project.", listOf(1 to 18, 3 to 18, 6 to 10)),
        SeedOffer("aziza@demo.uz", "SQL and databases", "Intermediate", "SELECT, JOIN and designing tables.", listOf(2 to 19, 5 to 17)),
        SeedOffer("bobur@demo.uz", "Guitar", "Beginner", "First chords and simple songs.", listOf(2 to 20, 4 to 20, 7 to 12)),
        SeedOffer("bobur@demo.uz", "Web development", "Advanced", "HTML, CSS and JavaScript from scratch.", listOf(1 to 20, 6 to 14)),
        SeedOffer("dilnoza@demo.uz", "UI design in Figma", "Advanced", "Frames, components and clean layouts.", listOf(3 to 19, 7 to 15)),
        SeedOffer("dilnoza@demo.uz", "Presentation design", "Intermediate", "Make slides people remember.", listOf(4 to 18, 5 to 18)),
        SeedOffer("jasur@demo.uz", "IELTS writing", "Advanced", "Task 1 and Task 2 structure and feedback.", listOf(2 to 18, 6 to 16)),
        SeedOffer("jasur@demo.uz", "Korean for beginners", "Beginner", "Hangul and first 50 phrases.", listOf(3 to 20, 7 to 18)),
        SeedOffer("nilufar@demo.uz", "Cooking plov", "Advanced", "Real Tashkent wedding plov.", listOf(6 to 11, 7 to 11)),
        SeedOffer("nilufar@demo.uz", "Chess", "Intermediate", "Openings and basic tactics.", listOf(1 to 19, 4 to 19))
    )

    // Finished lessons with reviews, so ratings are visible from the first start
    val lessons = listOf(
        SeedLesson("bobur@demo.uz", "aziza@demo.uz", "Python basics", 0, 5, "Patient and clear. I wrote my first script in one hour."),
        SeedLesson("dilnoza@demo.uz", "aziza@demo.uz", "Python basics", 1, 4, "Good examples, a little fast at the end."),
        SeedLesson("aziza@demo.uz", "dilnoza@demo.uz", "UI design in Figma", 0, 5, "Great tips for components."),
        SeedLesson("nilufar@demo.uz", "bobur@demo.uz", "Guitar", 0, 5, "Fun lessons, learned three chords."),
        SeedLesson("jasur@demo.uz", "nilufar@demo.uz", "Cooking plov", 0, 5, "Best plov of my life.")
    )
}
