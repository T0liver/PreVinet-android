# PreViNet Android App — Specification

**Version:** 1.0
**Date:** June 2026
**Scope:** New `android/` directory — native Android client
**Target model:** Claude Fable

---

## 1. What and Why

PreViNet collects annotated photos of diseased grape leaves from vineyard workers in the field and returns SAM segmentation results. The web app (in `web/`) serves iOS and desktop users. This Android app is the primary field client: most users carry Android phones into the vineyard, often with **no mobile coverage**.

The Android app shares the design language defined in `UI_DESIGN_SPEC.md` so web and Android feel like one product, and adds three capabilities the web cannot provide well:

1. **Offline-first capture** — photograph and annotate with no connection; submissions queue locally and upload automatically when connectivity returns
2. **Local image cache** — submitted photos are kept on-device, so the result screen renders instantly from local files and only the small mask PNG is downloaded
3. **Closing the async loop** — local notifications (upload finished, results reminder) and App Links bring users back to their results without needing to save a URL manually

---

## 2. Technology Decisions

| Concern | Choice | Reason |
|---|---|---|
| Language / UI | **Kotlin + Jetpack Compose (Material 3)** | Required; matches backend language; M3 maps 1:1 to the design tokens |
| Architecture | Single activity, MVVM, manual DI (`AppContainer`) | App is small; no Hilt/Koin keeps the build simple and reliable |
| Navigation | Navigation Compose | Deep links to `/result/{id}` declared in the nav graph |
| Local DB | **Room** | Offline outbox (submissions + photos + bboxes) and submission history |
| Background upload | **WorkManager** (`CoroutineWorker`, network constraint, exponential backoff) | Survives process death; uploads queued submissions when online |
| Prefs | DataStore Preferences | Consent flag, cached disease list, notification prefs |
| HTTP | **Ktor Client** (OkHttp engine) + kotlinx.serialization | Upload progress via `onUpload`; same stack family as the backend |
| Images | **Coil 3** | Loads local files and remote thumbnails/masks with one API; disk cache for masks |
| Photo intake | Photo Picker API + `ACTION_IMAGE_CAPTURE` | Per `UI_DESIGN_SPEC.md §8.6`; both work fully offline |
| Min/target SDK | minSdk 26, targetSdk 35 | Covers field devices several years old |

**No backend changes.** The API contract is fixed (verified against `backend/src/main/kotlin/com/previNet/routes/SubmissionsRoute.kt`):

- `POST /api/v1/submissions` multipart fields: `images[]` (files), `annotation_tier`, `disease_label`, `bboxes[]` (one JSON `{"x","y","w","h"}` per image, normalized 0–1, `null` literal for images without a box; required to match image count when tier = 3), `gps_lat`, `gps_lon`, `notes`, `device_fingerprint`
- `GET /api/v1/submissions/{id}` — status polling
- `GET /api/v1/submissions/{id}/result` — result list with `thumbnail_url`, `mask_url`, `confidence`, `quality_score`
- `POST /api/v1/submissions/{id}/correction` — body `{ "image_id": "...", "corrected_bbox": {"x","y","w","h"} }`
- `GET /api/v1/diseases` — label list (cached locally with a bundled fallback)

---

## 3. Android-Specific Features

### 3.1 Offline-first submission queue (outbox)

- Photos taken/picked are immediately copied into app-private storage (downscaled to ≤2048 px long edge, JPEG q85; EXIF GPS stripped client-side when the user opts out of location sharing)
- The whole submission (photos, tier, label, bboxes, notes) is saved to Room as a local record
- Upload always goes through a WorkManager `UploadWorker` (unique work per submission, `NETWORK_CONNECTED` constraint, exponential backoff). Online: runs immediately and the UI observes its progress. Offline: waits silently for connectivity
- Offline UX: amber banner on the submit screen ("You're offline — photos will be saved and uploaded automatically"), submit button relabels to "Save N photos for later", and a post-save screen explains what happens next
- When a background upload completes, a notification delivers the result link ("the result URL is sacred" — the user must never lose it because the upload happened while the phone was in a pocket)

### 3.2 Local image cache for results

