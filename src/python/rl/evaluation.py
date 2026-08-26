from __future__ import annotations

import math
import statistics
from dataclasses import dataclass

import torch

from binary_protocol import (
    ACTION_LAYOUT_REVISION,
    CAPABILITY_SCHEMA_REVISION,
    LOADOUT_SCHEMA_REVISION,
    OBSERVATION_SCHEMA_REVISION,
    PRIVILEGED_SCHEMA_REVISION,
)

ROLE_FEATURES_PER_SLOT = 5
ROLE_ON_FIELD_SHARE = 0
ROLE_DAMAGE_SHARE = 1
ROLE_OFF_FIELD_SHARE = 2
ROLE_ENTRY_SHARE = 3
ROLE_STAY_SHARE = 4

GENERALIZATION_REPORT_SCHEMA_VERSION = 1
GENERALIZATION_REPORT_REVISION = "rotation-generalization-v1"
SEARCH_METHODS = (
    "deterministic_random",
    "unguided_search",
    "guided_search",
)
EVALUATION_METHODS = SEARCH_METHODS + ("model_only",)
DATASET_SPLITS = ("train", "validation", "holdout")


@dataclass(frozen=True)
class RotationEvaluationPreset:
    """Reproducible inputs for one rotation-generalization evaluation."""

    name: str
    seeds: tuple[int, ...]
    search_call_budget: int
    simulator_revision: str


class RotationGeneralizationError(ValueError):
    """Raised when a generalization report fails one or more closed gates."""

    def __init__(self, failures, report):
        self.failures = tuple(failures)
        self.report = report
        super().__init__("; ".join(self.failures))


def assert_policy_client_compatible(policy, client, context):
    mismatches = []
    if client.action_layout_revision != ACTION_LAYOUT_REVISION:
        mismatches.append(
            "action_layout_revision "
            f"policy={ACTION_LAYOUT_REVISION} service={client.action_layout_revision}"
        )
    expected_schemas = (
        OBSERVATION_SCHEMA_REVISION,
        PRIVILEGED_SCHEMA_REVISION,
        LOADOUT_SCHEMA_REVISION,
        CAPABILITY_SCHEMA_REVISION,
    )
    client_schemas = (
        client.observation_schema_revision,
        client.privileged_schema_revision,
        client.loadout_schema_revision,
        client.capability_schema_revision,
    )
    if client_schemas != expected_schemas:
        mismatches.append(
            f"schema_revisions policy={expected_schemas} service={client_schemas}"
        )
    if policy.observation_size != client.observation_size:
        mismatches.append(
            f"observation_size policy={policy.observation_size} service={client.observation_size}"
        )
    if policy.action_size != client.action_size:
        mismatches.append(
            f"action_size policy={policy.action_size} service={client.action_size}"
        )
    if policy.char_feature_size != client.char_feature_size:
        mismatches.append(
            f"char_feature_size policy={policy.char_feature_size} service={client.char_feature_size}"
        )
    if policy.global_feature_size != client.global_feature_size:
        mismatches.append(
            f"global_feature_size policy={policy.global_feature_size} service={client.global_feature_size}"
        )
    if policy.num_chars != client.num_chars:
        mismatches.append(
            f"num_chars policy={policy.num_chars} service={client.num_chars}"
        )
    if policy.privileged_observation_size != client.privileged_observation_size:
        mismatches.append(
            "privileged_observation_size "
            f"policy={policy.privileged_observation_size} service={client.privileged_observation_size}"
        )
    if mismatches:
        raise ValueError(f"{context} mismatch between policy and rollout service: " + ", ".join(mismatches))


def extract_role_feature(role_vector, num_chars, feature_index):
    if not role_vector:
        return [0.0 for _ in range(num_chars)]
    values = []
    for slot in range(num_chars):
        offset = slot * ROLE_FEATURES_PER_SLOT + feature_index
        values.append(role_vector[offset] if offset < len(role_vector) else 0.0)
    return values


