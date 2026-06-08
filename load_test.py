import argparse
import csv
import math
import os
import random
import statistics
import time
from concurrent.futures import ThreadPoolExecutor, as_completed

import requests
import uuid
import matplotlib.pyplot as plt

URL = "http://localhost:8080/api/orders/new"
RESULT_DIR = "results"


def percentile(values, p):
    if not values:
        return 0.0

    values = sorted(values)
    k = (len(values) - 1) * (p / 100.0)
    f = math.floor(k)
    c = math.ceil(k)

    if f == c:
        return values[int(k)]

    return values[f] * (c - k) + values[c] * (k - f)


def make_payload():
    district_id = random.randint(1, 50)
    warehouse_id = ((district_id - 1) % 10) + 1
    customer_id = district_id + 50 * random.randint(0, 199)

    return {
        "txId": str(uuid.uuid4()),
        "itemId": random.randint(1, 2000),
        "warehouseId": warehouse_id,
        "districtId": district_id,
        "customerId": customer_id,
        "orderQty": random.randint(1, 3),
    }


def send_request(req_id):
    payload = make_payload()
    start = time.perf_counter()

    try:
        response = requests.post(URL, json=payload, timeout=10)
        latency = time.perf_counter() - start

        # ok = response.status_code == 200 and (
        #     "THÀNH CÔNG" in response.text or "SUCCESS" in response.text.upper()
        # )
        ok = response.status_code == 200 and (
            "SUCCESS" in response.text.upper()
        )

        return {
            "req_id": req_id,
            "ok": ok,
            "status": response.status_code,
            "latency": latency,
        }

    except Exception:
        latency = time.perf_counter() - start
        return {
            "req_id": req_id,
            "ok": False,
            "status": 0,
            "latency": latency,
        }


def run_once(users, requests_per_user):
    total_requests = users * requests_per_user
    results = []

    start = time.perf_counter()

    with ThreadPoolExecutor(max_workers=users) as executor:
        futures = [
            executor.submit(send_request, i)
            for i in range(total_requests)
        ]

        for future in as_completed(futures):
            results.append(future.result())

    elapsed = time.perf_counter() - start

    latencies = [r["latency"] for r in results]
    successes = sum(1 for r in results if r["ok"])
    failures = total_requests - successes

    return {
        "users": users,
        "total_requests": total_requests,
        "elapsed_sec": elapsed,
        "successes": successes,
        "failures": failures,
        "tps": successes / elapsed if elapsed > 0 else 0,
        "mean_latency_ms": statistics.mean(latencies) * 1000,
        "median_latency_ms": statistics.median(latencies) * 1000,
        "p99_latency_ms": percentile(latencies, 99) * 1000,
    }


def aggregate(raw_rows):
    grouped = {}

    for row in raw_rows:
        grouped.setdefault(row["users"], []).append(row)

    summary = []

    for users, rows in sorted(grouped.items()):
        summary.append({
            "users": users,
            "runs": len(rows),
            "avg_tps": statistics.mean(r["tps"] for r in rows),
            "median_tps": statistics.median(r["tps"] for r in rows),
            "mean_latency_ms": statistics.mean(r["mean_latency_ms"] for r in rows),
            "median_latency_ms": statistics.mean(r["median_latency_ms"] for r in rows),
            "p99_latency_ms": statistics.mean(r["p99_latency_ms"] for r in rows),
            "successes": sum(r["successes"] for r in rows),
            "failures": sum(r["failures"] for r in rows),
        })

    return summary


def find_saturation(summary):
    for prev, cur in zip(summary, summary[1:]):
        if prev["avg_tps"] <= 0:
            continue

        growth = (cur["avg_tps"] - prev["avg_tps"]) / prev["avg_tps"]

        if growth < 0.05:
            return cur["users"]

    best = max(summary, key=lambda r: r["avg_tps"])
    return best["users"]


