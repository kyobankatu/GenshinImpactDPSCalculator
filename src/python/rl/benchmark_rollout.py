import argparse
import time

from rollout_service_client import RolloutServiceClient


def main():
    args = parse_args()
    envs = args.envs
    steps = args.steps
    host = args.host
    port = args.port

    client = RolloutServiceClient(host, port)
    runner_id = client.create_runner(envs)
    observations, masks = client.reset_runner(runner_id, False)
    del observations, masks

    actions = [0 for _ in range(envs)]
    start = time.time()
    for step in range(steps):
        for idx in range(envs):
            actions[idx] = step % 3
        client.step_runner(runner_id, actions)
    duration = max(1e-6, time.time() - start)
    print(f"Python benchmark: envs={envs} steps={envs * steps} duration={duration:.3f}s envSteps/s={(envs * steps) / duration:.1f}")
    client.close_runner(runner_id)
    client.close()


def parse_args():
    parser = argparse.ArgumentParser(description="Benchmark Python-side rollout throughput against the Java rollout service.")
    parser.add_argument("--envs", type=int, default=4, help="number of vectorized environments")
    parser.add_argument("--steps", type=int, default=128, help="number of batched steps to execute")
    parser.add_argument("--host", default="127.0.0.1", help="rollout service host")
    parser.add_argument("--port", type=int, default=5005, help="rollout service port")
    return parser.parse_args()


if __name__ == "__main__":
    main()
