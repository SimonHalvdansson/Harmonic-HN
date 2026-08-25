package com.simon.harmonichackernews.navigation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A platform-neutral destination used by the full-screen physics debug surface. */
@Serializable
data object CoulombGasDestination : AppDestination

/**
 * Stable JSON navigation payload shared by Android intents, iOS routes and desktop windows.
 *
 * Android continues to decode the historical individual extras as a migration fallback, but new
 * writes use this codec so adding destination fields no longer requires another Bundle schema.
 */
object AppDestinationCodec {
    const val ANDROID_PAYLOAD_EXTRA = "com.simon.harmonichackernews.EXTRA_DESTINATION_JSON"
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        classDiscriminator = "kind"
    }

    fun encode(destination: AppDestination): String = json.encodeToString(
        DestinationEnvelope.serializer(),
        destination.toEnvelope(),
    )

    fun decode(serialized: String?): AppDestination? {
        if (serialized.isNullOrBlank()) return null
        return runCatching {
            json.decodeFromString(DestinationEnvelope.serializer(), serialized).destination
        }.getOrNull()
    }

    private fun AppDestination.toEnvelope(): DestinationEnvelope = when (this) {
        is StoryDestination -> DestinationEnvelope.Story(this)
        is EditorDestination -> DestinationEnvelope.Editor(this)
        is SubmissionsDestination -> DestinationEnvelope.Submissions(this)
        CoulombGasDestination -> DestinationEnvelope.CoulombGas
    }

    @Serializable
    private sealed interface DestinationEnvelope {
        val destination: AppDestination

        @Serializable
        @SerialName("story")
        data class Story(val value: StoryDestination) : DestinationEnvelope {
            override val destination: AppDestination get() = value
        }

        @Serializable
        @SerialName("editor")
        data class Editor(val value: EditorDestination) : DestinationEnvelope {
            override val destination: AppDestination get() = value
        }

        @Serializable
        @SerialName("submissions")
        data class Submissions(val value: SubmissionsDestination) : DestinationEnvelope {
            override val destination: AppDestination get() = value
        }

        @Serializable
        @SerialName("coulomb_gas")
        data object CoulombGas : DestinationEnvelope {
            override val destination: AppDestination get() = CoulombGasDestination
        }
    }
}
