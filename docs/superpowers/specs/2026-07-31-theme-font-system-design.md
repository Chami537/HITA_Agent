# Theme Font System Design

## Goal

Make each UI style select a coherent font scheme so body text, labels, titles,
toolbars, drawer content, and timetable text follow the active visual theme.

## Current problem

`HitaComposeTheme` applies a shared Material3 typography setup. Only some title
components manually select the P5, Sumi, or Sora font. Body and label text use
the platform default, while individual screens can bypass the theme with local
`fontFamily` and `fontWeight` values. This makes styles visually inconsistent.

## Design

Add a theme-level `HitaFontScheme` selected from `HitaThemeStyle`:

| Style | Primary font | Purpose |
| --- | --- | --- |
| Classic | Noto Sans SC | Neutral and readable default |
| Apple Glass | MiSans or Noto Sans SC | Light, modern interface text |
| Cyber | Orbitron for Latin/digits with Chinese fallback | Technical display accents |
| Sora Cloud | Noto Serif SC | Editorial Japanese-poster character |
| Persona 5 | Existing `p5_title.ttf` | Preserve current P5 identity |
| Deep Space | Space Grotesk for Latin/digits with Chinese fallback | Geometric futuristic accents |
| Sumi | Existing `sumi_title.ttf` | Preserve current ink-brush identity |

The implementation will expose the active scheme through the design package and
derive Material3 `Typography` from it. Display, headline, title, body, and label
styles will inherit the selected family. Explicit theme title helpers will be
reduced to compatibility wrappers or removed where the Material typography
already provides the same result.

Fonts without complete Chinese glyph coverage will rely on Android/Compose font
fallback for Chinese text. The implementation will avoid applying a Latin-only
display font to all text when that would harm Chinese readability.

## Scope

- Add the required font resources and license notices.
- Add a deterministic style-to-font mapping.
- Apply the mapping in `HitaComposeTheme`.
- Update toolbar, drawer, timetable, and other manually overridden title text to
  inherit the theme scheme where appropriate.
- Preserve monospace text for code and technical content.
- Add unit tests for every `HitaThemeStyle` mapping and the existing P5/Sumi
  resource selection.

## Non-goals

- No redesign of font sizes, line heights, spacing, or weights unless required
  for a font to remain legible.
- No changes to XML compatibility themes beyond leaving their legacy fallback
  behavior intact.
- No changes to application behavior, navigation, or theme color systems.

## Verification

- Run the focused font/theme unit tests.
- Run `./gradlew assembleDebug`.
- Confirm every theme resolves a non-null font scheme.
- Confirm P5 and Sumi still use their existing bundled fonts.
- Confirm Chinese text remains readable in every theme and code blocks remain
  monospace.
