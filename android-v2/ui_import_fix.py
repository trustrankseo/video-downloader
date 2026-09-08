from pathlib import Path

p = Path('app/src/main/java/com/faisal/freshdownloader/AppMenuShell.kt')
t = p.read_text()
needle = 'import androidx.compose.runtime.*\n'
extra = 'import androidx.compose.runtime.saveable.rememberSaveable\n'
if extra not in t:
    if needle not in t:
        raise SystemExit('runtime import anchor not found')
    t = t.replace(needle, needle + extra, 1)
p.write_text(t)
