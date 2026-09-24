# EAS Credential Storage and Automatic Session Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Save EAS campus credentials in Android Keystore-backed encrypted storage and reuse them for Shenzhen relogin and Benbu/Weihai WebView autofill.

**Architecture:** Add a campus-scoped credential store separate from session tokens, migrate the existing stored username/password once, and wire successful login, logout, and recovery through that store. Keep Shenzhen's protocol relogin; add allowlisted WebView credential capture and autofill for Benbu/Weihai without auto-submitting forms.

**Tech Stack:** Kotlin, AndroidX `EncryptedSharedPreferences`, Android Keystore, Hilt, AndroidX WebKit message listener, Android WebView, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-09-24-eas-credential-autologin-design.md`

## Global Constraints

- Autofill only the campus-specific approved scheme, host, and `/authserver/login` path.
- Never automatically submit a Benbu or Weihai login form.
- Never bypass CAPTCHA or MFA; preserve the existing interactive fallback and retry cap.
- Keep credentials outside transient session state after migration and do not log credential values.
- Logout clears session tokens, cookies, and cached identity but retains saved credentials; explicit credential removal deletes only the requested campus/account entry.
- A credential-store read or migration failure must leave manual login available.

## Review Focus

- Legacy Shenzhen credential fields may be missing, blank, or partially present; migration must not create unusable credentials or break token loading. Cover with migration-policy unit tests in Task 1.
- Benbu/Weihai may redirect through multiple authentication hosts; autofill must match exact campus rules and reject lookalike hosts, wrong schemes, paths, and subframes. Cover the URL policy in Task 3.
- Login submission may lead to MFA, failure, cancellation, or a success URL; commit a pending candidate only on existing success detection and clear it otherwise. Cover the pending-candidate state transitions in Task 3.
- A different username at the same campus must not silently reuse or overwrite another account's password. Cover campus/account lookup and removal in Task 1.
- Logout can race with session refresh; saved credentials must survive without allowing stale token writes. Cover retained credentials and existing session-generation guard behavior in Task 2.

---

## File Map

- Create `app/src/main/java/cn/limpu/hita/data/source/preference/EasCredentialStore.kt`: campus/account-scoped encrypted credential persistence and one-time legacy migration.
- Modify `app/src/main/java/cn/limpu/hita/data/source/preference/EasPreferenceSource.kt`: expose legacy credential data to the migration path and stop treating password fields as current session state after migration.
- Modify `app/src/main/java/cn/limpu/hita/di/RepositoryModule.kt`: provide the singleton credential store.
- Modify `app/src/main/java/cn/limpu/hita/data/repository/EASRepository.kt`: save credentials after successful login; use stored credentials for Shenzhen relogin; split session logout from credential removal.
- Modify `app/src/main/java/cn/limpu/hita/data/work/ScoreReminderWorker.kt`: pass the credential store when constructing `EASRepository` outside Hilt.
- Modify `app/src/main/java/cn/limpu/hita/ui/event/EventItemFragment.kt`: pass the credential store to its direct `EASRepository` construction.
- Modify `app/src/main/java/cn/limpu/hita/data/source/web/eas/EASWebSource.kt`: allow Shenzhen's existing relogin path to read the credential store instead of `EASToken.password`.
- Create `app/src/main/java/cn/limpu/hita/data/repository/EasCredentialReloginPolicy.kt`: reject a saved password unless campus and account match the active Shenzhen session.
- Modify `app/src/main/java/cn/limpu/hita/ui/eas/login/PopUpLoginEAS.kt`: prefill from the credential store and pass campus/login context to WebView login.
- Modify `app/src/main/java/cn/limpu/hita/ui/eas/login/WebViewLoginActivity.kt`: capture submitted credentials on approved login pages, commit only after existing success detection, and autofill approved forms without submission.
- Create `app/src/main/java/cn/limpu/hita/ui/eas/login/EasWebCredentialPolicy.kt`: pure URL/host/path policy and credential form script payload construction, separated from WebView lifecycle.
- Modify `app/build.gradle`: add AndroidX WebKit for origin-scoped WebView message listeners if no existing dependency provides it.
- Create `app/src/test/java/cn/limpu/hita/data/source/preference/EasCredentialMigrationPolicyTest.kt`: migration and campus/account key behavior.
- Create `app/src/test/java/cn/limpu/hita/ui/eas/login/EasWebCredentialPolicyTest.kt`: allowlist and autofill policy behavior.
- Run `app/src/test/java/cn/limpu/hita/data/repository/EasSessionGenerationGuardTest.kt` as regression coverage for stale session writes.
- Create and run `app/src/test/java/cn/limpu/hita/data/repository/EasCredentialReloginPolicyTest.kt` for campus/account matching.

### Task 1: Credential store and migration

**Files:**
- Create: `app/src/main/java/cn/limpu/hita/data/source/preference/EasCredentialStore.kt`
- Modify: `app/src/main/java/cn/limpu/hita/data/source/preference/EasPreferenceSource.kt`
- Modify: `app/src/main/java/cn/limpu/hita/di/RepositoryModule.kt`
- Create: `app/src/test/java/cn/limpu/hita/data/source/preference/EasCredentialMigrationPolicyTest.kt`

**Interfaces:**
- Create `data class EasCredential(val campus: EASToken.Campus, val username: String, val password: String)`.
- Create `EasCredentialStore.get(campus: EASToken.Campus, username: String? = null): EasCredential?`.
- Create `EasCredentialStore.save(campus: EASToken.Campus, username: String, password: String)`.
- Create `EasCredentialStore.remove(campus: EASToken.Campus, username: String)`.
- Create `EasCredentialStore.migrateLegacyIfNeeded(legacy: EasPreferenceSource)`; migration is idempotent and does not clear session tokens.
- Create pure `LegacyEasCredentialPolicy.migratableCredential(campus, username, password): EasCredential?`; return a credential only for a nonblank Shenzhen username/password pair.
- Add a pure migration/key policy helper so JUnit tests do not need Android `Context` or a new mocking dependency.
- Call migration during `EASRepository` initialization before any session write can replace legacy fields; keep the migration-complete marker in the credential store.

- [x] **Step 1: Add failing migration-policy tests** for complete Shenzhen credentials, rejection of non-Shenzhen placeholder credentials, blank username/password, missing credentials, repeat migration, and campus/account isolation.

```kotlin
@Test
fun `non Shenzhen legacy password is not migrated`() {
    assertNull(
        LegacyEasCredentialPolicy.migratableCredential(
            EASToken.Campus.BENBU,
            username = "student",
            password = "student"
        )
    )
}
```

- [x] **Step 2: Run the focused test** with `./gradlew :app:testDebugUnitTest --tests 'cn.limpu.hita.data.source.preference.EasCredentialMigrationPolicyTest'`; confirm it fails because the policy and store do not exist.
- [x] **Step 3: Implement the store** using `EncryptedSharedPreferences` with an Android Keystore-backed key, campus/account-scoped entries, and an idempotent migration marker. Reject non-Shenzhen legacy password fields and incomplete pairs.
- [x] **Step 4: Register the store** as a singleton through `RepositoryModule`; keep manual login available if store creation or legacy migration throws.
- [x] **Step 5: Run the focused test** with the same command; confirm migration and key-policy cases pass.

### Task 2: Login save, Shenzhen relogin, and logout lifecycle

**Files:**
- Modify: `app/src/main/java/cn/limpu/hita/data/repository/EASRepository.kt`
- Modify: `app/src/main/java/cn/limpu/hita/data/source/web/eas/EASWebSource.kt`
- Modify: `app/src/main/java/cn/limpu/hita/data/work/ScoreReminderWorker.kt`
- Modify: `app/src/main/java/cn/limpu/hita/data/source/preference/EasPreferenceSource.kt`
- Run: `app/src/test/java/cn/limpu/hita/data/repository/EasSessionGenerationGuardTest.kt`
- Create: `app/src/test/java/cn/limpu/hita/data/repository/EasCredentialReloginPolicyTest.kt`
- Modify: `app/src/main/java/cn/limpu/hita/ui/event/EventItemFragment.kt`
- Create: `app/src/main/java/cn/limpu/hita/data/repository/EasCredentialReloginPolicy.kt`

**Interfaces:**
- Inject `EasCredentialStore` into `EASRepository` and pass it to the Shenzhen service through a credential-provider callback.
- `EasCredentialReloginPolicy.passwordFor(tokenCampus, tokenUsername, credential)` returns a password only for matching Shenzhen campus/account credentials.
- After `enrichLoginToken` accepts the final successful direct Shenzhen login and before publishing success, save the original username/password to `EasCredentialStore`. Do not save Benbu/Weihai cookie-login method arguments; Task 3 commits those captured WebView candidates.
- Keep `logout()` as session cleanup; expose `forgetCredentials(campus, username)` separately.

- [x] **Step 1: Run the existing session guard test** with `./gradlew :app:testDebugUnitTest --tests 'cn.limpu.hita.data.repository.EasSessionGenerationGuardTest'` to record the baseline for stale refresh behavior.
- [x] **Step 2: Wire successful login persistence** in `EASRepository`; save only the direct Shenzhen username/password after login enrichment succeeds, and do not persist on failed login, failed token enrichment, stale `expectedEpoch`, or cancellation.
- [x] **Step 3: Wire Shenzhen relogin** to fetch credentials by campus/account from `EasCredentialStore`; keep existing retry limits and `authEpoch`/session-generation checks; redact credentials from every log line.
- [x] **Step 4: Split logout from credential removal**; keep `clearEasToken()` session-only and expose `forgetCredentials()` to remove one selected saved account.
- [x] **Step 5: Update direct repository construction** in `ScoreReminderWorker` and run the session guard test command; confirm stale refresh protections still pass.

### Task 3: WebView capture and allowlisted autofill for Benbu/Weihai

**Files:**
- Create: `app/src/main/java/cn/limpu/hita/ui/eas/login/EasWebCredentialPolicy.kt`
- Modify: `app/src/main/java/cn/limpu/hita/ui/eas/login/WebViewLoginActivity.kt`
- Modify: `app/src/main/java/cn/limpu/hita/ui/eas/login/PopUpLoginEAS.kt`
- Create: `app/src/test/java/cn/limpu/hita/ui/eas/login/EasWebCredentialPolicyTest.kt`

**Interfaces:**
- `EasWebCredentialPolicy.matchesLoginPage(campus: EASToken.Campus, url: Uri, isMainFrame: Boolean): Boolean` accepts only these iOS-aligned rules:
  - Benbu: `http://ids-hit-edu-cn-s.ivpn.hit.edu.cn/.../authserver/login` and `https://ids.hit.edu.cn/.../authserver/login`.
  - Weihai: `https://webvpn.hitwh.edu.cn/.../authserver/login` and `https://ids.hit.edu.cn/.../authserver/login`.
