# S03: Temporal Filter Comparison

> **Gate:** Must complete before M06 (Temporal Tracking)

## Hypothesis

One Euro Filter will provide the best trade-off between jitter reduction and responsiveness for corner tracking, compared to exponential smoothing and Kalman filtering, because it adapts its smoothing strength based on the rate of change.

## Alternatives

| # | Filter | Description | Parameters |
|---|--------|-------------|------------|
| A | **Exponential smoothing** | `output = α × input + (1-α) × previous` | α (smoothing factor) |
| B | **One Euro Filter** | Speed-adaptive low-pass filter; smooths more when slow, less when fast | minCutoff, beta, dCutoff |
| C | **Kalman filter (2D per corner)** | State estimation with process/measurement noise models | processNoise, measurementNoise |
| D | **Double exponential smoothing** | Exponential smoothing with trend component | α, β |
| E | **Moving average (N frames)** | Simple average of last N frames | windowSize |

Each filter is applied **independently per corner** (8 values: 4 corners × 2 coordinates).

## Test Corpus

Test data consists of recorded detection sequences (not static images):

| Scenario | Frames | Description |
|----------|--------|-------------|
| Static document | 120 | Camera and document stationary — measures jitter reduction |
| Slow pan | 120 | Camera slowly panning over document — measures tracking responsiveness |
| Fast movement | 60 | Quick camera movement — measures lag vs smoothing |
| Detector dropout | 120 | Simulate detector missing every 5th frame — measures dropout tolerance |
| Target switch | 90 | Two documents, manual switch between them — measures switch response |
| Jittery detection | 120 | Artificially add Gaussian noise to corners — measures noise filtering |

Sequences can be:
- Recorded from actual device (preferred)
- Synthetically generated with known ground truth (for precise metrics)

## Metrics

| Metric | Target | Description |
|--------|--------|-------------|
| Jitter reduction | ≥ 60% | RMS corner displacement vs unfiltered, static scene |
| Tracking latency | ≤ 3 frames | Time for filtered output to converge within 5px of new stable position |
| Overshoot | ≤ 5 px | Maximum overshoot when target moves to new position |
| Dropout tolerance | ≥ 3 frames | Maintain reasonable output for N consecutive detector dropouts |
| Switch response | ≤ 5 frames | Time to converge on new target after explicit switch |
| Computational cost | ≤ 0.5 ms | Per-frame filter update time |

## Procedure

1. **Generate or record test sequences:**
   - For each scenario, produce a time-series of corner positions (4 corners × 2 coords × N frames)
   - Include ground truth where possible (for synthetic data)

2. **Implement each filter:**
   - Pure Kotlin implementation (no Android dependency)
   - Unit-testable with deterministic input

3. **Parameter tuning:**
   - For each filter, run grid search or manual tuning over reasonable parameter ranges
   - Optimize for: minimize jitter while keeping latency ≤ 3 frames
   - Record optimal parameters per filter

4. **Benchmark:**
   - Run each filter (with tuned parameters) on all test sequences
   - Record all metrics per scenario per filter
   - Aggregate across scenarios

5. **Visual comparison:**
   - Plot filtered vs unfiltered corner trajectories for each filter
   - Identify visual artifacts (overshoot, lag, oscillation)

## Decision Rule

1. **Must reduce jitter ≥ 60%** on static scenes
2. **Must respond within 3 frames** to genuine movement
3. Among filters meeting both: prefer lowest jitter with least latency
4. Tie-breaker: simplicity of implementation and tuning
5. If One Euro Filter meets targets: prefer it (hypothesis)
6. If no filter meets all targets: combine approaches (e.g., One Euro for smooth tracking, hard reset for target switch)

## Result / Decision Record

> **Status:** PENDING
> **Selected filter:** TBD
> **Optimal parameters:** TBD
> **Rationale:** TBD
