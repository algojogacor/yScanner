# M19: PDF Export Options & Size Estimation

## Objective
Implement the PDF export user interface, file size estimation algorithm, and generation progress tracking. This enables the user to configure page sizes and quality settings, preview the resulting file size, and share the generated PDF document.

## Product Requirements
- **22.1**: Page size selector (A4, Auto, A5, B5, Letter, Original Ratio)
- **22.2**: Quality selector (High, Balanced, Small)
- **22.3**: Editable filename input
- **22.4**: Estimated file size display
- **22.5**: Export generation progress UI with cancellation
- **22.6**: Parameter tuning for quality profiles
- **22.7**: System share sheet integration

## Architecture References
- **11.2**: PDF Export flow
- **42**: Export UI components
- **43**: Size Estimation algorithms
- **44**: Output handling and sharing

## Current State
The core PDF rendering engine from M18 exists and can generate basic PDF files, but there is no user-facing interface to configure settings, estimate size, or trigger the export flow with progress feedback.

## Scope
- Define `PdfSizeEstimator` interface and its implementation.
- Develop the size estimation algorithm based on page dimensions, complexity, and quality profiles.
- Build the Compose UI for PDF export settings (filename, page size, quality, estimated size).
- Build the Compose UI for export progress with a cancel button.
- Tune compression and resolution parameters for High, Balanced, and Small profiles.
- Implement file saving to a user-accessible directory (e.g., MediaStore or Documents) and integrate Android's ShareSheet.

## Non-Goals
- OCR integration or text overlay in the PDF.
- PDF password protection or encryption.
- Cloud syncing of generated PDFs.
- Direct printing (only standard Android sharing is supported).

## Dependencies
- M18: Core PDF Rendering Engine must be completed.
- The `Document` data model containing page metadata.

## Components
- `com.yscanner.export.PdfSizeEstimator`: Interface for estimation.
- `com.yscanner.export.PdfSizeEstimatorImpl`: Implementation applying dimension and compression heuristics.
- `com.yscanner.export.QualityProfile`: Enum or sealed class defining High, Balanced, Small parameters.
- `com.yscanner.export.PageSizeOption`: Enum for A4, A5, B5, Letter, Auto.
- `com.yscanner.export.ui.ExportSettingsScreen`: Compose screen for configuring export.
- `com.yscanner.export.ui.ExportSettingsViewModel`: ViewModel managing export state and size estimation.
- `com.yscanner.export.ui.ExportProgressDialog`: Compose dialog showing rendering progress.
- `com.yscanner.export.ShareHelper`: Utility for invoking Android `Intent.ACTION_SEND`.

## Data Flow
1. User opens Export Settings. `ExportSettingsViewModel` initializes with default filename, A4 size, and Balanced quality.
2. ViewModel passes the `Document` and current `PdfOptions` to `PdfSizeEstimator`.
3. `PdfSizeEstimator` returns an estimated size (Long).
4. UI displays the estimate. As user changes options, ViewModel re-calculates the estimate.
5. User clicks "Export". UI shows `ExportProgressDialog`.
6. ViewModel calls PDF renderer (from M18). Renderer emits progress updates (pages rendered).
7. On completion, the file is saved to public storage, and `ShareHelper` opens the system ShareSheet.

## Implementation Steps
1. **Quality Profiles & Page Sizes**: Define `QualityProfile` (JPEG quality, scale factors) and `PageSizeOption` (dimensions in points) enums in `com.yscanner.export`.
2. **Size Estimator**: 
   - Create `PdfSizeEstimator` interface.
   - Implement `PdfSizeEstimatorImpl` to calculate base size (Page area * resolution factor * JPEG compression ratio based on `QualityProfile`) plus PDF overhead (~2-5KB per page).
3. **ViewModel**: Create `ExportSettingsViewModel` with `StateFlow` for filename, page size, quality, and estimated size.
4. **Settings UI**: Implement `ExportSettingsScreen` in Compose. Add a `TextField` for filename, dropdowns/segmented buttons for Size and Quality, and text for the size estimate.
5. **Progress UI**: Implement `ExportProgressDialog` showing a `LinearProgressIndicator` and a "Cancel" button.
6. **Export Action**: Wire the export button to invoke the PDF renderer. Expose a `Flow<Float>` from the renderer to update the progress bar.
7. **Storage & Sharing**: Implement `ShareHelper` using `FileProvider` to share the generated PDF file. Ensure output goes to a user-accessible directory using MediaStore or standard `Context.getExternalFilesDir`.
8. **Parameter Tuning**: Conduct empirical tests on sample images to finalize JPEG quality and downscale ratios for High (e.g., 90% quality, no downscale), Balanced (e.g., 75%, 0.8x), and Small (e.g., 60%, 0.5x).

## Testing
- `PdfSizeEstimatorTest`: Verify that changing page size or quality produces different, mathematically sound estimations.
- `ExportSettingsViewModelTest`: Verify that changing state triggers re-estimation.
- `ShareHelperTest`: Verify correct Intent flags and URI generation (using mock Context).
- `QualityProfileTest`: Assert correct dimension and quality mappings.

## Validation
- **Size Accuracy**: Generate 10 diverse PDFs and verify the estimated size is within 20% of the actual file size.
- **Page Integrity**: Open exported PDFs in standard viewers to ensure all page sizes (A4, A5, etc.) are valid and aspect ratios are preserved.
- **UX Flow**: Cancel an in-progress export to ensure the file is not corrupted or left locked, and UI returns to settings.

## Performance
- Size estimation must run in under 50ms (synchronous UI update).
- Export dialog must render smoothly without jank while the background thread performs heavy PDF generation.

## Failure Cases
- Storage full during export: Show localized error message "Not enough storage space".
- Out of Memory (OOM) during rendering: Catch `OutOfMemoryError`, cleanup partial files, and suggest "Small" quality.
- Share intent fails (no handler): Show Toast "No app available to share PDF".

## Acceptance Criteria
- User can select from the required page sizes and quality profiles.
- Filename can be edited before generation.
- Estimated size updates dynamically when options change.
- Export progress bar displays accurately and can be cancelled.
- Final PDF file size is within 20% of the estimate.
- User can successfully share the exported PDF via Android ShareSheet.

## Git Checkpoint
`feat: Add PDF export UI, size estimation, and sharing`

## Risks
- Estimating JPEG compression size accurately before actual compression is highly variable depending on image entropy (text vs. photos).
- High resolution PDFs on low-end devices may still cause OOMs despite tuning.

## Open Questions
- Should the estimated size show a range (e.g., "1.2 MB - 1.5 MB") instead of a single absolute number to account for entropy variance?
- Do we need a default suffix for filenames (e.g., `Document_20231024.pdf`)?