def evaluate_policy(
    policy,
    client,
    device,
    deterministic,
    generate_report=False,
    include_action_trace=False,
    seed=None,
):
    if len(client.party_names) > 1:
        per_party = {}
        aggregate = {
            "aggregate_type": "macro_average",
            "reward": 0.0,
            "damage": 0.0,
            "steps": 0.0,
            "invalid_actions": 0.0,
            "mean_top_probability": 0.0,
            "action_fractions": [0.0 for _ in range(policy.action_size)],
            "mean_attention_scores": [0.0 for _ in range(policy.num_chars)],
            "role_alignment_score": 0.0,
            "carry_alignment_score": 0.0,
            "off_field_alignment_score": 0.0,
            "entry_alignment_score": 0.0,
            "stay_alignment_score": 0.0,
            "expected_on_field_shares": [0.0 for _ in range(policy.num_chars)],
            "realized_on_field_shares": [0.0 for _ in range(policy.num_chars)],
        }
        for party_id, party_name in enumerate(client.party_names):
            summary = evaluate_single_episode(
                policy,
                client,
                device,
                deterministic=deterministic,
                generate_report=generate_report,
                forced_party_id=party_id,
                include_action_trace=include_action_trace,
                seed=seed,
            )
            per_party[party_name] = summary
            aggregate["reward"] += summary["reward"]
            aggregate["damage"] += summary["damage"]
            aggregate["steps"] += summary["steps"]
            aggregate["invalid_actions"] += summary["invalid_actions"]
            aggregate["mean_top_probability"] += summary["mean_top_probability"]
            aggregate["role_alignment_score"] += summary.get("role_alignment_score", 0.0)
            aggregate["carry_alignment_score"] += summary.get("carry_alignment_score", 0.0)
            aggregate["off_field_alignment_score"] += summary.get("off_field_alignment_score", 0.0)
            aggregate["entry_alignment_score"] += summary.get("entry_alignment_score", 0.0)
            aggregate["stay_alignment_score"] += summary.get("stay_alignment_score", 0.0)
            for index, fraction in enumerate(summary["action_fractions"]):
                aggregate["action_fractions"][index] += fraction
            for index, score in enumerate(summary["mean_attention_scores"]):
                aggregate["mean_attention_scores"][index] += score
            for index, value in enumerate(summary.get("expected_on_field_shares", [])):
                aggregate["expected_on_field_shares"][index] += value
            for index, value in enumerate(summary.get("realized_on_field_shares", [])):
                aggregate["realized_on_field_shares"][index] += value
        party_count = len(client.party_names)
        aggregate["reward"] /= party_count
        aggregate["damage"] /= party_count
        aggregate["steps"] /= party_count
        aggregate["invalid_actions"] /= party_count
        aggregate["mean_top_probability"] /= party_count
        aggregate["role_alignment_score"] /= party_count
        aggregate["carry_alignment_score"] /= party_count
        aggregate["off_field_alignment_score"] /= party_count
        aggregate["entry_alignment_score"] /= party_count
        aggregate["stay_alignment_score"] /= party_count
        aggregate["action_fractions"] = [
            value / party_count for value in aggregate["action_fractions"]
        ]
        aggregate["mean_attention_scores"] = [
            value / party_count for value in aggregate["mean_attention_scores"]
        ]
        aggregate["expected_on_field_shares"] = [
            value / party_count for value in aggregate["expected_on_field_shares"]
        ]
        aggregate["realized_on_field_shares"] = [
            value / party_count for value in aggregate["realized_on_field_shares"]
        ]
        aggregate["per_party"] = per_party
        return aggregate
    return evaluate_single_episode(
        policy,
        client,
        device,
        deterministic=deterministic,
        generate_report=generate_report,
        forced_party_id=-1,
        include_action_trace=include_action_trace,
        seed=seed,
    )