- `WebViewLoginActivity` keeps a pending `EasCredential?` candidate in memory, obtained only from the login form on an approved main-frame page; save it through `EasCredentialStore` only when `isSuccessPage(url)` confirms the existing login success.
- Register `WebViewCompat.addWebMessageListener` with exact `http`/`https` origin rules for the iOS-aligned hosts. Accept candidates only when `isMainFrame` is true, the origin passes `matchesLoginPage`, and the message parses as one username/password pair. Do not use a JavaScript interface exposed to every iframe.
- `EasWebCredentialCaptureState.stage(candidate)`, `commitOnSuccess(save)`, and `discard()` make the save-after-success boundary unit-testable; failed/cancelled flows cannot commit a candidate.
- `EasWebCredentialPolicy.readOptionalCredential(read)` catches credential-store read failures and returns `null` so the page remains manually usable.
- The autofill script returns success/failure, dispatches `input` and `change`, and never calls click, `submit`, or `requestSubmit`.

```kotlin
@Test
fun `lookalike authentication host is rejected`() {
    assertFalse(
        EasWebCredentialPolicy.matchesLoginPage(
            EASToken.Campus.BENBU,
            Uri.parse("https://ids.hit.edu.cn.attacker.invalid/authserver/login"),
            isMainFrame = true
        )
    )
}
```

