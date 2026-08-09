# Shenzhen Course Browser Collapsing Header Implementation Plan

> **Required execution skill:** Use `superpowers:subagent-driven-development` task-by-task. Each task requires an implementer, a task-scoped reviewer covering both spec compliance and code quality, and a final whole-change review.

**Goal:** Let the Shenzhen course browser's controls scroll away with the course list while keeping navigation and selection actions fixed and preserving all course-selection behavior.

**Architecture:** Keep the existing fixed `TopAppBar` and bottom `SelectionActionBar`, but replace the fixed controls plus course-only list with one stateful `LazyColumn`. Derive a small top-bar shortcut from list position and animate the shared list back to item zero. Keep the login/error-only branch independent so recovery actions remain visible.

**Tech stack:** Kotlin, Jetpack Compose Material 3, Android ViewModel policy helper, JUnit 4, Gradle, ADB.

## Global Constraints

- Do not change selection, concurrency, scheduling, retry, timing, or one-shot POST parameters or behavior.
- Do not perform a real course-selection submission during verification.
- Keep `TopAppBar` fixed and keep `SelectionActionBar` fixed at the bottom for the available-course source.
- Source tabs, explanatory copy, filters/search, recommendation, preview, inline error, task cards, page summary, course cards, loading, and pagination must share one `LazyColumn` and scroll away together.
- Show a compact filter/top shortcut in `TopAppBar` only after the shared list has moved away from its top; tapping it must smoothly return to item zero.
- Login-required and page-less error states must remain usable and must not show a submission bar.
- Preserve all pre-existing dirty-worktree changes, especially the six-file term-list fix. Stage and commit only files intentionally changed by each task.
- Use existing resources and visual tokens; do not add dependencies or redesign course cards.

---

### Task 1: Specify the collapsed-header shortcut policy

**Files:**

- Modify: `app/src/test/java/cn/limpu/hita/ui/eas/catalog/ShenzhenCourseSelectionUiPolicyTest.kt`
- Modify: `app/src/main/java/cn/limpu/hita/ui/eas/catalog/ShenzhenCourseCatalogViewModel.kt`

- [ ] **Step 1: Add the failing policy test**

Add one test named `filter shortcut appears only after a scrollable list leaves the top`. Assert these exact cases:

```kotlin
assertFalse(
    ShenzhenCourseSelectionUiPolicy.shouldShowFilterShortcut(
        firstVisibleItemIndex = 0,
        canScrollBackward = false
    )
)
assertFalse(
    ShenzhenCourseSelectionUiPolicy.shouldShowFilterShortcut(
        firstVisibleItemIndex = 1,
        canScrollBackward = false
    )
)
assertTrue(
    ShenzhenCourseSelectionUiPolicy.shouldShowFilterShortcut(
        firstVisibleItemIndex = 1,
        canScrollBackward = true
    )
)
```

- [ ] **Step 2: Prove RED**

