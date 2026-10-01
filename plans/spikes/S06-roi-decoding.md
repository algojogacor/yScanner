# S06: Full-Resolution ROI Decoding

> **Gate:** Must complete before M08 (One Page Full-Resolution Refinement)

## Hypothesis

Android's `BitmapRegionDecoder` can decode ROI regions around document corners from a full-resolution JPEG (4032×3024) in ≤ 50ms per corner with ≤ 20 MB peak memory, making full-image decode unnecessary for corner refinement.

## Alternatives

| # | Strategy | Description | Expected Memory |
|---|----------|-------------|-----------------|
| A | **BitmapRegionDecoder** | Android API: decode arbitrary JPEG region without full decode | ~2-5 MB per ROI |
| B | **Full decode + crop** | Decode entire image, create sub-bitmap for each corner | ~48 MB (full ARGB) |
| C | **Downsampled full decode** | Decode at 1/2 or 1/4 resolution, crop corners from downsampled | ~12-24 MB |
| D | **OpenCV ROI via imread with ROI** | Use OpenCV's native image loading with ROI specification | Varies |
| E | **libjpeg-turbo partial decode** | JNI to libjpeg-turbo for MCU-aligned partial JPEG decode | ~2-5 MB per ROI |

## Test Corpus

| Image Size | Resolution | Format | Count |
|------------|-----------|--------|-------|
| 12 MP | 4032×3024 | JPEG | 5 |
| 8 MP | 3264×2448 | JPEG | 3 |
| 16 MP | 4608×3456 | JPEG | 2 |
| 48 MP | 8000×6000 | JPEG | 1 (extreme case) |
| 12 MP | 4032×3024 | PNG | 1 (non-JPEG) |

## Metrics

| Metric | Target | Description |
|--------|--------|-------------|
| ROI decode time | ≤ 50 ms per corner | Time to decode one corner ROI |
| Total 4-corner time | ≤ 250 ms | Time to decode all 4 corners |
| Peak memory per ROI | ≤ 5 MB | Memory used for one ROI decode |
| Peak memory total | ≤ 25 MB | Memory for all 4 ROI decodes (including retained ROI bitmaps) |
| Image quality | lossless | ROI pixels identical to full-decode equivalent |
| API compatibility | API 24+ | Works on minSdk 24 |

## Procedure

1. **Prepare test images:**
   - Capture or obtain images at each resolution
   - Record known corner positions (4 corners per image)
   - Define ROI sizes: 200×200, 300×300, 400×400 pixels

2. **Strategy A (BitmapRegionDecoder):**
   ```kotlin
   val decoder = BitmapRegionDecoder.newInstance(inputStream, false)
   val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
   val roiBitmap = decoder.decodeRegion(Rect(x, y, x+w, y+h), options)
   ```
   - Test with ROI at each corner of each test image
   - Measure: decode time, memory, pixel correctness

3. **Strategy B (Full decode + crop):**
   - `BitmapFactory.decodeFile()` then `Bitmap.createBitmap(full, x, y, w, h)`
   - Measure for comparison baseline

4. **Strategy C (Downsampled):**
   - `BitmapFactory.Options.inSampleSize = 2` or `4`
   - Measure quality loss vs full resolution

5. **Compare:**
   - Create comparison table: time × memory × quality × compatibility
   - Verify BitmapRegionDecoder handles edge cases (ROI at image boundaries)

6. **Edge cases:**
   - ROI extends beyond image boundary (clip to boundary)
   - Very small images (smaller than ROI size)
   - Non-JPEG formats (PNG, HEIF)
   - Rotated images (EXIF orientation)

## Decision Rule

1. **BitmapRegionDecoder (A) is the default choice** if it meets all targets on API 24+
2. If BitmapRegionDecoder has issues with specific formats: use full decode (B) as fallback for those formats
3. If 48 MP images cause issues: use downsampled decode (C) for images above a threshold
4. Memory is the primary selection criterion — full decode (B) is only acceptable if ROI approaches are infeasible

## Result / Decision Record

> **Status:** PENDING
> **Selected strategy:** TBD
> **ROI size decision:** TBD
> **Fallback strategy:** TBD
> **Rationale:** TBD
