import random


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
