PRD.md

LOCALSCAN — PRODUCT REQUIREMENTS DOCUMENT

Status: Product and Technical Baseline

Platform: Native Android

Primary Technology Direction:
Kotlin
Jetpack Compose
CameraX
OpenCV
LiteRT / TFLite-compatible on-device ML
Local PDF generation

Design Scope:
Visual design, branding, color system, typography, component styling, animations, and polished UI composition are intentionally excluded from this PRD. Design will be handled as a separate workstream.

==================================================

1. PRODUCT OVERVIEW
   ==================================================

LocalScan adalah aplikasi document scanner native Android yang berfokus pada kualitas hasil scan, automatic document detection, perspective correction, book scanning, local AI processing, dan workflow multi-page yang cepat dan dapat dikendalikan sepenuhnya oleh pengguna.

Produk mengambil pola perilaku yang sudah terbukti dari scanner matang seperti vFlat, CamScanner, Adobe Scan, Genius Scan, SwiftScan, dan implementasi teknis seperti FairScan sebagai benchmark.

Benchmark tersebut digunakan untuk memahami pola UX dan engineering yang sudah digunakan di produk nyata. Tidak ada kebutuhan untuk menyalin kode proprietary, asset, branding, atau implementasi internal milik produk lain.

Tujuan utama produk:

1. Mendeteksi dokumen secara otomatis dan presisi.
2. Menentukan empat batas terluar dokumen untuk menghasilkan enclosing quadrilateral.
3. Mengoreksi perspektif secara otomatis.
4. Menyediakan Two Page Mode khusus untuk buku.
5. Melakukan curved-page dewarping pada Two Page Mode.
6. Memproses seluruh scanning secara lokal di perangkat.
7. Menjaga warna dan informasi dokumen.
8. Membersihkan hasil scan tanpa mengubah isi dokumen.
9. Mendukung scanning multi-page.
10. Memberikan kontrol penuh kepada pengguna terhadap hasil.
11. Membuat PDF secara lokal tanpa server.
12. Tidak menampilkan iklan pada aplikasi.

==================================================
2. PRODUCT PRINCIPLES
=====================

2.1. USER ADALAH OTORITAS TERAKHIR

AI membantu pengguna tetapi tidak boleh mengambil alih keputusan pengguna.

Aplikasi wajib:

* selalu menyediakan manual shutter;
* tidak memblokir capture manual;
* tidak memaksa pengguna melakukan retake;
* tidak menganggap hasil buruk menurut computer vision sebagai hasil yang tidak valid;
* memungkinkan pengguna melakukan undo;
* memungkinkan pengguna mengganti atau retake halaman;
* memungkinkan pengguna crop, rotate, delete, atau mengubah enhancement setelah capture.

Quality assessment digunakan untuk membantu pipeline dan Auto Capture, bukan untuk mengalahkan keputusan pengguna.

2.2. PHYSICAL DOCUMENT GEOMETRY ADALAH SOURCE OF TRUTH

Dokumen harus dideteksi berdasarkan bentuk fisiknya pada foto.

Ukuran PDF tidak boleh menentukan bagaimana AI mencari dokumen.

Contoh:

Pengguna memfoto satu lembar A4 dalam posisi miring.

Pipeline yang benar:

Foto
→ deteksi boundary fisik A4
→ tentukan quadrilateral
→ perspective correction
→ hasil scan
→ PDF layout

Pipeline yang tidak diperbolehkan:

A4 dipilih
→ AI mencari bentuk A4
→ crop berdasarkan template A4

Dengan demikian, pilihan A4, A5, B5, Letter, atau ukuran PDF lain hanya berlaku setelah dokumen berhasil dideteksi dan diproses.

2.3. OUTPUT NORMAL BERBENTUK QUADRILATERAL

Scanner menggunakan empat sisi/sudut terluar sebagai representasi standar dokumen.

Untuk bentuk dokumen normal:

Rectangle
→ quadrilateral

Dokumen yang miring:
→ quadrilateral mengikuti perspektif aktual

Dokumen berbentuk kompleks atau concave:
→ gunakan outer envelope
→ fit menjadi quadrilateral
→ area di dalam quadrilateral tetap dapat masuk ke hasil scan

Scanner tidak menggunakan arbitrary concave polygon sebagai bentuk crop standar.

Contoh konsep:

Dokumen:

┌───────────┐
│           └─────┐
│                 │
└───────┐         │
└─────────┘

Output geometry:

gunakan empat batas/sudut terluar untuk membentuk enclosing quadrilateral.

Akibatnya sebagian background yang berada di dalam envelope dapat tetap masuk.

Hal tersebut merupakan perilaku yang memang dirancang, bukan kegagalan algoritma.

2.4. REAL-TIME PROCESSING DAN FINAL PROCESSING ADALAH DUA MASALAH BERBEDA

Real-time processing memprioritaskan:

* latency rendah;
* responsif;
* stabilitas;
* temporal consistency;
* penggunaan memory rendah.

Post-capture processing memprioritaskan:

* akurasi geometry;
* kualitas gambar;
* corner refinement;
* perspective correction;
* dewarping;
* enhancement.

Tidak diperlukan satu model besar untuk mengerjakan seluruh pipeline.

