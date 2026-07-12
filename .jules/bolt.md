
## 2024-07-05 - Missing key in LazyColumn items

**Learning:** Missing `key` in `LazyColumn` items in Jetpack Compose can cause unnecessary re-renders when list data changes, especially when reordering, deleting, or adding items, impacting performance.

**Action:** Added `key` to `items` in `MoleDetailsScreen` and `SettingsScreen` to help Compose optimize list rendering by uniquely identifying elements based on item data (e.g. `entry.id`, `profile`).

## 2024-07-12 - Reusing Path object in Jetpack Compose Canvas loops

**Learning:** Creating new `androidx.compose.ui.graphics.Path` objects inside a Jetpack Compose `Canvas` loop (e.g., iterating through items to clip images) can lead to excessive garbage collection and dropped frames. Micro-allocations within the `draw` phase are particularly harmful to frontend performance.

**Action:** Extracted the `Path` instantiation outside the `Canvas` scope using `remember`, and updated the drawing loop to reuse this single path by calling `.reset()` before adding new geometry. This saves hundreds of allocations per frame in screens displaying many markers.
