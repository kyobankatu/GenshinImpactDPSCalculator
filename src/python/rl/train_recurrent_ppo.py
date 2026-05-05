import argparse
import csv
import os
import random
import time
from collections import defaultdict

import numpy as np
import torch
import torch.nn as nn

try:
    import wandb
except ImportError:
    wandb = None

from recurrent_ppo import RecurrentPolicy, compute_advantages
from rollout_service_client import build_rollout_client


class RNDModule(nn.Module):
    """Random Network Distillation for intrinsic exploration bonus.

    A frozen target network and a trained predictor both map observations
    to a small embedding. Prediction error is used as intrinsic reward.
    """

    def __init__(self, observation_size, embedding_size):
        super().__init__()
        self.target = nn.Sequential(
            nn.Linear(observation_size, embedding_size),
            nn.Tanh(),
            nn.Linear(embedding_size, embedding_size),
        )
        self.predictor = nn.Sequential(
            nn.Linear(observation_size, embedding_size),
            nn.Tanh(),
            nn.Linear(embedding_size, embedding_size),
            nn.Tanh(),
            nn.Linear(embedding_size, embedding_size),
        )
        for param in self.target.parameters():
            param.requires_grad = False

    def forward(self, observation):
        with torch.no_grad():
            target_embedding = self.target(observation)
        predicted_embedding = self.predictor(observation)
        return (predicted_embedding - target_embedding).pow(2).mean(dim=-1)

    def loss(self, observation):
        with torch.no_grad():
            target_embedding = self.target(observation)
        predicted_embedding = self.predictor(observation)
        return (predicted_embedding - target_embedding).pow(2).mean()


OUTPUT_DIR = "output/recurrent_ppo_py"
MODEL_PATH = os.path.join(OUTPUT_DIR, "latest-model.pt")
TRAIN_LOG_PATH = os.path.join(OUTPUT_DIR, "training_log.csv")


PRESETS = {
    "debug": {
        "updates": 4,
        "rollout_length": 32,
        "envs": 4,
        "hidden_size": 64,
        "ppo_epochs": 4,
        "sequence_length": 16,
        "sequence_minibatch_size": 8,
        "gamma": 0.99,
        "gae_lambda": 0.95,
        "clip_range": 0.20,
        "learning_rate": 3e-4,
        "value_coefficient": 0.5,
        "entropy_coefficient": 0.01,
        "entropy_final_coefficient": 0.01,
        "max_grad_norm": 0.5,
        "checkpoint_interval": 2,
        "evaluation_interval": 2,
        "auxiliary_prediction_weight": 0.05,
        "sil_loss_weight": 0.0,
        "sil_buffer_size_per_party": 16,
        "sil_min_episodes_before_ready": 8,
        "rnd_intrinsic_weight": 0.0,
        "rnd_embedding_size": 64,
        "use_vine_ppo": False,
        "vine_branch_count": 4,
        "vine_horizon": 16,
        "vine_sample_actions": ["SKILL", "BURST", "SWAP"],
        "vine_max_points_per_update": 64,
    },
}


def main():
    args = parse_args()
    run_training(args)