def evaluate_single_episode(
    policy,
    client,
    device,
    deterministic,
    generate_report,
    forced_party_id,
    include_action_trace=False,
    seed=None,
):
    runner_id = client.create_runner(1)
    observations, _privileged_observations, masks, _party_ids = client.reset_runner(
        runner_id, generate_report, forced_party_id=forced_party_id
    )
    hidden = torch.zeros(
        1,
        policy.recurrent_state_size,
        dtype=torch.float32,
        device=device,
    )
    total_reward = 0.0
    invalid_actions = 0
    steps = 0
    damage = 0.0
    top_probability_sum = 0.0
    action_counts = [0 for _ in range(policy.action_size)]
    action_trace = []
    attention_score_sum = torch.zeros(policy.num_chars, dtype=torch.float64)
    resolved_party_id = max(0, forced_party_id)
    try:
        while True:
            obs_tensor = torch.tensor(observations, dtype=torch.float32, device=device)
            mask_tensor = torch.tensor(masks, dtype=torch.float32, device=device)
            with torch.no_grad():
                inference = policy.act(obs_tensor, hidden, mask_tensor, deterministic=deterministic)
            attention_score_sum += inference["attention_scores"].detach().cpu().sum(dim=0).to(torch.float64)
            action = int(inference["action"][0].item())
            if include_action_trace:
                action_trace.append(action)
            action_counts[action] += 1
            top_probability_sum += inference["top_probability"][0].item()
            batch = client.step_runner(runner_id, [action])
            total_reward += batch["rewards"][0]
            if not batch["valid_actions"][0]:
                invalid_actions += 1
            steps = batch["episode_steps"][0] if batch["dones"][0] else batch["live_steps"][0]
            damage = batch["episode_damages"][0] if batch["dones"][0] else batch["total_damages"][0]
            if batch["dones"][0]:
                episode_party_ids = batch.get("episode_party_ids")
                if episode_party_ids:
                    resolved_party_id = episode_party_ids[0]
                expected_roles = batch.get("episode_expected_role_vectors", [[]])[0]
                realized_roles = batch.get("episode_realized_role_vectors", [[]])[0]
                break
            observations = batch["observations"]
            masks = batch["action_masks"]
            hidden = inference["hidden"]
    finally:
        client.close_runner(runner_id)
    total_actions = max(1, sum(action_counts))
    summary = {
        "reward": total_reward,
        "damage": damage,
        "steps": steps,
        "invalid_actions": invalid_actions,
        "mean_top_probability": top_probability_sum / max(1, steps),
        "action_fractions": [count / total_actions for count in action_counts],
        "mean_attention_scores": (attention_score_sum / max(1, steps)).tolist(),
        "role_alignment_score": batch.get("episode_role_alignment_scores", [0.0])[0],
        "carry_alignment_score": batch.get("episode_carry_alignment_scores", [0.0])[0],
        "off_field_alignment_score": batch.get("episode_off_field_alignment_scores", [0.0])[0],
        "entry_alignment_score": batch.get("episode_entry_alignment_scores", [0.0])[0],
        "stay_alignment_score": batch.get("episode_stay_alignment_scores", [0.0])[0],
        "expected_role_vector": expected_roles,
        "realized_role_vector": realized_roles,
        "expected_on_field_shares": extract_role_feature(expected_roles, policy.num_chars, ROLE_ON_FIELD_SHARE),
        "realized_on_field_shares": extract_role_feature(realized_roles, policy.num_chars, ROLE_ON_FIELD_SHARE),
        "expected_damage_shares": extract_role_feature(expected_roles, policy.num_chars, ROLE_DAMAGE_SHARE),
        "realized_damage_shares": extract_role_feature(realized_roles, policy.num_chars, ROLE_DAMAGE_SHARE),
        "party_id": resolved_party_id,
        "party_name": client.party_names[resolved_party_id],
        "objective": {
            "reward": total_reward,
            "total_damage": damage,
            "episode_steps": steps,
            "invalid_action_count": invalid_actions,
        },
    }
    if seed is not None:
        summary["seed"] = seed
    if include_action_trace:
        summary["action_trace"] = action_trace
    return summary


