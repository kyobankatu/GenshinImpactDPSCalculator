"""Behavior-clone expert trajectories and pretrain the shared value head."""

from __future__ import annotations

import argparse
import os
import random
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import numpy as np
import torch
import torch.nn.functional as functional

from expert_dataset import (
    ACTION_SIZE,
    OBSERVATION_SIZE,
    SIMULATOR_REVISION,
    ExpertDataset,
    ExpertSequenceChunk,
    build_party_balanced_order,
    build_sequence_chunks,
    fingerprints_by_split,
    load_expert_dataset,
    training_records,
    value_normalization,
)
from recurrent_ppo import (
    PRIVILEGED_OBSERVATION_SIZE,
    build_policy,
    validate_checkpoint_payload,
)
from rotation_model_registry import model_names


PRETRAINING_REVISION = 3
DEFAULT_OUTPUT = "output/expert_pretrain/latest-model.pt"
PRESETS = {
    "debug": {
        "epochs": 80,
        "hidden_size": 16,
        "sequence_length": 16,
        "batch_size": 4,
        "learning_rate": 3e-3,
        "value_coefficient": 0.25,
        "scheduler_gamma": 0.99,
        "policy_type": "gru",
    },
    "benchmark": {
        "epochs": 100,
        "hidden_size": 64,
        "sequence_length": 32,
        "batch_size": 32,
        "learning_rate": 3e-4,
        "value_coefficient": 0.5,
        "scheduler_gamma": 0.995,
        "policy_type": "transformer",
    },
}


@dataclass(frozen=True)
class PretrainingConfig:
    """Explicit deterministic supervised-training configuration."""

    dataset_path: str
    output_path: str = DEFAULT_OUTPUT
    resume_from: str | None = None
    epochs: int = 80
    hidden_size: int = 16
    sequence_length: int = 16
    batch_size: int = 4
    learning_rate: float = 3e-3
    value_coefficient: float = 0.25
    scheduler_gamma: float = 0.99
    policy_type: str = "gru"
    seed: int = 1234
    device: str = "cpu"

    def validate(self) -> None:
        if not self.dataset_path:
            raise ValueError("dataset_path is required")
        if self.epochs <= 0 or self.hidden_size <= 0 or self.sequence_length <= 0:
            raise ValueError("epochs, hidden_size, and sequence_length must be positive")
        if self.batch_size <= 0 or self.learning_rate <= 0.0:
            raise ValueError("batch_size and learning_rate must be positive")
        if self.value_coefficient < 0.0:
            raise ValueError("value_coefficient must be non-negative")
        if not 0.0 < self.scheduler_gamma <= 1.0:
            raise ValueError("scheduler_gamma must be within (0, 1]")
        if self.policy_type not in model_names():
            raise ValueError(
                f"policy_type must be one of {', '.join(model_names())}"
            )


def run_pretraining(config: PretrainingConfig) -> tuple[torch.nn.Module, list[dict[str, float]]]:
    """Train or resume a deterministic policy and save a PPO-compatible checkpoint."""
    config.validate()
    dataset = load_expert_dataset(config.dataset_path)
    train_records = training_records(dataset)
    mean, scale = value_normalization(train_records)
    device = torch.device(config.device)
    _seed_all(config.seed)
    policy = build_policy(
        config.policy_type,
        OBSERVATION_SIZE,
        config.hidden_size,
        ACTION_SIZE,
        privileged_observation_size=PRIVILEGED_OBSERVATION_SIZE,
    ).to(device)
    optimizer = torch.optim.Adam(policy.parameters(), lr=config.learning_rate)
    scheduler = torch.optim.lr_scheduler.ExponentialLR(
        optimizer, gamma=config.scheduler_gamma
    )
    start_epoch = 0
    history: list[dict[str, float]] = []
    if config.resume_from:
        start_epoch, history = _restore_checkpoint(
            config, dataset, policy, optimizer, scheduler, device
        )
    if start_epoch >= config.epochs:
        raise ValueError(
            f"Checkpoint epoch {start_epoch} already reaches requested epochs {config.epochs}"
        )

    for epoch in range(start_epoch + 1, config.epochs + 1):
        order = build_party_balanced_order(train_records, config.seed, epoch)
        chunks = list(
            build_sequence_chunks(
                order, config.sequence_length, mean, scale
            )
        )
        epoch_generator = random.Random((config.seed << 32) ^ epoch ^ 0x5EED)
        epoch_generator.shuffle(chunks)
        metrics = _train_epoch(policy, optimizer, chunks, config, device)
        scheduler.step()
        metrics["epoch"] = float(epoch)
        metrics["learning_rate"] = float(scheduler.get_last_lr()[0])
        validation = _evaluate_split(
            policy,
            dataset,
            "validation",
            config,
            mean,
            scale,
            device,
        )
        metrics.update(validation)
        history.append(metrics)
        _save_checkpoint(
            config,
            dataset,
            policy,
            optimizer,
            scheduler,
            epoch,
            history,
            mean,
            scale,
        )
    return policy, history