def run_training(args, run=None):
    preset = args.preset
    seed = args.seed
    host = args.host
    port = args.port
    ports = args.ports
    endpoints = args.endpoints

    config = resolve_config(args)
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    client = build_rollout_client(host=host, port=port, ports=ports, endpoints=endpoints)
    runner_id = client.create_runner(config["envs"])
    observations, privileged_observations, action_masks, party_ids = client.reset_runner(runner_id, False)

    policy = RecurrentPolicy(
        client.observation_size,
        config["hidden_size"],
        client.action_size,
        char_feature_size=client.char_feature_size,
        global_feature_size=client.global_feature_size,
        num_chars=client.num_chars,
        privileged_observation_size=client.privileged_observation_size,
    ).to(device)
    optimizer = torch.optim.Adam(policy.parameters(), lr=config["learning_rate"])
    rnd_module = None
    rnd_optimizer = None
    rnd_intrinsic_weight = config.get("rnd_intrinsic_weight", 0.0)
    if rnd_intrinsic_weight > 0.0:
        rnd_module = RNDModule(
            client.observation_size,
            config.get("rnd_embedding_size", 64),
        ).to(device)
        rnd_optimizer = torch.optim.Adam(
            rnd_module.predictor.parameters(),
            lr=config["learning_rate"],
        )
    rnd_running_mean = 0.0
    rnd_running_var = 1.0
    rnd_running_count = 0
    start_update = 0
    if args.resume_from:
        if not os.path.exists(args.resume_from):
            raise FileNotFoundError(f"Resume checkpoint not found: {args.resume_from}")
        checkpoint_payload = RecurrentPolicy.load_payload(args.resume_from, map_location=device)
        checkpoint_hidden_size = checkpoint_payload["hidden_size"]
        checkpoint_observation_size = checkpoint_payload["observation_size"]
        checkpoint_action_size = checkpoint_payload["action_size"]
        checkpoint_char_feature_size = checkpoint_payload.get("char_feature_size")
        checkpoint_global_feature_size = checkpoint_payload.get("global_feature_size")
        checkpoint_num_chars = checkpoint_payload.get("num_chars")
        checkpoint_privileged_observation_size = checkpoint_payload.get("privileged_observation_size")
        if checkpoint_hidden_size != config["hidden_size"]:
            raise ValueError(
                f"Resume checkpoint hidden_size mismatch: checkpoint={checkpoint_hidden_size} config={config['hidden_size']}"
            )
        if checkpoint_observation_size != client.observation_size:
            raise ValueError(
                "Resume checkpoint observation size mismatch: "
                f"checkpoint={checkpoint_observation_size} service={client.observation_size}"
            )
        if checkpoint_action_size != client.action_size:
            raise ValueError(
                "Resume checkpoint action size mismatch: "
                f"checkpoint={checkpoint_action_size} service={client.action_size}"
            )
        if checkpoint_char_feature_size is not None and checkpoint_char_feature_size != policy.char_feature_size:
            raise ValueError(
                "Resume checkpoint char_feature_size mismatch: "
                f"checkpoint={checkpoint_char_feature_size} policy={policy.char_feature_size}"
            )
        if checkpoint_global_feature_size is not None and checkpoint_global_feature_size != policy.global_feature_size:
            raise ValueError(
                "Resume checkpoint global_feature_size mismatch: "
                f"checkpoint={checkpoint_global_feature_size} policy={policy.global_feature_size}"
            )
        if checkpoint_num_chars is not None and checkpoint_num_chars != policy.num_chars:
            raise ValueError(
                f"Resume checkpoint num_chars mismatch: checkpoint={checkpoint_num_chars} policy={policy.num_chars}"
            )
        if checkpoint_privileged_observation_size is not None and checkpoint_privileged_observation_size != policy.privileged_observation_size:
            raise ValueError(
                "Resume checkpoint privileged_observation_size mismatch: "
                f"checkpoint={checkpoint_privileged_observation_size} policy={policy.privileged_observation_size}"
            )
        policy.load_state_dict(checkpoint_payload["state_dict"])
        optimizer_state_dict = checkpoint_payload.get("optimizer_state_dict")
        if optimizer_state_dict is not None:
            optimizer.load_state_dict(optimizer_state_dict)
        start_update = int(checkpoint_payload.get("update", 0))
        print(f"Resuming training from {args.resume_from} at update {start_update}")
    hidden_states = torch.zeros(config["envs"], config["hidden_size"], dtype=torch.float32, device=device)
    owns_run = run is None
    run = init_wandb(args, config, client, device, existing_run=run)

    print(
        f"Starting Python Recurrent PPO training: preset={preset} updates={config['updates']} envs={config['envs']} rollout={config['rollout_length']} device={device.type}"
    )
    if start_update >= config["updates"]:
        print(f"Checkpoint is already at update {start_update}; requested updates={config['updates']}, so no training will run.")

    try:
        for update in range(start_update + 1, config["updates"] + 1):
            entropy_coefficient = scheduled_entropy_coefficient(config, update)
            rollout_start = time.time()
            completed_segments = []
            active_segments = [[] for _ in range(config["envs"])]
            episode_rewards = []
            episode_damages = []
            episode_steps = []
            episode_party_ids = []
            invalid_actions = 0
            damage_delta_sum = 0.0
            intrinsic_reward_sum = 0.0
            attention_score_sum = torch.zeros(policy.num_chars, dtype=torch.float64)
            attention_score_count = 0

            for _ in range(config["rollout_length"]):
                obs_tensor = torch.tensor(observations, dtype=torch.float32, device=device)
                mask_tensor = torch.tensor(action_masks, dtype=torch.float32, device=device)

                with torch.no_grad():
                    action_output = policy.act(obs_tensor, hidden_states, mask_tensor, deterministic=False)
                attention_score_sum += action_output["attention_scores"].detach().cpu().sum(dim=0).to(torch.float64)
                attention_score_count += action_output["attention_scores"].shape[0]

                actions = action_output["action"].cpu().tolist()
                batch = client.step_runner(runner_id, actions)
                next_observations = batch["observations"]
                next_privileged_observations = batch["privileged_observations"]
                next_action_masks = batch["action_masks"]
                next_party_ids = batch["party_ids"]
                vine_snapshot_ids_step = batch.get("vine_snapshot_ids", [-1] * config["envs"])

                intrinsic_rewards = [0.0] * config["envs"]
                if rnd_module is not None:
                    with torch.no_grad():
                        rnd_errors = rnd_module(obs_tensor).detach().cpu().tolist()
                    batch_mean = float(np.mean(rnd_errors))
                    batch_var = float(np.var(rnd_errors)) + 1e-8
                    rnd_running_count += 1
                    alpha = 1.0 / rnd_running_count
                    rnd_running_mean = rnd_running_mean * (1.0 - alpha) + batch_mean * alpha
                    rnd_running_var = rnd_running_var * (1.0 - alpha) + batch_var * alpha
                    rnd_std = max(float(rnd_running_var) ** 0.5, 1e-8)
                    intrinsic_rewards = [
                        rnd_intrinsic_weight * (rnd_errors[e] - rnd_running_mean) / rnd_std
                        for e in range(config["envs"])
                    ]

                intrinsic_reward_sum += sum(intrinsic_rewards)
                for env_index in range(config["envs"]):
                    augmented_reward = batch["rewards"][env_index] + intrinsic_rewards[env_index]
                    step_rec = build_step_record(
                        observations[env_index],
                        privileged_observations[env_index],
                        hidden_states[env_index],
                        action_masks[env_index],
                        actions[env_index],
                        action_output["log_probability"][env_index].item(),
                        action_output["value"][env_index].item(),
                        augmented_reward,
                        batch["dones"][env_index],
                    )
                    step_rec["vine_snapshot_id"] = vine_snapshot_ids_step[env_index]
                    active_segments[env_index].append(step_rec)
                    if not batch["valid_actions"][env_index]:
                        invalid_actions += 1
                    damage_delta_sum += batch["damage_deltas"][env_index]
                    if batch["dones"][env_index]:
                        episode_rewards.append(batch["episode_rewards"][env_index])
                        episode_damages.append(batch["episode_damages"][env_index])
                        episode_steps.append(batch["episode_steps"][env_index])
                        episode_party_ids.append(batch["episode_party_ids"][env_index])
                        completed_segments.append(
                            {
                                "steps": active_segments[env_index],
                                "bootstrap_value": 0.0,
                                "episode_return": batch["episode_rewards"][env_index],
                                "party_id": batch["episode_party_ids"][env_index],
                                "is_complete_episode": True,
                            }
                        )
                        active_segments[env_index] = []
                        hidden_states[env_index].zero_()
                    else:
                        hidden_states[env_index] = action_output["hidden"][env_index]

                observations = next_observations
                privileged_observations = next_privileged_observations
                action_masks = next_action_masks
                party_ids = next_party_ids

            with torch.no_grad():
                obs_tensor = torch.tensor(observations, dtype=torch.float32, device=device)
                mask_tensor = torch.tensor(action_masks, dtype=torch.float32, device=device)
                logits, values, _, _, _ = policy.forward_step(obs_tensor, hidden_states, mask_tensor)
                del logits
                bootstrap_values = values.detach().cpu().tolist()
            for env_index in range(config["envs"]):
                if active_segments[env_index]:
                    completed_segments.append(
                        {
                            "steps": active_segments[env_index],
                            "bootstrap_value": bootstrap_values[env_index],
                        }
                    )

            segments = compute_advantages(completed_segments, config["gamma"], config["gae_lambda"])
            vine_metrics = apply_vine_ppo_advantages(segments, config, client, runner_id)
            sequence_chunks = build_sequence_chunks(segments, config["sequence_length"])
            optimization_start = time.time()
            optimization_metrics = train_epoch(
                policy,
                optimizer,
                sequence_chunks,
                config,
                device,
                entropy_coefficient,
                rnd_module=rnd_module,
                rnd_optimizer=rnd_optimizer,
            )
            optimization_duration = max(1e-6, time.time() - optimization_start)

            duration = max(1e-6, time.time() - rollout_start)
            rollout_duration = max(1e-6, duration - optimization_duration)
            steps = config["envs"] * config["rollout_length"]
            env_steps_per_second = steps / duration
            valid_timesteps = sum(len(chunk["steps"]) for chunk in sequence_chunks)
            samples_per_second = valid_timesteps / duration
            mean_reward = sum(episode_rewards) / len(episode_rewards) if episode_rewards else 0.0
            mean_damage = sum(episode_damages) / len(episode_damages) if episode_damages else 0.0
            mean_episode_steps = sum(episode_steps) / len(episode_steps) if episode_steps else 0.0
            invalid_rate = invalid_actions / max(1, steps)
            mean_damage_delta = damage_delta_sum / max(1, steps)
            completed_episodes = len(episode_rewards)
            max_damage = max(episode_damages) if episode_damages else 0.0
            min_damage = min(episode_damages) if episode_damages else 0.0
            max_episode_steps = max(episode_steps) if episode_steps else 0
            min_episode_steps = min(episode_steps) if episode_steps else 0
            valid_action_rate = 1.0 - invalid_rate
            mean_attention_scores = (
                (attention_score_sum / max(1, attention_score_count)).tolist()
                if attention_score_count > 0
                else [0.0 for _ in range(policy.num_chars)]
            )
            mean_sequence_length = (
                valid_timesteps / len(sequence_chunks) if sequence_chunks else 0.0
            )
            max_sequence_length = (
                max(len(chunk["steps"]) for chunk in sequence_chunks) if sequence_chunks else 0
            )
            rnd_intrinsic_reward_mean = intrinsic_reward_sum / max(1, steps)

            log_row = {
                "update": update,
                "vine_points": vine_metrics["vine_points"],
                "vine_setup_adv_mean": vine_metrics["setup_adv_mean"],
                "vine_advantage_bias": vine_metrics["advantage_bias"],
                "samples": valid_timesteps,
                "sequence_chunks": len(sequence_chunks),
                "mean_sequence_length": mean_sequence_length,
                "max_sequence_length": max_sequence_length,
                "padding_fraction": optimization_metrics["padding_fraction"],
                "auxiliary_loss": optimization_metrics["auxiliary_loss"],
                "sil_loss": optimization_metrics["sil_loss"],
                "policy_loss": optimization_metrics["policy_loss"],
                "value_loss": optimization_metrics["value_loss"],
                "entropy": optimization_metrics["entropy"],
                "mean_reward": mean_reward,
                "mean_damage": mean_damage,
                "mean_episode_steps": mean_episode_steps,
                "mean_damage_delta": mean_damage_delta,
                "completed_episodes": completed_episodes,
                "max_damage": max_damage,
                "min_damage": min_damage,
                "max_episode_steps": max_episode_steps,
                "min_episode_steps": min_episode_steps,
                "invalid_action_rate": invalid_rate,
                "valid_action_rate": valid_action_rate,
                "approx_kl": optimization_metrics["approx_kl"],
                "clip_fraction": optimization_metrics["clip_fraction"],
                "value_mean": optimization_metrics["value_mean"],
                "log_prob_mean": optimization_metrics["log_prob_mean"],
                "entropy_coefficient": entropy_coefficient,
                "rnd_intrinsic_reward_mean": rnd_intrinsic_reward_mean,
                "env_steps_per_second": env_steps_per_second,
                "samples_per_second": samples_per_second,
                "rollout_duration_sec": rollout_duration,
                "optimization_duration_sec": optimization_duration,
            }
            print(
                f"Update {update}: reward={mean_reward:.3f} damage={mean_damage:,.0f} steps={mean_episode_steps:.1f} invalid={invalid_rate:.3f} kl={optimization_metrics['approx_kl']:.5f} clip={optimization_metrics['clip_fraction']:.3f} entropyCoef={entropy_coefficient:.5f} policy={optimization_metrics['policy_loss']:.5f} value={optimization_metrics['value_loss']:.5f} aux={optimization_metrics['auxiliary_loss']:.5f} seqs={len(sequence_chunks)} meanSeq={mean_sequence_length:.1f} envSteps/s={env_steps_per_second:.1f}"
            )
            log_wandb(
                run,
                {
                    "train/update": update,
                    "vine/points": vine_metrics["vine_points"],
                    "vine/setup_action_advantage_mean": vine_metrics["setup_adv_mean"],
                    "vine/advantage_bias": vine_metrics["advantage_bias"],
                    "train/samples": valid_timesteps,
                    "train/sequence_chunks": len(sequence_chunks),
                    "train/mean_sequence_length": mean_sequence_length,
                    "train/max_sequence_length": max_sequence_length,
                    "train/padding_fraction": optimization_metrics["padding_fraction"],
                    "train/auxiliary_loss": optimization_metrics["auxiliary_loss"],
                    "train/sil_loss": optimization_metrics["sil_loss"],
                    "train/policy_loss": optimization_metrics["policy_loss"],
                    "train/value_loss": optimization_metrics["value_loss"],
                    "train/entropy": optimization_metrics["entropy"],
                    "train/mean_reward": mean_reward,
                    "train/mean_damage": mean_damage,
                    "train/mean_episode_steps": mean_episode_steps,
                    "train/mean_damage_delta": mean_damage_delta,
                    "train/completed_episodes": completed_episodes,
                    "train/max_damage": max_damage,
                    "train/min_damage": min_damage,
                    "train/max_episode_steps": max_episode_steps,
                    "train/min_episode_steps": min_episode_steps,
                    "train/invalid_action_rate": invalid_rate,
                    "train/valid_action_rate": valid_action_rate,
                    "train/approx_kl": optimization_metrics["approx_kl"],
                    "train/clip_fraction": optimization_metrics["clip_fraction"],
                    "train/value_mean": optimization_metrics["value_mean"],
                    "train/log_prob_mean": optimization_metrics["log_prob_mean"],
                    "train/entropy_coefficient": entropy_coefficient,
                    "train/rnd_intrinsic_reward_mean": rnd_intrinsic_reward_mean,
                    "perf/env_steps_per_second": env_steps_per_second,
                    "perf/samples_per_second": samples_per_second,
                    "perf/rollout_duration_sec": rollout_duration,
                    "perf/optimization_duration_sec": optimization_duration,
                    **{
                        f"train/attention_slot_{index}": score
                        for index, score in enumerate(mean_attention_scores)
                    },
                },
                step=update,
            )

            if update % config["evaluation_interval"] == 0 or update == config["updates"]:
                deterministic_summary = evaluate(policy, client, device, deterministic=True)
                stochastic_summary = evaluate(policy, client, device, deterministic=False)
                print(
                    f"  Eval det: reward={deterministic_summary['reward']:.3f} damage={deterministic_summary['damage']:,.0f} steps={deterministic_summary['steps']} invalid={deterministic_summary['invalid_actions']}"
                )
                print(
                    f"  Eval stoch: reward={stochastic_summary['reward']:.3f} damage={stochastic_summary['damage']:,.0f} steps={stochastic_summary['steps']} invalid={stochastic_summary['invalid_actions']}"
                )
                log_row.update(
                    {
                        "eval_det_reward": deterministic_summary["reward"],
                        "eval_det_damage": deterministic_summary["damage"],
                        "eval_det_steps": deterministic_summary["steps"],
                        "eval_det_invalid_actions": deterministic_summary["invalid_actions"],
                        "eval_stochastic_reward": stochastic_summary["reward"],
                        "eval_stochastic_damage": stochastic_summary["damage"],
                        "eval_stochastic_steps": stochastic_summary["steps"],
                        "eval_stochastic_invalid_actions": stochastic_summary["invalid_actions"],
                    }
                )
                log_wandb(
                    run,
                    flatten_eval_metrics("eval_det", deterministic_summary),
                    step=update,
                )
                log_wandb(
                    run,
                    flatten_eval_metrics("eval_stochastic", stochastic_summary),
                    step=update,
                )

            if update % config["checkpoint_interval"] == 0 or update == config["updates"]:
                policy.save(MODEL_PATH, optimizer, extra_state={"update": update})
            append_log(log_row)
    finally:
        client.close_runner(runner_id)
        client.close()
        if owns_run:
            finish_wandb(run)

    print(f"Saved checkpoint to {MODEL_PATH}")
    print(f"Saved training log to {TRAIN_LOG_PATH}")


