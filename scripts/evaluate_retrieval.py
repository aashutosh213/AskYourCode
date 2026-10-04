#!/usr/bin/env python3
"""Run the labelled AskYourCode retrieval benchmark against a local backend."""

import argparse
import json
import time
import urllib.error
import urllib.request
from pathlib import Path


DEFAULT_DATASET = Path(__file__).resolve().parents[1] / ".ai/evaluation/askyourcode-retrieval.json"
MODES = ("keyword", "vector", "hybrid", "reranked")


def request_json(url, method="GET", payload=None, timeout=120):
    body = None if payload is None else json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(
        url,
        data=body,
        method=method,
        headers={"Content-Type": "application/json"},
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"{method} {url} returned HTTP {error.code}: {detail}") from error
    except urllib.error.URLError as error:
        raise RuntimeError(f"Could not reach {url}: {error.reason}") from error


def search(base_url, mode, payload):
    result = request_json(f"{base_url}/api/search/{mode}", "POST", payload)
    if mode != "reranked":
        return result

    deadline = time.monotonic() + 300
    while result.get("status") in ("QUEUED", "RUNNING"):
        if time.monotonic() >= deadline:
            raise RuntimeError("Timed out waiting for reranked search job")
        time.sleep(0.25)
        result = request_json(f"{base_url}/api/search/jobs/{result['jobId']}")
    if result.get("status") != "COMPLETED" or result.get("result") is None:
        raise RuntimeError(result.get("message", "Reranked search failed"))
    return result["result"]


def hit_key(hit):
    return (hit.get("filePath", ""), hit.get("symbolName", ""))


def evaluate_mode(base_url, repository_path, cases, k, mode):
    recall_sum = precision_sum = reciprocal_rank_sum = latency_sum = 0.0
    warnings = set()
    for case in cases:
        payload = {"query": case["query"], "repositoryPath": repository_path, "limit": k}
        started = time.perf_counter()
        response = search(base_url, mode, payload)
        latency_sum += (time.perf_counter() - started) * 1000
        if response.get("warning"):
            warnings.add(response["warning"])

        relevant = {(item["filePath"], item["symbolName"]) for item in case["relevant"]}
        ranked_hits = response.get("results", [])[:k]
        retrieved = [hit_key(hit) for hit in ranked_hits]
        relevant_retrieved = {key for key in retrieved if key in relevant}
        recall_sum += len(relevant_retrieved) / len(relevant)
        precision_sum += len(relevant_retrieved) / k
        first_relevant_rank = next(
            (rank for rank, key in enumerate(retrieved, start=1) if key in relevant), None
        )
        reciprocal_rank_sum += 0.0 if first_relevant_rank is None else 1.0 / first_relevant_rank

    count = len(cases)
    return {
        "recallAtK": recall_sum / count,
        "precisionAtK": precision_sum / count,
        "meanReciprocalRankAtK": reciprocal_rank_sum / count,
        "meanLatencyMillis": latency_sum / count,
        "warnings": sorted(warnings),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", required=True, help="Exact repository path previously indexed by the backend")
    parser.add_argument("--base-url", default="http://localhost:8080", help="Spring Boot base URL")
    parser.add_argument("--dataset", type=Path, default=DEFAULT_DATASET)
    parser.add_argument("-k", type=int, default=5)
    args = parser.parse_args()
    if args.k <= 0:
        parser.error("-k must be positive")

    dataset = json.loads(args.dataset.read_text(encoding="utf-8"))
    base_url = args.base_url.rstrip("/")
    report = {
        "dataset": dataset["name"],
        "repositoryPath": args.repository,
        "k": args.k,
        "queryCount": len(dataset["cases"]),
        "strategies": {},
    }
    for mode in MODES:
        report["strategies"][mode] = evaluate_mode(
            base_url, args.repository, dataset["cases"], args.k, mode
        )
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
