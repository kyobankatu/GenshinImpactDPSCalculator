import random
import copy
import math

from expert_dataset import load_expert_dataset
from recurrent_ppo import PRIVILEGED_OBSERVATION_SIZE


class SILBuffer:
    """Per-party top-K episode buffer for self-imitation learning.

    Stores the highest-return episodes per party and exposes them for
    sampling during SIL training updates.  The buffer is keyed by
    party_id so that optimal rotations for different party compositions
    do not contaminate each other.

    Args:
        max_per_party: maximum number of episodes to keep per party
        min_episodes_before_ready: minimum total inserts before is_ready() is True
    """

    def __init__(self, max_per_party=16, min_episodes_before_ready=50):
        if max_per_party <= 0 or min_episodes_before_ready < 0:
            raise ValueError("SIL capacities must be positive or zero-ready")
        self._buffers = {}
        self.max_per_party = max_per_party
        self.min_episodes_before_ready = min_episodes_before_ready
        self._total_inserts = 0

    def try_insert(self, party_id, episode_return, sequence_chunks):
        """Insert episode if it improves the top-K buffer for this party.

        Args:
            party_id: integer party identifier
            episode_return: total undiscounted episode reward
            sequence_chunks: list of sequence chunks as produced by
                ``build_sequence_chunks``, each containing initial_hidden and steps

        Returns:
            True if the episode was inserted
        """
        if not isinstance(episode_return, (int, float)) or not math.isfinite(episode_return):
            raise ValueError("SIL episode return must be finite")
        if not sequence_chunks:
            raise ValueError("SIL episode requires sequence chunks")
        buf = self._buffers.setdefault(party_id, [])
        worst_return = buf[0][0] if buf else float("-inf")
        if len(buf) < self.max_per_party or episode_return > worst_return:
            if len(buf) >= self.max_per_party:
                buf.pop(0)
            buf.append((episode_return, sequence_chunks))
            buf.sort(key=lambda x: x[0])
            self._total_inserts += 1
            return True
        return False

    def sample_sequence_chunks(self, n):
        """Sample n sequence chunks proportional to episode return.

        Args:
            n: number of chunks to sample (with replacement if buffer is small)

        Returns:
            list of sampled sequence chunks, empty if buffer has no data
        """
        all_chunks = [
            (ret, chunk)
            for buf in self._buffers.values()
            for ret, segs in buf
            for chunk in segs
        ]
        if not all_chunks:
            return []
        min_ret = min(r for r, _ in all_chunks)
        weights = [r - min_ret + 1e-6 for r, _ in all_chunks]
        k = min(n, len(all_chunks))
        indices = random.choices(range(len(all_chunks)), weights=weights, k=k)
        return [all_chunks[i][1] for i in indices]

    def is_ready(self):
        """Returns True once enough episodes have been inserted to start SIL."""
        return self._total_inserts >= self.min_episodes_before_ready

    def size(self):
        """Total number of episodes currently stored across all parties."""
        return sum(len(buf) for buf in self._buffers.values())

    @property
    def total_inserts(self):
        return self._total_inserts

    def load_expert_dataset(self, path, sequence_length, hidden_size):
        """Load persistent top-K expert trajectories into party-local buffers."""
        if sequence_length <= 0 or hidden_size <= 0:
            raise ValueError("SIL sequence and hidden sizes must be positive")
        dataset = load_expert_dataset(path)
        training_records = [record for record in dataset.records if record.split == "train"]
        if not training_records:
            raise ValueError("SIL expert dataset has no training records")
        for record in training_records:
            chunks = []
            decisions = record.decisions
            for start in range(0, len(decisions), sequence_length):
                steps = []
                for index, decision in enumerate(
                    decisions[start : start + sequence_length]
                ):
                    steps.append(
                        {
                            "observation": list(decision.observation),
                            "privileged_observation": [0.0]
                            * PRIVILEGED_OBSERVATION_SIZE,
                            "recurrent_input": [0.0] * hidden_size,
                            "action_mask": list(decision.legal_action_mask),
                            "action": decision.action_id,
                            "old_log_probability": 0.0,
                            "value": 0.0,
                            "reward": 0.0,
                            "done": start + index == len(decisions) - 1,
                            "advantage": 1.0,
                            "return_target": float(
                                record.terminal_objective["objectiveScore"]
                            ),
                            "expert_policy_target": list(
                                decision.visit_policy_target
                            ),
                        }
                    )
                chunks.append(
                    {"initial_hidden": [0.0] * hidden_size, "steps": steps}
                )
            self.try_insert(
                record.party_name,
                float(record.terminal_objective["objectiveScore"]),
                chunks,
            )
        return dataset.source_hash

    def state_dict(self):
        """Return a weights-only-safe persistent buffer snapshot."""
        return {
            "max_per_party": self.max_per_party,
            "min_episodes_before_ready": self.min_episodes_before_ready,
            "total_inserts": self._total_inserts,
            "buffers": copy.deepcopy(self._buffers),
        }

    def load_state_dict(self, payload):
        """Restore a validated persistent buffer snapshot."""
        required = {
            "max_per_party",
            "min_episodes_before_ready",
            "total_inserts",
            "buffers",
        }
        if not isinstance(payload, dict) or set(payload) != required:
            raise ValueError("Malformed SIL buffer checkpoint")
        if payload["max_per_party"] != self.max_per_party:
            raise ValueError("SIL buffer capacity mismatch")
        if payload["min_episodes_before_ready"] != self.min_episodes_before_ready:
            raise ValueError("SIL readiness threshold mismatch")
        if not isinstance(payload["total_inserts"], int) or payload["total_inserts"] < 0:
            raise ValueError("Invalid SIL insertion count")
        if not isinstance(payload["buffers"], dict):
            raise ValueError("Invalid SIL party buffers")
        self._buffers = copy.deepcopy(payload["buffers"])
        self._total_inserts = payload["total_inserts"]
        for party_id, buffer in self._buffers.items():
            if len(buffer) > self.max_per_party:
                raise ValueError(f"SIL party buffer exceeds capacity: {party_id}")
            for episode_return, chunks in buffer:
                if not math.isfinite(episode_return) or not chunks:
                    raise ValueError("Invalid SIL checkpoint episode")