def parse_args():
    parser = argparse.ArgumentParser(description="Train the PyTorch recurrent PPO learner against the Java rollout service.")
    parser.add_argument("--preset", choices=sorted(PRESETS.keys()), default="debug", help="training preset to use")
    parser.add_argument("--seed", type=int, default=1234, help="random seed")
    parser.add_argument("--host", default="127.0.0.1", help="rollout service host")
    parser.add_argument("--port", type=int, default=5005, help="rollout service port")
    parser.add_argument("--ports", default=None, help="comma-separated rollout service ports for multi-service fan-out")
    parser.add_argument("--endpoints", default=None, help="comma-separated rollout service host:port endpoints for multi-node fan-out")
    parser.add_argument("--updates", type=int, help="number of PPO updates")
    parser.add_argument("--rollout-length", type=int, help="steps collected per environment before each PPO update")
    parser.add_argument("--envs", type=int, help="number of vectorized environments")
    parser.add_argument("--hidden-size", type=int, help="recurrent hidden size")
    parser.add_argument("--ppo-epochs", type=int, help="number of PPO epochs per update")
    parser.add_argument("--sequence-length", type=int, help="sequence length used for truncated BPTT PPO updates")
    parser.add_argument(
        "--sequence-minibatch-size",
        type=int,
        help="number of sequence chunks per PPO minibatch",
    )
    parser.add_argument(
        "--minibatch-size",
        type=int,
        help="legacy alias for --sequence-minibatch-size",
    )
    parser.add_argument("--gamma", type=float, help="discount factor")
    parser.add_argument("--gae-lambda", type=float, help="GAE lambda")
    parser.add_argument("--clip-range", type=float, help="PPO clipping range")
    parser.add_argument("--learning-rate", type=float, help="Adam learning rate")
    parser.add_argument("--value-coefficient", type=float, help="value loss coefficient")
    parser.add_argument("--entropy-coefficient", type=float, help="starting entropy bonus coefficient")
    parser.add_argument("--entropy-final-coefficient", type=float, help="final entropy bonus coefficient")
    parser.add_argument("--max-grad-norm", type=float, help="gradient clipping max norm")
    parser.add_argument("--checkpoint-interval", type=int, help="checkpoint save interval in updates")
    parser.add_argument("--evaluation-interval", type=int, help="evaluation interval in updates")
    parser.add_argument("--auxiliary-prediction-weight", type=float, help="weight for auxiliary privileged-state prediction loss")
    parser.add_argument("--sil-loss-weight", type=float, default=None, help="weight for self-imitation learning loss")
    parser.add_argument("--sil-buffer-size-per-party", type=int, default=None, help="top-K episodes to keep per party in SIL buffer")
    parser.add_argument("--sil-min-episodes-before-ready", type=int, default=None, help="minimum episode inserts before SIL activates")
    parser.add_argument("--rnd-intrinsic-weight", type=float, default=None, help="weight for RND intrinsic reward (0.0 disables RND)")
    parser.add_argument("--rnd-embedding-size", type=int, default=None, help="embedding size for RND target/predictor networks")
    parser.add_argument("--use-vine-ppo", action="store_true", default=False, help="enable VinePPO Monte Carlo credit assignment")
    parser.add_argument("--vine-branch-count", type=int, default=None, help="number of MC branches per vine sample point K")
    parser.add_argument("--vine-horizon", type=int, default=None, help="horizon steps per vine branch H")
    parser.add_argument("--vine-max-points", type=int, default=None, help="max vine sample points per update (sub-sampling limit)")
    parser.add_argument("--rollout-workers", type=int, default=None, help="Java rollout worker override used for this run")
    parser.add_argument("--resume-from", default=None, help="path to a saved .pt checkpoint to resume from")
    parser.add_argument("--wandb", action="store_true", help="enable Weights & Biases logging")
    parser.add_argument("--wandb-project", default="genshin-recurrent-ppo", help="Weights & Biases project name")
    parser.add_argument("--wandb-entity", default=None, help="Weights & Biases entity/team name")
    parser.add_argument("--wandb-run-name", default=None, help="Weights & Biases run name override")
    parser.add_argument("--wandb-group", default=None, help="Weights & Biases run group")
    parser.add_argument("--wandb-mode", choices=("online", "offline", "disabled"), default="online", help="Weights & Biases mode")
    return parser.parse_args()


