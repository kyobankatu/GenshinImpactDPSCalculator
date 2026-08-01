# Cleanup boundaries

Usually generated or private: `build/`, `bin/`, `output/`, `.gradle/`, `.venv/`, `wandb/`, `sweeps/`, logs, profiler recordings, root generated reports, and training checkpoints. Presence alone does not make them stale.

Usually protected: `.git/`, `src/`, `config/`, `gradle/`, wrapper files, `AGENTS.md`, `README.md`, `TASKS.md`, tracked `.agents/skills`, tracked `.claude/skills`, scripts, committed `docs/`, and any active experiment checkpoint.

Always confirm with Git and live-process/job state. A tracked generated report is still protected unless the task explicitly authorizes updating or removing it.
