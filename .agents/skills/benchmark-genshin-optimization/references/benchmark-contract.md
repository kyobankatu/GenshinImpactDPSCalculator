# Benchmark contract

Record for every baseline and candidate:

- full Git revision and dirty-tree status;
- party catalog and exact workload command;
- Java, Python, NumPy, Torch, device, and operating-system versions;
- CPU/GPU model, visible CPU set, worker count, environment count, and endpoint topology;
- seed policy and known uncontrolled randomness;
- warm-up method, measured interval, repetitions, and summary statistic;
- correctness gates and numerical tolerance;
- damage, reward, invalid actions, ER result, or other semantic delta;
- throughput/latency distribution and memory if material;
- profiler evidence supporting the bottleneck hypothesis.

Use `BenchmarkRLJava` for Java environment stepping and `benchmark_rollout.py` for client/service throughput. They measure different boundaries and are not interchangeable. Use deterministic evaluation for matched policy comparisons; stochastic evaluation answers a different question.
