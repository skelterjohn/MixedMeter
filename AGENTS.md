# MixedMeter Agent Notes

Living scratch file for agent context. Review this at the start of relevant tasks and update it when the user states preferences, when project structure changes, or when useful architectural notes emerge.

Do not treat this as user-facing documentation unless the user asks for that.

## How the user likes to work

- **Version bumps:** When asked to bump the version, compile release notes and update `app/build.gradle.kts` (see below).
- **Release notes format:** Write release notes in a Google Play Console-friendly plain-text format (short lines/bullets, minimal markup) so they can be pasted directly.
- **Version bump commits:** When creating a version-bump commit, include release notes in the commit body.
- **Commits:** Write clear commit messages (subject + short body explaining why). Only create commits when explicitly asked. On this machine, follow **Git commits (Windows)** below — do not retry failed `git commit` patterns.
- **README maintenance:** Keep `README.md` accurate for GitHub visitors. When adding or changing user-facing behavior, update README in the same change (or immediately after) — do not leave it for a separate cleanup pass. See **README upkeep** below.
- **Scope:** Prefer focused changes; match existing code style and conventions.
- **This file:** Keep durable notes here about preferences, project layout, and code structure so future sessions stay consistent.
- **Signed releases:** Play upload requires gitignored `keystore.properties` (see `keystore.properties.example`). **`bundleRelease`** (signed AAB + artifacts) → `app/release/`. **`assembleRelease`** builds an unsigned release APK when no keystore is present (F-Droid); signs automatically when `keystore.properties` exists. Release builds use R8 and NDK debug symbols — see **Native debug symbols** below.
- **Native debug symbols (Play Console):** Dependencies ship `.so` libraries (Compose graphics path, DataStore). AGP extracts symbol tables only when the NDK is installed (`app/build.gradle.kts` pins `ndkVersion`). Install via Android Studio → Settings → Android SDK → SDK Tools → **NDK (Side by side)** (match `ndkVersion` in `app/build.gradle.kts`). After `bundleRelease`, confirm `app/release/native-debug-symbols.zip` exists (or upload it manually in App bundle explorer → Native debug symbols). If the zip is missing, the build logs a warning and Play will keep the “native code, no debug symbols” warning. Re-upload a new AAB built with NDK present; symbols are also embedded in the bundle under `BUNDLE-METADATA/com.android.tools.build.debugsymbols/` when extraction succeeds.

### Git commits (Windows)

This repo is on **Windows PowerShell** with **Git 2.30.1** (`C:\Program Files\Git\cmd\git.exe`). Shell and tooling quirks cause failed commits if you use the usual Linux/bash recipes.

**Shell**

- Chain commands with `;`, not `&&` (older PowerShell).
- `Set-Location` to the repo root once per command block.

**Commit command (use this every time)**

1. `git status`, `git diff`, `git log` (parallel is fine) — review before committing.
2. `git add` only the intended paths.
3. Write the message to `.git/COMMIT_MSG_TMP` (subject line, blank line, body). Keep it under `.git/` so it is not staged.
4. Commit with the **full git path** and **`-F`**, not `-m`:

```powershell
Set-Location "c:\Users\jasmu\AndroidStudioProjects\MixedMeter"
& 'C:\Program Files\Git\cmd\git.exe' add <paths>
& 'C:\Program Files\Git\cmd\git.exe' commit -F .git/COMMIT_MSG_TMP
Remove-Item .git/COMMIT_MSG_TMP -ErrorAction SilentlyContinue
& 'C:\Program Files\Git\cmd\git.exe' status
```

**Do not use (they failed here)**

- `git commit -m "..."` / multiple `-m` — errors with `unknown option 'trailer'` (wrapper vs. Git 2.30).
- Bash-style `git commit -m "$(cat <<'EOF' ... EOF)"` — not PowerShell.
- Bare `git commit` without `C:\Program Files\Git\cmd\git.exe` if the trailer error appears.

**Do not** run `git config`, `--no-verify`, or other workarounds unless the user asks.

## Project layout

Standard single-module Android app.

| Path | Purpose |
|------|---------|
| `app/build.gradle.kts` | App config, `versionCode`, `versionName` |
| `app/src/main/AndroidManifest.xml` | Four activities: `MainActivity`, `SettingsActivity`, `InformationActivity`, `SequenceActivity` |
| `app/src/main/java/skelterjohn/mixedmeter/` | All app Kotlin source (flat package, file-per-concern) |
| `app/src/main/java/skelterjohn/mixedmeter/ui/theme/` | Compose Material theme scaffolding (`Color`, `Type`, `Theme`) |
| `app/src/main/res/` | Drawables, mipmaps, animations, strings, XML themes |
| `design/` | Source assets (e.g. launcher SVG) |
| `gradle/libs.versions.toml` | Version catalog for dependencies |

