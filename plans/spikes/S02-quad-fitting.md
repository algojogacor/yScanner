# S02: Quadrilateral Fitting Algorithm

> **Gate:** Must complete before M04 (Geometry Engine)

## Hypothesis

A minimum-area bounding quadrilateral algorithm applied to a convex hull of the document boundary will produce more accurate document enclosures than simpler approaches (rotated bounding box, contour approximation), while remaining fast enough for real-time use (< 5ms per fit).

## Alternatives

| # | Algorithm | Description | Complexity |
|---|-----------|-------------|------------|
| A | **OpenCV minAreaRect** | Minimum area rotated rectangle (4 corners) | O(n log n) |
| B | **OpenCV approxPolyDP** | Douglas-Peucker contour simplification to 4 points | O(n) |
| C | **Convex hull → minimum-area quadrilateral** | Custom: compute convex hull, then find the 4-sided polygon with minimum area enclosing it | O(n²) |
| D | **Convex hull → line merging** | Group hull edges by angle, merge into 4 dominant lines, intersect for corners | O(n log n) |
| E | **Hough line-based** | Detect dominant lines in edge map, intersect 4 strongest orthogonal-ish lines | O(n) |

## Test Corpus

Minimum 30 images with ground-truth quadrilateral annotations:

| Category | Count | Characteristic |
|----------|-------|----------------|
| Clean rectangle | 5 | Near-perfect rectangle, easy case |
| Moderate perspective | 5 | Typical scanning angle |
| Extreme perspective | 5 | Very oblique viewing angle |
| Rounded corners | 3 | Documents with rounded edges |
| Concave/complex | 5 | L-shaped, irregular outline |
| Multiple documents | 3 | Must fit correct target |
| Small documents | 4 | Cards, receipts (small relative to frame) |

All images must have pre-computed segmentation masks and boundary points.

## Metrics

| Metric | Target | Description |
|--------|--------|-------------|
| Corner error (px) | ≤ 8 px at 640×480 | Euclidean distance from predicted to ground-truth corners |
| Quad IoU | ≥ 0.90 | Intersection-over-union with ground-truth quadrilateral |
| Fitting latency | ≤ 5 ms | Time to fit one quadrilateral from boundary points |
| Enclosure rate | 100% | All ground-truth document pixels inside predicted quad |
| Excess area | ≤ 15% | Area of predicted quad beyond document boundary |

## Procedure

1. For each test image: extract boundary from ground-truth mask
2. Compute convex hull of boundary
3. Apply each fitting algorithm to the hull
4. Measure: corner error, IoU, excess area, enclosure rate, latency
5. Aggregate results across test corpus
6. Visual inspection of worst cases per algorithm

## Decision Rule

1. **Must achieve 100% enclosure rate** — the quad must contain all document pixels (this is a product invariant)
2. Among algorithms with 100% enclosure: prefer lowest corner error
3. If corner error is similar (within 2px): prefer lower excess area
4. If quality is similar: prefer simpler/faster algorithm
5. If convex hull + minimum-area quad is clearly best but slow: investigate optimization or approximation

## Result / Decision Record

> **Status:** PENDING
> **Selected algorithm:** TBD
> **Rationale:** TBD
