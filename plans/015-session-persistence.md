# M14: Session Persistence & Recovery

## Objective
Implement robust local persistence and recovery for scan sessions, ensuring that multi-page workflows survive lifecycle interruptions like configuration changes and process deaths. Implement the underlying data layer combining Room database for metadata and file-based storage for source assets and derived images.

## Product Requirements
- PRD.md §20: Storage (Local secure storage for in-progress and completed scans)
- PRD.md §21: Scan session recovery (Restoring state after interruptions, process death)

## Architecture References
- ARCHITECTURE.md §36: Persistence Architecture
- ARCHITECTURE.md §37: Storage Hierarchy
- ARCHITECTURE.md §38: Process Death & Recovery Strategy

## Current State
The repository currently has only a default Android scaffold with a single `:app` module. No feature modules or data layer logic exist yet.

## Scope
- Room database setup for session metadata and page metadata.
- File-based storage hierarchy within the app-private workspace.
- `DocumentRepository` for managing `DocumentEntity` and `PageEntity`.
- `SourceAssetManager` for handling source and processed images on disk.
- `SessionPersistence` for session lifecycle (save, load, list, recover).
- Incremental persistence (saving after every page modification).
- Handling recovery scenarios: activity recreation, backgrounding, configuration changes, and process death.
- Implementing a cleanup policy for orphaned assets and temporary files.

## Non-Goals
- Generating PDF exports from the sessions (handled in a separate milestone).
- Cloud backup or synchronization.
- Building the UI layer for a session gallery or complex document management (this focuses purely on the underlying storage and recovery mechanisms).

## Dependencies
- M13: Page Manager and Multi-page Workflow (architecture/design decisions from M13 must inform the schema).
- Standard Android Jetpack libraries (Room, WorkManager).

## Components
- `com.localscan.data.db.AppDatabase`: Room database instance.
- `com.localscan.data.db.dao.DocumentDao`: Data access object for documents and pages.
- `com.localscan.data.entity.DocumentEntity`: Room entity for a scanning session/document.
- `com.localscan.data.entity.PageEntity`: Room entity for an individual page (stores file paths, order, crop coordinates).
- `com.localscan.data.repository.DocumentRepository`: Interface and implementation (`DocumentRepositoryImpl`) for CRUD operations on documents and pages.
- `com.localscan.data.storage.SourceAssetManager`: Interface and implementation (`SourceAssetManagerImpl`) for reading/writing image files.
- `com.localscan.data.session.SessionPersistence`: Interface and implementation (`SessionPersistenceImpl`) for saving/loading sessions.
- `com.localscan.data.storage.StorageHierarchy`: Utility object to manage the directory structures.

## Data Flow
1. **New Capture**: Camera captures an image -> `SourceAssetManager` stores to `sessions/<session-id>/sources/` -> `DocumentRepository` creates/updates `PageEntity`.
2. **Editing**: Image processed -> `SourceAssetManager` stores output to `sessions/<session-id>/derived/` -> `DocumentRepository` updates `PageEntity`.
3. **Session Interruption**: System kills app -> On restart, UI requests `SessionPersistence.recoverInterruptedSessions()` -> Rebuilds PageManager state from Room DB and file paths.

## Implementation Steps
1. **Storage Hierarchy Setup**
   - Create `StorageHierarchy` in `com.localscan.data.storage` managing:
     - `sessions/<session-id>/metadata`
     - `sessions/<session-id>/sources/`
     - `sessions/<session-id>/derived/`
     - `sessions/<session-id>/temp/`
     - `exports/`
2. **Room Database Implementation**
   - Create `DocumentEntity` (id, timestamp, status).
   - Create `PageEntity` (id, documentId, orderIndex, sourcePath, derivedPath).
   - Create `DocumentDao` with insert, update, delete, get, reorder queries.
   - Setup `AppDatabase` extending `RoomDatabase`.
