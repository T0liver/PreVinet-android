# PreViNet Android

![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white)
![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![Min SDK](https://img.shields.io/badge/minSdk-26-informational)

Android field client for the PreViNet grape disease research platform. Vineyard workers photograph diseased vine leaves, annotate them on-device, and receive AI segmentation results — with full offline support for areas with no signal.

## Features
- **Offline-first queue** — photos are saved locally and uploaded automatically via WorkManager when connectivity returns
- **Three annotation tiers** — photos only (baseline), photos + disease label (improved), or photos + label + bounding boxes (best quality)
- **Bounding box canvas** — press-and-drag annotation with per-photo navigation and a 5% minimum size guard
- **Magenta mask overlay** — segmentation result rendered in #FF00FF (complementary to leaf-green) with an adjustable opacity slider
- **Local image cache** — full-resolution photos stay on device; only the small mask PNG is downloaded and disk-cached by Coil
- **Result corrections** — redraw a bounding box after seeing the AI result to improve the model
- **My Submissions history** — live status tracking (Saved → Uploading → Processing → Results ready / Failed)
- **App Links + notifications** — `https://<your-domain>/result/{id}` opens directly in-app; local reminder fires at 18:00 on submission day

## Example Output
```
[ Camera / Gallery ] → Submit screen
        ↓ (offline? queued in Room + WorkManager)
[ Result URL screen ] ← upload complete notification
        ↓
[ Result screen: local photo + magenta mask overlay + correction sheet ]
```

## How to Run
### 1. Clone the Repository
```bash
git clone https://github.com/T0liver/PreVinet-android.git
```

### 2. Navigate to Project Directory
```bash
cd PreVinet-android
```

### 3. Configure the backend URL
In `gradle.properties` (create if absent):
```properties
PREVINET_API_BASE=https://your-server.example.com
```

### 4. Build and install
```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Technologies Used
- Kotlin 2.2 + Jetpack Compose (Material 3)
- Room 2.8 — local submission database (KSP)
- WorkManager 2.10 — network-constrained background uploads
- DataStore Preferences — consent flag, cached disease list
- Ktor 3.0 (OkHttp engine) + kotlinx.serialization — HTTP client
- Coil 3 — image loading with OkHttp network fetcher and disk cache
- Navigation Compose — single-activity, deep-link-aware nav graph
- Manual DI via `AppContainer` — no Hilt/Koin

## Project Structure
```
app/src/main/java/com/previNet/android/
├── PreViNetApp.kt          Application + AppContainer (manual DI)
├── MainActivity.kt         Single activity, NavHost, deep link handler
├── ui/
│   ├── submit/             SubmitScreen, SubmitViewModel, DiseasePickerSheet, BboxDrawSheet
│   ├── result/             ResultScreen, ResultViewModel, MaskOverlay, CorrectionSheet
│   ├── resulturl/          ResultUrlScreen (holds the share link)
│   ├── submissions/        MySubmissionsScreen (history + status)
│   ├── consent/            ConsentSheet
│   ├── components/         BboxCanvas, OfflineBanner, TierIndicator, StatusChip, ConfidenceBar
│   └── theme/              Color, Type, Shape, Spacing, Theme
├── data/
│   ├── api/                ApiClient (Ktor), Dtos, ApiException
│   ├── db/                 AppDatabase (Room), Entities, Daos
│   ├── ImageStore.kt       Photo copy, downscale ≤2048px, EXIF GPS strip
│   ├── SubmissionRepository.kt
│   ├── DiseaseRepository.kt
│   └── PrefsRepository.kt
├── work/
│   ├── UploadWorker.kt     Network-constrained upload with progress notification
│   ├── ReminderWorker.kt   18:00 results-ready reminder
│   └── NotificationChannels.kt
└── util/
    ├── ConnectivityObserver.kt
    └── Haptics.kt
```

## Recent Changes
- docs: rewrite README with full project documentation
- chore: add Gradle wrapper properties
- chore: add .gitignore
- docs: add project specification and implementation prompt
- feat: wire up Application class, DI container, nav host, and manifest

## Contributing
Fork the repository and submit pull requests.

## License
See [LICENSE](LICENSE).
