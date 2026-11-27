# Release benchmark comparison

Original CI baseline and protocol: [review run 45728229384](https://github.com/OmranHakami/IPTV/actions/runs/45728229384). Its published measurements remain the historical baseline. This paired local comparison uses the same build host, toolchain and pinned reviewer fixtures for both revisions. After initial runs, measurements were repeated in corrected, audited, audited, corrected order. Each release invocation retained the reviewer’s three warmups, ten base samples and five extended samples per size. The table pools the two measured runs per revision (20 base and 10 extended samples per cell). Durations in ms; p95 is nearest rank. Raw observations are in [benchmark-samples.csv](benchmark-samples.csv).

| Suite / operation | Rows | Audited median / p95 | Corrected median / p95 | Median change |
|---|---:|---:|---:|---:|
| base / favorite | 1,000 | 0.0064 / 0.0070 | 0.0062 / 0.0076 | -2.1% |
| base / recent | 1,000 | 0.0021 / 0.0022 | 0.0021 / 0.0023 | +0.0% |
| base / search | 1,000 | 0.0236 / 0.0239 | 0.0236 / 0.0241 | +0.1% |
| base / favorite | 10,000 | 0.1049 / 0.1484 | 0.1234 / 0.1792 | +17.6% |
| base / recent | 10,000 | 0.0216 / 0.0375 | 0.0265 / 0.0372 | +22.8% |
| base / search | 10,000 | 0.2476 / 0.3128 | 0.2466 / 0.2729 | -0.4% |
| base / favorite | 50,000 | 1.4503 / 1.7701 | 1.3403 / 1.7687 | -7.6% |
| base / recent | 50,000 | 0.3875 / 0.4681 | 0.4066 / 0.4552 | +4.9% |
| base / search | 50,000 | 1.3757 / 1.5478 | 1.3523 / 1.5046 | -1.7% |
| base / favorite | 100,000 | 2.8642 / 4.0624 | 3.1254 / 4.8474 | +9.1% |
| base / recent | 100,000 | 0.8626 / 0.9289 | 0.8795 / 1.0848 | +2.0% |
| base / search | 100,000 | 2.6161 / 2.8240 | 2.6558 / 3.1686 | +1.5% |
| extended / alternate | 1,000 | 0.0070 / 0.0077 | 0.0069 / 0.0080 | -1.0% |
| extended / lifecycle | 1,000 | 1.0322 / 1.6711 | 0.8566 / 1.1517 | -17.0% |
| extended / match | 1,000 | 0.2053 / 0.5328 | 0.1969 / 0.2468 | -4.1% |
| extended / recent | 1,000 | 0.0034 / 0.0044 | 0.0041 / 0.0187 | +20.7% |
| extended / search | 1,000 | 0.0262 / 0.0400 | 0.0264 / 0.0313 | +0.7% |
| extended / alternate | 10,000 | 0.0837 / 0.1236 | 0.1020 / 0.2575 | +21.9% |
| extended / lifecycle | 10,000 | 10.3331 / 12.5851 | 8.9947 / 11.1760 | -13.0% |
| extended / match | 10,000 | 2.2721 / 2.5945 | 2.3961 / 3.5121 | +5.5% |
| extended / recent | 10,000 | 0.0755 / 0.1141 | 0.0828 / 0.1524 | +9.7% |
| extended / search | 10,000 | 0.3002 / 0.4801 | 0.3118 / 0.9724 | +3.9% |
| extended / alternate | 50,000 | 1.2984 / 1.4983 | 1.2559 / 2.0135 | -3.3% |
| extended / lifecycle | 50,000 | 50.9927 / 56.2756 | 43.8373 / 64.4458 | -14.0% |
| extended / match | 50,000 | 16.6716 / 18.6299 | 15.9362 / 18.7010 | -4.4% |
| extended / recent | 50,000 | 0.4774 / 0.5213 | 0.4648 / 0.6645 | -2.6% |
| extended / search | 50,000 | 4.6214 / 5.0047 | 3.5284 / 6.3390 | -23.7% |
| extended / alternate | 100,000 | 2.4394 / 2.6806 | 2.8010 / 3.6599 | +14.8% |
| extended / lifecycle | 100,000 | 102.4146 / 113.9650 | 87.7929 / 88.8582 | -14.3% |
| extended / match | 100,000 | 33.5002 / 53.9204 | 32.5431 / 34.6007 | -2.9% |
| extended / recent | 100,000 | 0.8981 / 0.9621 | 0.9112 / 1.0633 | +1.5% |
| extended / search | 100,000 | 9.4749 / 20.1135 | 9.5095 / 10.9742 | +0.4% |

The corrected metadata lifecycle median decreased at all four sizes. The 50k lifecycle p95 rose from 56.28 to 64.45 ms, and 10k standalone matching median rose from 2.27 to 2.40 ms. At 100k, favorites median rose 9.1% on unchanged code, and the unchanged alternative-search control rose 14.8%. These shared-host samples show variability and cannot isolate a causal regression. No P1–P3 optimization was implemented. The harness uses missing TMDB dates during its timed lifecycle and does not isolate valid-date parsing, account-history operations, allocations or TV hardware.
