"""Versioned policy-value advisor framing and legality validation."""

from __future__ import annotations

import json
import math
import re
import struct
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable

from binary_protocol import ACTION_LAYOUT_REVISION, OBSERVATION_SCHEMA_REVISION
from expert_dataset import SCHEMA_VERSION as DATASET_SCHEMA_VERSION
from expert_dataset import SIMULATOR_REVISION
from recurrent_ppo import ARCHITECTURE_REVISION


POLICY_VALUE_SCHEMA_VERSION = 1
ACTION_SIZE = 11
OBSERVATION_SIZE = 287
MAX_FRAME_BYTES = 16 * 1024 * 1024
MASKED_MASS_TOLERANCE = 1.0e-12
DIAGNOSTICS = frozenset(("none", "unavailable", "timeout", "invalid-response"))


@dataclass(frozen=True)
class PolicyValueContract:
    """Immutable simulator, dataset, model, and checkpoint compatibility tuple."""

    simulator_revision: str
    dataset_schema_version: int
    dataset_source_hash: str
    action_layout_revision: int
    observation_schema_revision: int
    model_revision: int
    checkpoint_fingerprint: str

    def validate(self) -> None:
        """Reject any stale revision or malformed content fingerprint."""
        if (
            self.simulator_revision != SIMULATOR_REVISION
            or self.dataset_schema_version != DATASET_SCHEMA_VERSION
            or self.action_layout_revision != ACTION_LAYOUT_REVISION
            or self.observation_schema_revision != OBSERVATION_SCHEMA_REVISION
            or self.model_revision != ARCHITECTURE_REVISION
            or not _is_hash(self.dataset_source_hash)
            or not _is_hash(self.checkpoint_fingerprint)
        ):
            raise ValueError("Policy-value contract revision mismatch")

    def to_wire(self) -> dict[str, Any]:
        """Return the camel-case representation shared with Java."""
        self.validate()
        return {
            "simulatorRevision": self.simulator_revision,
            "datasetSchemaVersion": self.dataset_schema_version,
            "datasetSourceHash": self.dataset_source_hash,
            "actionLayoutRevision": self.action_layout_revision,
            "observationSchemaRevision": self.observation_schema_revision,
            "modelRevision": self.model_revision,
            "checkpointFingerprint": self.checkpoint_fingerprint,
        }

    @staticmethod
    def from_wire(payload: Any) -> "PolicyValueContract":
        """Parse and validate an exact wire contract."""
        required = {
            "simulatorRevision",
            "datasetSchemaVersion",
            "datasetSourceHash",
            "actionLayoutRevision",
            "observationSchemaRevision",
            "modelRevision",
            "checkpointFingerprint",
        }
        if not isinstance(payload, dict) or set(payload) != required:
            raise ValueError("Malformed policy-value contract")
        contract = PolicyValueContract(
            simulator_revision=payload["simulatorRevision"],
            dataset_schema_version=payload["datasetSchemaVersion"],
            dataset_source_hash=payload["datasetSourceHash"],
            action_layout_revision=payload["actionLayoutRevision"],
            observation_schema_revision=payload["observationSchemaRevision"],
            model_revision=payload["modelRevision"],
            checkpoint_fingerprint=payload["checkpointFingerprint"],
        )
        contract.validate()
        return contract


@dataclass(frozen=True)
class PolicyValueQuery:
    """One ordered advisor input with caller-owned recurrent state."""

    request_id: int
    state_hash: int
    observation: tuple[float, ...]
    legal_action_mask: tuple[float, ...]
    recurrent_state: tuple[float, ...] = ()


@dataclass(frozen=True)
class PolicyValueEstimate:
    """One normalized response whose value cannot represent terminal authority."""

    request_id: int
    policy_prior: tuple[float, ...]
    value_estimate: float | None
    recurrent_state: tuple[float, ...]
    diagnostic: str = "none"


def validate_query(query: PolicyValueQuery) -> None:
    """Validate dimensions, finite state, and at least one legal action."""
    if not isinstance(query.request_id, int) or query.request_id < 0:
        raise ValueError("Invalid policy-value request ID")
    if not isinstance(query.state_hash, int):
        raise ValueError("Invalid policy-value state hash")
    _finite_vector(query.observation, OBSERVATION_SIZE, "observation")
    mask = _finite_vector(query.legal_action_mask, ACTION_SIZE, "legal action mask")
    if not any(value > 0.5 for value in mask):
        raise ValueError("Policy-value query has no legal action")
    _finite_vector(query.recurrent_state, None, "recurrent state")


