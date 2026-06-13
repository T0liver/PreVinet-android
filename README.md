# PreViNet Android

> Field client for the PreViNet grape disease research platform — photograph diseased vine leaves, annotate them in seconds, and get AI segmentation results back, even when you're standing in a vineyard with no signal.

![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white)
![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![Min SDK](https://img.shields.io/badge/minSdk-26-informational)

---

## What is PreViNet?

PreViNet collects annotated photos of diseased grape leaves from vineyard workers and runs them through a SAM-based segmentation model to map disease extent. This Android app is the primary field client — most users carry Android phones into the vineyard, and most vineyards have patchy mobile coverage at best.

The app was built around three constraints that the web version cannot satisfy:

1. **You might have no signal at all.** Every action that matters works fully offline.
2. **The result link is sacred.** There are no accounts. Once you upload, the result URL is the only way back to your results — the app makes sure you never lose it.
3. **Masks need to be readable on green leaves in sunlight.** The segmentation overlay is rendered in magenta (#FF00FF) rather than the conventional green.

---

## Features

### Offline-first submission queue
Photos taken or picked are immediately copied into app-private storage, downscaled to ≤ 2048 px (JPEG q85), and saved to a local Room database. An amber banner and a relabelled submit button ("Save N photos for later") make the offline state obvious. A WorkManager `UploadWorker` with a `NETWORK_CONNECTED` constraint and exponential backoff handles the upload transparently the moment connectivity returns — no user action required.

### Three annotation tiers
| Tier | What you provide | Quality |
|------|-----------------|---------|
| 1 — Basic | Photos only | Baseline |
| 2 — Good | Photos + disease label | Improved |
| 3 — Gold | Photos + label + bounding box per photo | Best |

A live tier indicator on the submit screen updates as the user fills in more information.

### Local image cache
Because submitted photos already live on the device, the result screen renders the **full-resolution local photo** and downloads **only the small mask PNG**. Masks are disk-cached by Coil so revisiting a result works offline. If a photo has been cleared (or the result comes in via App Link on a different device), Coil falls back automatically to the server thumbnail.

### Magenta mask overlay
The segmentation mask is composited in **magenta (#FF00FF, 45 % alpha)** — the complementary colour of leaf-green, making it unmistakable in sunlight. A *Mask visibility* slider (0–100 %, default 75 %) lets users fade the overlay. The user's original bounding box is always shown as a dashed amber stroke alongside the mask for comparison.

### Bounding box annotation
A full-width press-and-drag canvas lets users draw a box around the affected area. Navigation controls step through each photo in the batch. Boxes covering less than 5 % of the image are rejected with inline feedback. The same interaction is reused in the correction sheet.

### Corrections
After seeing the segmentation result, users can redraw a bounding box to correct the model's output. The correction is POSTed to the backend and the card updates in place.

### My Submissions
A local history screen lists every submission with its current state — *Saved offline → Uploading → Processing → Results ready / Failed (tap to retry)*. Reached via a badged history icon in the submit screen header.

### Notifications and App Links
- A local reminder notification fires at 18:00 on the day of submission (next morning if submitted late), deep-linking straight to the result screen.
- An upload-complete notification delivers the result link the moment a background upload finishes.
- `https://<your-domain>/result/{id}` is declared as an auto-verified App Link so tapping the URL from any app opens the result screen directly.

---

## Tech stack

| Concern | Library / approach |
|---|---|
| Language & UI | Kotlin 2.2 + Jetpack Compose, Material 3 |
| Architecture | Single activity · MVVM · manual DI (`AppContainer`) |
| Navigation | Navigation Compose with deep link routes |
| Local database | Room 2.8 (KSP) |
| Background work | WorkManager 2.10 (`CoroutineWorker`) |
| Preferences | DataStore Preferences |
| HTTP | Ktor 3.0 (OkHttp engine) + kotlinx.serialization |
| Image loading | Coil 3 with OkHttp network fetcher + disk cache |
| Photo intake | Photo Picker API + `ACTION_IMAGE_CAPTURE` |
| Connectivity | `NetworkCallback` exposed as `StateFlow<Boolean>` |
| Min / target SDK | 26 (Android 8.0) / 35 |

No Hilt, no Koin — the app is small enough that a hand-rolled `AppContainer` keeps the build fast and the dependency graph readable.

---

## Getting started

### Prerequisites
- Android Studio Meerkat or newer (or the CLI tools)
- JDK 17+
- Android SDK with API 35 platform

### Clone and build
```bash
git clone https://github.com/<your-org>/previNet-android.git
cd previNet-android
./gradlew assembleDebug
```

### Point at your backend
The API base URL defaults to `https://grape.example.com`. Override it in your local `gradle.properties`:

```properties
PREVINET_API_BASE=https://your-server.example.com
```

This also controls the host used in the App Link intent filter, so the result deep-link will match your domain automatically.

---

## Project structure

```
app/src/main/java/com/previNet/android/
├── PreViNetApp.kt            Application subclass + AppContainer (manual DI)
├── MainActivity.kt           Single activity, edge-to-edge, NavHost
├── ui/
│   ├── theme/                Color, Type, Shape, Spacing, Theme
│   ├── components/           BboxCanvas, ConfidenceBar, OfflineBanner, StatusChip, TierIndicator
│   ├── consent/              ConsentSheet
│   ├── submit/               SubmitScreen, SubmitViewModel, DiseasePickerSheet, BboxDrawSheet
│   ├── resulturl/            ResultUrlScreen
│   ├── result/               ResultScreen, ResultViewModel, ResultCard, MaskOverlay, CorrectionSheet
│   └── submissions/          MySubmissionsScreen
├── data/
│   ├── api/                  ApiClient (Ktor), Dtos, ApiException
│   ├── db/                   AppDatabase (Room), Entities, Daos
│   ├── ImageStore.kt         Photo copy + downscale + EXIF GPS strip
│   ├── Fingerprint.kt        Anonymous stable device ID
│   ├── PrefsRepository.kt    DataStore wrapper
│   ├── DiseaseRepository.kt  Disease list with bundled fallback
│   └── SubmissionRepository.kt
├── work/
│   ├── UploadWorker.kt       Network-constrained upload with progress + notification
│   ├── ReminderWorker.kt     18:00 results-ready reminder
│   ├── ReminderScheduler.kt  Schedules / cancels reminders by submission ID
│   └── NotificationChannels.kt
└── util/
    ├── ConnectivityObserver.kt
    └── Haptics.kt
```

---

## API contract

The app talks to a fixed backend API — no backend changes are needed or made by this client.

| Method | Endpoint | Purpose |
|--------|----------|---------|
| `POST` | `/api/v1/submissions` | Multipart upload (images, label, bboxes, GPS, notes) |
| `GET` | `/api/v1/submissions/{id}` | Status polling |
| `GET` | `/api/v1/submissions/{id}/result` | Fetch segmentation results |
| `POST` | `/api/v1/submissions/{id}/correction` | Submit corrected bounding box |
| `GET` | `/api/v1/diseases` | Fetch disease label list |

---

## App Links setup

To enable verified App Links (`https://your-domain/result/{id}` → opens the result screen directly), host a Digital Asset Links file at:

```
https://your-domain/.well-known/assetlinks.json
```

See the [Android App Links documentation](https://developer.android.com/training/app-links/verify-android-applinks) for the required JSON format.

---

## License

See [LICENSE](LICENSE).
