package com.simon.harmonichackernews.ui.theme

import androidx.compose.ui.graphics.Color
import com.simon.harmonichackernews.settings.ColorSchemeStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ThemePaletteInitializationTest {
    @Test
    fun resolvingAPaletteInitializesOnlyItsRequiredSchemesAndReusesThem() {
        // Other theme tests visit every palette. Isolate these two singletons so the first-use
        // assertion also runs against a fresh catalog regardless of test order.
        val catalog = FreshThemeCatalog()
        assertEquals(emptySet(), catalog.initializedGenerated())
        assertEquals(emptySet(), catalog.initializedCurated())

        val blue = catalog.scheme("blue", false).colorScheme
        assertEquals(setOf("blueLight"), catalog.initializedGenerated())
        assertEquals(emptySet(), catalog.initializedCurated())
        assertEquals(Color(0xFF475D92), blue.primary)
        assertSame(blue, catalog.scheme("blue", false).colorScheme)

        val classic = catalog.scheme("classic", true).colorScheme
        assertEquals(setOf("blueLight", "roseDark"), catalog.initializedGenerated())
        assertEquals(setOf("classicDark"), catalog.initializedCurated())
        assertEquals(Color(0xFF1A1C26), classic.surface)
        assertSame(classic, catalog.scheme("classic", true).colorScheme)
    }

    private class FreshThemeCatalog {
        private val catalogName = HarmonicThemeCatalog::class.java.name
        private val generatedName = MaterialColorSchemes::class.java.name
        private val loader = object : ClassLoader(HarmonicThemeCatalog::class.java.classLoader) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> {
                if (listOf(catalogName, generatedName).none { name == it || name.startsWith("$it\$") }) {
                    return super.loadClass(name, resolve)
                }
                return synchronized(getClassLoadingLock(name)) {
                    val loaded = findLoadedClass(name) ?: run {
                        val bytes = checkNotNull(parent.getResourceAsStream(name.replace('.', '/') + ".class"))
                            .use { it.readBytes() }
                        defineClass(name, bytes, 0, bytes.size)
                    }
                    if (resolve) resolveClass(loaded)
                    loaded
                }
            }
        }
        private val catalog = Class.forName(catalogName, true, loader)
        private val generated = Class.forName(generatedName, true, loader)
        private val instance = catalog.getField("INSTANCE").get(null)
        private val scheme = catalog.getMethod(
            "scheme", String::class.java, Boolean::class.javaPrimitiveType, ColorSchemeStyle::class.java,
        )

        fun scheme(id: String, dark: Boolean): HarmonicThemePalette =
            scheme.invoke(instance, id, dark, ColorSchemeStyle.Balanced) as HarmonicThemePalette

        fun initializedGenerated(): Set<String> = initialized(generated)
        fun initializedCurated(): Set<String> = initialized(catalog)

        private fun initialized(type: Class<*>): Set<String> = type.declaredFields
            .filter { Lazy::class.java.isAssignableFrom(it.type) }
            .filter {
                it.isAccessible = true
                (it.get(null) as Lazy<*>).isInitialized()
            }
            .map { it.name.removeSuffix("\$delegate") }
            .toSet()
    }
}
