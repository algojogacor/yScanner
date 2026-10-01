# S05: Enhancement Pipeline

> **Gate:** Must complete before M11 (Enhancement)

## Hypothesis

A pipeline combining CLAHE (Contrast Limited Adaptive Histogram Equalization), bilateral filtering, and gamma correction can produce Natural enhancement quality comparable to commercial scanners, while a pipeline adding local adaptive thresholding with ink-aware masking can produce Clean enhancement that preserves handwriting and pencil marks.

## Alternatives

### Natural Enhancement Pipelines

| # | Pipeline | Description |
|---|----------|-------------|
| N1 | **CLAHE + bilateral + gamma** | CLAHE for local contrast, bilateral for noise reduction while preserving edges, gamma for brightness |
| N2 | **Histogram equalization + Gaussian blur** | Simple global equalization + light smoothing |
| N3 | **Retinex-based (SSR/MSR)** | Single/Multi-Scale Retinex for illumination normalization |
| N4 | **Homomorphic filtering** | Frequency-domain illumination normalization |

### Clean Enhancement Pipelines

| # | Pipeline | Description |
|---|----------|-------------|
| C1 | **Adaptive threshold + morphological cleanup + ink masking** | Sauvola/Niblack threshold, morphological open/close, preserve colored ink via HSV masking |
| C2 | **Background estimation + subtraction + contrast** | Estimate background via large-kernel blur, subtract, enhance foreground |
| C3 | **Division-based shadow removal + CLAHE** | Divide image by blurred version (removes shadows), then CLAHE for contrast |
| C4 | **GrabCut-inspired foreground separation** | Separate text/content from background, clean background, recombine |

## Test Corpus

Minimum 25 images, post-perspective-correction:

| Category | Count | Purpose |
|----------|-------|---------|
| Typed text on white paper | 5 | Baseline quality |
| Handwritten (pen) | 3 | Verify handwriting preservation |
| Handwritten (pencil) | 3 | Verify faint pencil preservation |
| Colored ink | 2 | Verify color preservation |
| Shadow on document | 3 | Verify shadow removal |
| Glare on document | 2 | Verify glare handling |
| Uneven lighting | 3 | Verify illumination normalization |
| Mixed content (text + images + stamps) | 2 | Verify diverse content handling |
| Low contrast | 2 | Verify enhancement of faint content |

## Metrics

| Metric | Target | Description |
|--------|--------|-------------|
| Text readability (SSIM vs reference) | ≥ 0.85 | Structural similarity to manually-enhanced reference |
| Handwriting preservation | 100% | No handwritten strokes lost |
| Pencil preservation | 100% | No pencil marks lost (assessed at 2x zoom) |
| Color preservation (ΔE) | ≤ 10 | CIE ΔE2000 color difference for colored elements |
| Shadow reduction | ≥ 70% | Reduction in shadow area brightness variance |
| Processing time | ≤ 1 second | Per page at full resolution |
| Peak memory | ≤ 150 MB additional | Memory above baseline for enhancement |
| Artifact rate | ≤ 5% | Visible processing artifacts |

## Procedure

1. **Implement each candidate pipeline** as a standalone function:
   - Input: perspective-corrected Bitmap
   - Output: enhanced Bitmap
   - Use OpenCV for all operations

2. **Parameter tuning per pipeline:**
   - CLAHE: clipLimit (1.0-4.0), tileGridSize (4×4 to 16×16)
   - Bilateral: d (5-15), sigmaColor (50-150), sigmaSpace (50-150)
   - Gamma: value (0.7-1.5)
   - Adaptive threshold: blockSize (11-51), C (2-15)
   - Use a subset of test corpus for tuning (not full evaluation set)

3. **Natural evaluation:**
   - Run each Natural pipeline on full test corpus
   - Measure: readability SSIM, color preservation, processing time
   - Visual assessment: does the output look "natural" and improved?

4. **Clean evaluation:**
   - Run each Clean pipeline on full test corpus
   - Measure: readability SSIM, handwriting/pencil preservation, shadow reduction
   - Visual assessment at 2x zoom: are faint marks preserved?
   - Specific test: pencil marks must remain visible after Clean

5. **Compare and select**

## Decision Rule

### Natural:
1. Must preserve handwriting and pencil at 100%
2. Must process within 1 second
3. Among pipelines meeting targets: prefer highest readability SSIM
4. Prefer simpler pipeline if quality difference ≤ 3%

### Clean:
1. **Must preserve pencil marks at 100%** — this is non-negotiable
2. Must not make black-and-white so aggressive that faint information disappears
3. Must process within 1 second
4. Among pipelines meeting targets: prefer best shadow reduction
5. Must maintain color awareness (colored ink stays colored, not binarized)

## Result / Decision Record

> **Status:** PENDING
> **Selected Natural pipeline:** TBD
> **Selected Clean pipeline:** TBD
> **Optimal parameters:** TBD
> **Rationale:** TBD
