# MAP visual system

<!-- impeccable:design-contract 1 -->

## Direction

MAP is a private daily field instrument: a composed, dependable place to see
the current moment and act on what comes next. Its surfaces feel like precise
blue enamel and mineral paper, with native Android controls and no invented
data or device-control metaphors.

## Visual grammar

- Light surfaces use mineral white (`#F1F5F8`) and blue enamel (`#F8FAFC`) with
  deep cobalt ink (`#102B4C`). Dark surfaces use a midnight-blue field
  (`#0D1929`) and lighter enamel (`#17273A`) with cool-white ink (`#F3F6FA`).
- Persimmon (`#C44C2D` light, `#FF7954` dark) marks primary actions and active
  selections. The small chartreuse MAP dot (`#567700` light, `#C7E45B` dark)
  marks MAP's local-first identity. Hairline rules and tonal surfaces organize
  content; a single restrained outline may clarify an interactive surface.
- The Home instrument pairs a drawn analog dial with Android's live `TextClock`.
  Both read the device's local time. The dial has no animation; it refreshes on
  system time changes. The adjacent digital readout carries the exact,
  accessible time value and respects the user's 12/24-hour setting.
- Home gives that instrument a deep enamel-blue panel with a clear local-time
  label; the analog face and white digital readout form one intentional focal
  surface. Tasks shows a compact, data-derived count of open, due-today, and
  overdue work before its primary action. Empty task lists still read as empty.
- Calendar pairs its date strip with a selected-date readout so the current
  agenda has a visible anchor. Tools uses three icon-led local-workspace
  modules for Documents, Scan, and Music, each with a direct native action and
  current state. These bounded surfaces keep the workbench scan-friendly
  without turning the main content into a dense card grid.
- Archivo carries display, headline, section, label, and numeral roles at clear
  weight steps. Work Sans carries body and metadata text so content remains
  easy to scan. Sentence case keeps the voice direct.
- Buttons and bounded focus surfaces use 10–12dp corners. Focus can use a
  quiet tonal fill; other content is separated by spacing and rules instead
  of dense card grids. No gradients, decorative shadows, or visualized data
  without a real local source.
- Primary navigation uses a Material 3 expressive bottom bar on compact
  windows and a navigation rail on expanded windows. The selected destination
  uses the persimmon tonal indicator; the shared action buttons keep the same
  Field instrument palette and clear pressed, focused, and disabled states.
- Short landscape windows keep all four named destinations in a bottom bar and
  compress Home around the live clock, truthful focus status, and Add task so
  those primary actions remain visible without scrolling.
- Confirmation and Music action dialogs use Material 3 surfaces and text
  buttons so decisions keep the same palette and hierarchy as their screens.
- Light and dark themes are authored for changing room light, with their own
  foreground and disabled-state colors. Focus states, explicit errors, and
  48dp minimum targets remain visible in both.
- The Home instrument panel and tool icon tiles have paired light and dark
  colors. The clock dial remains static, and no task or tool status is
  fabricated when local data is empty.

## Interaction contract

Home starts with the MAP mark, date, current-time instrument, and the existing
Now & next focus surface. The clock always reflects system time; tasks,
appointments, and document details only appear when they exist in local data.
Adding, completing, pinning, scheduling, opening, scanning, and playing remain
real native actions. MAP retains Room/SQLite metadata and reads user files in
place; it does not fabricate examples, copy a source document silently, or
require network state.

Every action has a visible pressed, focused, and disabled state. Async work
shows progress and recovery feedback. System Back, accessible labels, touch
targets, system light/dark modes, larger text, and reduced motion are preserved
across destinations.
