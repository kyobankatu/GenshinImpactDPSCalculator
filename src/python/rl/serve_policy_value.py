"""Loopback-only batched policy-value inference service for Java search."""

from __future__ import annotations

import argparse
import hashlib
import socket
import socketserver
import struct
import threading
from pathlib import Path

import torch

from policy_value_protocol import (
    DATASET_SCHEMA_VERSION,
    MAX_FRAME_BYTES,
    PolicyValueContract,
    PolicyValueEstimate,
    PolicyValueQuery,
    decode_request_batch,
    encode_response_batch,
    normalize_policy_value_estimate,
)
from recurrent_ppo import load_policy


class TorchPolicyValueAdvisor:
    """Checkpoint-backed batched advisor with caller-owned recurrent state."""

    def __init__(self, checkpoint_path: str | Path, dataset_source_hash: str):
        checkpoint = Path(checkpoint_path)
        payload = torch.load(checkpoint, map_location="cpu", weights_only=False)
        if payload.get("dataset_source_hash") != dataset_source_hash:
            raise ValueError("Policy-value checkpoint dataset fingerprint is stale")
        self.policy, _ = load_policy(checkpoint)
        self.policy.eval()
        self._inference_lock = threading.Lock()
        self.contract = PolicyValueContract(
            simulator_revision=payload["simulator_revision"],
            dataset_schema_version=DATASET_SCHEMA_VERSION,
            dataset_source_hash=dataset_source_hash,
            action_layout_revision=payload["action_layout_revision"],
            observation_schema_revision=payload["observation_schema_revision"],
            model_revision=payload["architecture_revision"],
            checkpoint_fingerprint=hashlib.sha256(checkpoint.read_bytes()).hexdigest(),
        )
        self.contract.validate()

    def advise(
        self, queries: tuple[PolicyValueQuery, ...]
    ) -> tuple[PolicyValueEstimate, ...]:
        """Run one allocation-bounded model batch and return normalized estimates."""
        hidden_rows = []
        for query in queries:
            if query.recurrent_state:
                if len(query.recurrent_state) != self.policy.recurrent_state_size:
                    raise ValueError("Policy-value recurrent state dimension mismatch")
                hidden_rows.append(query.recurrent_state)
            else:
                hidden_rows.append((0.0,) * self.policy.recurrent_state_size)
        with self._inference_lock, torch.no_grad():
            output = self.policy.act(
                torch.tensor(
                    [query.observation for query in queries], dtype=torch.float32
                ),
                torch.tensor(hidden_rows, dtype=torch.float32),
                torch.tensor(
                    [query.legal_action_mask for query in queries],
                    dtype=torch.float32,
                ),
                deterministic=True,
            )
        estimates = []
        for index, query in enumerate(queries):
            estimates.append(
                normalize_policy_value_estimate(
                    query.request_id,
                    output["probabilities"][index].tolist(),
                    float(output["value"][index]),
                    output["hidden"][index].tolist(),
                    query.legal_action_mask,
                )
            )
        return tuple(estimates)


def process_frame(
    frame: bytes,
    advisor,
    contract: PolicyValueContract,
    max_batch_size: int,
) -> bytes:
    """Validate one request, invoke the model, and validate ordered output."""
    queries = decode_request_batch(frame, contract)
    if len(queries) > max_batch_size:
        raise ValueError("Policy-value request exceeds batch limit")
    estimates = tuple(advisor.advise(queries))
    if len(estimates) != len(queries):
        raise ValueError("Policy-value advisor returned a partial batch")
    validated = []
    for query, estimate in zip(queries, estimates):
        if estimate.request_id != query.request_id:
            raise ValueError("Policy-value advisor response order mismatch")
        validated.append(
            normalize_policy_value_estimate(
                estimate.request_id,
                estimate.policy_prior,
                estimate.value_estimate,
                estimate.recurrent_state,
                query.legal_action_mask,
                estimate.diagnostic,
            )
        )
    return encode_response_batch(contract, validated)


class PolicyValueServer(socketserver.ThreadingTCPServer):
    """Threaded loopback server with bounded request frames and clean shutdown."""

    allow_reuse_address = True
    daemon_threads = True

    def __init__(self, address, advisor, max_batch_size: int):
        host, _ = address
        if not _is_loopback(host) or max_batch_size <= 0:
            raise ValueError("Policy-value server must use loopback and positive batch size")
        self.advisor = advisor
        self.contract = advisor.contract
        self.max_batch_size = max_batch_size
        super().__init__(address, _PolicyValueHandler)


class _PolicyValueHandler(socketserver.BaseRequestHandler):
    def handle(self) -> None:
        self.request.settimeout(30.0)
        while True:
            try:
                prefix = _recv_exact(self.request, 4)
            except (ConnectionError, TimeoutError, OSError):
                return
            size = struct.unpack(">I", prefix)[0]
            if size <= 0 or size > MAX_FRAME_BYTES:
                return
            try:
                body = _recv_exact(self.request, size)
                response = process_frame(
                    prefix + body,
                    self.server.advisor,
                    self.server.contract,
                    self.server.max_batch_size,
                )
                self.request.sendall(response)
            except (ConnectionError, OSError, RuntimeError, ValueError):
                return


def _recv_exact(connection: socket.socket, size: int) -> bytes:
    data = bytearray()
    while len(data) < size:
        chunk = connection.recv(size - len(data))
        if not chunk:
            raise ConnectionError("Policy-value connection closed")
        data.extend(chunk)
    return bytes(data)


def _is_loopback(host: str) -> bool:
    try:
        return socket.gethostbyname(host).startswith("127.")
    except socket.gaierror:
        return False


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--checkpoint", required=True)
    parser.add_argument("--dataset-source-hash", required=True)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=18761)
    parser.add_argument("--max-batch-size", type=int, default=128)
    args = parser.parse_args()
    advisor = TorchPolicyValueAdvisor(args.checkpoint, args.dataset_source_hash)
    with PolicyValueServer(
        (args.host, args.port), advisor, args.max_batch_size
    ) as server:
        print(
            "Policy-value service listening on "
            f"{args.host}:{server.server_address[1]} "
            f"checkpoint={advisor.contract.checkpoint_fingerprint}",
            flush=True,
        )
        try:
            server.serve_forever(poll_interval=0.2)
        except KeyboardInterrupt:
            pass


if __name__ == "__main__":
    main()
