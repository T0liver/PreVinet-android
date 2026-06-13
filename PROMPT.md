# Prompt for Claude Fable — PreViNet Android App

> Paste this prompt in full. It is self-contained: no other files need to be shared with the model.

---

## Context

You are building the production Android app for **PreViNet**, a grape disease image collection system used by vineyard workers in the field. The app collects photos of diseased grape leaves, optional annotations (disease label + bounding box), and submits them to a backend API that runs SAM 3.1 segmentation. After processing (up to 12 hours), users view their segmentation results and can correct the AI output.

**There are no accounts.** The result URL is the user's only connection to their data — the app must never let it be lost, even when the upload finishes in the background.

**Users** are vineyard workers outdoors in direct sunlight, using Android phones with dirty hands, often with **no mobile coverage in the vineyard**. Every interaction must be field-first: large tap targets, high contrast, minimal text, fully offline-capable capture.

A companion web app already exists and uses the exact same design tokens defined below — the two must look like one product.

---

## Your Task

Build a complete, production-quality native Android app in `android/` using **Kotlin + Jetpack Compose (Material 3)**. Single-activity, MVVM, manual dependency injection (no Hilt/Koin/Dagger), Room, WorkManager, DataStore, Ktor Client, Coil 3.

Implement every screen, every component, and every behaviour described in this prompt. Do not stub or skip anything with "TODO". The result must compile with `./gradlew assembleDebug` and run.

Three behaviours are non-negotiable headline features:

1. **Offline-first**: photos can be taken and submissions composed with no network; they are saved locally and uploaded automatically by WorkManager when connectivity returns.
2. **Local image cache**: photos submitted from this device are kept in app storage; the result screen renders the local full-resolution photo and downloads **only the mask PNG**, overlaying it on top.
3. **Magenta mask**: the segmentation mask overlay is **magenta (`#FF00FF`)** — the complementary colour to leaf green — so users can see it instantly on green foliage. (The web app uses green here; Android deliberately diverges on this one token for field visibility.)

---

## Project Setup

### Module layout

```
android/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/libs.versions.toml
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/values/strings.xml, themes.xml
        └── java/com/previNet/android/…
```

### Versions (gradle/libs.versions.toml)

Use these or the closest stable equivalents — if a version fails to resolve, bump to the nearest stable release:

- Kotlin 2.0.x + Compose compiler Gradle plugin (`org.jetbrains.kotlin.plugin.compose`), KSP
- AGP 8.7+
- Compose BOM (latest stable), `material3`, `material-icons-extended`, `navigation-compose`
- `androidx.activity:activity-compose`, `androidx.lifecycle:lifecycle-viewmodel-compose`, `lifecycle-runtime-compose`
- Room (runtime, ktx, compiler via KSP)
- WorkManager `work-runtime-ktx`
- DataStore `datastore-preferences`
- Ktor client: `ktor-client-core`, `ktor-client-okhttp`, `ktor-client-content-negotiation`, `ktor-serialization-kotlinx-json`
- `kotlinx-serialization-json`
- Coil 3: `coil-compose`, `coil-network-okhttp`
- `androidx.exifinterface:exifinterface`
- `androidx.core:core-splashscreen`

### app/build.gradle.kts

- `namespace = "com.previNet.android"`, `minSdk = 26`, `targetSdk = 35`, `compileSdk = 35`
- `buildConfigField("String", "API_BASE", "\"${apiBase}\"")` where `apiBase` is read from a Gradle property `PREVINET_API_BASE` defaulting to `"https://grape.example.com"`
- `manifestPlaceholders["resultHost"] = host parsed from apiBase` (used by the App Link intent filter)
- Enable `buildFeatures { compose = true; buildConfig = true }`

### AndroidManifest.xml

- Permissions: `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`, `CAMERA` is **not** needed (system camera intent), `ACCESS_COARSE_LOCATION` is **not** needed (GPS comes from photo EXIF only)
- Application: `PreViNetApp`, single `MainActivity` (`exported=true`, launcher intent filter)
- App Link intent filter on MainActivity:
  ```xml
  <intent-filter android:autoVerify="true">
      <action android:name="android.intent.action.VIEW" />
      <category android:name="android.intent.category.DEFAULT" />
      <category android:name="android.intent.category.BROWSABLE" />
      <data android:scheme="https" android:host="${resultHost}" android:pathPrefix="/result/" />
  </intent-filter>
  ```
- A `FileProvider` (`androidx.core.content.FileProvider`) with a `file_paths.xml` granting access to a `camera/` subdir of `cacheDir` — used for `ACTION_IMAGE_CAPTURE` output

---

## Design System (must match the web app exactly)

Implement in `ui/theme/`. **Do not use dynamic colour (Material You)** — the brand palette is fixed for cross-platform consistency.

### Color.kt — light scheme