- [x] **Step 1: Add failing policy/state tests** for each allowed campus rule and rejection of HTTP/HTTPS mismatch, wrong campus host, suffix/lookalike host, wrong path, and subframe; also cover candidate stage/commit/discard and storage-read failure returning no credential.
- [x] **Step 2: Run the focused test** with `./gradlew :app:testDebugUnitTest --tests 'cn.limpu.hita.ui.eas.login.EasWebCredentialPolicyTest'`; confirm it fails before the policy exists.
- [x] **Step 3: Implement the pure policy and autofill script**; reuse iOS's exact scheme/host/path constraints and visible username/password field matching. Keep the existing WebView page-success detection as the only credential-save commit boundary.
- [x] **Step 4: Capture first-login credentials safely** by injecting a submit observer only on policy-approved top-level login pages; post the pair through the origin-scoped WebMessage listener. Retain the candidate in Activity memory across MFA/navigation; never log it; discard it on cancellation, failed login, activity destruction, or a changed campus/account.
- [x] **Step 5: Connect WebView lifecycle hooks**: inject autofill after `onPageFinished` for an approved login page; clear injected JS state on navigation out, finish, cancel, or destroy; on authenticated success, save the pending candidate before returning cookies to the login sheet.
- [x] **Step 6: Run the focused policy/state test**; inspect the resulting script to confirm there is no form submission call and that no credentials are sent to logs or intent extras.

