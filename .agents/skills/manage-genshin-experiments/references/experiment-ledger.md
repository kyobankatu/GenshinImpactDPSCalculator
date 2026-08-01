# Experiment ledger fields

- stable experiment ID and status: planned, running, validating, complete, failed, blocked;
- full Git revision and dirty-tree description;
- objective, hypothesis, workload, party catalog, and correctness tolerance;
- exact command, seed policy, environment, workers, envs, devices, and endpoints count;
- input, checkpoint, log, report, and result references without secret values;
- latest verified step and evidence;
- failure text summary and retry safety;
- next executable action or unresolved decision;
- final comparison and retained artifacts.

Store bulky metrics in machine-readable artifacts and point to them. Do not copy model files, private topology, W&B credentials, or full logs into the ledger.
