import torch

ROLE_FEATURES_PER_SLOT = 5
ROLE_ON_FIELD_SHARE = 0
ROLE_DAMAGE_SHARE = 1
ROLE_OFF_FIELD_SHARE = 2
ROLE_ENTRY_SHARE = 3
ROLE_STAY_SHARE = 4


def assert_policy_client_compatible(policy, client, context):
    mismatches = []
    if policy.observation_size != client.observation_size:
        mismatches.append(
            f"observation_size policy={policy.observation_size} service={client.observation_size}"
        )
    if policy.action_size != client.action_size:
        mismatches.append(
            f"action_size policy={policy.action_size} service={client.action_size}"
        )
    if policy.char_feature_size != client.char_feature_size:
        mismatches.append(
            f"char_feature_size policy={policy.char_feature_size} service={client.char_feature_size}"
        )
    if policy.global_feature_size != client.global_feature_size:
        mismatches.append(
            f"global_feature_size policy={policy.global_feature_size} service={client.global_feature_size}"
        )
    if policy.num_chars != client.num_chars:
        mismatches.append(
            f"num_chars policy={policy.num_chars} service={client.num_chars}"
        )
    if policy.privileged_observation_size != client.privileged_observation_size:
        mismatches.append(
            "privileged_observation_size "
            f"policy={policy.privileged_observation_size} service={client.privileged_observation_size}"
        )
    if mismatches:
        raise ValueError(f"{context} mismatch between policy and rollout service: " + ", ".join(mismatches))


def extract_role_feature(role_vector, num_chars, feature_index):
    if not role_vector:
        return [0.0 for _ in range(num_chars)]
    values = []
    for slot in range(num_chars):
        offset = slot * ROLE_FEATURES_PER_SLOT + feature_index
        values.append(role_vector[offset] if offset < len(role_vector) else 0.0)
    return values


def evaluate_policy(policy, client, device, deterministic, generate_report=False):
    if len(client.party_names) > 1:
        per_party = {}
        aggregate = {
            "aggregate_type": "macro_average",
            "reward": 0.0,
            "damage": 0.0,
            "steps": 0.0,
            "invalid_actions": 0.0,
            "mean_top_probability": 0.0,
            "action_fractions": [0.0 for _ in range(policy.action_size)],
            "mean_attention_scores": [0.0 for _ in range(policy.num_chars)],
            "role_alignment_score": 0.0,
            "carry_alignment_score": 0.0,
            "off_field_alignment_score": 0.0,
            "entry_alignment_score": 0.0,
            "stay_alignment_score": 0.0,
            "expected_on_field_shares": [0.0 for _ in range(policy.num_chars)],
            "realized_on_field_shares": [0.0 for _ in range(policy.num_chars)],
        }
        for party_id, party_name in enumerate(client.party_names):
            summary = evaluate_single_episode(
                policy,
                client,
                device,
                deterministic=deterministic,
                generate_report=generate_report,
                forced_party_id=party_id,
            )
            per_party[party_name] = summary
            aggregate["reward"] += summary["reward"]
            aggregate["damage"] += summary["damage"]
            aggregate["steps"] += summary["steps"]
            aggregate["invalid_actions"] += summary["invalid_actions"]
            aggregate["mean_top_probability"] += summary["mean_top_probability"]
            aggregate["role_alignment_score"] += summary.get("role_alignment_score", 0.0)
            aggregate["carry_alignment_score"] += summary.get("carry_alignment_score", 0.0)
            aggregate["off_field_alignment_score"] += summary.get("off_field_alignment_score", 0.0)
            aggregate["entry_alignment_score"] += summary.get("entry_alignment_score", 0.0)
            aggregate["stay_alignment_score"] += summary.get("stay_alignment_score", 0.0)
            for index, fraction in enumerate(summary["action_fractions"]):
                aggregate["action_fractions"][index] += fraction
            for index, score in enumerate(summary["mean_attention_scores"]):
                aggregate["mean_attention_scores"][index] += score
            for index, value in enumerate(summary.get("expected_on_field_shares", [])):
                aggregate["expected_on_field_shares"][index] += value
            for index, value in enumerate(summary.get("realized_on_field_shares", [])):
                aggregate["realized_on_field_shares"][index] += value
        party_count = len(client.party_names)
        aggregate["reward"] /= party_count
        aggregate["damage"] /= party_count
        aggregate["steps"] /= party_count
        aggregate["invalid_actions"] /= party_count
        aggregate["mean_top_probability"] /= party_count
        aggregate["role_alignment_score"] /= party_count
        aggregate["carry_alignment_score"] /= party_count
        aggregate["off_field_alignment_score"] /= party_count
        aggregate["entry_alignment_score"] /= party_count
        aggregate["stay_alignment_score"] /= party_count
        aggregate["action_fractions"] = [
            value / party_count for value in aggregate["action_fractions"]
        ]
        aggregate["mean_attention_scores"] = [
            value / party_count for value in aggregate["mean_attention_scores"]
        ]
        aggregate["expected_on_field_shares"] = [
            value / party_count for value in aggregate["expected_on_field_shares"]
        ]
        aggregate["realized_on_field_shares"] = [
            value / party_count for value in aggregate["realized_on_field_shares"]
        ]
        aggregate["per_party"] = per_party
        return aggregate
    return evaluate_single_episode(
        policy,
        client,
        device,
        deterministic=deterministic,
        generate_report=generate_report,
        forced_party_id=-1,
    )


