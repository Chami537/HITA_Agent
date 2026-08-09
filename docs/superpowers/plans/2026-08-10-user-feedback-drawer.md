# User Feedback Drawer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a drawer button that opens the approved Tencent questionnaire.

**Architecture:** Extend the existing callback chain in `MainActivity.kt` and render one additional existing `DrawerItem`. Reuse the existing Android intent pattern and string/icon resources without introducing new components or dependencies.

**Tech Stack:** Kotlin, Jetpack Compose, Android resources

## Global Constraints

- Label must be `用户反馈`.
- URL must be `https://wj.qq.com/s2/27538298/9fr9/`.
- Preserve existing drawer styling and all unrelated uncommitted changes.
- Do not commit or push without explicit confirmation.

---

### Task 1: Add the user-feedback drawer action

**Files:**
- Modify: `app/src/main/java/cn/limpu/hita/ui/main/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: existing `DrawerItem(icon: Int, title: String, onClick: () -> Unit)`
- Produces: `onUserFeedback: () -> Unit` callback from `MainScreen` to `MainDrawer`

- [x] **Step 1: Update the existing string resource**

Set `main_drawer_menu_report` to `用户反馈`.

- [x] **Step 2: Wire the external-link callback**

Pass `onUserFeedback` through `MainScreen` and open the approved URL using:

```kotlin
startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wj.qq.com/s2/27538298/9fr9/")))
```

- [x] **Step 3: Render the existing drawer component**

Insert this item between agreement/privacy and About HITA:

```kotlin
DrawerItem(
    R.drawable.ic_baseline_edit_24,
    stringResource(R.string.main_drawer_menu_report),
    onUserFeedback
)
```

- [x] **Step 4: Verify**

Run:

```powershell
.\gradlew.bat :app:assembleDebug
git diff --check
```

Expected: Debug build succeeds and the diff check exits with code 0.

- [x] **Step 5: Commit after user confirmation**

Show the scoped diff summary and wait for explicit commit confirmation.
