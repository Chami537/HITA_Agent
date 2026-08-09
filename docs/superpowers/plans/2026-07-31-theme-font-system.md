# Theme Font System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every HITA UI style resolve a deterministic font scheme and let Compose typography inherit that scheme across body, label, title, toolbar, drawer, and timetable text.

**Architecture:** Add a small `HitaFontScheme` value model in the design package. A pure mapping function converts `HitaThemeStyle` to the scheme; `HitaComposeTheme` converts the scheme into Material3 `Typography`. Existing P5 and Sumi helpers remain as compatibility accessors, while direct component overrides use the active theme font instead of repeating style-specific fallback chains.

**Tech Stack:** Kotlin, Jetpack Compose Material3, Android resource fonts, JUnit, Gradle.

## Global Constraints

- Preserve the existing P5 `p5_title.ttf` and Sumi `sumi_title.ttf` resources.
- Keep monospace text for code and technical content.
- Do not change theme colors, layout spacing, navigation, or application behavior.
- Keep XML compatibility themes on their existing `sans-serif-medium` fallback.
- Use `?.`/safe access patterns and Kotlin official style.
- Run focused tests before `./gradlew assembleDebug`.
- Do not commit without user confirmation.

---

### Task 1: Add the font scheme model and mapping

**Files:**
- Create: `app/src/main/java/cn/limpu/hita/ui/design/HitaFontScheme.kt`
- Test: `app/src/test/java/cn/limpu/hita/ui/design/HitaFontSchemeTest.kt`

**Interfaces:**
- Produces `data class HitaFontScheme(val primary: FontFamily, val display: FontFamily = primary)`.
- Produces `fun hitaFontSchemeFor(style: HitaThemeStyle): HitaFontScheme`.
- Produces `@Composable fun hitaActiveFontScheme(): HitaFontScheme` using `HitaTheme.style`.

- [ ] **Step 1: Write failing mapping tests**

Test that every `HitaThemeStyle` maps to a non-null scheme, that the seven
styles are deterministic, and that P5/Sumi use their existing resource-backed
families. Test the mapping function directly so no Android activity or theme
rendering is required.

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```powershell
./gradlew :app:testDebugUnitTest --tests 'cn.limpu.hita.ui.design.HitaFontSchemeTest'
```

Expected: compilation failure because `HitaFontScheme` and
`hitaFontSchemeFor` do not exist yet.

- [ ] **Step 3: Implement the minimal scheme mapping**

Use this primary-family mapping:

```kotlin
CLASSIC -> FontFamily.SansSerif
APPLE_GLASS -> Inter
CYBER -> Orbitron
SORA_CLOUD -> FontFamily.Serif
P5 -> existing p5_title.ttf family
DEEP_SPACE -> Space Grotesk
SUMI -> existing sumi_title.ttf family
```

Use Android/Compose fallback for Chinese glyphs when a Latin display font does
not contain them. Keep P5 and Sumi at their current `Black` and `Bold` weights.

- [ ] **Step 4: Run the focused test and verify it passes**

Run the same Gradle test command. Expected: all mapping assertions pass.

### Task 2: Add and register the new font resources

**Files:**
- Create: `app/src/main/res/font/inter.ttf`
- Create: `app/src/main/res/font/orbitron.ttf`
- Create: `app/src/main/res/font/space_grotesk.ttf`
- Create: `app/src/main/assets/OFL_Inter.txt`
- Create: `app/src/main/assets/OFL_Orbitron.txt`
- Create: `app/src/main/assets/OFL_SpaceGrotesk.txt`
- Modify: `app/src/main/java/cn/limpu/hita/ui/design/HitaFontScheme.kt`

**Interfaces:**
- `HitaFontScheme` owns the resource-backed `FontFamily` instances and exposes
  the same stable mapping used by Task 1.

- [ ] **Step 1: Add the licensed font files**

Add the Google Fonts OFL font files with Android-safe lowercase resource names.
Record the corresponding license text beside the existing P5/Sumi license
notices. Do not add a runtime download or network dependency.

