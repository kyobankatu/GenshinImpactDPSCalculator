"""Cross-language policy-value contract and malformed-frame regressions."""

from __future__ import annotations

import json
import math
import os
import struct
import sys

import pytest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from policy_value_protocol import (
    PolicyValueContract,
    PolicyValueEstimate,
    PolicyValueQuery,
    decode_request_batch,
    decode_response_batch,
    encode_request_batch,
    encode_response_batch,
    load_recorded_fixture,
    normalize_policy_value_estimate,
    uniform_fallback,
)


DATASET_HASH = "1b57a2f27296dd66e5f0336dddf6ad4a9d3b0020c60290705f5adece0c0a6495"
CHECKPOINT_HASH = "b" * 64
FIXTURE = os.path.join(
    os.path.dirname(__file__), "fixtures", "policy_value_v1.json"
)


def _contract(**overrides) -> PolicyValueContract:
    values = {
        "simulator_revision": "rotation-simulator-v4",
        "dataset_schema_version": 2,
        "dataset_source_hash": DATASET_HASH,
        "action_layout_revision": 2,
        "observation_schema_revision": 2,
        "model_revision": 2,
        "checkpoint_fingerprint": CHECKPOINT_HASH,
    }
    values.update(overrides)
    return PolicyValueContract(**values)


def _query(request_id: int = 7) -> PolicyValueQuery:
    mask = [0.0] * 11
    mask[0] = 1.0
    mask[6] = 1.0
    return PolicyValueQuery(
        request_id=request_id,
        state_hash=42,
        observation=(0.0,) * 287,
        legal_action_mask=tuple(mask),
        recurrent_state=(0.25, -0.5),
    )


def test_cross_language_fixture_normalizes_and_round_trips_recurrent_state():
    entries = load_recorded_fixture(
        FIXTURE, _contract(), "fixture-policy-value"
    )
    entry = entries[42]
    estimate = normalize_policy_value_estimate(
        7,
        entry["policyPrior"],
        entry["valueEstimate"],
        entry["recurrentStateOut"],
        _query().legal_action_mask,
    )
    assert math.isclose(estimate.policy_prior[0], 2.0 / 3.0)
    assert math.isclose(estimate.policy_prior[6], 1.0 / 3.0)
    assert estimate.policy_prior[1] == 0.0
    assert estimate.value_estimate == 12.5
    assert estimate.recurrent_state == (0.5, -0.25)


def test_batched_frames_preserve_order_and_uniform_fallback():
    contract = _contract()
    queries = (_query(7), _query(8))
    request_frame = encode_request_batch(contract, queries)
    assert decode_request_batch(request_frame, contract) == queries
    estimates = (
        normalize_policy_value_estimate(
            7, [2.0] + [0.0] * 5 + [1.0] + [0.0] * 4,
            4.0, (0.5, -0.25), queries[0].legal_action_mask,
        ),
        uniform_fallback(queries[1], "unavailable"),
    )
    response_frame = encode_response_batch(contract, estimates)
    decoded = decode_response_batch(response_frame, contract, queries)
    assert decoded == estimates
    assert decoded[1].diagnostic == "unavailable"
    assert decoded[1].recurrent_state == queries[1].recurrent_state


@pytest.mark.parametrize(
    "field,value",
    [
        ("model_revision", 1),
        ("action_layout_revision", 1),
        ("observation_schema_revision", 1),
    ],
)
def test_stale_contract_is_rejected(field, value):
    stale = _contract(**{field: value})
    with pytest.raises(ValueError, match="revision mismatch"):
        stale.validate()


def test_stale_dataset_and_checkpoint_fingerprints_are_rejected():
    query = _query()
    for stale in (
        _contract(dataset_source_hash="a" * 64),
        _contract(checkpoint_fingerprint="c" * 64),
    ):
        frame = encode_request_batch(stale, (query,))
        with pytest.raises(ValueError, match="contract is stale"):
            decode_request_batch(frame, _contract())


def test_invalid_policy_value_outputs_are_rejected():
    query = _query()
    valid = [2.0] + [0.0] * 5 + [1.0] + [0.0] * 4
    cases = (
        valid[:-1],
        [float("nan")] + valid[1:],
        [-1.0] + valid[1:],
        [0.0] * 11,
        [2.0, 0.1] + valid[2:],
    )
    for weights in cases:
        with pytest.raises(ValueError):
            normalize_policy_value_estimate(
                query.request_id,
                weights,
                None,
                (),
                query.legal_action_mask,
            )
    with pytest.raises(ValueError, match="not finite"):
        normalize_policy_value_estimate(
            query.request_id,
            valid,
            float("nan"),
            (),
            query.legal_action_mask,
        )


def test_malformed_truncated_and_response_order_frames_are_rejected():
    contract = _contract()
    query = _query()
    with pytest.raises(ValueError, match="Malformed"):
        decode_request_batch(b"bad", contract)
    valid = encode_request_batch(contract, (query,))
    with pytest.raises(ValueError, match="Truncated"):
        decode_request_batch(valid[:-1], contract)

    estimate = uniform_fallback(query, "timeout")
    payload = json.loads(encode_response_batch(contract, (estimate,))[4:])
    payload["items"][0]["requestId"] = query.request_id + 1
    encoded = json.dumps(payload, separators=(",", ":")).encode()
    mismatched = struct.pack(">I", len(encoded)) + encoded
    with pytest.raises(ValueError, match="order mismatch"):
        decode_response_batch(mismatched, contract, (query,))


def test_truncated_batch_and_nonfinite_recurrent_state_are_rejected():
    contract = _contract()
    query = _query()
    estimate = uniform_fallback(query, "timeout")
    frame = encode_response_batch(contract, (estimate,))
    with pytest.raises(ValueError, match="truncated"):
        decode_response_batch(frame, contract, (query, _query(8)))
    malformed = PolicyValueEstimate(
        request_id=query.request_id,
        policy_prior=estimate.policy_prior,
        value_estimate=None,
        recurrent_state=(float("nan"),),
    )
    with pytest.raises(ValueError):
        encode_response_batch(contract, (malformed,))