def resolve_config(args):
    config = dict(PRESETS[args.preset])
    overrides = {
        "updates": args.updates,
        "rollout_length": args.rollout_length,
        "envs": args.envs,
        "hidden_size": args.hidden_size,
        "ppo_epochs": args.ppo_epochs,
        "sequence_length": args.sequence_length,
        "sequence_minibatch_size": args.sequence_minibatch_size,
        "gamma": args.gamma,
        "gae_lambda": args.gae_lambda,
        "clip_range": args.clip_range,
        "learning_rate": args.learning_rate,
        "value_coefficient": args.value_coefficient,
        "entropy_coefficient": args.entropy_coefficient,
        "entropy_final_coefficient": args.entropy_final_coefficient,
        "max_grad_norm": args.max_grad_norm,
        "checkpoint_interval": args.checkpoint_interval,
        "evaluation_interval": args.evaluation_interval,
        "auxiliary_prediction_weight": args.auxiliary_prediction_weight,
        "sil_loss_weight": args.sil_loss_weight,
        "sil_buffer_size_per_party": args.sil_buffer_size_per_party,
        "sil_min_episodes_before_ready": args.sil_min_episodes_before_ready,
        "rnd_intrinsic_weight": args.rnd_intrinsic_weight,
        "rnd_embedding_size": args.rnd_embedding_size,
        "vine_branch_count": args.vine_branch_count,
        "vine_horizon": args.vine_horizon,
        "vine_max_points_per_update": args.vine_max_points,
    }
    if getattr(args, "use_vine_ppo", False):
        overrides["use_vine_ppo"] = True
    for key, value in overrides.items():
        if value is not None:
            config[key] = value
    if args.minibatch_size is not None and args.sequence_minibatch_size is None:
        config["sequence_minibatch_size"] = args.minibatch_size
    if config["sequence_length"] <= 0:
        raise ValueError(f"sequence_length must be positive, got {config['sequence_length']}")
    if config["sequence_minibatch_size"] <= 0:
        raise ValueError(
            "sequence_minibatch_size must be positive, "
            f"got {config['sequence_minibatch_size']}"
        )
    return config