2.5. AI TIDAK BOLEH MENGHALLUCINATE ISI DOKUMEN

Enhancement hanya boleh memperjelas informasi yang memang sudah ada.

Dilarang:

* menciptakan tulisan;
* menciptakan stroke;
* menciptakan tanda tangan;
* menciptakan cap;
* menggambar ulang isi dokumen secara generatif;
* menghapus informasi penting hanya karena dianggap noise.

==================================================
3. PLATFORM DAN TARGET DEVICE
=============================

3.1. PLATFORM

Aplikasi harus native Android.

Teknologi utama:

* Kotlin
* Jetpack Compose
* CameraX
* OpenCV
* LiteRT / TFLite-compatible local ML runtime
* local PDF generation

Tidak menggunakan web app sebagai basis scanner.

3.2. TARGET DEVICE

Target utama:

* mid-range Android dan di atasnya;
* perangkat dengan 8 GB RAM harus dapat menjalankan workflow normal dengan nyaman;
* tidak boleh membutuhkan flagship device sebagai requirement.

Minimum Android version harus ditentukan berdasarkan compatibility library dan hasil benchmark implementasi final, bukan dipaksakan terlalu rendah bila mengorbankan kualitas atau maintainability.

3.3. MEMORY TARGET

Target engineering:

* normal scanning workflow idealnya berada sekitar atau di bawah 600–700 MB working memory;
* peak memory idealnya tidak melewati sekitar 800 MB;
* tidak menyimpan semua full-resolution page sebagai bitmap sekaligus;
* source image disimpan sebagai file/reference;
* page yang selesai diproses dilepas dari RAM;
* PDF generation dilakukan page-by-page.

Angka tersebut merupakan target engineering yang wajib divalidasi dengan profiling pada perangkat nyata.

==================================================
4. CORE CAMERA ARCHITECTURE
===========================

Arsitektur kamera:

CameraX CameraProvider
→ Preview
→ ImageAnalysis
→ ImageCapture

Preview:
Digunakan untuk tampilan kamera dan overlay.

ImageAnalysis:
Digunakan untuk low-resolution real-time AI inference.

ImageCapture:
Digunakan untuk mengambil foto final dengan kualitas setinggi yang masuk akal.

Konsep lengkap:

CAMERA

CameraX CameraProvider

Preview
→ live camera view

ImageAnalysis
→ low-resolution frame
→ AI detector
→ candidate geometry
→ target selection
→ temporal tracking
→ stable geometry
→ overlay
→ Auto Capture readiness

ImageCapture
→ full-resolution image file

Kemudian:

full-resolution image
→ coordinate transformation
→ geometry refinement
→ One Page atau Two Page pipeline
→ quality assessment
→ enhancement
→ PageObject
→ Page Manager
→ PDF Renderer
→ PDF file

ImageCapture sebaiknya menulis hasil ke file secara langsung atau melalui mekanisme file-backed yang setara untuk mengurangi memory pressure.

Full-resolution capture tidak boleh ditahan sebagai bitmap besar sepanjang session jika tidak diperlukan.

==================================================
5. CAMERA REQUIREMENTS
======================

5.1. CUSTOM CAMERA

Aplikasi harus menyediakan camera interface sendiri.

Jangan membuka aplikasi kamera bawaan perangkat untuk melakukan scanning.

5.2. PREVIEW

Preview harus dioptimalkan untuk dokumen:

* responsif;
* stabil;
* overlay tidak menyebabkan lag besar;
* aspect ratio dan transform harus dikelola dengan benar.

5.3. IMAGEANALYSIS

ImageAnalysis digunakan untuk real-time detector.

Gunakan strategi latest-frame / KEEP_ONLY_LATEST atau pendekatan setara.

Jika AI masih memproses frame sebelumnya, frame lama tidak boleh menumpuk.

Konsep:

new frame
→ detector busy?
→ ya: buang frame stale
→ tidak: proses frame terbaru

Jangan membuat antrian panjang frame kamera.

ImageProxy harus segera dilepas setelah selesai digunakan.

Inference tidak boleh berjalan di main thread.

5.4. IMAGECAPTURE

ImageCapture memprioritaskan kualitas hasil dibanding latency minimum.

Capture harus:

* menggunakan resolusi tinggi yang sesuai kemampuan device;
* menyimpan hasil ke file;
* mempertahankan orientasi/metadata dengan benar;
* menghindari full-resolution bitmap jangka panjang di RAM.

5.5. AUTOFOCUS

Default:

* continuous autofocus;
* automatic exposure;
* automatic white balance.

Tidak menggunakan hard refocus setiap 4 detik.

Sebagai gantinya dapat digunakan quality/focus watchdog sekitar setiap 4 detik.

Watchdog hanya melakukan evaluasi:

Apakah fokus masih baik?
Apakah target berubah?
Apakah image quality menurun?
Apakah kamera perlu melakukan re-meter/re-focus?

Jika fokus masih stabil:
→ jangan paksa refocus.

Jika kondisi berubah:
→ lakukan re-meter atau refocus.

5.6. TAP-TO-GUIDE

Satu tap pada preview memiliki dua fungsi:

1. memberi tahu AI dokumen mana yang dimaksud pengguna;
2. melakukan camera metering/focus pada area tap.

Urutan:

tap
→ identifikasi candidate yang mengandung titik tap
→ tingkatkan prioritas candidate tersebut
→ target lock
→ focus/exposure metering pada titik tap

Pengguna tidak perlu menggeser empat sudut saat kamera sedang aktif.

Manual corner crop hanya tersedia setelah capture.

5.7. FLASH MODES

Tersedia:

1. Flash Off
2. Torch / Always On
3. Flash on Capture

Default:

Flash Off

Aplikasi mengingat pilihan flash terakhir pengguna.

==================================================
6. DOCUMENT DETECTION
=====================

6.1. LOCAL AI SEGMENTATION

Deteksi dokumen menggunakan local on-device AI segmentation sebagai komponen utama.

Model sebaiknya menghasilkan document mask / dense document representation.

Model tidak harus langsung menghasilkan empat corner.

Segmentation digunakan sebagai dasar geometry engine.

6.2. DETECTION PIPELINE

AI segmentation mask
→ noise cleanup
→ boundary extraction
→ outer envelope estimation
→ quadrilateral fitting
→ corner refinement
→ candidate geometry

Segmentation mask bukan hasil final crop.

6.3. MULTIPLE DOCUMENTS

Jika frame berisi lebih dari satu dokumen, sistem membuat beberapa candidate.

Candidate selection mempertimbangkan:

* tap-to-guide prior;
* target yang sedang dikunci;
* apakah candidate mengandung titik tap;
* geometry confidence;
* temporal consistency;
* document coverage;
* candidate persistence;
* image quality;
* spatial stability.

6.4. TARGET LOCKING

Misalnya:

A4 #1 = target
A4 #2 = candidate lain

Frame 1:
A4 #1 dipilih

Frame 2:
A4 #1 dipilih

Frame 3:
A4 #2 memiliki raw confidence sedikit lebih tinggi

Sistem tetap mempertahankan A4 #1 sebagai target.

Sistem tidak boleh berpindah target hanya karena satu frame menghasilkan skor lebih tinggi.

Target dapat berubah ketika:

* user melakukan tap pada dokumen lain;
* target keluar frame;
* target benar-benar hilang;
* tracking gagal dalam jangka waktu tertentu.

==================================================
7. TEMPORAL TRACKING
====================

Temporal tracking adalah komponen wajib.

Jangan menggunakan perilaku:

Frame 1 → detect → langsung render
Frame 2 → detect → langsung render
Frame 3 → detect → langsung render

karena dapat menghasilkan jitter.

Pipeline:

Detector
→ raw candidate
→ target selection
→ temporal tracker
→ smoothing/filtering
→ stable geometry
→ overlay

State machine:

SEARCHING
→ CANDIDATE
→ TRACKING
→ STABLE
→ CAPTURE READY
→ CAPTURED

Tracker harus menstabilkan:

* empat corner;
* outer envelope;
* ukuran;
* orientasi;
* confidence;
* identitas candidate.

Tracker harus dapat menoleransi detector dropout singkat.

Jika detector kehilangan target hanya sesaat:
→ jangan langsung menghapus overlay.

Jitter menjadi salah satu metric benchmark.

Metode smoothing yang akan digunakan harus dipilih melalui benchmark antara opsi yang sesuai, misalnya exponential smoothing, One Euro Filter, Kalman-like filtering, atau metode lain.

==================================================
8. LIVE OVERLAY
===============

Boundary overlay selalu ditampilkan selama kamera aktif jika sistem mempunyai estimate yang berarti.

Overlay bukan template A4/A5/Letter.

Overlay merepresentasikan physical document boundary.

Confidence behavior:

HIGH:

* boundary stabil;
* dapat menjadi capture-ready.

MEDIUM:

* boundary tetap terlihat;
* belum boleh Auto Capture.

LOW:

* best-effort boundary tetap boleh ditampilkan;
* tidak boleh Auto Capture;
* sistem tidak boleh berpura-pura yakin.

Yang berubah berdasarkan confidence adalah perilaku sistem, bukan keberadaan overlay.

==================================================
9. AUTO CAPTURE
===============

9.1. AUTO CAPTURE ON

Auto Capture hanya boleh aktif jika:

* candidate valid;
* target identity stabil;
* geometry stabil;
* capture readiness memadai;
* tidak ada severe motion;
* target berada pada kondisi capture yang masuk akal.

Jika siap:

stable
→ capture ready
→ countdown 2 detik
→ ImageCapture

Countdown 2 detik wajib digunakan.

9.2. AUTO CAPTURE OFF

User menekan shutter.

Capture dilakukan.

Tidak ada quality gate yang boleh memblokir.

9.3. MANUAL SHUTTER

Manual shutter selalu tersedia.

Walaupun Auto Capture ON:

user tetap dapat menekan shutter kapan saja.

9.4. NO FORCED RETAKE

Setelah capture:

hasil harus diterima dan diproses.

Tidak boleh ada sistem yang memutuskan:

"hasil terlalu buruk, user wajib retake."

User sendiri yang menentukan apakah hasil sudah cukup bagus.

==================================================
10. COORDINATE TRANSFORMATION
=============================

Quad yang diperoleh dari ImageAnalysis tidak boleh langsung digunakan sebagai koordinat final pada full-resolution image.

