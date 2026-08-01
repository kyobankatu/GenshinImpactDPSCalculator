# Local cluster contract

This describes the machine this repository is developed on. The job scripts themselves are ignored by Git
(`*.sh` and `execute.sh` in `.gitignore`), so this file is the only tracked record of how they are used.
Always re-read the actual script before submitting; treat the parameter tables here as orientation.

## Scheduler

Login host: `login-02.yokota`. Submission wrapper: `ybatch`, which sits in front of Slurm.

| Command | Purpose |
|---|---|
| `ybatch <script> [sbatch args]` | submit a job script containing `#YBATCH` directives |
| `squeue -u "$USER"` | list own queued and running jobs |
| `scancel <job-id>` | cancel own job |
| `sinfo` | partition availability and node states |
| `yokota-rns --batch <resource>` | print the `#SBATCH` lines a `#YBATCH -r` resource expands to |

`ybatch` rewrites the script before handing it to `sbatch`:

- `#YBATCH -r <resource>` expands to `--gres`, `--ntasks-per-node`, `-p`, and `--comment` lines.
- `#YBATCH -p low` adds `--nice=5000`; `#YBATCH -p verylow` adds `--nice=10000`.
- Any other `#YBATCH` option is an error.

Plain `#SBATCH` lines in the script are passed through, so `-N`, `-J`, `--time`, `--output`, and `--error`
stay under direct control.

## Resource names

`#YBATCH -r <partition>_<n>` where `<n>` scales the allocation. Verified expansions:

| Resource | GPUs | ntasks-per-node | Partition |
|---|---|---|---|
| `rtx6000-ada2_2` | 2 | 96 | `rtx6000-ada2` |
| `rtx6000-ada2_1` | 1 | 48 | `rtx6000-ada2` |
| `threadripper-3960x_1` | 0 | 6 | `threadripper-3960x` |
| `epyc-7502_8` | 0 | 64 | `epyc-7502` |

On GPU partitions `<n>` is the GPU count and CPU tasks scale with it. On CPU partitions `<n>` is a slot
multiplier. Confirm any unfamiliar resource with `yokota-rns --batch <resource>` before submitting.

Partitions seen in `sinfo`: `epyc-7502`, `threadripper-3960x`, `a4500`, `am`, `a6000`, `a100`, `am4`,
`rtx6000-ada`, `rtx6000-ada2`, `ad`, `h100`, `dgx-b200`, `rtx6000-bw`. Availability changes; read `sinfo`
rather than trusting this list. Default time limit is 7 days on most partitions and 1 day on `dgx-b200`.

## Job script family

All four live at the repository root and are untracked.

| Script | Resource | Role |
|---|---|---|
| `execute.sh` | `rtx6000-ada2_2` | single-node training: builds classes, refreshes capability profiles, starts one local `ServeRLJava`, then runs `train_recurrent_ppo.py` against it |
| `execute_learner.sh` | `rtx6000-ada2_1` | learner-only, connects to already-running remote rollout endpoints via `--endpoints` |
| `execute_rollout.sh` | `epyc-7502_8` | rollout-service-only, binds `0.0.0.0`, derives its port from the job ID, and publishes an endpoint file under `output/rl_endpoints/<tag>/` |
| `execute_sweep_agent.sh` | `rtx6000-ada2_2` | runs `wandb agent "$SWEEP_ID"`; requires `SWEEP_ID` in the environment or it exits |
| `evaluate.sh` | `threadripper-3960x_1` | starts a local rollout service and runs `evaluate_policy.py` against an existing checkpoint |

The split learner/rollout pair is the multi-node path: submit `execute_rollout.sh` first, read the published
endpoint files, then point the learner at them. `execute_learner.sh` currently carries hard-coded endpoints,
so edit it or use the Python entry point directly rather than assuming its defaults are live.

## Shared environment contract

Every job script performs the same setup. Reproduce it exactly for manual runs:

```
cd /home/katumon/develop/java/GenshinImpactDPSCalculator/
mkdir -p logs output/recurrent_ppo_py
source .venv/bin/activate
export JAVA_HOME=$HOME/develop/java/jdk/jdk-17.0.18+8
export PATH="$JAVA_HOME/bin:$PATH"
```

- Java is JDK 17 from `$HOME/develop/java/jdk/jdk-17.0.18+8`; the scripts abort if `$JAVA_HOME/bin/java` is
  not executable.
- Python is the in-tree `.venv` (3.14).
- `GRADLE_USER_HOME` is set per job, defaulting to `${TMPDIR:-$HOME/.gradle-user-home}/job-${SLURM_JOB_ID}`,
  so concurrent jobs do not share a Gradle cache. `execute_rollout.sh` deliberately shares
  `$HOME/.gradle-user-home` instead.
- A trap kills the background rollout service on exit; the service PID is checked a few seconds after start
  and the job fails fast if it already died.

## execute.sh parameters

Flags:

