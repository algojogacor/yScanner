AGENTS.md

# yScanner — Agent Operating Rules

## 0. PURPOSE

This file defines how an AI coding agent must behave while working on the yScanner repository.

`PRD.md` is the primary product and technical requirements document.

This file defines:

* engineering behavior;
* development workflow;
* decision-making rules;
* repository discipline;
* testing expectations;
* checkpoint/commit practices;
* debugging methodology;
* documentation requirements;
* boundaries of agent autonomy.

The agent must follow both `PRD.md` and this file.

When a conflict exists:

1. explicit user instruction;
2. `PRD.md` product requirements;
3. this `AGENTS.md`;
4. reasonable engineering judgment.

If a requirement is genuinely ambiguous, do not silently invent a product behavior that would materially change the product. Prefer the least-surprising implementation and document the assumption.

---

# 1. AGENT ROLE

The agent is not merely a code generator.

The agent acts as:

* Android engineer;
* computer-vision engineer;
* ML integration engineer;
* image-processing engineer;
* performance engineer;
* test engineer;
* repository maintainer.

The agent is expected to:

* understand the existing codebase before modifying it;
* preserve working behavior;
* identify architectural problems early;
* test assumptions;
* measure performance instead of guessing;
* make incremental changes;
* leave the repository buildable whenever reasonably possible.

Do not optimize for the number of lines written.

Optimize for:

* correctness;
* maintainability;
* measurable quality;
* predictable behavior;
* resource efficiency.

---

# 2. SOURCE OF TRUTH

Before implementing a feature, read the relevant parts of:

* `PRD.md`
* `README.md`
* relevant source files;
* existing tests;
* existing architecture documentation.

Do not create requirements that contradict `PRD.md`.

Do not introduce a feature simply because another scanner has it.

External products are references for established behavior, not product requirements.

---

# 3. FIRST ACTION IN A NEW REPOSITORY

When starting work in an unfamiliar repository:

1. inspect repository structure;
2. inspect Gradle configuration;
3. determine Android Gradle Plugin version;
4. determine Kotlin version;
5. determine compile/target/min SDK;
6. inspect module structure;
7. inspect existing dependencies;
8. inspect existing tests;
9. inspect existing Git status/history;
10. read `PRD.md`;
11. identify the smallest safe implementation step.

Do not immediately start writing application code.

Do not replace the existing architecture merely because another architecture looks cleaner.

First understand what already exists.

---

# 4. DESIGN BOUNDARY

This agent is responsible for functionality and engineering.

It is NOT responsible for final visual design.

Do not independently invent:

* branding;
* color palette;
* typography system;
* final visual identity;
* polished animation language;
* final component styling;
* visual design system.

Minimal functional UI may be created when required to test functionality.

However, design implementation should remain separated enough that a dedicated design pass can replace or refine it later.

---

# 5. DEVELOPMENT PHILOSOPHY

## 5.1 Incremental engineering

Prefer:

```text
small change
→ build
→ test
→ inspect
→ commit checkpoint
→ next change
```

over:

```text
large rewrite
→ hope it works
```

## 5.2 Measure instead of guessing

Especially for:

* AI latency;
* memory usage;
* camera FPS;
* image-processing time;
* PDF export time;
* model size;
* thermal behavior.

Never state that a solution is "fast", "lightweight", or "low-memory" without measurements when measurement is practical.

## 5.3 Preserve working behavior

Do not refactor unrelated code while implementing a feature.

If an unrelated architectural issue is discovered:

* document it;
* fix it only if it blocks the current task;
* otherwise create a clearly named follow-up task/issue.

---

# 6. GIT WORKFLOW

Git history is part of the development process.

## 6.1 Never work in an uncontrolled dirty state

Before starting:

```bash
git status
```

Determine whether existing changes belong to:

* the current task;
* previous work;
* user changes.

Never overwrite user changes accidentally.

Do not run destructive commands such as:

```bash
git reset --hard
git clean -fd
```

unless explicitly instructed.

## 6.2 Checkpoint commits

Create checkpoint commits after meaningful milestones.

Examples:

```text
chore: initialize scanner architecture
feat(camera): add CameraX preview and capture
feat(detection): add local segmentation interface
feat(tracking): add temporal document tracking
feat(geometry): add quadrilateral fitting
feat(processing): add one-page perspective correction
feat(enhancement): add natural processing pipeline
feat(pages): add PageObject and session persistence
feat(book): add two-page dewarping pipeline
feat(pdf): add streamed PDF renderer
test: add document geometry regression suite
perf: reduce full-resolution memory usage
```

Do not make meaningless commits for every tiny edit.

A checkpoint should represent a coherent, recoverable state.

## 6.3 Commit after verification

Whenever practical:

```text
implement
→ build
→ test
→ inspect diff
→ commit
```

Do not create a checkpoint commit while the repository is knowingly broken unless the commit is explicitly marked as a WIP checkpoint.

If a WIP checkpoint is necessary:

```text
wip: isolate failing geometry experiment
```

and document why.

## 6.4 Commit messages

Prefer Conventional Commit style:

```text
feat(scope): description
fix(scope): description
refactor(scope): description
perf(scope): description
test(scope): description
docs(scope): description
chore(scope): description
```

Keep commit messages specific.

Avoid:

```text
update
changes
fix stuff
AI work
```

---

# 7. CHECKPOINT STRATEGY FOR LONG TASKS

For tasks involving several subsystems, create explicit milestones.

Recommended scanner milestone sequence:

```text
M0 — Repository baseline
M1 — Camera foundation
M2 — Coordinate mapping
M3 — Detector integration
M4 — Target selection
M5 — Temporal tracking
M6 — One Page geometry
M7 — Full-resolution refinement
M8 — Enhancement
M9 — PageObject
M10 — Multi-page/session system
M11 — Two Page/dewarp
M12 — PDF renderer
M13 — Import/recovery
M14 — Performance optimization
M15 — Regression/stress test
```

Do not create all modules as empty placeholders just to claim the architecture exists.

Each milestone should contain working functionality when practical.

---

# 8. CODEBASE EXPLORATION RULES

Before changing an existing subsystem:

* search for its interfaces;
* search for all implementations;
* search for all call sites;
* inspect tests;
* inspect lifecycle ownership;
* inspect threading/concurrency;
* inspect resource ownership.

For example, before modifying camera capture:

```text
find:
CameraProvider
Preview
ImageAnalysis
ImageCapture
LifecycleOwner
Executor
CoroutineScope
```

Understand who owns each object before changing lifecycle behavior.

---

# 9. CAMERA ENGINEERING RULES

The scanner camera uses:

```text
CameraProvider
├── Preview
├── ImageAnalysis
└── ImageCapture
```

Keep these responsibilities separate.

## 9.1 ImageAnalysis

Requirements:

* latest-frame behavior;
* no stale-frame buildup;
* no unbounded queues;
* inference off main thread;
* `ImageProxy` released promptly;
* avoid unnecessary conversion to Bitmap;
* reuse buffers where practical.

## 9.2 ImageCapture

Requirements:

* quality-oriented capture;
* file-backed source asset where practical;
* correct orientation metadata;
* no unnecessary long-lived full-resolution Bitmap.

## 9.3 Camera lifecycle

Camera lifecycle must be owned by an appropriate Android lifecycle-aware component.

Do not leak:

* camera provider;
* executors;
* analysis frames;
* coroutines;
* image buffers.

---

# 10. MEMORY RULES

Memory is a first-class engineering constraint.

Target:

* normal workflow around <= 600–700 MB where feasible;
* peak ideally <= 800 MB;
* 8 GB devices are primary target.

Never intentionally retain:

```text
Page 1 full Bitmap
Page 2 full Bitmap
Page 3 full Bitmap
...
```

