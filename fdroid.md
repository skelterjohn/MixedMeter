# F-Droid submission checklist — MixedMeter

Target app ID: `skelterjohn.mixedmeter`  
Source repo: [github.com/skelterjohn/mixedmeter](https://github.com/skelterjohn/mixedmeter)  
License: Apache-2.0

Use this list before opening a [packaging request](https://gitlab.com/fdroid/fdroiddata/-/issues/new?issuable_template=Request) or a merge request to [fdroiddata](https://gitlab.com/fdroid/fdroiddata).

Reference docs: [Quick Start Guide](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/) · [Inclusion Policy](https://f-droid.org/docs/Inclusion_Policy/)

---

## Already in good shape

- [x] Public source repository on GitHub
- [x] Apache-2.0 `LICENSE` and `NOTICE` in repo (code + assets listed)
- [x] No Firebase, Google Play Services, or other known non-FOSS SDKs in dependencies
- [x] Standard Gradle project; builds with `./gradlew` (no Android Studio required)
- [x] Author is the repo owner (no third-party permission needed)

---

## Repo and release hygiene

- [ ] **Push latest changes** — ensure GitHub `main` matches what you want reviewed (license files, README, etc.)
- [ ] **GitHub Releases** — repo currently has no published releases; create at least one when ready
- [ ] **Tag each release** — e.g. for `versionName` `1.3.4`, tag commit `v1.3.4` and push tags  
      F-Droid auto-update expects tags that match release versions
- [ ] **Changelog per version** — add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (max 500 chars)  
      Current `versionCode`: **15**

---

## Build fixes for F-Droid servers

F-Droid builds on their infrastructure **without** your upload keystore.

- [x] **Allow unsigned release builds** — `assembleRelease` works without `keystore.properties`; F-Droid signs the APK themselves
- [ ] **Verify locally without keystore** — temporarily rename/remove `keystore.properties`, then:
  ```powershell
  .\gradlew.bat assembleRelease
  ```
  Expect APK at `app/build/outputs/apk/release/app-release.apk`
- [ ] **Confirm ProGuard/R8** — release uses minify + shrink; ensure release build and a quick smoke test still work after the above

---

## Store metadata in the repo (Fastlane / Triple-T layout)

F-Droid pulls descriptions and graphics from the upstream repo. Add:

```
fastlane/metadata/android/en-US/
  short_description.txt       # ≤80 characters, no trailing period
  full_description.txt
  changelogs/15.txt           # one file per versionCode
  images/icon.png             # 512×512 PNG
  images/phoneScreenshots/
    1.png
    2.png
    …                         # at least 2 phone screenshots recommended
```

- [ ] Write `short_description.txt` (one line pitch)
- [ ] Write `full_description.txt` (can adapt from `README.md`)
- [ ] Export `images/icon.png` from launcher artwork (`design/ic_launcher_meter.svg` or `app/src/main/res/drawable/ic_launcher.png`)
- [ ] Capture phone screenshots (main metronome, sequence screen, settings)
- [ ] Add changelog file for versionCode **15** (and future codes on each release)

---

## Optional but helpful

- [ ] **GitHub repo description / About** — short blurb + link (GitHub page still shows empty description)
- [ ] **Issue tracker URL** — GitHub Issues is fine; mention it in the submission
- [ ] **Categories** — likely `Music` or `Audio` in fdroid metadata (maintainer can set this)
- [ ] **Reproducible builds** — optional for v1; see [F-Droid reproducible builds](https://f-droid.org/docs/Reproducible_Builds/) if you want byte-identical APKs later

---

## Submit to F-Droid

Choose one path:

### Option A — Request packaging (less work for you)

- [ ] Open a [new packaging request](https://gitlab.com/fdroid/fdroiddata/-/issues/new?issuable_template=Request)
- [ ] Include:
  - App name: **MixedMeter**
  - Application ID: `skelterjohn.mixedmeter`
  - Source: https://github.com/skelterjohn/mixedmeter
  - License: Apache-2.0
  - Current version: `1.3.4` (versionCode `15`)
  - Note: *I am the author and approve inclusion in F-Droid*
- [ ] Watch the issue and reply if maintainers ask questions

### Option B — Merge request to fdroiddata (faster if you write metadata yourself)

- [ ] Fork [gitlab.com/fdroid/fdroiddata](https://gitlab.com/fdroid/fdroiddata)
- [ ] Add `metadata/skelterjohn.mixedmeter.yml` (starter fields below)
- [ ] Run CI / `fdroid lint` if you set up [fdroidserver](https://gitlab.com/fdroid/fdroidserver)
- [ ] Open MR titled **New App: skelterjohn.mixedmeter**

Starter metadata sketch (adjust after local build test):

```yaml
Categories:
  - Music
License: Apache-2.0
AuthorName: John Asmuth
SourceCode: https://github.com/skelterjohn/mixedmeter
IssueTracker: https://github.com/skelterjohn/mixedmeter/issues
WebSite: https://github.com/skelterjohn/mixedmeter

RepoType: git
Repo: https://github.com/skelterjohn/mixedmeter

Builds:
  - versionName: '1.3.4'
    versionCode: 15
    commit: v1.3.4
    subdir: app
    gradle:
      - yes
      - assembleRelease

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: '1.3.4'
CurrentVersionCode: 15
```

---

## After acceptance

- [ ] Wait for build cycle (~24–48 hours after fdroiddata merge is typical)
- [ ] Confirm app on [f-droid.org](https://f-droid.org/) and install from F-Droid client
- [ ] For each future Play/F-Droid release:
  1. Bump `versionCode` / `versionName` in `app/build.gradle.kts`
  2. Add `fastlane/.../changelogs/<versionCode>.txt`
  3. Commit, tag `v<versionName>`, push tag
  4. F-Droid auto-update should pick up the new tag

---

## Quick pre-submit command summary

```powershell
# Must succeed WITHOUT keystore.properties present
.\gradlew.bat assembleRelease

# Play upload path (keystore required — not used by F-Droid)
.\gradlew.bat bundleRelease
```
