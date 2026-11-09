package uz.skillswap.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.unit.dp

/**
 * SkillSwap's own skill icons, drawn for this app instead of emoji: 24x24 line icons with round ends
 * and a 1.8 stroke, so every skill looks like part of one set (and the same on every phone).
 * Each icon is a list of SVG-style paths; "true" marks a filled shape (dots, piano keys).
 * The icons are white; the screens tint them (category colour, gold, white on a star...).
 */
object SkillIcons {
    private class Spec(val rotate: Float, val shapes: List<Pair<String, Boolean>>)

    private val specs: Map<String, Spec> = mapOf(
        "python" to Spec(0.0f, listOf(
            "M6 4 H18 A3 3 0 0 1 21 7 V17 A3 3 0 0 1 18 20 H6 A3 3 0 0 1 3 17 V7 A3 3 0 0 1 6 4 Z" to false,
            "M7 9.5 L10 12 L7 14.5" to false,
            "M12.5 15 H17" to false
        )),
        "web" to Spec(0.0f, listOf(
            "M6 4 H18 A3 3 0 0 1 21 7 V17 A3 3 0 0 1 18 20 H6 A3 3 0 0 1 3 17 V7 A3 3 0 0 1 6 4 Z" to false,
            "M3 8.5 H21" to false,
            "M5.45 6.25 A0.55 0.55 0 1 0 6.55 6.25 A0.55 0.55 0 1 0 5.45 6.25 Z" to true,
            "M7.45 6.25 A0.55 0.55 0 1 0 8.55 6.25 A0.55 0.55 0 1 0 7.45 6.25 Z" to true,
            "M9.8 12 L7.8 14.25 L9.8 16.5" to false,
            "M14.2 12 L16.2 14.25 L14.2 16.5" to false,
            "M12.7 11.4 L11.3 17.1" to false
        )),
        "database" to Spec(0.0f, listOf(
            "M5 6 A7 2.6 0 1 0 19 6 A7 2.6 0 1 0 5 6 Z" to false,
            "M5 6 V18 C5 19.45 8.13 20.6 12 20.6 C15.87 20.6 19 19.45 19 18 V6" to false,
            "M5 12 C5 13.45 8.13 14.6 12 14.6 C15.87 14.6 19 13.45 19 12" to false
        )),
        "braces" to Spec(0.0f, listOf(
            "M9 4 C7.2 4 7 5.3 7 7.2 V9.8 C7 11.1 6.3 12 5 12 C6.3 12 7 12.9 7 14.2 V16.8 C7 18.7 7.2 20 9 20" to false,
            "M15 4 C16.8 4 17 5.3 17 7.2 V9.8 C17 11.1 17.7 12 19 12 C17.7 12 17 12.9 17 14.2 V16.8 C17 18.7 16.8 20 15 20" to false,
            "M11.1 12 A0.9 0.9 0 1 0 12.9 12 A0.9 0.9 0 1 0 11.1 12 Z" to true
        )),
        "chart" to Spec(0.0f, listOf(
            "M4 4 V20 H20" to false,
            "M8.5 16.5 V12.5" to false,
            "M12.5 16.5 V9.5" to false,
            "M16.5 16.5 V6.5" to false
        )),
        "chat" to Spec(0.0f, listOf(
            "M6 3.5 H14 A2 2 0 0 1 16 5.5 V10.5 A2 2 0 0 1 14 12.5 H9 L6 15 V12.5 A2 2 0 0 1 4 10.5 V5.5 A2 2 0 0 1 6 3.5 Z" to false,
            "M18.5 8.5 A2 2 0 0 1 20.5 10.5 V15.5 A2 2 0 0 1 18.5 17.5 V20 L15.5 17.5 H11 A2 2 0 0 1 9 15.5 V15" to false
        )),
        "pencil" to Spec(0.0f, listOf(
            "M15 4.5 L19.5 9 L9.5 19 H5 V14.5 Z" to false,
            "M12.8 6.7 L17.3 11.2" to false,
            "M13 20 H20" to false
        )),
        "taegeuk" to Spec(0.0f, listOf(
            "M3.5 12 A8.5 8.5 0 1 0 20.5 12 A8.5 8.5 0 1 0 3.5 12 Z" to false,
            "M3.5 12 A4.25 4.25 0 0 1 12 12 A4.25 4.25 0 0 0 20.5 12" to false
        )),
        "matryoshka" to Spec(0.0f, listOf(
            "M8.7 6.8 A3.3 3.3 0 1 0 15.3 6.8 A3.3 3.3 0 1 0 8.7 6.8 Z" to false,
            "M9 8.6 C6.6 11 6.6 15.5 7.6 18.2 C8.2 19.8 9.8 20.8 12 20.8 C14.2 20.8 15.8 19.8 16.4 18.2 C17.4 15.5 17.4 11 15 8.6" to false,
            "M9.2 14.4 C10.8 15.6 13.2 15.6 14.8 14.4" to false,
            "M10 6.9 A2 2 0 1 0 14 6.9 A2 2 0 1 0 10 6.9 Z" to false
        )),
        "globe" to Spec(0.0f, listOf(
            "M3.2 12 A8.8 8.8 0 1 0 20.8 12 A8.8 8.8 0 1 0 3.2 12 Z" to false,
            "M12 3.2 C14.6 5.8 15.6 8.8 15.6 12 C15.6 15.2 14.6 18.2 12 20.8 C9.4 18.2 8.4 15.2 8.4 12 C8.4 8.8 9.4 5.8 12 3.2 Z" to false,
            "M3.5 12 H20.5" to false
        )),
        "pen" to Spec(0.0f, listOf(
            "M12 3 L18 9.5 L14.5 18.5 H9.5 L6 9.5 Z" to false,
            "M12 3 V10.3" to false,
            "M10.4 12 A1.6 1.6 0 1 0 13.6 12 A1.6 1.6 0 1 0 10.4 12 Z" to false,
            "M9.5 18.5 V21 H14.5 V18.5" to false
        )),
        "camera" to Spec(0.0f, listOf(
            "M6 7 H18 A3 3 0 0 1 21 10 V17 A3 3 0 0 1 18 20 H6 A3 3 0 0 1 3 17 V10 A3 3 0 0 1 6 7 Z" to false,
            "M8.3 7 L9.8 4.5 H14.2 L15.7 7" to false,
            "M8.4 13.5 A3.6 3.6 0 1 0 15.6 13.5 A3.6 3.6 0 1 0 8.4 13.5 Z" to false,
            "M17 10 A0.6 0.6 0 1 0 18.2 10 A0.6 0.6 0 1 0 17 10 Z" to true
        )),
        "play" to Spec(0.0f, listOf(
            "M6 5 H18 A3 3 0 0 1 21 8 V16 A3 3 0 0 1 18 19 H6 A3 3 0 0 1 3 16 V8 A3 3 0 0 1 6 5 Z" to false,
            "M10 9.4 V14.6 L14.6 12 Z" to true
        )),
        "palette" to Spec(0.0f, listOf(
            "M12 3 C7 3 3 6.8 3 11.6 C3 16.6 7 20.6 12 20.6 C13.3 20.6 13.9 19.6 13.4 18.6 C12.8 17.3 13.6 16 15 16 H17 C19.2 16 21 14.2 21 12 C21 6.9 16.9 3 12 3 Z" to false,
            "M6.4 11.5 A1.2 1.2 0 1 0 8.8 11.5 A1.2 1.2 0 1 0 6.4 11.5 Z" to true,
            "M8.4 7.6 A1.2 1.2 0 1 0 10.8 7.6 A1.2 1.2 0 1 0 8.4 7.6 Z" to true,
            "M13 7 A1.2 1.2 0 1 0 15.4 7 A1.2 1.2 0 1 0 13 7 Z" to true,
            "M16.1 10.4 A1.2 1.2 0 1 0 18.5 10.4 A1.2 1.2 0 1 0 16.1 10.4 Z" to true
        )),
        "guitar" to Spec(40.0f, listOf(
            "M12 1.8 V10.2" to false,
            "M10.6 1.8 H13.4" to false,
            "M12 10.2 C9.2 10.2 8.4 11.7 8.9 13 C9.2 13.9 8.7 14.4 8 15.2 C6.6 16.8 7 22.2 12 22.2 C17 22.2 17.4 16.8 16 15.2 C15.3 14.4 14.8 13.9 15.1 13 C15.6 11.7 14.8 10.2 12 10.2 Z" to false,
            "M10.4 16.4 A1.6 1.6 0 1 0 13.6 16.4 A1.6 1.6 0 1 0 10.4 16.4 Z" to false,
            "M10.6 19.6 H13.4" to false
        )),
        "piano" to Spec(0.0f, listOf(
            "M5.5 5 H18.5 A2.5 2.5 0 0 1 21 7.5 V16.5 A2.5 2.5 0 0 1 18.5 19 H5.5 A2.5 2.5 0 0 1 3 16.5 V7.5 A2.5 2.5 0 0 1 5.5 5 Z" to false,
            "M7.5 13 V19" to false,
            "M12 13 V19" to false,
            "M16.5 5 V19" to false,
            "M6.8 5 H8.2 A0.7 0.7 0 0 1 8.9 5.7 V12.3 A0.7 0.7 0 0 1 8.2 13 H6.8 A0.7 0.7 0 0 1 6.1 12.3 V5.7 A0.7 0.7 0 0 1 6.8 5 Z" to true,
            "M11.3 5 H12.7 A0.7 0.7 0 0 1 13.4 5.7 V12.3 A0.7 0.7 0 0 1 12.7 13 H11.3 A0.7 0.7 0 0 1 10.6 12.3 V5.7 A0.7 0.7 0 0 1 11.3 5 Z" to true
        )),
        "note" to Spec(0.0f, listOf(
            "M9 18 V5.6 L19 3.6 V16" to false,
            "M4 18 A2.5 2.5 0 1 0 9 18 A2.5 2.5 0 1 0 4 18 Z" to false,
            "M14 16 A2.5 2.5 0 1 0 19 16 A2.5 2.5 0 1 0 14 16 Z" to false,
            "M9 9.2 L19 7.2" to false
        )),
        "dumbbell" to Spec(-35.0f, listOf(
            "M7.5 12 H16.5" to false,
            "M5.7 7.5 H6.3 A1.2 1.2 0 0 1 7.5 8.7 V15.3 A1.2 1.2 0 0 1 6.3 16.5 H5.7 A1.2 1.2 0 0 1 4.5 15.3 V8.7 A1.2 1.2 0 0 1 5.7 7.5 Z" to false,
            "M17.7 7.5 H18.3 A1.2 1.2 0 0 1 19.5 8.7 V15.3 A1.2 1.2 0 0 1 18.3 16.5 H17.7 A1.2 1.2 0 0 1 16.5 15.3 V8.7 A1.2 1.2 0 0 1 17.7 7.5 Z" to false,
            "M2.6 10.2 V13.8" to false,
            "M21.4 10.2 V13.8" to false
        )),
        "knight" to Spec(0.0f, listOf(
            "M6.5 20.5 H17.5" to false,
            "M8 20.5 V18.6 H16 V20.5" to false,
            "M9.2 18.6 C9.2 15.8 11.6 14.6 11.2 12.4 L8.4 13.5 C7.3 13.9 6.3 13 6.7 11.9 L8.6 7.6 C9.7 5.1 11.7 3.9 14.1 4.3 C17.6 4.9 18.6 9 17.6 13 C17.1 15 16 16.6 16 18.6" to false,
            "M11.4 8.2 A0.8 0.8 0 1 0 13 8.2 A0.8 0.8 0 1 0 11.4 8.2 Z" to true,
            "M12.4 4.4 L11.6 2.6 L10.2 5" to false
        )),
        "shoe" to Spec(0.0f, listOf(
            "M3 16.4 C3 15.2 3.8 14.5 4.9 14.2 L8.8 13.1 L10.3 9.6 C10.6 8.9 11.5 8.8 11.9 9.4 L13.1 11.1 C14.7 12.9 17.4 13.6 19.9 13.9 C20.7 14 21.2 14.6 21.2 15.4 V16.4 C21.2 17.3 20.5 18 19.6 18 H4.6 C3.7 18 3 17.3 3 16.4 Z" to false,
            "M3.1 16 H21.1" to false,
            "M10.2 12.7 L11.8 13.5" to false,
            "M11.2 10.9 L12.8 11.7" to false,
            "M2.5 8.5 H6" to false,
            "M2 11.5 H5" to false
        )),
        "pulse" to Spec(0.0f, listOf(
            "M3 12 H7 L9.3 6.5 L13.2 17.5 L15.4 12 H21" to false
        )),
        "mic" to Spec(0.0f, listOf(
            "M12 3 H12 A3 3 0 0 1 15 6 V11 A3 3 0 0 1 12 14 H12 A3 3 0 0 1 9 11 V6 A3 3 0 0 1 12 3 Z" to false,
            "M5.6 11.4 A6.4 6.4 0 0 0 18.4 11.4" to false,
            "M12 17.8 V21" to false,
            "M8.8 21 H15.2" to false
        )),
        "easel" to Spec(0.0f, listOf(
            "M5 3.5 H19 A2 2 0 0 1 21 5.5 V14 A2 2 0 0 1 19 16 H5 A2 2 0 0 1 3 14 V5.5 A2 2 0 0 1 5 3.5 Z" to false,
            "M12 16 V18.5" to false,
            "M8.2 21 L12 18.5 L15.8 21" to false,
            "M7 12.2 L10 9.2 L12.6 11.2 L17 7.2" to false
        )),
        "cap" to Spec(0.0f, listOf(
            "M12 4 L22 9 L12 14 L2 9 Z" to false,
            "M6 11 V15.6 C6 17.1 8.8 18.6 12 18.6 C15.2 18.6 18 17.1 18 15.6 V11" to false,
            "M22 9 V14.2" to false
        )),
        "book" to Spec(0.0f, listOf(
            "M12 6.5 C10 5 7 4.5 3.5 5 V18.6 C7 18.1 10 18.6 12 20.1 C14 18.6 17 18.1 20.5 18.6 V5 C17 4.5 14 5 12 6.5 Z" to false,
            "M12 6.5 V20.1" to false
        )),
        "kazan" to Spec(0.0f, listOf(
            "M3.5 11 H20.5" to false,
            "M5 11 V15.6 C5 18 6.8 20 9.2 20 H14.8 C17.2 20 19 18 19 15.6 V11" to false,
            "M3 12.8 H5" to false,
            "M19 12.8 H21" to false,
            "M7 11 C7 8.7 9.2 7.3 12 7.3 C14.8 7.3 17 8.7 17 11" to false,
            "M12 7.3 V5.4" to false,
            "M10.6 5.4 H13.4" to false
        )),
        "cupcake" to Spec(0.0f, listOf(
            "M6.2 12 L7.6 20 H16.4 L17.8 12" to false,
            "M10.2 12 L10.8 20" to false,
            "M13.8 12 L13.2 20" to false,
            "M6 12 C4.7 12 4.4 9.9 6 9.4 C6 6.9 8.3 5.4 10.6 6 C11.4 4.4 13.8 4.2 14.9 5.8 C17.1 5.5 18.9 7.4 18.1 9.4 C19.6 9.9 19.3 12 18 12 Z" to false,
            "M14.1 3.4 A1.1 1.1 0 1 0 16.3 3.4 A1.1 1.1 0 1 0 14.1 3.4 Z" to true
        )),
        "quill" to Spec(0.0f, listOf(
            "M5 20 L13.8 11.2" to false,
            "M7.6 15.6 C8.7 8.8 13.4 4.6 20 4 C19.4 10.6 15.2 15.3 8.4 16.4" to false,
            "M10.6 13 L13.7 13.3" to false,
            "M12.8 10.2 L16 10.4" to false,
            "M3.5 20.5 H9" to false
        )),
        "chefhat" to Spec(0.0f, listOf(
            "M7 13.5 C4.8 13.5 3.5 11.5 4.2 9.6 C4.9 7.7 7 7.2 8.2 7.8 C8.8 5.6 10.4 4.5 12 4.5 C13.6 4.5 15.2 5.6 15.8 7.8 C17 7.2 19.1 7.7 19.8 9.6 C20.5 11.5 19.2 13.5 17 13.5 V19.5 H7 Z" to false,
            "M7 16.5 H17" to false
        )),
        "medal" to Spec(0.0f, listOf(
            "M8.6 2.5 L11.2 9" to false,
            "M15.4 2.5 L12.8 9" to false,
            "M6.4 14.6 A5.6 5.6 0 1 0 17.6 14.6 A5.6 5.6 0 1 0 6.4 14.6 Z" to false,
            "M12 11.6 L12.95 13.5 L15 13.8 L13.5 15.2 L13.85 17.2 L12 16.25 L10.15 17.2 L10.5 15.2 L9 13.8 L11.05 13.5 Z" to true
        )),
        "sprout" to Spec(0.0f, listOf(
            "M12 21 V11.5" to false,
            "M12 14 C12 10.4 9.6 8.4 5.4 8.4 C5.4 12 7.8 14 12 14 Z" to false,
            "M12 11.5 C12 7.4 14.6 4.8 19 4.8 C19 8.9 16.4 11.5 12 11.5 Z" to false,
            "M8.5 21 H15.5" to false
        )),
        "code" to Spec(0.0f, listOf(
            "M8.5 7.5 L4 12 L8.5 16.5" to false,
            "M15.5 7.5 L20 12 L15.5 16.5" to false,
            "M13.6 5.5 L10.4 18.5" to false
        )),
        "spark" to Spec(0.0f, listOf(
            "M12 3 L13.9 10.1 L21 12 L13.9 13.9 L12 21 L10.1 13.9 L3 12 L10.1 10.1 Z" to false
        ))
    )