- Because submitted photos already live in app storage, the result screen renders the **full-resolution local photo** and downloads **only the mask PNG**
- Server `image_id`s are mapped to local photos by array order (upload order == result order); if a local file is missing (deep link from another device, cleared storage), Coil falls back to the server `thumbnail_url`
- Masks are cached by Coil's disk cache so revisiting a result works offline

### 3.3 Magenta mask overlay

- The segmentation mask is rendered **magenta** (`#FF00FF`, 45 % alpha fill), not green: magenta is the complementary colour of leaf-green, so the mask is unmistakable on green foliage in sunlight
- A "Mask visibility" slider (0–100 %, default 75 %) lets users fade the overlay
- The user's original bbox (if any) is drawn as a dashed amber stroke for comparison — this stays amber per the shared design language

### 3.4 Notifications and App Links

- Results reminder: local notification at 18:00 the day of submission (next morning if submitted late), deep-linking to the result screen. `POST_NOTIFICATIONS` permission is requested *after* a successful submission, framed as "Want a reminder when your results are ready?"
- Upload-complete notification for background uploads
- `https://<domain>/result/{id}` declared as an auto-verified App Link (requires `assetlinks.json` on the server — documented, not part of this build)

### 3.5 My Submissions screen

The offline queue and the no-accounts model make a local history screen necessary (it replaces the web's single "previous submission" banner): every local submission with its state — *Saved offline → Uploading → Processing → Results ready / Failed (tap to retry)*. Reached via a history icon on the submit screen header (badged when items are queued).

---

## 4. Screen Catalog

| Screen | Notes |
|---|---|
| Consent sheet | `ModalBottomSheet`, first launch, stored in DataStore; no decline button; works offline |
| Submit screen | Photo strip, disease picker sheet, live tier indicator, collapsed optional section, offline banner; per `UI_DESIGN_SPEC.md §6.2` |
| Bbox drawing sheet | Press-and-drag on full-width photo; normalized 0–1 coords; prev/next across photos; <5 % area rejected |
| Upload state | In-place linear progress (from `UploadWorker` progress); form dimmed; error re-enables form |
| Result URL screen | Sacred-link moment: copy, Android Sharesheet, open results, submit another; notification permission ask; offline variant ("Saved — will upload automatically") |
| Result screen | Pending (60 s poll + pull-to-refresh) / Ready (result cards) / Not found; serves local photos + magenta masks |
| Result card | Local photo + magenta mask overlay + visibility slider, disease name, confidence bar, quality score, Looks good / Correct it |
| Correction sheet | Same drawing interaction; faded mask underneath; POSTs correction |
| My Submissions | Local history list with states, retry, delete |

Design tokens (colours light+dark, type scale, spacing, radii, icons) come from `UI_DESIGN_SPEC.md §4`, implemented as a custom Compose `ColorScheme`/`Typography`/`Shapes`. Dynamic colour (Material You) is **disabled** for brand consistency with the web app. Haptics per `UI_DESIGN_SPEC.md §8.2`. Edge-to-edge rendering.

---

## 5. Deliverables

```
android/
├── settings.gradle.kts / build.gradle.kts / gradle/libs.versions.toml
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        └── java/com/previNet/android/
            ├── PreViNetApp.kt            ← Application + AppContainer (manual DI)
            ├── MainActivity.kt           ← single activity, edge-to-edge, NavHost
            ├── ui/theme/                 ← Color, Type, Shape, Theme (light+dark)
            ├── ui/components/            ← Buttons, chips, sheets, ConfidenceBar, OfflineBanner…
            ├── ui/consent/  ui/submit/  ui/resulturl/  ui/result/  ui/submissions/
            ├── data/api/                 ← Ktor client + DTOs
            ├── data/db/                  ← Room: SubmissionEntity, PhotoEntity, DAOs
            ├── data/                     ← ImageStore, SubmissionRepository, Fingerprint, DiseaseRepository
            ├── work/                     ← UploadWorker, ReminderScheduler
            └── util/                     ← Connectivity flow, haptics, formatting
```

The full implementation prompt is `android/PROMPT.md` (self-contained — paste into Claude Fable without any other files).

---

## 6. Out of Scope

- Backend changes of any kind
- Accounts / login (the system has none)
- iOS (covered by the web PWA)
- In-app camera (CameraX) — system camera intent is sufficient and offline-safe
- Internationalisation (English-only, structure ready for Hungarian later)
- Admin features
