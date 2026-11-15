# OH — Product and UX Specification

## Product definition

OH is an English-only, fully local player for Google TV and Android-APK-compatible tablets using authorized Xtream-compatible services. It provides two deliberately separate experiences:

1. **Live TV** — channels, Live TV categories, EPG and fast channel switching.
2. **Movies & TV Shows** — a unified on-demand library with no provider category ingestion or storage.

The permanent Starting Page is the only switch between experiences. Settings is shared and returns to the experience that opened it.

## Product principles

- Fast from the sofa: every primary task is reachable with a D-pad and few steps.
- Local-first: account, catalog, EPG, favorites, history and progress stay on the TV.
- Honest resilience: cached browsing survives provider downtime; stale data is labeled, never destroyed by a failed refresh.
- Clean, not empty: restrained graphite surfaces, strong focus, large type and visual breathing room.
- Familiar, not copied: Netflix-like traversal and disclosure patterns; Pinterest-inspired card rhythm and imagery; original OH brand and information architecture.
- No bundled content, no stream proxy and no cloud application backend.

## Brand system

- Name: **OH**
- Canonical mark: user-supplied OH symbol.
- Primary background: soft graphite, not pure black.
- Surface: lifted charcoal.
- Focus/accent: clear medium blue.
- Primary text: warm white.
- Secondary text: cool gray.
- Artwork provides color; chrome remains neutral.
- Motion: 120–180 ms focus scale/fade; no autoplay previews.

## Navigation model

### Device variants

- **OH TV** requires Android TV/Google TV, remains landscape-only and uses D-pad focus scaling.
- **OH Tablet** does not require Leanback, supports touchscreen feedback and adaptive catalog columns, and can run on HarmonyOS 2/3 MatePad devices that retain Android APK compatibility.
- Both variants share the database, Xtream adapter, TMDB enrichment, synchronization and playback core.
- HarmonyOS NEXT is excluded because it cannot run Android APKs.

### First run

`Launch → Xtream Setup → Validation → Initial Sync → Starting Page`

### Normal launch

`Launch → Starting Page`

### Mode flow

`Starting Page → Live TV → Starting Page`

`Starting Page → Movies & TV Shows → Starting Page`

Settings can be opened from the Starting Page and both experiences. Closing Settings returns to its caller.

### Remote rules

- D-pad moves focus geometrically and predictably.
- OK activates the focused control.
- Back closes overlays, returns to the previous screen, then returns to the Starting Page from a mode root.
- Back on the Starting Page asks to exit.
- Play/Pause controls media.
- Channel Up/Down changes live channels while playing.
- Long OK opens contextual actions where supported.
- Focus returns to the invoking item after Details, Settings or playback closes.
- Rows remember their last focused item.
- Focus never lands on decorative content.

## Screen inventory

### 1. Xtream Setup

Fields: account name (optional), server URL, username and password. States: default, keyboard, validating, invalid credentials, server unavailable, insecure HTTP notice and expired account.

### 2. Initial Synchronization

Shows independent progress for Live TV, EPG, Movies and TV Shows. A successful scope becomes usable without waiting for failed optional scopes. States: loading, partial success, complete and failed.

### 3. Starting Page

Two large cards: **Live TV** and **Movies & TV Shows**. Shared Settings is visible. States: left focused, right focused, offline and subscription expired.

### 4. Live TV Browser

Three columns: Live TV categories, channels and preview/current-next EPG. Live categories are the only provider categories accepted. States: default, focused channel, missing EPG, empty, offline and stale.

### 5. Live Player

Full-screen video with a minimal overlay: channel, current program, progress, current/next, audio, subtitles and channel list. States: hidden controls, visible controls, buffering, retrying, unsupported format and stream unavailable.

### 6. Movies & TV Shows Home

Navigation: Home, Movies, TV Shows, Search, My List and Settings. Rows: Continue Watching, Recently Added Movies, Recently Added TV Shows and My List. No genre/category rows. States: default, focus, loading, empty, offline and stale.

### 7. Unified Catalog

One paged grid for Movies or TV Shows. Controls: search, sort and Favorites only. No categories or genre filters in version one. States: default, focused item, searching, no results and loading.

