# MixedMeter Release + Version Bump Instructions

These instructions apply whenever the user asks to "bump the version" or requests release notes.

## Source of truth

- Version values live in `app/build.gradle.kts`:
  - `versionCode`
  - `versionName` (semantic version: `MAJOR.MINOR.PATCH`)

## Required workflow for each version bump request

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

## Release-note style

- Keep notes concise and user-facing.
- Group by impact when helpful (features, fixes, UI/UX, maintenance).
- Reflect actual commit history only; do not invent changes.

## Safety rules

- Do not bump major without explicit user instruction.
- Do not skip commit review scope (must be "since last version bump commit").
- Do not create a git commit unless the user explicitly asks for one.
