# MAP domain context

## Product

MAP means My Awesome App. It is a personal Android utility app with a Home/Today entry screen.

## Terms

- **Task** — a local action record with a title and optional notes, schedule, recurrence, and tags.
- **Today** — the Home view of Inbox, Overdue, Today, and Upcoming task groups plus recent app activity.
- **Scan session** — a recoverable set of captured or imported document pages before PDF export.
- **Document** — metadata for a user file such as a scan PDF or image; file bytes remain file-backed.
- **Audio item** — metadata for a user-selected local audio file; MAP does not own a duplicate copy.
- **Unavailable** — a retained record whose referenced file cannot currently be opened.

## Ownership

Room/SQLite owns operational state and metadata. Files remain local files. There is no account, cloud state, or server authority.
