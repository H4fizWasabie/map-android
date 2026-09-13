# MAP visual system

<!-- impeccable:design-contract 1 -->

## Direction

MAP is a tactile personal workspace: warm, capable, and human without
becoming a playful planner. Atelier gives the private utility a composed
physical world of paper, deep teal, saffron, and vermilion while keeping the
next useful action unambiguous.

## Visual grammar

- Day surfaces use warm paper (`map_background`, `map_card`) with deep teal
  ink. Saffron marks focus and calm local state; vermilion marks the primary
  action and current selection. Night keeps the same hue roles in a deep teal
  room rather than inverting the light theme.
- Hairline rules still structure the header and task groups. Surfaces may use
  restrained 10dp corners and tonal separation where a physical tool needs a
  clear boundary; avoid dense card grids and decorative shadow.
- Two typefaces carry opposite jobs: Archivo (variable weight/width) is
  set at high weight for display, headline, section, and label text and
  every numeral readout (clock, group counts); Work Sans carries body and
  metadata text at normal weight so content stays quiet and readable.
- Sentence case throughout, including section titles — the confident
  register comes from weight and scale, not capitalization.
- Buttons and surfaces use 10dp corners, with one larger 16dp focus surface
  where the state needs emphasis. No gradients; elevation comes from color
  and a single restrained boundary.
- Light and dark themes are intentionally authored for variable real-world
  light; dark is an ink room, not an inverted light theme.

## Interaction contract

Every action has a visible pressed/focused/disabled state, every async path
has loading and failure feedback, and every destination keeps system Back and
48dp touch targets. Home leads with the current moment — a bold clock readout
after the Today/date hairline — and the Focus group before the day's task
groups. Buttons use familiar Android controls; Atelier's identity comes from
the color roles, warm surfaces, and carefully weighted type.
