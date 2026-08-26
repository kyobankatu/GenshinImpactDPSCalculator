"""Run one checkpoint's local policy-value service and matched Java search."""

from __future__ import annotations

import argparse
import hashlib
import json
import shlex
import socket
import subprocess
import sys
import time
from pathlib import Path

from expert_dataset import load_expert_dataset


def build_benchmark_arguments(
    dataset,
    output,
    checkpoint_fingerprint,
    host,
    port,
    seeds,
    budget,
    split,
):
    """Build strict SampleLauncher arguments for one live-guidance cell."""
    if (
        not seeds
        or seeds != tuple(sorted(set(seeds)))
        or budget <= 0
        or port <= 0
        or len(checkpoint_fingerprint) != 64
    ):
        raise ValueError("Live search benchmark configuration is invalid")
    return [
        "BenchmarkRotationSearch",
        "--output",
        str(output),
        "--split",
        split,
        "--seeds",
        ",".join(str(seed) for seed in seeds),
        "--budget",
        str(budget),
        "--dataset",
        str(dataset),
        "--guidance-mode",
        "live",
        "--policy-value-host",
        host,
        "--policy-value-port",
        str(port),
        "--policy-value-timeout-ms",
        "1000",
        "--checkpoint-fingerprint",
        checkpoint_fingerprint,
    ]


def validate_live_report(path, dataset_source_hash, checkpoint_fingerprint, budget):
    """Validate the three-arm report before accepting one experiment cell."""
    try:
        report = json.loads(Path(path).read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise ValueError("Live search report is unavailable or malformed") from error
    provenance = report.get("checkpointProvenance", {})
    if (
        provenance.get("checkpointRevision")
        != f"sha256:{checkpoint_fingerprint}"
        or provenance.get("datasetSourceHash") != dataset_source_hash
    ):
        raise ValueError("Live search report checkpoint provenance mismatch")
    metrics = report.get("metrics")
    if not isinstance(metrics, list) or not metrics:
        raise ValueError("Live search report has no metrics")
    methods = {metric.get("method") for metric in metrics}
    expected = {
        "deterministic-random",
        "unguided-evolutionary",
        "policy-guided",
    }
    if methods != expected:
        raise ValueError("Live search report method coverage mismatch")
    if any(metric.get("simulatorCalls") != budget for metric in metrics):
        raise ValueError("Live search report call budget mismatch")
    return report


def run_live_search(args) -> None:
    """Own the bounded service lifecycle and fail closed on partial output."""
    tracked = subprocess.run(
        ["git", "status", "--porcelain", "--untracked-files=no"],
        check=True,
        capture_output=True,
        text=True,
    ).stdout
    if tracked.strip():
        raise ValueError("Live search requires a clean tracked working tree")
    dataset = load_expert_dataset(args.dataset)
    checkpoint = Path(args.checkpoint)
    checkpoint_fingerprint = hashlib.sha256(checkpoint.read_bytes()).hexdigest()
    port = args.port or _free_port(args.host)
    service_command = [
        sys.executable,
        str(Path(__file__).with_name("serve_policy_value.py")),
        "--checkpoint",
        str(checkpoint),
        "--dataset-source-hash",
        dataset.source_hash,
        "--host",
        args.host,
        "--port",
        str(port),
    ]
    benchmark_arguments = build_benchmark_arguments(
        args.dataset,
        args.output,
        checkpoint_fingerprint,
        args.host,
        port,
        args.seeds,
        args.budget,
        args.split,
    )
    gradle_command = [
        "./gradlew",
        "BenchmarkRotationSearch",
        f"--args={shlex.join(benchmark_arguments)}",
    ]
    log_path = Path(args.log)
    log_path.parent.mkdir(parents=True, exist_ok=True)
    with log_path.open("w", encoding="utf-8") as log:
        service = subprocess.Popen(
            service_command,
            stdout=log,
            stderr=subprocess.STDOUT,
            text=True,
        )
        try:
            _wait_for_service(service, args.host, port, args.startup_timeout)
            subprocess.run(
                gradle_command,
                check=True,
                stdout=log,
                stderr=subprocess.STDOUT,
                text=True,
            )
        finally:
            service.terminate()
            try:
                service.wait(timeout=10.0)
            except subprocess.TimeoutExpired:
                service.kill()
                service.wait(timeout=5.0)
    validate_live_report(
        args.output,
        dataset.source_hash,
        checkpoint_fingerprint,
        args.budget,
    )


def _wait_for_service(process, host, port, timeout):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise RuntimeError("Policy-value service exited before readiness")
        try:
            with socket.create_connection((host, port), timeout=0.2):
                return
        except OSError:
            time.sleep(0.1)
    raise TimeoutError("Policy-value service readiness timed out")


def _free_port(host):
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as listener:
        listener.bind((host, 0))
        return listener.getsockname()[1]


def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset", required=True)
    parser.add_argument("--checkpoint", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--log", required=True)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=0)
    parser.add_argument("--seeds", default="104729,130363,155921")
    parser.add_argument("--budget", type=int, default=128)
    parser.add_argument("--split", choices=("all", "train", "validation", "holdout"), default="all")
    parser.add_argument("--startup-timeout", type=float, default=30.0)
    values = parser.parse_args()
    values.seeds = tuple(int(value) for value in values.seeds.split(","))
    return values


def main() -> None:
    run_live_search(parse_args())


if __name__ == "__main__":
    main()
