# MAP visual system

<!-- impeccable:design-contract 1 -->

## Direction

MAP is a signal, not a decoration: a composed Android tool that tells the
user what's next with the same unambiguous confidence as station signage.
The interface leans on scale, weight, and a strict grid rather than
metaphor or iconography. One signal color means one thing everywhere —
action and current state — and it stays constant across light and dark;
only the surrounding room (paper by day, ink by night) changes.

## Visual grammar

- Day surfaces are soft neutral white (`map_background`, `map_card`) with
  near-black ink text; night surfaces are ink-black with off-white text.
  Cobalt blue (`map_accent`) is the only accent, used identically in both
  themes for buttons, links, selection, and emphasis.
- Hairline rules are the sole structural device: full-width 1dp dividers
  separate the header, Focus, and each task group. There are no cards, no
  dot markers, and no route lines — hierarchy comes from type weight and
  the grid, not containers.
- Two typefaces carry opposite jobs: Archivo (variable weight/width) is
  set at high weight for display, headline, section, and label text and
  every numeral readout (clock, group counts); Work Sans carries body and
  metadata text at normal weight so content stays quiet and readable.
- Sentence case throughout, including section titles — the confident
  register comes from weight and scale, not capitalization.
- Corners are near-flat (2dp) on buttons and surfaces; there is no
  decorative radius, gradient, or shadow.
- Light and dark themes are intentionally authored for variable real-world
  light; dark is an ink room, not an inverted light theme.

## Interaction contract

Every action has a visible pressed/focused/disabled state, every async path
has loading and failure feedback, and every destination keeps system Back and
48dp touch targets. Home leads with the current moment — a bold clock readout
after the Today/date hairline — and the Focus group before the day's task
groups. Buttons carry no compound iconography; identity comes from type and
the signal color alone.
