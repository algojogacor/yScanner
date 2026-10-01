# M11: Enhancement (Natural / Clean)

## Objective
Implement the image enhancement pipelines (Natural and Clean modes) to improve the readability of perspective-corrected document images, while ensuring a non-destructive processing architecture.

## Product Requirements
- PRD §14: Enhancement
- PRD §15: Non-destructive processing

## Architecture References
- ARCHITECTURE.md §29-31

## Current State
Perspective-corrected document images are available from the cropping pipeline (M09), and quality metrics are provided (M10). The repository currently lacks the image enhancement components.

## Scope
- Define the `EnhancementEngine` interface and models (`EnhancementMode`, `EnhancementParameters`).
- Implement the **Original** mode (no processing, pass-through).
- Implement the **Natural** enhancement pipeline (preserve color, mild corrections, illumination normalization, modest denoise).
- Implement the **Clean** enhancement pipeline (shadow suppression, contrast improvement, local detail enhancement, selective sharpening, color-aware).
- Ensure a non-destructive design where the source image is untouched and enhancement parameters are stored for re-rendering.
- Ensure pipelines preserve handwriting, pencil marks, colored ink, signatures, and stamps.
- Guarantee that no generated content (hallucinations) is introduced.
- Guarantee that processing is not so aggressive that faint meaningful information disappears.

## Non-Goals
- UI components for selecting enhancement modes or adjusting parameters (to be handled in later UI milestones).
- Document and page storage integration.
- Binarization (Black & White mode) or Grayscale mode (if not part of Natural/Clean, though Clean is often color-aware).
- Cloud-based enhancement.

## Dependencies
- M09: Cropping / Perspective Correction.
- M10: Document Quality Metrics.
- Spike S05: Enhancement Pipeline (for algorithm selection and OpenCV integration).

## Components
- `com.localscan.enhancement.EnhancementEngine`: Interface defining the processing contract.
- `com.localscan.enhancement.EnhancementMode`: Enum containing `ORIGINAL`, `NATURAL`, `CLEAN`.
- `com.localscan.enhancement.EnhancementParameters`: Data class containing tuning values (brightness, contrast, saturation, sharpness, shadowReduction, noiseReduction).
- `com.localscan.enhancement.pipeline.NaturalPipeline`: Implementation of the Natural enhancement process using OpenCV.
- `com.localscan.enhancement.pipeline.CleanPipeline`: Implementation of the Clean enhancement process using OpenCV.
- `com.localscan.enhancement.OpenCvEnhancementEngine`: Implementation of `EnhancementEngine` orchestrating the pipelines.

## Data Flow
`ImageSource` (Perspective-corrected image) + `EnhancementMode` + `EnhancementParameters` -> `EnhancementEngine` -> Processed `Bitmap`.

## Implementation Steps
1. **Define Interfaces and Models:**
   - Create `EnhancementMode` enum (`ORIGINAL`, `NATURAL`, `CLEAN`) in `com.localscan.enhancement`.
   - Create `EnhancementParameters` data class in `com.localscan.enhancement`.
   - Define `EnhancementEngine` interface in `com.localscan.enhancement`.
2. **Implement Original Pipeline:**
   - Create `PassThroughPipeline` that simply decodes and returns the source `Bitmap` without modifications.
3. **Implement Natural Pipeline (`NaturalPipeline.kt`):**
   - Apply mild exposure and white balance correction.
   - Use OpenCV for illumination normalization.
   - Apply modest denoising (e.g., bilateral filter) without excessive sharpening.
   - Ensure color fidelity is preserved.
4. **Implement Clean Pipeline (`CleanPipeline.kt`):**
   - Apply illumination normalization and shadow suppression.
   - Improve contrast using CLAHE (Contrast Limited Adaptive Histogram Equalization) on the luminance channel (e.g., LAB color space).
   - Apply local adaptive thresholding logic or unsharp masking for local detail enhancement.
   - Apply selective sharpening and denoise while preserving colored ink/stamps.
5. **Create Engine Implementation:**
   - Implement `OpenCvEnhancementEngine` that routes requests to the correct pipeline based on the provided `EnhancementMode`.
   - Ensure the engine handles `EnhancementParameters` dynamically.
6. **Integration & Non-Destructive Wrapper:**
   - Ensure the engine relies purely on inputs and returns a new `Bitmap` to maintain the non-destructive architecture.

## Testing
- `EnhancementEngineTest`: Verify the correct pipeline is invoked based on `EnhancementMode`.
- `NaturalPipelineTest`: Verify output dimensions match input, and extreme color shifts do not occur.
- `CleanPipelineTest`: Verify contrast improvement and detail preservation.
- `NonDestructiveTest`: Ensure the source file/bitmap is never modified by the engine.
- Visual Regression Tests: Compare pipeline outputs against a known set of test images containing handwriting, pencil, and stamps to ensure detail preservation.

## Validation
- Provide a set of test document images (receipts, handwritten notes, printed documents with signatures).
- Manually review outputs of `NATURAL` and `CLEAN` modes to ensure no text loss, no hallucinations, and preserved stamps/signatures.

## Performance
- Enhancement processing should complete in < 500ms for a 12MP image on a mid-range device.
- Memory usage should be optimized by recycling intermediate `Mat` objects in OpenCV.

## Failure Cases
- If an OpenCV operation fails (e.g., memory exhaustion), catch the exception, log the error, and fallback to `ORIGINAL` mode (returning the source image).

## Acceptance Criteria
- `EnhancementEngine` successfully processes images using `ORIGINAL`, `NATURAL`, and `CLEAN` modes.
- Processing is completely non-destructive to the source image.
- Handwriting, pencil marks, and stamps are distinctly visible after `NATURAL` and `CLEAN` enhancement.
- No generated artifacts or hallucinated content appear in the output.
- Performance meets latency targets.

## Git Checkpoint
`feat(enhancement): Implement Natural and Clean enhancement pipelines`

## Risks
- Tuning OpenCV pipelines to work well across all lighting conditions is difficult.
- OOM errors during high-resolution matrix operations.

## Open Questions
- What are the exact default values for `EnhancementParameters` for each mode based on Spike S05 results?
- Do we need to downsample the image slightly before complex filtering to meet the 500ms target, or process at full resolution?