| M3 slot | Hex |
|---|---|
| primary | `#1B5E20` |
| onPrimary | `#FFFFFF` |
| primaryContainer | `#C8E6C9` |
| onPrimaryContainer | `#1B5E20` |
| secondary | `#B45309` |
| secondaryContainer | `#FEF3C7` |
| onSecondaryContainer | `#78350F` |
| background | `#FAFAF8` |
| surface | `#FFFFFF` |
| surfaceVariant | `#F1F3F0` |
| onSurface | `#1A1C19` |
| onSurfaceVariant | `#44483F` |
| outline | `#74796E` |
| outlineVariant | `#C3C8BC` |
| error | `#BA1A1A` |
| errorContainer | `#FFDAD6` |
| onErrorContainer | `#410002` |

### Dark scheme

| M3 slot | Hex |
|---|---|
| primary | `#A5D6A7` |
| onPrimary | `#1A1C19` |
| primaryContainer | `#1B5E20` |
| onPrimaryContainer | `#C8E6C9` |
| secondary | `#FCD34D` |
| secondaryContainer | `#78350F` |
| onSecondaryContainer | `#FEF3C7` |
| background | `#131510` |
| surface | `#1E2119` |
| surfaceVariant | `#282B24` |
| onSurface | `#E2E4DF` |
| onSurfaceVariant | `#C3C8BC` |
| outline | `#8C9187` |
| outlineVariant | `#44483F` |
| error | `#FFB4AB` |
| errorContainer | `#93000A` |
| onErrorContainer | `#FFDAD6` |

### Extra semantic colours (top-level vals, not in ColorScheme)

```kotlin
val Tier1Color  = Color(0xFF74796E)   // dark: 0xFF8C9187
val Tier2Color  = Color(0xFFB45309)   // dark: 0xFFFCD34D
val Tier3Color  = Color(0xFF1B5E20)   // dark: 0xFFA5D6A7
val MaskMagenta = Color(0xFFFF00FF)   // same in light & dark — mask overlay
const val MASK_FILL_ALPHA = 0.45f
val BboxGreenFill = Color(0x4D1B5E20) // drawing gesture fill (30% green)
```

Expose tier colours through a small `LocalTierColors` composition local that switches with the theme.

### Type.kt

System font (Roboto). M3 `Typography` overrides:

| Slot | Size / weight / line height |
|---|---|
| headlineLarge | 28sp / W400 / 35sp |
| headlineMedium | 24sp / W400 / 31sp |
| titleLarge | 22sp / W500 / 28sp |
| titleMedium | 16sp / W500 / 24sp |
| bodyLarge | 16sp / W400 / 24sp |
| bodyMedium | 14sp / W400 / 20sp |
| bodySmall | 12sp / W400 / 16sp |
| labelLarge | 14sp / W500 / 20sp |
| labelMedium | 12sp / W500 / 16sp |

### Shape.kt

`small = 8.dp`, `medium = 12.dp` (buttons, fields), `large = 16.dp` (cards), `extraLarge = 28.dp` (sheets — top corners only via `RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)` where used).

### Spacing

`object Spacing { val xs = 4.dp; val sm = 8.dp; val md = 16.dp; val lg = 24.dp; val xl = 32.dp; val xxl = 48.dp }`

Screen margins 16.dp; max content width 600.dp (wrap screens in a centered `widthIn(max = 600.dp)` column for tablets). Touch targets ≥ 48.dp; primary CTAs 56.dp tall.

### Icons

Use `material-icons-extended` (rounded variants where available): `AddAPhoto`, `PhotoLibrary`, `LocalFlorist`, `CropFree`, `LocationOn`, `Notes`, `ContentCopy`, `Share`, `HourglassEmpty`, `Autorenew`, `CheckCircle`, `Draw`, `Eco`, `Close`, `ExpandMore`/`ExpandLess`, `LooksOne`, `LooksTwo`, `Looks3`, `Warning`, `Check`, `History`, `CloudOff`, `CloudUpload`, `Delete`, `Refresh`.

### Edge-to-edge

Call `enableEdgeToEdge()` in `MainActivity`; pad content with `WindowInsets.safeDrawing` via `Scaffold`. Use the splashscreen API with the green primary as background.

---

## API Contract (verified against the backend source — use exactly these names)

Base URL: `BuildConfig.API_BASE`.

### `GET /api/v1/diseases`
Returns `["black_rot","peronospora","powdery_mildew","botrytis","esca","phomopsis","unknown"]` (plain JSON array of strings).

### `POST /api/v1/submissions` — multipart/form-data

| Part | Type | Rule |
|---|---|---|
| `images[]` | file, repeated | 1–30 JPEG files, ≤15 MB each |
| `annotation_tier` | text | `1`, `2`, or `3` |
| `disease_label` | text | required for tier 2/3; one of the disease slugs |
| `bboxes[]` | text, repeated | **only when tier = 3**; one entry per image **in image order**; each entry is JSON `{"x":0.12,"y":0.30,"w":0.45,"h":0.40}` with values normalized 0–1, or the literal string `null` for images without a box. Count MUST equal image count or the server returns 422 |
| `gps_lat` / `gps_lon` | text | optional doubles; send only when the user has GPS sharing enabled and EXIF GPS was found |
| `notes` | text | optional, ≤500 chars |
| `device_fingerprint` | text | SHA-256 hex string |