def build_rotation_generalization_report(java_summary, model_summary, preset):
    """Build a complete fail-closed report without mutating either input."""
    failures = []
    java_payload = _require_mapping(java_summary, "Java benchmark", failures)
    java_payload = _normalize_java_benchmark(java_payload, failures)
    model_payload = (
        _require_mapping(model_summary, "model-only summary", failures)
        if model_summary is not None
        else {}
    )
    _validate_top_level_revision(java_payload, "Java benchmark", preset, failures)
    if model_summary is not None:
        _validate_top_level_revision(
            model_payload, "model-only summary", preset, failures
        )

    fingerprint_splits = _validate_fingerprint_splits(java_payload, failures)
    _validate_dataset_replay(java_payload, failures)
    unsupported = java_payload.get("unsupportedScenarios", [])
    if not isinstance(unsupported, list):
        failures.append("unsupportedScenarios must be a list")
    elif unsupported:
        failures.append(
            "unsupported scenarios are present: "
            + ", ".join(str(item) for item in unsupported)
        )

    runs = _merge_runs(java_payload, model_payload, failures)
    indexed_runs = _validate_runs(runs, fingerprint_splits, preset, failures)
    provenance = _merge_provenance(java_payload, model_payload, failures)
    dataset_replay = java_payload.get("datasetReplay")
    dataset_hash = (
        dataset_replay.get("datasetSourceHash")
        if isinstance(dataset_replay, dict)
        else None
    )
    _validate_provenance(
        provenance, fingerprint_splits, dataset_hash, failures
    )
    _validate_complete_coverage(indexed_runs, fingerprint_splits, preset, failures)
    _validate_equal_search_budgets(indexed_runs, preset, failures)
    _validate_archive_monotonicity(runs, failures)

    quality = _quality_metrics(indexed_runs, fingerprint_splits, preset, failures)
    report = {
        "schemaVersion": GENERALIZATION_REPORT_SCHEMA_VERSION,
        "reportRevision": GENERALIZATION_REPORT_REVISION,
        "preset": {
            "name": preset.name,
            "seeds": list(preset.seeds),
            "searchCallBudget": preset.search_call_budget,
        },
        "simulatorRevision": preset.simulator_revision,
        "qualityGatePassed": not failures,
        "failures": list(failures),
        "datasetReplay": java_payload.get("datasetReplay"),
        "fingerprintSplits": {
            split: sorted(fingerprint_splits[split]) for split in DATASET_SPLITS
        },
        "checkpointProvenance": provenance or None,
        "unsupportedScenarios": unsupported if isinstance(unsupported, list) else None,
        "metrics": quality,
        "runCount": len(runs),
    }
    return report


def validate_rotation_generalization(java_summary, model_summary, preset):
    """Return a valid report or raise with the complete failed-gate report."""
    report = build_rotation_generalization_report(
        java_summary, model_summary, preset
    )
    if not report["qualityGatePassed"]:
        raise RotationGeneralizationError(report["failures"], report)
    return report


def _validate_top_level_revision(payload, label, preset, failures):
    if payload.get("schemaVersion") != GENERALIZATION_REPORT_SCHEMA_VERSION:
        failures.append(f"{label} schemaVersion mismatch")
    revision = payload.get("simulatorRevision")
    if revision != preset.simulator_revision:
        failures.append(
            f"{label} simulator revision mismatch: "
            f"expected={preset.simulator_revision} actual={revision}"
        )


