from pathlib import Path

# v1.4.7 Huawei review fixes:
# - first-launch explicit privacy consent
# - in-app bilingual Privacy Policy page (English + Simplified Chinese)
# - keep China Mainland support by exposing a Chinese policy version
# - installed launcher icon is maintained from the AppGallery-matching resource

g = Path('app/build.gradle.kts')
s = g.read_text()
for old_code in (
    'versionCode = 28', 'versionCode = 29', 'versionCode = 30', 'versionCode = 31'
):
    s = s.replace(old_code, 'versionCode = 32')
for old_name in (
    'versionName = "1.4.3"', 'versionName = "1.4.4"',
    'versionName = "1.4.5"', 'versionName = "1.4.6"'
):
    s = s.replace(old_name, 'versionName = "1.4.7"')
g.write_text(s)

# Put the privacy consent gate between splash and the main app.
p = Path('app/src/main/java/com/faisal/freshdownloader/MainActivity.kt')
t = p.read_text()
if 'PrivacyConsentGate { AppMenuShell() }' not in t:
    if 'AppMenuShell()' not in t:
        raise SystemExit('AppMenuShell call not found for privacy gate')
    t = t.replace('AppMenuShell()', 'PrivacyConsentGate { AppMenuShell() }', 1)
p.write_text(t)

# Add a permanent Privacy Policy item to the hamburger menu.
m = Path('app/src/main/java/com/faisal/freshdownloader/AppMenuShell.kt')
u = m.read_text()

if 'Privacy("Privacy Policy", "▣")' not in u:
    anchors = [
        '    Appearance("Appearance", "◐"),\n    About("About Me", "i"),',
        '    Engine("App & Engine", "⚙"),\n    About("About Me", "i"),'
    ]
    replaced = False
    for anchor in anchors:
        if anchor in u:
            if 'Appearance(' in anchor:
                replacement = '    Appearance("Appearance", "◐"),\n    Privacy("Privacy Policy", "▣"),\n    About("About Me", "i"),'
            else:
                replacement = '    Engine("App & Engine", "⚙"),\n    Privacy("Privacy Policy", "▣"),\n    About("About Me", "i"),'
            u = u.replace(anchor, replacement, 1)
            replaced = True
            break
    if not replaced:
        raise SystemExit('Menu enum anchor not found for Privacy Policy')

if 'MenuPage.Privacy -> PrivacyPolicyPage()' not in u:
    anchors = [
        '                    MenuPage.Appearance -> AppearancePage()\n                    MenuPage.About -> AboutPage()',
        '                    MenuPage.Engine -> EnginePage(vm)\n                    MenuPage.About -> AboutPage()'
    ]
    replaced = False
    for anchor in anchors:
        if anchor in u:
            if 'MenuPage.Appearance' in anchor:
                replacement = '                    MenuPage.Appearance -> AppearancePage()\n                    MenuPage.Privacy -> PrivacyPolicyPage()\n                    MenuPage.About -> AboutPage()'
            else:
                replacement = '                    MenuPage.Engine -> EnginePage(vm)\n                    MenuPage.Privacy -> PrivacyPolicyPage()\n                    MenuPage.About -> AboutPage()'
            u = u.replace(anchor, replacement, 1)
            replaced = True
            break
    if not replaced:
        raise SystemExit('Menu routing anchor not found for Privacy Policy')

m.write_text(u)

print('Huawei v1.4.7 privacy review fixes applied')
