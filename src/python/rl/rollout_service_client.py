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


class MultiRolloutServiceClient:
    def __init__(self, host="127.0.0.1", ports=None):
        ports = ports or [5005]
        self.clients = [RolloutServiceClient(host, port) for port in ports]
        self.version = self.clients[0].version
        self.observation_size = self.clients[0].observation_size
        self.action_size = self.clients[0].action_size
        for client in self.clients[1:]:
            if client.version != self.version:
                raise RuntimeError("Protocol version mismatch across rollout services")
            if client.observation_size != self.observation_size or client.action_size != self.action_size:
                raise RuntimeError("Observation/action space mismatch across rollout services")

    def create_runner(self, env_count: int):
        shard_counts = split_evenly(env_count, len(self.clients))
        shards = []
        for client, shard_envs in zip(self.clients, shard_counts):
            if shard_envs <= 0:
                continue
            shards.append((client, client.create_runner(shard_envs), shard_envs))
        return shards

    def reset_runner(self, runner_handle, generate_report: bool = False):
        observations = []
        masks = []
        for shard_index, (client, runner_id, _shard_envs) in enumerate(runner_handle):
            shard_observations, shard_masks = client.reset_runner(runner_id, generate_report and shard_index == 0)
            observations.extend(shard_observations)
            masks.extend(shard_masks)
        return observations, masks

    def step_runner(self, runner_handle, actions):
        observations = []
        masks = []
        rewards = []
        dones = []
        valid_actions = []
        damage_deltas = []
        total_damages = []
        episode_rewards = []
        episode_damages = []
        episode_steps = []
        live_steps = []
        offset = 0
        for client, runner_id, shard_envs in runner_handle:
            shard_actions = actions[offset:offset + shard_envs]
            offset += shard_envs
            batch = client.step_runner(runner_id, shard_actions)
            observations.extend(batch["observations"])
            masks.extend(batch["action_masks"])
            rewards.extend(batch["rewards"])
            dones.extend(batch["dones"])
            valid_actions.extend(batch["valid_actions"])
            damage_deltas.extend(batch["damage_deltas"])
            total_damages.extend(batch["total_damages"])
            episode_rewards.extend(batch["episode_rewards"])
            episode_damages.extend(batch["episode_damages"])
            episode_steps.extend(batch["episode_steps"])
            live_steps.extend(batch["live_steps"])
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

    def close_runner(self, runner_handle):
        for client, runner_id, _shard_envs in runner_handle:
            client.close_runner(runner_id)
        return True

    def shutdown_server(self):
        for client in self.clients:
            client.shutdown_server()
        return True

    def close(self):
        for client in self.clients:
            client.close()


def split_evenly(total, buckets):
    base = total // buckets
    remainder = total % buckets
    return [base + (1 if index < remainder else 0) for index in range(buckets)]


def build_rollout_client(host="127.0.0.1", port=5005, ports=None):
    if ports:
        parsed_ports = [int(value.strip()) for value in ports.split(",") if value.strip()]
        if not parsed_ports:
            raise ValueError("ports argument did not contain any valid port numbers")
        if len(parsed_ports) == 1:
            return RolloutServiceClient(host, parsed_ports[0])
        return MultiRolloutServiceClient(host, parsed_ports)
    return RolloutServiceClient(host, port)
