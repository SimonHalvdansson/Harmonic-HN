package com.simon.harmonichackernews.desktop

/** Let the site's native palette and browser controls adapt without recoloring article media. */
internal fun desktopWebViewAppearanceScript(
    dark: Boolean,
    matchTheme: Boolean,
    manualInversion: Boolean,
): String {
    val schemeRule = if (matchTheme) {
        ":root{color-scheme:${if (dark) "dark" else "light"}!important;}"
    } else ""
    // Pixel inversion is reserved for the user's explicit invert action. Keeping our rules in a
    // separate stylesheet means disabling them restores any styles originally supplied by the site.
    val inversionRule = if (manualInversion) {
        ":root{filter:invert(1) hue-rotate(180deg)!important;}"
    } else ""
    return """
        (function(){
            var id='harmonic-browser-appearance';
            var style=document.getElementById(id);
            if(!style){style=document.createElement('style');style.id=id;
                (document.head||document.documentElement).appendChild(style);}
            style.textContent='$schemeRule$inversionRule';
        })();
    """.trimIndent()
}