def _normalize_java_benchmark(payload, failures):
    """Translate the Java benchmark wire shape into the validator shape."""
    if "metrics" not in payload:
        return payload
    if payload.get("benchmarkRevision") != "rotation-search-benchmark-v1":
        failures.append("Java benchmark benchmarkRevision mismatch")
    raw_metrics = payload.get("metrics")
    if not isinstance(raw_metrics, list):
        failures.append("Java benchmark metrics must be a list")
        raw_metrics = []
    method_names = {
        "deterministic-random": "deterministic_random",
        "unguided-evolutionary": "unguided_search",
        "policy-guided": "guided_search",
        "model-only": "model_only",
    }
    runs = []
    fingerprint_splits = {split: set() for split in DATASET_SPLITS}
    for metric in raw_metrics:
        if not isinstance(metric, dict):
            runs.append(metric)
            continue
        split = metric.get("split")
        fingerprint = metric.get("scenarioFingerprint")
        if split in fingerprint_splits and isinstance(fingerprint, str):
            fingerprint_splits[split].add(fingerprint)
        actions = metric.get("bestFoundActions")
        run = {
            "method": method_names.get(metric.get("method"), metric.get("method")),
            "seed": metric.get("seed"),
            "split": split,
            "scenarioFingerprint": fingerprint,
            "horizonSeconds": metric.get("horizonSeconds"),
            "simulatorCalls": metric.get("simulatorCalls"),
            "wallTimeSeconds": (
                metric.get("wallTimeNanos") / 1_000_000_000.0
                if _is_integer(metric.get("wallTimeNanos"))
                else None
            ),
            "terminalDamage": metric.get("totalDamage"),
            "dps": metric.get("dps"),
            "cyclicEnergyDeficit": metric.get("energyDeficit"),
            "invalidActions": metric.get("invalidActionCount"),
            "invalidActionRate": metric.get("invalidActionRate"),
            "totalActions": len(actions) if isinstance(actions, list) else None,
            "objectiveScore": metric.get("objectiveScore"),
            "archiveDiversity": metric.get("archiveDiversity"),
            "archiveScores": metric.get("archiveScores"),
            "actionTrace": actions if metric.get("method") == "model-only" else None,
            "simulatorRevision": metric.get("simulatorRevision"),
            "datasetRevision": metric.get("datasetRevision"),
        }
        runs.append(run)
    replay = payload.get("datasetReplay")
    normalized_replay = None
    if isinstance(replay, dict):
        total = replay.get("totalRecords")
        replayed = replay.get("replayedRecords")
        normalized_replay = {
            "datasetSchemaVersion": replay.get("schemaVersion"),
            "datasetSourceHash": replay.get("sourceHash"),
            "expectedRecords": total,
            "replayedRecords": replayed,
            "failedRecords": (
                total - replayed
                if _is_integer(total) and _is_integer(replayed)
                else None
            ),
            "replayRate": replay.get("replayRate"),
        }
    normalized = {
        "schemaVersion": payload.get("schemaVersion"),
        "simulatorRevision": payload.get("simulatorRevision"),
        "datasetReplay": normalized_replay,
        "fingerprintSplits": {
            split: sorted(fingerprints)
            for split, fingerprints in fingerprint_splits.items()
        },
        "unsupportedScenarios": payload.get("unsupportedComparisons", []),
        "runs": runs,
    }
    if payload.get("checkpointProvenance") is not None:
        normalized["checkpointProvenance"] = payload["checkpointProvenance"]
    return normalized


def _validate_fingerprint_splits(payload, failures):
    raw_splits = payload.get("fingerprintSplits")
    result = {split: set() for split in DATASET_SPLITS}
    if not isinstance(raw_splits, dict):
        failures.append("fingerprintSplits must be an object")
        return result
    for split in DATASET_SPLITS:
        values = raw_splits.get(split)
        if not isinstance(values, list) or not values:
            failures.append(f"fingerprintSplits.{split} must be a non-empty list")
            continue
        for value in values:
            if not isinstance(value, str) or not value.strip():
                failures.append(f"fingerprintSplits.{split} contains a blank value")
            else:
                result[split].add(value)
        if len(result[split]) != len(values):
            failures.append(f"fingerprintSplits.{split} contains duplicates")
    for left_index, left in enumerate(DATASET_SPLITS):
        for right in DATASET_SPLITS[left_index + 1 :]:
            overlap = result[left] & result[right]
            if overlap:
                failures.append(
                    f"fingerprint leak between {left} and {right}: {sorted(overlap)}"
                )
    return result


