import argparse
import csv
import os
import random
import time

import numpy as np
import torch

try:
    import wandb
except ImportError:
    wandb = None

from recurrent_ppo import RecurrentPolicy, compute_advantages
from rollout_service_client import build_rollout_client


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
        "minibatch_size": 32,
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
    },
}


def main():
    args = parse_args()
    preset = args.preset
    seed = args.seed
    host = args.host
    port = args.port
    ports = args.ports

    config = resolve_config(args)
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    client = build_rollout_client(host=host, port=port, ports=ports)
    runner_id = client.create_runner(config["envs"])
    observations, action_masks = client.reset_runner(runner_id, False)

    policy = RecurrentPolicy(client.observation_size, config["hidden_size"], client.action_size).to(device)
    optimizer = torch.optim.Adam(policy.parameters(), lr=config["learning_rate"])
    hidden_states = torch.zeros(config["envs"], config["hidden_size"], dtype=torch.float32, device=device)
    run = init_wandb(args, config, client, device)

    print(
        f"Starting Python Recurrent PPO training: preset={preset} updates={config['updates']} envs={config['envs']} rollout={config['rollout_length']} device={device.type}"
    )

    try:
        for update in range(1, config["updates"] + 1):
            entropy_coefficient = scheduled_entropy_coefficient(config, update)
            rollout_start = time.time()
            segments = [{"steps": [], "bootstrap_value": 0.0} for _ in range(config["envs"])]
            episode_rewards = []
            episode_damages = []
            episode_steps = []
            invalid_actions = 0
            damage_delta_sum = 0.0

            for _ in range(config["rollout_length"]):
                obs_tensor = torch.tensor(observations, dtype=torch.float32, device=device)
                mask_tensor = torch.tensor(action_masks, dtype=torch.float32, device=device)

                with torch.no_grad():
                    action_output = policy.act(obs_tensor, hidden_states, mask_tensor, deterministic=False)

                actions = action_output["action"].cpu().tolist()
                batch = client.step_runner(runner_id, actions)
                next_observations = batch["observations"]
                next_action_masks = batch["action_masks"]

                for env_index in range(config["envs"]):
                    segments[env_index]["steps"].append(
                        {
                            "observation": observations[env_index],
                            "recurrent_input": hidden_states[env_index].detach().cpu().tolist(),
                            "action_mask": action_masks[env_index],
                            "action": actions[env_index],
                            "old_log_probability": action_output["log_probability"][env_index].item(),
                            "value": action_output["value"][env_index].item(),
                            "reward": batch["rewards"][env_index],
                            "done": batch["dones"][env_index],
                        }
                    )
                    if not batch["valid_actions"][env_index]:
                        invalid_actions += 1
                    damage_delta_sum += batch["damage_deltas"][env_index]
                    if batch["dones"][env_index]:
                        episode_rewards.append(batch["episode_rewards"][env_index])
                        episode_damages.append(batch["episode_damages"][env_index])
                        episode_steps.append(batch["episode_steps"][env_index])
                        hidden_states[env_index].zero_()
                    else:
                        hidden_states[env_index] = action_output["hidden"][env_index]

                observations = next_observations
                action_masks = next_action_masks

            with torch.no_grad():
                obs_tensor = torch.tensor(observations, dtype=torch.float32, device=device)
                mask_tensor = torch.tensor(action_masks, dtype=torch.float32, device=device)
                logits, values, _ = policy.forward_step(obs_tensor, hidden_states, mask_tensor)
                del logits
                bootstrap_values = values.detach().cpu().tolist()
            for env_index in range(config["envs"]):
                segments[env_index]["bootstrap_value"] = bootstrap_values[env_index]

            samples = compute_advantages(segments, config["gamma"], config["gae_lambda"])
            optimization_start = time.time()
            policy_loss, value_loss, entropy, approx_kl, clip_fraction, value_mean, log_prob_mean = train_epoch(
                policy, optimizer, samples, config, device, entropy_coefficient
            )
            optimization_duration = max(1e-6, time.time() - optimization_start)

            duration = max(1e-6, time.time() - rollout_start)
            rollout_duration = max(1e-6, duration - optimization_duration)
            steps = config["envs"] * config["rollout_length"]
            env_steps_per_second = steps / duration
            samples_per_second = len(samples) / duration
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

            log_row = {
                "update": update,
                "samples": len(samples),
                "policy_loss": policy_loss,
                "value_loss": value_loss,
                "entropy": entropy,
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
                "approx_kl": approx_kl,
                "clip_fraction": clip_fraction,
                "value_mean": value_mean,
                "log_prob_mean": log_prob_mean,
                "entropy_coefficient": entropy_coefficient,
                "env_steps_per_second": env_steps_per_second,
                "samples_per_second": samples_per_second,
                "rollout_duration_sec": rollout_duration,
                "optimization_duration_sec": optimization_duration,
            }
            print(
                f"Update {update}: reward={mean_reward:.3f} damage={mean_damage:,.0f} steps={mean_episode_steps:.1f} invalid={invalid_rate:.3f} kl={approx_kl:.5f} clip={clip_fraction:.3f} entropyCoef={entropy_coefficient:.5f} policy={policy_loss:.5f} value={value_loss:.5f} envSteps/s={env_steps_per_second:.1f}"
            )
            log_wandb(
                run,
                {
                    "train/update": update,
                    "train/samples": len(samples),
                    "train/policy_loss": policy_loss,
                    "train/value_loss": value_loss,
                    "train/entropy": entropy,
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
                    "train/approx_kl": approx_kl,
                    "train/clip_fraction": clip_fraction,
                    "train/value_mean": value_mean,
                    "train/log_prob_mean": log_prob_mean,
                    "train/entropy_coefficient": entropy_coefficient,
                    "perf/env_steps_per_second": env_steps_per_second,
                    "perf/samples_per_second": samples_per_second,
                    "perf/rollout_duration_sec": rollout_duration,
                    "perf/optimization_duration_sec": optimization_duration,
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
                policy.save(MODEL_PATH, optimizer)
            append_log(log_row)
    finally:
        client.close_runner(runner_id)
        client.close()
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
    parser.add_argument("--updates", type=int, help="number of PPO updates")
    parser.add_argument("--rollout-length", type=int, help="steps collected per environment before each PPO update")
    parser.add_argument("--envs", type=int, help="number of vectorized environments")
    parser.add_argument("--hidden-size", type=int, help="recurrent hidden size")
    parser.add_argument("--ppo-epochs", type=int, help="number of PPO epochs per update")
    parser.add_argument("--minibatch-size", type=int, help="minibatch size for PPO optimization")
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
    parser.add_argument("--wandb", action="store_true", help="enable Weights & Biases logging")
    parser.add_argument("--wandb-project", default="genshin-recurrent-ppo", help="Weights & Biases project name")
    parser.add_argument("--wandb-entity", default=None, help="Weights & Biases entity/team name")
    parser.add_argument("--wandb-run-name", default=None, help="Weights & Biases run name override")
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
        "minibatch_size": args.minibatch_size,
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
    }
    for key, value in overrides.items():
        if value is not None:
            config[key] = value
    return config


def scheduled_entropy_coefficient(config, update):
    start = config["entropy_coefficient"]
    end = config.get("entropy_final_coefficient", start)
    total_updates = max(1, config["updates"])
    progress = 0.0 if total_updates <= 1 else (update - 1) / (total_updates - 1)
    return start + (end - start) * progress


def init_wandb(args, config, client, device):
    if not args.wandb:
        return None
    if wandb is None:
        raise RuntimeError("wandb logging was requested, but the wandb package is not installed in the active Python environment.")
    run_config = {
        "preset": args.preset,
        "seed": args.seed,
        "host": args.host,
        "port": args.port,
        "device": device.type,
        "observation_size": client.observation_size,
        "action_size": client.action_size,
    }
    run_config.update(config)
    return wandb.init(
        project=args.wandb_project,
        entity=args.wandb_entity,
        name=args.wandb_run_name,
        mode=args.wandb_mode,
        config=run_config,
    )


def log_wandb(run, metrics, step):
    if run is not None:
        wandb.log(metrics, step=step)


def finish_wandb(run):
    if run is not None:
        wandb.finish()


def train_epoch(policy, optimizer, samples, config, device, entropy_coefficient):
    if not samples:
        return 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0

    total_policy_loss = 0.0
    total_value_loss = 0.0
    total_entropy = 0.0
    total_approx_kl = 0.0
    total_clip_fraction = 0.0
    total_value_mean = 0.0
    total_log_prob_mean = 0.0
    updates = 0

    for _ in range(config["ppo_epochs"]):
        random.shuffle(samples)
        for start in range(0, len(samples), config["minibatch_size"]):
            minibatch = samples[start:start + config["minibatch_size"]]
            observation = torch.tensor([sample["observation"] for sample in minibatch], dtype=torch.float32, device=device)
            recurrent_input = torch.tensor([sample["recurrent_input"] for sample in minibatch], dtype=torch.float32, device=device)
            action_mask = torch.tensor([sample["action_mask"] for sample in minibatch], dtype=torch.float32, device=device)
            action = torch.tensor([sample["action"] for sample in minibatch], dtype=torch.long, device=device)
            old_log_probability = torch.tensor(
                [sample["old_log_probability"] for sample in minibatch], dtype=torch.float32, device=device
            )
            advantage = torch.tensor([sample["advantage"] for sample in minibatch], dtype=torch.float32, device=device)
            return_target = torch.tensor([sample["return_target"] for sample in minibatch], dtype=torch.float32, device=device)

            logits, value, _ = policy.forward_step(observation, recurrent_input, action_mask)
            distribution = torch.distributions.Categorical(logits=logits)
            new_log_probability = distribution.log_prob(action)
            entropy = distribution.entropy().mean()
            approx_kl = (old_log_probability - new_log_probability).mean()

            ratio = torch.exp(new_log_probability - old_log_probability)
            clipped_ratio = torch.clamp(ratio, 1.0 - config["clip_range"], 1.0 + config["clip_range"])
            surrogate = ratio * advantage
            clipped_surrogate = clipped_ratio * advantage
            policy_loss = -torch.minimum(surrogate, clipped_surrogate).mean()
            value_loss = 0.5 * (return_target - value).pow(2).mean()
            total_loss = policy_loss + config["value_coefficient"] * value_loss - entropy_coefficient * entropy
            clip_fraction = (torch.abs(ratio - 1.0) > config["clip_range"]).float().mean()
            value_mean = value.mean()
            log_prob_mean = new_log_probability.mean()

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
            updates += 1

    return (
        total_policy_loss / updates,
        total_value_loss / updates,
        total_entropy / updates,
        total_approx_kl / updates,
        total_clip_fraction / updates,
        total_value_mean / updates,
        total_log_prob_mean / updates,
    )


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
    return metrics


def evaluate(policy, client, device, deterministic):
    runner_id = client.create_runner(1)
    observations, masks = client.reset_runner(runner_id, False)
    hidden = torch.zeros(1, policy.hidden_size, dtype=torch.float32, device=device)
    total_reward = 0.0
    invalid_actions = 0
    steps = 0
    damage = 0.0
    top_probability_sum = 0.0
    action_counts = [0 for _ in range(policy.action_size)]
    try:
        while True:
            obs_tensor = torch.tensor(observations, dtype=torch.float32, device=device)
            mask_tensor = torch.tensor(masks, dtype=torch.float32, device=device)
            with torch.no_grad():
                action_output = policy.act(obs_tensor, hidden, mask_tensor, deterministic=deterministic)
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
    return {
        "reward": total_reward,
        "damage": damage,
        "steps": steps,
        "invalid_actions": invalid_actions,
        "mean_top_probability": top_probability_sum / max(1, steps),
        "action_fractions": [count / total_actions for count in action_counts],
    }


def append_log(row):
    file_exists = os.path.exists(TRAIN_LOG_PATH)
    with open(TRAIN_LOG_PATH, "a", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=[
                "update",
                "samples",
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
        )
        if not file_exists:
            writer.writeheader()
        writer.writerow(row)


if __name__ == "__main__":
    main()
