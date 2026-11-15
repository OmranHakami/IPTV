# Large catalog update — 0.2.0

This release targets Xtream catalogs containing tens of thousands of Live TV, movie and TV-show records. Room remains the source of truth; the network only feeds bounded transactions into it.

| Layer | Before | After 0.2.0 |
|---|---|---|
| Network | A 60-second whole-call deadline could abort a healthy long import | 12-second connect timeout, 90-second idle-read timeout, and no whole-call deadline |
| JSON parsing | Streamed in 500-record batches | Cancellation-aware streaming in 300-record batches |
| Database writes | Batch writes, with metadata reads and writes separated | Each batch's metadata merge and upsert is one Room transaction |
| Failure safety | Generation cleanup protected old rows, but failure state ignored them | Failed refreshes report `Stale` and keep the retained catalog usable |
| Live TV memory | Entire filtered channel list was emitted as `Flow<List<…>>` | Room `PagingSource` loads only the visible window |
| Catalog memory | Movies and shows were paged without a maximum retained window | All three catalogs use 60-item pages with a 300-item window |
| Progress | Processed count plus success/failure | Waiting, Running, Ready, Empty, Stale, or Failed, with elapsed time and last-success time |
| Verification | Small tolerant parsing tests | Added a 25,000-record bounded-batch parser test and stale-cache summary test |

Synchronization remains sequential—Live TV, then Movies, then TV Shows—to avoid placing three very large responses and write streams under memory and I/O pressure simultaneously.
