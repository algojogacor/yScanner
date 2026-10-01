BRIEF.md

LOCALSCAN — PROJECT BRIEF

==================================================

1. PROJECT SUMMARY
   ==================================================

LocalScan adalah aplikasi document scanner native Android yang berfokus pada kualitas scan, automatic document detection, perspective correction, book scanning, local AI, dan workflow multi-page.

Tujuan produk adalah memberikan pengalaman scanner yang setara dengan pola penggunaan scanner matang seperti vFlat dan CamScanner, tetapi dengan:

* tanpa iklan;
* tanpa kebutuhan server;
* tanpa account untuk core workflow;
* AI berjalan secara lokal/on-device;
* kontrol penuh tetap berada di tangan pengguna;
* kualitas scan menjadi prioritas utama.

Visual design, branding, typography, color system, component styling, dan polished UI tidak termasuk dalam brief ini. Design akan dikerjakan secara terpisah.

==================================================
2. CORE PRODUCT IDEA
====================

Scanner harus terasa seperti scanner sungguhan, bukan kamera biasa yang diberi crop tool.

Alur utama:

CameraX
→ live document detection
→ target selection
→ temporal tracking
→ capture
→ full-resolution geometry refinement
→ perspective correction / dewarp
→ enhancement
→ PageObject
→ page management
→ PDF

AI bertugas membantu memahami dokumen.

User tetap menentukan apa yang ingin di-scan dan apakah hasil akhirnya sudah dianggap bagus.

==================================================
3. NON-NEGOTIABLE PRINCIPLES
============================

3.1. USER ALWAYS HAS FINAL CONTROL

* Manual shutter selalu tersedia.
* Auto Capture bersifat optional.
* Auto Capture menggunakan countdown 2 detik.
* Capture manual tidak boleh diblokir.
* Quality assessment tidak boleh memaksa user melakukan retake.
* User dapat melakukan retake/replacement sendiri setelah capture.
* User dapat undo dan mengedit hasil.

3.2. PHYSICAL DOCUMENT IS THE SOURCE OF TRUTH

AI harus mendeteksi bentuk fisik dokumen pada gambar terlebih dahulu.

PDF page size tidak boleh menentukan document detection.

Urutan:

capture
→ detect document
→ geometry correction
→ enhancement
→ PDF layout

Bukan:

A4 selected
→ AI mencari bentuk A4.

3.3. STANDARD OUTPUT IS AN ENCLOSING QUADRILATERAL

Dokumen normal:

document
→ four outer corners
→ quadrilateral
→ perspective correction

Dokumen miring:

→ quadrilateral mengikuti perspektif aktual.

Dokumen kompleks/concave:

→ cari outer envelope
→ bentuk enclosing quadrilateral
→ area background yang berada di dalam quadrilateral boleh tetap masuk.

Scanner tidak menggunakan concave polygon sebagai normal crop output.

3.4. AI MUST NOT INVENT DOCUMENT CONTENT

Enhancement hanya boleh memperjelas informasi yang sudah ada.

Tidak boleh melakukan generative reconstruction terhadap:

* text;
* handwriting;
* signature;
* stamp;
* diagram;
* document marks.

==================================================
4. CAMERA EXPERIENCE
====================

Aplikasi memiliki custom in-app camera.

Gunakan CameraX:

CameraProvider
├── Preview
├── ImageAnalysis
└── ImageCapture

Preview:

* live camera;
* live document overlay.

ImageAnalysis:

* low-resolution;
* real-time AI;
* latest-frame strategy;
* tidak menumpuk stale frames.

ImageCapture:

* full-resolution;
* quality-oriented;
* file-backed processing.

Default camera behavior:

* continuous autofocus;
* automatic exposure;
* automatic white balance.

Autofocus watchdog dapat melakukan evaluasi berkala, sekitar 4 detik, tetapi tidak memaksa refocus jika kondisi sudah stabil.

Flash modes:

* Flash Off;
* Torch / Always On;
* Flash on Capture.

Default:
Flash Off.