def scheduled_entropy_coefficient(config, update):
    start = config["entropy_coefficient"]
    end = config.get("entropy_final_coefficient", start)
    total_updates = max(1, config["updates"])
    progress = 0.0 if total_updates <= 1 else (update - 1) / (total_updates - 1)
    return start + (end - start) * progress


def init_wandb(args, config, client, device, existing_run=None):
    if not args.wandb and existing_run is None:
        return None
    if wandb is None:
        raise RuntimeError("wandb logging was requested, but the wandb package is not installed in the active Python environment.")
    run_config = {
        "preset": args.preset,
        "seed": args.seed,
        "host": args.host,
        "port": args.port,
        "ports": args.ports,
        "endpoints": args.endpoints,
        "rollout_workers": args.rollout_workers,
        "device": device.type,
        "observation_size": client.observation_size,
        "action_size": client.action_size,
        "privileged_observation_size": client.privileged_observation_size,
    }
    run_config.update(config)
    if existing_run is not None:
        existing_run.config.update(run_config, allow_val_change=True)
        return existing_run
    return wandb.init(
        project=args.wandb_project,
        entity=args.wandb_entity,
        name=args.wandb_run_name,
        group=args.wandb_group,
        mode=args.wandb_mode,
        config=run_config,
    )


def log_wandb(run, metrics, step):
    if run is not None:
        wandb.log(metrics, step=step)


def finish_wandb(run):
    if run is not None:
        wandb.finish()


