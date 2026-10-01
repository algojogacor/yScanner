# Handoff Report: Architecture & Technical Plans Mining

**Agent:** `spec_miner_survey_2`  
**Working Directory:** `d:\Projects\pdfscanner\.agents\teamwork\spec_miner_survey_2`  
**Parent Agent:** `teamwork_preview_orchestrator` (`d5a8c364-b3d4-4fee-8f48-03456ac7fd55`)  
**Date:** 2026-10-01  
**Handoff Type:** Hard (Task Complete)

---

## 1. Observation

1. **Original Request:**
   - File: `d:\Projects\pdfscanner\.agents\teamwork\ORIGINAL_REQUEST.md` (lines 11–26).
   - Specifies R1 (Project Foundation & Build System with 11 modules: `:app`, `:camera`, `:detection`, `:geometry`, `:processing`, `:domain`, `:data`, `:pdf`, `:import`, `:common`, `:test-fixtures`), R2 (CameraX, 7 coordinate spaces, ML segmentation, tracking, auto capture), R3 (Boundary extraction → outer envelope → quad fitting → corner refinement, perspective correction, quality metrics, enhancement, Two Page dewarping), R4 (PageObject, Page Manager, session persistence, gallery import, streaming PDF), and R5 (Memory budget $\le$ 600–700 MB normal, $\le$ 800 MB peak, 20-page sessions without unbounded growth).

2. **System Architecture Document:**
   - File: `d:\Projects\pdfscanner\ARCHITECTURE.md` (2,659 lines, 46,122 bytes).
   - Line 78–86: Dependency flow points inward: UI → Application / Use Cases → Domain → Infrastructure / Implementations.
   - Line 128: "Rendered bitmap bukan source of truth." (PageObject is authoritative).
   - Line 224–286: Module structure definition.
   - Line 733–764: Specification of the 7 coordinate systems: Sensor, ImageAnalysis, Preview/View, Normalized, ImageCapture, Processed-image, and PDF.
   - Line 830–850: Geometry engine components: `BoundaryExtractor`, `OuterEnvelopeEstimator`, `QuadrilateralFitter`, `CornerRefiner`, `PerspectiveEstimator`, `DewarpEngine`, `CoordinateMapper`.
   - Line 876–893: Complex/concave geometry resolution to enclosing quadrilateral.
   - Line 923–963: Two Page pipeline requiring gutter detection, curvature estimation, and mandatory non-linear dewarping.
   - Line 1056–1078: Non-destructive image enhancement (`ImageEnhancer`).
   - Line 1421–1443: PDF rendering streaming memory model ($O(1)$ memory, no simultaneous retention of all page bitmaps).
   - Line 2172–2224: Product invariants 1 to 12.

3. **Master Implementation Plan:**
   - File: `d:\Projects\pdfscanner\plans/000-master-plan.md` (843 lines, 31,527 bytes).
   - Line 116–163: Full milestone sequence M00 through M22.
   - Line 225–251: Critical hard dependencies table.
   - Line 264–275: Critical path and end-to-end vertical slice sequence.
   - Line 427–450: Spikes S01 to S06 gating respective milestones.
   - Line 537–551: Detailed performance and memory budgets.

4. **Detailed Milestone & Spike Plans:**
   - Directory: `d:\Projects\pdfscanner\plans/` contains 24 files: `000-master-plan.md`, `001-project-foundation.md` through `023-release-hardening.md`, plus `plans/spikes/` containing 6 spike documents (`S01-model-selection.md` through `S06-roi-decoding.md`).
   - Each plan specifies components, data flow, implementation steps, testing, failure cases, and acceptance criteria.

5. **Agent Operating Rules:**
   - File: `d:\Projects\pdfscanner\AGENTS.md` (lines 142–148, 252–260, 310–330, 420–460).
   - Enforces memory rules (prefer file reference + small metadata, never retain full bitmaps of all pages), threading (no heavy processing on main thread), non-destructive enhancement, and explicit release of native OpenCV `Mat` resources.

---

## 2. Logic Chain

1. **Module Layout Synthesis:**
   - Observation 1 and Observation 2 define the target modules.
   - The flat layout (`:app`, `:camera`, `:detection`, `:geometry`, `:processing`, `:domain`, `:data`, `:pdf`, `:import`, `:common`, `:test-fixtures`) specified in `ORIGINAL_REQUEST.md` and `001-project-foundation.md` fulfills the logical layering of `ARCHITECTURE.md` while avoiding excessive Gradle nested subproject complexity.
   - Inward-pointing dependencies ensure `:domain` has zero Android SDK dependencies, enabling lightning-fast JVM unit testing for domain logic, geometry math, and session states.