==================================================
5. LIVE DOCUMENT DETECTION
==========================

Local AI segmentation menjadi detector utama.

Pipeline:

low-resolution frame
→ segmentation
→ boundary extraction
→ outer envelope
→ quadrilateral fitting
→ corner refinement
→ candidate geometry

Segmentation mask bukan hasil crop final.

==================================================
6. TAP-TO-GUIDE AND TARGET LOCKING
==================================

Jika beberapa dokumen terlihat:

User dapat tap satu dokumen.

Tap:

* memberi spatial prior kepada AI;
* memilih candidate yang mengandung titik tap;
* memperkuat target lock;
* memicu focus/exposure metering di area tap.

Setelah target dipilih:

AI harus mempertahankan target tersebut secara temporal.

AI tidak boleh berpindah dari dokumen yang dipilih ke dokumen lain hanya karena satu frame menghasilkan confidence lebih tinggi.

Target berubah jika:

* user tap dokumen lain;
* target benar-benar keluar;
* target hilang dalam periode yang cukup lama;
* tracking gagal.

==================================================
7. TEMPORAL TRACKING
====================

Boundary tidak boleh langsung berubah berdasarkan hasil detector setiap frame.

Pipeline:

Detector
→ candidate
→ target selection
→ temporal tracker
→ smoothing/filtering
→ stable geometry
→ overlay

State:

SEARCHING
→ CANDIDATE
→ TRACKING
→ STABLE
→ CAPTURE READY
→ CAPTURED

Tujuan utama:

* mengurangi jitter;
* menjaga target identity;
* membuat overlay terasa stabil;
* tetap responsif terhadap pergerakan nyata.

==================================================
8. LIVE OVERLAY
===============

Boundary overlay selalu terlihat jika sistem memiliki estimate yang berarti.

Overlay bukan template A4, A5, B5, atau Letter.

Overlay menunjukkan physical document boundary.

Confidence behavior:

HIGH:
→ capture-ready.

MEDIUM:
→ boundary tetap terlihat, belum Auto Capture.

LOW:
→ best-effort boundary tetap dapat terlihat, tetapi tidak Auto Capture.

==================================================
9. AUTO CAPTURE
===============

Auto Capture ON:

valid target
→ target stable
→ geometry stable
→ quality readiness adequate
→ countdown 2 detik
→ capture

Auto Capture OFF:

user tekan shutter
→ capture.

Manual shutter tetap tersedia walaupun Auto Capture aktif.

Tidak ada forced retake.

==================================================
10. FINGER / OCCLUSION BEHAVIOR
===============================

Finger Remover bukan fitur wajib pada initial release.

Tangan/jari boleh tetap muncul pada hasil scan.

Yang penting:

* finger tidak dianggap sebagai document boundary;
* geometry tetap mengikuti envelope dokumen;
* partial occlusion tidak langsung menggagalkan scanning;
* sistem menggunakan bagian dokumen yang terlihat dan geometry continuity untuk memperkirakan envelope bila memungkinkan.

Prinsip:

occlusion adalah kondisi yang harus ditoleransi, bukan alasan otomatis untuk memaksa retake.

==================================================
11. COORDINATE PIPELINE
=======================

Live quad dari ImageAnalysis tidak boleh langsung digunakan sebagai final crop pada full-resolution image.

Pipeline:

low-resolution analysis
→ CameraX coordinate transformation
→ sensor/normalized coordinates
→ full-resolution capture coordinates
→ local refinement
→ final geometry

Jangan mengasumsikan scaling sederhana berdasarkan width/height.

Harus memperhitungkan:

* rotation;
* sensor orientation;
* crop rectangle;
* viewport;
* aspect ratio;
* output dimensions;
* CameraX transformation.

==================================================
12. DOCUMENT GEOMETRY ENGINE
============================

Geometry menjadi salah satu core subsystem aplikasi.

Komponen:

* boundary extraction;
* outer envelope;
* quadrilateral fitting;
* corner refinement;
* edge refinement;
* perspective estimation;
* book spread detection;
* page boundary detection;
* gutter detection;
* curvature estimation;
* dewarp mesh;
* coordinate mapping.