Responses: `202` `{ "submission_id", "status", "image_count", "result_url", "message" }` · `400` validation · `413` file too large · `422` bbox/image count mismatch · `429` rate limit (10 req/min, 350 images/day per IP) · `403` banned. Error bodies: `{ "error": "..." }`.

### `GET /api/v1/submissions/{id}`
`{ "submission_id", "status", "image_count", "annotation_tier", "created_at" }` — status: `pending` → `relayed` → `segmented` → `done`. `404` if unknown.

### `GET /api/v1/submissions/{id}/result`
Available once status is `segmented`/`done`:
```json
{ "submission_id": "...", "status": "segmented", "images": [
    { "image_id": "...", "thumbnail_url": "/api/v1/images/{id}",
      "mask_url": "/api/v1/masks/{id}", "disease_label": "peronospora",
      "confidence": 0.87, "quality_score": 0.91, "corrected": false } ] }
```
**The `images` array is in upload order** — this is what lets you map server results onto locally cached photos by index. `mask_url`/`thumbnail_url` are relative; prefix with `API_BASE`. `mask_url` may be null if the mask isn't ready — degrade gracefully (show photo + original bbox labelled "Original annotation").

### `POST /api/v1/submissions/{id}/correction`
Body `{ "image_id": "...", "corrected_bbox": {"x":..,"y":..,"w":..,"h":..} }` (normalized 0–1). `200` on success.

### Mask PNG format
Binary mask: white (or any value > 128) = diseased area, black = background.

---

## Architecture

### Manual DI — `PreViNetApp.kt`

```kotlin
class PreViNetApp : Application() {
    lateinit var container: AppContainer
    override fun onCreate() { super.onCreate(); container = AppContainer(this); NotificationChannels.create(this) }
}

class AppContainer(app: Application) {
    val db = Room.databaseBuilder(app, AppDatabase::class.java, "previNet.db").build()
    val api = ApiClient(BuildConfig.API_BASE)
    val imageStore = ImageStore(app)
    val prefs = PrefsRepository(app)              // DataStore
    val connectivity = ConnectivityObserver(app)  // StateFlow<Boolean>
    val submissions = SubmissionRepository(app, db, api, imageStore)
    val diseases = DiseaseRepository(api, prefs)
}
```

ViewModels are created with a `viewModelFactory` that pulls from `AppContainer` (helper extension `Context.appContainer`).

### Room — `data/db/`

```kotlin
@Entity(tableName = "submissions")
data class SubmissionEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val serverId: String? = null,          // null until uploaded
    val state: String,                     // QUEUED | UPLOADING | FAILED | SUBMITTED
    val serverStatus: String? = null,      // pending | relayed | segmented | done
    val annotationTier: Int,
    val diseaseLabel: String? = null,
    val notes: String? = null,
    val shareGps: Boolean = true,
    val gpsLat: Double? = null, val gpsLon: Double? = null,
    val createdAt: Long, val uploadedAt: Long? = null,
    val resultUrl: String? = null,         // absolute URL, the sacred link
    val lastError: String? = null,
)

@Entity(tableName = "photos", foreignKeys = [...ON DELETE CASCADE...])
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val submissionLocalId: Long,
    val orderIndex: Int,
    val filePath: String,                  // absolute path in app storage
    val fileSize: Long,
    val bboxX: Double? = null, val bboxY: Double? = null,
    val bboxW: Double? = null, val bboxH: Double? = null,
    val serverImageId: String? = null,     // filled in when results arrive, by orderIndex
)
```

DAOs expose `Flow`-based queries: all submissions (newest first), submission with photos by localId / serverId, counts of queued items (for the history badge).

### `data/ImageStore.kt`

- `import(uri: Uri, stripGps: Boolean): ImportedPhoto` — copies a picked/captured image into `filesDir/photos/<uuid>.jpg`:
  - Decode with `BitmapFactory` + `inSampleSize`, downscale so the long edge ≤ 2048 px, re-encode JPEG quality 85 (keeps uploads small on rural mobile data and always under the 15 MB limit)
  - Apply EXIF orientation to the pixels (bake rotation in), so bbox coordinates always refer to the displayed orientation
  - Read EXIF GPS (`ExifInterface.latLong`) **before** re-encoding and return it with the file; never write GPS into the stored copy (re-encoding strips all EXIF — that is desired; the backend strips metadata anyway)
  - Return `ImportedPhoto(file, sizeBytes, lat?, lon?)`