3. **SourceAssetManager**
   - Create `SourceAssetManager` interface.
   - Implement `store(Bitmap)`, `store(Uri)`, `getImageSource(path)`, `delete(path)`, and `cleanup(retainedPaths)` in `SourceAssetManagerImpl`. Ensure source assets are never deleted while referenced by any `PageEntity`.
4. **DocumentRepository**
   - Create `DocumentRepository` interface.
   - Implement `createDocument`, `getDocument`, `updateDocument`, `deleteDocument`, `getAllDocuments`, `addPage`, `updatePage`, `removePage`, `reorderPages` in `DocumentRepositoryImpl` using `DocumentDao`.
5. **SessionPersistence**
   - Create `SessionPersistence` interface.
   - Implement `saveSession`, `loadSession`, `listSessions`, `deleteSession`, `recoverInterruptedSessions` in `SessionPersistenceImpl`.
6. **Incremental Persistence Hook-up**
   - Wire `SessionPersistence` to the multi-page workflow to trigger `saveSession` asynchronously after every page add/edit/reorder.
7. **Process Death Recovery Hook-up**
   - Ensure the main entry point (Activity/ViewModel) checks for interrupted sessions via `SessionPersistence.recoverInterruptedSessions()` on `onCreate()` if `savedInstanceState` indicates an abnormal termination.
8. **Cleanup Worker**
   - Implement a WorkManager `CoroutineWorker` (`CleanupWorker`) that scans `sessions/<session-id>/temp/` and cleans up orphaned files by checking valid paths against `DocumentRepository`.

## Testing
- **Room Tests**: `DocumentDaoTest` to verify CRUD, cascading deletes, and page reordering.
- **Storage Tests**: `SourceAssetManagerTest` testing file creation, reading, and deletion.
- **Persistence Tests**: `SessionPersistenceTest` mocking DAO and file storage to verify state serialization and incremental saves.
- **Recovery Tests**: Unit test simulating process death (recreating `SessionPersistenceImpl` and verifying `recoverInterruptedSessions()` correctly rebuilds the active session state).

## Validation
- Launch app, capture two pages, force process kill via ADB (`adb shell am kill com.localscan.app`), restart app -> verify session resumes with two pages.
- Verify storage hierarchy maps correctly to the device's internal app data directory.
- Verify `CleanupWorker` correctly identifies and removes unreferenced files.

## Performance
- All DB and Disk I/O must use `Dispatchers.IO`.
- `SourceAssetManager` must efficiently compress bitmaps (e.g., JPEG/WebP) to minimize disk usage while maintaining acceptable resolution.
- Incremental saves must be fast enough not to block UI interaction during rapid sequential captures.

## Failure Cases
- **Disk Full**: Catch `IOException` on asset store, bubble up specific `StorageException` for UI error display.
- **DB Corruption**: Fallback to destructive recreation (using Room's `fallbackToDestructiveMigration()`).
- **Missing File**: If `PageEntity` references a file that doesn't exist, gracefully drop the page or show a missing image placeholder, and remove the entry on next save.

## Acceptance Criteria
- `DocumentRepository`, `SourceAssetManager`, and `SessionPersistence` interfaces are fully implemented and unit-tested.
- App-private storage matches the defined hierarchy.
- Adding, editing, and deleting pages incrementally updates the Room DB.
- Simulating a process death during an active session successfully restores the session upon reopening the app.
- Temporary files and orphaned assets are successfully removed by the cleanup routine.

## Git Checkpoint
`feat(persistence): implement session persistence, room database, and process recovery`

## Risks
- **Disk Exhaustion**: High-resolution source images could quickly fill up device storage if temp files aren't reliably cleaned up.
- **Concurrency**: Race conditions between background saving and user editing could lead to corrupted or mismatched state.

## Open Questions
- Do we need to encrypt the Room DB for session metadata, or is standard Android app sandboxing sufficient for our security requirements?
- Should we expose a "Settings" option for users to manually trigger a storage cleanup?
