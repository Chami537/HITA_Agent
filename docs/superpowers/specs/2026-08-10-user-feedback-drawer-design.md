# User Feedback Drawer Design

## Goal

Add a “用户反馈” button to the right-side drawer that opens
`https://wj.qq.com/s2/27538298/9fr9/` in the system browser.

## Design

- Reuse the existing `DrawerItem` component so spacing, typography, colors, and theme behavior remain identical.
- Place the button between the agreement/privacy item and the About HITA item.
- Reuse `ic_baseline_edit_24` as the leading icon.
- Pass a dedicated `onUserFeedback` callback through `MainScreen` to `MainDrawer` and open the URL with `Intent.ACTION_VIEW`.
- Reuse the existing `main_drawer_menu_report` string resource, changing its unused value to “用户反馈”.

## Validation

- Build the Debug APK so Compose signatures, resources, and the external-link callback compile together.
- Inspect the scoped diff and run `git diff --check`.
- Do not modify or stage existing course-selection work, `AGENTS.md`, or `graphify-out/`.