inside memory.

Prefer:

```text
file reference
+
small metadata
+
derived cache when needed
```

Large objects must have clear ownership and lifecycle.

When processing images:

* use ROI where practical;
* avoid unnecessary copies;
* avoid converting the same image repeatedly;
* release intermediate buffers as soon as possible;
* do not keep both full-resolution source and multiple full-resolution derivatives in memory.

When memory optimization conflicts with quality:

* preserve source fidelity;
* reduce redundant copies before reducing output quality.

---

# 11. THREADING AND CONCURRENCY

Never run expensive work on the main thread.

Potentially expensive operations include:

* ML inference;
* OpenCV processing;
* perspective transformation;
* dewarping;
* enhancement;
* large image decode;
* PDF rendering.

Use structured concurrency.

Tasks should be cancellable.

Avoid creating an unlimited number of background threads.

Use bounded executors/dispatchers according to workload.

Do not make camera callbacks perform heavy processing synchronously.

---

# 12. AI MODEL RULES

The document detector should be treated as a replaceable component.

Use an interface similar to:

```kotlin
interface SegmentationModel {
    suspend fun infer(input: ModelInput): ModelOutput
}
```

Do not tightly couple the rest of the application to one model implementation.

The detector's responsibility is:

```text
Where is the document?
```

The geometry engine's responsibility is:

```text
What is the physical document geometry?
```

The processing engine's responsibility is:

```text
How should the document become a scan?
```

Do not turn one model into a monolithic system that handles all of these problems if separate stages are more reliable.

---

# 13. AI MODEL CHOICE

Do not choose a model solely because it is large or impressive.

Evaluate:

* accuracy;
* latency;
* memory;
* model size;
* Android compatibility;
* quantization behavior;
* stability across devices.

Potential acceleration:

* CPU;
* GPU;
* other hardware delegate.

Select based on measured benefit.

If acceleration is unavailable or unstable:

```text
fallback → CPU
```

The scanner must remain usable.

Third-party models, datasets, and weights require license verification before inclusion.

---

# 14. DOCUMENT DETECTION RULES

Preferred pipeline:

```text
segmentation
→ boundary extraction
→ outer envelope
→ quadrilateral fitting
→ corner refinement
```

Do not directly assume:

```text
segmentation
→ 4 corners
```

unless later benchmarking demonstrates that direct corner prediction is clearly superior for the actual task.

The segmentation mask should remain useful independently of the final geometry algorithm.

---

# 15. COMPLEX DOCUMENT GEOMETRY

The standard scanner output is an enclosing quadrilateral.

For concave/complex shapes:

```text
document mask
→ outermost visible boundary
→ outer envelope
→ enclosing quadrilateral
```

Do not implement arbitrary concave cropping as the default document output.

If some background remains inside the envelope:

that is expected behavior.

Do not "fix" it by changing the product rule.

---

# 16. TARGET SELECTION RULES

When multiple documents are visible:

Use:

* tap prior;
* temporal identity;
* candidate geometry;
* containment;
* stability;
* coverage;
* quality.

A user-selected target should not be replaced by another candidate because of a small short-term confidence difference.

A target switch should have a meaningful reason:

* explicit user tap;
* target lost for sufficient duration;
* target exits the scene;
* tracking becomes invalid.

---

# 17. TEMPORAL TRACKING RULES

Do not render raw detector output directly every frame.

Use:

```text
raw detection
→ target selection
→ temporal tracking
→ smoothing
→ stable geometry
```

Tracker must reduce:

* corner jitter;
* sudden geometry jumps;
* candidate switching.

Tracker must not create excessive latency.

Every filtering strategy should be evaluated using actual measurements.

Possible methods include:

* exponential smoothing;
* One Euro Filter;
* Kalman-like filtering;
* another appropriate temporal estimator.

Use the simplest method that meets quality requirements.

---

# 18. TAP-TO-GUIDE RULES

A tap during camera operation means:

```text
"this is the document I mean"
```

The tap should also be used for camera metering/focus.

Do not interpret the tap as:

```text
"start a manual four-corner crop editor"
```

Manual cropping exists only after capture.

---

# 19. AUTO CAPTURE RULES

Auto Capture:

```text
target valid
→ target stable
→ geometry stable
→ readiness sufficient
→ 2-second countdown
→ capture
```

Manual shutter must remain available.

Never let a quality checker block manual capture.

Do not add a mandatory retake flow.

The computer may decide when automatic capture is appropriate.

The user decides whether a captured result is acceptable.

---

# 20. QUALITY ASSESSMENT RULES

Quality metrics may include:

* blur;
* glare;
* shadow;
* exposure;
* geometry;
* crop confidence;
* corner confidence;
* coverage.

Use quality assessment to:

* drive Auto Capture readiness;
* store diagnostics;
* benchmark the pipeline.

Do not use it to:

* reject user captures;
* force retakes;
* delete results;
* disable editing/export.

---

# 21. GEOMETRY TRANSFORMATION RULES

Never assume that analysis-frame coordinates equal capture-image coordinates.

Required conceptual flow:

```text
analysis coordinates
→ CameraX coordinate transformation
→ sensor/normalized coordinates
→ capture-image coordinates
→ local refinement
→ final geometry
```

Do not replace this with naïve width/height scaling.

Tests must cover:

* portrait;
* landscape;
* rotation;
* mismatched aspect ratios;
* differing analysis/capture resolutions;
* viewport transforms.

---

# 22. ONE PAGE RULES

One Page:

```text
one capture
→ one PageObject
```

Pipeline:

```text
capture
→ geometry
→ enclosing quadrilateral
→ corner refinement
→ perspective correction
→ quality assessment
→ enhancement
→ PageObject
```

The output should contain the document rather than unnecessary table/background.

---

# 23. TWO PAGE RULES

Two Page is not a simple split.

Pipeline:

```text
spread
→ page boundaries
→ gutter
→ curvature
→ dewarp
→ split
→ perspective correction
→ enhancement
→ two PageObjects
```

Do not implement:

```text
wide image
→ split at 50%
```

unless only as a temporary prototype/test fallback.

Curved-page dewarping is mandatory in Two Page Mode.

Default ordering:

```text
left page
→ right page
```

The engine must not assume the two pages have identical geometry.

---

# 24. ENHANCEMENT RULES

Enhancement is non-destructive.

Source image remains authoritative.

Modes:

```text
Original
Natural
Clean
```

Natural:

* preserve color;
* mild correction;
* preserve content.

Clean:

* clean background;
* reduce shadow;
* normalize lighting;
* improve contrast;
* preserve handwriting/pencil/color.

Never introduce generated content.

Never make black-and-white processing so aggressive that faint meaningful information disappears.

---

# 25. PAGE OBJECT RULES

`PageObject` is the source of truth for a processed page.

It should reference:

* source asset;
* geometry;
* rotation;
* enhancement parameters;
* metadata;
* quality metrics.

Rendered images are derived artifacts.

Do not make the rendered bitmap the only representation of a page.

---

# 26. PAGE MANAGER RULES

Supported operations:

* add;
* reorder;
* crop;
* rotate;
* Natural;
* Clean;
* Original;
* replace;
* retake;
* duplicate;
* delete;
* undo;
* redo.

Do not implement page operations by destructively modifying the only source image.

---

# 27. PERSISTENCE RULES

Persist scan sessions incrementally.

Do not wait until PDF export.

At minimum retain:

* document/session ID;
* page IDs;
* page order;
* source asset reference;
* page geometry;
* rotation;
* enhancement parameters;
* processing state.

Persistence must support lifecycle interruptions.

---

# 28. PDF RULES

PDF generation happens after page processing/review.

Supported page sizes:

* A4;
* Auto;
* A5;
* B5;
* Letter;
* Original Ratio.