- `newCameraOutputUri(): Pair<Uri, File>` — `FileProvider` URI in `cacheDir/camera/` for `ACTION_IMAGE_CAPTURE`
- `delete(paths)` — cleanup when a submission is deleted

This store **is** the image cache: files persist after upload so the result screen reads them locally.

### `data/api/ApiClient.kt`

Ktor `HttpClient(OkHttp)` with `ContentNegotiation` + lenient `Json { ignoreUnknownKeys = true }`. Functions:

- `getDiseases(): List<String>`
- `getSubmission(id): SubmissionStatusDto?` (null on 404)
- `getResult(id): SubmissionResultDto`
- `postCorrection(id, imageId, bbox)`
- `submit(parts…, onProgress: (Float) -> Unit): SubmissionAcceptedDto` — `submitFormWithBinaryData` with `MultiPartFormDataContent`; wire `onUpload { sent, total -> }` to `onProgress`. Map non-202 responses to a sealed `ApiException(code, userMessage)` using the error-message table at the end of this prompt.

### `data/Fingerprint.kt`

SHA-256 hex of `Settings.Secure.ANDROID_ID + "|" + Build.MANUFACTURER + "|" + Build.MODEL + "|" + Build.VERSION.SDK_INT`. Computed once, cached in DataStore.

### `data/DiseaseRepository.kt`

Bundled fallback list (the seven slugs above). On app start, if online, refresh from `GET /api/v1/diseases` and cache the JSON in DataStore. Exposes `Flow<List<Disease>>` where `Disease(slug, displayName, description)` uses this mapping:

| slug | display | description |
|---|---|---|
| `black_rot` | Black rot | Dark spots on leaves |
| `peronospora` | Downy mildew (peronospora) | Yellow-green patches |
| `powdery_mildew` | Powdery mildew | White powdery coating |
| `botrytis` | Grey mould (botrytis) | Fuzzy grey growth |
| `esca` | Esca | Leaf scorch, tiger stripes |
| `phomopsis` | Phomopsis | Brown spots near veins |
| `unknown` | I'm not sure | *(always last, italic, secondary colour)* |

Unknown slugs from the server: title-case the slug, no description.

### `util/ConnectivityObserver.kt`

`callbackFlow` on `ConnectivityManager.registerDefaultNetworkCallback` (validated internet capability) → `StateFlow<Boolean> isOnline`, started eagerly in the app scope.

---

## Offline Queue and Upload — `work/UploadWorker.kt`

Submitting **always** follows the same path, online or offline:

1. `SubmissionRepository.enqueue(draft)` writes `SubmissionEntity(state = QUEUED)` + `PhotoEntity` rows in a transaction
2. Enqueue unique work `"upload-$localId"` (`ExistingWorkPolicy.KEEP`): `OneTimeWorkRequestBuilder<UploadWorker>` with `NetworkType.CONNECTED` constraint, `BackoffPolicy.EXPONENTIAL` 30 s, input data = localId. Mark it expedited (`setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)`)

`UploadWorker` (a `CoroutineWorker`):

- Sets state `UPLOADING`; builds the multipart request from Room + files; reports `setProgress(workDataOf("pct" to …))`
- Tier is computed at enqueue time: 3 if diseaseLabel != null && any photo has a bbox; 2 if diseaseLabel != null; else 1. When tier == 3 send one `bboxes[]` entry per photo in `orderIndex` order (`null` literal for boxless photos)
- **Success (202):** save `serverId`, `resultUrl` (= `API_BASE + result_url` if relative), `uploadedAt`, state `SUBMITTED`, `serverStatus = "pending"`. Schedule the 18:00 results reminder (below). If the app is not in the foreground (track via `ProcessLifecycleOwner` or a simple foreground flag in the repository), post an **upload-complete notification**: title "Photos uploaded ✓", text "Tap to open your results link — keep it safe!", tap deep-links to the result screen. Return `Result.success()`
- **Permanent failure (400/403/413/422):** state `FAILED`, store the user message in `lastError`, `Result.failure()` (user retries manually from My Submissions after fixing nothing — these are rare programming/abuse cases)
- **Transient (429/5xx/IOException):** `Result.retry()` (keep state `UPLOADING`→ revert to `QUEUED` so UI shows "Waiting to upload")

The repository exposes `uploadProgress(localId): Flow<UploadUiState>` combining Room state + `WorkManager.getWorkInfosForUniqueWorkFlow` progress, so the submit screen can render the live progress bar for foreground uploads.

### Notifications — `work/ReminderScheduler.kt` + `NotificationChannels`

Two channels: `uploads` ("Upload status", default importance) and `results` ("Results ready", high).

Results reminder: a `OneTimeWorkRequest<ReminderWorker>` with an initial delay targeting **18:00 local time the same day**; if it's already past 17:00, target 10:00 the next morning. Notification: title "Your grape disease results may be ready", text "Tap to see your segmentation results", deep link to the result screen, channel `results`. Tag it with the localId so it can be cancelled if the user already viewed the segmented result.

