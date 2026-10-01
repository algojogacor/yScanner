# S04: Dewarp Strategy

> **Gate:** Must complete before M16 (Dewarp Pipeline)

## Hypothesis

A geometric model-based dewarping approach (cylindrical or parametric surface model + mesh remap) will provide acceptable page flattening quality for common book spreads without requiring a dedicated ML model for curvature estimation, and can be implemented within the project's memory and latency budgets.

## Alternatives

| # | Strategy | Description | Complexity |
|---|----------|-------------|------------|
| A | **Cylindrical surface model** | Assume pages curve as cylinder sections; estimate cylinder radius from gutter geometry; generate mesh remap | Medium |
| B | **Polynomial surface model** | Fit a polynomial surface (degree 3-4) to detected text lines or horizontal features; use as dewarp mesh | Medium-High |
| C | **Text-line detection + straightening** | Detect text lines via Hough/contours, compute per-line curvature, straighten each line | High |
| D | **ML-based curvature estimation** | Train a model to predict a per-pixel displacement field or curvature map from the spread image | Very High |
| E | **Bezier curve model** | Model gutter and page edges as Bezier curves; interpolate dewarp mesh between curves | Medium |

## Test Corpus

Minimum 20 book-spread images with varying conditions:

| Category | Count | Description |
|----------|-------|-------------|
| Flat open book | 3 | Nearly 180° opening, minimal curvature |
| Moderate curvature | 5 | Typical reading position (~140-160°) |
| Strong curvature | 5 | Book barely open (~100-130°) |
| Asymmetric curvature | 3 | Left and right pages with different curvature |
| Dark gutter | 2 | Deep binding, shadow in gutter |
| Thick book | 2 | Thick spine causing extreme curvature near binding |

Ground truth: manually dewarped reference images (or at minimum, human-assessed readability scores).

## Metrics

| Metric | Target | Description |
|--------|--------|-------------|
| Text line straightness | ≥ 85% horizontal | Percentage of text lines within ±2° of horizontal after dewarp |
| Character distortion | ≤ 5% | Character aspect ratio deviation from expected |
| Gutter artifact rate | ≤ 10% | Percentage of spreads with visible gutter artifacts |
| Processing latency | ≤ 2 seconds | Total dewarp time per page (full-res) |
| Peak memory | ≤ 200 MB additional | Memory above baseline for dewarp processing |
| Human readability score | ≥ 4/5 | Subjective assessment on 5-point scale |

## Procedure

1. **Implement each candidate strategy** as a standalone function:
   - Input: full-resolution spread image + gutter line + page boundaries
   - Output: dewarped left page, dewarped right page

2. **For cylindrical model (Strategy A):**
   - Estimate gutter position and curvature from edge/line features
   - Model each page as a section of a cylinder with radius R
   - Generate a mesh mapping from curved surface to flat surface
   - Apply OpenCV `remap()` with the generated mesh

3. **For polynomial model (Strategy B):**
   - Detect horizontal features (text lines, edges)
   - Fit polynomial curve to detected features
   - Generate dewarp mesh from polynomial surface
   - Apply OpenCV `remap()`

4. **For text-line model (Strategy C):**
   - Detect text lines using projection profiles or connected components
   - Estimate per-line curvature
   - Straighten each line independently
   - Handle inter-line spacing

5. **Benchmark each strategy:**
   - Run on full test corpus
   - Measure all metrics
   - Visual inspection of results

6. **Compare quality vs complexity trade-off**

## Decision Rule

1. **Must achieve ≥ 85% text line straightness** on moderate curvature images
2. **Must process within 2 seconds** per page
3. **Must fit within 200 MB additional memory**
4. Among strategies meeting targets: prefer simpler implementation
5. If cylindrical model (A) meets targets: prefer it (simplest)
6. If no strategy meets all targets: combine approaches (e.g., cylindrical for initial pass, text-line refinement for fine correction)
7. If geometric approaches fail: investigate ML-based (D) as fallback, accepting higher complexity

## Result / Decision Record

> **Status:** PENDING
> **Selected strategy:** TBD
> **Rationale:** TBD