def _validate_dataset_replay(payload, failures):
    replay = payload.get("datasetReplay")
    if not isinstance(replay, dict):
        failures.append("datasetReplay must be an object")
        return
    expected = replay.get("expectedRecords")
    replayed = replay.get("replayedRecords")
    failed = replay.get("failedRecords")
    rate = replay.get("replayRate")
    dataset_schema = replay.get("datasetSchemaVersion")
    dataset_hash = replay.get("datasetSourceHash")
    if not _is_integer(expected) or expected <= 0:
        failures.append("datasetReplay.expectedRecords must be positive")
    if not _is_integer(replayed) or replayed < 0:
        failures.append("datasetReplay.replayedRecords must be non-negative")
    if not _is_integer(failed) or failed < 0:
        failures.append("datasetReplay.failedRecords must be non-negative")
    if not _is_finite_number(rate):
        failures.append("datasetReplay.replayRate must be finite")
    if dataset_schema != 1:
        failures.append("datasetReplay.datasetSchemaVersion mismatch")
    if not _is_sha256(dataset_hash):
        failures.append("datasetReplay.datasetSourceHash must be a SHA-256 digest")
    if (
        _is_integer(expected)
        and _is_integer(replayed)
        and _is_integer(failed)
        and (replayed != expected or failed != 0)
    ):
        failures.append(
            f"dataset replay is partial: expected={expected} "
            f"replayed={replayed} failed={failed}"
        )
    if _is_finite_number(rate) and float(rate) != 1.0:
        failures.append(f"dataset replay rate must be 1.0, got {rate}")


def _merge_runs(java_payload, model_payload, failures):
    java_runs = java_payload.get("runs")
    if not isinstance(java_runs, list):
        failures.append("Java benchmark runs must be a list")
        java_runs = []
    model_runs = model_payload.get("runs", [])
    if not isinstance(model_runs, list):
        failures.append("model-only summary runs must be a list")
        model_runs = []
    return list(java_runs) + list(model_runs)


def _validate_runs(runs, fingerprint_splits, preset, failures):
    fingerprint_to_split = {
        fingerprint: split
        for split, fingerprints in fingerprint_splits.items()
        for fingerprint in fingerprints
    }
    indexed = {}
    horizons = {}
    for index, run in enumerate(runs):
        label = f"runs[{index}]"
        if not isinstance(run, dict):
            failures.append(f"{label} must be an object")
            continue
        method = run.get("method")
        fingerprint = run.get("scenarioFingerprint")
        split = run.get("split")
        seed = run.get("seed")
        if method not in EVALUATION_METHODS:
            failures.append(f"{label} has unsupported method: {method}")
            continue
        if not isinstance(fingerprint, str) or fingerprint not in fingerprint_to_split:
            failures.append(f"{label} has unsupported scenario: {fingerprint}")
            continue
        if split != fingerprint_to_split[fingerprint]:
            failures.append(
                f"{label} split mismatch for {fingerprint}: {split}"
            )
        if not _is_integer(seed):
            failures.append(f"{label} seed is missing or not an integer")
            continue
        if seed not in preset.seeds:
            failures.append(f"{label} uses a non-preset seed: {seed}")
        if run.get("simulatorRevision") != preset.simulator_revision:
            failures.append(
                f"{label} simulator revision mismatch: "
                f"expected={preset.simulator_revision} "
                f"actual={run.get('simulatorRevision')}"
            )
        key = (method, fingerprint, seed)
        if key in indexed:
            failures.append(f"duplicate benchmark run: {key}")
        indexed[key] = run

        for field in (
            "horizonSeconds",
            "objectiveScore",
            "terminalDamage",
            "dps",
            "cyclicEnergyDeficit",
            "invalidActionRate",
            "wallTimeSeconds",
        ):
            if not _is_finite_number(run.get(field)):
                failures.append(f"{label}.{field} must be finite")
        horizon = run.get("horizonSeconds")
        if _is_finite_number(horizon) and horizon <= 0.0:
            failures.append(f"{label}.horizonSeconds must be positive")
        previous_horizon = horizons.setdefault(fingerprint, horizon)
        if (
            _is_finite_number(horizon)
            and _is_finite_number(previous_horizon)
            and float(horizon) != float(previous_horizon)
        ):
            failures.append(f"horizon mismatch for scenario {fingerprint}")
        calls = run.get("simulatorCalls")
        if not _is_integer(calls) or calls <= 0:
            failures.append(f"{label}.simulatorCalls must be positive")
        invalid = run.get("invalidActions")
        total_actions = run.get("totalActions")
        if not _is_integer(invalid) or invalid != 0:
            failures.append(f"{label}.invalidActions must be zero")
        if not _is_integer(total_actions) or total_actions <= 0:
            failures.append(f"{label}.totalActions must be positive")
        if _is_finite_number(run.get("invalidActionRate")) and float(
            run["invalidActionRate"]
        ) != 0.0:
            failures.append(f"{label}.invalidActionRate must be zero")
        if method == "model_only":
            trace = run.get("actionTrace")
            if not isinstance(trace, list) or not trace:
                failures.append(f"{label}.actionTrace must be non-empty")
            elif _is_integer(total_actions) and len(trace) != total_actions:
                failures.append(f"{label}.actionTrace length mismatch")
            elif any(not _is_integer(action) or action < 0 for action in trace):
                failures.append(f"{label}.actionTrace contains an invalid action ID")
        elif not _is_finite_number(run.get("archiveDiversity")):
            failures.append(f"{label}.archiveDiversity must be finite")
    return indexed


