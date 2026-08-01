---
name: diagnose-genshin-training
description: Triage recurrent PPO training and evaluation runs that behave wrong, using training log columns, W&B metrics, and service logs to separate reward collapse, entropy collapse, invalid-action growth, value scaling, sequence padding waste, throughput stalls, resume mismatch, VinePPO branching, RND weighting, and sweep anomalies. Use when a learning run looks broken rather than when RL code contracts change.
---

# Diagnose a Genshin training run

1. Read [training-triage.md](references/training-triage.md). Use `develop-genshin-rl` when a Java/Python contract must change; use this skill to find out what is actually wrong first.
2. Gather evidence before forming a hypothesis: `output/recurrent_ppo_py/training_log.csv`, the scheduler log `logs/<job-name>.<job-id>`, the service log `logs/rollout_service.<job-id>.<port>.log`, and the W&B run if one exists.
3. Establish the run's identity: revision, profile, party selection, seed, resume checkpoint and its update count, policy type, and whether VinePPO, RND, or SIL were enabled. A run compared against a different configuration is not a regression.
4. Ignore the first updates. Episode-scoped columns stay zero until episodes complete, so `completed_episodes`, `completed_episode_mean_reward`, and `completed_episode_mean_damage` are meaningless early in a run.
5. Localize before explaining. Decide whether the symptom lives in the environment (invalid actions, episode steps, damage), the update (approx KL, clip fraction, entropy, losses), the data path (sequence padding, throughput split), or the harness (resume, party metadata, W&B config).
6. Form one falsifiable hypothesis and test it with the cheapest reproduction: a `diagnosis` profile single-party run, a bounded local `--preset debug` run, or `evaluate_policy.py` against the saved checkpoint.
7. Separate simulator changes from learner changes. A simulator accuracy fix can invalidate an existing checkpoint behaviorally even when tensor shapes match; check whether the checkpoint predates the current revision.
8. Change one factor per follow-up run and keep the previous checkpoint. Never overwrite a checkpoint that is the only evidence of the behavior being diagnosed.
9. Report the symptom, the evidence with actual column values or log lines, the localized cause, what was ruled out, and the single next run to confirm.

Do not tune hyperparameters to hide a mechanic bug, and never conclude from one update or one seed.