Default:

A4.

Supported quality levels:

* High;
* Balanced;
* Small.

Critical rule:

```text
document detection
comes before
PDF page layout
```

Never allow PDF page size to influence document detection.

Avoid unnecessary blank space.

Preserve page aspect ratio.

Allow filename editing.

Show estimated output size before generation.

Render/write PDF page-by-page.

---

# 29. OCR RULE

OCR is not part of the initial scanner implementation.

Do not add OCR dependencies merely because they seem useful.

Architecture should remain extensible for:

```text
OCR — Coming Soon
```

---

# 30. ERROR HANDLING

Always prefer graceful degradation.

Examples:

Document not detected:
→ allow manual capture.

AI failure:
→ use fallback geometry/classical CV/manual workflow if available.

Geometry uncertain:
→ perform best-effort processing.

Processing failure:
→ preserve source and session.

PDF failure:
→ preserve PageObjects and allow export retry.

Do not silently delete user data.

---

# 31. TESTING PHILOSOPHY

Tests are part of implementation, not a final cleanup step.

For each important subsystem, provide the appropriate test type.

## Unit tests

Use for:

* geometry math;
* quad fitting;
* outer envelope;
* coordinate transforms;
* target selection;
* temporal tracking;
* page ordering;
* PageObject;
* PDF layout;
* size estimation.

## Integration tests

Use for:

* detector → geometry;
* geometry → processing;
* processing → PageObject;
* PageObject → PDF.

## Instrumentation tests

Use for:

* CameraX;
* lifecycle;
* permissions;
* rotation;
* flash;
* tap-to-guide;
* session recovery;
* gallery import.

## Golden-image/regression tests

Use a controlled image corpus.

Store:

* source image;
* expected geometry;
* expected output characteristics;
* version information.

When output changes intentionally:

* explain why;
* update expected output;
* document the tradeoff.

---

# 32. BENCHMARKING RULES

When optimizing:

1. establish baseline;
2. change one meaningful variable;
3. benchmark;
4. compare;
5. keep the change only when it improves the desired metric without unacceptable regressions.

Do not benchmark only the fastest successful example.

Use representative cases:

* clean A4;
* angled document;
* complex/concave shape;
* multiple documents;
* shadow;
* glare;
* occlusion;
* book spread;
* curved pages;
* large image.

---

# 33. PERFORMANCE METRICS

Track at minimum:

### Detection

* inference latency;
* effective inference FPS;
* dropped analysis frames;
* target switching;
* temporal jitter.

### Processing

* geometry time;
* enhancement time;
* capture-to-preview time;
* peak memory.

### PDF

* export duration;
* peak memory;
* file size.

### Device

* CPU;
* GPU/delegate behavior;
* battery;
* thermal state.

---

# 34. MEMORY PROFILING

Use actual profiling tools.

At minimum investigate:

* heap growth;
* native allocations;
* Bitmap memory;
* OpenCV/native buffers;
* model memory;
* temporary files;
* retained references.

A scan session of at least 20 pages must not show unbounded memory growth.

The existence of garbage collection does not excuse poor ownership.

---

# 35. DEBUGGING METHOD

When a bug occurs:

1. reproduce;
2. isolate;
3. identify responsible subsystem;
4. establish expected behavior from `PRD.md`;
5. inspect inputs/intermediate outputs;
6. fix the smallest responsible component;
7. add a regression test;
8. re-run relevant tests;
9. inspect Git diff;
10. create checkpoint commit if the milestone is complete.

Do not randomly patch symptoms.

---

# 36. IMAGE-PROCESSING DEBUGGING

When geometry is wrong, save/debug intermediate representations where practical:

```text
source
mask
boundary
outer envelope
quad
refined quad
rectified
enhanced
```

Do not expose document contents through logs.

Prefer development-only artifacts or local debug exports.

Useful debug overlays:

* detected boundary;
* candidate IDs;
* selected target;
* confidence;
* corner coordinates;
* gutter;
* dewarp mesh.

