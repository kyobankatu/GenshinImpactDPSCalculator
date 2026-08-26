"""Evaluate one frozen-dataset rotation policy checkpoint offline."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
import statistics
import tempfile
import time
from pathlib import Path

import torch

from expert_dataset import (
    fingerprints_by_split,
    load_expert_dataset,
    training_records,
    value_normalization,
)
from pretrain_expert_policy import PRETRAINING_REVISION
from recurrent_ppo import load_policy


EVALUATION_SCHEMA_VERSION = 1


def evaluate_checkpoint(dataset_path, checkpoint_path, device="cpu"):
    """Return policy, value, and inference metrics without simulator claims."""
    dataset = load_expert_dataset(dataset_path)
    checkpoint = Path(checkpoint_path)
    payload = torch.load(checkpoint, map_location=device, weights_only=False)
    if payload.get("pretraining_revision") != PRETRAINING_REVISION:
        raise ValueError("Rotation checkpoint pretraining revision mismatch")
    if payload.get("dataset_source_hash") != dataset.source_hash:
        raise ValueError("Rotation checkpoint dataset fingerprint is stale")
    policy, _ = load_policy(checkpoint, map_location=device)
    policy.to(device)
    policy.eval()
    mean, scale = value_normalization(training_records(dataset))
    split_metrics = {}
    all_latencies = []
    for split in ("train", "validation", "holdout"):
        records = tuple(record for record in dataset.records if record.split == split)
        split_metrics[split], latencies = _evaluate_records(
            policy,
            records,
            mean,
            scale,
            torch.device(device),
        )
        all_latencies.extend(latencies)
    config = payload.get("pretraining_config")
    if not isinstance(config, dict) or config.get("policyType") != payload["policy_type"]:
        raise ValueError("Rotation checkpoint training configuration is malformed")
    return {
        "schemaVersion": EVALUATION_SCHEMA_VERSION,
        "model": payload["policy_type"],
        "seed": payload.get("pretraining_seed"),
        "datasetSourceHash": dataset.source_hash,
        "trainingFingerprints": list(fingerprints_by_split(dataset)["train"]),
        "holdoutFingerprints": list(fingerprints_by_split(dataset)["holdout"]),
        "checkpointFingerprint": hashlib.sha256(checkpoint.read_bytes()).hexdigest(),
        "checkpointSelectionRule": "final-matched-epoch",
        "optimizationSteps": sum(
            int(item["optimizer_steps"])
            for item in payload["pretraining_history"]
        ),
        "trainingConfig": config,
        "splitMetrics": split_metrics,
        "inferenceLatencyMillis": (
            statistics.median(all_latencies) if all_latencies else 0.0
        ),
    }


def _evaluate_records(policy, records, mean, scale, device):
    top1 = 0
    top3 = 0
    targets = []
    predictions = []
    latencies = []
    decisions = 0
    with torch.no_grad():
        for record in records:
            recurrent = torch.zeros(
                1,
                policy.recurrent_state_size,
                dtype=torch.float32,
                device=device,
            )
            terminal_score = float(record.terminal_objective["objectiveScore"])
            for decision in record.decisions:
                observation = torch.tensor(
                    [decision.observation], dtype=torch.float32, device=device
                )
                action_mask = torch.tensor(
                    [decision.legal_action_mask],
                    dtype=torch.float32,
                    device=device,
                )
                _synchronize(device)
                started = time.perf_counter_ns()
                output = policy.act(
                    observation,
                    recurrent,
                    action_mask,
                    deterministic=True,
                )
                _synchronize(device)
                latencies.append((time.perf_counter_ns() - started) / 1_000_000.0)
                recurrent = output["hidden"]
                probabilities = output["probabilities"][0]
                teacher = int(torch.tensor(decision.visit_policy_target).argmax())
                ranked = torch.topk(probabilities, min(3, policy.action_size)).indices
                top1 += int(output["action"].item() == teacher)
                top3 += int((ranked == teacher).any().item())
                q_values = decision.q_estimates
                target = (
                    q_values[decision.action_id]
                    if any(abs(value) > 0.0 for value in q_values)
                    else terminal_score
                )
                targets.append(float(target))
                predictions.append(float(output["value"].item()) * scale + mean)
                decisions += 1
    if decisions == 0:
        return {
            "decisions": 0,
            "policyTop1Accuracy": 0.0,
            "policyTop3Accuracy": 0.0,
            "valueRankCorrelation": 0.0,
            "valueCalibrationError": 0.0,
        }, latencies
    return {
        "decisions": decisions,
        "policyTop1Accuracy": top1 / decisions,
        "policyTop3Accuracy": top3 / decisions,
        "valueRankCorrelation": _spearman(targets, predictions),
        "valueCalibrationError": statistics.mean(
            abs(target - prediction)
            for target, prediction in zip(targets, predictions)
        ),
    }, latencies


def _spearman(left, right):
    if len(left) != len(right) or not left:
        raise ValueError("Rank-correlation inputs are invalid")
    left_ranks = _ranks(left)
    right_ranks = _ranks(right)
    left_mean = statistics.mean(left_ranks)
    right_mean = statistics.mean(right_ranks)
    numerator = sum(
        (a - left_mean) * (b - right_mean)
        for a, b in zip(left_ranks, right_ranks)
    )
    left_scale = math.sqrt(sum((value - left_mean) ** 2 for value in left_ranks))
    right_scale = math.sqrt(sum((value - right_mean) ** 2 for value in right_ranks))
    if left_scale == 0.0 or right_scale == 0.0:
        return 0.0
    return numerator / (left_scale * right_scale)


def _ranks(values):
    ordered = sorted(range(len(values)), key=lambda index: (values[index], index))
    ranks = [0.0] * len(values)
    start = 0
    while start < len(ordered):
        end = start + 1
        while end < len(ordered) and values[ordered[end]] == values[ordered[start]]:
            end += 1
        rank = (start + end - 1) / 2.0
        for position in range(start, end):
            ranks[ordered[position]] = rank
        start = end
    return ranks


def _synchronize(device):
    if device.type == "cuda":
        torch.cuda.synchronize(device)


def atomic_write(path, payload):
    """Publish one strict JSON result atomically."""
    destination = Path(path)
    destination.parent.mkdir(parents=True, exist_ok=True)
    encoded = (
        json.dumps(payload, sort_keys=True, indent=2, allow_nan=False) + "\n"
    ).encode("utf-8")
    descriptor, temporary = tempfile.mkstemp(
        prefix=f".{destination.name}.", suffix=".tmp", dir=destination.parent
    )
    try:
        with os.fdopen(descriptor, "wb") as handle:
            handle.write(encoded)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary, destination)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset", required=True)
    parser.add_argument("--checkpoint", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--device", default="cpu")
    args = parser.parse_args()
    result = evaluate_checkpoint(args.dataset, args.checkpoint, args.device)
    atomic_write(args.output, result)
    print(json.dumps(result, sort_keys=True))


if __name__ == "__main__":
    main()