def normalize_policy_value_estimate(
    request_id: int,
    policy_prior: Iterable[float],
    value_estimate: float | None,
    recurrent_state: Iterable[float],
    legal_action_mask: Iterable[float],
    diagnostic: str = "none",
) -> PolicyValueEstimate:
    """Mask numerical dust, reject illegal mass, and normalize legal policy mass."""
    raw = _finite_vector(tuple(policy_prior), ACTION_SIZE, "policy prior")
    mask = _finite_vector(tuple(legal_action_mask), ACTION_SIZE, "legal action mask")
    if diagnostic not in DIAGNOSTICS:
        raise ValueError("Unknown policy-value diagnostic")
    normalized = list(raw)
    total = 0.0
    for action, weight in enumerate(normalized):
        if weight < 0.0:
            raise ValueError("Policy-value prior contains negative weight")
        if mask[action] <= 0.5:
            if weight > MASKED_MASS_TOLERANCE:
                raise ValueError("Policy-value prior assigns masked probability")
            normalized[action] = 0.0
        else:
            total += weight
    if not math.isfinite(total) or total <= 0.0:
        raise ValueError("Policy-value prior has no legal mass")
    normalized = [weight / total for weight in normalized]
    if value_estimate is not None and (
        isinstance(value_estimate, bool)
        or not isinstance(value_estimate, (int, float))
        or not math.isfinite(value_estimate)
    ):
        raise ValueError("Policy-value estimate is not finite")
    recurrent = _finite_vector(tuple(recurrent_state), None, "recurrent state")
    return PolicyValueEstimate(
        request_id=request_id,
        policy_prior=tuple(normalized),
        value_estimate=None if value_estimate is None else float(value_estimate),
        recurrent_state=recurrent,
        diagnostic=diagnostic,
    )


def uniform_fallback(query: PolicyValueQuery, diagnostic: str) -> PolicyValueEstimate:
    """Return deterministic legal-uniform guidance without advancing recurrent state."""
    if diagnostic == "none":
        raise ValueError("Fallback requires a failure diagnostic")
    return normalize_policy_value_estimate(
        query.request_id,
        tuple(1.0 if value > 0.5 else 0.0 for value in query.legal_action_mask),
        None,
        query.recurrent_state,
        query.legal_action_mask,
        diagnostic,
    )


def encode_request_batch(
    contract: PolicyValueContract, queries: Iterable[PolicyValueQuery]
) -> bytes:
    """Encode one length-prefixed local binary request frame."""
    items = tuple(queries)
    if not items:
        raise ValueError("Policy-value request batch is empty")
    for query in items:
        validate_query(query)
    return _encode_frame(
        {
            "schemaVersion": POLICY_VALUE_SCHEMA_VERSION,
            "kind": "request",
            "contract": contract.to_wire(),
            "items": [_query_to_wire(query) for query in items],
        }
    )


def decode_request_batch(
    frame: bytes, expected_contract: PolicyValueContract
) -> tuple[PolicyValueQuery, ...]:
    """Decode a complete request frame and reject stale contracts."""
    payload = _decode_frame(frame, "request", expected_contract)
    items = payload.get("items")
    if not isinstance(items, list) or not items:
        raise ValueError("Policy-value request batch is truncated")
    queries = tuple(_query_from_wire(item) for item in items)
    if len({query.request_id for query in queries}) != len(queries):
        raise ValueError("Policy-value request IDs are duplicated")
    return queries


def encode_response_batch(
    contract: PolicyValueContract, estimates: Iterable[PolicyValueEstimate]
) -> bytes:
    """Encode one length-prefixed local binary response frame."""
    items = tuple(estimates)
    if not items:
        raise ValueError("Policy-value response batch is empty")
    return _encode_frame(
        {
            "schemaVersion": POLICY_VALUE_SCHEMA_VERSION,
            "kind": "response",
            "contract": contract.to_wire(),
            "items": [_estimate_to_wire(estimate) for estimate in items],
        }
    )


def decode_response_batch(
    frame: bytes,
    expected_contract: PolicyValueContract,
    queries: Iterable[PolicyValueQuery],
) -> tuple[PolicyValueEstimate, ...]:
    """Decode, normalize, and enforce exact response batch order."""
    expected_queries = tuple(queries)
    payload = _decode_frame(frame, "response", expected_contract)
    items = payload.get("items")
    if not isinstance(items, list) or len(items) != len(expected_queries):
        raise ValueError("Policy-value response batch is truncated")
    estimates = []
    for query, item in zip(expected_queries, items):
        if not isinstance(item, dict) or item.get("requestId") != query.request_id:
            raise ValueError("Policy-value response order mismatch")
        estimates.append(
            normalize_policy_value_estimate(
                item["requestId"],
                item.get("policyPrior", ()),
                item.get("valueEstimate"),
                item.get("recurrentState", ()),
                query.legal_action_mask,
                item.get("diagnostic", "none"),
            )
        )
    return tuple(estimates)


