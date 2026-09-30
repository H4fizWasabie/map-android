# MAP visual direction

Seed key `d5242459`, assigned candidate 4. No re-roll.

## Seven grounded directions

1. Transit board: visible time windows and one next departure.
2. Index archive: movable slips for actions, appointments, and files.
3. Wayfinder: route lines connecting today’s tasks and appointments.
4. Field instrument: precise, tactile readouts that make a private daily workspace feel dependable. **Selected.**
5. Tailor’s measuring table: align commitments and available time.
6. Darkroom contact sheet: scan, document, and media items as one local archive.
7. Printer’s ledger: clear ruled groups for open, overdue, and completed work.

## Assignment and challenge evaluation

The seed assigned candidate 4, the Field instrument. Audience identification: high; it reads as a capable personal tool used during quick phone check-ins. Product clarity: high; its readouts and ruled hierarchy map directly to local tasks, appointments, and tool state. The chosen comp is `.impeccable/mocks/home-01-clock-next-list.png`.

Seed challengers were compared on the same axes. Split-flap concourse: high clarity, medium identification; its station identity overstates schedule. Cathode numeral stack: medium clarity, low identification; the product has no device-control model. Civic prospectus ribbon: low clarity and identification; it implies a public service. Factory-records catalog / pulsar plot: medium on both; useful calibration lines are fused into the chosen field grammar, but its catalog topology obscures the immediate task. Origami sequence: low clarity; task work is not a step-by-step craft. Alphabet storm: low clarity; expressive letterforms compete with task scanning. Candidate 4 wins on both axes; none displaced it for aesthetics alone.

## Direction contract

THESIS: MAP is a private daily instrument for seeing and acting on what is next; it avoids the generic dashboard of equal cards.

OWN-WORLD: mineral-white and blue enamel, cobalt ink, one persimmon action, a chartreuse state point, precision rules, and Archivo/Work Sans.

STORY: within seconds the user sees the current moment, the next commitment, and a direct path to complete or add a task.

FIRST VIEWPORT: MAP mark and current date share the top line; a static analog dial sits beside the live local digital clock; the existing Now & next area and real task rows follow; Add task remains obvious; native Home, Calendar, Tasks, Tools navigation stays at the bottom. Empty states do not invent appointments or tasks.

FORM: Operate, option 4 of seven grounded directions, seed `d5242459`.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, and DESIGN.md.

## Comp inventory

| Element | Treatment | Implementation |
| --- | --- | --- |
| MAP mark and date | identity marker and formatted current date share the top line | Android text, shared MAP identity mark, and date formatter |
| Current time | analog field dial beside a high-weight live, localized time readout | Native Canvas view refreshes on system clock changes; Android TextClock provides the accessible, user-configured 12/24-hour value |
| Next commitment | two-part rail with time and task | Existing task data and native views |
| Task rows | explicit check target, title, metadata, rule | Existing task row and completion flow |
| Add task | full-width persimmon action with add icon | Native Button and vector icon |
| Bottom navigation | four familiar destinations and selected state | Existing shared native navigation |
| Surface and dividers | mineral enamel, flat tonal fill, thin rules | Existing XML colors and shape drawables |
