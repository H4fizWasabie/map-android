---
name: MAP
description: A calm private lavender workspace.
colors:
  background: "#F5F3F8"
  text: "#201D28"
  muted: "#696271"
  accent: "#7651A6"
  accent-pressed: "#5B397F"
  selection: "#E9DDF5"
  on-accent: "#FFFFFF"
  divider: "#DED8E6"
  card: "#FFFFFF"
  focus: "#E9DDF5"
  nav-background: "#FFFFFF"
  disabled: "#E8E3ED"
  error: "#BA1A1A"
  on-error: "#FFFFFF"
  error-container: "#FFDAD6"
  on-error-container: "#410002"
  background-dark: "#19161F"
  text-dark: "#F5F0FA"
  muted-dark: "#C2B9CC"
  accent-dark: "#CFADF5"
  accent-pressed-dark: "#E3C9FF"
  selection-dark: "#443252"
  on-accent-dark: "#30203F"
  divider-dark: "#433A4D"
  card-dark: "#272230"
  focus-dark: "#3B2D49"
  nav-background-dark: "#211B29"
  disabled-dark: "#39313F"
  error-dark: "#FFB4AB"
  on-error-dark: "#690005"
  error-container-dark: "#93000A"
  on-error-container-dark: "#FFDAD6"
typography:
  display:
    fontFamily: "Archivo, sans-serif"
    fontSize: "30sp"
    fontWeight: 800
  headline:
    fontFamily: "Archivo, sans-serif"
    fontSize: "22sp"
    fontWeight: 800
  title:
    fontFamily: "Archivo, sans-serif"
    fontSize: "16sp"
    fontWeight: 700
  body:
    fontFamily: "Work Sans, sans-serif"
    fontSize: "16sp"
    fontWeight: 400
  label:
    fontFamily: "Archivo, sans-serif"
    fontSize: "14sp"
    fontWeight: 700
  metadata:
    fontFamily: "Work Sans, sans-serif"
    fontSize: "13sp"
    fontWeight: 500
  caption:
    fontFamily: "Work Sans, sans-serif"
    fontSize: "12sp"
    fontWeight: 400
rounded:
  control: "16dp"
  filter: "24dp"
spacing:
  tight: "4dp"
  small: "8dp"
  medium: "12dp"
  content: "16dp"
  screen: "24dp"
components:
  button-primary:
    backgroundColor: "{colors.accent}"
    textColor: "{colors.on-accent}"
    rounded: "{rounded.control}"
  button-secondary:
    backgroundColor: "{colors.card}"
    textColor: "{colors.text}"
    rounded: "{rounded.control}"
  input-search:
    backgroundColor: "{colors.card}"
    textColor: "{colors.text}"
    typography: "{typography.body}"
    rounded: "{rounded.control}"
    padding: "8dp 14dp"
  chip-selected:
    backgroundColor: "{colors.selection}"
    textColor: "{colors.text}"
    rounded: "{rounded.filter}"
  card-focus:
    backgroundColor: "{colors.focus}"
    textColor: "{colors.text}"
    rounded: "{rounded.control}"
  navigation:
    backgroundColor: "{colors.nav-background}"
---
# Design System: MAP

## Overview

**Creative North Star: "Lavender workspace"**

MAP is a calm, private workspace: warm pale surfaces, charcoal ink, lavender focus and purple actions. Strong Archivo headings make the next useful action easy to find; quieter Work Sans content gives local tasks and files room to breathe. Rounded tonal surfaces and restrained row rules carry the composition.

This records the built native Android system after the approved identity replacement. Bundled fonts, familiar Material controls and honest local state keep the visual identity useful in changing light and at larger system text sizes.

**Key Characteristics:**
- Warm lavender neutrals and a single purple action accent.
- Strong headings, readable content and quiet metadata.
- Flat tonal groups, rounded controls and open list rows.
- Native navigation, explicit recovery and adaptable text.

## Colors

The frontmatter contains the authoritative light colors and their `-dark` counterparts; use Android theme resources to select the pair.

### Primary
- **Workspace purple** (`accent`): primary actions, activated native controls and the small MAP identity dot. `accent-pressed` supplies the deeper light-theme and lighter dark-theme emphasis.
- **Lavender focus** (`focus`, `selection`): current work and active destinations. These roles share a light value but separate in dark mode; keep their meanings distinct.
- **Action ink** (`on-accent`): readable text on filled purple controls.

### Neutral
- **Warm paper** (`background`): the app canvas.
- **Charcoal ink** (`text`) and **quiet ink** (`muted`): content hierarchy, including readable search hints.
- **Clear surface** (`card`, `nav-background`): controls, tool groups and navigation.
- **Quiet rule** (`divider`): row separation and restrained control outlines.
- **Inactive surface** (`disabled`): unavailable controls, paired with readable muted text.

Error colors are semantic recovery roles (`error`, `on-error`, `error-container`, `on-error-container`), rather than extra brand accents. Android assigns them to native error treatments.

**The Meaningful Purple Rule.** Use purple for an action, active selection or current focus; avoid decorating every row with it.

