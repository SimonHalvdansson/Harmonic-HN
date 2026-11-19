package com.simon.harmonichackernews.settings

enum class UserAvatarStyle(val storedValue: String, val label: String) {
    MOSAIC("mosaic", "Mosaic"),
    PIXELS("pixels", "Pixels"),
    ORBITAL("orbital", "Abstract"),
    ROBOT("robot", "Robots"),
    LANDSCAPE("landscape", "Landscapes"),
    LETTERS("letters", "Letters"),
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
        val availableStyles = UserAvatarStyle.entries
        var choiceCount = 0
        for (index in availableStyles.indices) {
            if (availableStyles[index] in styles) choiceCount++
        }
        if (choiceCount == 0) return UserAvatarStyle.MOSAIC
        // Continue the same hash with the suffix without constructing a string for every avatar.
        var selectedIndex = if (choiceCount == 1) 0 else
            (userAvatarSeed(":style", userAvatarSeed(author)).toUInt() % choiceCount.toUInt()).toInt()
        for (index in availableStyles.indices) {
            val style = availableStyles[index]
            if (style in styles && selectedIndex-- == 0) return style
        }
        return UserAvatarStyle.MOSAIC
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
fun userAvatarSeed(author: String): Int = userAvatarSeed(author, 0x811c9dc5.toInt())

private fun userAvatarSeed(author: String, initial: Int): Int = author.fold(initial) { hash, char ->
    (hash xor char.code) * 0x01000193
}

/** HN usernames are ASCII; preserve numeric and punctuation initials as part of their identity. */
fun userAvatarInitial(author: String): String = author.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
