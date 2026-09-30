# MAP domain context

## Product

MAP means My Awesome App. It is a personal Android utility app with a Home/Today entry screen.

## Terms

- **Task** — a local action record with a title and optional notes, schedule, recurrence, and tags.
- **Task composer** — the focused task-creation interaction: title first, with scheduling and additional details revealed progressively.
- **Today** — the Home view of Inbox, Overdue, Today, and Upcoming task groups plus recent app activity.
- **Calendar** — the scheduled time view of tasks, opening to a calm Today agenda on phones with a compact horizontal date strip, an optional week view, and a lightweight time grid. Date-only tasks appear as all-day items.
- **Tools** — the fourth primary destination for Personal tools: Documents, Scan, and Music. It is a calm list whose rows expose useful current state, such as recent documents, an unfinished scan, or the current track.
- **Scan session** — a recoverable set of captured or imported document pages before PDF export.
- **Document** — metadata for a user file such as a scan PDF or image; file bytes remain file-backed.
- **Document viewer** — the read-only local reading surface for user-selected PDFs and images, including files exported by scanning apps; it views source files directly and does not silently copy or modify them.
- **Audio item** — metadata for a user-selected local audio file; MAP does not own a duplicate copy.
- **Music library** — MAP's local index of audio items from user-selected folders, organized by songs, albums, artists, genres, folders, playlists, favorites, and listening history.
- **Queue** — the persistent, editable playback order; items can be reordered, removed, played next, cleared, or saved as a playlist.
- **Playlist** — a user-owned named ordering of audio items; it references source files and does not duplicate them.
- **Now Playing** — the full player surface for artwork, track identity, seeking, playback controls, shuffle, repeat, favorite, and queue access.
- **Mini-player** — the compact playback surface shown across MAP while audio is active.
- **Equalizer** — device-adaptive sound controls exposing the available bands, presets, and supported effects without pretending unsupported controls exist.
- **Sleep timer** — a playback stop rule set by custom duration, end of track, or end of queue.
- **Unavailable** — a retained record whose referenced file cannot currently be opened.

## Ownership

Room/SQLite owns operational state and metadata. Files remain local files. There is no account, cloud state, or server authority.

## Experience

MAP follows the **Field instrument** direction: precise, tactile readouts make a private daily workspace feel dependable. Home opens to the current date and time, shown by a static analog dial beside a live, localized digital clock. The clock uses the device's local time and has no animated hands. The Now & next focus area comes from existing task data, with optional user pinning; empty states remain empty and do not invent appointments. Calendar opens to a calm Today agenda on phones with a compact horizontal date strip, a lightweight time grid, and an optional week view. Home, Calendar, Tasks, and Tools are the primary navigation. Tools is a calm list of Documents, Scan, and Music rows, each showing useful current state rather than acting as a static menu. Scheduled tasks appear in both Tasks and Calendar; date-only tasks appear as all-day items; unscheduled tasks remain in Inbox. The task composer opens as a focused bottom sheet on the current screen. Calendar stays behind it on the selected date and week; the title comes first, and Add details reveals date, time, notes, recurrence, and tags. Completion uses an explicit control with an Undo snackbar rather than hidden swipe-only actions.

MAP includes a compatibility-first read-only document viewer for PDFs and images, especially files exported by scanning apps such as CamScanner. The viewer opens source files directly without silently copying or modifying them and shows an explicit recovery state when a file cannot be opened, including an option to open the source with another app. Documents live in Tools with a recent-documents list and an Open document action. Music is a full local-only sub-app within Tools. Its Now Playing surface stays calm and immersive: artwork, identity, and primary playback controls lead, while queue, equalizer, sleep timer, and metadata use focused secondary surfaces. An active track exposes a persistent mini-player across MAP.

The shared visual language uses mineral-white and blue-enamel light surfaces, midnight-blue dark surfaces, cobalt ink, a persimmon action color, and a chartreuse identity marker. Archivo carries display and numeral roles; Work Sans carries body and metadata. Rules, spacing, and quiet tonal surfaces organize content without a dense card grid. Accessible contrast, system light/dark themes, large text, visible focus and error states, 48dp targets, and reduced motion apply throughout. MAP remains a local-first utility, not a cluttered productivity game.