Geometry pipeline dipisahkan dari model AI agar dapat diuji dan dikembangkan secara independen.

==================================================
13. ONE PAGE MODE
=================

One Page adalah mode scanning normal.

One capture
→ one document
→ one PageObject.

Pipeline:

source
→ segmentation/boundary
→ outer envelope
→ quadrilateral
→ corner refinement
→ homography
→ rectified page
→ quality assessment
→ enhancement
→ PageObject

Contoh:

* A4;
* A5;
* B5;
* cards;
* receipts;
* notes;
* posters;
* single book page.

Ukuran fisik dokumen tidak perlu diketahui dalam sentimeter untuk melakukan crop dan perspective correction.

==================================================
14. TWO PAGE MODE
=================

Two Page adalah mode khusus open-book spread.

Bukan:

wide quad
→ split 50/50.

Pipeline:

full-resolution capture
→ spread detection
→ left/right page boundaries
→ gutter detection
→ curvature estimation
→ dewarp
→ left/right separation
→ perspective correction
→ enhancement
→ two PageObjects

Dewarp selalu aktif pada Two Page Mode.

Default output order:

left page
→ first page

right page
→ second page

Sistem harus tetap mampu menangani:

* buku tidak terbuka 180°;
* gutter gelap;
* curvature besar;
* page boundary tidak sempurna;
* lighting tidak ideal;
* left/right curvature berbeda.

==================================================
15. QUALITY ASSESSMENT
======================

Quality assessment tidak digunakan untuk memaksa retake.

Live metrics membantu Auto Capture.

Possible metrics:

* document confidence;
* geometry stability;
* blur;
* motion;
* coverage;
* target persistence.

Post-capture metrics:

* blur score;
* glare score;
* shadow score;
* exposure;
* geometry confidence;
* corner confidence;
* crop confidence;
* document coverage.

Quality score bersifat internal/advisory.

User tetap memegang keputusan.

==================================================
16. ENHANCEMENT
===============

Dua mode utama:

Natural / Color

dan

Clean / Intelligent Clean.

NATURAL:

* preserve natural color;
* mild exposure;
* mild white balance;
* illumination correction;
* modest denoise;
* no excessive sharpening.

CLEAN:

* cleaner background;
* shadow suppression;
* illumination normalization;
* contrast improvement;
* local detail enhancement;
* denoise;
* selective sharpening;
* color-aware preservation.

Harus tetap mempertahankan:

* handwriting;
* pencil;
* signatures;
* stamps;
* colored ink;
* highlights;
* diagrams.

Tidak menggunakan generative enhancement untuk isi dokumen.

==================================================
17. NON-DESTRUCTIVE PAGE PROCESSING
===================================

Source image menjadi sumber utama.

PageObject menyimpan:

* source reference;
* geometry;
* rotation;
* enhancement parameters;
* metadata;
* quality metrics.

Rendered output adalah derived cache.

User dapat berpindah:

Original
→ Natural
→ Clean

tanpa menghancurkan source.

==================================================
18. PAGE MANAGEMENT
===================

Setiap PageObject mendukung:

* crop;
* manual crop;
* rotate left;
* rotate right;
* Natural;
* Clean;
* Original;
* replace;
* retake;
* duplicate;
* delete;
* undo;
* redo.

Document-level:

* add page;
* reorder;
* replace;
* duplicate;
* delete;
* apply enhancement mode to all;
* export PDF.

Manual crop hanya muncul setelah capture.

Tidak ada live four-corner editing.

==================================================
19. MULTI-PAGE WORKFLOW
=======================

Workflow:

Camera
→ Capture
→ automatic processing
→ page preview
→ Retake / Manual Crop / Next
→ back to Camera
→ next page
→ ...
→ Finish
→ Review
→ Page Manager
→ PDF export

User dapat membuat banyak halaman dalam satu scan session.

==================================================
20. IMPORT FROM GALLERY
=======================

Existing photos dapat di-import.