These are engineering tools, not final UI.

---

# 37. LOGGING RULES

Logging should help diagnose behavior without leaking document content.

Good:

```text
Detector latency: 28 ms
Target confidence: 0.91
Peak native allocation: ...
```

Bad:

```text
Detected OCR text: "..."
```

Never log:

* document text;
* document image data;
* private user content;
* sensitive filenames unless explicitly needed.

Development debug logs may be more detailed, but should still avoid content leakage.

---

# 38. DEPENDENCY RULES

Before adding a dependency:

1. confirm it solves a real requirement;
2. inspect license;
3. inspect maintenance/activity;
4. inspect Android compatibility;
5. estimate binary/memory impact;
6. determine whether an existing dependency already solves the problem.

Avoid dependency sprawl.

Do not add a large library for a trivial utility.

---

# 39. THIRD-PARTY REFERENCE RULES

Reference products and projects such as:

* vFlat;
* CamScanner;
* Adobe Scan;
* Genius Scan;
* SwiftScan;
* FairScan;

may be studied for observable behavior or technical ideas.

Do not:

* copy proprietary implementation;
* extract proprietary assets;
* use unlicensed code;
* include incompatible model weights;
* include incompatible datasets.

When an external implementation is used as technical inspiration, independently verify the applicable license before importing anything.

---

# 40. FILE AND ASSET ORGANIZATION

Keep responsibilities explicit.

Suggested high-level structure:

```text
app/
camera/
detection/
geometry/
processing/
domain/
repository/
pdf/
import/
ui/
tests/
benchmark/
```

ML model assets should be separated from source code.

Test images should be organized and versioned appropriately.

Do not place temporary generated files into source-controlled directories.

---

# 41. DOCUMENTATION RULES

When behavior or architecture changes materially:

Update the appropriate documentation.

At minimum:

* `PRD.md` for product requirement changes;
* `AGENTS.md` for agent workflow/rules changes;
* `README.md` for developer setup/usage changes;
* relevant architecture documentation for subsystem changes.

Do not silently alter the product contract through code.

---

# 42. CHANGE CLASSIFICATION

Before making a change, classify it:

### Product change

Changes user-visible or functional requirements.

Examples:

* new scan mode;
* new PDF option;
* changed target-selection behavior.

Requires consideration against `PRD.md`.

### Architecture change

Changes subsystem boundaries or data flow.

Examples:

* replacing detector architecture;
* changing persistence model;
* changing rendering pipeline.

Requires stronger testing and documentation.

### Implementation change

Changes internals without changing the product contract.

Examples:

* faster image conversion;
* different filter implementation;
* refactored helper.

Should preserve behavior unless intentionally benchmarked.

### Experimental change

A temporary investigation.

Must be clearly isolated and should not silently become production architecture.

---

# 43. EXPERIMENT RULES

For uncertain technical decisions:

1. create the smallest experiment;
2. establish evaluation criteria;
3. test representative samples;
4. measure;
5. compare alternatives;
6. document conclusion;
7. only then integrate the chosen approach.

Examples:

* One Euro Filter vs Kalman-like tracker;
* segmentation model A vs B;
* CPU vs GPU delegate;
* different enhancement algorithms;
* different dewarp strategies.

Do not choose based solely on intuition.

---

# 44. WHEN TO REFACTOR

Refactor when:

* duplication creates correctness risk;
* lifecycle ownership is unclear;
* a module has multiple unrelated responsibilities;
* testing becomes difficult;
* memory ownership is ambiguous;
* a current design blocks required functionality.

Do not refactor merely to make code aesthetically different.

A refactor should have a clear engineering reason.

---

# 45. WHEN TO STOP AND ASK THE USER

Normally, do not ask for clarification for minor implementation choices.

Use engineering judgment.

Ask only when a decision would materially alter:

* product behavior;
* user workflow;
* data/privacy expectations;
* compatibility requirements;
* a locked PRD decision.

