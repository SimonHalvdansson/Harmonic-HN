package com.simon.harmonichackernews.ui.content

import com.simon.harmonichackernews.resources.*
import org.jetbrains.compose.resources.DrawableResource

/** Stable IDs retain their symbol when renamed; custom categories always use the tag symbol. */
internal fun categorySymbol(categoryId: String?): DrawableResource = when (categoryId) {
    "ai" -> Res.drawable.ic_psychology
    "software" -> Res.drawable.ic_code
    "security" -> Res.drawable.ic_shield
    "privacy" -> Res.drawable.ic_visibility_off
    "science" -> Res.drawable.ic_science
    "space" -> Res.drawable.ic_planet
    "hardware" -> Res.drawable.ic_memory
    "business" -> Res.drawable.ic_business_center
    "startups" -> Res.drawable.ic_rocket_launch
    "economy" -> Res.drawable.ic_monitoring
    "finance" -> Res.drawable.ic_account_balance
    "world" -> Res.drawable.ic_public
    "climate" -> Res.drawable.ic_thermostat
    "cars" -> Res.drawable.ic_directions_car
    "history" -> Res.drawable.ic_history_edu
    "culture" -> Res.drawable.ic_theater_comedy
    "energy" -> Res.drawable.ic_bolt
    "health" -> Res.drawable.ic_health_and_safety
    else -> Res.drawable.ic_tag
}
