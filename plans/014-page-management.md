# M13: Page Management & Multi-Page Workflow

## Objective
Implement the Page Manager screen, multi-page document workflow, page-level editing operations (crop, rotate, enhance), document-level operations, and an efficient undo/redo system for the editing session.

## Product Requirements
- PRD §17: Page Manager
- PRD §18: Multi-page workflow
- PRD INVARIANT: Manual crop only exists AFTER capture.

## Architecture References
- ARCHITECTURE.md §32-35

## Current State
The domain model (`PageObject`, `Document`) exists from M12. The underlying image processing and basic UI scaffolding exist. No multi-page state management, Page Manager UI, or undo/redo mechanisms are currently implemented.

## Scope
- Implement Page Manager UI (thumbnail grid, preview, operation buttons) in Jetpack Compose.
- Implement page operations (Rotate, Enhance, Delete, Duplicate, Retake).
- Implement post-capture manual crop editor (4-corner adjustment and perspective correction re-application).
- Implement document operations (Add page, Reorder pages via drag and drop, Apply enhancement to all pages).
- Implement a command-based or state-based undo/redo system for the editing session.
- Wire the multi-page workflow (Camera -> Capture -> Preview/Edit -> Camera -> Finish -> Page Manager).
- Implement on-demand page rendering.

## Non-Goals
- Live camera four-corner editing (strictly post-capture).
- Full bitmap snapshots for undo/redo (use command/operation descriptors instead).
- Final PDF export generation (covered in a later milestone).

## Dependencies
- M12: Domain Model & Processing Pipeline completion.

## Components
- `com.yscanner.app.ui.pagemanager.PageManagerScreen` (Compose screen)
- `com.yscanner.app.ui.pagemanager.PageManagerViewModel` (State holder)
- `com.yscanner.app.ui.editor.ManualCropScreen` (Compose screen for 4-corner crop)
- `com.yscanner.app.domain.model.PageOperation` (sealed class for undo/redo commands)
- `com.yscanner.app.domain.editor.EditSession` (Undo/redo manager)
- `com.yscanner.app.domain.editor.GeometryTransformer` (Re-applies perspective transform)

## Data Flow
1. User captures images in Camera screen -> `PageObject`s added to `Document`.
2. User proceeds to `PageManagerScreen` -> loads `Document` and displays thumbnails.
3. User selects a page -> loads preview image on demand.
4. User applies an operation (e.g., Rotate) -> `PageManagerViewModel` creates a `PageOperation` -> applied to `EditSession` -> updates `PageObject` -> UI triggers re-render.
5. User selects manual crop -> opens `ManualCropScreen` -> edits 4 corners -> applies -> new perspective transform computed -> preview updated.

## Implementation Steps
1. Create `PageOperation` sealed class and `EditSession` manager to handle undo/redo stacks.
2. Build `PageManagerViewModel` exposing `Document` state and editing actions.
3. Implement `PageManagerScreen` Compose UI with a thumbnail grid and preview area.
4. Implement drag-and-drop reordering logic within the grid.
5. Implement `ManualCropScreen` allowing interactive 4-corner drag on the captured image and applying `GeometryTransformer`.
6. Implement single-page actions: Rotate (90 degrees), Enhance (mode selection), Delete, Duplicate, and Retake (navigates back to Camera for replacement).
7. Implement document-wide actions: Add page (navigates to Camera) and Apply enhancement to all.
8. Wire the multi-page navigation flow (Camera -> Preview -> Camera -> Page Manager).
9. Ensure images are loaded and rendered on demand using a caching mechanism to avoid OOM errors.

## Testing
- `EditSessionTest`: Verify undo/redo logic correctly applies and reverts `PageOperation`s without data loss.
- `PageManagerViewModelTest`: Verify state emissions when adding, deleting, reordering, and editing pages.
- `GeometryTransformerTest`: Verify new geometry points produce correct perspective warp coordinates.

## Validation
- Verify page changes are reflected immediately in the preview and grid.
- Verify undo/redo works sequentially for multiple operations.
- Verify manual crop recalculates the perspective correctly without degrading the original image unnecessarily.

## Performance
- Loading previews must happen on a background thread.
- Full resolution bitmaps should only be loaded when necessary for editing/saving; use downscaled bitmaps for thumbnails and UI previews.
- Undo/redo must be memory-efficient (metadata only, no full bitmap copies).

## Failure Cases
- OOM when loading images: Catch exceptions, clear caches, and retry with higher subsampling.
- Invalid crop geometry (e.g., non-convex polygons): Reject and reset to previous geometry.

## Acceptance Criteria
- User can capture multiple pages and view them in a grid.
- User can rotate, enhance, delete, duplicate, and manually crop pages.
- User can drag to reorder pages.
- User can undo and redo editing operations.
- Multi-page capture flow works seamlessly.
- App handles multi-page documents (e.g., 20+ pages) without crashing from OOM.

## Git Checkpoint
`feat: implement Page Manager and multi-page workflow`

## Risks
- Handling many high-res images in memory could lead to OOM on low-end devices.
- Complex geometry interactions during manual crop could result in invalid shapes.

## Open Questions
- Should we persist the undo/redo stack across app restarts or limit it to the active editing session? (Defaulting to active session only).
