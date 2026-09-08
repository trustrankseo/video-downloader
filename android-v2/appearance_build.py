from pathlib import Path

# Keep v1.4.2 visible, but bump Android versionCode so this build updates over
# the previous sidebar build.
g = Path('app/build.gradle.kts')
s = g.read_text()
for old in ('versionCode = 23', 'versionCode = 24', 'versionCode = 25', 'versionCode = 26', 'versionCode = 27'):
    s = s.replace(old, 'versionCode = 28')
g.write_text(s)

# Make the existing downloader palette react to Dark / Light / System settings.
p = Path('app/src/main/java/com/faisal/freshdownloader/MainActivity.kt')
t = p.read_text()
old_palette = '''private val Ink = Color(0xFF08101E)
private val InkSoft = Color(0xFF0D1728)
private val CardDark = Color(0xFF111D31)
private val Cyan = Color(0xFF55DDF7)
private val Blue = Color(0xFF5B7CFF)
private val Purple = Color(0xFF9D6CFF)
private val Success = Color(0xFF59D99A)
private val Danger = Color(0xFFFF5C6C)
private val Warning = Color(0xFFFFC857)
private val Muted = Color(0xFF91A1B9)
private val WhiteSoft = Color(0xFFF5F8FF)
'''
new_palette = '''private val Ink: Color get() = AppearanceRuntime.background
private val InkSoft: Color get() = AppearanceRuntime.surface
private val CardDark: Color get() = AppearanceRuntime.surfaceVariant
private val Cyan: Color get() = AppearanceRuntime.accentSecondary
private val Blue: Color get() = AppearanceRuntime.activeAccent
private val Purple: Color get() = AppearanceRuntime.accentTertiary
private val Success = Color(0xFF59D99A)
private val Danger = Color(0xFFFF5C6C)
private val Warning = Color(0xFFFFC857)
private val Muted: Color get() = AppearanceRuntime.muted
private val WhiteSoft: Color get() = AppearanceRuntime.onSurface
'''
if old_palette in t:
    t = t.replace(old_palette, new_palette, 1)

old_theme = '''@Composable
private fun PremiumTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Cyan,
            secondary = Purple,
            tertiary = Blue,
            background = Ink,
            surface = InkSoft,
            surfaceVariant = CardDark,
            onPrimary = Ink,
            onBackground = WhiteSoft,
            onSurface = WhiteSoft,
            onSurfaceVariant = Muted,
            error = Danger
        ),
        content = content
    )
}
'''
new_theme = '''@Composable
private fun PremiumTheme(content: @Composable () -> Unit) {
    UniversalDownloaderTheme(content)
}
'''
if old_theme in t:
    t = t.replace(old_theme, new_theme, 1)

# Main downloader background follows the active palette instead of staying navy.
t = t.replace(
    'listOf(Color(0xFF07101E), Color(0xFF0A1426), Color(0xFF08101E))',
    'listOf(Ink, InkSoft, Ink)'
)
# Explicit translucent whites on ordinary surfaces become theme-aware; solid
# white used on selected/accent buttons stays white for contrast.
t = t.replace('Color.White.copy(alpha', 'WhiteSoft.copy(alpha')
p.write_text(t)

# Add Appearance to the hamburger menu and make the shell itself theme-aware.
m = Path('app/src/main/java/com/faisal/freshdownloader/AppMenuShell.kt')
u = m.read_text()

u = u.replace(
    '    Engine("App & Engine", "⚙"),\n    About("About Me", "i"),',
    '    Engine("App & Engine", "⚙"),\n    Appearance("Appearance", "◐"),\n    About("About Me", "i"),'
)
u = u.replace(
    '                    MenuPage.Engine -> EnginePage(vm)\n                    MenuPage.About -> AboutPage()',
    '                    MenuPage.Engine -> EnginePage(vm)\n                    MenuPage.Appearance -> AppearancePage()\n                    MenuPage.About -> AboutPage()'
)

# Adaptive shell colors.
u = u.replace('drawerContainerColor = Color(0xFF0B1526)', 'drawerContainerColor = MaterialTheme.colorScheme.surface')
u = u.replace('containerColor = Color(0xFF08101E)', 'containerColor = MaterialTheme.colorScheme.background')
u = u.replace('Surface(shadowElevation = 4.dp, color = Color(0xFF0C1728))', 'Surface(shadowElevation = 4.dp, color = MaterialTheme.colorScheme.surface)')
u = u.replace('Color(0xFF5B7CFF)', 'MaterialTheme.colorScheme.primary')
u = u.replace('Color(0xFF55DDF7)', 'MaterialTheme.colorScheme.secondary')
u = u.replace('Color(0xFF111D31)', 'MaterialTheme.colorScheme.surfaceVariant')
u = u.replace('Color.White.copy(alpha', 'MaterialTheme.colorScheme.onSurface.copy(alpha')
u = u.replace('color = Color.White)', 'color = MaterialTheme.colorScheme.onSurface)')
u = u.replace(
    'listOf(Color(0xFF153452), Color(0xFF223267), Color(0xFF3A2356))',
    'listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.30f), MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.24f))'
)

if 'private fun AppearancePage()' not in u:
    anchor = '''@Composable
private fun AboutPage() {
'''
    appearance_page = '''@Composable
private fun AppearancePage() {
    val context = LocalContext.current
    val appearance = AppearanceRuntime.current()

    SimplePage {
        Text("Appearance", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        Text(
            "Choose a black dark theme, clean white light theme, or follow your Android system automatically.",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
        )

        Text("THEME MODE", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f), style = MaterialTheme.typography.labelSmall)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = appearance.mode == mode,
                    onClick = { AppearanceRuntime.setMode(context, mode) },
                    label = {
                        Text(
                            when (mode) {
                                AppThemeMode.SYSTEM -> "System"
                                AppThemeMode.DARK -> "Dark"
                                AppThemeMode.LIGHT -> "White"
                            }
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Text("ACCENT COLOR", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f), style = MaterialTheme.typography.labelSmall)
        AppAccent.entries.chunked(3).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { accent ->
                    val selected = appearance.accent == accent
                    Surface(
                        onClick = { AppearanceRuntime.setAccent(context, accent) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) AppearanceRuntime.accentColor(accent).copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(
                            1.dp,
                            if (selected) AppearanceRuntime.accentColor(accent) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                modifier = Modifier.size(18.dp),
                                shape = RoundedCornerShape(99.dp),
                                color = AppearanceRuntime.accentColor(accent)
                            ) {}
                            Text(
                                accent.name.lowercase().replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        InfoTile(
            "Current mode",
            when (appearance.mode) {
                AppThemeMode.SYSTEM -> "System • currently ${if (appearance.darkResolved) "Dark" else "White"}"
                AppThemeMode.DARK -> "Dark Black"
                AppThemeMode.LIGHT -> "White Light"
            }
        )
        InfoTile("Saved", "Theme and accent choices are stored on this device and applied on the next app launch too.")
    }
}

'''
    if anchor not in u:
        raise SystemExit('AboutPage anchor not found')
    u = u.replace(anchor, appearance_page + anchor, 1)

m.write_text(u)