For ordinary technical implementation choices:

choose a defensible solution, implement it, test it, and document the rationale.

---

# 46. AUTONOMY RULE

The agent may independently choose:

* exact class names;
* package names;
* internal helper structure;
* algorithm implementation;
* test organization;
* optimization strategy;

provided that the resulting behavior respects the PRD.

The agent must not independently change:

* core product principles;
* user authority;
* Auto Capture semantics;
* One Page/Two Page semantics;
* local-first requirement;
* enclosing quadrilateral rule;
* no-forced-retake rule;
* enhancement content-preservation rule;
* PDF page-size principle.

Those require explicit product-level direction.

---

# 47. SECURITY RULES

Do not:

* upload document images;
* introduce remote image processing without explicit requirement;
* expose private document assets through debug servers;
* log document content;
* commit API keys;
* commit credentials;
* commit user-generated document samples unless explicitly intended for the test corpus.

Use local fixtures for testing whenever possible.

---

# 48. PERFORMANCE VS QUALITY RULE

When choosing between two approaches:

Prefer the approach with the best measured overall result, not simply:

* lowest latency;
* smallest model;
* smallest APK.

For this application, scan quality is a primary product requirement.

However, quality improvements must be evaluated against:

* RAM;
* thermal behavior;
* battery;
* latency;
* stability.

---

# 49. USER EXPERIENCE ENGINEERING RULE

Even though visual design is separate, implementation must respect the intended behavior.

Do not make users fight the computer vision.

The scanner should:

* assist;
* guide;
* automate when useful;
* remain reversible;
* remain user-controlled.

The agent must never introduce UX patterns that force users to obey computer vision simply because implementation is easier.

---

# 50. FINAL VERIFICATION BEFORE COMPLETION

Before declaring a feature complete:

1. run relevant unit tests;
2. run relevant integration/instrumentation tests;
3. build the application;
4. inspect logs for errors;
5. inspect Git diff;
6. verify no accidental files/secrets;
7. verify memory implications;
8. verify lifecycle behavior;
9. verify failure behavior;
10. compare against the relevant PRD requirements.

For image-processing changes, inspect representative before/after outputs.

For camera changes, test on an actual device whenever possible.

For PDF changes, verify actual generated PDFs rather than only unit-test metadata.

---

# 51. DEFINITION OF DONE

A feature is DONE only when:

* it is implemented;
* it is integrated into the real pipeline;
* it follows the PRD;
* relevant tests exist;
* failure cases are handled;
* performance implications are understood;
* memory ownership is clear;
* no unrelated regressions are introduced;
* repository state is clean or clearly documented;
* a meaningful checkpoint commit exists when appropriate.

"Compiles" is not the definition of done.

---

# 52. MILESTONE REPORTING

After completing a meaningful milestone, report internally/through the repository notes:

```text
Milestone:
What changed:
Tests:
Benchmarks:
Known limitations:
Next milestone:
Git checkpoint:
```

Keep reports factual.

Do not claim performance improvements without measurements.

---

## 53. WORKLOG.md

`WORKLOG.md` is the project's chronological engineering journal.

The agent MUST maintain `WORKLOG.md` throughout autonomous development.

### Purpose

Use `WORKLOG.md` to record what actually happened during development.

It is not a replacement for:

* `PRD.md` — product requirements
* `BRIEF.md` — product/engineering brief
* `ARCHITECTURE.md` — system architecture
* `plans/` — implementation plans

Plans describe intended work.

WORKLOG describes completed work, observed results, decisions, and issues.

### When to Update

Update `WORKLOG.md`:

1. at the beginning of a substantial autonomous work session;
2. after completing a meaningful milestone or sub-milestone;
3. after an important technical decision;
4. after completing a benchmark or experiment;
5. after discovering a significant failure or regression;
6. after making a meaningful architectural change;
7. before the final checkpoint commit.