def build_step_record(
    observation,
    privileged_observation,
    recurrent_input,
    action_mask,
    action,
    old_log_probability,
    value,
    reward,
    done,
):
    return {
        "observation": list(observation),
        "privileged_observation": list(privileged_observation),
        "recurrent_input": recurrent_input.detach().cpu().tolist(),
        "action_mask": list(action_mask),
        "action": action,
        "old_log_probability": old_log_probability,
        "value": value,
        "reward": reward,
        "done": done,
    }


def build_sequence_chunks(segments, sequence_length):
    chunks = []
    for segment in segments:
        steps = segment["steps"]
        for start in range(0, len(steps), sequence_length):
            chunk_steps = steps[start:start + sequence_length]
            if not chunk_steps:
                continue
            chunks.append(
                {
                    "initial_hidden": chunk_steps[0]["recurrent_input"],
                    "steps": chunk_steps,
                }
            )
    return chunks


def build_sequence_minibatch(chunks, policy, device):
    batch_size = len(chunks)
    max_seq_len = max(len(chunk["steps"]) for chunk in chunks)
    observations = torch.zeros(
        batch_size, max_seq_len, policy.observation_size, dtype=torch.float32, device=device
    )
    privileged_observations = torch.zeros(
        batch_size, max_seq_len, policy.privileged_observation_size, dtype=torch.float32, device=device
    )
    initial_hidden = torch.zeros(
        batch_size, policy.hidden_size, dtype=torch.float32, device=device
    )
    action_masks = torch.ones(
        batch_size, max_seq_len, policy.action_size, dtype=torch.float32, device=device
    )
    actions = torch.zeros(batch_size, max_seq_len, dtype=torch.long, device=device)
    old_log_probabilities = torch.zeros(
        batch_size, max_seq_len, dtype=torch.float32, device=device
    )
    advantages = torch.zeros(batch_size, max_seq_len, dtype=torch.float32, device=device)
    return_targets = torch.zeros(batch_size, max_seq_len, dtype=torch.float32, device=device)
    loss_mask = torch.zeros(batch_size, max_seq_len, dtype=torch.float32, device=device)

    for batch_index, chunk in enumerate(chunks):
        initial_hidden[batch_index] = torch.tensor(
            chunk["initial_hidden"], dtype=torch.float32, device=device
        )
        for step_index, step in enumerate(chunk["steps"]):
            observations[batch_index, step_index] = torch.tensor(
                step["observation"], dtype=torch.float32, device=device
            )
            privileged_observations[batch_index, step_index] = torch.tensor(
                step["privileged_observation"], dtype=torch.float32, device=device
            )
            action_masks[batch_index, step_index] = torch.tensor(
                step["action_mask"], dtype=torch.float32, device=device
            )
            actions[batch_index, step_index] = step["action"]
            old_log_probabilities[batch_index, step_index] = step["old_log_probability"]
            advantages[batch_index, step_index] = step["advantage"]
            return_targets[batch_index, step_index] = step["return_target"]
            loss_mask[batch_index, step_index] = 1.0

    return {
        "observations": observations,
        "privileged_observations": privileged_observations,
        "initial_hidden": initial_hidden,
        "action_masks": action_masks,
        "actions": actions,
        "old_log_probabilities": old_log_probabilities,
        "advantages": advantages,
        "return_targets": return_targets,
        "loss_mask": loss_mask,
    }


def apply_vine_ppo_advantages(segments, config, client, runner_id):
    """Replace GAE advantages at SKILL/BURST/SWAP steps with VinePPO MC estimates.

    Uses A_VinePPO = Q_MC(s,a) - V_learned(s) where Q_MC is obtained by running
    K random rollouts after executing action a from snapshot state s.
    V_learned is the value estimate already in the step record from the policy network.

    Returns a dict of vine metrics: vine_points, setup_adv_mean, advantage_bias.
    """
    metrics = {"vine_points": 0, "setup_adv_mean": 0.0, "advantage_bias": 0.0}
    if not config.get("use_vine_ppo", False):
        return metrics

    K = config.get("vine_branch_count", 4)
    H = config.get("vine_horizon", 16)
    gamma = config["gamma"]
    max_points = config.get("vine_max_points_per_update", 64)

    vine_candidates = []
    for seg_idx, seg in enumerate(segments):
        for step_idx, step in enumerate(seg["steps"]):
            snap_id = step.get("vine_snapshot_id", -1)
            if snap_id >= 0:
                vine_candidates.append((seg_idx, step_idx, snap_id, step["action"]))

    if not vine_candidates:
        return metrics

    if len(vine_candidates) > max_points:
        vine_candidates = random.sample(vine_candidates, max_points)

    adv_list = []
    bias_list = []
    for seg_idx, step_idx, snap_id, action in vine_candidates:
        try:
            q_mc = client.branch_rollout(runner_id, snap_id, action, K, H, gamma)
        except Exception:
            continue
        step = segments[seg_idx]["steps"][step_idx]
        v_learned = step["value"]
        vine_adv = q_mc - v_learned
        gae_adv = step["advantage"]
        step["advantage"] = vine_adv
        adv_list.append(vine_adv)
        bias_list.append(abs(vine_adv - gae_adv))

    if adv_list:
        metrics["vine_points"] = len(adv_list)
        metrics["setup_adv_mean"] = float(np.mean(adv_list))
        metrics["advantage_bias"] = float(np.mean(bias_list))
    return metrics