def _train_epoch(policy, optimizer, chunks, config, device):
    policy.train()
    totals = {"policy_loss": 0.0, "value_loss": 0.0, "accuracy": 0.0}
    updates = 0
    for start in range(0, len(chunks), config.batch_size):
        batch = _collate(chunks[start : start + config.batch_size], policy, device)
        logits, values, _, _, _ = policy.forward_sequence(
            batch["observations"],
            batch["initial_hidden"],
            batch["action_masks"],
            sequence_mask=batch["sequence_mask"],
        )
        valid = batch["loss_mask"]
        valid_count = valid.sum().clamp_min(1.0)
        log_probabilities = functional.log_softmax(logits, dim=-1)
        policy_loss = -(
            batch["policy_targets"] * log_probabilities
        ).sum(dim=-1)
        policy_loss = (policy_loss * valid).sum() / valid_count
        value_loss = (
            (values - batch["value_targets"]).pow(2) * valid
        ).sum() / valid_count
        loss = policy_loss + config.value_coefficient * value_loss
        optimizer.zero_grad()
        loss.backward()
        torch.nn.utils.clip_grad_norm_(policy.parameters(), 1.0)
        optimizer.step()
        predictions = logits.argmax(dim=-1)
        teacher = batch["policy_targets"].argmax(dim=-1)
        accuracy = ((predictions == teacher).float() * valid).sum() / valid_count
        totals["policy_loss"] += float(policy_loss.detach())
        totals["value_loss"] += float(value_loss.detach())
        totals["accuracy"] += float(accuracy.detach())
        updates += 1
    if updates == 0:
        raise ValueError("Expert training produced no minibatches")
    metrics = {name: value / updates for name, value in totals.items()}
    metrics["optimizer_steps"] = float(updates)
    return metrics


def _evaluate_split(policy, dataset, split, config, mean, scale, device):
    records = tuple(record for record in dataset.records if record.split == split)
    if not records:
        return {}
    chunks = build_sequence_chunks(records, config.sequence_length, mean, scale)
    policy.eval()
    correct = 0.0
    count = 0.0
    with torch.no_grad():
        for start in range(0, len(chunks), config.batch_size):
            batch = _collate(chunks[start : start + config.batch_size], policy, device)
            logits, _, _, _, _ = policy.forward_sequence(
                batch["observations"],
                batch["initial_hidden"],
                batch["action_masks"],
                sequence_mask=batch["sequence_mask"],
            )
            valid = batch["loss_mask"]
            correct += float(
                (
                    (logits.argmax(dim=-1) == batch["policy_targets"].argmax(dim=-1))
                    .float()
                    * valid
                ).sum()
            )
            count += float(valid.sum())
    return {f"{split}_accuracy": correct / max(count, 1.0)}


