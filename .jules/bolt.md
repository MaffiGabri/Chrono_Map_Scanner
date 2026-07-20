
## 2024-07-05 - Missing key in LazyColumn items

**Learning:** Missing `key` in `LazyColumn` items in Jetpack Compose can cause unnecessary re-renders when list data changes, especially when reordering, deleting, or adding items, impacting performance.

**Action:** Added `key` to `items` in `MoleDetailsScreen` and `SettingsScreen` to help Compose optimize list rendering by uniquely identifying elements based on item data (e.g. `entry.id`, `profile`).

## 2024-07-19 - Avoid Path Instantiation in Compose Canvas Loops
**Learning:** Instantiating objects like `Path` inside a `Canvas` drawing loop in Jetpack Compose causes micro-allocations which degrade performance during frequent redraws (e.g., dragging, zooming).
**Action:** Extracted `androidx.compose.ui.graphics.Path()` instantiation out of the `Canvas` drawing loop in `BodyMapScreen` using `remember` and reused it using `.reset()` before applying new parameters.