Tech stack: Kotlin, Jetpack Compose, Material 3, DataStore preferences, Gradle Kotlin DSL.

## Code structure

All source lives in package `skelterjohn.mixedmeter`. Activities host Compose UI; logic is split into focused files rather than deep subpackages.

**Screens (activities)**

- `MainActivity.kt` — primary metronome UI: BPM dial, time signature, beat boxes, playback controls
- `SequenceActivity.kt` — sequence editor and playback
- `SettingsActivity.kt` — tone, theme, and app settings
- `InformationActivity.kt` — creator email, Discord, and GitHub links

**UI building blocks**

- `CircleDisplay.kt` — circular BPM / percent dial and related geometry helpers
- `NavCornerButtons.kt` — bottom nav icons and sizing for small displays
- `SequenceItemViews.kt`, `SequenceRepeatCountPicker.kt`, `SequenceSaveLoadDialogs.kt` — sequence UI pieces
- `ActivityTransitions.kt` — slide transitions between activities

**Themes**

- `AppTheme.kt` — named app themes (light/dark/lava/etc.), `ProvideAppTheme`, `currentAppTheme()`
- `ui/theme/` — base Compose `MixedMeterTheme` wiring

**Metronome audio / timing**

- `MetronomeTiming.kt` — beat schedules, beat-box timing, click-active state encode/decode
- `MetronomePlayback.kt` — schedule helpers, BPM scaling, beat-box progress for UI
- `MetronomeClickWav.kt` — WAV generation for click tones
- `MetronomeLoopRenderer.kt` — renders click audio into loop buffers
- `MetronomeLoopPlayer.kt` — plays prerendered metronome loops

**Sequence data / playback**

- `SequenceStore.kt` — DataStore persistence, flows for items/names/saved sequences
- `SequenceMap.kt` — map UI for sequence items
- `SequencePrerender.kt` — prerender sequence segments for loop playback

## README upkeep

`README.md` is the public feature list for GitHub visitors. Keep it current whenever user-facing behavior changes.

### When to update

- New screen, navigation path, or gesture
- New or renamed setting, tone, theme, or playback option
- Changed defaults or workflows described in **Basic Usage**
- License or distribution notes that visitors should see

Skip README updates for internal refactors, build/CI-only changes, or fixes that do not change what users can do.

### What to update

1. **Core Features** — add or revise bullets for the affected screen(s).
2. **Basic Usage** — adjust numbered steps when the default workflow changes.
3. **Tech Notes** — only when stack or persistence behavior changes in a visitor-relevant way.
4. **This file** — if project layout or activities change (manifest table, **Screens** list).

### Checklist (use when shipping a user-facing feature)

- [ ] README **Core Features** reflects the new behavior
- [ ] README **Basic Usage** still matches how someone would try the feature
- [ ] AGENTS **Project layout** / **Screens** updated if files or activities were added

## Release + version bump

Applies when the user asks to "bump the version" or requests release notes.

### Source of truth

- Version values live in `app/build.gradle.kts`:
  - `versionCode`
  - `versionName` (semantic version: `MAJOR.MINOR.PATCH`)

### Required workflow

1. Locate the most recent **version bump commit** in git history.
   - Prefer commits whose message indicates a version update (for example: `push version`, `update version ...`).
   - Confirm by checking that `app/build.gradle.kts` changed in that commit.
2. Collect all commits from after that commit up to `HEAD`.
3. Review and summarize those commits as release notes.
4. Determine semantic version bump:
   - Bump `MAJOR` only when the user explicitly instructs it.
   - Otherwise bump `MINOR` when new features were added.
   - Otherwise bump `PATCH` when changes are fixes only.
5. Update `app/build.gradle.kts`:
   - Increment `versionName` according to the chosen bump.
   - Increment `versionCode` by 1 for each release bump.
6. Return:
   - A concise release-note summary covering all commits since the last bump commit.
   - The old and new `versionName` and `versionCode`.

### Release-note style

- Keep notes concise and user-facing.
- Group by impact when helpful (features, fixes, UI/UX, maintenance).
- Reflect actual commit history only; do not invent changes.

### Safety rules

- Do not bump major without explicit user instruction.
- Do not skip commit review scope (must be "since last version bump commit").
- Do not create a git commit unless the user explicitly asks for one.