def train_epoch(policy, optimizer, sequence_chunks, config, device, entropy_coefficient, rnd_module=None, rnd_optimizer=None):
    if not sequence_chunks:
        return {
            "policy_loss": 0.0,
            "value_loss": 0.0,
            "entropy": 0.0,
            "approx_kl": 0.0,
            "clip_fraction": 0.0,
            "value_mean": 0.0,
            "log_prob_mean": 0.0,
            "padding_fraction": 0.0,
            "auxiliary_loss": 0.0,
            "sil_loss": 0.0,
        }

    total_policy_loss = 0.0
    total_value_loss = 0.0
    total_entropy = 0.0
    total_approx_kl = 0.0
    total_clip_fraction = 0.0
    total_value_mean = 0.0
    total_log_prob_mean = 0.0
    total_valid_steps = 0.0
    total_padded_steps = 0.0
    total_auxiliary_loss = 0.0
    updates = 0

    for _ in range(config["ppo_epochs"]):
        chunk_indices = list(range(len(sequence_chunks)))
        random.shuffle(chunk_indices)
        for start in range(0, len(chunk_indices), config["sequence_minibatch_size"]):
            minibatch_indices = chunk_indices[start:start + config["sequence_minibatch_size"]]
            minibatch_chunks = [sequence_chunks[index] for index in minibatch_indices]
            minibatch = build_sequence_minibatch(minibatch_chunks, policy, device)

            logits, value, _, _, auxiliary_prediction = policy.forward_sequence(
                minibatch["observations"],
                minibatch["initial_hidden"],
                minibatch["action_masks"],
                privileged_observations=minibatch["privileged_observations"],
                sequence_mask=minibatch["loss_mask"],
            )
            distribution = torch.distributions.Categorical(logits=logits)
            new_log_probability = distribution.log_prob(minibatch["actions"])
            valid_mask = minibatch["loss_mask"]
            valid_count = valid_mask.sum().clamp_min(1.0)
            entropy = (distribution.entropy() * valid_mask).sum() / valid_count
            approx_kl = (
                (minibatch["old_log_probabilities"] - new_log_probability) * valid_mask
            ).sum() / valid_count

            ratio = torch.exp(new_log_probability - minibatch["old_log_probabilities"])
            clipped_ratio = torch.clamp(ratio, 1.0 - config["clip_range"], 1.0 + config["clip_range"])
            surrogate = ratio * minibatch["advantages"]
            clipped_surrogate = clipped_ratio * minibatch["advantages"]
            policy_loss = -(
                torch.minimum(surrogate, clipped_surrogate) * valid_mask
            ).sum() / valid_count
            value_loss = (
                0.5 * (minibatch["return_targets"] - value).pow(2) * valid_mask
            ).sum() / valid_count
            auxiliary_loss = (
                (auxiliary_prediction - minibatch["privileged_observations"]).pow(2)
                * valid_mask.unsqueeze(-1)
            ).sum() / (valid_count * policy.privileged_observation_size)
            total_loss = (
                policy_loss
                + config["value_coefficient"] * value_loss
                - entropy_coefficient * entropy
                + config["auxiliary_prediction_weight"] * auxiliary_loss
            )
            clip_fraction = (
                ((torch.abs(ratio - 1.0) > config["clip_range"]).float() * valid_mask).sum() / valid_count
            )
            value_mean = (value * valid_mask).sum() / valid_count
            log_prob_mean = (new_log_probability * valid_mask).sum() / valid_count

            optimizer.zero_grad()
            total_loss.backward()
            torch.nn.utils.clip_grad_norm_(policy.parameters(), config["max_grad_norm"])
            optimizer.step()

            total_policy_loss += policy_loss.item()
            total_value_loss += value_loss.item()
            total_entropy += entropy.item()
            total_approx_kl += approx_kl.item()
            total_clip_fraction += clip_fraction.item()
            total_value_mean += value_mean.item()
            total_log_prob_mean += log_prob_mean.item()
            total_valid_steps += valid_count.item()
            total_padded_steps += valid_mask.numel() - valid_count.item()
            total_auxiliary_loss += auxiliary_loss.item()
            updates += 1

    if rnd_module is not None and rnd_optimizer is not None and sequence_chunks:
        all_obs = []
        for chunk in sequence_chunks:
            for step in chunk["steps"]:
                all_obs.append(step["observation"])
        obs_tensor = torch.tensor(all_obs, dtype=torch.float32, device=device)
        rnd_loss = rnd_module.loss(obs_tensor)
        rnd_optimizer.zero_grad()
        rnd_loss.backward()
        rnd_optimizer.step()

    total_positions = total_valid_steps + total_padded_steps
    return {
        "policy_loss": total_policy_loss / updates,
        "value_loss": total_value_loss / updates,
        "entropy": total_entropy / updates,
        "approx_kl": total_approx_kl / updates,
        "clip_fraction": total_clip_fraction / updates,
        "value_mean": total_value_mean / updates,
        "log_prob_mean": total_log_prob_mean / updates,
        "padding_fraction": 0.0 if total_positions == 0 else total_padded_steps / total_positions,
        "auxiliary_loss": total_auxiliary_loss / updates,
        "sil_loss": 0.0,
    }


def flatten_eval_metrics(prefix, summary):
    metrics = {
        f"{prefix}/reward": summary["reward"],
        f"{prefix}/damage": summary["damage"],
        f"{prefix}/steps": summary["steps"],
        f"{prefix}/invalid_actions": summary["invalid_actions"],
        f"{prefix}/mean_top_probability": summary["mean_top_probability"],
    }
    for action_index, fraction in enumerate(summary["action_fractions"]):
        metrics[f"{prefix}/action_fraction_{action_index}"] = fraction
    for slot_index, score in enumerate(summary.get("mean_attention_scores", [])):
        metrics[f"{prefix}/attention_slot_{slot_index}"] = score
    for party_name, party_summary in summary.get("per_party", {}).items():
        party_prefix = f"{prefix}/party_{slugify(party_name)}"
        metrics[f"{party_prefix}/reward"] = party_summary["reward"]
        metrics[f"{party_prefix}/damage"] = party_summary["damage"]
        metrics[f"{party_prefix}/steps"] = party_summary["steps"]
        metrics[f"{party_prefix}/invalid_actions"] = party_summary["invalid_actions"]
        metrics[f"{party_prefix}/mean_top_probability"] = party_summary["mean_top_probability"]
        for action_index, fraction in enumerate(party_summary["action_fractions"]):
            metrics[f"{party_prefix}/action_fraction_{action_index}"] = fraction
        for slot_index, score in enumerate(party_summary.get("mean_attention_scores", [])):
            metrics[f"{party_prefix}/attention_slot_{slot_index}"] = score
    return metrics


