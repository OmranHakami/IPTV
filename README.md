# OH for Google TV

Current source version: **0.3.1**

OH is an English-only, fully local Xtream-compatible player for Google TV / Android TV and compatible Android-based Huawei tablets. It separates Live TV from Movies & TV Shows at a permanent starting hub and keeps one shared Settings area.

## Current milestone

Implemented:

- TV-only Android manifest, launcher icon and 320×180 OH banner
- Kotlin + Compose for TV dark design foundation
- First-run Xtream connection screen
- Phone-assisted browser setup hosted temporarily by the TV on the local network
- Encrypted browser-to-TV credential transfer with a five-minute one-time code and manual TV fallback
- Redesigned experience selection, manual setup, phone pairing and library preparation screens
- Redesigned Movies and TV Shows browser navigation with a Room-backed TMDB genre filter and live counts
- Android Keystore-backed AES-GCM credential storage
- Tolerant Xtream authentication
- Progressive, cancellation-aware streaming imports in 300-item bounded batches
- Live TV categories and channels
- Unified Movies and TV Shows catalogs with no VOD/series category calls or storage
- Room database with account-scoped IDs and generation-based stale cleanup
- Paging for Live TV, Movies and TV Shows with a 300-item memory ceiling per list
- Starting Page with Live TV and Movies & TV Shows modes
- Deterministic starting-page focus with high-contrast D-pad indicators
- Provider-safe three-scope synchronization for Live TV, Movies and TV Shows
- Independent sync states, elapsed time, retained-cache fallback and per-scope retry-safe failure reporting
- Live TV browser
- Movie and TV-show grids
- Progressive TMDB enrichment as visible catalog cards load, with four-request concurrency limit, local caching, and provider-data fallback
- Movie details
- TV-show details, seasons, episodes and episode playback
- Compact cinematic TV-show page: full-height key art on the left, dense episode rail on the right, vertical season selector, focused white episode card, Continue and progress indicators
- Direct Media3/ExoPlayer playback with resume, buffering, retry and safe errors
- Local search across movies and TV shows
- Favorites / My List
- Continue Watching with 90% completion handling
- Shared Settings
- Credential URL redaction and tolerant-type unit tests
- Separate TV and tablet product flavors sharing the same local backend
- Touch feedback, reduced tablet margins and adaptive catalog grids
- Compact visual scale across onboarding, hub, catalogs, details, settings and playback states for 720p/1080p TV readability without oversized controls

Remaining release-hardening milestones:

- XMLTV/short EPG ingestion and timeline UI
- MediaSession and custom playback overlay
- Audio/subtitle preference persistence
- Parental-control PIN and hidden content
- WorkManager periodic refresh
- Full focus restoration and codec tests on a physical TCL TV
- Database migration test fixtures and 25k-item performance benchmarks

## Requirements

- Android Studio with JDK 17
- Android SDK 36
- TCL Google TV, Android TV emulator, or an Android-APK-compatible Huawei MatePad

The project uses AGP 8.9.1 and Gradle 8.11.1. The minimum Android version is API 28.

## Local setup

1. Copy `local.properties.example` to `local.properties`.
2. Set `sdk.dir` to your Android SDK path.
3. The approved TMDB v3 key is included in `gradle.properties`. A `TMDB_API_KEY` value in `local.properties` overrides it after key rotation.
4. Open the project in Android Studio and sync Gradle.
5. Select the `tvDebug` or `tabletDebug` build variant.
6. Run it on the matching device.

Do not commit `local.properties`. Xtream credentials are entered on the TV and encrypted locally.

## Build

```bash
# TCL / Google TV
./gradlew testTvDebugUnitTest assembleTvDebug

# Huawei MatePad / Android tablet
./gradlew testTabletDebugUnitTest assembleTabletDebug
```

The APKs are created under:

- `app/build/outputs/apk/tv/debug/`
- `app/build/outputs/apk/tablet/debug/`

On Windows, `./verify.ps1` runs unit tests, lint and both debug APK builds in one command.

The tablet APK supports touch and adaptive grids and does not require the Android TV `leanback` feature. The TV APK remains landscape-only and D-pad optimized. HarmonyOS 2/3 devices that retain Android APK compatibility can use the tablet build. HarmonyOS NEXT requires a separate native application and cannot install this Android APK.

## Design and behavior

The binding product decisions are in [`docs/OH_PRODUCT_SPEC.md`](docs/OH_PRODUCT_SPEC.md). The broader backend reasoning is in [`docs/LOCAL_BACKEND_ARCHITECTURE.md`](docs/LOCAL_BACKEND_ARCHITECTURE.md).

The 0.2.0 large-catalog changes and before/after comparison are in [`docs/LARGE_CATALOG_UPDATE.md`](docs/LARGE_CATALOG_UPDATE.md).

The phone onboarding protocol, deployment boundary and D-pad acceptance tests are in [`docs/PHONE_PAIRING_AND_ONBOARDING.md`](docs/PHONE_PAIRING_AND_ONBOARDING.md).

## Content and metadata

OH does not provide content. Users connect their own authorized Xtream-compatible service.

Movie and TV-show provider categories are deliberately ignored. TMDB genres may appear as descriptive metadata but do not create provider category navigation.

This product can use TMDB data but is not endorsed or certified by TMDB. Before public or commercial distribution, review TMDB's current terms and obtain any required commercial agreement.

## Security notes

- Playback URLs contain Xtream credentials and must never be logged.
- Release builds must not enable HTTP body logging.
- HTTP providers are supported for compatibility; HTTPS is preferred.
- The TMDB key is a client-side credential and can be extracted from a distributed APK. Revisit the metadata architecture before a public release.
"# IPTV" 