def _collate(chunks: list[ExpertSequenceChunk], policy, device):
    if not chunks:
        raise ValueError("Cannot collate an empty expert minibatch")
    length = max(len(chunk.decisions) for chunk in chunks)
    batch_size = len(chunks)
    observations = torch.zeros(batch_size, length, OBSERVATION_SIZE, device=device)
    action_masks = torch.zeros(batch_size, length, ACTION_SIZE, device=device)
    policy_targets = torch.zeros(batch_size, length, ACTION_SIZE, device=device)
    value_targets = torch.zeros(batch_size, length, device=device)
    sequence_mask = torch.zeros(batch_size, length, device=device)
    loss_mask = torch.zeros(batch_size, length, device=device)
    for row, chunk in enumerate(chunks):
        count = len(chunk.decisions)
        observations[row, :count] = torch.tensor(
            [decision.observation for decision in chunk.decisions],
            device=device,
        )
        action_masks[row, :count] = torch.tensor(
            [decision.legal_action_mask for decision in chunk.decisions],
            device=device,
        )
        policy_targets[row, :count] = torch.tensor(
            [decision.visit_policy_target for decision in chunk.decisions],
            device=device,
        )
        value_targets[row, :count] = torch.tensor(
            chunk.value_targets,
            device=device,
        )
        sequence_mask[row, :count] = 1.0
        loss_mask[row, chunk.loss_start:count] = 1.0
    return {
        "observations": observations,
        "action_masks": action_masks,
        "policy_targets": policy_targets,
        "value_targets": value_targets,
        "sequence_mask": sequence_mask,
        "loss_mask": loss_mask,
        "initial_hidden": torch.zeros(
            batch_size, policy.recurrent_state_size, device=device
        ),
    }


def _save_checkpoint(
    config,
    dataset,
    policy,
    optimizer,
    scheduler,
    epoch,
    history,
    mean,
    scale,
):
    output = Path(config.output_path)
    output.parent.mkdir(parents=True, exist_ok=True)
    split_fingerprints = fingerprints_by_split(dataset)
    training_fingerprints = list(split_fingerprints["train"])
    policy.save(
        output,
        optimizer,
        extra_state={
            "pretraining_revision": PRETRAINING_REVISION,
            "pretraining_seed": config.seed,
            "pretraining_config": {
                "epochs": config.epochs,
                "hiddenSize": config.hidden_size,
                "sequenceLength": config.sequence_length,
                "batchSize": config.batch_size,
                "learningRate": config.learning_rate,
                "valueCoefficient": config.value_coefficient,
                "schedulerGamma": config.scheduler_gamma,
                "policyType": config.policy_type,
            },
            "pretraining_epoch": epoch,
            "dataset_source_hash": dataset.source_hash,
            "dataset_record_hashes": [
                record.record_hash for record in dataset.records
            ],
            "simulator_revision": SIMULATOR_REVISION,
            "training_fingerprints": training_fingerprints,
            "normalization_fingerprints": training_fingerprints,
            "scheduler_state_dict": scheduler.state_dict(),
            "python_random_state": random.getstate(),
            "numpy_random_state": _serialize_numpy_random_state(),
            "torch_random_state": torch.get_rng_state(),
            "pretraining_history": history,
            "value_normalization": {"mean": mean, "scale": scale},
        },
    )


def _restore_checkpoint(config, dataset, policy, optimizer, scheduler, device):
    checkpoint = Path(config.resume_from)
    if not checkpoint.is_file():
        raise ValueError(f"Pretraining checkpoint does not exist: {checkpoint}")
    payload = torch.load(checkpoint, map_location=device, weights_only=False)
    validate_checkpoint_payload(payload, checkpoint)
    required = (
        "pretraining_revision",
        "pretraining_seed",
        "pretraining_config",
        "pretraining_epoch",
        "dataset_source_hash",
        "dataset_record_hashes",
        "simulator_revision",
        "training_fingerprints",
        "normalization_fingerprints",
        "optimizer_state_dict",
        "scheduler_state_dict",
        "python_random_state",
        "numpy_random_state",
        "torch_random_state",
        "pretraining_history",
        "value_normalization",
    )
    missing = [name for name in required if name not in payload]
    if missing:
        raise ValueError(f"Pretraining checkpoint is missing metadata: {missing}")
    if payload["pretraining_revision"] != PRETRAINING_REVISION:
        raise ValueError("Pretraining checkpoint revision mismatch")
    if payload["pretraining_seed"] != config.seed:
        raise ValueError("Pretraining checkpoint seed mismatch")
    expected_config = {
        "epochs": config.epochs,
        "hiddenSize": config.hidden_size,
        "sequenceLength": config.sequence_length,
        "batchSize": config.batch_size,
        "learningRate": config.learning_rate,
        "valueCoefficient": config.value_coefficient,
        "schedulerGamma": config.scheduler_gamma,
        "policyType": config.policy_type,
    }
    checkpoint_config = dict(payload["pretraining_config"])
    checkpoint_config["epochs"] = config.epochs
    if checkpoint_config != expected_config:
        raise ValueError("Pretraining checkpoint configuration mismatch")
    if payload["simulator_revision"] != SIMULATOR_REVISION:
        raise ValueError("Pretraining checkpoint simulator revision mismatch")
    if payload["dataset_source_hash"] != dataset.source_hash:
        raise ValueError("Pretraining checkpoint dataset manifest hash mismatch")
    hashes = [record.record_hash for record in dataset.records]
    if payload["dataset_record_hashes"] != hashes:
        raise ValueError("Pretraining checkpoint dataset record hash mismatch")
    expected_fingerprints = list(fingerprints_by_split(dataset)["train"])
    if payload["training_fingerprints"] != expected_fingerprints:
        raise ValueError("Pretraining checkpoint training fingerprints mismatch")
    if payload["normalization_fingerprints"] != expected_fingerprints:
        raise ValueError("Pretraining checkpoint normalization fingerprints mismatch")
    if payload["policy_type"] != config.policy_type:
        raise ValueError("Pretraining checkpoint policy type mismatch")
    if payload["hidden_size"] != config.hidden_size:
        raise ValueError("Pretraining checkpoint hidden size mismatch")
    policy.load_state_dict(payload["state_dict"])
    optimizer.load_state_dict(payload["optimizer_state_dict"])
    scheduler.load_state_dict(payload["scheduler_state_dict"])
    random.setstate(payload["python_random_state"])
    np.random.set_state(_deserialize_numpy_random_state(payload["numpy_random_state"]))
    torch.set_rng_state(payload["torch_random_state"].cpu())
    return int(payload["pretraining_epoch"]), list(payload["pretraining_history"])