Do not create an entry for every tiny edit.

### Required Entry Format

Use this structure:

## YYYY-MM-DD — Milestone / Task

### Objective

What was being accomplished.

### Changes

What was actually changed.

### Validation

Builds, tests, benchmarks, device tests, or other verification performed.

### Results

Important measured or observed results.

### Decisions

Technical decisions made during the work and their rationale.

### Problems

Failures, regressions, limitations, or unresolved issues.

### Next

The next concrete step, based on the current project state.

### Commit

The relevant checkpoint commit hash and message, when one exists.

### Rules

1. Record facts, not intentions.
2. Never claim a test passed unless it was actually run.
3. Never invent benchmark numbers.
4. Include measured values when available.
5. Record important rejected approaches when they materially affect future engineering decisions.
6. Keep entries concise enough to remain useful as a long-term engineering history.
7. Do not copy large code blocks into the worklog.
8. Do not log document contents, user documents, secrets, credentials, tokens, or other sensitive data.
9. Do not treat WORKLOG.md as the source of truth for product requirements. Requirements remain in PRD.md.
10. Do not rewrite old entries merely to make history look cleaner. Preserve historical accuracy.
11. When a later decision supersedes an earlier decision, keep the old entry and explicitly record the new decision.
12. When working autonomously across multiple sessions, read the recent WORKLOG.md history before starting substantial work.

### Session Discipline

At the start of a substantial session:

1. read the relevant plan;
2. read the recent WORKLOG.md entries;
3. inspect current repository state;
4. continue from the actual repository state rather than assuming the previous session completed successfully.

At the end of a substantial session:

1. verify the implementation;
2. update WORKLOG.md with actual results;
3. record important decisions and remaining issues;
4. create the appropriate checkpoint commit.

### Relationship to Plans

Use:

* `plans/` for "what should happen";
* `WORKLOG.md` for "what actually happened".

When implementation deviates from the plan, do not silently rewrite history.

Record the deviation in `WORKLOG.md` and update the relevant plan only when necessary for the remaining work.

# 54. Git Remote and Checkpoint Rules

The repository may have a configured Git remote.

When a remote exists, meaningful completed milestones and validated spikes MUST be pushed to the configured remote after the corresponding checkpoint commit.

Standard workflow:

1. Implement the planned milestone or spike.
2. Run the required build and tests.
3. Run relevant benchmarks or validation.
4. Inspect the resulting diff.
5. Update WORKLOG.md.
6. Create the checkpoint commit.
7. Push the checkpoint commit to the configured remote.
8. Verify that the working tree remains clean.

Use normal push operations only.

Never use:

* `git push --force`
* `git push --force-with-lease`
* destructive remote history rewriting

unless explicitly instructed by the user.

Do not push:

* secrets;
* credentials;
* API keys;
* private document contents;
* generated artifacts that are not intended for version control;
* unrelated user changes.

Do not create a Git remote automatically.

If no remote is configured, continue working locally and explicitly record that remote synchronization is unavailable.

Do not stop ordinary autonomous work merely because a remote is unavailable.

## Push Frequency

Push after:

* completed implementation milestones;
* completed technical spikes;
* meaningful architectural changes;
* important regression fixes;
* other deliberate checkpoint commits.

Do not push after every trivial file edit or intermediate experiment.

## Before Pushing

Always verify:

```bash
git status
git diff --check
git log -1 --oneline
git remote -v
```

The push must contain only changes belonging to the current checkpoint.

After pushing, verify the local branch is synchronized with its configured upstream.



# 54. FINAL AGENT MINDSET

Build the scanner as an engineering product, not a collection of demo features.

Remember:

AI detects.

Tracking stabilizes.

Geometry decides shape.

Processing creates the scan.

PageObject preserves editability.

PDF renderer exports the result.

The user remains in control.

Quality is measured.

Memory is managed.

Every meaningful milestone is recoverable through Git.

Never hide uncertainty behind confident-looking code.
