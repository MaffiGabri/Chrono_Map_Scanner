## 2024-07-03 - Jetpack Compose Content Descriptions
**Learning:** In Jetpack Compose, setting `contentDescription = null` on interactive elements or standalone icons hides them from screen readers (like TalkBack), making the UI inaccessible for visually impaired users. Even if the visual context is obvious, relying solely on visual cues excludes users depending on assistive technologies.
**Action:** Always provide meaningful `contentDescription`s (e.g., via `stringResource`) for buttons, clickable elements, and standalone informational icons. Only set it to `null` if the icon is purely decorative *and* accompanied by a text label that already conveys the full meaning to screen readers.

## 2024-07-26 - Jetpack Compose Content Descriptions
**Learning:** Found several key images (like the Body Map) and standalone icons (like the App Logo) that had `contentDescription = null`. This makes the UI completely inaccessible to screen readers for these important elements.
**Action:** Replaced `contentDescription = null` with proper `stringResource` values for these elements. Ensure that when adding new images or icons that convey meaning, they are always accompanied by a proper content description in both supported languages (Italian and English).
