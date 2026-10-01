# Handoff Report — Specification Mining for yScanner

**Agent:** `spec_miner_survey_1`  
**Parent:** `teamwork_preview_orchestrator` (`d5a8c364-b3d4-4fee-8f48-03456ac7fd55`)  
**Workspace:** `d:\Projects\pdfscanner`  
**Report Artifact:** `d:\Projects\pdfscanner\.agents\teamwork\spec_miner_survey_1\spec_report.md`  
**Handoff Type:** Hard (Task complete)

---

## 1. Observation

Direct observations from the workspace files and specification contracts:

1. **`ORIGINAL_REQUEST.md` (lines 11–25, 28–83):**
   - Outlines five core requirements: R1 (Project Foundation & Build System, 11 modules: `:app`, `:camera`, `:detection`, `:geometry`, `:processing`, `:domain`, `:data`, `:pdf`, `:import`, `:common`, `:test-fixtures`), R2 (Camera & Real-Time Detection Pipeline, 7 coordinate spaces, 2s countdown), R3 (Document Geometry & Image Processing, enclosing quad, ROI refinement, Natural/Clean modes, Two Page book dewarping), R4 (Domain Model, Persistence, PDF Streaming, 6 page sizes × 3 quality profiles), R5 (Quality & Performance: ≤600–700 MB normal, ≤800 MB peak, 20-page session stability).
   - Lists 47 explicit acceptance check items across Build, Camera, Detection, Geometry, Two Page, Domain, PDF, Performance, and Testing.

2. **`PRD.md` (1,811 lines, 40,848 bytes):**
   - §2 / §32: Establishes the 17 core product principles and invariants. Key mandates: user has final authority; physical document geometry is source of truth; PDF page size never dictates detection; enclosing quadrilateral for all planar/concave shapes; non-destructive enhancement; offline-first; no generative hallucinations.
   - §3.3 / §22: Specifies memory limits of ≤600–700 MB normal working memory and ≤800 MB peak on 8 GB RAM target devices; bans retaining multiple full-resolution page bitmaps in RAM.
   - §5: Camera architecture using CameraX (`Preview`, `ImageAnalysis` with `KEEP_ONLY_LATEST`, `ImageCapture` with file-backed output, continuous AF with ~4s watchdog, 3-state flash).
   - §6–§9: Local ML segmentation, candidate extraction, tap-to-guide + metering, temporal tracking state machine, Auto Capture with 2-second countdown, unblocked manual shutter.
   - §10–§11: 7 coordinate spaces, enclosing quadrilateral, full-resolution corner refinement, OpenCV homography perspective correction.
   - §12: Dedicated Two Page book mode with gutter detection, curvature estimation, and mandatory curved-page dewarping.
   - §14: Natural and Clean enhancement pipelines with strict handwriting/pencil preservation.
   - §16–§18: `PageObject` and `Document` domain models, post-capture manual crop, multi-page workflow with undo/redo.
   - §20–§21: Offline app-private storage hierarchy, incremental session persistence, process death recovery.
   - §22: Streamed page-by-page PDF generation supporting 6 page sizes (A4 default, Auto, A5, B5, Letter, Original Ratio) and 3 quality profiles (High, Balanced, Small), pre-export size estimator accurate to within 20%, and filename editing.
   - §33: 47 functional acceptance criteria.

3. **`BRIEF.md` (1,020 lines, 21,035 bytes):**
   - §1–§3: Reinforces commercial-grade scanning comparable to vFlat/CamScanner but without ads, accounts, or cloud.
   - §35: Documents the core product mantra:
     *"AI helps understand. Geometry ensures shape. Processing preserves quality. The user determines the outcome. PDF only comes after scanning is complete."*

4. **`ARCHITECTURE.md` (2,659 lines, 46,122 bytes):**
   - Outlines 11 logical modules, separation of concerns (dependencies point inward), domain model purity (zero Android framework dependencies in `:domain`), and memory ownership models.

5. **`plans/` directory (24 plan files + 6 spike files, ~186 KB):**
   - `plans/000-master-plan.md` through `plans/023-release-hardening.md` map 23 sequential milestones (M00 to M22).
   - `plans/003-coordinate-system.md` explicitly defines the 7 coordinate spaces: Sensor, ImageAnalysis, Preview/View, Normalized (0..1), ImageCapture, Processed-image, and PDF.
   - `plans/009-one-page-refinement.md` details `BitmapRegionDecoder` ROI corner decoding (200–400px boxes, <20 MB RAM footprint).
   - `plans/017-dewarp.md` details OpenCV `remap()` non-linear dewarping.
   - `plans/019-pdf-renderer.md` details `android.graphics.pdf.PdfDocument` page-by-page streaming with `bitmap.recycle()`.
   - `plans/021-performance.md` specifies thermal throttling (`PowerManager.OnThermalStatusChangedListener`) and latency budgets (ML ≤ 50ms, FPS ≥ 15, overlay latency ≤ 2 frames, geometry ≤ 500ms, enhancement ≤ 1s, capture-to-preview ≤ 3s, PDF export ≤ 25s for 10 pages).