Contoh:

ImageAnalysis:
1280 × 720

ImageCapture:
4032 × 3024

Quad dari analysis frame harus melalui transformation pipeline.

Analysis coordinate
→ CameraX transformation
→ sensor/normalized coordinate
→ capture-image coordinate
→ local refinement
→ final quad

Jangan hanya menggunakan:

captureWidth / analysisWidth

dan

captureHeight / analysisHeight

sebagai scaling sederhana.

Transformation harus memperhitungkan:

* crop rectangle;
* sensor orientation;
* rotation;
* PreviewView viewport;
* aspect ratio;
* CameraX transformation;
* capture output dimensions.

Wajib ada test untuk:

* portrait;
* landscape;
* device rotation;
* 4:3 vs wide preview;
* resolusi capture berbeda;
* berbagai crop/viewport configuration.

==================================================
11. DOCUMENT GEOMETRY ENGINE
============================

Buat dedicated geometry module.

DocumentGeometryEngine

Komponen:

* Candidate Processing
* Boundary Extraction
* Outer Envelope Estimation
* Quadrilateral Fitting
* Corner Refinement
* Edge Refinement
* Perspective Estimation
* Book Spread Detection
* Page Boundary Detection
* Gutter Detection
* Curvature Estimation
* Dewarp Mesh Generation
* Coordinate Mapping

11.1. SINGLE PAGE

Pipeline:

Segmentation Mask
→ Outer Envelope
→ Quadrilateral Fit
→ Corner Refinement
→ Homography
→ Rectified Page

11.2. OUTER ENVELOPE

Jangan memaksa:

segmentation mask
→ polygon asli
→ arbitrary crop

Sebaliknya:

segmentation mask
→ physical boundary
→ outer envelope
→ enclosing quadrilateral

Tujuannya adalah scanner behavior yang konsisten.

11.3. COMPLEX / CONCAVE SHAPE

Untuk dokumen seperti:

┌───────────┐
│           └─────┐
│                 │
└───────┐         │
└─────────┘

sistem harus memilih empat batas terluar.

Area yang berada di dalam enclosing quadrilateral tetapi sebenarnya merupakan background dapat tetap masuk ke hasil.

Hal ini disengaja.

11.4. CORNER REFINEMENT

Corner refinement dilakukan setelah low-resolution detection.

Refinement memanfaatkan informasi full-resolution dan local edge structure untuk memperbaiki:

* posisi corner;
* panjang sisi;
* orientasi;
* intersection antar edge.

11.5. PERSPECTIVE CORRECTION

Perspective correction harus:

* membuat dokumen terlihat front-facing;
* mempertahankan content;
* tidak menambahkan background yang tidak perlu;
* tidak melakukan stretching abnormal;
* menggunakan geometry aktual dokumen.

Untuk planar single-page document, homography/perspective transformation digunakan bila sesuai.

==================================================
12. TWO PAGE MODE
=================

Two Page Mode adalah pipeline khusus untuk open-book spread.

Two Page bukan sekadar:

wide quadrilateral
→ split 50/50

12.1. PIPELINE

Full-resolution image
→ book/spread detection
→ left/right page boundaries
→ gutter detection
→ curvature estimation
→ dewarp mesh
→ nonlinear remapping
→ page flattening
→ left/right separation
→ perspective rectification
→ enhancement
→ two PageObjects

12.2. GUTTER

Sistem harus berusaha mengenali:

* lokasi gutter;
* bentuk gutter;
* perubahan geometry di sekitar binding;
* perbedaan posisi gutter kiri/kanan bila diperlukan.

12.3. CURVATURE

Curvature estimation harus memperhitungkan bahwa halaman buku tidak selalu datar.

Model harus dapat bekerja pada:

* buku yang tidak terbuka 180 derajat;
* page curl;
* curvature berbeda antara halaman kiri dan kanan;
* page edge yang tidak sempurna.

12.4. DEWARP

Curved-page dewarping WAJIB dilakukan dalam Two Page Mode.

Tidak boleh bergantung pada user mengaktifkannya secara manual.

Tujuan:

* meluruskan halaman;
* mengurangi efek melengkung;
* menjaga teks tetap terbaca;
* menghasilkan dua halaman yang terasa seperti hasil scan datar.

12.5. SPLIT

Setelah geometry dan dewarp:

left page
→ PageObject pertama

right page
→ PageObject kedua

Default ordering:

left → right

Harus tersedia mekanisme document-level untuk membalik urutan ketika workflow membaca dari kanan ke kiri.

12.6. IMPERFECT BOOK

Pipeline harus menerima foto yang tidak sempurna.

Tidak boleh memblokir pengguna hanya karena:

* gutter gelap;
* buku sedikit miring;
* page curvature besar;
* halaman kiri/kanan tidak identik;
* page boundary sedikit rusak;
* lighting tidak sempurna.

==================================================
13. QUALITY ASSESSMENT
======================

Quality assessment memiliki dua tahap.

13.1. LIVE CAPTURE READINESS

Dipakai untuk Auto Capture.

Metric dapat mencakup:

* document confidence;
* target persistence;
* geometry stability;
* blur/motion estimate;
* coverage;
* temporal stability.

Tujuan:

