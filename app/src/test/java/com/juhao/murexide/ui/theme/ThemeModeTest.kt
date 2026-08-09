package com.juhao.murexide.ui.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import androidx.compose.ui.graphics.Color
import org.junit.Test

class ThemeModeTest {

    @Test
    fun whiteThemeFollowsExplicitAndSystemDarkModes() {
        assertFalse(usesDarkTheme("light", systemInDarkTheme = true))
        assertTrue(usesDarkTheme("dark", systemInDarkTheme = false))
        assertFalse(usesDarkTheme("system", systemInDarkTheme = false))
        assertTrue(usesDarkTheme("system", systemInDarkTheme = true))
    }

    @Test
    fun whiteDarkPaletteUsesDarkSurfaces() {
        assertNotEquals(WhiteLightColorScheme.surface, WhiteDarkColorScheme.surface)
        assertNotEquals(WhiteLightColorScheme.onSurface, WhiteDarkColorScheme.onSurface)
        assertTrue(WhiteDarkColorScheme.surfaceContainerHighest == Color(0xFF191919))
        assertTrue(WhiteDarkColorScheme.surfaceContainer == Color(0xFF111111))
        assertTrue(WhiteDarkColorScheme.surfaceContainerHigh == Color(0xFF2C2C2C))
    }
}
