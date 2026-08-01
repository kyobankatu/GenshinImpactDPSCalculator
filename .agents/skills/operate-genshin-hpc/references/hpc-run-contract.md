# HPC run contract

## Smallest sequence

1. Build Java classes in the selected immutable environment.
2. Run `PartyCatalogRegressionTest` and a bounded `BenchmarkRLJava` baseline.
3. Start one `ServeRLJava` service with explicit host, port, workers, and party selection.
4. Query service compatibility through the tracked Python client.
5. Run a short debug training or deterministic evaluation.
6. Add endpoints only after the single-endpoint gate passes.

## Record

- Git revision and relevant file integrity;
- native scheduler/environment commands;
- job ID, owner, name, resources, wall time, and terminal state;
- Java/Python/Torch versions and architecture;
- party catalog, workers, environments, rollout length, seed, and endpoint count;
- correctness, invalid actions, throughput distribution, and checkpoint path reference;
- cleanup status and whether retry is safe.

Keep endpoint values and scheduler outputs in private run records when they reveal internal topology. Git should contain only reusable, non-sensitive templates and contracts.
