"""Bounded local policy-value service regressions."""

from __future__ import annotations

import os
import socket
import sys
import threading

import pytest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from policy_value_protocol import (
    PolicyValueContract,
    PolicyValueQuery,
    decode_response_batch,
    encode_request_batch,
    normalize_policy_value_estimate,
)
from expert_dataset import load_expert_dataset
from pretrain_expert_policy import PretrainingConfig, run_pretraining
from serve_policy_value import (
    PolicyValueServer,
    TorchPolicyValueAdvisor,
    process_frame,
)


EXPERT_FIXTURE = os.path.join(
    os.path.dirname(__file__), "fixtures", "expert_dataset_v2.jsonl"
)


class StaticAdvisor:
    """Deterministic test advisor with no model dependency."""

    def __init__(self, contract):
        self.contract = contract

    def advise(self, queries):
        return tuple(
            normalize_policy_value_estimate(
                query.request_id,
                [1.0 if index == 0 else 0.0 for index in range(11)],
                3.5,
                query.recurrent_state,
                query.legal_action_mask,
            )
            for query in queries
        )


def _contract() -> PolicyValueContract:
    return PolicyValueContract(
        simulator_revision="rotation-simulator-v4",
        dataset_schema_version=2,
        dataset_source_hash="a" * 64,
        action_layout_revision=2,
        observation_schema_revision=2,
        model_revision=2,
        checkpoint_fingerprint="b" * 64,
    )


def _query(request_id=1) -> PolicyValueQuery:
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


def test_process_frame_validates_batch_and_response_order():
    contract = _contract()
    queries = (_query(1), _query(2))
    response = process_frame(
        encode_request_batch(contract, queries),
        StaticAdvisor(contract),
        contract,
        2,
    )
    estimates = decode_response_batch(response, contract, queries)
    assert [estimate.request_id for estimate in estimates] == [1, 2]
    assert all(estimate.value_estimate == 3.5 for estimate in estimates)

    class PartialAdvisor(StaticAdvisor):
        def advise(self, queries):
            return super().advise(queries[:1])

    with pytest.raises(ValueError, match="partial batch"):
        process_frame(
            encode_request_batch(contract, queries),
            PartialAdvisor(contract),
            contract,
            2,
        )


def test_service_round_trip_and_batch_limit():
    contract = _contract()
    advisor = StaticAdvisor(contract)
    with PolicyValueServer(("127.0.0.1", 0), advisor, 2) as server:
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            queries = (_query(3), _query(4))
            with socket.create_connection(server.server_address, timeout=1.0) as client:
                client.sendall(encode_request_batch(contract, queries))
                prefix = _recv_exact(client, 4)
                size = int.from_bytes(prefix, "big")
                response = prefix + _recv_exact(client, size)
            estimates = decode_response_batch(response, contract, queries)
            assert [estimate.request_id for estimate in estimates] == [3, 4]
        finally:
            server.shutdown()
            thread.join(timeout=1.0)

    with pytest.raises(ValueError, match="batch limit"):
        process_frame(
            encode_request_batch(contract, (_query(1), _query(2))),
            advisor,
            contract,
            1,
        )


def test_service_rejects_non_loopback_and_mask_disagreement():
    with pytest.raises(ValueError, match="loopback"):
        PolicyValueServer(("192.0.2.1", 0), StaticAdvisor(_contract()), 1)

    class IllegalAdvisor(StaticAdvisor):
        def advise(self, queries):
            estimate = normalize_policy_value_estimate(
                queries[0].request_id,
                [0.0, 1.0] + [0.0] * 9,
                None,
                (),
                [0.0, 1.0] + [0.0] * 9,
            )
            return (estimate,)

    with pytest.raises(ValueError, match="masked probability"):
        process_frame(
            encode_request_batch(_contract(), (_query(),)),
            IllegalAdvisor(_contract()),
            _contract(),
            1,
        )


def test_checkpoint_advisor_runs_batched_model_and_rejects_stale_dataset(tmp_path):
    checkpoint = tmp_path / "policy.pt"
    run_pretraining(
        PretrainingConfig(
            dataset_path=EXPERT_FIXTURE,
            output_path=str(checkpoint),
            epochs=1,
            hidden_size=8,
            sequence_length=4,
            batch_size=2,
            learning_rate=1.0e-3,
            policy_type="gru",
            seed=91,
        )
    )
    dataset = load_expert_dataset(EXPERT_FIXTURE)
    decision = dataset.records[0].decisions[0]
    query = PolicyValueQuery(
        request_id=9,
        state_hash=decision.state_hash,
        observation=decision.observation,
        legal_action_mask=decision.legal_action_mask,
    )
    advisor = TorchPolicyValueAdvisor(checkpoint, dataset.source_hash)
    estimate = advisor.advise((query,))[0]
    assert estimate.request_id == 9
    assert abs(sum(estimate.policy_prior) - 1.0) < 1.0e-6
    assert len(estimate.recurrent_state) == 8
    with pytest.raises(ValueError, match="stale"):
        TorchPolicyValueAdvisor(checkpoint, "c" * 64)


def _recv_exact(connection, size):
    data = bytearray()
    while len(data) < size:
        chunk = connection.recv(size - len(data))
        if not chunk:
            raise ConnectionError("test service closed early")
        data.extend(chunk)
    return bytes(data)
