# Training triage

## Evidence sources

| Source | Contents |
|---|---|
| `output/recurrent_ppo_py/training_log.csv` | one row per update, all columns below |
| `output/recurrent_ppo_py/latest-model.pt` | checkpoint; its `update` field is the resume point |
| `logs/<job-name>.<job-id>` | scheduler stdout/stderr including the per-update summary line |
| `logs/rollout_service.<job-id>.<port>.log` | Java rollout service output; first place to look for service death |
| W&B run | same metrics under `train/`, `vine/`, and evaluation prefixes |
| `output/rl_report.html`, `output/rl_report_<party>.html` | deterministic evaluation reports |

The per-update stdout line carries the fastest summary: `completedEpReward`, `completedEpDamage`,
`roleAlign`, `steps`, `invalid`, `kl`, `clip`, `entropyCoef`, `policy`, `value`, `aux`, `seqs`, `meanSeq`,
`envSteps/s`.

## Log columns by concern

**Environment behavior**: `mean_reward`, `mean_damage`, `mean_damage_delta`, `completed_episode_mean_reward`,
`completed_episode_mean_damage`, `completed_episodes`, `mean_episode_steps`, `max_episode_steps`,
`min_episode_steps`, `max_damage`, `min_damage`, `invalid_action_rate`, `valid_action_rate`.

**Role shaping**: `mean_role_alignment`, `mean_carry_alignment`, `mean_off_field_alignment`,
`mean_entry_alignment`, `mean_stay_alignment`. These stay at zero when
`ROLE_ALIGNMENT_BONUS_WEIGHT` is 0.

**Update health**: `policy_loss`, `value_loss`, `entropy`, `entropy_coefficient`, `approx_kl`,
`clip_fraction`, `value_mean`, `log_prob_mean`, `auxiliary_loss`.

**Auxiliary objectives**: `sil_loss`, `sil_buffer_size`, `rnd_intrinsic_reward_mean`, and the W&B-only
`vine/points`, `vine/setup_action_advantage_mean`, `vine/advantage_bias`.

**Data path and throughput**: `samples`, `sequence_chunks`, `mean_sequence_length`, `max_sequence_length`,
`padding_fraction`, `env_steps_per_second`, `samples_per_second`, `rollout_duration_sec`,
`optimization_duration_sec`.

**Evaluation**: `eval_det_reward`, `eval_det_damage`, `eval_det_steps`, `eval_det_invalid_actions`, and the
`eval_stochastic_*` counterparts. These are populated only on evaluation-interval updates.

## Warm-up artifacts, not bugs

- Episode-scoped columns are zero until the first episodes finish. With a 70 s episode and a rollout length of
  32, expect several updates of zeros.
- `sil_buffer_size` is empty before the buffer fills.
- `eval_*` columns are blank between evaluation intervals.
- `value_loss` starts very large because returns are damage-scaled and unnormalized; judge its trend, not its
  magnitude.

## Symptom to first check

| Symptom | Look at | Common causes |
|---|---|---|
| Reward flat at zero past warm-up | `completed_episodes`, `mean_episode_steps` | episodes never terminate; episode seconds or rollout length mismatch |
| Reward collapses mid-run | `approx_kl`, `clip_fraction`, `entropy` | update too aggressive; learning rate or clip range; a simulator change altering reward scale |
| `invalid_action_rate` climbing | service log, action mask contract | mask and policy head disagree; party catalog or action size changed under an old checkpoint |
| `entropy` decaying to near zero early | `entropy_coefficient` | entropy floor too low; the script clamps the final coefficient to at least 0.01, so a lower request is silently raised |
| `approx_kl` spiking with high `clip_fraction` | learning rate, PPO epochs | too many epochs per batch, or sequence minibatch too small |
| `value_loss` diverging | `value_mean`, reward scale | damage-scaled returns plus a changed reward weight; check `mean_damage_delta` |
| Throughput far below expectation | `rollout_duration_sec` vs `optimization_duration_sec` | if rollout dominates, raise `JAVA_ROLLOUT_WORKERS` or environments; if optimization dominates, the learner is the bottleneck |
| `padding_fraction` high | `mean_sequence_length`, `max_sequence_length` | sequence length far above real episode chunks, wasting batch capacity |
| Resumed run behaves unlike its parent | checkpoint `update`, revision | resume adds the profile budget on top of completed updates; a simulator fix can invalidate the checkpoint behaviorally |
| Service dies at startup | `logs/rollout_service.*.log` | party name not in `RLPartyRegistry`, stale capability profiles, port already bound |
| Evaluation disagrees with training | `eval_det_*` vs stochastic | deterministic policy exposes a degenerate argmax that sampling hid |
| Sweep trials all fail identically | sweep agent log, `SWEEP_ID` | missing `SWEEP_ID`, wrong entity/project, or an invalid parameter range |

## Reproduction ladder

Use the cheapest step that can still show the symptom:

1. Inspect the existing log and checkpoint; no run at all.
2. `python src/python/rl/evaluate_policy.py --mode both --checkpoint output/recurrent_ppo_py/latest-model.pt`
   against a locally started `ServeRLJava`.
3. `./gradlew BenchmarkRLJava` when the suspicion is throughput or service health.
4. A bounded local run: `python3 src/python/rl/train_recurrent_ppo.py --preset debug --updates 20 --envs 4
   --rollout-length 32`.
5. A `diagnosis`-profile job with a single party.
6. A `full`-profile job only after the hypothesis is confirmed.

## Configuration confounders

Check these before calling anything a regression:

- party selection: single `FlinsParty2` versus the `default` multi-party catalog changes reward scale;
- `EPISODE_SECONDS`, which defaults to 70;
- `POLICY_TYPE`, `transformer` by default, `gru` alternative;
- `USE_VINE_PPO` and its branch count, horizon, and max points;
- `TRAIN_RND_INTRINSIC_WEIGHT`, `TRAIN_AUXILIARY_PREDICTION_WEIGHT`, `VALUE_QUANTILE`;
- `ROLE_ALIGNMENT_BONUS_WEIGHT`;
- stale `config/capability_profiles/profiles.json` when parties changed;
- seed, which defaults to 1234, so identical-looking runs may not be independent samples.

## Checkpoint safety

- Copy `latest-model.pt` before any run that could overwrite it when it is the only evidence of a behavior.
- `--no-resume` starts fresh without moving the existing file.
- Never delete `output/` or `logs/` content while a job is running.

## Reporting

State the symptom, the exact columns or log lines that evidence it, the configuration of the affected run and
of any run it is being compared against, the localized cause, hypotheses ruled out, and the single next run
that would confirm the diagnosis.
