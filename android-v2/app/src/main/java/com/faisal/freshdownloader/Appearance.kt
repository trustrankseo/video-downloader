package com.faisal.freshdownloader

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class AppThemeMode { SYSTEM, DARK, LIGHT }
enum class AppAccent { BLUE, CYAN, PURPLE, GREEN, ORANGE, RED }

data class AppearanceSettings(
    val mode: AppThemeMode,
    val accent: AppAccent,
    val darkResolved: Boolean
)

object AppearanceRuntime {
    private const val PREFS = "universal_downloader_appearance"
    private const val KEY_MODE = "theme_mode"
    private const val KEY_ACCENT = "accent_color"
    private val revision = mutableIntStateOf(0)

    var activeDark: Boolean = true
        private set
    var activeAccent: Color = Color(0xFF5B7CFF)
        private set

    val background: Color get() = if (activeDark) Color(0xFF050607) else Color(0xFFF6F7FB)
    val surface: Color get() = if (activeDark) Color(0xFF0D1015) else Color(0xFFFFFFFF)
    val surfaceVariant: Color get() = if (activeDark) Color(0xFF151A22) else Color(0xFFECEFF5)
    val onSurface: Color get() = if (activeDark) Color(0xFFF7F8FC) else Color(0xFF111318)
    val muted: Color get() = if (activeDark) Color(0xFF9AA6B5) else Color(0xFF5E6877)
    val accentSecondary: Color get() = activeAccent.copy(alpha = if (activeDark) 1f else 0.92f)
    val accentTertiary: Color get() = when (activeAccent) {
        Color(0xFF5B7CFF) -> Color(0xFF8A5CFF)
        Color(0xFF19B9D1) -> Color(0xFF4B8DFF)
        Color(0xFF8B5CF6) -> Color(0xFFD05CE3)
        Color(0xFF20A464) -> Color(0xFF24B6A6)
        Color(0xFFF28C28) -> Color(0xFFFFB020)
        else -> Color(0xFFE4546B)
    }

    @Composable
    fun current(): AppearanceSettings {
        val context = LocalContext.current
        val ignored by revision
        ignored
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val mode = runCatching {
            AppThemeMode.valueOf(prefs.getString(KEY_MODE, AppThemeMode.SYSTEM.name) ?: AppThemeMode.SYSTEM.name)
        }.getOrDefault(AppThemeMode.SYSTEM)
        val accent = runCatching {
            AppAccent.valueOf(prefs.getString(KEY_ACCENT, AppAccent.BLUE.name) ?: AppAccent.BLUE.name)
        }.getOrDefault(AppAccent.BLUE)
        val systemDark = isSystemInDarkTheme()
        val dark = when (mode) {
            AppThemeMode.SYSTEM -> systemDark
            AppThemeMode.DARK -> true
            AppThemeMode.LIGHT -> false
        }
        activeDark = dark
        activeAccent = accentColor(accent)
        return AppearanceSettings(mode, accent, dark)
    }

    fun setMode(context: Context, mode: AppThemeMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_MODE, mode.name).apply()
        revision.intValue++
    }

    fun setAccent(context: Context, accent: AppAccent) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_ACCENT, accent.name).apply()
        revision.intValue++
    }

    fun accentColor(accent: AppAccent): Color = when (accent) {
        AppAccent.BLUE -> Color(0xFF5B7CFF)
        AppAccent.CYAN -> Color(0xFF19B9D1)
        AppAccent.PURPLE -> Color(0xFF8B5CF6)
        AppAccent.GREEN -> Color(0xFF20A464)
        AppAccent.ORANGE -> Color(0xFFF28C28)
        AppAccent.RED -> Color(0xFFE4546B)
    }
}

@Composable
fun UniversalDownloaderTheme(content: @Composable () -> Unit) {
    val appearance = AppearanceRuntime.current()
    val accent = AppearanceRuntime.activeAccent
    val scheme = if (appearance.darkResolved) {
        darkColorScheme(
            primary = accent,
            secondary = AppearanceRuntime.accentSecondary,
            tertiary = AppearanceRuntime.accentTertiary,
            background = AppearanceRuntime.background,
            surface = AppearanceRuntime.surface,
            surfaceVariant = AppearanceRuntime.surfaceVariant,
            onPrimary = Color.White,
            onSecondary = Color.White,
            onTertiary = Color.White,
            onBackground = AppearanceRuntime.onSurface,
            onSurface = AppearanceRuntime.onSurface,
            onSurfaceVariant = AppearanceRuntime.muted,
            error = Color(0xFFFF5C6C)
        )
    } else {
        lightColorScheme(
            primary = accent,
            secondary = AppearanceRuntime.accentSecondary,
            tertiary = AppearanceRuntime.accentTertiary,
            background = AppearanceRuntime.background,
            surface = AppearanceRuntime.surface,
            surfaceVariant = AppearanceRuntime.surfaceVariant,
            onPrimary = Color.White,
            onSecondary = Color.White,
            onTertiary = Color.White,
            onBackground = AppearanceRuntime.onSurface,
            onSurface = AppearanceRuntime.onSurface,
            onSurfaceVariant = AppearanceRuntime.muted,
            error = Color(0xFFB3261E)
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
