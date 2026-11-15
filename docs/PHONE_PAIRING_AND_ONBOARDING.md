# Local phone pairing and onboarding — 0.3.1

## Implemented journey

1. **Choose setup method** — phone setup receives deterministic initial focus; manual TV entry remains available.
2. **Phone pairing** — OH starts a temporary HTTP page on the TV’s local IPv4 address and port `8765`, creates an ephemeral TV key pair and opens a five-minute session.
3. **Browser entry** — a phone on the same Wi-Fi opens `http://<TV-IP>:8765`, retrieves only the TV public key, and encrypts the provider details before sending them directly to the TV.
4. **TV verification** — OH decrypts the envelope, authenticates directly with the Xtream provider and saves credentials through Android Keystore.
5. **Library preparation** — independent Live TV, Movies and TV Shows states update while synchronization runs.
6. **Choose experience** — live Room counts populate the Movies & TV Shows and Live TV cards.
7. **Browse media** — the supplied Movies & Shows navigation is applied with Home, Movies, TV Shows, My List and Search actions plus a paged genre filter using real locally cached TMDB genres.

There is no cloud relay, domain, account, Redis instance or external credential storage. The local HTTP server exists only while the pairing screen is active and closes after submission, cancellation or expiry.

## Local-network boundary

- The phone and TV must be connected to the same LAN/Wi-Fi.
- Guest Wi-Fi, AP isolation, VPN routing or firewall rules can prevent the phone from reaching the TV.
- OH uses local port `8765`; if another application occupies it, phone pairing reports an error and manual TV entry remains available.
- The browser page uses HTTP, as requested. Provider details are protected at the application layer with the TV’s ephemeral RSA public key before submission.
- Manual TV entry remains available when local pairing is unreachable.

## D-pad acceptance pass

Static code establishes initial focus and redundant focus visuals, but release certification still requires an emulator or physical remote pass.

| Screen | Initial focus | Expected traversal |
|---|---|---|
| Setup choice | Finish on your phone | Right to manual setup; Left returns |
| Phone pairing | Manual setup while waiting; retry after error | Select opens the focused fallback/retry; Back cancels the server session |
| Manual setup | Server URL | Down through username, password and actions; Back returns to setup choice |
| Preparing library | Start watching when any scope is usable | Select opens ready content; failed initial sync focuses retry |
| Experience choice | Movies & TV Shows | Right to Live TV; Left returns; Settings remains reachable above |

Verify long provider URLs, missing artwork, expired codes, unavailable relay, invalid credentials, process recreation, tablet touch input and 720p/1080p layouts before release.
