# MAP visual system

<!-- impeccable:design-contract 1 -->

## Direction

MAP is a personal field instrument: a composed Android tool for checking the
next useful action and moving through local work. It should feel precise,
quietly premium, and dependable in a morning kitchen, a bright commute, or a
late-night room.

## Visual grammar

- Graphite ink and linen surfaces create the base; lapis/cobalt marks only
  action, selection, and active state.
- Use a compact Material hierarchy: top app context, one primary action,
  clear section bands, and familiar Android navigation.
- Prefer strong alignment, 12-16dp surfaces, 8dp rhythm, and restrained
  dividers over card grids, decorative gradients, or ornamental motion.
- Type is one system sans with a measured role scale: display for screen
  identity, headline for protected focus, title for sections, body for
  content, label for actions, and metadata for supporting detail.
- Light and dark themes are intentionally authored for variable real-world
  light; dark is graphite, not an inverted light theme.

## Interaction contract

Every action has a visible pressed/focused/disabled state, every async path
has loading and failure feedback, and every destination keeps system Back and
48dp touch targets. Home leads with the current moment; secondary tools stay
available without competing with it. The MAP coordinate mark belongs to the
primary action, and its 180ms settle motion is disabled when Android animations
are disabled.