def evaluate_single_episode(policy, client, device, deterministic, generate_report, forced_party_id):
    runner_id = client.create_runner(1)
    observations, _privileged_observations, masks, _party_ids = client.reset_runner(
        runner_id, generate_report, forced_party_id=forced_party_id
    )
    hidden = torch.zeros(1, policy.hidden_size, dtype=torch.float32, device=device)
    total_reward = 0.0
    invalid_actions = 0
    steps = 0
    damage = 0.0
    top_probability_sum = 0.0
    action_counts = [0 for _ in range(policy.action_size)]
    attention_score_sum = torch.zeros(policy.num_chars, dtype=torch.float64)
    resolved_party_id = max(0, forced_party_id)
    try:
        while True:
            obs_tensor = torch.tensor(observations, dtype=torch.float32, device=device)
            mask_tensor = torch.tensor(masks, dtype=torch.float32, device=device)
            with torch.no_grad():
                inference = policy.act(obs_tensor, hidden, mask_tensor, deterministic=deterministic)
            attention_score_sum += inference["attention_scores"].detach().cpu().sum(dim=0).to(torch.float64)
            action = int(inference["action"][0].item())
            action_counts[action] += 1
            top_probability_sum += inference["top_probability"][0].item()
            batch = client.step_runner(runner_id, [action])
            total_reward += batch["rewards"][0]
            if not batch["valid_actions"][0]:
                invalid_actions += 1
            steps = batch["episode_steps"][0] if batch["dones"][0] else batch["live_steps"][0]
            damage = batch["episode_damages"][0] if batch["dones"][0] else batch["total_damages"][0]
            if batch["dones"][0]:
                episode_party_ids = batch.get("episode_party_ids")
                if episode_party_ids:
                    resolved_party_id = episode_party_ids[0]
                expected_roles = batch.get("episode_expected_role_vectors", [[]])[0]
                realized_roles = batch.get("episode_realized_role_vectors", [[]])[0]
                break
            observations = batch["observations"]
            masks = batch["action_masks"]
            hidden = inference["hidden"]
    finally:
        client.close_runner(runner_id)
    total_actions = max(1, sum(action_counts))
    return {
        "reward": total_reward,
        "damage": damage,
        "steps": steps,
        "invalid_actions": invalid_actions,
        "mean_top_probability": top_probability_sum / max(1, steps),
        "action_fractions": [count / total_actions for count in action_counts],
        "mean_attention_scores": (attention_score_sum / max(1, steps)).tolist(),
        "role_alignment_score": batch.get("episode_role_alignment_scores", [0.0])[0],
        "carry_alignment_score": batch.get("episode_carry_alignment_scores", [0.0])[0],
        "off_field_alignment_score": batch.get("episode_off_field_alignment_scores", [0.0])[0],
        "entry_alignment_score": batch.get("episode_entry_alignment_scores", [0.0])[0],
        "stay_alignment_score": batch.get("episode_stay_alignment_scores", [0.0])[0],
        "expected_role_vector": expected_roles,
        "realized_role_vector": realized_roles,
        "expected_on_field_shares": extract_role_feature(expected_roles, policy.num_chars, ROLE_ON_FIELD_SHARE),
        "realized_on_field_shares": extract_role_feature(realized_roles, policy.num_chars, ROLE_ON_FIELD_SHARE),
        "expected_damage_shares": extract_role_feature(expected_roles, policy.num_chars, ROLE_DAMAGE_SHARE),
        "realized_damage_shares": extract_role_feature(realized_roles, policy.num_chars, ROLE_DAMAGE_SHARE),
        "party_id": resolved_party_id,
        "party_name": client.party_names[resolved_party_id],
    }
