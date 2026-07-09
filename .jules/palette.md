## 2024-07-08 - Fixing null content descriptions
**Learning:** Found that some `Icon` components in Jetpack Compose were missing `contentDescription`, leading to accessibility issues, particularly for visually impaired users.
**Action:** When adding `Icon` or `Image` components, ensure they always have a meaningful `contentDescription` using `stringResource` unless they are purely decorative and accompanied by descriptive text, in which case `contentDescription = null` is acceptable but discouraged. Always try to find a suitable description.
