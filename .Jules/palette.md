## 2024-07-24 - Accessibility improvements for hardcoded Strings

**Learning:** Hardcoded text inside `contentDescription` attributes can easily slip through normal string localization reviews since they are not directly rendered on the UI, which ultimately breaks accessibility (screen readers read untranslated text).
**Action:** When performing accessibility reviews, specifically search for hardcoded `contentDescription` text strings in `Icon`, `IconButton`, and `Image` composables and move them to `strings.xml`.