Run:

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests "cn.limpu.hita.ui.eas.catalog.ShenzhenCourseSelectionUiPolicyTest" --console=plain
```

Expected: compilation fails because `shouldShowFilterShortcut` does not exist.

- [ ] **Step 3: Add the minimal policy helper**

Add this pure helper to `ShenzhenCourseSelectionUiPolicy`:

```kotlin
fun shouldShowFilterShortcut(
    firstVisibleItemIndex: Int,
    canScrollBackward: Boolean
): Boolean = firstVisibleItemIndex > 0 && canScrollBackward
```

- [ ] **Step 4: Prove GREEN**

Run the same focused command. Expected: all tests in `ShenzhenCourseSelectionUiPolicyTest` pass.

- [ ] **Step 5: Review and commit only Task 1 files**

Inspect `git diff --check` and the two-file diff. Commit only the policy and its test with message:

```text
test: define Shenzhen catalog collapse shortcut policy
```

---

### Task 2: Move browser controls into the course list

**Files:**

- Modify: `app/src/main/java/cn/limpu/hita/ui/eas/catalog/ShenzhenCourseCatalogActivity.kt`

- [ ] **Step 1: Introduce shared list state and derived shortcut visibility**

In `ShenzhenCourseCatalogScreen`, create `rememberLazyListState()` and `rememberCoroutineScope()`. Derive shortcut visibility with `derivedStateOf` and `ShenzhenCourseSelectionUiPolicy.shouldShowFilterShortcut(listState.firstVisibleItemIndex, listState.canScrollBackward)`.

Add only the imports required for `rememberLazyListState`, `derivedStateOf`, `rememberCoroutineScope`, and `kotlinx.coroutines.launch`.

- [ ] **Step 2: Add the compact top-bar shortcut**

Before the existing refresh action, conditionally render an `IconButton`. Use the existing `R.drawable.ic_baseline_search_24` resource, content description `返回筛选条件`, and `coroutineScope.launch { listState.animateScrollToItem(0) }`.

The button must not render when the list is at the top or cannot scroll backward. Keep the refresh action unchanged.

- [ ] **Step 3: Build one main scrolling surface**

Retain the existing fixed `TopAppBar`. Retain the existing early branch for `NeedsWebLogin` and page-less `Error`, including its task cards and recovery card.

For the normal browser branch, replace the fixed source row, explanatory text, `CatalogFilters`, recommendation button, preview button, inline error text, and course-only `LazyColumn` with one:

```kotlin
LazyColumn(
    state = listState,
    modifier = Modifier.weight(1f),
    contentPadding = PaddingValues(
        start = tokens.spacing.lg,
        end = tokens.spacing.lg,
        top = tokens.spacing.xs,
        bottom = tokens.spacing.xl
    ),
    verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm)
)
```

Put these in this exact order as list content:

1. Source chip row.
2. Source explanation.
3. `CatalogFilters`.
4. Recommendation button.
5. Preview button.
6. Inline error text when present.
7. Existing active and terminal selection job items.
8. Page summary.
9. Course cards.
10. Pagination, or the existing loading block when no page exists.

Adapt padding formerly supplied by the fixed outer layout so controls keep the current visual spacing without double horizontal padding. Do not change callbacks, labels, card keys, selection checks, loading dimensions, or pagination behavior.

- [ ] **Step 4: Preserve the fixed bottom action bar**

Keep `SelectionActionBar` after the shared `LazyColumn`, conditional only on `source == ShenzhenCourseCatalogSource.AVAILABLE`, with its existing callbacks and permission/alarm flow unchanged.

- [ ] **Step 5: Run focused regression tests**

Run:

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests "cn.limpu.hita.ui.eas.catalog.ShenzhenCourseSelectionUiPolicyTest" --console=plain
```

Expected: pass.

- [ ] **Step 6: Run full unit and build verification**

Run:

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest :app:assembleDebug --rerun-tasks --console=plain
git diff --check
```

Expected: all unit tests pass, debug APK builds, and whitespace check is clean.

- [ ] **Step 7: Install and verify on the connected device without submitting**

Install:

```powershell
E:\Android\sdk\platform-tools\adb.exe -s 3B15B40043600000 install -r -t app\build\outputs\apk\debug\app-debug.apk
```

Open the app normally through MainActivity, navigate through `功能中心` to `深圳课程浏览`, and verify:

- At the top, all controls remain visible and the shortcut is absent.
- Scrolling courses down removes tabs, explanation, filters, recommendation, preview, and task cards from view and expands usable course space.
- The top app bar and available-source bottom action bar remain fixed.
- The compact shortcut appears after collapse and smoothly returns to the filters at the top.
- School-source, loading, empty, inline-error, login-required, and page-less error paths remain usable when reachable without external mutation.
- Do not tap immediate submit, scheduled submit confirmation, or any action that sends a real selection POST.

- [ ] **Step 8: Review and commit only the UI task**

Review the Activity diff for behavior drift and verify the six-file term-list fix remains intact. Commit only the Activity changes with message:

```text
feat: collapse Shenzhen course browser controls on scroll
```

---

### Task 3: Final integrated review

- [ ] Generate a whole-change review package from the pre-task base through Task 2.
- [ ] Have a fresh reviewer verify the approved design, all Global Constraints, test evidence, and the absence of parameter or submission-flow changes.
- [ ] Resolve any Critical or Important findings through the subagent fix/re-review loop.
- [ ] Confirm `git status --short` contains only the preserved pre-existing term-list changes and unrelated untracked files.
- [ ] Report the exact test/build/device evidence and commits; do not push unless the user separately requests it.