| Flag | Effect |
|---|---|
| `--use-vine-ppo` | enable VinePPO branching |
| `--no-resume` | ignore `latest-model.pt` and start fresh |
| `--no-wandb` | disable Weights & Biases logging |
| `--multi-party` | select the `default` multi-party catalog |
| `--value-quantile <v>` | value head quantile |
| `--policy-type <gru\|transformer>` | policy architecture, default `transformer` |
| `--rl-parties <names>` | explicit party selection, overrides `--multi-party` |
| `--role-alignment-bonus-weight <w>` | reward shaping weight |

Environment overrides read by the script include `TRAIN_PROFILE`, `TRAIN_SEED`, `TRAIN_UPDATES`,
`TRAIN_ENVS`, `TRAIN_ROLLOUT_LENGTH`, `TRAIN_SEQUENCE_LENGTH`, `TRAIN_SEQUENCE_MINIBATCH_SIZE`,
`TRAIN_HIDDEN_SIZE`, `TRAIN_PPO_EPOCHS`, `TRAIN_LEARNING_RATE`, `TRAIN_ENTROPY_COEFFICIENT`,
`TRAIN_ENTROPY_FINAL_COEFFICIENT`, `TRAIN_CHECKPOINT_INTERVAL`, `TRAIN_EVALUATION_INTERVAL`,
`JAVA_ROLLOUT_WORKERS`, `EPISODE_SECONDS`, `REFRESH_CAPABILITY_PROFILES`, `RESUME_CHECKPOINT`,
`ROLLOUT_BIND_HOST`, `WANDB_PROJECT`, `WANDB_ENTITY`, `WANDB_MODE`, `WANDB_GROUP`, `WANDB_RUN_NAME`.

Profiles:

| | `diagnosis` | `full` |
|---|---|---|
| updates | 200 | 4000 |
| envs | 16 | 2048 |
| rollout length | 32 | 32 |
| sequence length | 32 | 64 |
| sequence minibatch | 32 | 64 |
| Java rollout workers | 8 | 16 |
| checkpoint / eval interval | 10 | 25 |
| W&B run prefix | `recurrent_ppo_diag` | `recurrent_ppo_prod` |

Shared defaults: hidden size 256, 4 PPO epochs, gamma 0.99, GAE lambda 0.95, clip 0.20, learning rate 3e-4,
value coefficient 0.5, entropy 0.03 decaying to a floor of 0.01, max grad norm 0.5, episode 70 s,
auxiliary prediction weight 0.05, RND weight 0.0, policy `transformer`, party `FlinsParty2`.

Note that `execute.sh` passes `--preset debug` to the trainer and then overrides every hyperparameter
explicitly; the preset name is not the profile.

## Resume semantics

- Default `RESUME_TRAINING=true` with `RESUME_CHECKPOINT=output/recurrent_ppo_py/latest-model.pt`.
- When that file exists, the script reads its `update` field and **adds** the profile's update count on top,
  unless `TRAIN_UPDATES` was set explicitly. A resumed `full` run therefore targets
  `4000 + completed_updates`.
- Check the checkpoint's update count before submitting when the intended budget matters.
- `--no-resume` starts fresh but does not move the existing checkpoint; back it up first if it matters.

## Weights & Biases

Project `genshin-recurrent-ppo`, entity `katumon`, mode `online` by default. Run names are generated as
`<prefix>_<sp|mp>_e<envs>_w<workers>_<job-id>` when `WANDB_RUN_NAME` is unset. Sweeps require
`SWEEP_ID=<entity>/<project>/<id>` in the environment of `execute_sweep_agent.sh`, optionally with
`SWEEP_AGENT_COUNT`. Sweep definitions live under `sweeps/`, which is untracked. Never place an API key in a
tracked file, a commit, or a job script.

## Logs and outputs

| Path | Content |
|---|---|
| `logs/<job-name>.<job-id>` | combined stdout/stderr, since `--output` and `--error` point at the same file |
| `logs/rollout_service.<job-id>.<port>.log` | Java rollout service output |
| `output/recurrent_ppo_py/latest-model.pt` | resumable checkpoint |
| `output/recurrent_ppo_py/training_log.csv` | per-update metrics |
| `output/rl_report.html`, `output/rl_report_<party>.html` | deterministic evaluation reports |
| `output/rl_endpoints/<tag>/*.endpoint` | published `host:port` values from `execute_rollout.sh` |

Job names in use: `recurrent_ppo`, `recurrent_ppo_learner`, `recurrent_ppo_rollout`, `recurrent_sweep`,
`rl_evaluate`. `logs/` and `output/` are ignored by Git; keep them out of commits and out of cleanup while a
job is live.

## Local-only alternative

For checks that do not need a scheduler, run the bounded local path instead of a job:

```
./gradlew build
./gradlew ReactionRegressionTest
./gradlew BenchmarkRLJava
python -m pytest src/python/rl/tests
```

Starting `ServeRLJava` plus a short `--preset debug` training run on the login host is acceptable only as a
seconds-to-minutes smoke test, and must be stopped afterwards.
