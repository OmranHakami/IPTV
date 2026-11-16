# OH — IPTV Player

**This README is the single source of truth for the intended OH app experience.** It describes what the app should do, not a claim that every feature is already implemented or tested. If the code disagrees with a requirement below, that is a gap to investigate—not permission to silently change the requirement.

## Product vision

OH is a fast, polished IPTV player for **Google TV / Android TV and compatible Android tablets**, including touch-first layouts around **11.5-inch tablets**. It connects to a user's own authorized Xtream-compatible IPTV service and provides three clear destinations: **Live TV, Movies, and TV Shows**.

The Android app owns its UI, catalog cache, viewing history, and playback. It talks directly to the user's IPTV provider and, when configured, to metadata/subtitle services. **There is no OH cloud account, required cloud backend, remote pairing service, Redis, or external application database.** Internet access is still required for provider streams and optional third-party metadata/subtitle requests.

OH does not bundle or supply IPTV content.

## The experience

### First-time setup

1. On a fresh installation (or whenever there is no saved account), **open the Phone Pairing screen directly** after launch. Do **not** display a separate setup-method selection screen. The Phone Pairing screen itself must provide a clear action to open manual Xtream setup.
2. Phone pairing offers the temporary web page hosted by the TV itself at `http://<TV-LAN-IP>:8765`; the phone must be on the same local network. Manual server URL, username and password entry must remain directly accessible from this screen if pairing is not wanted or cannot connect.
3. Pairing uses a short-lived, one-time session (five minutes) and encrypts submitted provider credentials for the TV before transmission. The server stops on completion, cancellation, or expiration.
4. Returning to the Phone Pairing screen from manual setup must not force a separate method-selection step. After removing an account, return directly to Phone Pairing. A returning user with a valid saved account opens the main experience selector instead.
5. Validate the Xtream account, store credentials securely on the device, and show separate import progress for Live TV, Movies, and TV Shows.
6. Make successfully imported sections usable even if another section fails. Existing cached content survives recoverable failures.
7. Open the main experience selector after onboarding.

### Main experience selector

- Present **three destinations**: **Movies**, **TV Shows**, and **Live TV**, with access to shared Settings.
- The **orange selection indicator moves with actual D-pad focus**. It must not remain painted on a default destination after focus moves elsewhere.
- Button labels are **centered**, with no decorative arrows beside their names.
- Remote navigation must be predictable; the focused item is visually obvious. Back navigation returns to the appropriate previous screen.
- TV layouts prioritize remote and D-pad use; tablet layouts support touch and do not scale controls excessively large.

### Movies

- Separate Movies page with its own browsing experience and **categories obtained from the IPTV provider**.
- Filter movies using the provider's actual movie category IDs, not TMDB genres substituted for IPTV categories.
- Show poster grids/cards, search, details, favorites/My List, and resume watching.
- Load large catalogs incrementally rather than holding every poster/card in memory at once.

### TV Shows

- Separate TV Shows page with **its own IPTV-provider series categories**, independent of movie categories.
- Browse shows, open show details, choose seasons and episodes, play an episode, and resume progress.
- Search and favorites/My List apply appropriately to shows as well as movies.

### Live TV

- Separate Live TV browser with provider categories and channels.
- Channel selection, direct live playback, channel switching, and clear loading/error states work with a remote.
- EPG/current-next information should be shown when available; missing EPG must not block playback.

## Focused cards and trailers — mandatory interaction

For both Movie and TV Show cards:

1. **As soon as a card receives focus:** show a **small, immediate expansion** and a clear moving focus indicator. Display the IPTV data already available (such as name and artwork) without waiting for a network call.
2. **While focus remains:** enrich the presentation with cached or asynchronously retrieved TMDB information when available (such as preferred title, artwork, rating, and description). The card must remain responsive.
3. **After the same card remains focused continuously for 5 seconds:** expand into the richer preview state and **play a trailer if one is available**. Do not describe static artwork as a trailer that is playing.
4. **If focus moves away before 5 seconds:** cancel that pending preview. If a preview is already playing, stop it and return to the compact card state.
5. If a matching trailer is unavailable, show a useful still/metadata preview rather than a broken player. Do not autoplay trailers across an unfocused grid.

The **five-second timer gates trailer playback**, not initial IPTV loading or TMDB metadata retrieval. Metadata may be fetched and cached before focus; focus must not trigger duplicate downloads unnecessarily.

## IPTV and TMDB: two separate information sources

**Never overwrite or replace IPTV-origin fields in storage with TMDB values.** Each movie/show has two independently identifiable metadata sources:

- **IPTV source:** provider content ID, stream details, original name, poster/artwork, provider category, provider description/rating where available, and other provider-supplied fields.
- **TMDB source:** matched TMDB ID, title, descriptions, posters/backdrops, release information, ratings, genres, and trailer information when available.

**Display selection:** prefer valid TMDB presentation metadata when it exists; otherwise fall back to IPTV metadata **field by field**. Always preserve the original IPTV values. For **catalog membership, categories, stream identity, and playback**, the IPTV provider remains authoritative.

TMDB matching and enrichment should happen asynchronously, be cached locally, and avoid blocking catalog browsing. Prefer a reliable match; never attach unrelated TMDB data just to fill an empty field. The UI must handle missing or unmatched TMDB data without hiding playable IPTV content.

## Playback

- Play provider streams directly on the device using the Android media player stack; OH does not require its own stream relay.
- Support live channels, movies, and series episodes, with buffering/retry/error feedback appropriate to each.
- Movies and episodes support pause, seek, continue watching, and saved playback position.
- Support available audio tracks and subtitle selection, including optional subtitle lookup when configured.
- Playback URLs containing provider credentials must not be persisted or logged.
- Focus and navigation return to the correct content after exiting playback.

## Local storage, sync, and reliability

- Cache provider catalogs, separate TMDB metadata, categories, favorites, and playback progress locally using Room/SQLite or equivalent local persistence.
- Use paging and bounded processing for providers with tens of thousands of channels, movies, or episodes.
- Import Live TV, Movies, and TV Shows independently, with clear progress, partial success, retry, and stale-cache states.
- A failed or cancelled refresh must not destroy the last usable catalog or personal viewing state.
- **Fresh-install-only release policy:** each release is evaluated as a clean installation with a newly created local database. Preserving or migrating databases from earlier installed app versions is out of scope; old app data may be discarded when installing a new release. Within a running installation, normal refreshes, recoverable failures, and playback updates must still preserve the current account, favorites, and viewing progress.
- Secure Xtream credentials using Android Keystore-backed storage; never expose raw credentials in diagnostic messages or logs.
- Keep a secure local pairing boundary on **HTTP port 8765**. Do not add cloud pairing, mandatory HTTPS pairing, remote activation, or an unrelated backend service.

## Design and device requirements

- A premium, restrained dark interface with artwork-forward movie/show cards and **orange focus/selection cues**.
- Clear visual feedback for focused, selected, loading, error, empty, stale, and offline states.
- Buttons and typography remain legible on TVs without becoming oversized on 11.5-inch tablets.
- D-pad, OK, Back, and playback controls must remain usable with a TV remote; touch controls must work in the tablet build.
- Every important action must be reachable without relying on a mouse.
- English is the initial interface language.

## Required screens

1. Phone pairing (first screen for users without a saved account) with manual Xtream setup accessible as a fallback; **no setup-choice screen**
2. Library preparation and sync progress
3. Main experience selector: Movies / TV Shows / Live TV
4. Movies browser with IPTV movie categories
5. TV Shows browser with IPTV series categories
6. Live TV categories and channel browser
7. Movie details
8. TV Show details / seasons / episodes
9. Search across the relevant catalog content
10. My List / favorites and Continue Watching
11. Live and on-demand player
12. Shared Settings (account, sync, playback, subtitles, and other supported preferences)

## What must be verified before calling a change complete

- **TV and tablet:** the feature works in both builds, with correct navigation, focus, layout, and touch behavior.
- **Source separation:** TMDB enrichment never replaces original IPTV fields; missing TMDB values fall back correctly.
- **Categories:** Movies and TV Shows use the distinct IPTV categories supplied by the provider.
- **Focus and preview:** immediate small expansion, exactly five seconds of continuous focus before attempting trailer playback, and cancellation on focus loss.
- **Navigation:** orange focus follows the selected control; home labels are centered without arrows.
- **Resilience:** fresh-install database creation, current-installation data integrity, offline cached browsing, and interrupted-sync recovery are tested. Historical database upgrades are not required.
- **Security:** LAN-only pairing remains on port 8765; credentials and playback URLs stay private.
- **Performance:** large catalog navigation stays responsive without unbounded memory loading.
- **Playback:** live, movie, and episode playback plus resume, subtitles, and errors are tested on representative devices.

## Rule for coding agents and future changes

Treat this document as **approved product intent**, not a test report. Inspect the actual current code and tests before claiming that a requirement is implemented. For every change, state (a) which requirement it serves, (b) its code locations, (c) tests actually run, and (d) any remaining gap. **Do not restore old product rules from deleted documents or introduce unrelated infrastructure or architecture rewrites.** If a product decision is genuinely missing, flag the gap rather than inventing a requirement.
