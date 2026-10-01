# M20: Performance & Memory Optimization

## Objective
Optimize memory usage and processing performance to meet strict resource budgets, ensuring stability during long scanning sessions (e.g., 20+ pages) without out-of-memory errors or unacceptable latency. Establish benchmarks and automated tests for memory footprint and performance metrics.

## Product Requirements
- PRD.md §3.3 (Memory target)
- PRD.md §22 (Memory strategy)
- PRD.md §24 (Performance requirements)

## Architecture References
- ARCHITECTURE.md §14
- ARCHITECTURE.md §17
- ARCHITECTURE.md §45-53

## Current State
All major features (scanning, enhancement, ML models, PDF export) are implemented but may not be fully optimized for memory and performance. Unbounded memory growth, excessive native memory usage, or thermal throttling could be present during sustained use.

## Scope
- Memory profiling and optimization for Java heap, native heap (OpenCV Mats, Bitmaps), and ML models.
- Performance benchmarking for inference, analysis FPS, UI latency, geometry correction, enhancement, and PDF export.
- Implementation of thermal monitoring and dynamic performance throttling.
- Device capability detection for adaptive quality/resolution.
- Memory leak detection and fix (ensuring OpenCV Mats and ImageProxies are released promptly).

## Non-Goals
- Changing the core ML models or architectural logic (unless explicitly required for memory reduction).
- Modifying UI design or user flows.

## Dependencies
- M14 (PDF Generation & Export)
- M16 (Advanced Enhancement Filters)
- M19 (End-to-End Workflow & Polish)

## Components
- `com.yscanner.core.memory.MemoryManager` (New/Updated)
- `com.yscanner.core.performance.ThermalMonitor` (New)
- `com.yscanner.core.performance.DeviceCapabilities` (New)
- `com.yscanner.scanner.ml.DocumentDetector`
- `com.yscanner.scanner.processing.ImageEnhancer`
- `com.yscanner.pdf.PdfGenerator`
- `com.yscanner.app.YScannerApplication`

## Data Flow
- **Memory Management**: Images/Mats enter processing pipelines -> Bitmaps/Mats are pooled/reused -> Processed data saved to disk -> In-memory buffers aggressively cleared via `try/finally`.
- **Thermal & Performance**: System thermal state -> `ThermalMonitor` -> `DocumentDetector` (adjusts FPS/inference rate) and `DeviceCapabilities` (adjusts resolutions).

## Implementation Steps
1. Create `com.yscanner.core.performance.DeviceCapabilities` to detect RAM class and CPU capabilities, establishing memory budgets and resolution limits.
2. Implement `com.yscanner.core.performance.ThermalMonitor` to listen to system thermal states (API 29+ `PowerManager.OnThermalStatusChangedListener`).
3. Update `DocumentDetector` and CameraX analyzers to hook into `ThermalMonitor` and throttle frame rate/inference frequency if thermal state degrades.
4. Review and refactor all OpenCV usage (e.g., `ImageEnhancer`, geometry correction) to ensure `Mat.release()` is explicitly called within `try/finally` blocks.
5. Review CameraX `ImageAnalysis.Analyzer` implementations to guarantee `ImageProxy.close()` is called deterministically.
6. Implement Bitmap pooling/reuse in processing pipelines where applicable to reduce GC churn.
7. Refactor `PdfGenerator` to strictly guarantee page-by-page rendering with no multiple full-res bitmaps loaded simultaneously.
8. Set up AndroidX Macrobenchmark or automated integration tests to measure the specified performance targets.
9. Create a specific 20-page session simulation test to measure memory growth over time.

## Testing
- `MemoryLeakTest`: Run a 20-page scan cycle and assert heap usage does not grow unboundedly (using LeakCanary in debug builds or heap dumps).
- `PerformanceBenchmarkTest`: Macrobenchmark measuring ML inference latency, analysis FPS, geometry correction, enhancement, and PDF generation times.
- `ThermalThrottlingTest`: Mock thermal states and assert frame rate reduction in analysis pipelines.
- `ResourceReleaseTest`: Unit tests ensuring OpenCV Mats and Bitmaps are closed/recycled in all edge cases.

## Validation
- Monitor memory usage in Android Studio Profiler during a 20-page capture and export session. Peak memory must stay below 800 MB on an 8 GB device.
- Verify through benchmarks that inference latency is ≤ 50ms, analysis FPS ≥ 15, and PDF export (10 pages) is ≤ 25s.
- Ensure no OutOfMemoryError occurs during extensive stress testing.

## Performance
- Target Memory: Normal workflow ≤ 600-700 MB, peak ≤ 800 MB (8 GB RAM device).
- ML inference latency: ≤ 50ms.
- Analysis FPS: ≥ 15 FPS.
- Overlay latency: ≤ 2 frames.
- Full-res geometry: ≤ 500ms.
- Enhancement: ≤ 1 second.
- Capture-to-preview: ≤ 3 seconds.
- PDF per-page render: ≤ 2 seconds.
- PDF export (10 pages): ≤ 25 seconds.

## Failure Cases
- Extreme memory pressure: Safely evict non-essential caches, gracefully fail current operation, and show "Out of memory, try reducing resolution" dialog instead of crashing.
- Severe thermal throttling: Reduce preview FPS, pause analysis, warn user.

## Acceptance Criteria
- 20-page scan and export session completes without OOM on target devices.
- All performance benchmarks meet the required targets.
- OpenCV Mats and ImageProxies are demonstrably released promptly.
- App adapts to thermal pressure by reducing load.

## Git Checkpoint
`feat: M20 - Implement comprehensive memory and performance optimizations`

## Risks
- Native memory leaks from OpenCV can be hard to track and profile compared to Java heap.
- Varying thermal throttling behaviors across different OEM devices.

## Open Questions
- Should we implement a custom Bitmap pool or rely on existing libraries (e.g., Glide's bitmap pool) if available?
- What are the precise graceful degradation paths for low-RAM (e.g., 2 GB or 3 GB) Android Go devices?