def _merge_provenance(java_payload, model_payload, failures):
    candidates = []
    for label, payload in (
        ("Java benchmark", java_payload),
        ("model-only summary", model_payload),
    ):
        if "checkpointProvenance" in payload:
            value = payload["checkpointProvenance"]
            if not isinstance(value, dict):
                failures.append(f"{label} checkpointProvenance must be an object")
            else:
                candidates.append(value)
    if not candidates:
        failures.append("checkpoint provenance is missing")
        return {}
    first = candidates[0]
    if any(candidate != first for candidate in candidates[1:]):
        failures.append("checkpoint provenance differs between inputs")
    return dict(first)


def _validate_provenance(
    provenance, fingerprint_splits, expected_dataset_hash, failures
):
    if not provenance:
        return
    train = fingerprint_splits["train"]
    holdout = fingerprint_splits["holdout"]
    checkpoint_revision = provenance.get("checkpointRevision")
    if not isinstance(checkpoint_revision, str) or not checkpoint_revision.strip():
        failures.append("checkpointProvenance.checkpointRevision must not be blank")
    dataset_hash = provenance.get("datasetSourceHash")
    if not _is_sha256(dataset_hash):
        failures.append(
            "checkpointProvenance.datasetSourceHash must be a SHA-256 digest"
        )
    elif dataset_hash != expected_dataset_hash:
        failures.append(
            "checkpoint and replay dataset source hashes do not match"
        )
    for field in ("trainingFingerprints", "normalizationFingerprints"):
        values = provenance.get(field)
        if not isinstance(values, list) or not values:
            failures.append(f"checkpointProvenance.{field} must be non-empty")
            continue
        if any(not isinstance(value, str) or not value for value in values):
            failures.append(f"checkpointProvenance.{field} contains a blank value")
            continue
        value_set = set(values)
        if len(value_set) != len(values):
            failures.append(f"checkpointProvenance.{field} contains duplicates")
        leaked = value_set & holdout
        if leaked:
            failures.append(f"holdout fingerprint leaked into {field}: {sorted(leaked)}")
        outside_train = value_set - train
        if outside_train:
            failures.append(
                f"checkpointProvenance.{field} is not train-only: "
                f"{sorted(outside_train)}"
            )


def _validate_complete_coverage(indexed, fingerprint_splits, preset, failures):
    for split in DATASET_SPLITS:
        for fingerprint in fingerprint_splits[split]:
            for seed in preset.seeds:
                for method in EVALUATION_METHODS:
                    if (method, fingerprint, seed) not in indexed:
                        failures.append(
                            f"missing {method} run for {split} "
                            f"scenario={fingerprint} seed={seed}"
                        )


