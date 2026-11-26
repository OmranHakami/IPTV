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
- DOM-029. When Live TV, Movies and TV Shows each finish the current synchronization with `Outcome::Success(0)`, L3 exposes `State::Empty` for all three. The approved product behavior is to notify the user that all three catalogs are empty and offer **Enter the hub** or **Retry synchronization**. Consumers must check the outcomes of the current attempt: `Summary::usable()` alone cannot distinguish empty from failure, and cancellation can restore an earlier empty snapshot. Notification, navigation and retry execution belong to the consuming layers.
- Verified the decision against the existing compiled Rust integration tests. `sync_outcomes` and `catalog_replacement_contract` both passed on rerun.
- Search uses the original provider title as specified by DOM-066. Expanding it to TMDB titles requires product approval.
- One session instance arbitrates all its submissions. L6 must share that instance, supply monotonic timestamps, enforce LAN transport rules and stop serving on expiry/cancellation. L1/L2 report continuous card focus and elapsed time. Track enumeration, subtitle lookup, and trailer playback are adapter responsibilities.
- The cached paging port requires a positive caller-selected limit. L4/L5 choose their caps and implement efficient filtering, ordering, offsets and reads. The domain performs no DB, network or filesystem work. No other proposed service port was necessary to implement L3.
- Integration and device behavior, secure storage, encrypted pairing, durable deletion, actual offline playback and sync recovery need verification when their respective layers are connected. This change makes no migration or performance claim.

## Correction cycle 01

The results above describe initial delivery. This cycle used implementation base `23ccc9c31482b4ed1286569e7fbd82e3e0fb5e7d` and independent review `963ec44299908539b156a1ccbd7846cbf615842a`. Reviewed README, the current domain baseline, all five requested audit reports, both independent suites, and Kotlin metadata field handling. Decisions and changed signatures are in [Domain-Layer.md §12](Domain-Layer.md). The [matrix](TRACEABILITY.md) retains all 72 IDs.

| Finding | Audited behavior | Correction and evidence |
|---|---|---|
| A1 · DOM-017/018 | catalog and metadata imported one another | Presentation now lives in a shared leaf module. The original metadata import remains available. Independent pair regression passed. All eight business modules' crate imports were extracted and topologically sorted. A valid ordering is pairing, presentation, account, catalog, sync, personal, discovery, metadata. |
| R4 · DOM-019/020 | A nonblank malformed TMDB date displaced the provider date | Validate TMDB calendar days including century leap rules before selecting the date. Provider originals, year-only provider dates and other valid TMDB fields survived fallback in tests. |
| R5 · DOM-021/022/024/031/064/065 | Restoring equal match inputs revived expired tokens | Live VOD owns an immutable Arc snapshot. Matching inputs change the snapshot allocation. Attachment checks that allocation and response ID/kind. Foreign rows, generations and instances are rejected. Equal-input refresh preserves the snapshot. Adapters must associate actual details with the correct request and validate provider payloads. |
| R2 · DOM-003/004/057/058 | Active response already accepted despite past expiry | Approved and covered by a dedicated acceptance test; no production auth change. |
| R7 · DOM-006/007/067 | A reused unkeyed history could show another user's searches | One collection keys its histories by account, caps each at three, and accepts the account removal obligation. Interleaved writes, filtered queries, deletion, repeated deletion and recreation passed. |

### Execution

Linux x86_64, official Rust 1.100.0, debug tests and release benchmarks. Both production and composed verification crates passed `cargo fmt --check`, `cargo clippy --all-targets -- -D warnings`, `cargo test --all-targets`, and `cargo test --doc`. `cargo tree` contained only oh-domain.

| Check | Audited | Corrected |
|---|---:|---:|
| Original suite | 13 passed | 13 passed with migrated calls |
| New acceptance tests | — | 4 passed |
| Independent ordinary tests | 11 passed, 3 failed | 14 passed |
| Doctests | 1 passed | 1 passed |
| Release benchmark functions (separate execution) | 2 passed | 2 passed |
| LLVM lines | 438/441 (99.32%) | 480/484 (99.17%) |
| LLVM functions | 98/99 (98.99%) | 106/107 (99.07%) |
| LLVM regions | 457/464 (98.49%) | 509/516 (98.64%) |

Locally reproduced the original A1, R4 and R5 failures before correction. Re-ran 64 current-attempt sync combinations, generated title/progress cases and shared-session contention. Four uncovered lines and seven missed regions remain in metadata; branch coverage was not measured. Coverage used Rust LLVM tools and instrument-coverage on the composed suite with tests/toolchain sources excluded. [Comparison and raw performance samples](verification/PERFORMANCE.md).

To reproduce independent verification, copy the crate outside the checkout, export both review test files from the pinned review SHA into its tests directory, then run the following commands in that copy:

```sh
patch -p1 < verification/reviewer-fixtures.patch
cargo fmt --check
cargo clippy --all-targets -- -D warnings
cargo test --all-targets
cargo test --doc
cargo tree
cargo test --release -- --ignored --nocapture --test-threads=1
```

The base independent suite was byte-identical. The extended suite required only [fixture and call-site migrations](verification/reviewer-fixtures.patch) for account IDs and owner-issued tokens. Expected values, rejection cases, timing loops and sample sizes were retained. The original enrichment test now captures fresh evidence for each new generation and explicitly rejects the obsolete token. The independent review branch was read only.

The full diff outside rust/domain against the audited SHA is empty. Gradle still includes only the Android app. Android CI and build/test/lint status for the final commit are linked in PR #63. No TV device, database, provider network, UI or lifecycle integration was exercised. The review branch’s earlier mutation run is historical evidence; this correction cycle did not repeat mutation testing. Independent review remains pending.
