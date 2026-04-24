import argparse
import os

import torch

from recurrent_ppo import RecurrentPolicy
from rollout_service_client import build_rollout_client


MODEL_PATH = "output/recurrent_ppo_py/latest-model.pt"


def main():
    args = parse_args()
    checkpoint = args.checkpoint
    host = args.host
    port = args.port
    ports = args.ports
    mode = args.mode

    if not os.path.exists(checkpoint):
        raise SystemExit(f"Checkpoint not found: {checkpoint}")

    client = build_rollout_client(host=host, port=port, ports=ports)
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    policy, _ = RecurrentPolicy.load(checkpoint, map_location=device)
    policy = policy.to(device)
    try:
        if mode in ("deterministic", "both"):
            summary = run_evaluation(policy, client, device, deterministic=True, generate_report=True)
            print(
                f"Python PPO deterministic evaluation: reward={summary['reward']:.3f} damage={summary['damage']:,.0f} steps={summary['steps']} invalid={summary['invalid_actions']} topProb={summary['mean_top_probability']:.3f}"
            )
            print("Generated output/rl_report.html")
        if mode in ("stochastic", "both"):
            summary = run_evaluation(policy, client, device, deterministic=False, generate_report=False)
            print(
                f"Python PPO stochastic evaluation: reward={summary['reward']:.3f} damage={summary['damage']:,.0f} steps={summary['steps']} invalid={summary['invalid_actions']} topProb={summary['mean_top_probability']:.3f}"
            )
    finally:
        client.close()


def parse_args():
    parser = argparse.ArgumentParser(description="Evaluate a saved PyTorch recurrent PPO checkpoint against the Java rollout service.")
    parser.add_argument("--checkpoint", default=MODEL_PATH, help="path to the saved .pt checkpoint")
    parser.add_argument("--host", default="127.0.0.1", help="rollout service host")
    parser.add_argument("--port", type=int, default=5005, help="rollout service port")
    parser.add_argument("--ports", default=None, help="comma-separated rollout service ports for multi-service fan-out")
    parser.add_argument("--mode", choices=("deterministic", "stochastic", "both"), default="both", help="evaluation mode")
    return parser.parse_args()


def run_evaluation(policy, client, device, deterministic, generate_report):
    runner_id = client.create_runner(1)
    observations, masks = client.reset_runner(runner_id, generate_report)
    hidden = torch.zeros(1, policy.hidden_size, dtype=torch.float32, device=device)
    total_reward = 0.0
    invalid_actions = 0
    top_probability_sum = 0.0
    action_counts = [0 for _ in range(policy.action_size)]
    try:
        while True:
            obs_tensor = torch.tensor(observations, dtype=torch.float32, device=device)
            mask_tensor = torch.tensor(masks, dtype=torch.float32, device=device)
            with torch.no_grad():
                inference = policy.act(obs_tensor, hidden, mask_tensor, deterministic=deterministic)
            action = int(inference["action"][0].item())
            action_counts[action] += 1
            top_probability_sum += inference["top_probability"][0].item()
            batch = client.step_runner(runner_id, [action])
            total_reward += batch["rewards"][0]
            if not batch["valid_actions"][0]:
                invalid_actions += 1
            if batch["dones"][0]:
                steps = batch["episode_steps"][0]
                return {
                    "reward": total_reward,
                    "damage": batch["episode_damages"][0],
                    "steps": steps,
                    "invalid_actions": invalid_actions,
                    "mean_top_probability": top_probability_sum / max(1, steps),
                    "action_fractions": [count / max(1, sum(action_counts)) for count in action_counts],
                }
            observations = batch["observations"]
            masks = batch["action_masks"]
            hidden = inference["hidden"]
    finally:
        client.close_runner(runner_id)


if __name__ == "__main__":
    main()
