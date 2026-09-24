# EAS Credential Storage and Automatic Session Recovery Design

Date: 2026-09-24
Status: Draft for review

## Goal

Port the iOS academic-login credential reuse behavior to Android so students do not have to re-enter their account credentials after they have been saved, and expired sessions can reuse those credentials. Shenzhen should continue to relogin through its existing protocol flow. Benbu and Weihai should use the existing WebView login flow, with saved credentials filled into the login form; the user remains responsible for submitting the form and completing CAPTCHA or additional verification.

## Current behavior

- Android stores the EAS token, username, and password together in `EasPreferenceSource`, which uses `EncryptedSharedPreferences` and an Android Keystore-backed master key.
- The login sheet pre-fills username and password only for Shenzhen. Benbu and Weihai launch `WebViewLoginActivity` directly.
- Shenzhen session recovery can relogin using credentials on the token. Benbu and Weihai return control to `EASActivity.handleSessionExpired`, which opens the WebView login flow.
- `EASRepository.logout()` clears the complete token preference, including the password.
- iOS keeps credentials in a dedicated Keychain store. Its WebView autofill script fills allowlisted login forms, does not submit them, and invalidates the injected password state after use.

## Design

### Credential storage boundary

Add a campus-scoped `EasCredentialStore` abstraction, backed by encrypted preferences using a key protected by Android Keystore. It owns credentials independently of transient EAS session data. Keep credentials keyed by campus and normalized account identity so a login for one campus or account cannot overwrite another account silently.

On first use, migrate complete Shenzhen username/password pairs from the existing encrypted EAS token preference into the new store. Do not treat non-Shenzhen `EASToken.password` values as login passwords: the current WebView flow can populate that field with a placeholder, and its login metadata may carry an unrelated electronic-lab token. Preserve `electronicExpToken` behavior. Preserve the existing plaintext-preference migration behavior, but copy only verified credential fields into the credential store. After a successful migration, session serialization must no longer be the source of truth for the Shenzhen password. Do not log credentials or include them in diagnostic payloads.

Successful interactive login saves the supplied credentials only after the campus login succeeds. Shenzhen saves through the existing repository login-success path. For Benbu and Weihai, capture the actual username/password submitted on the allowlisted authentication form, retain that candidate only in memory, and commit it only after the existing success detection confirms login. The cookie-login repository parameters are not the source of those credentials. Failed or cancelled login discards the candidate.

### Login and session recovery

- **Shenzhen:** read the campus/account credentials from `EasCredentialStore` when the existing `tryRelogin` path detects an expired session. Keep the existing retry limit and stale-session generation checks. If credentials are missing or rejected, open the normal login sheet with saved fields prefilled when available.
- **Benbu and Weihai:** when session recovery opens `WebViewLoginActivity`, provide the selected campus and account identity. On each main-frame navigation, autofill only when the URL matches a campus-specific allowlist and a recognized username/password form is present. Use the same value-setting and event-dispatch behavior as the iOS implementation. Never submit the form automatically. A missing credential, CAPTCHA, MFA prompt, or unmatched login page leaves the existing interactive WebView behavior available.
- **Normal login:** prefill the Shenzhen native fields from the store. For Benbu and Weihai, the WebView may fill saved credentials after the user reaches an allowlisted login form. First-time login remains interactive; successful completion stores the credentials for the next attempt.

### Logout and credential removal

Separate session cleanup from credential removal. Normal logout and session expiration clear tokens, cookies, and cached identity but retain saved credentials so the next login can reuse them. Provide a distinct explicit “forget saved credentials/account” action that removes credentials for the selected campus/account. If the existing UI has no account-removal action, expose the store operation without adding an unrelated settings surface in this change; product placement can be decided during implementation review.

### Failure and security behavior

A storage read or migration failure must not block manual login. Autofill is restricted to exact approved scheme/host/path rules for each campus; credentials are never injected into arbitrary pages or subframes. The injected JavaScript state is short-lived and cleared after fill, cancellation, navigation outside the allowlist, or WebView destruction. The feature does not bypass CAPTCHA, MFA, or user submission.

If automatic relogin fails, preserve the existing retry cap and return the user to the interactive login path. Never report a successful session until the existing campus-specific login checks pass.

## Alternatives considered

1. **Keep credentials only inside `EASToken`:** smallest code change, but logout currently erases them and web-campus login does not have a reusable credential source. Rejected because it does not meet the retention and cross-campus goals.
2. **Rely only on Android Autofill / Google Password Manager:** avoids app-managed storage, but the app cannot silently read those credentials for background relogin, and save/restore behavior depends on the user's Autofill service. Rejected as the sole mechanism.
3. **Use an app-owned Android Keystore-backed credential store and allowlisted WebView autofill:** matches the iOS Keychain boundary, supports both protocol relogin and WebView prefill, and keeps form submission and additional verification with the user. Recommended.

## Scope boundaries

- No automatic WebView form submission.
- No CAPTCHA or MFA bypass.
- No change to campus authentication protocols or token formats beyond removing password ownership from transient session state after migration.
- No redesign of the login UI beyond credential prefill and the minimum integration needed to save successful WebView credentials.

## Validation criteria

- Existing Shenzhen login still succeeds; stored credentials survive logout and can be used for a later session recovery.
- Benbu and Weihai credentials are saved only after a successful WebView login, reused to fill only their approved login forms, and are not submitted automatically.
- Missing, invalid, or unavailable credentials fall back to the current interactive flow.
- Migration from existing encrypted token preferences preserves campus/session state while making the credential store the only password source of truth.
- Explicit credential removal deletes only the selected campus/account credentials.
- Existing retry, stale-session, logout, and cancellation behavior remains correct.
