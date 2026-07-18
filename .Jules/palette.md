## 2024-07-24 - Accessibility improvements for hardcoded Strings

**Learning:** Hardcoded text inside `contentDescription` attributes can easily slip through normal string localization reviews since they are not directly rendered on the UI, which ultimately breaks accessibility (screen readers read untranslated text).
**Action:** When performing accessibility reviews, specifically search for hardcoded `contentDescription` text strings in `Icon`, `IconButton`, and `Image` composables and move them to `strings.xml`.
## 2026-07-07 - Extracting Strings for Screen Reader Accessibility
**Learning:** In a multi-language codebase, hardcoded `contentDescription` strings within Composables (like `Icon`) directly degrade accessibility by failing to translate for screen readers, meaning users who don't speak the default language are given unintelligible voice feedback.
**Action:** Always search explicitly for hardcoded string patterns inside `contentDescription = "..."` or `Text("...")` and convert them to `stringResource` values placed within `strings.xml` (both default and ).
## 2026-07-07 - Extracting Strings for Screen Reader Accessibility
**Learning:** In a multi-language codebase, hardcoded `contentDescription` strings within Composables (like `Icon`) directly degrade accessibility by failing to translate for screen readers, meaning users who don't speak the default language are given unintelligible voice feedback.
**Action:** Always search explicitly for hardcoded string patterns inside `contentDescription = "..."` or `Text("...")` and convert them to `stringResource` values placed within `strings.xml` (both default and localized variants).
## 2024-07-24 - Accessibility improvements for clickable modifiers
**Learning:** Custom Compose elements built with `Box` and `Modifier.clickable` are completely invisible to screen readers without semantic tags, causing them to be announced poorly (or not at all) as interactive buttons.
**Action:** When creating or reviewing custom clickable components, always provide an `onClickLabel` with a localized string and assign the semantic `role = Role.Button` within the `Modifier.clickable()` parameter list.

## 2024-07-24 - Accessibility improvements for hardcoded Strings in Text
**Learning:** Hardcoded text inside `Text` composables directly degrades accessibility by failing to translate for screen readers, meaning users who don't speak the default language are given unintelligible voice feedback.
**Action:** Always search explicitly for hardcoded string patterns inside `Text("...")` and convert them to `stringResource` values placed within `strings.xml` (both default and localized variants).