### 8. Details

Hero artwork, title, year, runtime/seasons, TMDB rating and vote count, English description, director/cast and Play/Resume/My List. TV Shows add seasons and episodes. States: full metadata, provider-only fallback, unmatched metadata, loading and locked.

### 9. VOD Player

Video, progress, play/pause, seek, audio and subtitles. States: playing, paused, seeking, buffering, subtitle/audio selector, next episode and playback failure.

### 10. Search

Searches the local Room index across Movies and TV Shows. States: empty query, keyboard focused, results, no results and recent queries.

### 11. Shared Settings

Account, Synchronization, Playback, Audio & Subtitles, Parental Controls, Live TV, Storage, Diagnostics, Startup, About and Credits.

## Data rules

- Xtream Live TV categories are fetched and stored.
- `get_vod_categories` and `get_series_categories` are never called.
- Movie and TV-show category fields are ignored at parsing and never persisted.
- Provider IDs are strings and scoped by account ID.
- Full credential-bearing playback URLs are constructed only in memory.
- Failed or malformed refreshes never replace valid cached data.
- Movie and series detail calls are lazy.
- Catalog imports stream and batch records; the full 25,000+ item catalog is never held as one UI list.
- EPG retention starts at 2 days past and 7 days future.

## TMDB enrichment

Priority: explicit TMDB ID → external ID → normalized title/year/type search. Exact ID matches are high confidence. Search matches require title/type/year scoring and a threshold. Low-confidence matches retain provider metadata.

Store English metadata only: title, overview, release date, runtime, rating, vote count, genres as descriptive fields, poster/backdrop paths, cast, director, certification and refresh time. Genres are never used as provider categories in version one.

Refresh or delete TMDB content before the six-month cache limit. Display TMDB attribution in About/Credits. If English metadata is missing, use safe provider metadata; do not pull another language.

## Security

- Xtream credentials use Android Keystore-backed AES-GCM encryption.
- TMDB credentials are injected from local properties and excluded from Git.
- Release logs never contain HTTP bodies, authorization headers, usernames, passwords or full playback URLs.
- HTTPS is preferred. HTTP accounts show a one-time warning.
- TLS verification is never disabled.
- Account removal deletes credentials and account-scoped local data after confirmation.

## Playback policy

- Media3/ExoPlayer with HLS and MPEG-TS live support plus progressive MP4/MKV where supported by the device.
- Hardware decoding preferred; unsupported codec errors are explicit.
- No permanent media cache, recording or downloading in version one.
- No autoplay previews.
- Resume VOD after 30 seconds; mark complete at 90%.
- Auto-play next episode is on by default and configurable.
- Catch-up capability is detected but playback is deferred until core Live TV is stable.

## Performance targets

- Cached Starting Page visible within 1.5 seconds on the target TCL TV.
- Cached database query returns first content page within 200 ms.
- D-pad reaction and focus animation begin within 100 ms.
- No network or database work on the main thread.
- 25,000 movies remain browsable without full-list materialization.
- Sync writes use bounded batches and expose progressive usability.
- Artwork cache is bounded and automatically pruned.

## Version-one exclusions

- Cloud sync, application accounts, payments and device activation.
- Multiple active Xtream accounts in the UI (schema remains account-scoped).
- Recording, downloads and media caching.
- Provider Movies/TV Shows categories.
- Autoplay previews.
- Catch-up playback.
- Recommendations that transmit watch history.
- Non-English UI or metadata.

## Acceptance criteria

1. A valid account can authenticate without plaintext credential persistence.
2. Invalid, expired, offline and partial-sync states are recoverable.
3. Starting Page cleanly separates Live TV from Movies & TV Shows.
4. Every interactive element is reachable and visibly focused using only a D-pad.
5. Live TV categories work; movie and TV-show categories are never requested or stored.
6. Cached catalogs are browsable offline.
7. Large catalog fixtures do not cause unbounded memory growth.
8. TMDB enriches by explicit ID and safely falls back when unmatched.
9. Live, movie and episode playback URLs are never persisted or logged.
10. Playback, audio, subtitles, resume, favorites, history and Settings work on a representative TCL Google TV.
