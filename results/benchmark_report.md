# TPC-C New Order Benchmark Report

## Methodology
- Tests are run from 1 to 20 concurrent users.
- Each user level is tested multiple times.
- Metrics include TPS, Mean latency, Median latency, and P99 latency.
- The transaction touches two sites: Warehouse site and Customer site.

## Saturation Point
- Estimated saturation point: 4 concurrent users.
- Best observed average TPS: 26.90 TPS at 19 users.

## Textbook Link
According to Özsu's distributed database cost model:

Cost = IO + CPU + Communication

In this benchmark, adding more concurrent users increases CPU cost, IO cost, and communication cost between two database sites. When one of these costs becomes the bottleneck, TPS stops increasing and P99 latency rises.

## Summary Table
| Users | Avg TPS | Median TPS | Mean Latency | Median Latency | P99 Latency | Success | Fail |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 2.71 | 2.95 | 392.04 | 318.53 | 903.08 | 60 | 0 |
| 2 | 3.76 | 3.95 | 557.08 | 497.91 | 1043.48 | 120 | 0 |
| 3 | 8.99 | 9.07 | 332.33 | 317.51 | 496.20 | 180 | 0 |
| 4 | 7.99 | 8.30 | 499.45 | 484.94 | 953.03 | 240 | 0 |
| 5 | 8.39 | 8.24 | 612.62 | 549.92 | 1526.82 | 300 | 0 |
| 6 | 14.93 | 13.74 | 410.43 | 410.20 | 575.29 | 360 | 0 |
| 7 | 11.84 | 10.08 | 620.66 | 601.38 | 1006.68 | 420 | 0 |
| 8 | 11.64 | 11.96 | 694.56 | 666.36 | 1057.75 | 480 | 0 |
| 9 | 13.39 | 13.82 | 660.23 | 622.75 | 1388.50 | 540 | 0 |
| 10 | 11.47 | 10.48 | 869.69 | 800.53 | 2386.74 | 600 | 0 |
| 11 | 17.22 | 17.05 | 623.66 | 600.77 | 949.89 | 660 | 0 |
| 12 | 17.62 | 17.96 | 670.21 | 651.38 | 997.52 | 720 | 0 |
| 13 | 18.62 | 18.85 | 684.55 | 669.11 | 1058.52 | 780 | 0 |
| 14 | 18.72 | 17.86 | 742.45 | 717.82 | 1244.94 | 840 | 0 |
| 15 | 25.11 | 25.48 | 595.56 | 585.42 | 794.26 | 900 | 0 |
| 16 | 23.02 | 22.33 | 681.26 | 655.72 | 1113.15 | 960 | 0 |
| 17 | 21.77 | 22.02 | 766.50 | 737.97 | 1145.59 | 1020 | 0 |
| 18 | 24.48 | 24.76 | 724.20 | 718.08 | 1039.00 | 1080 | 0 |
| 19 | 26.90 | 26.74 | 693.03 | 675.83 | 1012.41 | 1140 | 0 |
| 20 | 22.57 | 20.53 | 906.60 | 862.51 | 1480.14 | 1200 | 0 |

## Generated Charts
- tps_line.png
- latency_line.png
- tps_heatmap.png