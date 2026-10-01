# M18: PDF Renderer

## Objective
Implement the PDF Renderer to convert a Document containing multiple PageObjects into a single PDF file, supporting various page sizes and quality profiles using a streaming page-by-page rendering approach.

## Product Requirements
PRD.md §22

## Architecture References
ARCHITECTURE.md §11, §39-44

## Current State
The project currently has only a default Android scaffold with a single `:app` module. The Document model with PageObjects (M12) exists conceptually.

## Scope
- Implement `PdfRenderer` interface and implementation.
- Define `PdfOptions`, `PdfPageSize`, `PdfQuality`, and `PdfResult` data structures.
- Implement the PDF layout engine supporting A4, AUTO, A5, B5, LETTER, and ORIGINAL_RATIO.
- Implement quality profiles (HIGH, BALANCED, SMALL) controlling resolution and JPEG compression.
- Implement page-by-page streaming rendering using Android's `PdfDocument` API to minimize memory footprint.

## Non-Goals
- UI integration for PDF export (will be handled in a separate milestone).
- PDF encryption or password protection.
- OCR text layer in the PDF.
- Advanced PDF features like bookmarks or annotations.

## Dependencies
- Document model and PageObjects (M12).

## Components
- `com.localscan.export.PdfRenderer`: Interface for rendering PDF.
- `com.localscan.export.PdfRendererImpl`: Implementation using `android.graphics.pdf.PdfDocument`.
- `com.localscan.export.PdfOptions`: Data class holding export options.
- `com.localscan.export.PdfPageSize`: Enum (A4, AUTO, A5, B5, LETTER, ORIGINAL_RATIO).
- `com.localscan.export.PdfQuality`: Enum (HIGH, BALANCED, SMALL).
- `com.localscan.export.PdfResult`: Data class for result details.
- `com.localscan.export.PdfLayoutEngine`: Utility for calculating page dimensions and image bounds.

## Data Flow
1. Caller invokes `PdfRenderer.render(document, options, outputPath, progressCallback)`.
2. `PdfRendererImpl` creates a `android.graphics.pdf.PdfDocument`.
3. For each `PageObject` in `Document`:
   a. Load image bitmap using appropriate `BitmapFactory.Options` based on `PdfQuality`.
   b. Determine dimensions using `PdfLayoutEngine` based on `PdfPageSize` and source aspect ratio.
   c. Compress bitmap if necessary.
   d. Start `PdfDocument.Page`, draw bitmap to page canvas, finish page.
   e. Release bitmap memory (recycle).
   f. Invoke `progressCallback`.
4. Write `PdfDocument` to `outputPath`.
5. Return `PdfResult`.

## Implementation Steps
1. Create `com.localscan.export` package.
2. Create `PdfPageSize.kt` (A4, AUTO, A5, B5, LETTER, ORIGINAL_RATIO) and `PdfQuality.kt` (HIGH, BALANCED, SMALL).
3. Create `PdfOptions.kt` and `PdfResult.kt` data classes.
4. Create `PdfRenderer.kt` interface.
5. Implement `PdfLayoutEngine.kt` to calculate dimensions:
   - A4/A5/B5/Letter: fit document image within page, preserve aspect ratio, minimize blank space.
   - AUTO: page size matches document dimensions.
   - ORIGINAL_RATIO: page size preserves exact document ratio.
6. Implement `PdfRendererImpl.kt` utilizing `android.graphics.pdf.PdfDocument`:
   - Stream page-by-page: load source -> apply geometry -> enhance -> compress -> write to PDF -> release bitmap.
   - Do NOT hold all pages in memory simultaneously.
7. Add unit tests for `PdfLayoutEngine`.
8. Add integration tests for `PdfRendererImpl`.

## Testing
- `PdfLayoutEngineTest`: Verify output page dimensions and image drawing rects for different `PdfPageSize` options, ensuring aspect ratio preservation.
- `PdfRendererImplTest`: Verify PDF generation succeeds, file is created, page count matches, file size matches expected ranges for different `PdfQuality` levels, and progress callback is invoked correctly.

## Validation
- Generated PDF files must be valid and viewable in standard PDF readers (e.g., Adobe Acrobat, Chrome).
- Memory profiling must show that only one page bitmap is loaded into memory at a time during rendering.

## Performance
- Memory: RAM usage bounded by the size of the largest single page bitmap (at the target quality). No out-of-memory errors for large documents (e.g., 50+ pages).
- CPU: Execution time scales linearly with page count.
- Storage: Output file size reflects the chosen `PdfQuality`.

## Failure Cases
- Disk full during writing: Throw `IOException` and clean up partial files.
- Invalid image source: Fail the render process and propagate an error.
- Interruption: Cancel the rendering safely if the process is cancelled.

## Acceptance Criteria
- `PdfRenderer` successfully converts a multi-page document into a valid PDF file.
- All `PdfPageSize` and `PdfQuality` options are supported and function correctly.
- Memory usage remains stable (O(1) with respect to document length) during rendering.
- Progress callback is called per page.
- No unnecessary blank space in the PDF pages.

## Git Checkpoint
`feat(export): implement PDF renderer with streaming page support`

## Risks
- Handling very high-resolution source images might cause OOM before resizing can occur; needs careful `BitmapFactory.Options` usage for downsampling.
- Variations in Android's `PdfDocument` API behavior across OS versions.

## Open Questions
- What are the exact pixel dimensions/DPI mappings for standard sizes like A4 in Android's `PdfDocument` (default is 72 DPI)?
