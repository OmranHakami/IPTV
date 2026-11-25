# Rust domain implementation and verification

L3 is a standalone Cargo library under `rust/domain/`. Base commit `b01dd17f78bfb3dcc2a7cd6db4a5472cc8c415df`. See [the matrix](TRACEABILITY.md) for DOM-001 through DOM-072.

## Evidence

Compared the baseline with README.md, account and catalog policies in OhRepository, Entities, Daos, PlaybackPolicy, XtreamGateway, LocalPairingServer, TmdbGateway and the search logic in OhViewModel. Kotlin regression references included PlaybackPolicyTest, SyncSummaryTest, MovieEnrichmentFreshnessTest, SeriesEpisodesTest and TmdbMovieMatchTest. The Rust tests use independent assertions at policy boundaries.

From this directory, Rust 1.100.0 passed:

```sh
cargo fmt --check
cargo clippy --all-targets -- -D warnings
cargo test
cargo tree
```

13 integration tests and one compile-fail doctest passed. Coverage includes 16 simultaneous submissions to one pairing session, exact timing boundaries, ambiguous and stale matches, account/type/parent collisions, offline reads through an in-memory test adapter, and cancelled and explicitly empty refreshes. The dependency tree contains only this crate. No unsafe code is permitted.

Android isolation was checked by comparing the complete Git tree outside `rust/domain/` against the base and checking Gradle's project inputs. Kotlin sources, Android build configuration, and CI configuration are byte-identical. No JNI interface or Gradle task references the crate. Android CI results belong to the PR checks. No on-device playback or performance measurement was performed.

## Decisions and remaining responsibilities

- Opaque provider IDs retain leading zeros. Episode keys include parent series. Credential references and provider identities cross the boundary. The platform handles secrets and builds transient playback URLs. L3 exposes no URL field on progress, no player, logging or persistence. Adapters must put actual identifiers in ID fields.
- L3 checks an allocated account ID against a snapshot, selects the latest authenticated account and identifies both account state and credentials for removal. Atomic uniqueness, durable cleanup and retry belong to L4/L5/L7. A typed auth failure can cross the boundary without carrying a provider response or secret.
- Both movie and series supplied matches must have the correct content kind, ID, normalized title/original title and known year. Fallback requires one distinct positive matching ID on a single page. Applying details requires the same ID/kind and current account/content match inputs. Identical inputs preserve enrichment on refresh. Provider fields remain independently accessible.
- A refresh decision permits replacement only for a successfully validated catalog scope or episode set. An explicit empty success clears that set. An adapter must validate the complete provider response and apply replacements transactionally. Personal data is outside this operation. L4 owns cancellation delivery and refresh concurrency.
- An all-empty sync reports success per scope and zero usable sections. Whether onboarding may enter the hub is an unresolved navigation decision. Search uses the original provider title as specified by DOM-066. Expanding it to TMDB titles requires product approval. Neither gate navigation nor expanded search is implemented.
- One session instance arbitrates all its submissions. L6 must share that instance, supply monotonic timestamps, enforce LAN transport rules and stop serving on expiry/cancellation. L1/L2 report continuous card focus and elapsed time. Track enumeration, subtitle lookup, and trailer playback are adapter responsibilities.
- The cached paging port requires a positive caller-selected limit. L4/L5 choose their caps and implement efficient filtering, ordering, offsets and reads. The domain performs no DB, network or filesystem work. No other proposed service port was necessary to implement L3.
- Integration and device behavior, secure storage, encrypted pairing, durable deletion, actual offline playback and sync recovery need verification when their respective layers are connected. This change makes no migration or performance claim.