Request `POST_NOTIFICATIONS` (API 33+) **only** on the Result URL screen after a successful submission, with a pre-prompt card: "Want a reminder when your results are ready?" [Yes, notify me] [No thanks]. Store the choice; never ask again after a refusal.

---

## Navigation — `MainActivity.kt`

Navigation Compose, four destinations:

| Route | Screen |
|---|---|
| `submit` | SubmitScreen (start destination) |
| `saved/{localId}` | ResultUrlScreen (post-submit, both online & offline variants) |
| `result/{localId}` | ResultScreen (localId = Room key; resolves serverId internally) |
| `submissions` | MySubmissionsScreen |

Deep link: `https://<host>/result/{serverId}` → on receipt, look up Room by serverId; if found navigate to `result/{localId}`, otherwise insert a stub `SubmissionEntity(state = SUBMITTED, serverId = …, resultUrl = …)` with zero photos and navigate to it (covers links shared from other devices — everything then loads from the server).

Back behaviour: result → submit; sheets close on system back. Never intercept the back gesture otherwise.

The consent gate: collect `prefs.consentGiven` as state in MainActivity's root composable; when false, render the ConsentSheet over whatever screen is active (scrim blocks interaction). Dismissing the sheet without agreeing keeps the app browsable but the submit button is replaced by "Review data notice" which re-opens the sheet.

---

## Screens

### 1. ConsentSheet — `ui/consent/`

`ModalBottomSheet` (M3), `extraLarge` top corners, not dismissible by scrim tap while content shows:

```
[drag handle]
[Eco icon, 48dp, primary]
"Research data notice"        ← headlineMedium, primary colour
"What you are sharing:"       ← titleMedium
• Photos of diseased grape leaves
• GPS location from photos (only if you allow it)
• Your IP address
"Purpose: training a grape disease AI model for university research.
 Data is used only for this research."   ← bodyMedium, onSurfaceVariant
[ I agree and continue ]      ← filled button, fullWidth, 56dp
"You can close this, but you'll need to agree before submitting."  ← bodySmall, centered
```

On agree: `prefs.setConsentGiven(true)`, sheet animates away. No decline button. Works fully offline.

### 2. SubmitScreen — `ui/submit/`

`Scaffold` with a slim header row (not a TopAppBar):

```
PreViNet                          [History icon + badge]
Grape disease photos
```

History icon navigates to `submissions`; show a small badge with the count of QUEUED/FAILED items (Room flow).

**Offline banner** (when `!isOnline`): full-width `secondaryContainer` surface, `CloudOff` icon: "You're offline — photos will be saved and uploaded automatically when you're back online." Slides in/out with `AnimatedVisibility`.

**Section 1 — Photos** (card, `large` shape, `surface`, subtle elevation):

- Empty state: 160dp+ dashed-border box (`drawBehind` dashed stroke, outline colour) containing two stacked 56dp buttons: `[AddAPhoto] Take a photo` and `[PhotoLibrary] Add from gallery`
  - Camera: `rememberLauncherForActivityResult(ActivityResultContracts.TakePicture())` with `ImageStore.newCameraOutputUri()`; on success import the file
  - Gallery: `PickMultipleVisualMedia(maxItems = 30)` (Photo Picker — works on 13+, backported by the activity library)
  - Imports run on `Dispatchers.IO` with a small inline progress indicator ("Adding 5 photos…")
- Loaded state: `LazyRow` of 88dp thumbnails (Coil, `ContentScale.Crop`, `medium` corners):
  - × remove badge top-right (32dp circular, `rgba(0,0,0,0.6)` background, white Close icon)
  - If a bbox is drawn: 2dp primary border + 24dp green check badge bottom-left
  - Tap thumbnail → BboxDrawSheet at that index
  - Trailing "+ Add more" tile (dashed, Add icon) → reopens the picker choice (small popup with camera/gallery)
- Below the strip: "`12 photos · 8.4 MB`" (labelMedium, onSurfaceVariant). If any file would have exceeded limits pre-downscale, no warning needed (downscaling guarantees size)

**Section 2 — Annotation** (card):

- Disease field: outlined-field-styled clickable surface ("Select disease…" / chosen display name, `LocalFlorist` leading icon, `ExpandMore` trailing). Opens **DiseasePickerSheet**: `ModalBottomSheet`, title "Select disease", one 56dp row per disease — radio + display name (titleMedium) + description (bodySmall, onSurfaceVariant), divider between rows; "I'm not sure" last, italic, muted. Tap selects + closes
- **TierIndicator**: pill chip + hint line, updates live:

| Condition | Chip | Icon | Label | Hint |
|---|---|---|---|---|
| no label, no bbox | outlined, Tier1Color | LooksOne | "Basic · Tier 1" | "Add a disease label to improve quality" |
| label only | filled secondaryContainer | LooksTwo | "Good · Tier 2" | "Draw a box on a photo for Gold quality" |
| label + ≥1 bbox | filled primaryContainer | Looks3 | "Gold · Tier 3" | "Excellent! Your annotation is Gold quality." |

  Upgrade animation: `animateFloatAsState` scale 1f→1.05f→1f over ~250 ms, triggered only on upgrades; haptic `HapticFeedbackConstants.CONFIRM`. Wrapper has `semantics { liveRegion = LiveRegionMode.Polite }`
- Helper text under the chip when label selected && photos exist && no bbox yet: "Tap a photo above to draw a box around the disease."

**Section 3 — Optional** (collapsed by default): full-width toggle row "Notes and location" with chevron; expands inline (`AnimatedVisibility`):
- Notes: `OutlinedTextField`, 500-char limit enforced, counter "47 / 500" bottom-right
- GPS switch: "Share photo location" + bodySmall "Uses the GPS stored in your photos to map disease spots. Turn off to remove location data." Default ON. When OFF, `ImageStore.import` strips GPS and no `gps_lat/lon` is sent; when ON, the first photo's EXIF GPS (if any) is stored on the submission

**Submit button**: fullWidth, 56dp, filled. Online label: "Submit N photos". Offline label: "Save N photos for later" with `CloudUpload` icon. Disabled (38 % alpha, still visible) until ≥1 photo, with helper text "Add at least one photo". Haptic CONFIRM on press.

**Upload state** (online): button area is replaced in place by a `LinearProgressIndicator` (primary colour, full width, 8dp, rounded) + "Uploading 12 photos… 62 %" (bodyMedium). After 8 s append "This may take a moment on mobile data." Form above dims to 50 % alpha and ignores input. No cancel button. On worker failure with a permanent error: bar turns error colour, message replaces the percentage, form re-enables, photos preserved. On success → navigate to `saved/{localId}` (clear submit form state).

**Offline submit**: instant — enqueue and navigate to `saved/{localId}` immediately.

**SubmitViewModel** holds: photo list (`ImportedPhoto` + bbox per item), disease, notes, gps toggle, expanded flag, upload state. It survives configuration changes; photo files are already on disk so process death loses only the in-progress form metadata (acceptable).

### 3. BboxDrawSheet — `ui/submit/BboxDrawSheet.kt`

`ModalBottomSheet`, opens on thumbnail tap.

```
[handle]
"Photo 3 of 12"                  ← titleMedium, onSurfaceVariant
"Draw a box around the disease"  ← bodyMedium
[Photo — full width, ≥60% of sheet height, aspect-fit]
[Clear box]          [Confirm box]
← Previous                Next →
```

Implementation:
- Coil `AsyncImage` of the local file inside a `Box`; a `Canvas`/`Modifier.pointerInput` overlay sized exactly to the rendered image bounds (compute via `onGloballyPositioned` + image aspect ratio letterboxing math — coordinates must be relative to the **image**, not the container)
- `detectDragGestures`: press-and-drag draws a live rectangle — `BboxGreenFill` fill + 2dp primary stroke (the *drawing* colour stays green = brand interaction colour; magenta is reserved for AI masks on results)
- On release: normalize to 0–1 of image dimensions; if `w*h < 0.05` show inline "Box too small — try drawing a larger area." (error colour, bodySmall) and discard; else store + haptic `CLOCK_TICK`
- Existing bbox renders as a solid rectangle when reopening
- First-ever open (DataStore flag): 1.5 s ghost tutorial — a pulsing dot that travels diagonally, suggesting press-and-drag
- "Confirm box" stores and advances to the next photo (closes after the last). "Clear box" clears this photo only. Previous/Next freely navigate; the title index updates
- Canvas semantics: `contentDescription = "Draw a bounding box by pressing and dragging. Currently: box drawn / no box"`

### 4. ResultUrlScreen — `ui/resulturl/` (route `saved/{localId}`)

Observes the submission row. Two variants by state:

**A — Uploaded (state SUBMITTED, resultUrl available):** the sacred-link moment.

```
[CheckCircle 48dp, primary]   "Submitted!"   ← headlineMedium
"12 photos received"                          ← bodyLarge
──────
[Warning icon, secondary]  "Save this link"   ← titleMedium in secondary (amber) colour
"There are no accounts. This link is the only way to see and correct your results."
┌──────────────────────────────────────┐
│ grape.example.com/result/550e84…     │      ← monospace (FontFamily.Monospace), selectable
└──────────────────────────────────────┘
[ ContentCopy  Copy link ]                    ← outlined, fullWidth
[ Share        Share…    ]                    ← outlined, fullWidth → Android Sharesheet
──────
[notification pre-prompt card — see Notifications section]
"Results are usually ready within a few hours."
[ Open results page ]                         ← filled, fullWidth → result/{localId}
[ Submit another batch ]                      ← text button → back to submit (form reset)
```

Copy: `ClipboardManager`, snackbar "Link copied to clipboard", button label becomes "Copied ✓" for 2 s. Share: `Intent.ACTION_SEND` chooser with the URL.

