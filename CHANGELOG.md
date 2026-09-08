# Changelog

## [Unreleased]

### Added

- Created the native Android foundation and project operating rules.
- Added the first Home/Today layout with task groups and recent activity state.
- Added local task storage, task creation, completion, recurrence selection, and one-day snooze.
- Added recoverable scan sessions with image import, camera capture, and PDF export.
- Added local music-folder scanning, persistent queue state, and lock-screen playback service.
- Added the full local Music library with selected-folder refresh, embedded metadata fallback, search, sorting, filters, favorites, history, albums, artists, genres, folders, and playlists.
- Added calm Now Playing with artwork fallback, seek and transport controls, editable queue, mini-player, sleep timer modes, and device-adaptive equalizer controls.
- Added SAF-backed local playback through a foreground media service with audio focus, headphone-disconnect pause, notification controls, lock-screen metadata, and state restoration without autoplay.
- Added task due-date reminders with Android notifications and bounded one-day snooze.
- Added light and dark system theme resources for the utility board palette.
- Hardened task persistence against blank titles and stale double actions.
- Added a calm Calendar destination with date strip, agenda/week view, all-day and timed tasks, completion, and snooze.
- Added a list-based Tools destination for Documents, Scan, and Music.
- Added a direct read-only PDF/image viewer with persisted local document history and external-app recovery.
- Added a lightweight Calendar hour timeline for timed tasks.
- Added a focused bottom-sheet task composer and visible completion undo.
- Made the task composer title-first with an explicit Add details reveal.
- Added optional Home Focus pinning so Now/Next can stay anchored to a chosen task.
- Added nearby-page PDF rendering, bounded zoom controls, image pinch zoom, and local OCR-backed document search.
- Added broader local audio format discovery and URI-aware playback fallback for Android-supported files such as Opus.
- Added automatic document-bound cropping for clear scan edges and per-page PDF export selection.
- Added the full Google document-scanner capture flow for automatic corner correction, perspective cleanup, rotation, and multi-page review.

### Changed

- Kept foreground Music commands and notification startup from taking down the app when a local playback transition fails.
- Requested Music notification access when playback starts from Home or Scan so controls remain visible across entry points.
- Kept Now Playing secondary actions reachable on narrow screens and with larger system text.
- Kept Now Playing artwork within its content margins on narrow phones.
- Opened the task composer ready for immediate title entry with the keyboard visible.
- Kept long track names compact in the Home music mini-player while preserving artist context.
- Stacked Tools actions on narrow windows so large text keeps each tool readable.
- Moved visible PDF bitmap rendering off the UI thread with stale-result protection.
- Kept large Music library queries and transformations off the UI thread, applying only the newest result.
- Kept every expanded navigation destination reachable in short landscape windows.
- Kept document viewer zoom, OCR, and search actions readable with large text.
- Made recurring task completion atomic so a next occurrence cannot be lost mid-write.
- Made task rows open details from any non-checkbox tap across Home, Tasks, and Calendar.
- Used a lightweight Music row count instead of loading the full library twice when opening or rerendering Music.
- Refined Tools into a calm single-surface list with contextual states, compact actions, and readable large-text rows.
- Treated the running playback service, not stale persisted metadata, as the source of truth for Music state after a cold launch.
- Kept Home's Music play control synchronized with foreground playback state and TalkBack labels.
- Bounded embedded music artwork decoding so oversized local album art cannot exhaust MAP while rendering playback surfaces.
- Kept completion Undo visible when tasks are completed from the Tasks destination.
- Opened Music directly in Now Playing when requested instead of rendering the full library first.
- Applied system-bar insets across MAP screens so edge-to-edge status and gesture bars do not cover content.
- Adapted primary navigation to use a native rail on expanded Android windows while retaining the compact phone bar.
- Moved Scan PDF processing and writing off the UI thread with explicit export progress state.
- Ignored late document OCR callbacks after the viewer has been destroyed.
- Kept Home, Calendar, Tasks, and Tools in a predictable single navigation stack.
- Enabled Android predictive Back dispatch for the native MAP navigation contract.
- Routed Music player Back through Android's modern system callback while retaining older-device support.
- Virtualized Music result rows with a native list so large local libraries do not build every row at once.
- Disabled Android app backup and cleartext traffic for MAP's private local state.
- Removed dependency-added network permissions from the merged app manifest; local features now ship without app network access.
- Added an authored adaptive MAP launcher icon for the application and round launcher slot.
- Started the issue #14 premium visual foundation with authored graphite/linen themes, signal-accent actions, and icon-led primary navigation.
- Added visual anchors to Documents, Scan, and Music tools and aligned Music controls with the shared pressed-state treatment.
- Refined Scan and Document Viewer entry states with compact back navigation and clearer primary capture, export, and recovery actions.
- Added a clear separation between Scan capture and PDF export actions.
- Added the current date to Today and promoted Save/Add task actions to the primary visual treatment.
- Moved notification permission from app launch to the due-task reminder action that actually needs it.
- Replaced Music's generic gradient artwork fallback with authored MAP music geometry in the shared signal palette.
- Replaced the native MediaPlayer path with AndroidX Media3 ExoPlayer for more reliable local Opus playback and safe pause/seek during buffering.
- Made selected music-folder URI permissions persist using the provider-granted SAF flags; uninstalling still intentionally clears them with app data.
- Fixed PDF search navigation inside the nested viewer scroll containers so a match jumps to its page reliably.
- Recorded the Quiet Focus UI direction and timeline-first Home/Calendar relationship.
- Recorded Today-agenda Calendar defaults, all-day tasks, and automatic Now/Next behavior with optional pinning.
- Recorded progressive bottom-sheet task creation and explicit completion with Undo.
- Recorded the phone-first calm-agenda Calendar layout with date strip and lightweight time grid.
- Recorded Scan and Music as personal tools plus the compatibility-first document viewer requirement.
- Recorded the read-only direct-source viewer boundary for CamScanner files.
- Recorded Documents in Personal tools with recent files and external-app recovery.
- Recorded Tools as the fourth primary destination for Documents, Scan, and Music.
- Recorded the list-based Tools screen with contextual state for each personal tool.
- Expanded the confirmed Music contract to a full local library, player, adaptive equalizer, queue, playlists, filters, sleep timer, and app-wide mini-player.
- Recorded on-open/manual library refresh, local metadata fallback, and state restoration without automatic playback.
- Connected the primary navigation to Home, Calendar, Tasks, and Tools.
- Refined Home to foreground Now/Next and hide empty task sections; softened native button treatment across light and dark themes.
- Refined the primary surfaces into a Quiet Personal Workspace with shared navigation, calm task rows, focus treatment, and task details.
- Added clearer accent hierarchy and pressed/focused states to shared actions and primary navigation.
- Prevented Bluetooth/media-session reconnect commands from auto-resuming paused music.
- Kept device audio-effect initialization failures from crashing local playback.
- Kept unavailable selected music folders from crashing Music while refreshing.