def _validate_equal_search_budgets(indexed, preset, failures):
    scenario_seeds = {
        (fingerprint, seed)
        for method, fingerprint, seed in indexed
        if method in SEARCH_METHODS
    }
    for fingerprint, seed in sorted(scenario_seeds):
        runs = [indexed.get((method, fingerprint, seed)) for method in SEARCH_METHODS]
        if any(run is None for run in runs):
            continue
        calls = [run.get("simulatorCalls") for run in runs]
        if any(not _is_integer(value) for value in calls):
            continue
        if len(set(calls)) != 1:
            failures.append(
                f"unequal search call budget for scenario={fingerprint} "
                f"seed={seed}: {calls}"
            )
        elif calls[0] != preset.search_call_budget:
            failures.append(
                f"search call budget mismatch for scenario={fingerprint} "
                f"seed={seed}: expected={preset.search_call_budget} "
                f"actual={calls[0]}"
            )


def _validate_archive_monotonicity(runs, failures):
    for index, run in enumerate(runs):
        if not isinstance(run, dict) or run.get("method") not in SEARCH_METHODS:
            continue
        scores = run.get("archiveScores")
        if not isinstance(scores, list) or not scores:
            failures.append(f"runs[{index}].archiveScores must be non-empty")
            continue
        if any(not _is_finite_number(score) for score in scores):
            failures.append(f"runs[{index}].archiveScores must be finite")
            continue
        if any(float(current) < float(previous) for previous, current in zip(scores, scores[1:])):
            failures.append(f"runs[{index}] archive regressed")


def _quality_metrics(indexed, fingerprint_splits, preset, failures):
    holdout_keys = [
        (fingerprint, seed)
        for fingerprint in sorted(fingerprint_splits["holdout"])
        for seed in preset.seeds
    ]
    scores = _scores_for_keys(indexed, holdout_keys)
    medians = {
        method: statistics.median(values) if values else None
        for method, values in scores.items()
    }
    if medians["model_only"] is None or medians["deterministic_random"] is None:
        failures.append("model-only and deterministic-random holdout medians are required")
    elif medians["model_only"] <= medians["deterministic_random"]:
        failures.append(
            "model-only holdout median does not exceed deterministic random: "
            f"model={medians['model_only']} random={medians['deterministic_random']}"
        )
    if medians["guided_search"] is None or medians["unguided_search"] is None:
        failures.append("guided and unguided holdout medians are required")
    elif medians["guided_search"] < medians["unguided_search"]:
        failures.append(
            "guided-search holdout median is below unguided search: "
            f"guided={medians['guided_search']} unguided={medians['unguided_search']}"
        )
    return {
        "holdoutMedianObjective": medians,
        "holdoutSampleCount": {
            method: len(values) for method, values in scores.items()
        },
        "objectiveBySplit": {
            split: {
                method: _distribution(values)
                for method, values in _scores_for_keys(
                    indexed,
                    [
                        (fingerprint, seed)
                        for fingerprint in sorted(fingerprint_splits[split])
                        for seed in preset.seeds
                    ],
                ).items()
            }
            for split in DATASET_SPLITS
        },
    }


def _scores_for_keys(indexed, scenario_seed_keys):
    scores = {}
    for method in EVALUATION_METHODS:
        values = []
        for fingerprint, seed in scenario_seed_keys:
            run = indexed.get((method, fingerprint, seed))
            value = run.get("objectiveScore") if run else None
            if _is_finite_number(value):
                values.append(float(value))
        scores[method] = values
    return scores


def _distribution(values):
    if not values:
        return None
    return {
        "count": len(values),
        "mean": statistics.fmean(values),
        "median": statistics.median(values),
        "populationStdDev": statistics.pstdev(values),
        "minimum": min(values),
        "maximum": max(values),
    }


def _require_mapping(value, label, failures):
    if isinstance(value, dict):
        return value
    failures.append(f"{label} must be a JSON object")
    return {}


def _is_integer(value):
    return isinstance(value, int) and not isinstance(value, bool)


def _is_finite_number(value):
    return (
        isinstance(value, (int, float))
        and not isinstance(value, bool)
        and math.isfinite(value)
    )


def _is_sha256(value):
    if not isinstance(value, str) or len(value) != 64:
        return False
    return all(character in "0123456789abcdef" for character in value)
