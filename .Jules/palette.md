## 2024-07-24 - Accessibility improvements for hardcoded Strings

**Learning:** Hardcoded text inside `contentDescription` attributes can easily slip through normal string localization reviews since they are not directly rendered on the UI, which ultimately breaks accessibility (screen readers read untranslated text).
**Action:** When performing accessibility reviews, specifically search for hardcoded `contentDescription` text strings in `Icon`, `IconButton`, and `Image` composables and move them to `strings.xml`.
## 2026-07-07 - Extracting Strings for Screen Reader Accessibility
**Learning:** In a multi-language codebase, hardcoded `contentDescription` strings within Composables (like `Icon`) directly degrade accessibility by failing to translate for screen readers, meaning users who don't speak the default language are given unintelligible voice feedback.
**Action:** Always search explicitly for hardcoded string patterns inside `contentDescription = "..."` or `Text("...")` and convert them to `stringResource` values placed within `strings.xml` (both default and ).
## 2026-07-07 - Extracting Strings for Screen Reader Accessibility
**Learning:** In a multi-language codebase, hardcoded `contentDescription` strings within Composables (like `Icon`) directly degrade accessibility by failing to translate for screen readers, meaning users who don't speak the default language are given unintelligible voice feedback.
**Action:** Always search explicitly for hardcoded string patterns inside `contentDescription = "..."` or `Text("...")` and convert them to `stringResource` values placed within `strings.xml` (both default and localized variants).
