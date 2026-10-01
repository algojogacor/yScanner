# M17: Gallery Import

## Objective
Enable users to import existing photos from their device gallery and process them through the standard document scanning pipeline.

## Product Requirements
PRD.md §19-20

## Architecture References
ARCHITECTURE.md §12, §39

## Current State
The full single-page processing pipeline (document detection → geometry → perspective correction → enhancement) exists. However, it is currently only fed by the camera capture module. There is no mechanism to feed existing images from device storage into this pipeline.

## Scope
- Implement `GalleryImporter` interface to handle `List<Uri> → List<PageObject>`.
- Integrate Android Photo Picker (and legacy MediaStore fallback if required).
- Implement EXIF orientation handling and memory-efficient loading of large images.
- Push imported images through the exact same processing pipeline as camera captures.
- Support batch processing for multiple image selections.
- Provide a fallback mechanism for images where no document is detected (default to full-image crop).
- Handle permissions appropriately using modern Android photo picker.
- Show progress indication for batch imports.
- Add imported pages to the current document via `PageManager`.

## Non-Goals
- Building a custom gallery UI (will use the system photo picker).
- In-place editing or modifying original gallery photos (copies are made).
- Video extraction or frame grabbing.

## Dependencies
- M04: Geometry Engine
- M11: Enhancement Pipeline

## Components
- `com.yscanner.core.gallery.GalleryImporter`: Interface for importing images.
- `com.yscanner.core.gallery.GalleryImporterImpl`: Implementation handling EXIF and memory-efficient loading.
- `com.yscanner.core.gallery.BitmapUtils`: Utility for downsampling and EXIF rotation.
- `com.yscanner.ui.gallery.GalleryImportViewModel`: Manages batch import state, progress, and errors.
- `com.yscanner.ui.gallery.GalleryPickerContract`: `ActivityResultContract` for the photo picker.
- `com.yscanner.core.pipeline.PipelineManager`: The existing pipeline to reuse.

## Data Flow
1. User selects `List<Uri>` via system photo picker.
2. `GalleryImporter` reads EXIF metadata, decodes images memory-efficiently (downsampling if needed), and copies them to app-private storage.
3. For each copied image, an `ImageSource` is created.
4. `ImageSource` is sent to the existing document detection pipeline.
5. If a document is detected, the detected quad is used. If not, the pipeline falls back to full image bounds.
6. Perspective correction and enhancement are applied sequentially.
7. A `PageObject` is generated and added to `PageManager`.

## Implementation Steps
1. Create `com.yscanner.core.gallery.GalleryImporter` interface and `GalleryImporterImpl`.
2. Implement memory-efficient bitmap loading with EXIF rotation correction in `BitmapUtils.decodeWithExif()`. Ensure it handles JPEG, PNG, and HEIF.
3. Implement `GalleryPickerContract` to launch `PickVisualMediaRequest(ActivityResultContracts.PickMultipleVisualMedia())`.
4. Create `GalleryImportViewModel` to coordinate batch processing, tracking total vs. completed items and reporting progress per image.
5. Update the pipeline integration in `GalleryImporterImpl` to run the `DetectionEngine`. If detection returns no quad, generate a quad representing the full image boundaries.
6. Feed the quad and image into `GeometryEngine` and `EnhancementEngine` to produce a final `PageObject`.
7. Add an "Import from Gallery" entry point in the UI that launches the picker and observes the view model's progress state.
8. Persist the generated `PageObject` list into the current document via `PageManager`.

## Testing
- `GalleryImporterImplTest`: Verify correct decoding, EXIF rotation handling, and app-private storage copying for mock URIs.
- `GalleryBatchProcessingTest`: Verify multiple URIs are processed sequentially and added correctly to `PageManager`.
- `NoDocumentFallbackTest`: Verify an image with no visible document yields a `PageObject` representing the full-image bounds.
- `ExifRotationTest`: Verify 90, 180, and 270-degree rotated images are corrected before processing.

## Validation
- Import 50+ high-resolution images simultaneously to ensure no OOM exceptions occur.
- Import images with varied EXIF rotation flags and visually confirm the final output is upright.
- Verify modern photo picker is used on supported devices without requesting `READ_EXTERNAL_STORAGE`.

## Performance
- **RAM**: Must process images sequentially or in very small chunks to avoid OOM. Must decode bounds first (`inJustDecodeBounds = true`) before loading the full bitmap to ensure safe downsampling.
- **CPU**: All decoding and pipeline execution must happen on a background dispatcher (`Dispatchers.IO` or `Dispatchers.Default`), never blocking the main UI thread.

## Failure Cases
- **OOM during load**: Catch `OutOfMemoryError`, increase downsampling factor, and retry, or fail the individual image gracefully and continue the batch.
- **Corrupt image URI**: Skip, report a failure for that specific item, and continue with the rest of the batch.
- **Storage full**: Abort the entire operation gracefully and show an error dialog.

## Acceptance Criteria
- User can select multiple images (e.g., 20+) from the system gallery.
- Images are correctly rotated based on EXIF data.
- Document detection runs; fallback to full crop works seamlessly if detection fails.
- Final processed pages appear in the current document.
- UI shows clear batch progress (e.g., "Processing 3 of 10").

## Git Checkpoint
`feat: implement gallery import and batch processing pipeline`

## Risks
- Severe OOM risks with high-res modern smartphone photos (e.g., 48MP/108MP sensors). Robust downsampling is critical.
- Long processing times for large batches could lead to the user backgrounding the app, potentially causing the OS to kill the process if not handled as a foreground service (if applicable).

## Open Questions
- Should very large batch imports (e.g., >50 images) automatically trigger a Foreground Service to prevent process death if the user backgrounds the app?