Menentukan apakah kondisi cukup stabil untuk menjalankan countdown Auto Capture.

13.2. POST-CAPTURE QUALITY

Compute:

* blur score;
* glare score;
* shadow score;
* exposure score;
* geometry score;
* crop confidence;
* corner confidence;
* document coverage.

Quality score boleh menjadi metadata/internal diagnostic.

13.3. USER AUTONOMY

Quality score tidak boleh:

* memblokir hasil;
* memaksa retake;
* membatalkan capture;
* menyatakan user wajib mengambil ulang foto.

Jika user menganggap hasil sudah bagus:
→ hasil tetap dipertahankan.

==================================================
14. ENHANCEMENT
===============

Dua mode utama:

1. Natural / Color
2. Clean / Intelligent Clean

14.1. NATURAL / COLOR

Tujuan:

* menjaga warna asli;
* koreksi exposure secara ringan;
* koreksi white balance secara ringan;
* illumination normalization;
* noise reduction secukupnya;
* mempertahankan karakter natural.

Harus menjaga:

* stamp/cap;
* tanda tangan;
* colored handwriting;
* highlight;
* diagrams;
* colored ink.

14.2. CLEAN / INTELLIGENT CLEAN

Tujuan:

* background lebih bersih;
* shadow suppression;
* illumination normalization;
* contrast improvement;
* local detail enhancement;
* denoise;
* selective sharpening;
* color-aware processing.

Harus tetap aman untuk:

* text;
* handwriting;
* pencil;
* faint pen strokes;
* signatures;
* colored marks.

Jangan menjadikan mode Clean sebagai pure black-and-white conversion yang agresif.

14.3. GENERATIVE PROCESSING

Generative enhancement dilarang untuk core scanner pipeline.

Scanner hanya boleh memperjelas informasi yang sudah tersedia.

==================================================
15. NON-DESTRUCTIVE PROCESSING
==============================

Source asset harus dipisahkan dari hasil render.

Konsep:

RAW SOURCE
→ geometry parameters
→ rotation
→ enhancement parameters
→ render

User dapat berpindah:

Natural
→ Clean
→ Original

tanpa menghancurkan source.

Rendered result adalah derived artifact/cache.

Cache dapat dihapus dan dibuat ulang.

Source image tetap menjadi sumber utama.

==================================================
16. PAGE OBJECT
===============

PageObject adalah pusat domain model.

Conceptual structure:

PageObject

* id
* sourceAsset
* pageGeometry
* rotation
* enhancementParameters
* metadata
* qualityMetrics

Contoh:

PageObject
→ source image reference
→ crop geometry
→ rotation
→ enhancement
→ origin
→ capture metadata
→ processing metadata

Potential geometry types:

* SinglePageGeometry
* TwoPageLeftGeometry
* TwoPageRightGeometry
* ManualCropGeometry

Rendered bitmap tidak boleh menjadi source of truth.

==================================================
17. PAGE MANAGER
================

Page Manager mengelola seluruh PageObject dalam sebuah Document.

Setiap page wajib mendukung:

* crop;
* manual crop;
* rotate 90° left;
* rotate 90° right;
* Natural;
* Clean;
* Original;
* replace/retake;
* delete;
* duplicate;
* undo;
* redo.

Document-level actions:

* add page;
* reorder page;
* delete page;
* replace page;
* duplicate page;
* apply enhancement mode to all;
* export PDF.

Manual crop hanya muncul setelah capture.

Tidak ada live four-corner manual editing.

==================================================
18. MULTI-PAGE WORKFLOW
=======================

Workflow utama:

Camera
→ Capture
→ automatic processing
→ page preview
→ Retake / Manual Crop / Next
→ kembali ke camera
→ capture page berikutnya
→ ...
→ Finish
→ Page Review
→ Page Manager
→ PDF export

User dapat melakukan banyak page capture dalam satu session.

Tidak perlu kembali ke home screen setelah setiap page.

==================================================
19. IMPORT FROM GALLERY
=======================

Aplikasi harus mendukung import foto yang sudah ada.

Imported image masuk ke pipeline yang sama sebisa mungkin.

Photo
→ document detection
→ geometry
→ perspective correction
→ enhancement
→ PageObject

Jangan membuat dua crop engine yang sepenuhnya berbeda antara kamera dan gallery import.

Gallery import juga menjadi salah satu sumber penting untuk regression testing document detector.

==================================================
20. STORAGE DAN PRIVACY
=======================

20.1. OFFLINE-FIRST

Core scanning harus dapat bekerja tanpa internet.

Tidak boleh membutuhkan server untuk:

* AI detection;
* geometry;
* enhancement;
* page management;
* PDF creation.

Tidak membutuhkan account untuk core scanning.

20.2. LOCAL PROCESSING

Captured document tidak boleh dikirim ke server sebagai requirement pipeline.

Core processing dilakukan pada device.

20.3. STORAGE MODEL

Gunakan:

* app-private working directory untuk temporary/intermediate assets;
* persistent local session storage;
* user-accessible export location untuk file final.

20.4. TEMPORARY ASSETS

Temporary assets dibersihkan setelah:

* export berhasil;
* session dibatalkan;
* session cleanup;
* recovery/session expiration policy terpenuhi.

