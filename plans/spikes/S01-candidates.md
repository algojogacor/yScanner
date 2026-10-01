# S01-candidates — Document Boundary / Segmentation Model & Dataset Survey

> **Status:** Research evidence only. **No decision is made here.** This file feeds `S01-model-selection.md`.
> **Author:** research spike (general-purpose-1)
> **Date:** 2026-10-01
> **Scope:** Evidence gathering for the `:detection` module of yScanner (offline Android document scanner).
> **Hard constraint reminder:** offline/on-device, minSdk 24, mid-range Snapdragon 600-class CPU, ≤50 ms inference, ≥15 FPS, model ≤20 MB, loaded RAM ≤50 MB, IoU ≥0.85 / Dice ≥0.90 on document class, runtime preference LiteRT/TFLite.

> **IMPORTANT — benchmark provenance rule for this whole document:** every accuracy/latency number below is labelled with the hardware it was measured on. **No candidate in this survey has a published latency measured on a physical Android device.** There is no target device available in this environment, so **all latency/throughput figures are "not yet validated on target device."** Any inference-time claim in this document is either (a) a static model statistic (params/FLOPs/file size) or (b) a benchmark from the source paper/README measured on desktop GPU/CPU or on a dataset evaluation, and is labelled as such.

---

## 1. TL;DR

- **Candidates found: 9 model families** (5 with obtainable pretrained weights that are plausibly relevant, 4 that are architecturally relevant but have **no document-domain pretrained weights**), plus **1 zero-weight classical baseline** and **4 datasets** for the train-from-scratch option.
- **The trade-off space has three real shapes:**

  **Axis 1 — ML vs no-ML.**
  - *No-ML (classical OpenCV):* zero weights, zero license risk, zero conversion risk, tiny RAM, sub-millisecond CPU. But brittle on low contrast, cluttered backgrounds, partial occlusion, non-rectangular pages. Fails the "≥15 FPS **with acceptable quality on the hard corpus**" bar on its own; realistically a fallback/first-pass, not the primary.
  - *ML:* needed for robustness on the hard cases in the S01 test corpus (shadow, glare, complex background, occlusion). Every ML path brings conversion, op-support, and license work.

  **Axis 2 — pretrained (adopt) vs train-from-scratch.**
  - *Adopt pretrained:* exactly **one** purpose-built, permissively-licensed, offline-deployable **document-boundary** model family exists and is obtainable — **DocAligner** (Apache-2.0). Everything else is either (a) a **generic edge detector** (HED/DexiNed/TEED) or (b) a **generic salient-object/dichotomous** segmenter (U²-Net/ISNet) that is *not* trained on documents and whose domain mismatch is a real quality risk, or (c) a **real-time semantic segmenter with no document weights** (PP-LiteSeg/SegFormer/DeepLabV3+/BiSeNetV2) that would need fine-tuning anyway.
  - *Train-from-scratch:* **no open, permissively-licensed, pixel-mask document-boundary dataset was found.** The best-fitting dataset (SmartDoc 2015 Ch1, CC BY 4.0) provides **quadrilateral corner annotations, not pixel masks** — masks would have to be synthesized by filling the quad. Doc3D (MIT) provides geometry (3D/UV/depth) from which masks can be derived but is a dewarping dataset, and its historical download server is reported offline. So "train from scratch" effectively means "fine-tune an ImageNet/COCO-pretrained backbone on a synthesized-mask dataset," which is a real project, not a shortcut.

  **Axis 3 — semantic mismatch of the output.**
  - The `:detection` contract (`004-document-detector.md`) expects a **segmentation mask** → `CandidateExtractor` → quad. But the best document-specific pretrained model (DocAligner) outputs **corner heatmaps/points directly**, and the edge models output **edge maps**. Choosing those changes the module contract (bypass or reshape `CandidateExtractor`). This is a first-class architectural decision, not just a model choice.