Imported photos menggunakan scanner engine yang sama:

image
→ document detection
→ geometry
→ perspective correction
→ enhancement
→ PageObject

Tidak membuat crop engine terpisah khusus gallery.

==================================================
21. PAGEOBJECT AND DOCUMENT MODEL
=================================

PageObject adalah pusat domain model.

Structure concept:

Document
├── PageObject 1
├── PageObject 2
├── PageObject 3
└── PageObject 4

PageObject tidak boleh bergantung pada rendered bitmap sebagai source of truth.

Document model digunakan oleh:

* Page Manager;
* editor;
* persistence;
* PDF Renderer;
* future OCR.

==================================================
22. MEMORY STRATEGY
===================

Target device:

mid-range Android dan above.

8 GB RAM harus menjadi target utama yang realistis.

Target:

* normal working memory sekitar <= 600–700 MB;
* peak idealnya <= 800 MB;
* tidak menahan seluruh full-resolution pages di RAM;
* source images file-backed;
* large temporary buffers segera dilepas;
* ImageProxy segera ditutup;
* PDF rendering page-by-page.

Full-resolution source boleh disimpan pada disk.

Tidak perlu selalu memuat seluruh source ke giant Bitmap.

Jika hanya ROI yang diperlukan untuk refinement, proses ROI dengan resolution yang cukup.

==================================================
23. STORAGE AND PRIVACY
=======================

Core workflow harus offline.

Tidak memerlukan:

* server;
* account;
* cloud.

Processing:

* AI local;
* geometry local;
* enhancement local;
* page management local;
* PDF local.

Gunakan app-private workspace untuk temporary processing.

Persist scan sessions secara incremental.

Source page tidak dihapus sebelum session selesai atau user menghapusnya.

Tidak mengirim document images sebagai telemetry.

Jangan logging isi dokumen.

==================================================
24. SESSION RECOVERY
====================

ScanSession menyimpan:

* session metadata;
* page order;
* page source references;
* geometry;
* enhancement;
* processing state.

Page disimpan secara incremental.

Tujuan:

session dapat dipulihkan setelah:

* activity recreation;
* backgrounding;
* configuration change;
* memory pressure;
* process restart bila memungkinkan.

==================================================
25. PDF OUTPUT
==============

Page size:

* A4;
* Auto;
* A5;
* B5;
* Letter;
* Original Ratio.

Default:

A4.

Quality:

* High;
* Balanced;
* Small.

Filename dapat diubah sebelum generation.

Estimated output size ditampilkan sebelum generation.

PDF dibuat secara page-by-page.

Physical crop selalu dilakukan sebelum PDF layout.

A4 tidak boleh memaksa detector mencari A4.

Renderer tidak boleh membuat blank space yang tidak perlu.

Aspect ratio hasil scan harus dipertahankan.

==================================================
26. OCR
=======

OCR bukan bagian initial scanner release.

Dapat ditampilkan sebagai:

OCR — Coming Soon

OCR tidak boleh menjadi dependency untuk scanning atau PDF generation.

==================================================
27. AI MODEL STRATEGY
=====================

Live detector menggunakan local segmentation model yang ringan.

Final processing menggunakan geometry refinement yang lebih presisi.

Model harus replaceable.

Concept:

SegmentationModel
→ inference
→ segmentation output
→ geometry engine

Backend dapat menggunakan:

* LiteRT CPU;
* hardware delegate jika benchmark menunjukkan stabilitas/performa yang baik;
* future backend.

Jika acceleration tidak tersedia:

→ CPU fallback.

==================================================
28. AI DATASET
==============

Training dataset harus dimiliki/dikendalikan project dan memiliki coverage luas.

Include:

* paper documents;
* A4;
* A5;
* B5;
* cards;
* receipts;
* posters;
* notes;
* handwriting;
* pencil;
* colored ink;
* white documents;
* dark documents;
* varied backgrounds;
* shadow;
* glare;
* uneven lighting;
* perspective;
* rotation;
* partial occlusion;
* fingers/hands;
* multiple documents;
* partially visible documents;
* concave/complex shapes;
* curled paper;
* damaged paper;
* books;
* open-book spreads.

