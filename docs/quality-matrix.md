# MAP quality matrix

Last checked: 2026-09-30 on Pixel_10_Pro API 37 / Android 17 emulator `emulator-5554`, with connected tests isolated on disposable `MAP_Disposable`.

## Previous Lavender workspace verification (issue #203)

- Native screenshot review covered Home, Tasks, Calendar, Tools, searchable Documents, Scan and PDF viewing; light/dark Home, expanded Home, and large-text Home/Calendar were also captured. The approved comp uses illustrative records; the app only shows local records.
- Impeccable finish review: **ship**. Searchable document access, Home task priority, PDF header insets and search-hint contrast were resolved. Fresh generic agents substituted for the unavailable named finish-reviewer/documenter roles.
- Native task date/time OK actions use explicit theme-aware text colors (6.00:1 light, 8.06:1 dark). Cancel shares the same color selector. Search hint contrast is 5.86:1 in the light screenshot.
- The first connected run completed 42 checks with three failures: an equalizer preset interaction, an old unavailable-document text selector, and a floating task action covering a task title at 2x text. Equalizer passed isolated without a product change; the document check now uses the preserved accessibility label. Large text uses an inline Add task action, preventing the overlap.
- Final focused TaskActions, DocumentViewerZoom and large-text Home gate passed **11/11**, including actual 450% PDF magnification, pinch zoom, local document search/type filters, retained unavailable records, rebuilt PDF header insets, task detail actions, and completion/Undo/delete. `test lint assembleDebug` passed with that gate.
- Final complete connected gate passed **42/42** on `MAP_Disposable` API 37 / Android 17 (6m59s). GitHub Android quality gate passed for the implementation commit; final documentation read-back is verified before merge.
- Phone acceptance of this redesigned debug build remains pending; no signed release was produced.

## Earlier verified baseline

| Area | Result | Evidence |
| --- | --- | --- |
| Build, lint, unit tests | Pass | `rtk ./gradlew --no-daemon test lint assembleDebug compileDebugAndroidTestKotlin` |
| Connected UI regressions | Pass, 40 tests | `rtk env ANDROID_SERIAL=emulator-5556 ./gradlew --no-daemon connectedDebugAndroidTest` passed 40/40 on the disposable API 37 / Android 17 `MAP_Disposable` AVD (6m15s). Focused large-text empty-focus, scan export/viewer, and large-text task-detail checks each passed 1/1. Coverage includes selected-week labeling, timed recurring task save/reminder scheduling, active-playback equalizer preset application, sleep-timer set/clear, and scan session → PDF registration → viewer → fresh empty session. |
| Material 3 expressive controls and navigation | Pass | Home and Calendar inspected on Pixel_10_Pro in light and dark themes. After the final connected suite, installed the current debug APK with `adb install -r` (preserving Pixel app data), launched MainActivity, and visually confirmed the light Home empty state, live clock, analog dial, and navigation bar on `emulator-5554`. The adaptive bottom bar shows its selected indicator; all seven Calendar date targets fit at phone width; the selected day is visibly tinted and exposed as `selected=true` in the UI hierarchy. Navigation rail, RTL, and large-text layouts passed the connected regressions. |
| Notification-denial recovery | Pass | `NotificationPermissionRecoveryTest` passed after the test was wired through `MainActivity.onRequestPermissionsResult` |
| Primary destinations and empty states | Pass | Freshly installed the debug APK and inspected Home, Calendar agenda/week, Tasks, Tools, Scan, and Music first use. Launched the scanner and used Back; Scan returned with no pages and `Export PDF (0)`. Created a temporary task, opened details, completed it, used Undo, deleted it, and verified the task was gone; opened and canceled both document and music-folder pickers without selecting files. |
| Home in light/dark and portrait/landscape | Pass | Inspected all four theme/orientation combinations. Final short-height capture `/tmp/final-home-landscape.png` shows the clock, truthful empty focus status, full Add task button, and all four labeled destinations within the 2856×1280 landscape viewport without scrolling. Changed Android's 12/24-hour preference and confirmed the digital clock switches from `4:55 AM` to `04:55`, then removed the override; restored the AVD to light portrait afterward. |
| Task and pinned-focus persistence after process death | Pass | Created temporary records, force-stopped MAP, relaunched, confirmed both, then removed the records |
| Task composer with keyboard open | Pass | Save action remained reachable above the IME |
| True split-screen / multi-window | Pass | Opened MAP and Settings through Android Overview split-screen; `dumpsys` confirmed MAP `MainActivity` in `mode=multi-window` at `[0,0][1280,1413]`. Switched MAP to Calendar and opened the task composer with the keyboard; Cancel and Save remained visible. |
| TalkBack focus, labels, and state | Partial pass | With TalkBack enabled, focus traversed Home → Add task → title → Add details → Cancel → Save; Calendar exposed full date labels and `selected=true`; Tasks and Tools navigation/actions were reachable. TTS connected and Android logged playback events, but the headless emulator audio did not reach the host recorder, so spoken wording remains unverified. |
| RTL, large text, touch targets | Pass | `RightToLeftAccessibilityTest`; date targets checked against 48dp minimum at 2x font scale |
| Narrow and expanded layouts | Pass for tested layouts | `CalendarComposerNavigationTest`, `RightToLeftAccessibilityTest`, and `TaskActionsUiTest`; the live split-screen session also confirmed MAP navigation and task composition in the reduced-height pane |
| Rotation and draft retention | Pass | `CalendarComposerNavigationTest.composerRestoresDraftAndCalendarDateAfterRotation` |
| Reduced motion | Pass | Document search and task-composer instrumentation tests with animations disabled |
| Document recovery and zoom | Pass | Missing-PDF recovery, 450% button zoom, and a real two-finger `UiObject2.pinchOpen` gesture in `DocumentViewerZoomTest` |
| Scan export and missing-page recovery | Pass | Synthetic multi-page export and unavailable-page tests in `ScanPdfExportTest` and `ScanExportViewerIntegrationTest` |
| Live scanner capture and cancellation | Pass on virtual camera | On a fresh install, the emulator camera detected and auto-captured a frame, reached page correction, and the confirmed discard returned to Scan with `No pages captured yet` and `Export PDF (0)`. Physical-camera behavior remains for phone smoke. |
| Music playback and audio focus | Pass | `MusicTransitionTest` playback, reopen, and focus-loss cases |
| Music local-file UI | Pass with generated test audio | Granted a SAF folder, refreshed two WAV files, opened the correct track in Now Playing, checked play/pause/resume, queue contents, playlist create/rename/delete, and favorite filtering; applied a preset during playback, verified saved bands, and set/cleared a one-minute sleep timer. On a fresh install, Add folder opened Android's folder picker and cancel returned without granting access. Test files and MAP app data were cleared afterward. Physical audio output and lock-screen behavior remain for phone smoke. |
| Changelog and patch hygiene | Pass | `./scripts/check-changelog.sh` and `git diff --check` |

## Still needed for acceptance

- Confirm spoken TalkBack labels and state announcements across primary screens; the emulator confirmed focus and accessibility-node state, not the spoken output.
- Physical-phone smoke for camera/scanning, PDF export, notifications, local data, and music while locked.

Issue #171 remains open until these checks pass or their exceptions are explicitly accepted. No release build or signing was performed.
