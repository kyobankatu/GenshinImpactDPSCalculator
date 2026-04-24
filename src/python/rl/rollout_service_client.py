import socket

from binary_protocol import (
    CMD_CLOSE_RUNNER,
    CMD_CREATE_RUNNER,
    CMD_HELLO,
    CMD_RESET_RUNNER,
    CMD_SHUTDOWN,
    CMD_STEP_RUNNER,
    VERSION,
    recv_bool,
    recv_bools,
    recv_doubles,
    recv_int,
    recv_ints,
    send_bool,
    send_int,
    send_ints,
)


class RolloutServiceClient:
    def __init__(self, host="127.0.0.1", port=5005):
        self.sock = socket.create_connection((host, port))
        self.version = None
        self.observation_size = None
        self.action_size = None
        self._hello()

    def _hello(self):
        send_int(self.sock, CMD_HELLO)
        self.version = recv_int(self.sock)
        self.observation_size = recv_int(self.sock)
        self.action_size = recv_int(self.sock)
        if self.version != VERSION:
            raise RuntimeError(f"Protocol version mismatch: java={self.version} python={VERSION}")

    def create_runner(self, env_count: int) -> int:
        send_int(self.sock, CMD_CREATE_RUNNER)
        send_int(self.sock, env_count)
        return recv_int(self.sock)

    def reset_runner(self, runner_id: int, generate_report: bool = False):
        send_int(self.sock, CMD_RESET_RUNNER)
        send_int(self.sock, runner_id)
        send_bool(self.sock, generate_report)
        env_count = recv_int(self.sock)
        obs_width = recv_int(self.sock)
        observations = self._recv_matrix(env_count, obs_width)
        mask_width = recv_int(self.sock)
        masks = self._recv_matrix(env_count, mask_width)
        return observations, masks

    def step_runner(self, runner_id: int, actions):
        send_int(self.sock, CMD_STEP_RUNNER)
        send_int(self.sock, runner_id)
        send_ints(self.sock, actions)

        env_count = recv_int(self.sock)
        obs_width = recv_int(self.sock)
        observations = self._recv_matrix(env_count, obs_width)
        mask_width = recv_int(self.sock)
        masks = self._recv_matrix(env_count, mask_width)
        rewards = recv_doubles(self.sock, env_count)
        dones = recv_bools(self.sock, env_count)
        valid_actions = recv_bools(self.sock, env_count)
        damage_deltas = recv_doubles(self.sock, env_count)
        total_damages = recv_doubles(self.sock, env_count)
        episode_rewards = recv_doubles(self.sock, env_count)
        episode_damages = recv_doubles(self.sock, env_count)
        episode_steps = recv_ints(self.sock, env_count)
        live_steps = recv_ints(self.sock, env_count)

        return {
            "observations": observations,
            "action_masks": masks,
            "rewards": rewards,
            "dones": dones,
            "valid_actions": valid_actions,
            "damage_deltas": damage_deltas,
            "total_damages": total_damages,
            "episode_rewards": episode_rewards,
            "episode_damages": episode_damages,
            "episode_steps": episode_steps,
            "live_steps": live_steps,
        }

    def close_runner(self, runner_id: int):
        send_int(self.sock, CMD_CLOSE_RUNNER)
        send_int(self.sock, runner_id)
        return recv_bool(self.sock)

    def shutdown_server(self):
        send_int(self.sock, CMD_SHUTDOWN)
        return recv_bool(self.sock)

    def close(self):
        self.sock.close()

    def _recv_matrix(self, rows: int, cols: int):
        flat = recv_doubles(self.sock, rows * cols)
        return [flat[i * cols:(i + 1) * cols] for i in range(rows)]