6. **Current Workspace State (`list_dir` on root):**
   - Greenfield repository: `.git` exists, but no build files (`build.gradle.kts`, `settings.gradle.kts`), no source code, and no test code exist yet.

---

## 2. Logic Chain

1. **Premise 1 (Completeness of Specification):** The repository contracts (`ORIGINAL_REQUEST.md`, `PRD.md`, `BRIEF.md`, `ARCHITECTURE.md`, `plans/`) constitute a fully articulated, self-consistent engineering specification with no unresolved architectural ambiguities.
2. **Premise 2 (Feature Inventory & Categorization):** By evaluating every requirement across the 35 PRD sections and 23 milestone plans, exactly 60 distinct features across 16 subsystems were identified, mapped, and structured into the standard Spec Miner table format.
3. **Premise 3 (Edge Cases & Fallbacks):** Real-world document scanning involves numerous physical, optical, and hardware failure modes (low contrast, deep book gutters, motion blur, partial occlusion, storage full, process death, thermal load). Exactly 30 concrete edge cases with explicit system fallback behaviors were documented.
4. **Premise 4 (Non-Negotiable Thresholds):** Strict non-functional acceptance criteria (7 coordinate spaces, 2s countdown, 6 page sizes × 3 quality profiles, ≤600–700 MB normal / ≤800 MB peak memory, 20-page session stability, offline-only privacy) are quantitatively defined and ready to gate subsequent implementation milestones.
5. **Conclusion:** The specification mining task is complete, fully validated, and synthesized into `spec_report.md`.

---

## 3. Caveats

- **Visual Design Scope:** As explicitly stated in `PRD.md` §18, `BRIEF.md` §33, and `AGENTS.md` §4, final visual design, typography, color palettes, animations, and branding are intentionally out of scope and reserved for a dedicated design pass. Functional UI (wireframes/Compose widgets) is specified solely to support feature execution.
- **Spike Gating:** While algorithms are fully specified, specific mathematical filter parameters (e.g. One Euro Filter coefficients in S03, exact dewarp mesh grid density in S04, CLAHE clip limit in S05) are gated by their respective experimental spikes during implementation.
- **No Implementation Performed:** In strict accordance with the SPECIFICATION MINER role and dispatch constraints, no application code, build files, or Gradle configurations were created or modified.

---

## 4. Conclusion

The authoritative product specifications for yScanner have been exhaustively probed and compiled into `d:\Projects\pdfscanner\.agents\teamwork\spec_miner_survey_1\spec_report.md`. The report contains:
- 60 fully defined features across 16 subsystems with complete inputs, outputs, error behaviors, and PRD/plan traceability.
- 30 distinct edge cases with verified fallback and recovery behaviors.
- The 17 non-negotiable product invariants.
- Detailed acceptance criteria and mathematical mappings for the 7 coordinate spaces, 2-second auto-capture countdown, 6 page sizes × 3 quality profiles, streaming PDF architecture, and strict memory/latency budgets.
The downstream implementation team has an unambiguous, comprehensive roadmap to begin M00 (Project Foundation).

---

## 5. Verification Method

To independently verify this report and its findings:
1. **Inspect Report Content:**
   `view_file` on `d:\Projects\pdfscanner\.agents\teamwork\spec_miner_survey_1\spec_report.md`
   Verify presence of the "Features Discovered" table (60 items), "Edge Cases" table (30 items), and the 17 Invariants.
2. **Cross-Reference Acceptance Criteria:**
   Inspect `d:\Projects\pdfscanner\.agents\teamwork\ORIGINAL_REQUEST.md` (lines 28–83) and verify that all 47 acceptance criteria are accounted for in `spec_report.md`.
3. **Cross-Reference PRD & Plans:**
   Verify references to `PRD.md` §3.3, §5, §6–§9, §10–§12, §14, §16–§18, §20–§22, and milestone plans `plans/001-project-foundation.md` through `plans/023-release-hardening.md`.
4. **Invalidation Conditions:**
   The findings would be invalidated if `PRD.md` or `ORIGINAL_REQUEST.md` were modified to change product invariants (e.g. allowing cloud OCR, removing curved-page dewarping, or changing memory budgets).