def evaluate(policy, client, device, deterministic):
    if len(client.party_names) > 1:
        per_party = {}
        aggregate_reward = 0.0
        aggregate_damage = 0.0
        aggregate_steps = 0.0
        aggregate_invalid_actions = 0.0
        aggregate_top_probability = 0.0
        aggregate_action_fractions = [0.0 for _ in range(policy.action_size)]
        aggregate_attention_scores = [0.0 for _ in range(policy.num_chars)]
        for party_id, party_name in enumerate(client.party_names):
            summary = evaluate_single_episode(
                policy, client, device, deterministic, generate_report=False, forced_party_id=party_id
            )
            per_party[party_name] = summary
            aggregate_reward += summary["reward"]
            aggregate_damage += summary["damage"]
            aggregate_steps += summary["steps"]
            aggregate_invalid_actions += summary["invalid_actions"]
            aggregate_top_probability += summary["mean_top_probability"]
            for index, fraction in enumerate(summary["action_fractions"]):
                aggregate_action_fractions[index] += fraction
            for index, score in enumerate(summary["mean_attention_scores"]):
                aggregate_attention_scores[index] += score
        party_count = len(client.party_names)
        return {
            "reward": aggregate_reward / party_count,
            "damage": aggregate_damage / party_count,
            "steps": aggregate_steps / party_count,
            "invalid_actions": aggregate_invalid_actions / party_count,
            "mean_top_probability": aggregate_top_probability / party_count,
            "action_fractions": [value / party_count for value in aggregate_action_fractions],
            "mean_attention_scores": [value / party_count for value in aggregate_attention_scores],
            "per_party": per_party,
        }
    return evaluate_single_episode(policy, client, device, deterministic, generate_report=False, forced_party_id=-1)


def evaluate_single_episode(policy, client, device, deterministic, generate_report, forced_party_id):
    runner_id = client.create_runner(1)
    observations, _privileged_observations, masks, party_ids = client.reset_runner(
        runner_id, generate_report, forced_party_id=forced_party_id
    )
    hidden = torch.zeros(1, policy.hidden_size, dtype=torch.float32, device=device)
    total_reward = 0.0
    invalid_actions = 0
    steps = 0
    damage = 0.0
    top_probability_sum = 0.0
    action_counts = [0 for _ in range(policy.action_size)]
    attention_score_sum = torch.zeros(policy.num_chars, dtype=torch.float64)
    try:
        while True:
            obs_tensor = torch.tensor(observations, dtype=torch.float32, device=device)
            mask_tensor = torch.tensor(masks, dtype=torch.float32, device=device)
            with torch.no_grad():
                action_output = policy.act(obs_tensor, hidden, mask_tensor, deterministic=deterministic)
            attention_score_sum += action_output["attention_scores"].detach().cpu().sum(dim=0).to(torch.float64)
            action = [int(action_output["action"][0].item())]
            action_counts[action[0]] += 1
            top_probability_sum += action_output["top_probability"][0].item()
            batch = client.step_runner(runner_id, action)
            total_reward += batch["rewards"][0]
            if not batch["valid_actions"][0]:
                invalid_actions += 1
            steps = batch["episode_steps"][0] if batch["dones"][0] else batch["live_steps"][0]
            damage = batch["episode_damages"][0] if batch["dones"][0] else batch["total_damages"][0]
            if batch["dones"][0]:
                break
            observations = batch["observations"]
            masks = batch["action_masks"]
            hidden = action_output["hidden"]
    finally:
        client.close_runner(runner_id)
    total_actions = max(1, sum(action_counts))
    mean_attention_scores = (attention_score_sum / max(1, steps)).tolist()
    return {
        "reward": total_reward,
        "damage": damage,
        "steps": steps,
        "invalid_actions": invalid_actions,
        "mean_top_probability": top_probability_sum / max(1, steps),
        "action_fractions": [count / total_actions for count in action_counts],
        "mean_attention_scores": mean_attention_scores,
        "party_id": party_ids[0] if party_ids else forced_party_id,
        "party_name": client.party_names[party_ids[0]] if party_ids else client.party_names[forced_party_id],
    }


def slugify(value):
    return value.lower().replace(" ", "_")


def append_log(row):
    file_exists = os.path.exists(TRAIN_LOG_PATH)
    with open(TRAIN_LOG_PATH, "a", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=[
                "update",
                "samples",
                "sequence_chunks",
                "mean_sequence_length",
                "max_sequence_length",
                "padding_fraction",
                "auxiliary_loss",
                "sil_loss",
                "sil_buffer_size",
                "policy_loss",
                "value_loss",
                "entropy",
                "mean_reward",
                "mean_damage",
                "mean_episode_steps",
                "mean_damage_delta",
                "completed_episodes",
                "max_damage",
                "min_damage",
                "max_episode_steps",
                "min_episode_steps",
                "invalid_action_rate",
                "valid_action_rate",
                "approx_kl",
                "clip_fraction",
                "value_mean",
                "log_prob_mean",
                "entropy_coefficient",
                "rnd_intrinsic_reward_mean",
                "env_steps_per_second",
                "samples_per_second",
                "rollout_duration_sec",
                "optimization_duration_sec",
                "eval_det_reward",
                "eval_det_damage",
                "eval_det_steps",
                "eval_det_invalid_actions",
                "eval_stochastic_reward",
                "eval_stochastic_damage",
                "eval_stochastic_steps",
                "eval_stochastic_invalid_actions",
            ],
            extrasaction="ignore",
        )
        if not file_exists:
            writer.writeheader()
        writer.writerow(row)


if __name__ == "__main__":
    main()