Primary annotation:

document segmentation mask.

Secondary annotation:

* enclosing quadrilateral;
* corner points;
* page boundaries;
* gutter;
* curvature/dewarp target jika diperlukan.

==================================================
29. EVALUATION METRICS
======================

Jangan menilai model hanya berdasarkan segmentation Dice.

Measure:

* segmentation accuracy;
* corner localization error;
* edge error;
* quadrilateral IoU;
* false crop;
* missed crop;
* target switching;
* temporal jitter;
* latency;
* memory;
* thermal impact;
* battery impact.

Final output:

* readability;
* geometry accuracy;
* dewarp quality;
* color preservation;
* shadow handling;
* glare handling;
* handwriting preservation;
* pencil preservation.

==================================================
30. TESTING
===========

Unit tests:

* geometry;
* homography;
* quad fitting;
* outer envelope;
* coordinate transform;
* tracking;
* target selection;
* PageObject;
* PDF layout.

Golden-image tests:

* fixed source photo corpus;
* expected crop;
* expected geometry;
* perspective correction;
* enhancement;
* dewarp.

Instrumentation:

* CameraX;
* lifecycle;
* capture;
* flash;
* tap-to-guide;
* rotation;
* permissions;
* session recovery;
* gallery import.

Stress testing:

* 20+ page session;
* repeated capture;
* repeated processing;
* repeated mode switching;
* manual editing;
* PDF export at all qualities;
* large input images.

==================================================
31. ERROR HANDLING
==================

Document not detected:

→ manual shutter tetap tersedia.

Geometry imperfect:

→ best-effort processing.

AI error:

→ fallback ke classical CV/manual workflow bila memungkinkan.

Processing error:

→ source tetap ada;
→ user dapat retry/replace/retake.

PDF error:

→ session tetap tersimpan;
→ export dapat diulang.

==================================================
32. DEVELOPMENT PRIORITY
========================

Priority order:

1. Document geometry accuracy.
2. Target selection and temporal tracking.
3. Final image quality.
4. Memory efficiency.
5. Imperfect-condition robustness.
6. Multi-page workflow.
7. Maintainability.
8. UI polish.

Development sequence:

Phase 1:
Camera foundation.

Phase 2:
Local detector.

Phase 3:
Target selection + temporal tracker.

Phase 4:
Full-resolution geometry + One Page.

Phase 5:
Enhancement + quality metrics.

Phase 6:
PageObject + Page Manager + persistence.

Phase 7:
Two Page + gutter + dewarp.

Phase 8:
PDF renderer + export options.

Phase 9:
Import + recovery + stress testing.

==================================================
33. DESIGN BOUNDARY
===================

Design work is intentionally separated from this project brief.

Do not use this brief to determine:

* visual style;
* typography;
* color palette;
* branding;
* UI component appearance;
* animation language;
* polished layout.

The brief defines product behavior and engineering requirements only.

==================================================
34. PRODUCT DEFINITION IN ONE PARAGRAPH
=======================================

LocalScan adalah document scanner native Android yang menggunakan local AI untuk memahami dokumen secara real-time, mempertahankan target yang dipilih user melalui tap dan temporal tracking, melakukan automatic capture atau manual capture, memperbaiki geometry pada full-resolution image, menggunakan enclosing quadrilateral sebagai crop standar termasuk untuk dokumen kompleks/concave, menyediakan One Page dan dedicated Two Page book scanning dengan gutter detection serta mandatory curved-page dewarping, menyediakan Natural dan Clean enhancement yang non-destructive, mengelola hasil melalui PageObject dan multi-page workflow, serta menghasilkan PDF secara lokal tanpa server, account, atau iklan.

==================================================
35. CORE PRODUCT MANTRA
=======================

AI membantu memahami.

Geometry memastikan bentuk.

Processing menjaga kualitas.

User menentukan hasil.

PDF hanya datang setelah scan selesai.
