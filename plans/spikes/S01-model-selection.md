# S01: Segmentation Model Selection

> **Gate:** Must complete before M03 (Document Detector)

## Hypothesis

A lightweight U-Net variant or MobileNet-based segmentation model, quantized to INT8, can achieve document segmentation at ≥15 FPS on a mid-range Android device (Snapdragon 600-series or equivalent) while maintaining sufficient mask quality for the downstream geometry pipeline.

## Alternatives

| # | Model Architecture | Size (est.) | Quantization | Runtime |
|---|-------------------|-------------|--------------|---------|
| A | **U-Net with MobileNetV3 encoder** | 5-15 MB | INT8 (TFLite) | LiteRT CPU |
| B | **DeepLabV3+ with MobileNetV3** | 8-20 MB | INT8 (TFLite) | LiteRT CPU |
| C | **Custom lightweight U-Net** (fewer channels) | 2-8 MB | INT8 (TFLite) | LiteRT CPU |
| D | **BiSeNet / BiSeNetV2** | 5-12 MB | INT8 (TFLite) | LiteRT CPU |
| E | **PP-LiteSeg** (PaddleSeg → ONNX → TFLite) | 3-10 MB | INT8 | LiteRT CPU |

### Acceleration Variants

For each model, also test:
- GPU delegate (if available on test device)
- NNAPI delegate (if available)
- CPU-only (mandatory fallback)

## Test Corpus

Minimum 50 images, representing:

| Category | Count | Description |
|----------|-------|-------------|
| Standard A4 | 10 | Clean, good lighting, white paper |
| Perspective | 5 | Moderate to extreme viewing angle |
| Cards/receipts | 5 | Small documents |
| Shadow/glare | 5 | Challenging lighting |
| Multiple documents | 5 | 2-3 documents in frame |
| Complex background | 5 | Cluttered desk, textured surface |
| Partial occlusion | 5 | Fingers, hand holding document |
| Low light | 3 | Dim environment |
| Colored paper | 3 | Non-white documents |
| Concave/irregular | 4 | Non-rectangular shapes |

All images must have ground-truth segmentation masks.

## Metrics

| Metric | Target | Priority |
|--------|--------|----------|
| Inference latency (CPU) | ≤ 50 ms | Critical |
| Inference latency (GPU/NNAPI) | ≤ 30 ms | Desirable |
| Segmentation IoU (document class) | ≥ 0.85 | Critical |
| Segmentation Dice | ≥ 0.90 | Critical |
| Model file size | ≤ 20 MB | Desirable |
| Model RAM (loaded) | ≤ 50 MB | Critical |
| False positive rate (no document) | ≤ 5% | Important |
| Analysis FPS (end-to-end) | ≥ 15 FPS | Critical |
| Downstream corner error (px) | ≤ 10 px at 640×480 | Important |

## Procedure

1. **Prepare test environment:**
   - Mid-range Android device (identify specific device model)
   - Install benchmark harness app
   - Configure TFLite interpreter with CPU, GPU, NNAPI delegates

2. **Prepare models:**
   - For each candidate: train or obtain pre-trained weights
   - Convert to TFLite format
   - Apply INT8 quantization (post-training or quantization-aware training)
   - Verify model loads correctly on target device

3. **Run inference benchmarks:**
   - Per model × per delegate: run 100 inferences
   - Record: mean latency, P50, P95, P99
   - Record: RAM usage (baseline → loaded → inference peak)
   - Record: thermal state after 60 seconds sustained inference

4. **Run quality benchmarks:**
   - Per model: run on full test corpus
   - Compute: IoU, Dice, false positive rate, false negative rate
   - Compute: downstream corner error (run full geometry pipeline on mask output)
   - Visual inspection of mask quality on difficult cases

5. **Compare:**
   - Create comparison table: latency × quality × size × memory
   - Identify Pareto-optimal candidates

## Decision Rule

Select the model that:

1. Meets ALL critical targets (latency ≤ 50ms CPU, IoU ≥ 0.85, FPS ≥ 15, RAM ≤ 50 MB)
2. Among models meeting critical targets, prefer:
   - Higher IoU (quality over speed, as long as FPS target met)
   - Smaller model size
   - Lower thermal impact
3. If GPU/NNAPI acceleration provides ≥30% speedup without stability issues, prefer it; otherwise default to CPU
4. If no model meets all critical targets: identify the limiting factor and investigate architectural changes (smaller input resolution, channel pruning, etc.)

## Result / Decision Record

> **Status:** PENDING — to be completed during implementation phase
>
> **Selected model:** TBD
> **Rationale:** TBD
> **Benchmark results:** TBD
> **Acceleration decision:** TBD