def write_csv(path, rows, fieldnames):
    with open(path, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def make_charts(summary, raw_rows, out_dir):
    users = [r["users"] for r in summary]

    plt.figure(figsize=(9, 5))
    plt.plot(users, [r["avg_tps"] for r in summary], marker="o")
    plt.xlabel("Concurrent users")
    plt.ylabel("TPS")
    plt.title("New Order Throughput vs Concurrent Users")
    plt.grid(True)
    plt.tight_layout()
    plt.savefig(os.path.join(out_dir, "tps_line.png"), dpi=160)
    plt.close()

    plt.figure(figsize=(9, 5))
    plt.plot(users, [r["mean_latency_ms"] for r in summary], marker="o", label="Mean")
    plt.plot(users, [r["median_latency_ms"] for r in summary], marker="o", label="Median")
    plt.plot(users, [r["p99_latency_ms"] for r in summary], marker="o", label="P99")
    plt.xlabel("Concurrent users")
    plt.ylabel("Latency ms")
    plt.title("Latency Statistics")
    plt.legend()
    plt.grid(True)
    plt.tight_layout()
    plt.savefig(os.path.join(out_dir, "latency_line.png"), dpi=160)
    plt.close()

    runs = sorted(set(r["run"] for r in raw_rows))
    matrix = []

    for run in runs:
        row = []
        for user in users:
            value = next(
                r["tps"]
                for r in raw_rows
                if r["run"] == run and r["users"] == user
            )
            row.append(value)
        matrix.append(row)

    plt.figure(figsize=(10, 4))
    plt.imshow(matrix, aspect="auto")
    plt.colorbar(label="TPS")
    plt.xticks(range(len(users)), users)
    plt.yticks(range(len(runs)), runs)
    plt.xlabel("Concurrent users")
    plt.ylabel("Run")
    plt.title("TPS Heatmap")
    plt.tight_layout()
    plt.savefig(os.path.join(out_dir, "tps_heatmap.png"), dpi=160)
    plt.close()


def write_report(path, summary, saturation):
    best = max(summary, key=lambda r: r["avg_tps"])

    lines = [
        "# TPC-C New Order Benchmark Report",
        "",
        "## Methodology",
        "- Tests are run from 1 to 20 concurrent users.",
        "- Each user level is tested multiple times.",
        "- Metrics include TPS, Mean latency, Median latency, and P99 latency.",
        "- The transaction touches two sites: Warehouse site and Customer site.",
        "",
        "## Saturation Point",
        f"- Estimated saturation point: {saturation} concurrent users.",
        f"- Best observed average TPS: {best['avg_tps']:.2f} TPS at {best['users']} users.",
        "",
        "## Textbook Link",
        "According to Özsu's distributed database cost model:",
        "",
        "Cost = IO + CPU + Communication",
        "",
        "In this benchmark, adding more concurrent users increases CPU cost, IO cost, and communication cost between two database sites. When one of these costs becomes the bottleneck, TPS stops increasing and P99 latency rises.",
        "",
        "## Summary Table",
        "| Users | Avg TPS | Median TPS | Mean Latency | Median Latency | P99 Latency | Success | Fail |",
        "|---:|---:|---:|---:|---:|---:|---:|---:|",
    ]

    for r in summary:
        lines.append(
            f"| {r['users']} | {r['avg_tps']:.2f} | {r['median_tps']:.2f} | "
            f"{r['mean_latency_ms']:.2f} | {r['median_latency_ms']:.2f} | "
            f"{r['p99_latency_ms']:.2f} | {r['successes']} | {r['failures']} |"
        )

    lines.extend([
        "",
        "## Generated Charts",
        "- tps_line.png",
        "- latency_line.png",
        "- tps_heatmap.png",
    ])

    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--runs", type=int, default=3)
    parser.add_argument("--requests-per-user", type=int, default=20)
    parser.add_argument("--out", default=RESULT_DIR)

    args = parser.parse_args()

    os.makedirs(args.out, exist_ok=True)

    raw_rows = []

    for users in range(1, 21):
        for run in range(1, args.runs + 1):
            print(f"Running users={users}, run={run}")

            row = run_once(users, args.requests_per_user)
            row["run"] = run

            raw_rows.append(row)

            print(
                f"TPS={row['tps']:.2f}, "
                f"Mean={row['mean_latency_ms']:.2f}ms, "
                f"Median={row['median_latency_ms']:.2f}ms, "
                f"P99={row['p99_latency_ms']:.2f}ms"
            )

            time.sleep(0.5)

    summary = aggregate(raw_rows)
    saturation = find_saturation(summary)

    write_csv(
        os.path.join(args.out, "raw_runs.csv"),
        raw_rows,
        [
            "users",
            "run",
            "total_requests",
            "elapsed_sec",
            "successes",
            "failures",
            "tps",
            "mean_latency_ms",
            "median_latency_ms",
            "p99_latency_ms",
        ],
    )

    write_csv(
        os.path.join(args.out, "summary.csv"),
        summary,
        [
            "users",
            "runs",
            "avg_tps",
            "median_tps",
            "mean_latency_ms",
            "median_latency_ms",
            "p99_latency_ms",
            "successes",
            "failures",
        ],
    )

    make_charts(summary, raw_rows, args.out)
    write_report(os.path.join(args.out, "benchmark_report.md"), summary, saturation)

    print("DONE. Check results folder.")


if __name__ == "__main__":
    main()