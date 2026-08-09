# Shenzhen Selection Opening-Time Sync Design

## Goal

Synchronize the Shenzhen academic system's official course-selection opening time into HITA, show it in the course-selection browser, and use it as the default value for scheduled submission while retaining manual confirmation.

This feature must not change existing selection parameters or execution semantics. It does not automatically create a job, automatically submit a course, extend scheduling limits, retry POST requests, or submit any course more than once.

## Existing Gap

The current Android flow synchronizes the selection term, selection pools, available courses, selected courses, and local scheduled jobs. It does not preserve an official opening-time field at the model, parser, repository, ViewModel, or UI layer.

The existing Class reference implementation resolves opening time with these known Shenzhen fields:

- `ktxkkssj`
- fallback `ksrq`

Its source precedence is:

1. Course row.
2. Current selection page/pool.
3. `xkgzszOne` selection rule.

The Android implementation will preserve this precedence while treating the opening window primarily as metadata for the current term and selection pool.

`xksj` is not an opening-time field. Existing fixtures use it as the timestamp of an already completed selection action, so it must remain excluded from opening-time and meeting parsing.

## Data Model

Add a small immutable opening-time value that keeps both normalized and diagnostic information:

```kotlin
data class ShenzhenSelectionOpenTime(
    val rawValue: String,
    val epochMillis: Long,
    val source: ShenzhenSelectionOpenTimeSource
)
```

The source enum distinguishes `COURSE`, `POOL_OR_PAGE`, and `SELECTION_RULE`. This is for deterministic precedence, tests, and diagnostics; it is not a new user setting.

The effective time is carried with the selection catalog context. Course items may retain a course-specific time when the response provides one, while the page/pool context retains the fallback time for courses that omit it.

No opening-time field is added to local job persistence. A scheduled job continues to store only the exact user-confirmed execution timestamp. Later refreshes of official metadata must not silently mutate an existing job.

## Parsing and Resolution

The parser accepts `ktxkkssj` first and `ksrq` second at each supported response level:

1. Course object.
2. Top-level current pool/page object.
3. Nested `xkgzszOne` object.

Blank, malformed, or unsupported values are ignored without failing course browsing. Parsing supports the formats already observed in the Shenzhen flow and Class reference, including ISO timestamps with a zone, ISO timestamps without a zone interpreted in `Asia/Shanghai`, and `yyyy-MM-dd HH:mm:ss` interpreted in `Asia/Shanghai`.

Resolution is deterministic:

- A valid course-level value overrides page/pool metadata for that course.
- Otherwise the valid page/pool value is used.
- Otherwise the valid nested selection-rule value is used.
- Otherwise the effective opening time is unknown.

When selected courses expose different valid effective times, the scheduled-submission default uses the earliest time, matching the existing Class reference behavior. The confirmation UI shows that it is the earliest official time for the selected set; HITA still creates only one user-confirmed job and does not retry any course automatically.

## Synchronization Flow

Opening time is synchronized as part of the existing read-only refresh path:

1. Load the current selection term and pool metadata.
2. Query the selected pool's available-course context.
3. Parse all supported opening-time locations from responses HITA already requests.
4. Merge the effective value into the current catalog state.
5. Refresh UI state together with the course page.

The first implementation does not add a speculative endpoint. If currently captured responses contain no supported value, HITA reports the time as unavailable and retains manual scheduling. Live response verification can later identify another official field or endpoint without weakening authentication or adding blind requests.

## User Interface

For the available-course source, add a compact opening-time status near the filters/preview controls inside the shared scrolling `LazyColumn`:

- Future valid time: `官方开放时间 · yyyy-MM-dd HH:mm:ss` and `待开放`.
- Time at or before the device's current time: show the official time and `已开放`.
- Missing or malformed value: `官方开放时间 · 暂未同步`.

The status scrolls away with the other browser controls. It is not shown for the read-only whole-school catalog source.

Refreshing the catalog refreshes this status. Authentication and page-less error screens remain usable and do not display a stale opening time from a previous pool.

## Scheduled Submission Prefill

The existing `定时提交` action keeps its permission, exact-alarm, date/time picker, confirmation, and creation flow.

After the existing permission gates pass:

- If the earliest official time is within the existing valid scheduling interval, initialize the date/time confirmation with that timestamp.
- The user must still review and confirm it. No job is created merely by opening the dialog.
- If no valid official time exists, use the current manual date/time flow unchanged.
- If the official time has already passed, show `已开放` and use the current manual date/time flow rather than preselecting an invalid past timestamp.
- If the official time is farther than the existing 24-hour maximum, display it but show that it is not yet schedulable. Do not create a job and do not extend the limit.
- The existing 500 ms minimum delay remains unchanged.

Immediate submission is unaffected and still requires its current explicit confirmation.

## Error Handling

- Missing opening time is a recoverable metadata condition, not a catalog failure.
- A malformed timestamp is ignored and can be logged without including cookies or full response bodies.
- Authentication/HTML responses continue through existing login/error handling and must not reuse stale opening-time metadata.
- Changing term, pool, source, or login state clears or replaces the effective opening time with the newly loaded context.
- Existing scheduled jobs are never rescheduled when official metadata changes.

## Testing

Focused parser tests cover:

- `ktxkkssj` and `ksrq` parsing.
- Course → page/pool → `xkgzszOne` precedence.
- Zoned and Shenzhen-local timestamp normalization.
- Blank/malformed values.
- `xksj` remains excluded.

Policy/ViewModel tests cover:

- Earliest valid time across selected courses.
- Official time within the existing interval is used as the initial confirmation value.
- Missing or past time falls back to manual scheduling.
- Time beyond 24 hours is displayed but rejected for job creation.
- Source/term/pool/auth changes do not retain stale metadata.

Regression verification includes all Android unit tests, Debug APK assembly, whitespace checking, and later device validation when ADB is reconnected. Device testing must not tap immediate confirmation or create a real selection POST.

## Non-Goals

- No change to the 500 ms minimum or 24-hour maximum.
- No change to concurrency, retry, alarm, notification, or one-shot POST behavior.
- No automatic job creation or automatic confirmation.
- No automatic rescheduling of saved jobs.
- No new speculative network endpoint in the first implementation.
- No use of `xksj` as an opening time.
