# MAP decisions

- Android-native Kotlin application; minimum API 26, compile/target API 36.
- Application ID: `app.map.android`.
- Home/Today is the primary surface; navigation is Home, Music, Scan, Tasks.
- MVP visual language is a quiet utility board with system themes, restrained motion, explicit states, and accessibility-first controls.
- MVP has no account, backend, network service, analytics, crash reporting, or cloud sync.
- Room/SQLite owns local metadata and operational state; user files remain file-backed.
- Scanning is a recoverable multi-page camera/import workflow that exports readable PDFs; OCR is deferred.
- Music uses user-selected local folders, persistent queue/state, and background controls.
- Tasks support Inbox, Overdue, Today, Upcoming, recurrence, completion history, reminders, and bounded snooze.