- [ ] **Step 2: Run resource compilation**

Run:

```powershell
./gradlew :app:compileDebugKotlin
```

Expected: Android resource processing and Kotlin compilation succeed with the
new font resources.

- [ ] **Step 3: Re-run the focused mapping test**

Run the Task 1 test command and confirm the resource-backed families load.

### Task 3: Make Material3 typography inherit the active font scheme

**Files:**
- Modify: `app/src/main/java/cn/limpu/hita/ui/design/HitaComposeTheme.kt`
- Modify: `app/src/main/java/cn/limpu/hita/ui/design/HitaFontScheme.kt`
- Test: `app/src/test/java/cn/limpu/hita/ui/design/HitaFontSchemeTest.kt`

**Interfaces:**
- `HitaTheme.fonts` exposes the active scheme through the existing theme object.
- `HitaComposeTheme` builds all Material3 display, headline, title, body, and
  label styles from the active scheme's primary/display families.

- [ ] **Step 1: Add a failing typography-family assertion**

Extend the unit test around the pure typography builder, asserting that the
body, label, title, and display styles all use the expected family for each
theme scheme.

- [ ] **Step 2: Run the focused test and verify it fails**

Run the focused test command. Expected: the typography-family assertion fails
because `HitaComposeTheme` still uses the fixed `hitaTypography` value.

- [ ] **Step 3: Implement theme typography derivation**

Replace the fixed `FontFamily.Serif` assignments with a builder that copies
all Material3 typography slots using the active scheme. Keep font sizes,
line heights, and weights unchanged. Add `HitaTheme.fonts` as a read-only
composition-local accessor.

- [ ] **Step 4: Run the focused test and verify it passes**

Run the focused test command and confirm all family assertions pass.

### Task 4: Remove conflicting local font overrides

**Files:**
- Modify: `app/src/main/java/cn/limpu/hita/ui/main/MainActivity.kt`
- Modify: `app/src/main/java/cn/limpu/hita/ui/main/timetable/TimetableFragment.kt`
- Modify: `app/src/main/java/cn/limpu/hita/ui/design/HitaPersonaSurfaces.kt`
- Modify: `app/src/main/java/cn/limpu/hita/ui/design/HitaSumiSurfaces.kt`
- Modify: `app/src/main/java/cn/limpu/hita/ui/design/HitaSoraCloudSurfaces.kt`

**Interfaces:**
- Existing `hitaPersonaTitleFont`, `hitaSumiTitleFont`, and
  `hitaSoraTitleFont` remain source-compatible wrappers that return the active
  scheme's display family where callers still need them.

- [ ] **Step 1: Add a regression assertion for active theme selection**

Test that switching the style changes the resolved primary/display family and
does not fall back to the P5 or Sumi family for unrelated styles.

- [ ] **Step 2: Run the focused test and verify it fails**

Run the focused test command and confirm the regression assertion exposes the
old helper-specific behavior.

- [ ] **Step 3: Update component callers**

Replace repeated nullable fallback chains in toolbar, drawer, and timetable
titles with `HitaTheme.fonts.display`. Keep explicit `FontFamily.Monospace`
and unrelated code-text overrides unchanged.

- [ ] **Step 4: Run the focused test and compile**

Run:

```powershell
./gradlew :app:testDebugUnitTest --tests 'cn.limpu.hita.ui.design.HitaFontSchemeTest'
./gradlew :app:compileDebugKotlin
```

Expected: tests and compilation pass.

### Task 5: Full verification and handoff

**Files:**
- No new production files.

- [ ] **Step 1: Run all unit tests**

```powershell
./gradlew testDebugUnitTest
```

- [ ] **Step 2: Build the debug APK**

```powershell
./gradlew assembleDebug
```

- [ ] **Step 3: Review the final diff**

Confirm only the font resources, license files, design package, affected UI
callers, tests, and design/plan documents changed. Leave unrelated untracked
files untouched.

- [ ] **Step 4: Report results and request commit confirmation**

Summarize the mapping, verification results, and changed files. Do not create a
commit until the user explicitly confirms.
