package com.simon.harmonichackernews.settings

enum class UserAvatarStyle(val storedValue: String, val label: String) {
    MOSAIC("mosaic", "Mosaic"),
    PIXELS("pixels", "Pixels"),
    ORBITAL("orbital", "Abstract"),
    ROBOT("robot", "Robots"),
    LANDSCAPE("landscape", "Landscapes"),
}

enum class UserAvatarShape(val label: String) { CIRCLE("Circle"), ROUNDED("Rounded"), SQUARE("Square") }
enum class UserAvatarColors(val label: String) { VIVID("Vivid"), MUTED("Muted"), MONOCHROME("Mono") }

/** Kept separately from the visibility switch so disabling avatars retains their configuration. */
data class UserAvatarOptions(
    val styles: Set<UserAvatarStyle> = setOf(UserAvatarStyle.MOSAIC),
    val shape: UserAvatarShape = UserAvatarShape.CIRCLE,
    val colors: UserAvatarColors = UserAvatarColors.VIVID,
    val generic: Boolean = false,
) {
    val selectedStyles: List<UserAvatarStyle>
        get() = UserAvatarStyle.entries.filter { it in styles }.ifEmpty { listOf(UserAvatarStyle.MOSAIC) }

    val summary: String get() = if (generic) "Generic" else selectedStyles.joinToString { it.label }

    fun styleFor(author: String): UserAvatarStyle {
        val choices = selectedStyles
        return choices[(userAvatarSeed("$author:style").toUInt() % choices.size.toUInt()).toInt()]
    }

    fun encode(): String = listOf(
        selectedStyles.joinToString(",") { it.storedValue }, shape.name, colors.name, generic,
    ).joinToString(";")

    companion object {
        fun decode(value: String): UserAvatarOptions {
            val parts = value.split(';')
            val styles = parts.first().split(',').mapNotNull { raw ->
                UserAvatarStyle.entries.find { it.storedValue == raw }
            }.toSet()
            return UserAvatarOptions(
                styles = styles.ifEmpty { setOf(UserAvatarStyle.MOSAIC) },
                shape = UserAvatarShape.entries.find { it.name == parts.getOrNull(1) } ?: UserAvatarShape.CIRCLE,
                colors = UserAvatarColors.entries.find { it.name == parts.getOrNull(2) } ?: UserAvatarColors.VIVID,
                generic = parts.getOrNull(3)?.toBooleanStrictOrNull() ?: false,
            )
        }
    }
}

/** FNV-1a over Kotlin character codes; stable across all hosts. */
fun userAvatarSeed(author: String): Int = author.fold(0x811c9dc5.toInt()) { hash, char ->
    (hash xor char.code) * 0x01000193
}
