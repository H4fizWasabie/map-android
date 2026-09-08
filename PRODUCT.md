# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

MAP is for one person managing a small set of daily actions, appointments, documents, scans, and local music from an Android phone. They use it one-handed in changing light and want a calm tool that gets out of the way.

## Product Purpose

MAP is a private personal utility app with Home/Today as its entry point. It gives the user one local place to see what matters now, schedule and complete tasks, browse a calendar, open documents, scan pages, and play local music. Success means the user can understand the next useful action within seconds and trust that their data stays on the device.

## Positioning

MAP is a personal operating surface, not a social productivity platform: it connects everyday actions and tools around the user's current moment without requiring an account, network, or cloud service.

## Operating Context

The app is used on a phone during short check-ins and focused sessions. Home, Calendar, Tasks, and Tools are the primary destinations. Documents and scans remain file-backed; music references user-selected local files; Room/SQLite stores metadata and operational state.

## Capabilities and Constraints

- Native Kotlin Android app, minimum API 26, target/compile API 36.
- Local-first and offline: no backend, account, analytics, cloud sync, or network dependency.
- Tasks support inbox, scheduling, recurrence, completion history, reminders, and bounded snooze.
- Calendar supports a Today agenda, date strip, timed/all-day tasks, and secondary week view.
- Tools contains Documents, Scan, and Music with contextual state.
- Documents are read-only views of user-selected PDFs/images with unavailable-file recovery.
- Scanning is recoverable, multi-page, local, and exports readable PDFs.
- Music indexes selected folders without duplicating files and supports library, queue, playlists, playback state, notification controls, and unavailable-file retention.
- Existing data, files, metadata, accessibility, light/dark themes, and reduced-motion behavior must remain safe through visual changes.

## Brand Commitments

The name MAP means My Awesome App. The product should feel private, capable, composed, and human; never gamified, noisy, or like a generic dashboard.

## Evidence on Hand

The current repository contains the working Android implementation, local databases, emulator data, and the confirmed feature/context documents. No external customer claims, testimonials, or commercial performance evidence should be invented.

## Product Principles

- Make the next useful action obvious.
- Keep private data local and recoverable.
- Show state honestly, including unavailable and empty states.
- Prefer familiar Android behavior with a distinct MAP point of view.
- Spend visual energy on hierarchy and feedback, not decoration.

## Accessibility & Inclusion

Every interactive target is at least 48dp, content remains usable with larger system text, contrast is accessible in both themes, errors explain recovery, and reduced-motion settings are respected.
