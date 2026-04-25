# Split Rollout And Learner Tasks

## Goal

Run Java rollout collection on CPU-oriented nodes and PyTorch PPO learning on a separate GPU-oriented node.

The current single-node setup is simple, but rollout collection still dominates wall time.
The next architecture step is to decouple the rollout service from the learner so that:

- rollout can scale on CPU-heavy nodes
- learner can stay on a GPU-heavy node
- the two sides communicate over explicit rollout endpoints instead of `127.0.0.1`

## Current Situation

- learner optimization is already much cheaper than rollout collection
- single-node multi-port fan-out was slower than single service local execution
- rollout and learner are still coupled to one batch job by default
- the rollout service still needs a clean remote-endpoint contract for multi-node deployment

## Work Plan

### Phase 1: Make Rollout Endpoints First-Class

- Allow the Java rollout service to bind to a configurable host instead of only `127.0.0.1`
- Allow Python train/evaluate/benchmark entry points to accept explicit `host:port` endpoint lists
- Keep the old single-host local mode working for local debugging
- Deliverable:
  - rollout service can listen on a node-visible address
  - learner can consume remote endpoints without local-only assumptions

### Phase 2: Split Batch Entry Points

- Convert the current learner batch script into a learner-only job
- Add a rollout-only batch script for CPU nodes
- Use a shared endpoint-discovery directory so rollout jobs can publish their reachable `host:port`
- Make the learner wait for the expected number of rollout workers before starting PPO
- Deliverable:
  - rollout and learner can be submitted as separate jobs

### Phase 3: Update Docs And Operator Contract

- Document:
  - which script is submitted to rollout nodes
  - which script is submitted to the learner node
  - how rollout workers discover the same cluster tag
  - how to test the remote endpoint path manually
- Deliverable:
  - repo docs match the split-node deployment model

### Phase 4: Validate The Split Architecture

- Verify local syntax and build after the contract change
- Keep the old local path usable for development
- Confirm that the learner scripts can still talk to a single endpoint and to explicit endpoint lists
- Deliverable:
  - split-node support exists without breaking local debugging

## Acceptance Criteria

This node-splitting pass is complete only when:

- Java rollout can bind to a non-localhost address
- Python train/evaluate/benchmark can connect via explicit `host:port` endpoints
- there is a rollout batch script and a learner batch script
- learner startup no longer depends on starting local rollout inside the same job
- docs explain how to launch the two-job setup

## Notes

- Keep the single-endpoint local debugging path intact.
- Do not assume multi-node rollout is automatically faster; measure after the split is in place.
- Use CPU-heavy nodes for rollout first, then revisit process-level and node-level scaling after measurement.
