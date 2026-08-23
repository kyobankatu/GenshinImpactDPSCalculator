"""Policy-guided expert iteration and transactional DAgger recovery support."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
import tempfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import torch

from expert_dataset import ExpertDataset, ExpertRecord, load_expert_dataset
from recurrent_ppo import load_policy


RECOVERY_SCHEMA_VERSION = 1


def uniform_legal_prior(action_mask) -> tuple[float, ...]:
    """Return normalized uniform mass over legal actions only."""
    legal = [index for index, value in enumerate(action_mask) if value > 0.5]
    if not legal:
        raise ValueError("Cannot build a prior without a legal action")
    probability = 1.0 / len(legal)
    return tuple(probability if index in legal else 0.0 for index in range(len(action_mask)))


class PolicyPriorProvider:
    """Checkpoint-backed legal prior with explicit uniform failure fallback."""

    def __init__(self, checkpoint_path, dataset_source_hash, allow_fallback=True):
        self.allow_fallback = allow_fallback
        self.policy = None
        self.hidden_size = None
        self.failure = None
        try:
            payload = torch.load(checkpoint_path, map_location="cpu")
            checkpoint_dataset_hash = payload.get(
                "dataset_source_hash", payload.get("expert_dataset_hash")
            )
            if checkpoint_dataset_hash != dataset_source_hash:
                raise ValueError("Policy prior dataset provenance is stale")
            self.policy, _ = load_policy(checkpoint_path)
            self.policy.eval()
            self.hidden_size = self.policy.hidden_size
        except (OSError, RuntimeError, ValueError) as error:
            self.failure = error
            if not allow_fallback:
                raise

    def weights(self, observation, action_mask, recurrent_state=None):
        """Return legal policy probabilities or a uniform legal fallback."""
        fallback = uniform_legal_prior(action_mask)
        if self.policy is None:
            return fallback
        try:
            hidden = recurrent_state
            if hidden is None:
                hidden = torch.zeros(1, self.hidden_size)
            with torch.no_grad():
                output = self.policy.act(
                    torch.tensor([observation], dtype=torch.float32),
                    hidden,
                    torch.tensor([action_mask], dtype=torch.float32),
                    deterministic=False,
                )
            probabilities = output["probabilities"][0].tolist()
            if any(not math.isfinite(value) or value < 0.0 for value in probabilities):
                raise ValueError("Policy prior returned invalid probabilities")
            return tuple(probabilities)
        except (RuntimeError, ValueError) as error:
            self.failure = error
            if not self.allow_fallback:
                raise
            return fallback


class MonotonicExpertArchive:
    """Scenario-local best records that cannot regress across generations."""

    def __init__(self):
        self._best: dict[str, ExpertRecord] = {}

    def merge(self, records) -> int:
        improvements = 0
        for record in records:
            previous = self._best.get(record.scenario_fingerprint)
            score = float(record.terminal_objective["objectiveScore"])
            previous_score = (
                float(previous.terminal_objective["objectiveScore"])
                if previous is not None
                else float("-inf")
            )
            if score > previous_score or (
                score == previous_score
                and previous is not None
                and record.record_hash < previous.record_hash
            ):
                self._best[record.scenario_fingerprint] = record
                improvements += 1
        return improvements

    def best(self, scenario_fingerprint) -> ExpertRecord:
        if scenario_fingerprint not in self._best:
            raise KeyError(f"No expert archive entry for {scenario_fingerprint}")
        return self._best[scenario_fingerprint]

    def scores(self) -> dict[str, float]:
        return {
            fingerprint: float(record.terminal_objective["objectiveScore"])
            for fingerprint, record in sorted(self._best.items())
        }


@dataclass(frozen=True)
class RecoveryCandidate:
    """One low-confidence or teacher-disagreement expert query candidate."""

    record_id: str
    scenario_fingerprint: str
    state_hash: int
    legal_action_mask: tuple[float, ...]
    policy_probabilities: tuple[float, ...]
    teacher_action: int


def select_recovery_candidates(
    policy,
    dataset: ExpertDataset,
    confidence_threshold: float,
) -> tuple[RecoveryCandidate, ...]:
    """Select model states requiring all-legal-action recovery labels."""
    if not 0.0 <= confidence_threshold <= 1.0:
        raise ValueError("confidence_threshold must be within [0, 1]")
    policy.eval()
    candidates = []
    with torch.no_grad():
        for record in dataset.records:
            hidden = torch.zeros(1, policy.hidden_size)
            for decision in record.decisions:
                result = policy.act(
                    torch.tensor([decision.observation], dtype=torch.float32),
                    hidden,
                    torch.tensor([decision.legal_action_mask], dtype=torch.float32),
                    deterministic=True,
                )
                probabilities = result["probabilities"][0]
                predicted = int(result["action"].item())
                confidence = float(probabilities.max())
                if predicted != decision.action_id or confidence < confidence_threshold:
                    candidates.append(
                        RecoveryCandidate(
                            record_id=record.record_id,
                            scenario_fingerprint=record.scenario_fingerprint,
                            state_hash=decision.state_hash,
                            legal_action_mask=decision.legal_action_mask,
                            policy_probabilities=tuple(probabilities.tolist()),
                            teacher_action=decision.action_id,
                        )
                    )
                hidden = result["hidden"]
    return tuple(candidates)


def export_recorded_policy_prior(
    dataset: ExpertDataset,
    checkpoint_path: str | Path,
    output_path: str | Path,
) -> str:
    """Atomically export model probabilities for replayable dataset states."""
    provider = PolicyPriorProvider(checkpoint_path, dataset.source_hash, False)
    entries = []
    for record in dataset.records:
        hidden = torch.zeros(1, provider.hidden_size)
        for decision in record.decisions:
            weights = provider.weights(
                decision.observation,
                decision.legal_action_mask,
                hidden,
            )
            entries.append(
                {
                    "scenarioFingerprint": record.scenario_fingerprint,
                    "stateHash": str(decision.state_hash),
                    "weights": list(weights),
                }
            )
            with torch.no_grad():
                hidden = provider.policy.act(
                    torch.tensor([decision.observation], dtype=torch.float32),
                    hidden,
                    torch.tensor([decision.legal_action_mask], dtype=torch.float32),
                )["hidden"]
    payload = {
        "schemaVersion": 1,
        "actionLayoutRevision": 2,
        "observationSchemaRevision": 2,
        "datasetSourceHash": dataset.source_hash,
        "entries": entries,
    }
    encoded = (json.dumps(payload, separators=(",", ":")) + "\n").encode()
    destination = Path(output_path)
    destination.parent.mkdir(parents=True, exist_ok=True)
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
    return hashlib.sha256(encoded).hexdigest()


def query_all_legal_actions(
    client,
    runner_id,
    snapshot_id,
    action_mask,
    branches,
    horizon,
    gamma,
) -> tuple[float, ...]:
    """Query one bounded branch handle and validate every legal Q estimate."""
    if branches <= 0 or horizon <= 0 or not 0.0 < gamma <= 1.0:
        raise ValueError("Invalid recovery branch configuration")
    values = client.branch_rollout_multi(
        runner_id, snapshot_id, branches, horizon, gamma
    )
    if len(values) != len(action_mask):
        raise ValueError("Recovery Q response action dimension mismatch")
    for action, legal in enumerate(action_mask):
        value = values[action]
        if legal > 0.5 and not math.isfinite(value):
            raise ValueError(f"Recovery Q response omitted legal action {action}")
        if legal <= 0.5 and math.isfinite(value):
            raise ValueError(f"Recovery Q response labeled masked action {action}")
    return tuple(values)


class RecoveryLabelStore:
    """Atomic sidecar store for model-recovery all-action labels."""

    @staticmethod
    def append(path: str | Path, labels: list[dict[str, Any]]) -> str:
        destination = Path(path)
        existing = RecoveryLabelStore.read(destination) if destination.exists() else []
        merged = {RecoveryLabelStore._key(label): label for label in existing}
        for label in labels:
            RecoveryLabelStore._validate(label)
            merged[RecoveryLabelStore._key(label)] = label
        payload = {
            "schemaVersion": RECOVERY_SCHEMA_VERSION,
            "labels": [merged[key] for key in sorted(merged)],
        }
        encoded = (json.dumps(payload, sort_keys=True, separators=(",", ":")) + "\n").encode()
        destination.parent.mkdir(parents=True, exist_ok=True)
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
        return hashlib.sha256(encoded).hexdigest()

    @staticmethod
    def read(path: str | Path) -> list[dict[str, Any]]:
        source = Path(path)
        try:
            payload = json.loads(source.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as error:
            raise ValueError(f"Corrupt recovery label store: {source}") from error
        if payload.get("schemaVersion") != RECOVERY_SCHEMA_VERSION:
            raise ValueError("Recovery label schema mismatch")
        labels = payload.get("labels")
        if not isinstance(labels, list):
            raise ValueError("Recovery label store has no labels")
        for label in labels:
            RecoveryLabelStore._validate(label)
        return labels

    @staticmethod
    def _key(label: dict[str, Any]) -> str:
        RecoveryLabelStore._validate(label)
        return f"{label['scenarioFingerprint']}:{label['stateHash']}"

    @staticmethod
    def _validate(label: dict[str, Any]) -> None:
        required = {"scenarioFingerprint", "stateHash", "legalActionMask", "qEstimates"}
        if not isinstance(label, dict) or set(label) != required:
            raise ValueError("Malformed recovery label")
        if not label["scenarioFingerprint"] or not isinstance(label["stateHash"], int):
            raise ValueError("Invalid recovery label identity")
        mask = label["legalActionMask"]
        values = label["qEstimates"]
        if not isinstance(mask, list) or not isinstance(values, list) or len(mask) != len(values):
            raise ValueError("Recovery label action dimension mismatch")
        for legal, value in zip(mask, values):
            if legal > 0.5:
                if not isinstance(value, (int, float)) or not math.isfinite(value):
                    raise ValueError("Recovery label omitted a legal Q value")
            elif value is not None:
                raise ValueError("Recovery label contains a masked Q value")


def run_offline_iteration(dataset_path, checkpoint_path, confidence_threshold):
    """Run the deterministic data-model-recovery selection portion of one generation."""
    dataset = load_expert_dataset(dataset_path)
    provider = PolicyPriorProvider(checkpoint_path, dataset.source_hash, False)
    candidates = select_recovery_candidates(
        provider.policy, dataset, confidence_threshold
    )
    archive = MonotonicExpertArchive()
    archive.merge(dataset.records)
    return {
        "datasetHash": dataset.source_hash,
        "records": len(dataset.records),
        "recoveryCandidates": len(candidates),
        "archiveScores": archive.scores(),
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset", required=True)
    parser.add_argument("--checkpoint", required=True)
    parser.add_argument("--confidence-threshold", type=float, default=0.7)
    parser.add_argument("--output", default="output/expert_iteration/summary.json")
    parser.add_argument(
        "--prior-output", default="output/expert_iteration/policy_prior.json"
    )
    args = parser.parse_args()
    summary = run_offline_iteration(
        args.dataset, args.checkpoint, args.confidence_threshold
    )
    dataset = load_expert_dataset(args.dataset)
    summary["policyPriorHash"] = export_recorded_policy_prior(
        dataset, args.checkpoint, args.prior_output
    )
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(summary, sort_keys=True))


if __name__ == "__main__":
    main()
