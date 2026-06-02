# HITA Design System

HITA is migrating from View/XML screens to Jetpack Compose screens. New UI
should use the shared design tokens instead of hard-coded colors, dp values,
text appearances, or one-off widget styles.

The goal is the same idea as utility tokens in Tailwind CSS: one predictable
source for visual decisions, while keeping the current HITA style unchanged.

## Entry Points

- XML resources: `app/src/main/res/values/design_system.xml`
- Kotlin facade: `app/src/main/java/cn/limpu/hita/ui/design/HitaDesign.kt`
- Compose theme: `app/src/main/java/cn/limpu/hita/ui/design/HitaComposeTheme.kt`
- Existing theme mapping: `app/src/main/res/values/themes.xml`
- Existing component styles: `app/src/main/res/values/styles.xml`

`design_system.xml` is a facade over the existing resources. It should not
introduce a new visual language. If a value must change, change the underlying
semantic resource only when the whole app should visually change.

## Compose Usage

Every Compose screen must be wrapped by `HitaComposeTheme`:

```kotlin
HitaComposeTheme(style = HitaThemeStyle.Classic) {
    // screen content
}
```

Use Material theme colors and HITA tokens:

```kotlin
val tokens = HitaTheme.tokens

Text(
    text = title,
    color = MaterialTheme.colorScheme.onSurface,
    modifier = Modifier.padding(tokens.spacing.lg)
)
```

Theme styles are switched through `HitaThemeStyle`. `Classic` must remain a
pixel-level match for the current app style. New styles should be added by
extending the Compose theme layer first, not by hard-coding colors inside
screens.

## XML Usage

Prefer `ds_*` resources in new layouts:

```xml
android:padding="@dimen/ds_space_lg"
android:textColor="@color/ds_color_text_primary"
app:cardCornerRadius="@dimen/ds_radius_lg"
style="@style/Ds.Text.TitleMedium"
```

Prefer shared component styles:

```xml
style="@style/Ds.Widget.Card"
style="@style/Ds.Widget.Button"
style="@style/Ds.Widget.Button.Outlined"
style="@style/Ds.Widget.Toolbar"
```

Use raw hex colors only for exceptional assets or previews. Avoid introducing
new `#RRGGBB` values in layouts.

## Kotlin Usage

For custom Views and dynamic UI:

```kotlin
import cn.limpu.hita.ui.design.HitaDesign
import cn.limpu.hita.ui.design.dsColor
import cn.limpu.hita.ui.design.dsDimenPx

val color = context.dsColor(HitaDesign.Color.brand)
val padding = context.dsDimenPx(HitaDesign.Space.lg)
```

Use `dsThemeColor(...)` when the value must come from the active theme attr:

```kotlin
val textColor = context.dsThemeColor(R.attr.colorOnSurface)
```

## Migration Rule

The long-term goal is no `res/layout/*.xml` for main app screens. The migration
can temporarily keep XML while a screen is being ported, but a completed screen
should remove its layout binding and XML layout file.

Android system XML resources remain normal and should not be removed just to
chase a zero-XML count:

- `AndroidManifest.xml`
- `res/values/*.xml`
- `res/drawable/*.xml`
- `res/menu/*.xml` until the owning screen is migrated
- App widget RemoteViews XML unless that widget is separately migrated to Glance

When adding or touching UI:

1. Use `ds_*` colors, spacing, radius, and text styles.
2. In Compose, use `HitaComposeTheme`, `MaterialTheme`, and `HitaTheme.tokens`.
3. In XML that has not been migrated, prefer `Ds.Widget.*` component styles.
4. Keep old compatibility names such as `cruel_summer_primary` working.
5. Add a new design token only when the value is reused or has semantic meaning.
