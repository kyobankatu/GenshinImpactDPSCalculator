---
name: diagnose-genshin-training
description: Triage recurrent PPO training and evaluation runs that behave wrong, using training log columns, W&B metrics, and service logs to separate reward collapse, entropy collapse, invalid-action growth, value scaling, sequence padding waste, throughput stalls, resume mismatch, VinePPO branching, RND weighting, and sweep anomalies. Use when a learning run looks broken rather than when RL code contracts change.
---

# Diagnose a Genshin training run

Read `.agents/skills/diagnose-genshin-training/SKILL.md` completely and follow it as the canonical project workflow. Resolve and read selected references from that canonical skill directory. Repository and nearest-package `AGENTS.md` instructions remain authoritative.
