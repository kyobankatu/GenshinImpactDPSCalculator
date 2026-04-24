import os
import sys

import torch

from recurrent_ppo import RecurrentPolicy
from rollout_service_client import RolloutServiceClient


MODEL_PATH = "output/recurrent_ppo_py/latest-model.pt"


def main():
    checkpoint = sys.argv[1] if len(sys.argv) > 1 else MODEL_PATH
    host = sys.argv[2] if len(sys.argv) > 2 else "127.0.0.1"
    port = int(sys.argv[3]) if len(sys.argv) > 3 else 5005

    if not os.path.exists(checkpoint):
        raise SystemExit(f"Checkpoint not found: {checkpoint}")

    client = RolloutServiceClient(host, port)
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    policy, _ = RecurrentPolicy.load(checkpoint, map_location=device)
    policy = policy.to(device)
    runner_id = client.create_runner(1)
    observations, masks = client.reset_runner(runner_id, True)
    hidden = torch.zeros(1, policy.hidden_size, dtype=torch.float32, device=device)
    total_reward = 0.0
    invalid_actions = 0
    try:
        while True:
            obs_tensor = torch.tensor(observations, dtype=torch.float32, device=device)
            mask_tensor = torch.tensor(masks, dtype=torch.float32, device=device)
            with torch.no_grad():
                inference = policy.act(obs_tensor, hidden, mask_tensor, deterministic=True)
            batch = client.step_runner(runner_id, [int(inference["action"][0].item())])
            total_reward += batch["rewards"][0]
            if not batch["valid_actions"][0]:
                invalid_actions += 1
            if batch["dones"][0]:
                print(
                    f"Python PPO evaluation complete: reward={total_reward:.3f} damage={batch['episode_damages'][0]:,.0f} steps={batch['episode_steps'][0]} invalid={invalid_actions}"
                )
                print("Generated output/rl_report.html")
                break
            observations = batch["observations"]
            masks = batch["action_masks"]
            hidden = inference["hidden"]
    finally:
        client.close_runner(runner_id)
        client.close()


if __name__ == "__main__":
    main()
