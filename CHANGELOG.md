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

### Changed

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