Source page tidak boleh dihapus sebelum session benar-benar selesai atau user secara eksplisit menghapusnya.

20.5. PERMISSIONS

Minta permission seminimal mungkin.

Camera diperlukan untuk scanning menggunakan kamera.

Media access harus menggunakan API/picker Android modern yang sesuai dengan versi Android target.

==================================================
21. SCAN SESSION RECOVERY
=========================

Session harus disimpan secara incremental.

Contoh:

ScanSession

Page 01
Page 02
Page 03
Page 04
Metadata

Jangan menunggu sampai PDF dibuat sebelum menyimpan informasi page.

Setiap page selesai:
→ persist metadata
→ persist source/reference
→ persist processing state

Tujuan:

Session tetap dapat dipulihkan setelah:

* activity recreation;
* backgrounding;
* configuration change;
* moderate memory pressure;
* process restart bila memungkinkan.

==================================================
22. PDF EXPORT
==============

PDF dibuat setelah page processing dan review selesai.

22.1. PAGE SIZE OPTIONS

Tersedia:

* A4
* Auto
* A5
* B5
* Letter
* Original Ratio

Default:

A4

22.2. PHYSICAL CROP FIRST

Urutan wajib:

Capture
→ document crop
→ geometry correction
→ enhancement
→ processed page
→ PDF layout

Bukan:

A4 selected
→ AI searches for A4

22.3. NO ARTIFICIAL BLANK SPACE

Renderer tidak boleh menambahkan white space yang tidak perlu.

Khusus fixed page size seperti A4:

* preserve processed image aspect ratio;
* gunakan area halaman secara maksimal dan masuk akal;
* jangan stretching secara abnormal;
* jangan menambahkan margin putih besar tanpa alasan.

Auto dan Original Ratio harus tersedia untuk kebutuhan non-standard.

22.4. QUALITY OPTIONS

Tersedia:

* High
* Balanced
* Small

Parameter final seperti output resolution dan JPEG quality harus ditentukan melalui benchmark.

Tidak boleh ditentukan asal.

22.5. ESTIMATED FILE SIZE

Sebelum generate PDF:

tampilkan estimasi ukuran berdasarkan:

* jumlah page;
* processed image dimensions;
* compression;
* selected quality;
* PDF overhead.

Estimasi harus diberi label sebagai estimasi.

22.6. FILE NAME

User dapat mengganti nama file sebelum PDF dibuat.

22.7. STREAMED PDF RENDERING

PDF harus dibuat page-by-page.

PageObject 1
→ render
→ write PDF
→ release memory

PageObject 2
→ render
→ write PDF
→ release memory

dan seterusnya.

Jangan menyimpan semua rendered page bitmap dalam RAM.

==================================================
23. OCR
=======

OCR bukan bagian dari initial scanner release.

Architecture harus tetap dapat diperluas untuk OCR di masa depan.

Boleh ditampilkan di future UI sebagai:

OCR — Coming Soon

OCR tidak boleh menjadi dependency untuk:

* scanning;
* cropping;
* perspective correction;
* enhancement;
* PDF export.

==================================================
24. PERFORMANCE REQUIREMENTS
============================

24.1. LIVE DETECTION

Ukur:

* AI inference latency;
* overlay latency;
* effective FPS;
* dropped frames;
* temporal jitter;
* target switching;
* CPU usage;
* GPU/delegate behavior bila digunakan.

24.2. CAPTURE PROCESSING

Ukur:

* capture-to-preview latency;
* geometry processing time;
* enhancement processing time;
* peak memory;
* temporary disk usage.

24.3. PDF EXPORT

Ukur:

* export time per page count;
* peak memory;
* final PDF size.

24.4. THERMAL / BATTERY

Benchmark:

* long camera sessions;
* 20+ page scans;
* repeated image processing;
* repeated PDF export.

Inference tidak boleh dijalankan pada rate maksimum jika rate lebih rendah sudah menghasilkan UX yang sama.

==================================================
25. AI MODEL STRATEGY
=====================

Gunakan local segmentation model sebagai primary document detector.

Model harus replaceable.

Conceptual interface:

SegmentationModel
→ infer(ModelInput)
→ ModelOutput

Backend dapat berupa:

* LiteRT CPU;
* hardware acceleration/delegate jika stabil;
* future optimized backend.

Pemilihan acceleration harus berdasarkan benchmark nyata.

Jika hardware acceleration tidak tersedia:

→ aplikasi tetap berfungsi dengan CPU.

==================================================
26. AI TRAINING DAN DATASET
===========================

Project harus memiliki training/evaluation pipeline sendiri.

Jangan bergantung pada proprietary dataset atau third-party weights tanpa verifikasi license compatibility.

Dataset harus mencakup:

* A4;
* A5;
* B5;
* kartu/ID-card-like document;
* receipt;
* notebook;
* book pages;
* posters;
* handwritten notes;
* pencil;
* colored ink;
* white paper;
* dark documents;
* berbagai background;
* shadow;
* glare;
* uneven lighting;
* perspective;
* rotation;
* partial occlusion;
* finger/hand occlusion;
* multiple documents;
* partially visible documents;
* concave/complex documents;
* curled paper;
* damaged paper;
* book spreads.

26.1. PRIMARY ANNOTATION

Document segmentation mask.

