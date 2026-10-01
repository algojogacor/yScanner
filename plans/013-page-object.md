# M12: PageObject & Document Domain Model

## Objective
Establish the core domain model representing documents and their constituent pages (PageObject). This model acts as the authoritative representation of a scan, ensuring non-destructive editing where the original asset plus geometric and enhancement parameters can reproduce any derived image.

## Product Requirements
PRD.md §16, §17, §21

## Architecture References
ARCHITECTURE.md §9, §32-35

## Current State
No domain models for PageObject or Document exist. Processing pipeline exists but operates on raw images/data rather than rich domain entities. The app consists of a basic scaffold.

## Scope
1. Define the pure Kotlin `:domain` module.
2. Implement `PageObject` data class.
3. Implement `Document` data class.
4. Implement enums: `ScanMode`, `CaptureMethod`, `SourceType`, `EnhancementMode`, `ProcessingState`.
5. Implement `PageGeometry` sealed hierarchy.
6. Define domain interfaces for document and page operations.
7. Design for non-destructive rendering pipeline (rendered bitmap is a derived artifact).

## Non-Goals
- Implementing the Room database or physical storage mapping for these models.
- Implementing the actual UI to view or edit these models.
- OCR implementation (though the model must allow future extensibility).

## Dependencies
- M11 (Image Processing Pipeline) should be understood conceptually for parameter types.

## Components
Module: `:domain` (pure Kotlin, no Android framework dependencies)
- `com.localscan.domain.model.PageObject`
- `com.localscan.domain.model.Document`
- `com.localscan.domain.model.PageGeometry` (Sealed class: `SinglePageGeometry`, `TwoPageGeometry`, `ManualCropGeometry`)
- `com.localscan.domain.model.enums.*` (`ScanMode`, `CaptureMethod`, `SourceType`, `EnhancementMode`, `ProcessingState`)
- `com.localscan.domain.repository.DocumentRepository` (interface)

## Data Flow
Domain Model Instantiation -> Use Cases -> Repository Interfaces (to be implemented by data layer). 
Key pipeline invariant: PageObject + Source Asset -> Non-destructive render.

## Implementation Steps
1. Create `:domain` module in `d:\Projects\pdfscanner\domain` with pure Kotlin Gradle configuration.
2. Create `com.localscan.domain.model.enums` package: Define `ScanMode` (ONE_PAGE, TWO_PAGE), `CaptureMethod` (AUTO, MANUAL), `SourceType` (CAMERA, GALLERY), `ProcessingState` (PENDING, PROCESSING, COMPLETED, FAILED), `EnhancementMode` (ORIGINAL, NATURAL, CLEAN).
3. Create `com.localscan.domain.model.PageGeometry.kt`: Define sealed class `PageGeometry` and data classes `SinglePageGeometry`, `TwoPageGeometry` (left/right), `ManualCropGeometry` with properties: originalQuadrilateral, refinedQuadrilateral, outputSize, sourceImageSize.
4. Create `com.localscan.domain.model.PageObject.kt`: Define `PageObject` data class: id, documentId, sourceAssetPath, geometry, rotation (0, 90, 180, 270), enhancementMode, enhancementParameters, qualityMetrics, metadata (timestamp, scanMode, captureMethod, sourceType), processingState.
5. Create `com.localscan.domain.model.Document.kt`: Define `Document` data class: id, name, createdAt, modifiedAt, pages: List<PageObject>, pageCount.
6. Create `com.localscan.domain.repository.DocumentRepository.kt`: Define interface for CRUD operations (create, get, update, delete).

## Testing
- `com.localscan.domain.model.PageObjectTest`: Verify initialization, immutability, and data integrity.
- `com.localscan.domain.model.DocumentTest`: Verify page list management, count consistency, and immutability.

## Validation
- Ensure `:domain` module compiles with no Android dependencies (`import android.*` should result in compilation error).
- Validate that models enforce required fields and immutability (val properties).

## Performance
- Negligible performance overhead. These are lightweight pure Kotlin data classes.

## Failure Cases
- N/A for pure domain models (validation logic should throw standard exceptions like IllegalArgumentException).

## Acceptance Criteria
- `:domain` module exists and is purely Kotlin.
- `PageObject`, `Document`, `PageGeometry`, and enums are fully defined as per requirements.
- Domain interfaces for document operations are defined.
- Architecture inherently supports non-destructive recreation of final images from `sourceAssetPath` and parameters.
- Future OCR extensibility is supported (e.g., nullable `ocrData` or extensible metadata map).

## Git Checkpoint
"feat(domain): implement PageObject and Document domain models for M12"

## Risks
- Incorrect `PageGeometry` hierarchy abstraction might complicate the downstream rendering pipeline.
- Ensuring pure Kotlin module doesn't accidentally depend on Android libraries.

## Open Questions
- Should `enhancementParameters` be a `Map<String, Any>` or a strongly-typed sealed class?
