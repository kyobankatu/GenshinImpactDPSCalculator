import sys
import time

from rollout_service_client import RolloutServiceClient


def main():
    envs = int(sys.argv[1]) if len(sys.argv) > 1 else 4
    steps = int(sys.argv[2]) if len(sys.argv) > 2 else 128
    host = sys.argv[3] if len(sys.argv) > 3 else "127.0.0.1"
    port = int(sys.argv[4]) if len(sys.argv) > 4 else 5005

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


if __name__ == "__main__":
    main()