    private val cache = HashMap<String, ImageVector>()

    /** Every icon id, e.g. for preparing them all at once. */
    val ids: List<String> get() = specs.keys.toList()

    /** The icon with this id ("guitar", "python"...); unknown ids get the spark. */
    fun named(id: String): ImageVector = cache.getOrPut(id) { build(id, specs[id] ?: specs.getValue("spark")) }

    /** The icon for a skill by its name (and category as a fallback). */
    fun forSkill(skillName: String, category: String = ""): ImageVector = named(idFor(skillName, category))

    private fun build(id: String, spec: Spec): ImageVector =
        ImageVector.Builder(
            name = "skill_$id", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            group(rotate = spec.rotate, pivotX = 12f, pivotY = 12f) {
                for ((d, filled) in spec.shapes) {
                    if (filled) {
                        addPath(addPathNodes(d), fill = SolidColor(Color.White))
                    } else {
                        addPath(
                            addPathNodes(d), fill = null, stroke = SolidColor(Color.White), strokeLineWidth = 1.8f,
                            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
                        )
                    }
                }
            }
        }.build()

    /** Which icon a skill gets: words in its name first (any language mix), then its category. */
    fun idFor(skillName: String, category: String = ""): String {
        val n = skillName.lowercase()
        return when {
            "python" in n -> "python"
            "web" in n || "html" in n || "css" in n -> "web"
            "sql" in n || "database" in n -> "database"
            "javascript" in n || "typescript" in n -> "braces"
            "excel" in n || "data" in n -> "chart"
            "ielts" in n -> "pencil"
            "academic" in n -> "cap"
            "korean" in n -> "taegeuk"
            "russian" in n -> "matryoshka"
            "public speaking" in n || "speech" in n -> "mic"
            "english" in n || "conversation" in n -> "chat"
            "presentation" in n -> "easel"
            "figma" in n || n.startsWith("ui ") || "ux" in n -> "pen"
            "photo" in n -> "camera"
            "video" in n -> "play"
            "guitar" in n -> "guitar"
            "piano" in n -> "piano"
            "gym" in n || "fitness" in n -> "dumbbell"
            "chess" in n -> "knight"
            "run" in n -> "shoe"
            "plov" in n || "cook" in n -> "kazan"
            "bak" in n || "cake" in n -> "cupcake"
            "callig" in n -> "quill"
            "writing" in n -> "pencil"
            else -> forCategory(category)
        }
    }