def _seed_all(seed: int) -> None:
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    torch.use_deterministic_algorithms(True)


def _serialize_numpy_random_state() -> dict[str, Any]:
    algorithm, keys, position, has_gauss, cached_gaussian = np.random.get_state()
    return {
        "algorithm": algorithm,
        "keys": keys.tolist(),
        "position": position,
        "has_gauss": has_gauss,
        "cached_gaussian": cached_gaussian,
    }


def _deserialize_numpy_random_state(payload: dict[str, Any]):
    if not isinstance(payload, dict) or set(payload) != {
        "algorithm",
        "keys",
        "position",
        "has_gauss",
        "cached_gaussian",
    }:
        raise ValueError("Pretraining checkpoint NumPy RNG state is malformed")
    return (
        payload["algorithm"],
        np.asarray(payload["keys"], dtype=np.uint32),
        int(payload["position"]),
        int(payload["has_gauss"]),
        float(payload["cached_gaussian"]),
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--preset", choices=sorted(PRESETS), default="debug")
    parser.add_argument("--dataset", required=True)
    parser.add_argument("--output", default=DEFAULT_OUTPUT)
    parser.add_argument("--resume-from")
    parser.add_argument("--epochs", type=int)
    parser.add_argument("--hidden-size", type=int)
    parser.add_argument("--sequence-length", type=int)
    parser.add_argument("--batch-size", type=int)
    parser.add_argument("--learning-rate", type=float)
    parser.add_argument("--value-coefficient", type=float)
    parser.add_argument("--scheduler-gamma", type=float)
    parser.add_argument("--policy-type", choices=model_names())
    parser.add_argument("--seed", type=int, default=1234)
    parser.add_argument("--device", default="cpu")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    values: dict[str, Any] = dict(PRESETS[args.preset])
    for name in (
        "epochs",
        "hidden_size",
        "sequence_length",
        "batch_size",
        "learning_rate",
        "value_coefficient",
        "scheduler_gamma",
        "policy_type",
    ):
        value = getattr(args, name)
        if value is not None:
            values[name] = value
    config = PretrainingConfig(
        dataset_path=args.dataset,
        output_path=args.output,
        resume_from=args.resume_from,
        seed=args.seed,
        device=args.device,
        **values,
    )
    _, history = run_pretraining(config)
    final = history[-1]
    print(
        f"epoch={int(final['epoch'])} policyLoss={final['policy_loss']:.6f} "
        f"valueLoss={final['value_loss']:.6f} accuracy={final['accuracy']:.6f}"
    )


if __name__ == "__main__":
    main()