26.2. SECONDARY ANNOTATION

Jika dibutuhkan:

* enclosing quadrilateral;
* expected corners;
* edge information.

Two Page:

* left page;
* right page;
* gutter;
* page boundaries;
* curvature/dewarp target jika dataset memungkinkan.

26.3. DATASET SPLIT

Pisahkan:

* training;
* validation;
* held-out test.

Hindari leakage dari foto yang terlalu mirip.

==================================================
27. MODEL EVALUATION
====================

Model tidak boleh dinilai hanya dengan segmentation Dice.

Ukur:

* segmentation quality;
* corner localization error;
* edge error;
* quadrilateral IoU;
* crop false positive;
* crop false negative;
* target switching rate;
* temporal jitter;
* inference latency;
* memory use;
* thermal impact;
* battery impact.

Final scan quality juga harus diuji:

* text readability;
* perspective accuracy;
* dewarp quality;
* color preservation;
* shadow suppression;
* glare handling;
* handwriting preservation;
* pencil preservation.

==================================================
28. TESTING REQUIREMENTS
========================

28.1. UNIT TESTS

Wajib mencakup:

* homography;
* quadrilateral fitting;
* outer envelope fitting;
* corner refinement;
* coordinate transformations;
* target selection;
* temporal tracking;
* page ordering;
* PageObject persistence;
* PDF layout;
* file-size estimation.

28.2. INSTRUMENTATION TESTS

Wajib mencakup:

* CameraX lifecycle;
* permissions;
* capture;
* rotation;
* flash;
* tap-to-guide;
* background/foreground;
* session recovery;
* import workflow.

28.3. GOLDEN IMAGE TESTS

Gunakan corpus foto tetap.

Bandingkan:

* crop geometry;
* corner positions;
* output dimensions;
* perspective correction;
* enhancement;
* dewarp;
* ordering.

28.4. STRESS TEST

Minimal:

* 20-page scan;
* repeated capture;
* repeated Clean/Natural switching;
* repeated manual editing;
* PDF export High/Balanced/Small;
* large source images;
* gallery batch import.

==================================================
29. ERROR HANDLING
==================

29.1. DOCUMENT TIDAK TERDETEKSI

Tetap izinkan manual shutter.

29.2. GEOMETRY TIDAK SEMPURNA

Lakukan best-effort processing.

Jangan memaksa retake.

29.3. AI MODEL ERROR

Jika local model gagal:

→ fallback ke classical CV dan/atau manual workflow bila memungkinkan.

AI failure tidak boleh membuat seluruh aplikasi unusable.

29.4. PROCESSING FAILURE

Source asset tetap dipertahankan.

User dapat:

* retry;
* edit;
* replace;
* retake.

29.5. PDF EXPORT FAILURE

Session dan PageObject tetap dipertahankan.

User dapat mengulangi export.

==================================================
30. SECURITY DAN PRIVACY
========================

Core scanner tidak memerlukan network.

Document images tidak boleh di-upload sebagai bagian dari normal scanning workflow.

Jangan mengirim document image sebagai telemetry.

Jangan logging:

* document content;
* private document images;
* OCR text pada future implementation;
* user document metadata yang tidak dibutuhkan.

Temporary assets harus disimpan secara private selama processing.

==================================================
31. OUT OF SCOPE
================

Tidak termasuk initial scanner release:

* OCR implementation;
* cloud synchronization;
* server-side processing;
* collaboration;
* generative restoration;
* visual design system;
* branding;
* marketing pages;
* ad system;
* visual prototype specification.

Visual design akan dikerjakan secara terpisah.

==================================================
32. DEVELOPMENT PRINCIPLES
==========================

1. Scanner harus terasa seperti scanner, bukan kamera yang diberi crop tool.

2. User selalu dapat mengambil keputusan akhir.

3. AI membantu user tanpa mengambil kontrol penuh.

4. Physical document boundary selalu menjadi source of truth.

5. PDF page size tidak memengaruhi document detection.

6. Complex documents menggunakan enclosing quadrilateral.

7. One Page dan Two Page merupakan pipeline geometry yang berbeda.

8. Two Page selalu melakukan curved-page dewarping.

9. Live detection harus stabil secara temporal.

10. Low-resolution live geometry bukan final geometry.

11. Full-resolution image digunakan untuk final refinement.

12. Enhancement bersifat non-destructive.

13. Quality score tidak boleh memaksa retake.

14. Semua core processing berjalan lokal.

15. Memory harus diperlakukan sebagai resource terbatas.

16. Tidak menyimpan semua full-resolution pages di RAM.

17. PDF dibuat secara streaming page-by-page.

18. Semua fitur harus dapat diuji dengan deterministic test corpus bila memungkinkan.

==================================================
33. FUNCTIONAL ACCEPTANCE CRITERIA
==================================

Release candidate dianggap memenuhi baseline apabila:

1. User dapat melakukan scanning seluruhnya secara lokal.

2. Camera UI dimiliki oleh aplikasi.

3. Live document boundary dapat ditampilkan.

4. Boundary overlay tetap tersedia ketika confidence belum tinggi, selama ada estimate yang berarti.

5. User dapat tap sebuah dokumen untuk memberitahu AI target yang dimaksud.

