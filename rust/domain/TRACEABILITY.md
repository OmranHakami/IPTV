# L3 requirements traceability

Baseline: Domain-Layer.md §§3–8, checked against main at `b01dd17`. Rust module names below resolve under `src/`. Named integration tests are in `tests/domain.rs`. The verification report explains which effects require an adapter or device.

| ID | Requirement | Implementation | Automated test |
|---|---|---|---|
| DOM-001 | Xtream account | account::XtreamProfile | auth_and_removal |
| DOM-002 | Unique account IDs | account::available_id | unique_accounts_and_opaque_ids |
| DOM-003 | Authenticate before acceptance | account::Account::accept | auth_and_removal |
| DOM-004 | Retain status and expiry | account::Account | auth_and_removal |
| DOM-005 | Latest authentication | account::latest | auth_and_removal |
| DOM-006 | Account-scoped records | catalog::ProviderKey, personal::OnDemand | playback_and_identity, favorites |
| DOM-007 | Deletion includes account rows and credential handle | account::Removal | auth_and_removal |
| DOM-008 | Credential handle only | account::XtreamProfile, CredentialRef | auth_and_removal |
| DOM-009 | Live, movie and series catalogs | catalog::Kind, Channel, VodType | ordering, discovery |
| DOM-010 | Opaque provider identifiers | catalog::ProviderKey | unique_accounts_and_opaque_ids |
| DOM-011 | Typed identities and parent series | catalog::VodKey, EpisodeKey | playback_and_identity, favorites |
| DOM-012 | Independent category namespaces | catalog::CategoryKey, discovery::Browse | discovery, ordering |
| DOM-013 | Series, season and episode identity | catalog::Episode | ordering, playback_and_identity |
| DOM-014 | Channel number and EPG identity | catalog::Channel | ordering |
| DOM-015 | Playable provider row without optional enrichment | metadata::Vod::new, display | enrichment_and_refresh |
| DOM-016 | Independent catalog counts | sync::Summary | sync_outcomes |
| DOM-017 | Source separation | metadata::Vod, Enrichment | enrichment_and_refresh |
| DOM-018 | Provider membership, category and playback identity | catalog::ProviderVod, personal::Playback | enrichment_and_refresh, playback_and_identity |
| DOM-019 | Preferred presentation fields | metadata::Vod::display | enrichment_and_refresh |
| DOM-020 | Per-field fallback | metadata::Vod::display | enrichment_and_refresh |
| DOM-021 | Supplied hint and resolved match | metadata::MatchInputs, Enrichment | matching, enrichment_and_refresh |
| DOM-022 | Invalidate obsolete movie enrichment | metadata::Vod::refresh, attach | enrichment_and_refresh |
| DOM-023 | Unmatched row remains available | metadata::Vod::display | enrichment_and_refresh |
| DOM-024 | Reject unrelated match | metadata::MatchInputs::accepts | matching |
| DOM-025 | Independent scopes | sync::Summary | sync_outcomes |
| DOM-026 | Scope progress and outcomes | sync::Refresh | sync_outcomes |
| DOM-027 | Partial success | sync::Summary::usable | sync_outcomes |
| DOM-028 | Last usable cache after failure | sync::Refresh::finish | sync_outcomes, catalog_replacement_contract |
| DOM-029 | Empty versus failed | sync::State | sync_outcomes |
| DOM-030 | Removal only after successful refresh | sync::Outcome::catalog_action | catalog_replacement_contract |
| DOM-031 | Retain valid enrichment | metadata::Vod::refresh | enrichment_and_refresh |
| DOM-032 | Keep personal records across refresh | sync::RefreshAction, personal::Progress, Favorites | catalog_replacement_contract |
| DOM-033 | Live, movie and episode targets | personal::Playback, OnDemand | playback_and_identity |
| DOM-034 | URL-independent playback identity | personal::OnDemand, Playback | playback_and_identity |
| DOM-035 | Movie and episode progress | personal::Progress::record | playback_and_identity |
| DOM-036 | 90 percent completion | personal::Progress::record | playback_and_identity |
| DOM-037 | Five-second minimum resume | personal::resume | playback_and_identity |
| DOM-038 | Restart completed item | personal::resume | playback_and_identity |
| DOM-039 | Restart out-of-range position | personal::resume | playback_and_identity |
| DOM-040 | Reject nonpositive duration | personal::Progress::record | playback_and_identity |
| DOM-041 | URL-free progress contract | personal::Progress, Playback | playback_and_identity, Progress compile-fail doctest |
| DOM-042 | Movie favorites | personal::Favorites | favorites |
| DOM-043 | Series favorites | personal::Favorites | favorites |
| DOM-044 | Unique scoped favorite | personal::Favorites::add | favorites |
| DOM-045 | Newest favorites first | personal::Favorites::newest | favorites |
| DOM-046 | Continue watching by account and recency | personal::continue_watching | playback_and_identity |
| DOM-047 | Exclude completed progress | personal::continue_watching | playback_and_identity |
| DOM-048 | Episode progress includes series | catalog::EpisodeKey, personal::OnDemand | playback_and_identity |
| DOM-049 | Independent movie and series search | discovery::Browse::matches | discovery |
| DOM-050 | Provider category filter | discovery::Browse | discovery, ordering |
| DOM-051 | Bounded query contract | discovery::PageRequest, Page, CachedCatalog | bounded_snapshot |
| DOM-052 | Movie order | catalog::vod_order | ordering |
| DOM-053 | Series order | catalog::vod_order | ordering |
| DOM-054 | Channel order | catalog::channel_order | ordering |
| DOM-055 | Episode order | catalog::episode_order | ordering |
| DOM-056 | Cached browse port | discovery::CachedCatalog | bounded_snapshot |
| DOM-057 | Authentication flag and active status | account::Account::accept | auth_and_removal |
| DOM-058 | Typed failure reasons | account::AuthError | auth_and_removal |
| DOM-059 | Five-minute one-time session | pairing::Session::accept | pairing |
| DOM-060 | Pairing code normalization | pairing::normalize | pairing |
| DOM-061 | Clean title and prefer explicit year | metadata::search_terms | matching |
| DOM-062 | Unique normalized title/year match | metadata::MatchInputs::fallback | matching |
| DOM-063 | Reject ambiguous multipage results | metadata::MatchInputs::fallback | matching |
| DOM-064 | Validate supplied TMDB ID | metadata::MatchInputs::supplied | matching |
| DOM-065 | Series matching and invalidation | metadata::MatchInputs, Vod::refresh | matching, enrichment_and_refresh |
| DOM-066 | Trimmed original-name substring search | discovery::Browse::matches | discovery |
| DOM-067 | Three recent successful distinct searches | discovery::RecentSearches | discovery |
| DOM-068 | Continuous focus and immediate metadata | discovery::CardFocus | focus_and_tracks |
| DOM-069 | Missing trailer fallback | discovery::preview | focus_and_tracks |
| DOM-070 | Track selection and optional lookup eligibility | personal::selectable, subtitle_lookup_enabled | focus_and_tracks |
| DOM-071 | Replace episode set only on success | sync::Outcome::catalog_action | catalog_replacement_contract |
| DOM-072 | Failed/cancelled refresh retains usable snapshot and personal records | sync::Refresh::finish, Outcome::catalog_action | sync_outcomes, catalog_replacement_contract |