### Task 4: Login-sheet prefill and end-to-end integration review

**Files:**
- Modify: `app/src/main/java/cn/limpu/hita/ui/eas/login/PopUpLoginEAS.kt`
- Modify: `app/src/main/java/cn/limpu/hita/ui/eas/login/WebViewLoginActivity.kt`
- Review: `app/src/main/java/cn/limpu/hita/data/repository/EASRepository.kt`

**Interfaces:**
- The login sheet reads the last saved credential for its selected campus and pre-fills Shenzhen native fields.
- The Benbu/Weihai WebView flow resolves the last saved credential for the selected campus before load and supplies it only to the allowlisted autofill controller; no password is placed in an intent extra.
- Missing credentials, store errors, unmatched pages, and failed autofill preserve the current manual login path.

- [x] **Step 1: Implement login-sheet prefill** for every selected campus from `EasCredentialStore`; keep current campus selection and student-type behavior unchanged.
- [x] **Step 2: Wire session recovery** so Shenzhen uses protocol relogin and Benbu/Weihai reopen WebView with saved autofill credentials, leaving form submission/MFA with the user.
- [x] **Step 3: Review every credential flow**: first save occurs only after success; normal logout preserves credentials; explicit removal is campus/account-scoped; storage errors, failed autofill, and cancellation leave manual login available and clear pending credentials.
- [x] **Step 4: Run `./gradlew :app:testDebugUnitTest` and `./gradlew :app:assembleDebug` after implementation approval; confirm unit tests and debug assembly pass before commit review.

## Spec Coverage Self-Review

- Credential store and Android Keystore-backed encryption: Task 1.
- Migration from existing encrypted/legacy token preferences: Task 1.
- Shenzhen success save and protocol relogin: Task 2.
- Benbu/Weihai save-after-success and allowlisted autofill without submit: Task 3.
- Normal login prefill and WebView integration: Task 4.
- Logout preservation and explicit credential removal: Task 2.
- Storage failures and manual fallback: Tasks 1 and 4.
- Retry limits, stale session writes, and success checks: Tasks 2 and 3.

## Commit Boundary

Do not stage unrelated files. Follow `AGENTS.md`: before any commit, show the exact changed-file list and diff summary and wait for the user's explicit commit instruction.