2. **Coordinate Space Precision:**
   - Observation 2 (§18–19) and Observation 4 (`003-coordinate-system.md`) explicitly identify 7 distinct spaces: Sensor, ImageAnalysis, Preview/View, Normalized, ImageCapture, Processed, and PDF.
   - Because CameraX preview scaling (`FILL_CENTER`/`FIT_CENTER`), sensor rotation (0° to 270°), and capture aspect ratios (4:3 vs 16:9) do not align trivially, a central `CoordinateTransformer` operating via 2D affine and projective matrices is necessary. Naive width/height scaling is mathematically incorrect and strictly forbidden.

3. **Detection, Geometry, and Refinement Pipeline:**
   - Observation 2 (§9–11, §20–26) and Observation 4 (`004`, `005`, `008`, `009`, `010`, `016`, `017`) dictate the two-stage execution:
     - Real-time detection runs at low resolution ($\le 50\text{ ms}$) generating an initial enclosing quad, stabilized temporally via a One Euro Filter.
     - Post-capture processing runs at high resolution using `BitmapRegionDecoder` on 200–400 px ROIs for sub-pixel corner refinement. This caps peak memory during corner extraction to $<20\text{ MB}$, completely avoiding a $48\text{ MB}$ uncompressed ARGB full-image decode.
     - Book spreads require dedicated gutter extraction, curvature mapping, and sequential `remap()` dewarping (processing left page, releasing buffers, then processing right page).

4. **Persistence and Streaming PDF Rendering:**
   - Observation 2 (§36–44) and Observation 4 (`015`, `019`, `020`) define the persistence and export strategy.
   - Incremental Room DB updates guarantee recovery from process death.
   - PDF export processes one page at a time: source load → geometry → enhance → draw to PDF page → `bitmap.recycle()`. This guarantees $O(1)$ memory scaling regardless of whether the document has 2 pages or 50 pages.

5. **Milestone Sequencing:**
   - Combining Observation 3 (M00–M22, S01–S06) with module boundaries yields an unambiguous roadmap.
   - The critical path starts with M00 (Foundation) → M01 (Camera) → M02 (Coordinates) → M03 (Detector) → M04 (Geometry) → M05 (Target) → M06 (Tracking) → M08 (Refinement) → M09 (Perspective) → M11 (Enhancement) → M12 (Domain) → M13 (Page Manager) → M18 (PDF).

---

## 3. Caveats

- **Visual Design Scope:** As specified in `ARCHITECTURE.md` §1 and `AGENTS.md` §4, final visual design, animations, and color palettes are deliberately excluded. UI specifications are restricted to functional Compose scaffolds.
- **Model Weights:** The repository is greenfield; no pre-trained TFLite model file is currently checked in. Spike S01 must be executed before M03 to finalize model architecture and asset bundling.
- **Hardware Variation:** OpenCV JNI performance and CameraX sensor orientations may vary across OEM devices (Samsung, Xiaomi, etc.). Device capability queries and safe fallback defaults have been specified to mitigate this.

---

## 4. Conclusion

The architecture, subsystem interfaces, mathematical contracts, and milestone plan for yScanner (LocalScan) have been mined and synthesized into:
- `d:\Projects\pdfscanner\.agents\teamwork\spec_miner_survey_2\arch_report.md`

All 6 deliverable areas requested by the orchestrator have been thoroughly addressed:
1. Module boundaries and dependency graph (11 modules).
2. Key interfaces and data contracts between modules.
3. Coordinate transformation architecture (the 7 spaces).
4. Detection, geometry, and processing pipeline designs.
5. Persistence and PDF streaming architecture.
6. Proposed milestone structure based on module boundaries and plan documents.

The project is structurally primed for implementation starting with M00.

---

## 5. Verification Method

To independently verify the findings in this report:

1. **Inspect Report Content:**
   - Review `d:\Projects\pdfscanner\.agents\teamwork\spec_miner_survey_2\arch_report.md` for exhaustive coverage of all subsystems.
2. **Inspect Cross-References:**
   - Compare module specifications in `arch_report.md` §1 with `plans/001-project-foundation.md` and `ORIGINAL_REQUEST.md`.
   - Compare the 7 coordinate spaces in `arch_report.md` §3 with `ARCHITECTURE.md` §18 and `plans/003-coordinate-system.md`.
   - Compare the streaming PDF memory guarantees in `arch_report.md` §5 with `ARCHITECTURE.md` §42 and `plans/019-pdf-renderer.md`.
3. **Invalidation Conditions:**
   - If any module in `:domain` requires Android framework imports (`android.*`), this indicates a violation of the pure Kotlin domain invariant.
   - If PDF page dimensions are found to influence document edge detection, this violates Invariant 2.
   - If PDF rendering loads all page bitmaps into memory at once, this violates the $O(1)$ memory invariant.
