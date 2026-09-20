package com.simon.harmonichackernews.settings

enum class SurfaceEffectMode(val storedValue: String) {
    Solid("solid"), Frosted("frosted"), Glass("glass");

    companion object {
        internal const val STORAGE_KEY = "pref_surface_effect"

        fun fromStored(value: String?, default: SurfaceEffectMode = Frosted): SurfaceEffectMode =
            entries.firstOrNull { it.storedValue == value } ?: default
    }
}

/** Portable limits also protect Haze's strict shader parameter validation. */
enum class GlassParameter(
    val default: Float,
    val range: ClosedFloatingPointRange<Float>,
) {
    ButtonTint(0.7f, 0f..1f),
    DialogTint(0.65f, 0f..1f),
    SubtleTint(0.5f, 0f..1f),
    BackgroundOpacity(1f, 0f..1f),
    MaterialOpacity(1f, 0f..1f),
    RefractionStrength(0.7f, 0f..1f),
    RefractionHeight(0.25f, 0f..1f),
    RefractionDisplacement(15f, 0f..40f),
    Depth(1f, 0f..1f),
    BlurRadius(14f, 0f..40f),
    RefractionFold(0f, 0f..1f),
    SpecularIntensity(0.45f, 0f..1f),
    AmbientResponse(0.16f, 0f..1f),
    EdgeSoftness(1f, 0f..8f),
    LightX(-1f, -1f..1f),
    LightY(-1f, -1f..1f),
    ContentNormalBlend(0f, 0f..1f),
    SpecularExponent(24f, 1f..64f),
    FresnelExponent(3f, 0f..10f),
    ChromaticAberration(0.04f, 0f..1f),
    Contrast(0f, -1f..1f),
    WhitePoint(0f, -1f..1f),
    ChromaMultiplier(1f, 0f..2f);

    internal val storageKey get() = "pref_debug_glass_${name}"

    fun sanitize(value: Float): Float = if (value.isFinite()) value.coerceIn(range) else default
}

enum class GlassSwitch(val default: Boolean) {
    AdaptiveOptics(true),
    TintEnabled(true),
    ChromaticAberration(false),
    FullChromaticAberration(false);

    internal val storageKey get() = "pref_debug_glass_${name}_enabled"
}

enum class GlassSurfaceProfile {
    Circle, Squircle, Concave, Lip;

    companion object {
        internal const val STORAGE_KEY = "pref_debug_glass_surface_profile"
        fun fromStored(value: String?): GlassSurfaceProfile =
            entries.firstOrNull { it.name == value } ?: Circle
    }
}

data class GlassPreferences(
    val parameters: Map<GlassParameter, Float> = emptyMap(),
    val switches: Map<GlassSwitch, Boolean> = emptyMap(),
    val surfaceProfile: GlassSurfaceProfile = GlassSurfaceProfile.Circle,
) {
    operator fun get(parameter: GlassParameter): Float =
        parameter.sanitize(parameters[parameter] ?: parameter.default)

    operator fun get(option: GlassSwitch): Boolean = switches[option] ?: option.default
}

data class SurfaceEffectPreferences(
    val mode: SurfaceEffectMode = SurfaceEffectMode.Frosted,
    val glass: GlassPreferences = GlassPreferences(),
)