    private fun forCategory(category: String): String = when (category) {
        "Programming", "Tech" -> "code"
        "Languages" -> "globe"
        "Design" -> "palette"
        "Music" -> "note"
        "Fitness & Sport" -> "pulse"
        "Business & Study" -> "book"
        "Cooking & Crafts", "Life" -> "chefhat"
        else -> "spark"
    }

    /** Some answers only carry the skill's emoji from the server (galaxy, leaderboard): map it to our icon. */
    fun idForEmoji(emoji: String): String = when (emoji.replace("\uFE0F", "")) {
        "🐍" -> "python"; "💻" -> "web"; "🗄" -> "database"; "📜" -> "braces"; "📊" -> "chart"
        "🗣" -> "chat"; "✍" -> "pencil"; "🇰🇷" -> "taegeuk"; "🇷🇺" -> "matryoshka"
        "🎨" -> "pen"; "🖼" -> "camera"; "🎬" -> "play"; "🎸" -> "guitar"; "🎹" -> "piano"
        "🏋" -> "dumbbell"; "♟" -> "knight"; "🏃" -> "shoe"; "🎤" -> "mic"; "📽" -> "easel"
        "📝" -> "cap"; "🍛" -> "kazan"; "🧁" -> "cupcake"; "🖌" -> "quill"
        else -> "spark"
    }

    /** Medal for a lesson level or member level (tint it with levelColor); a sprout for newcomers. */
    fun forLevel(level: String): ImageVector = named(if (level == "Newcomer") "sprout" else "medal")

    /** The category an icon belongs to, for its colour when the skill's category is not known. */
    fun categoryOf(iconId: String): String = when (iconId) {
        "python", "web", "database", "braces", "chart", "code" -> "Programming"
        "chat", "pencil", "taegeuk", "matryoshka", "globe" -> "Languages"
        "pen", "camera", "play", "palette" -> "Design"
        "guitar", "piano", "note" -> "Music"
        "dumbbell", "knight", "shoe", "pulse" -> "Fitness & Sport"
        "mic", "easel", "cap", "book" -> "Business & Study"
        "kazan", "cupcake", "quill", "chefhat" -> "Cooking & Crafts"
        else -> ""
    }
}