def load_recorded_fixture(
    path: str | Path,
    expected_contract: PolicyValueContract,
    scenario_fingerprint: str,
) -> dict[int, dict[str, Any]]:
    """Load the plain JSON artifact consumed by RecordedPolicyValueAdvisor."""
    try:
        payload = json.loads(Path(path).read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise ValueError("Malformed recorded policy-value advisor") from error
    if payload.get("schemaVersion") != POLICY_VALUE_SCHEMA_VERSION:
        raise ValueError("Recorded policy-value schema mismatch")
    actual = PolicyValueContract.from_wire(payload.get("contract"))
    if actual != expected_contract:
        raise ValueError("Recorded policy-value contract is stale")
    entries = payload.get("entries")
    if not isinstance(entries, list):
        raise ValueError("Recorded policy-value entries are missing")
    selected: dict[int, dict[str, Any]] = {}
    for entry in entries:
        if not isinstance(entry, dict) or entry.get("scenarioFingerprint") != scenario_fingerprint:
            continue
        try:
            state_hash = int(entry["stateHash"])
        except (KeyError, TypeError, ValueError) as error:
            raise ValueError("Recorded policy-value state hash is invalid") from error
        if state_hash in selected:
            raise ValueError("Recorded policy-value state is duplicated")
        selected[state_hash] = entry
    if not selected:
        raise ValueError("Recorded policy-value scenario is unavailable")
    return selected


def _query_to_wire(query: PolicyValueQuery) -> dict[str, Any]:
    return {
        "requestId": query.request_id,
        "stateHash": str(query.state_hash),
        "observation": list(query.observation),
        "legalActionMask": list(query.legal_action_mask),
        "recurrentState": list(query.recurrent_state),
    }


def _query_from_wire(payload: Any) -> PolicyValueQuery:
    if not isinstance(payload, dict):
        raise ValueError("Malformed policy-value query")
    try:
        query = PolicyValueQuery(
            request_id=payload["requestId"],
            state_hash=int(payload["stateHash"]),
            observation=tuple(payload["observation"]),
            legal_action_mask=tuple(payload["legalActionMask"]),
            recurrent_state=tuple(payload.get("recurrentState", ())),
        )
    except (KeyError, TypeError, ValueError) as error:
        raise ValueError("Malformed policy-value query") from error
    validate_query(query)
    return query


def _estimate_to_wire(estimate: PolicyValueEstimate) -> dict[str, Any]:
    return {
        "requestId": estimate.request_id,
        "policyPrior": list(estimate.policy_prior),
        "valueEstimate": estimate.value_estimate,
        "recurrentState": list(estimate.recurrent_state),
        "diagnostic": estimate.diagnostic,
    }


def _encode_frame(payload: dict[str, Any]) -> bytes:
    encoded = json.dumps(
        payload, separators=(",", ":"), allow_nan=False
    ).encode("utf-8")
    if len(encoded) > MAX_FRAME_BYTES:
        raise ValueError("Policy-value frame exceeds size limit")
    return struct.pack(">I", len(encoded)) + encoded


def _decode_frame(
    frame: bytes, kind: str, expected_contract: PolicyValueContract
) -> dict[str, Any]:
    if not isinstance(frame, bytes) or len(frame) < 4:
        raise ValueError("Malformed policy-value frame")
    size = struct.unpack(">I", frame[:4])[0]
    if size > MAX_FRAME_BYTES or size != len(frame) - 4:
        raise ValueError("Truncated policy-value frame")
    try:
        payload = json.loads(frame[4:].decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise ValueError("Malformed policy-value frame") from error
    if (
        not isinstance(payload, dict)
        or payload.get("schemaVersion") != POLICY_VALUE_SCHEMA_VERSION
        or payload.get("kind") != kind
    ):
        raise ValueError("Policy-value frame schema mismatch")
    actual_contract = PolicyValueContract.from_wire(payload.get("contract"))
    if actual_contract != expected_contract:
        raise ValueError("Policy-value frame contract is stale")
    return payload


def _finite_vector(
    values: Iterable[float], size: int | None, name: str
) -> tuple[float, ...]:
    if not isinstance(values, (tuple, list)):
        raise ValueError(f"Policy-value {name} is not a vector")
    if size is not None and len(values) != size:
        raise ValueError(f"Policy-value {name} dimension mismatch")
    if any(
        isinstance(value, bool)
        or not isinstance(value, (int, float))
        or not math.isfinite(value)
        for value in values
    ):
        raise ValueError(f"Policy-value {name} contains non-finite value")
    return tuple(float(value) for value in values)


def _is_hash(value: Any) -> bool:
    return isinstance(value, str) and re.fullmatch(r"[0-9a-f]{64}", value) is not None