6. Target locking mencegah AI berpindah ke candidate lain secara tiba-tiba.

7. Temporal tracking membuat boundary tidak gemetar secara berlebihan.

8. Auto Capture menggunakan countdown dua detik.

9. Manual shutter selalu tersedia.

10. Manual capture tidak dapat diblokir oleh quality assessment.

11. Tidak ada sistem forced retake.

12. Manual crop hanya tersedia setelah capture.

13. One Page menghasilkan satu PageObject.

14. Two Page menghasilkan dua PageObject dalam urutan left-to-right default.

15. Two Page menggunakan gutter detection.

16. Two Page menggunakan curvature estimation.

17. Two Page selalu melakukan curved-page dewarping.

18. Complex/concave document diproses menggunakan enclosing quadrilateral.

19. Low-resolution analysis coordinates ditransformasi dengan benar ke full-resolution capture coordinates.

20. Full-resolution geometry direfine sebelum hasil final.

21. Perspective correction menghasilkan dokumen yang front-facing.

22. Enhancement dapat menggunakan Natural atau Clean.

23. Enhancement bersifat non-destructive.

24. Page dapat di-crop.

25. Page dapat di-rotate.

26. Page dapat di-delete.

27. Page dapat di-duplicate.

28. Page dapat di-replace/retake.

29. Page dapat di-reorder.

30. Undo/redo tersedia untuk editing yang relevan.

31. Session dapat menyimpan banyak PageObject.

32. Scan session dapat dipulihkan setelah lifecycle interruption bila memungkinkan.

33. Gallery image dapat diproses menggunakan scanner engine.

34. PDF tersedia dalam A4, Auto, A5, B5, Letter, dan Original Ratio.

35. A4 menjadi default PDF page size.

36. PDF page size tidak memengaruhi document detection.

37. Renderer tidak menciptakan blank space yang tidak perlu.

38. Quality tersedia sebagai High, Balanced, dan Small.

39. Estimated output file size ditampilkan sebelum export.

40. Filename dapat diganti sebelum PDF generation.

41. PDF dibuat secara page-by-page.

42. Semua core processing tidak membutuhkan server.

43. OCR belum menjadi dependency initial scanner.

44. Peak memory diukur pada perangkat 8 GB RAM.

45. Target memory ideal sekitar <= 600–700 MB untuk workflow normal dan <= 800 MB untuk peak yang dapat ditoleransi.

46. 20-page stress test berhasil tanpa memory growth yang tidak terkendali.

47. Detector, tracker, geometry engine, enhancement, PageObject, persistence, dan PDF renderer tetap terpisah secara arsitektural.

==================================================
34. REFERENCE PRODUCTS
======================

Produk benchmark:

vFlat

Digunakan sebagai referensi untuk:

* automatic document detection;
* document/book cropping;
* curved-page flattening;
* two-page book scanning.

CamScanner

Digunakan sebagai referensi untuk:

* automatic crop;
* multi-page workflow;
* enhancement;
* curve correction;
* page editing.

Adobe Scan

Digunakan sebagai referensi untuk:

* automatic document detection;
* scan workflow;
* page management;
* adding/importing images.

Genius Scan

Digunakan sebagai referensi untuk:

* page processing;
* export controls;
* page size;
* quality/resolution concepts;
* document workflow.

FairScan

Digunakan sebagai technical reference untuk:

* Android-native scanning architecture;
* CameraX;
* local ML;
* OpenCV;
* local PDF generation.

Benchmarking hanya berarti mengambil pola perilaku dan engineering yang relevan.

Tidak boleh menyalin:

* proprietary code;
* branding;
* proprietary assets;
* closed-source implementation;
* third-party dataset/model yang lisensinya tidak kompatibel.

==================================================
35. FINAL PRODUCT DEFINITION
============================

LocalScan adalah scanner Android native yang:

* melihat dokumen menggunakan local AI;
* menampilkan boundary secara live;
* memungkinkan user menentukan target dengan tap;
* menjaga target melalui temporal tracking;
* menggunakan Auto Capture dengan countdown dua detik;
* selalu memberikan manual shutter;
* memproses hasil secara full-resolution dengan geometry refinement;
* menghasilkan enclosing quadrilateral untuk dokumen normal maupun complex/concave;
* melakukan perspective correction;
* mempunyai One Page dan Two Page Mode;
* melakukan gutter detection dan curved-page dewarping pada Two Page;
* mempertahankan warna dan informasi dokumen;
* menyediakan Natural dan Clean enhancement;
* tidak memaksa retake;
* menggunakan PageObject sebagai pusat document model;
* menyediakan editing multi-page;
* membuat PDF secara lokal;
* tidak membutuhkan server;
* tidak membutuhkan account untuk core workflow;
* tidak menampilkan iklan;
* dan dirancang agar tetap layak digunakan pada perangkat Android mid-range dengan 8 GB RAM.

PRINSIP UTAMA:

"AI membantu scanner memahami dokumen, tetapi user tetap menentukan apa yang dianggap sebagai hasil yang benar."

"Physical document geometry is authoritative; PDF page size must never dictate document detection or create unnecessary background."

"Two Page Mode is a dedicated book-spread pipeline, not a simple two-way crop."

"Enhancement may clarify existing information, but must never invent document content."
