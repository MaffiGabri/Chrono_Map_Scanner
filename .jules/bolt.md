
## 2024-07-05 - Missing key in LazyColumn items

**Learning:** Missing `key` in `LazyColumn` items in Jetpack Compose can cause unnecessary re-renders when list data changes, especially when reordering, deleting, or adding items, impacting performance.

**Action:** Added `key` to `items` in `MoleDetailsScreen` and `SettingsScreen` to help Compose optimize list rendering by uniquely identifying elements based on item data (e.g. `entry.id`, `profile`).