- **Verified-obtainable, permissively-licensed, plausibly relevant:** DocAligner (Apache-2.0, 1.7–14.7 MB for the small variants), DexiNed (MIT, but 47.2 MB ONNX — over budget), TEED (MIT, 58 K params — tiny), U²-Netp (Apache-2.0, 4.7 MB — but SOD domain), ISNet (Apache-2.0 code — size over budget, domain mismatch). HED is BSD-3 code but 58.9 MB Caffe and trained on a research-only dataset — poor fit.
- **Could not verify:** exact DocAligner ONNX file names/sizes and a stable direct download URL (weights are auto-fetched by the PyPI package from GCP/Google Drive); the license of the ISNet HF mirror; the MIDV-2020 dataset license; TEED checkpoint file size; whether any *document-boundary* segmentation model exists on TF Hub (none found).

---

## 2. Candidate comparison table (condensed)

| # | Candidate | Architecture (backbone+decoder) | Source | License (commercial?) | Size / params | Format & TFLite path | Output semantics | Compute (published) | Benchmark (provenance) | Integration risk | Fit to yScanner budgets |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | **DocAligner** (LC050 / LC100 / MBV2-140 / FastViT-T8 / FastViT-SA24) | PP-LCNet or MobileNetV2 or FastViT backbone + BiFPN + heatmap head | [github](https://github.com/DocsaidLab/DocAligner) · [bench](https://docsaid.org/en/docs/docaligner/benchmark) | **Apache-2.0** ✅ (LICENSE verified) | LC050 1.7 MB/0.4 M · LC100 4.9 MB/1.2 M · MBV2-140 14.7 MB/3.7 M · FastViT-T8 13.1 MB/3.3 M · FastViT-SA24 83.1 MB/20.8 M (FP32) | **ONNX** + ONNXRuntime. TFLite path = ONNX→TF→TFLite (op-support risk, esp. FastViT). | **Corner heatmaps** + edge + presence flag → contour centroid → 4 points | 0.22–8.5 GFLOPs (static) | Jaccard Index 0.9826–0.9937 on SmartDoc 2015. **Provenance: dataset eval; no hardware stated; no latency published.** | Medium: ONNX→TFLite conversion, heatmap post-proc, not a mask | **Best size/latency fit** (LC050/LC100/MBV2-140). No latency evidence. |
| 2 | **DexiNed** (opencv_zoo) | Dense extreme-inception CNN, single edge head | [HF](https://huggingface.co/opencv/edge_detection_dexined) · [repo](https://github.com/xavysp/DexiNed) | **MIT** ✅ (HF LICENSE verified) | **47.2 MB ONNX**; ~35 M params (reported, secondary) | **ONNX** (quantized variant exists). TFLite path = ONNX→TF→TFLite. | **Edge map** → threshold + contour | Not published in repo | BSDS ODS/OIS (paper). **Provenance: desktop GPU, not Android.** | Medium-high: 47 MB ONNX conversion; size over budget | **Over 20 MB budget**; RAM/CPU risk high |
| 3 | **HED** | VGG16 backbone + 5 side edge outputs | [github](https://github.com/s9xie/hed) · [weights mirror](https://huggingface.co/JelleWestra/HED) | Code **BSD-3-Clause** ✅; **weights trained on BSDS500 (research dataset)** ⚠️ | **58.9 MB caffemodel** (verified) | **Caffe** — hardest to convert; no clean TFLite path | **Edge map** | — | BSDS ODS 0.788 (paper). **Provenance: desktop GPU, not Android.** | High: Caffe→TFLite, size, dataset licensing | **Over budget**; poor fit |
| 4 | **TEED** | Tiny CNN, ~58 K params, edge head | [github](https://github.com/xavysp/TEED) | **MIT** ✅ (LICENSE verified) | **~58 K params → ~0.24 MB FP32** (size of checkpoint file UNVERIFIED) | PyTorch → ONNX → TFLite (small, low op risk) | **Edge map** | Not published (tiny) | ODS/OIS on BSDS/BIPED (ICCV-W 2023). **Provenance: desktop GPU, not Android.** | Low-medium: generic edge detector, needs contour post-proc | **Excellent size fit**; quality on documents unproven |
| 5 | **U²-Net / U²-Netp** | Nested U-structure (RSU blocks), SOD head | [github](https://github.com/xuebinqin/U-2-Net) | **Apache-2.0** ✅ (LICENSE verified) | u2netp **4.7 MB**; u2net **176.3 MB** | PyTorch → ONNX → TFLite | **Saliency map** → threshold | Not published in repo | SOD benchmarks (paper). **Provenance: desktop GPU.** | Medium: domain mismatch (SOD, not documents) | u2netp fits size; quality risk high without fine-tune |
| 6 | **ISNet (DIS)** | U²-Net-style encoder-decoder, intermediate supervision | [github](https://github.com/xuebinqin/DIS) · [HF mirror](https://huggingface.co/NimaBoscarino/IS-Net_DIS-general-use) | Code **Apache-2.0** ✅; HF mirror license **UNVERIFIED** | ~176 MB class (UNVERIFIED exact) | PyTorch → ONNX → TFLite | **Dichotomous mask** | — | DIS5K metrics (paper). **Provenance: desktop GPU.** | Medium-high: size + domain mismatch | **Over budget** |
| 7 | **PP-LiteSeg** | STDC1/STDC2 encoder + FLD/UAFM/SPPM decoder | [PaddleSeg](https://github.com/PaddlePaddle/PaddleSeg/blob/release/2.10/configs/pp_liteseg/README.md) | **Apache-2.0** ✅ | Params not stated in zoo table (STDC1/2 are ~1–2 M class) | Paddle → ONNX → TFLite (documented export) | **Semantic mask** (Cityscapes classes) | Cityscapes mIoU 73–79 %, **FPS 102–273 (TensorRT, desktop GPU)** | Cityscapes/CamVid mIoU. **Provenance: desktop GPU (ONNX+TensorRT).** | Medium: multi-step conversion; **no document weights** | Good architecture fit; **requires fine-tuning** |
| 8 | **SegFormer / DeepLabV3+ / BiSeNetV2 (MobileNet-class encoders)** | SegFormer MiT; DeepLabV3(+); BiSeNetV2 | torchvision / HF / PaddleSeg | Apache-2.0 / MIT per component | varies (B0/BiSeNetV2 ~ few MB) | DeepLabV3-MobileNetV2 has an official TFLite path; SegFormer TFLite is fragile | **Semantic mask** | BiSeNetV2 156 FPS @512×1024 (PaddleSeg table) | **No document-domain pretrained weights found.** VOC/ADE20K/Cityscapes only. | Medium: needs fine-tuning; SegFormer TFLite op risk | Architecturally fine; **requires training data** |
| 9 | **HF / TF Hub "document segmentation" search** | — | HF search / TF Hub | — | — | — | — | — | **No document-boundary segmentation model found.** HF hits are NLP token-classification and *layout* models (YOLOv8 on DocLayNet). TF Hub: none found. | n/a | n/a |
| — | **Classical OpenCV baseline** | Canny/adaptive-threshold → morphology → `findContours` → `approxPolyDP` | in-project | **none (own code)** ✅ | 0 MB weights | n/a (pure OpenCV) | **Contour/quad** | n/a | n/a — **must be measured locally** | Low | **Excellent** size/latency; quality limited on hard cases |

**Legend:** ✅ = verified by fetching the actual license/asset; ⚠️ = verified but with a licensing caveat; UNVERIFIED = could not confirm.

---

## 3. Detailed per-candidate sections

### 3.1 DocAligner (Docsaid) — the only purpose-built document-boundary pretrained model found

| Field | Value |
|---|---|
| **Name** | DocAligner (variants: `LC050`, `LC100`, `MBV2-140`, `FastViT_T8`, `FastViT_SA24`; legacy `PReg-LC050-XAtt`) |
| **Architecture** | Feature extractor (**PP-LCNet** / **MobileNetV2** / **FastViT**) → **BiFPN** neck → **heatmap regression** head (4 corner heatmaps + edge + document-presence classification). Older point-regression variant: PP-LCNet + cross-attention + linear point head. |
| **Source** | GitHub: https://github.com/DocsaidLab/DocAligner (resolves). Docs: https://docsaid.org/en/docs/docaligner/ (resolves). Benchmark page: https://docsaid.org/en/docs/docaligner/benchmark (resolves). |
| **License** | **Apache-2.0** — verified by fetching `https://raw.githubusercontent.com/DocsaidLab/DocAligner/main/LICENSE`. Commercial use ✅. (Note: the README itself does not state a license; the LICENSE file is authoritative.) |
| **Model size** | FP32 sizes published: `LC050` **1.7 MB / 0.4 M params**; `LC100` **4.9 MB / 1.2 M**; `MBV2-140` **14.7 MB / 3.7 M**; `FastViT_T8` **13.1 MB / 3.3 M**; `FastViT_SA24` **83.1 MB / 20.8 M**. (From the benchmark page.) |
| **Format / TFLite path** | Native **ONNX** (inference via ONNXRuntime on CPU/GPU). No TFLite artifact provided. Concrete path: ONNX → `onnx2tf`/`onnx-tf` → TFLite (or use the TFLite/ONNX Runtime Android bindings directly). **Risk:** BiFPN + attention ops (FastViT) may not map cleanly; the LCNet/MobileNetV2 variants are the lowest-risk to convert. |
| **Input / Output** | Input 128×128 RGB (explicit for the point-regression model; **heatmap-model input resolution NOT stated — UNVERIFIED**, FLOPs imply a small input). Output: per-corner **heatmaps** + an **edge** map + a **presence** classification; post-processing = upscale heatmaps → per-corner contour → centroid = corner. **Not a segmentation mask.** |
| **Compute cost** | 0.22 GFLOPs (PReg-LC050-XAtt) up to 8.5 GFLOPs (FastViT_SA24); small variants 1.2–2.4 GFLOPs. Static figures; input resolution for the heatmap variants not stated. |
| **Available benchmarks** | Overall **Jaccard Index on SmartDoc 2015**: FastViT_SA24 0.9937, MBV2-140 0.9909, FastViT_T8 0.9906, LC100 0.9892, LC050 0.9826, PReg-LC050-XAtt 0.9596. |
| **Benchmark provenance** | **Dataset evaluation on SmartDoc 2015 (desktop/unknown host). No hardware stated. No latency/FPS published anywhere.** → not validated on target device. |
| **Preprocessing** | Resize to model input, RGB, normalization per package (the PyPI `docaligner-docsaid` package handles pre/post). |
| **Postprocessing** | Heatmap upscale, per-corner contour + centroid; presence threshold. |
| **Integration risk** | Medium. (1) ONNX→TFLite op support for BiFPN/attention. (2) Output is corners, not a mask — the `:detection` contract in `004-document-detector.md` assumes a mask → `CandidateExtractor`; adopting DocAligner would either bypass or reshape that stage. (3) Weights are **not** at a stable direct URL — the PyPI package fetches them from GCP/Google Drive (release notes show the storage host changed over time), so we must **pin and vendor** a specific ONNX file and record its hash. Direct ONNX URL/size = **UNVERIFIED**. |
| **Budget fit** | LC050/LC100/MBV2-140 satisfy the ≤20 MB file target. Whether they meet ≤50 ms CPU on Snapdragon-600 is **unknown — not validated on target device**; the LCNet-class backbones make it plausible but this is a hypothesis, not evidence. |

### 3.2 DexiNed (opencv_zoo build)

| Field | Value |
|---|---|
| **Name** | DexiNed (opencv_zoo edge detection build, `edge_detection_dexined_2024sep.onnx`) |
| **Architecture** | Dense extreme-inception CNN (DexiNed, WACV2020 lineage); single edge output. |
| **Source** | HF: https://huggingface.co/opencv/edge_detection_dexined (resolves). Upstream: https://github.com/xavysp/DexiNed (resolves). |
| **License** | **MIT** — verified on the HF model card and `LICENSE` file in that repo. Commercial ✅. (Upstream original repo license: **UNVERIFIED** — verify before vendoring upstream code.) |
| **Model size** | **47.2 MB** ONNX (verified from HF file tree). ~35 M params (reported by a secondary source; **UNVERIFIED from a primary source**). |
| **Format / TFLite path** | ONNX (+ a quantized variant). ONNX → TF → TFLite. |
| **Input / Output** | Image → **edge probability map**. Fixed input shape in the ONNX (OpenCV DNN infers at input shape; see opencv_zoo issue #44). |
| **Compute cost** | Not published. |
| **Benchmarks** | BSDS ODS/OIS (DexiNed paper). **Provenance: desktop GPU.** |
| **Integration risk** | Medium-high: 47 MB file, conversion cost, generic edge detector needs contour post-processing. |
| **Budget fit** | **Exceeds the ≤20 MB file target**; RAM/CPU risk on mid-range. |

### 3.3 HED (Holistically-Nested Edge Detection)

| Field | Value |
|---|---|
| **Name** | HED (VGG16-based) |
| **Source** | Code: https://github.com/s9xie/hed (resolves). Weights mirror: https://huggingface.co/JelleWestra/HED (resolves). |
| **License** | Code **BSD-3-Clause** (verified via raw LICENSE). **Weights were trained on BSDS500**, whose images carry their own research-use terms → **weight licensing is ambiguous / likely research-restricted** ⚠️. |
| **Model size** | **58.9 MB** caffemodel (verified from HF file tree) + 8.19 KB prototxt. |
| **Format** | **Caffe** — the hardest format to get into TFLite; no clean, maintained path. |
| **Input / Output** | Image → edge map. |
| **Benchmarks** | BSDS ODS ≈ 0.788 (paper). **Provenance: desktop GPU.** |
| **Budget fit** | **Exceeds ≤20 MB**; conversion risk high; dataset licensing caveat. **Recommend dropping** unless nothing else works. |

### 3.4 TEED (Tiny and Efficient Edge Detector)

| Field | Value |
|---|---|
| **Name** | TEED |
| **Architecture** | Very light CNN, **~58 K parameters** (<0.2 % of SOTA edge models), trained on BIPED. |
| **Source** | https://github.com/xavysp/TEED (resolves); `checkpoints/` folder exists in-repo (verified). |
| **License** | **MIT** (verified via raw LICENSE). Commercial ✅. |
| **Model size** | ~58 K params ⇒ **~0.24 MB FP32** (checkpoint file size itself **UNVERIFIED**; the `checkpoints/` dir contains `BIPED` and `current_res` subfolders). |
| **Format / TFLite path** | PyTorch → ONNX → TFLite; tiny graph, low op-support risk. |
| **Input / Output** | Image → **edge map** (crisp). |
| **Benchmarks** | ODS/OIS on BSDS/BIPED (ICCV 2023 Workshop). **Provenance: desktop GPU.** |
| **Integration risk** | Low-medium for conversion; **quality on documents is unproven** — it is a generic edge detector, so a clean page on a busy desk may produce edges everywhere, and contour/quad extraction would need to be robust. |
| **Budget fit** | **Best-in-class size fit.** Latency almost certainly fine on CPU, but **not validated on target device**, and document quality is the open question. |

### 3.5 U²-Net / U²-Netp

| Field | Value |
|---|---|
| **Name** | U²-Net (`u2net`) and U²-Net-lite (`u2netp`) |
| **Architecture** | Nested U-structure (RSU blocks), salient-object-detection head. |
| **Source** | https://github.com/xuebinqin/U-2-Net (resolves). |
| **License** | **Apache-2.0** (verified via raw LICENSE). Commercial ✅. |
| **Model size** | `u2netp` **4.7 MB**, `u2net` **176.3 MB** (from README). |
| **Format / TFLite path** | PyTorch `.pth` → ONNX → TFLite. `u2netp` is small and conversion-friendly. |
| **Input / Output** | **320×320** recommended; output = **saliency map** → threshold to mask. |
| **Benchmarks** | SOD datasets (paper). **Provenance: desktop GPU.** |
| **Integration risk** | Medium: **domain mismatch** — trained on salient objects (DUTS etc.), not documents. On a page-on-desk scene it may fire on the whole desk or on a high-contrast object. |
| **Budget fit** | `u2netp` fits the size budget; **quality is the risk** and would likely require fine-tuning on document data. |

### 3.6 ISNet (Dichotomous Image Segmentation)

| Field | Value |
|---|---|
| **Name** | IS-Net (`isnet-general-use.pth` / `isnet.pth`) |
| **Source** | https://github.com/xuebinqin/DIS (resolves). HF mirror: https://huggingface.co/NimaBoscarino/IS-Net_DIS-general-use (resolves). |
| **License** | Repo code + metric **Apache-2.0** (stated in README "Term of Use"); **the DIS5K dataset has separate Terms-of-Use**. **HF mirror license not shown on the page — UNVERIFIED.** |
| **Model size** | U²-Net-class architecture ⇒ **~176 MB range (UNVERIFIED exact)**. |
| **Format / TFLite path** | PyTorch → ONNX → TFLite. |
| **Input / Output** | Image → **dichotomous foreground mask**. |
| **Benchmarks** | DIS5K (paper). **Provenance: desktop GPU.** |
| **Budget fit** | **Exceeds ≤20 MB.** Domain mismatch (general foreground, not documents). |

### 3.7 PP-LiteSeg (PaddleSeg)

| Field | Value |
|---|---|
| **Name** | PP-LiteSeg-T (STDC1) / PP-LiteSeg-B (STDC2) |
| **Architecture** | STDC encoder + FLD (flexible lightweight decoder) + UAFM + SPPM. |
| **Source** | https://github.com/PaddlePaddle/PaddleSeg/blob/release/2.10/configs/pp_liteseg/README.md (resolves). |
| **License** | **Apache-2.0** (PaddleSeg). Commercial ✅. |
| **Model size** | Not stated in the zoo table; STDC1/STDC2 backbones are in the ~1–2 M-parameter class (exact value **UNVERIFIED**). |
| **Format / TFLite path** | Paddle → ONNX (documented: `model_export_onnx`) → TFLite. Repo warns the adaptive-average-pool op may not export; workaround = input dims multiple of 128. |
| **Input / Output** | Semantic **segmentation mask** over dataset classes. |
| **Benchmarks** | Cityscapes mIoU 73.10 % (T) – 79.04 % (B); **FPS 102.6–273.6**. |
| **Benchmark provenance** | **Desktop GPU via ONNX + TensorRT** (repo explicitly measures with `infer_onnx_trt.py`). **Not Android.** |
| **Integration risk** | Medium (multi-step conversion). **Critically: weights are Cityscapes/CamVid street-scene models — there are NO document-domain weights.** Would require fine-tuning. |
| **Budget fit** | Architecture fits the real-time budget on paper; **useless without document training data.** |

### 3.8 SegFormer / DeepLabV3+ / BiSeNetV2 (MobileNet-class encoders)

- **Finding:** architectures are relevant, but **no document-domain pretrained weights were found** for any of them — only VOC / ADE20K / Cityscapes. BiSeNetV2 appears in the PaddleSeg comparison table (Cityscapes, 156 FPS @512×1024, **desktop GPU**).
- **TFLite note:** DeepLabV3 with a MobileNetV2 encoder has an official TFLite conversion path (TF Model Garden / TF Hub). SegFormer's Transformer ops make TFLite conversion fragile.
- **Implication:** these belong in the *train-from-scratch / fine-tune* branch, not the *adopt-pretrained* branch. Their inclusion in `S01-model-selection.md` should be read as "architecture to fine-tune," not "model to download."

### 3.9 HuggingFace / TF Hub / Kaggle search result

- **No pretrained document-boundary segmentation model was found** on HF or TF Hub.
- HF `models?search=document segmentation` returns **NLP token-classification** models (`GEOcite/DocumentSegmentationModel`, `Prateek0515/legal-document-segmentation`) and **layout** models (e.g. `DILHTWD/documentlayoutsegmentation_YOLOv8_ondoclaynet`) — these segment *regions inside* a page, not the page boundary in a camera frame.
- `DALAI-project/Document_segmentation` = YOLOv5m **layout** model (5 region classes), **AGPL-3.0**, ~1 100 training images — **wrong task and AGPL** (AGPL is a distribution risk for a closed app).
- TF Hub: no document segmentation model found.

---

## 4. Datasets section (for the train-from-scratch / fine-tune option)

| Dataset | Task / annotation | License (commercial?) | Size / count | Availability | Fit |
|---|---|---|---|---|---|
| **SmartDoc 2015 — Challenge 1** | **Quadrilateral corner GT** (`tl_x,tl_y,bl_x,bl_y,br_x,br_y,tr_x,tr_y`) per frame — *boundary*, but as 4 points, **not pixel masks** | **CC BY 4.0** ✅ (attribution required) | **24 889 frames**, 150 clips, 30 documents, 5 backgrounds. Total GB **UNVERIFIED** (check `frames.tar.gz` on Releases) | GitHub: https://github.com/jchazalon/smartdoc15-ch1-dataset (resolves) + releases | **Best fit** for boundary. Masks must be synthesized by filling the quad. CC BY 4.0 is commercial-friendly. |
| **Doc3D** | 3D coords, depth, **UV**, normals, backward mapping, albedo — geometry for dewarping | **MIT** ✅ (verified on GitHub LICENSE and HF dataset card `license: mit`) | **100 k images** | GitHub: https://github.com/cvlab-stonybrook/doc3D-dataset · HF: https://huggingface.co/datasets/StonyBrook-CVLab/doc3D-dataset | Can derive boundary masks from UV/geometry, but it is a **dewarping** dataset. **Availability risk:** a 2022 community note reports the original FTP server offline; HF mirror exists but completeness **UNVERIFIED**. |
| **MIDV-500 / MIDV-2020** | Identity documents; quadrilateral/geometry annotations in video | **UNVERIFIED** (license not confirmed in this survey) | MIDV-2020: 1 000 video clips + 1 000 scanned images | https://l3i-share.univ-lr.fr/MIDV2020/midv2020.html (resolves) | Narrow domain (ID cards). Verify license before use. |
| **DocVQA** | OCR / question-answering over document images | — | — | — | **Not suitable** — it is not a boundary/segmentation dataset. Listed only to rule it out. |
| *(edge datasets)* **BIPED / UDED** | Edge maps for TEED/DexiNed training | BIPED on Kaggle (license **UNVERIFIED**); UDED repo linked from TEED | — | Kaggle / GitHub | Only relevant if adopting an edge-detector branch. |

**Key gap:** there is **no open, permissively-licensed, pixel-mask document-boundary dataset** readily found. The realistic train-from-scratch path is: synthesize masks from SmartDoc 2015 quads (+ optionally Doc3D geometry), then fine-tune an ImageNet/COCO-pretrained lightweight encoder-decoder (U-Net-MobileNetV3, DeepLabV3-MobileNetV2, PP-LiteSeg-STDC1). That is a **training project with data-engineering and licensing-tracking work**, not a download.

---

## 5. Classical no-ML baseline (zero weights)

A pure OpenCV pipeline is worth keeping as a **fallback and/or first-pass**, and it needs no model, no license, and no conversion:

1. Convert to gray; optional CLAHE / bilateral filter.
2. Either **Canny** (with auto thresholds) **or adaptive threshold**.
3. **Morphological close** + dilate to join edges.
4. `findContours` (external).
5. Filter by area / aspect; take the largest plausible quad via `approxPolyDP`; require convexity and 4 vertices; else `minAreaRect` / `convexHull`.
6. Score by area ratio + rectangularity; emit `DocumentCandidate`.

- **Pros:** 0 MB, sub-ms to a few ms on CPU, deterministic, no delegate/conversion risk, no license risk, trivially shippable.
- **Cons:** brittle on low contrast, glare/shadow, cluttered/textured backgrounds, partial occlusion, non-rectangular pages — exactly the S01 hard corpus. Also note `004-document-detector.md` already flags **OpenCV APK-size** as a risk.
- **Provenance:** no benchmark applies; **quality and latency must be measured locally** on the S01 corpus. **Not yet validated on target device.**

---

## 6. Open risks / unknowns (things that could NOT be verified)

1. **DocAligner ONNX artifacts:** no stable direct download URL, file names, or byte sizes could be verified. The PyPI package fetches weights from GCP/Google Drive (storage host has changed across releases). **Must be pinned + hashed + vendored.** The heatmap variants' **input resolution** is not published.
2. **No Android latency exists for any candidate.** All published FPS/ODS/OIS/JI numbers are desktop (GPU or TensorRT) or dataset evaluations. **Not yet validated on target device.**
3. **ISNet HF mirror license** not shown; upstream **code** is Apache-2.0 but the **DIS5K dataset** has separate terms.
4. **HED weights** are trained on **BSDS500** (research dataset) — weight licensing is ambiguous; treat as research-restricted until cleared.
5. **DexiNed parameter count** (~35 M) comes from a secondary source, not the upstream repo.
6. **TEED checkpoint file size** not read directly (repo `checkpoints/` confirmed present).
7. **PP-LiteSeg parameter count / file size** not stated in the zoo table.
8. **MIDV-2020 license** not confirmed.
9. **Doc3D download completeness** — original FTP reportedly offline; HF mirror completeness unverified.
10. **SmartDoc 2015 total download size** not stated (only frame count).
11. **TF Hub:** no document-boundary segmentation model found — but the search was keyword-based, so this is "not found," not a proof of absence.
12. **ONNX→TFLite op support** for DocAligner's BiFPN/attention, and for SegFormer, is **unverified** — a conversion spike is required before committing.

---

## 7. Decision inputs for the human (neutral — no winner recommended)

Axes to weigh:

1. **Output contract:** does yScanner want a **mask** (keeps `CandidateExtractor`, matches `004-document-detector.md`) or **corners directly** (DocAligner — simpler downstream, but rewrites the module contract)? This choice may dominate all others.
2. **Pretrained-vs-train:** adopt the one purpose-built document model (DocAligner, Apache-2.0, small variants 1.7–14.7 MB) vs invest in a fine-tuning project (needs a synthesized-mask dataset from SmartDoc 2015 + Doc3D).
3. **Licensing posture:** Apache-2.0/MIT candidates (DocAligner, TEED, U²-Net, PP-LiteSeg) vs the caveated ones (HED/BSDS weights; AGPL layout model; ISNet HF mirror). Project rule requires license verification before inclusion.
4. **Size budget:** only DocAligner-LC/MBV2, TEED, and U²-Netp are ≤20 MB. DexiNed (47 MB), HED (59 MB), ISNet (~176 MB) exceed it.
5. **Conversion risk:** native-TFLite (none found) > ONNX→TFLite (DocAligner, DexiNed, TEED, U²-Net, PP-LiteSeg) > Caffe (HED). A conversion spike is a prerequisite regardless.
6. **Quality-on-documents evidence:** only DocAligner has *document* accuracy evidence (SmartDoc 2015 JI 0.98–0.99). Everything else is edge/SOD/street-scene — **quality on documents is unproven** and the corpus is harder than any published benchmark here.
7. **Classical fallback posture:** ship classical CV as the guaranteed-working baseline and/or pre-filter, with ML as an enhancement — vs ML-first with classical only as a failure fallback.
8. **Verification cost:** every latency/RAM/thermal claim must be re-measured on the real target device (mid-range Snapdragon-600, 8 GB) — none of the numbers in this report are device numbers.

> **Bottom line for the human:** the evidence supports at most **two genuinely different bets** — (A) adopt **DocAligner** (only purpose-built, permissive, small, document-accurate option, but ONNX + corner-output + unverified latency) or (B) **fine-tune a lightweight semantic segmenter** on synthesized document masks (U-Net-MobileNetV3 / DeepLabV3-MobileNetV2 / PP-LiteSeg-STDC1, all Apache-2.0 and TFLite-friendly, but no ready weights and a dataset-building effort). **TEED / U²-Netp** are size-viable wildcards whose document quality is unproven. The **classical OpenCV** pipeline is the zero-risk fallback. No choice can be finalized without an on-device conversion + latency spike.