## Typography

Archivo and Work Sans are bundled variable fonts. Archivo carries display, headline, section, label and numeral roles; Work Sans carries body, metadata and captions. Use their weights from the frontmatter rather than system display substitutes. Android font fallbacks are recovery behavior, not identity.

The type ramp deliberately has two readable content roles at 16sp: a strong section title and a regular body. Display is 30sp, headline 22sp, label 14sp, metadata 13sp and caption 12sp. Display tracking is Android `letterSpacing = -0.025` (em). Other roles keep native line metrics and tracking; no fixed line-height scale is established. Numerals use Archivo weight 900, with size chosen for the readout rather than a global extra-large token.

**The Content Before Compression Rule.** Preserve system text scaling and allow wrapping or stacked controls before shrinking text.

## Layout

Frontmatter lengths are native Android units: **dp** for geometry and **sp** for scalable type, not CSS pixels. The sidecar's CSS previews translate these at nominal density and font scale 1; they are illustrations, not Android implementation specifications.

Primary screens use a vertically scrolling content column with 24dp horizontal gutters, system-bar/cutout insets and persistent named navigation. Most internal spacing falls on the recorded 4/8/12/16/24dp steps. Lists use breathing room and 1dp rules rather than a card around every item.

Expanded windows use a navigation rail when width is at least 600dp and height at least 500dp. Short windows retain the bottom bar and compress Home around current time, focus and the task action. Short windows or system font scale at least 1.3 use an inline, full-width Add task control so the action cannot cover list content. Short windows retain a 48dp control; larger text in taller windows uses content-driven height with a 56dp minimum. Narrow windows and large text stack clock readouts, tool actions and filter controls as needed. Every interactive target remains at least 48dp; small decorative icons do not define the target size.

## Elevation & Depth

Depth comes mainly from contrasting canvas, clear surfaces and lavender focus fills. Primary navigation has zero elevation. The native floating Add task action may retain Material elevation and ripple feedback; do not turn its platform treatment into a custom shadow vocabulary. There are no authored gradients or decorative shadow tokens.

**The Tonal Grouping Rule.** Separate related content with tone, spacing and quiet rules before adding visual depth.

The shared primary-action entrance moves 8dp into place over 180ms with a settling curve, only when Android animators are enabled. It is a short confirmation of hierarchy, not an ongoing animation. The clock dial is static between system time updates.

## Shapes

Shared buttons, search fields, focus groups and tool containers have 16dp rounded corners. Filter and date controls use a fuller 24dp radius. Native selected-navigation indicators retain their Material silhouette. Small icon surfaces and back controls may keep their existing native shapes; they do not enlarge the general corner scale.

## Components

### Buttons

Primary controls use purple with action ink; secondary controls use a clear surface, content ink and a quiet 1dp outline. Shared buttons use 16dp corners, sentence case and a minimum 48dp target. Primary labels use Archivo at 16sp; secondary labels use Work Sans at 14sp. Preserve Material pressed/ripple, keyboard focus and disabled behavior rather than inventing web hover behavior on Android.

### Chips

Document filters use rounded secondary buttons, lavender selected fill and visible text. Calendar dates use transparent resting surfaces and filled purple selection with action ink. Selection remains exposed through native state and accessible date labels, not color alone. Filters stack when width or system text size demands it.

### Cards / Containers

Current focus uses a lavender surface; tool groups use clear surfaces. Both follow the 16dp corner language. Keep metadata, direct actions and empty states legible within the group. Task and document lists stay open, separated by spacing and quiet rules.

### Inputs / Fields

Document search is a clear 16dp rounded surface with body text, an explicitly readable muted hint, native text/cursor interaction, 14dp horizontal and 8dp vertical padding, and a minimum 48dp height. Native composer, date and time controls inherit the authored theme; preserve their platform interaction and recovery states.

### Navigation

Home, Calendar, Tasks and Tools always have visible labels. The active destination uses a lavender tonal indicator and Archivo bold; resting labels use Work Sans. Labels are 12sp. Compact and short windows use a Material bottom bar; expanded windows use a rail, which grows and may put icons beside labels for larger system text.

### Current moment

Home pairs a native digital time readout with a drawn outline dial on a charcoal focus surface. Both use device-local time; the accessible digital readout respects the user's 12/24-hour preference. This is a Home signature, not a requirement to place clock panels on other screens.

## Do's and Don'ts

### Do:
- **Do** use theme-paired foregrounds and surfaces in both light and dark modes.
- **Do** preserve 48dp targets, larger system text, native focus and reduced-motion behavior.
- **Do** show real local state, readable empty states and explicit recovery actions.
- **Do** use Archivo hierarchy, Work Sans content and rounded tonal grouping.

### Don't:
- **Don't** restore the superseded cobalt/persimmon Field instrument palette.
- **Don't** add decorative gradients, custom shadow stacks or a card around every list row.
- **Don't** invent example records or status to fill an empty screen.
- **Don't** copy Home-specific clock composition or label treatments into every feature.
