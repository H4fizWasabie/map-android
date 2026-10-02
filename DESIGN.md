---
name: MAP
description: Three locally selectable visual identities for a private, personal-first workspace.
defaultTheme: Colorist Daybook
themes:
  Colorist Daybook:
    background: "#F5F1E5"
    ink: "#17261F"
    muted: "#53685E"
    accent: "#123F36"
    selection: "#DCE8DA"
    highlight: "#F4C95D"
    display: "Playfair Display"
  Botanical Print:
    background: "#F7F5EB"
    ink: "#153B30"
    muted: "#526A60"
    accent: "#15533E"
    selection: "#E6EEDC"
    highlight: "#F0C94B"
    display: "Playfair Display"
  Woven Poster:
    background: "#F2E8D4"
    ink: "#182B49"
    muted: "#53617A"
    accent: "#B64A31"
    navigation: "#18335B"
    selection: "#F1D5C5"
    display: "Barlow Condensed"
body: "DM Sans"
metadata: "DM Mono"
---

# Design System: MAP

MAP ships three selectable visual identities. The current choice is saved in local app preferences and applied by the shared `MapActivity` theme entry point. Colorist Daybook is the first-install default. Each theme has light and dark resources; theme switching changes the whole Android surface, including dialogs and system bars, while preserving the app's real local data.

## The three identities

- **Colorist Daybook** uses forest green, celadon, warm paper, and marigold. Playfair Display leads the page, DM Sans carries readable body text, and DM Mono marks dates and small labels. Its calendula engraving is used as a contained Home illustration.
- **Botanical Print** pairs luminous paper and evergreen ink with a restrained marigold highlight. Playfair Display and DM Sans stay readable against botanical print artwork, which is concentrated in the Home daybook header.
- **Woven Poster** uses warm woven-paper cream, cobalt navigation and ink, and rust actions. Barlow Condensed supplies the poster voice; DM Sans remains the body face and DM Mono the compact metadata face. Its woven collage is contained in the Home header.

The exact palette pairs live in `app/src/main/res/values/colors.xml` and `values-night/colors.xml`. These are semantic roles: background, ink, muted ink, card, focus, action, selection, divider, navigation, instrument and tool icon. Keep foregrounds paired with their surfaces in both modes. Theme previews are bundled transparent illustrations, not screenshot fragments.

## Shared product structure

Theme identity changes color, type, artwork treatment and native component surfaces. Product labels, task state, document recovery, calendar data, and music behavior continue to come from MAP's existing local source of truth. Empty states stay honest; concept-only account/search/notification controls and demo appointments are not added. The generated concepts guide the hierarchy and visual vocabulary, while Android text and controls remain semantic, scalable, keyboard/touch accessible, and at least 48dp when interactive.

Home presents the local date and greeting, locale-aware live digital time and a native clock dial, actual focus/tasks, and a real empty state where there is no content. The botanical and woven compositions put their original artwork into that useful header region. Primary navigation remains Home, Calendar, Tasks, and Tools. Tools exposes all three appearance choices with title, short palette description, artwork preview, and selected state. The app uses native dialogs for platform pickers and theme-paired colors for those dialogs.

## Shape, motion, and accessibility

Use familiar native control shapes and theme-aware Material surfaces. Theme art stays non-interactive and is hidden from accessibility so it cannot displace meaningful content. Preserve system text scaling, locale clock format, reduced motion, native focus/ripple, and visible selected states. Let content wrap or stack before reducing text size. Do not add fabricated data or decorative shadow/gradient treatments to imitate raster mockup details.