**B — Saved offline (state QUEUED/UPLOADING and no serverId):**

```
[CloudUpload 48dp, secondary]  "Saved!"          ← headlineMedium
"12 photos will upload automatically when you're back online."
"You'll get a notification with your results link." (only if notif permission granted —
 otherwise show the notification pre-prompt card here too)
[ View my submissions ]   ← filled → submissions
[ Submit another batch ]  ← text button
```

This variant live-updates: if connectivity returns while the user is on it, it transitions to variant A (the row's state flips to SUBMITTED via the worker).

### 5. ResultScreen — `ui/result/` (route `result/{localId}`)

`ResultViewModel`: loads the submission + photos from Room; if `serverId == null` show the queued state; else fetch `GET /submissions/{serverId}`, then `GET …/result` when segmented/done. Poll status every 60 s while pending/relayed (cancel when backgrounded — use `repeatOnLifecycle`). Pull-to-refresh (`PullToRefreshBox`) triggers an immediate poll. When results arrive: persist `serverImageId` per photo by index and `serverStatus`.

Header: `← New submission` text button; "Segmentation Result" headlineLarge; truncated id labelMedium muted; photo count + tier chip.

**State Queued (local, not yet uploaded):** `CloudUpload` icon 48dp secondary, "Waiting to upload" + offline explanation, button "Upload now" if online (re-enqueues with KEEP→ effectively retries) — and for FAILED: error message + "Try again" (re-enqueue with REPLACE) + "Delete" (confirm dialog → delete row + files).

**State Pending/Relayed:** `HourglassEmpty` 48dp secondary; "Processing your photos"; "Your photos have been received and are waiting for analysis. Results are usually ready within a few hours."; submitted date + "12 photos · Tier 2" chip; `[Check for results]` outlined button with inline spinner during the manual poll. No infinite spinner anywhere.

**State Segmented/Done:** list of **ResultCards** + footer "Thank you for contributing to grape disease research." (bodyMedium, centered, onSurfaceVariant).

**Not found (404):** Warning icon, "Submission not found", "Please check your link. If you submitted less than an hour ago, try again shortly."

### 6. ResultCard — `ui/result/ResultCard.kt`

One card per image (large shape, surface):

```
┌────────────────────────────────┐
│  [photo + MAGENTA mask overlay]│   ← local photo if available, else thumbnail_url
│  [dashed amber original bbox]  │
└────────────────────────────────┘
Mask visibility  [────●──] 75%       ← Slider 0–1, default 0.75
Downy mildew (peronospora)           ← titleMedium
Confidence 87 %  [████████──]        ← ConfidenceBar
Quality score 91 %                   ← bodySmall, onSurfaceVariant
──────
"Does this look right?"              ← titleMedium
[ ✓ Looks good ]  [ ✎ Correct it ]   ← two equal-width buttons (outlined / filled tonal)
```

**Image source — the cache rule:** if `photo.filePath` exists on disk, display the local full-res file (instant, offline-capable, zero data). Only the mask is fetched from `API_BASE + mask_url`. If the local file is missing (deep link from another device, cleared data), fall back to `API_BASE + thumbnail_url` via Coil. Enable Coil's disk cache so masks and thumbnails work offline after first view.

**Magenta mask rendering — `ui/result/MaskOverlay.kt`:**

1. Load the mask PNG as a software `Bitmap` (via Coil's `ImageLoader.execute`, `allowHardware(false)`)
2. Transform once (remember per mask, off the main thread): for each pixel, if luminance > 128 → `MaskMagenta` ARGB with 255 alpha, else transparent. (`getPixels`/`setPixels` on an IntArray — fast enough for mask-sized images)
3. Draw over the photo in a `Canvas`/`drawImage`, scaled to the photo's rendered bounds, with `alpha = MASK_FILL_ALPHA * sliderValue` (slider default 0.75 ⇒ effective ≈ 34 % opacity; slider at 0 hides the mask entirely)
4. If the original submission had a bbox for this photo (from Room), draw it as a **dashed amber** (`secondary`) 2dp stroke using `PathEffect.dashPathEffect`
5. `mask_url == null` → photo + amber bbox only, with a small caption "Original annotation — mask not available yet"

The magenta choice is deliberate: it is the complementary colour of leaf green and remains visible in bright sunlight. Do not use green for masks anywhere.

**ConfidenceBar:** full-width 8dp rounded track (`surfaceVariant`); fill colour primary ≥ 0.7, secondary 0.4–0.69, error < 0.4; percentage text to the right.

**Looks good:** local-only — swap the button row for "✓ Thanks for confirming!" (primary colour), haptic CONFIRM, persist the flag in memory (per session is fine).

**Correct it:** opens **CorrectionSheet** (`ModalBottomSheet`): same drawing interaction as BboxDrawSheet but the magenta mask is shown faded (alpha 0.15) under the drawing layer; instruction "Draw the correct box around the affected area"; buttons `[Clear]` `[Submit correction]`. On submit: POST correction; inline "Saving…" then snackbar "Correction saved — thank you!"; card then shows a "Correction submitted ✓" chip and disables both buttons. Errors: snackbar with the mapped message, sheet stays open.

Extract the shared drawing logic from BboxDrawSheet and CorrectionSheet into a `rememberBboxDrawState` + `BboxCanvas` composable — do not duplicate it.

### 7. MySubmissionsScreen — `ui/submissions/`

TopAppBar "My submissions" with back. `LazyColumn` of cards, newest first:

```
[64dp thumbnail of first photo]  12 photos · Tier 2     [StatusChip]
                                 12 Jun 2026, 10:31
                                 {error message if FAILED, error colour}
```

StatusChip mapping:

| Condition | Colour | Icon | Label |
|---|---|---|---|
| QUEUED (offline) | secondaryContainer | CloudOff | Saved offline |
| QUEUED/UPLOADING (online) | secondaryContainer | CloudUpload | Uploading |
| FAILED | errorContainer | Warning | Failed — tap to retry |
| SUBMITTED + pending/relayed | secondaryContainer | HourglassEmpty | Processing |
| SUBMITTED + segmented | primaryContainer | CheckCircle | Results ready |
| SUBMITTED + done | outlined | Check | Complete |

Tap card → `result/{localId}` (FAILED cards offer retry there). Long-press → delete with confirmation dialog (deletes Room rows + local files; warn "This also deletes your local copy of these photos" and, if SUBMITTED, "Your results link will be removed from this phone"). Empty state: Eco icon + "No submissions yet" + "Your submitted and saved batches appear here."

On entering this screen, refresh `serverStatus` for all SUBMITTED-but-not-done rows (parallel status fetches, ignore failures silently — offline just shows last known state).

---

## Cross-Cutting Requirements

### Haptics (`util/Haptics.kt` — `LocalView.current.performHapticFeedback`)

| Event | Constant |
|---|---|
| Submit pressed | CONFIRM |
| Tier upgrade | CONFIRM |
| Bbox confirmed | CLOCK_TICK |
| Upload/permanent error shown | REJECT |

### Accessibility

- Every icon-only button: `contentDescription`
- Tier indicator: polite live region
- Bbox canvas: state-describing `contentDescription` (see screen 3)
- Thumbnails: `contentDescription = "Photo {n}, box drawn/no box"`
- All sizes in `dp`/`sp`; layout must survive 200 % font scale (let cards grow, never fix text-container heights)
- Touch targets ≥ 48dp (`minimumInteractiveComponentSize`)

### Error messages (map in one place, e.g. `ApiException.userMessage`)

| Status | Message |
|---|---|
| 400 | "Invalid data. Please check your photos and try again." |
| 403 | "This device has been blocked from submitting." |
| 413 | "One or more photos is too large." |
| 422 | "Annotation mismatch. Please re-draw your boxes and try again." |
| 429 | "Daily limit reached. Your photos are saved — try again tomorrow." |
| 5xx | "The server is temporarily unavailable. Your photos are saved and will retry." |
| network | "No connection. Your photos are saved and will upload automatically." |

Never surface raw HTTP codes or exception text to the user.

### Strings

All user-visible text in `res/values/strings.xml` (English), referenced via `stringResource` — structure ready for a Hungarian translation later.

---

## What to Build — order

1. Gradle setup (`settings.gradle.kts`, version catalog, `app/build.gradle.kts`), `AndroidManifest.xml`, `strings.xml`
2. `ui/theme/` (Color, Type, Shape, Theme, Spacing) — light + dark, no dynamic colour
3. `data/` layer: Room entities/DAOs/database, `ImageStore`, `ApiClient` + DTOs, `Fingerprint`, `PrefsRepository`, `DiseaseRepository`, `ConnectivityObserver`, `SubmissionRepository`
4. `work/`: `UploadWorker`, `ReminderScheduler`, `NotificationChannels`
5. `ui/components/`: buttons, chips (tier/status), `ConfidenceBar`, `OfflineBanner`, `BboxCanvas` + `rememberBboxDrawState`, snackbar host
6. `PreViNetApp`, `MainActivity` (edge-to-edge, NavHost, deep link, consent gate)
7. Screens in flow order: ConsentSheet → SubmitScreen (+ DiseasePickerSheet, TierIndicator, BboxDrawSheet) → ResultUrlScreen → ResultScreen (+ ResultCard, MaskOverlay, CorrectionSheet) → MySubmissionsScreen

Write clean, idiomatic Kotlin and Compose: state hoisting, `StateFlow` in ViewModels collected with `collectAsStateWithLifecycle`, no business logic in composables, no `GlobalScope`, no blocking I/O on main. No leftover `Log.d` in final code. The app must build with `./gradlew assembleDebug`.
