## 2026-07-05 - Accessibility Labels for Icon Buttons
**Learning:** Found instances where screen reader labels (contentDescription) on icon-only buttons were hardcoded as plain strings (e.g., "Cambia vista") instead of using translation resources (stringResource(R.string...)). This prevents accessibility tools from correctly localizing labels for users of different languages.
**Action:** Ensure all `contentDescription` properties on interactive Compose elements use `stringResource` when specifying UI components, avoiding hardcoded plain strings so the app remains fully accessible across all supported languages.
## 2026-07-05 - Accessibility Labels for ExtendedFloatingActionButton
**Learning:** Some compound interactive components like ExtendedFloatingActionButton have multiple parts (icon and text) but the icon part sometimes had `contentDescription = null` even when it provides context.
**Action:** When an icon changes state along with a button (e.g. Add -> Cancel), ensure its content description changes appropriately alongside the text.
