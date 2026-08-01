---
name: submit-genshin-gpu-job
description: Submit, monitor, reproduce, and clean up batch jobs on this machine's ybatch/Slurm cluster for RL training, evaluation, rollout services, and W&B sweep agents, using the untracked execute script family and its environment contract. Use for ybatch submission, YBATCH resource selection, job logs, checkpoint resume, sweep agents, or GPU allocation on this site.
---

# Submit a Genshin GPU job

1. Read [cluster-contract.md](references/cluster-contract.md), then the job script you intend to submit. The `execute*.sh` and `evaluate.sh` files are untracked, so read them from the working tree instead of assuming their contents. Use `operate-genshin-hpc` for site-agnostic topology planning; use this skill for the concrete submission path.
2. Confirm you are on the login host and that the working tree is at the revision you intend to run. Job scripts `cd` to the absolute repository path and build from whatever is checked out at start time.
3. Choose the resource line deliberately. `#YBATCH -r <partition>_<n>` expands into real `#SBATCH` directives, so changing `_<n>` changes GPU count and CPU tasks together. Match `JAVA_ROLLOUT_WORKERS` and `TRAIN_ENVS` to the allocation rather than inheriting a profile default blindly.
4. Never launch training, a sweep agent, or a long rollout service on the login host. Reserve direct `./gradlew` and `python3` invocations for bounded compile, unit-test, and regression checks.
5. Submit with `ybatch <script>`, capture the returned job ID immediately, and record resource, profile, party selection, and resume checkpoint alongside it. Verify the job appears in `squeue -u "$USER"` before treating it as queued.
6. Start with a `diagnosis` profile and a single party before a `full` run. Scale one dimension at a time: environments, rollout workers, party count, then GPUs.
7. Monitor the real files: the scheduler log at `logs/<job-name>.<job-id>` and the service log at `logs/rollout_service.<job-id>.<port>.log`. Queue delay never authorizes submitting a duplicate; reconcile with `squeue` first.
8. Treat resume as the default. `execute.sh` resumes from `output/recurrent_ppo_py/latest-model.pt` and extends the update budget, so verify the checkpoint's update count matches your intent before submitting, and pass `--no-resume` when a clean run is required.
9. Record revision, exact submission command, job ID, resource, profile, party selection, seed, resume state, W&B run name, terminal state, and whether retry is safe.

Never cancel a job you did not submit. Never delete `logs/` or `output/` while a job is running, and never commit the job scripts or their outputs.
