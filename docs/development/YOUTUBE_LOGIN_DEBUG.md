# Troubleshoot YouTube web login

Open **Yuma Debug** (`re.muwmix.yumaplayer.debug`) and use Account → YouTube → Web browser. Debug has separate app data and WebView cookies from Release. Update an existing Debug installation with `adb install -r`; complete Google sign-in only if prompted.

## Keep sign-in inside the WebView

The login WebView uses its original mobile user agent and viewport. Google sign-in returns to `https://www.youtube.com` rather than Music's official-app promotion flow. Custom recovery start URLs remain supported, and cookie collection covers both ordinary YouTube and Music.

- HTTP(S) pages remain in the WebView, except YouTube Music's Play Store details page (`play.google.com/store/apps/details?id=com.google.android.apps.youtube.music`). That destination is blocked even when wrapped in an `intent://` or reported as a user gesture.
- Automatic non-web redirects and subframe app launches are consumed. Deliberate top-level external links use a sanitized browsable `ACTION_VIEW` intent; unavailable handlers show an error.
- Context extraction checks the actual YouTube hostname. A Google `continue` query containing `youtube.com` does not qualify.
- The login route is excluded from both navigation Haze capture layers. Other routes keep their effects; WebView hardware acceleration remains enabled.

A desktop-user-agent trial was withdrawn after a blank/flickering login surface was reported. An account-free local test page rendered normally. With the mobile user agent and login-only Haze exclusion, the user confirmed that the Google page rendered normally, then confirmed the account avatar after login on 2026-10-30.

## Complete login with a normalized DataSyncId

Google authenticates the session through cookies. DataSyncId supplies YouTube user/channel context for `onBehalfOfUser`, delegated account headers and authenticated playback/session selection. The repository requires a usable ID before saving a completed login.

The network response parser accepts canonical `responseContext.mainAppWebResponseContext.datasyncId` and the older `dataSyncId` alias. The page extractor also accepts both spellings and `DATASYNC_ID`.

Bridge inputs and cookie callbacks are serialized on the ViewModel's Main dispatcher. If a missing-ID lookup finishes after a different usable page candidate arrives, completion retries with that candidate. A later ID callback after a missing-ID error retains its retry path. Duplicate-cookie suppression and cancellation remain intact.

Visitor-only cookies do not start completion. With an authentication cookie present, failures show either the specific missing-ID message or the ordinary login-failed message. Account verification uses the complete normalized request-local snapshot, including DataSyncId; its headers and body cannot pick up different contexts from a late callback. Existing no-argument account-info callers remain supported.

### Device observations that determined the repair

- On 2026-10-29, a page ID arrived while completion was Loading. The original request still used its null candidate and failed without retrying. After the parser repair, the native lookup changed from `network_id=false` to `network_id=true`.
- Subsequent account verification returned HTTP 400. Clearing the ID alone did not fix it. A same-session cookie-only comparison returned 400 with `gl=CN` and 200 with `gl=US`, including an active account header.
- On 2026-10-30, a temporary instrumentation runner compared the installed app's native `InnerTube.accountMenu(WEB_REMIX, snapshot)` requests. Both used `gl=US`, `hl=en` and the same saved normalized cookie/visitor snapshot:

| DataSyncId | HTTP | Parsed account | Matches saved name/email |
| --- | --- | --- | --- |
| Omitted | 200 | Yes | Yes |
| Normalized ID retained | 200 | Yes | Yes |

The responses had equal name/email. Neither returned a channel handle; alternate/brand-channel selection was not compared. The saved normalized ID contained no `||` separator. Clearing the ID is therefore unnecessary for this tested login context, and verification retains it.

An earlier browser HTTP 500 used a raw page ID and a different WEB request, not the native normalized WEB_REMIX request above. Authentication values and account identities stayed on the phone during the native comparison. The temporary runner configuration/source and test package were removed afterward without clearing the Debug app's data.

## Choose the native YouTube request region

Settings → Content → **YouTube region** reuses the existing region preference and country-name mapping. Native requests default to US; account verification uses the selected region rather than a separate fixed-US override. The existing `next` request retains its fixed US/English context. Google sign-in pages are separate from this setting, and content-language behavior is unchanged.

The selector omits system-default and CN choices. Stored values are read directly, without migration or legacy-value normalization. If an existing installation stores CN or system-default, select US manually. An out-of-list stored value is shown literally so the selector remains usable; it is not silently converted.

## Read failure metadata without exporting account data

```powershell
adb logcat -v threadtime YouTubeLogin:D '*:S'
```

Debug-only diagnostics contain fixed navigation labels, failure stages, HTTP/error codes and exception type names. Do not collect unfiltered logcat, login screenshots or exported WebView storage. URLs, cookies, tokens, IDs, account details, response bodies and exception messages are excluded from this tag.

| Trace | Meaning |
| --- | --- |
| `store_navigation_blocked target=youtube-music` | The Music install page was consumed. |
| `external_navigation_blocked` | An automatic app/store redirect was consumed. |
| `external_navigation_unavailable reason=…` | A deliberate link could not open; the UI displays an error. |
| `web_error code=…` / `web_http_error status=…` | The main-frame WebView load failed. |
| `failure stage=cookie_validation` | A direct repository call supplied no usable authentication cookie. |
| `failure stage=datasync_lookup` | Neither the network nor the page candidate supplied a usable ID. |
| `failure stage=account_verify` | Account-menu verification failed after ID acquisition. |
| `failure stage=persist_session` | Account verification succeeded, but saving the session failed. |

The repository emits one failure record per failed attempt, with an HTTP status from its cause chain when available. Rendering/lifecycle traces, probe-source JavaScript diagnostics and duplicate ViewModel failure logs are removed.

## Run the relevant checks

```powershell
.\gradlew.bat :app:testGmsMobileArm64DebugUnitTest `
  --tests moe.rukamori.archivetune.ui.screens.YouTubeLoginNavigationTest `
  --tests moe.rukamori.archivetune.auth.YouTubeLoginRepositoryTest `
  --tests moe.rukamori.archivetune.viewmodels.LoginViewModelTest
.\gradlew.bat :core:innertube:test `
  --tests moe.rukamori.archivetune.innertube.AccountDataSyncIdParserTest
.\gradlew.bat :app:assembleGmsMobileArm64Debug
```

The APK is `app/build/outputs/apk/gmsMobileArm64/debug/app-gms-mobile-arm64-debug.apk`. Physical acceptance requires a usable, non-flickering sign-in page, no automatic store launch, and successful return with the account avatar. Exercise the region selector and failure feedback on the phone separately; unit tests cannot establish their presentation.
