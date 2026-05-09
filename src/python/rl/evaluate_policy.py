import argparse
import json
import os

import torch

from recurrent_ppo import RecurrentPolicy, load_policy
from rollout_service_client import build_rollout_client


MODEL_PATH = "output/recurrent_ppo_py/latest-model.pt"
EVAL_SUMMARY_PATH = "output/eval_summary.json"


def main():
    args = parse_args()
    checkpoint = args.checkpoint
    host = args.host
    port = args.port
    ports = args.ports
    endpoints = args.endpoints
    mode = args.mode
    show_summary = args.summary

    if not os.path.exists(checkpoint):
        raise SystemExit(f"Checkpoint not found: {checkpoint}")

    client = build_rollout_client(host=host, port=port, ports=ports, endpoints=endpoints)
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    policy, _ = load_policy(checkpoint, map_location=device)
    policy = policy.to(device)
    try:
        if mode in ("deterministic", "both"):
            summary = run_evaluation(policy, client, device, deterministic=True, generate_report=True)
            print(
                f"Python PPO deterministic evaluation: reward={summary['reward']:.3f} damage={summary['damage']:,.0f} steps={summary['steps']} invalid={summary['invalid_actions']} topProb={summary['mean_top_probability']:.3f}"
            )
            print_action_breakdown("deterministic", summary)
            print_attention_breakdown("deterministic", summary)
            print_party_breakdown("deterministic", summary)
            print_report_paths(summary)
            if show_summary and "per_party" in summary:
                print_summary_table("deterministic", summary)
                save_eval_summary("deterministic", summary)
        if mode in ("stochastic", "both"):
            summary = run_evaluation(policy, client, device, deterministic=False, generate_report=False)
            print(
                f"Python PPO stochastic evaluation: reward={summary['reward']:.3f} damage={summary['damage']:,.0f} steps={summary['steps']} invalid={summary['invalid_actions']} topProb={summary['mean_top_probability']:.3f}"
            )
            print_action_breakdown("stochastic", summary)
            print_attention_breakdown("stochastic", summary)
            print_party_breakdown("stochastic", summary)
            if show_summary and "per_party" in summary:
                print_summary_table("stochastic", summary)
                save_eval_summary("stochastic", summary)
    finally:
        client.close()


def parse_args():
    parser = argparse.ArgumentParser(description="Evaluate a saved PyTorch recurrent PPO checkpoint against the Java rollout service.")
    parser.add_argument("--checkpoint", default=MODEL_PATH, help="path to the saved .pt checkpoint")
    parser.add_argument("--host", default="127.0.0.1", help="rollout service host")
    parser.add_argument("--port", type=int, default=5005, help="rollout service port")
    parser.add_argument("--ports", default=None, help="comma-separated rollout service ports for multi-service fan-out")
    parser.add_argument("--endpoints", default=None, help="comma-separated rollout service host:port endpoints for multi-node fan-out")
    parser.add_argument("--mode", choices=("deterministic", "stochastic", "both"), default="both", help="evaluation mode")
    parser.add_argument("--summary", action="store_true", help="print per-party comparison table and save output/eval_summary.json")
    return parser.parse_args()


def run_evaluation(policy, client, device, deterministic, generate_report):
    if len(client.party_names) > 1:
        per_party = {}
        aggregate = {
            "reward": 0.0,
            "damage": 0.0,
            "steps": 0.0,
            "invalid_actions": 0.0,
            "mean_top_probability": 0.0,
            "action_fractions": [0.0 for _ in range(policy.action_size)],
            "mean_attention_scores": [0.0 for _ in range(policy.num_chars)],
        }
        for party_id, party_name in enumerate(client.party_names):
            summary = run_single_episode(
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
            for index, fraction in enumerate(summary["action_fractions"]):
                aggregate["action_fractions"][index] += fraction
            for index, score in enumerate(summary["mean_attention_scores"]):
                aggregate["mean_attention_scores"][index] += score
        party_count = len(client.party_names)
        aggregate["reward"] /= party_count
        aggregate["damage"] /= party_count
        aggregate["steps"] /= party_count
        aggregate["invalid_actions"] /= party_count
        aggregate["mean_top_probability"] /= party_count
        aggregate["action_fractions"] = [
            value / party_count for value in aggregate["action_fractions"]
        ]
        aggregate["mean_attention_scores"] = [
            value / party_count for value in aggregate["mean_attention_scores"]
        ]
        aggregate["per_party"] = per_party
        return aggregate
    return run_single_episode(policy, client, device, deterministic, generate_report, forced_party_id=-1)


def run_single_episode(policy, client, device, deterministic, generate_report, forced_party_id):
    runner_id = client.create_runner(1)
    observations, _privileged_observations, masks, party_ids = client.reset_runner(
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
                total_actions = max(1, sum(action_counts))
                resolved_party_id = party_ids[0] if party_ids else max(0, forced_party_id)
                return {
                    "reward": total_reward,
                    "damage": damage,
                    "steps": steps,
                    "invalid_actions": invalid_actions,
                    "mean_top_probability": top_probability_sum / max(1, steps),
                    "action_fractions": [count / total_actions for count in action_counts],
                    "mean_attention_scores": (attention_score_sum / max(1, steps)).tolist(),
                    "party_id": resolved_party_id,
                    "party_name": client.party_names[resolved_party_id],
                }
            observations = batch["observations"]
            masks = batch["action_masks"]
            hidden = inference["hidden"]
    finally:
        client.close_runner(runner_id)


def print_action_breakdown(label, summary):
    formatted = " ".join(
        f"a{index}={fraction:.3f}" for index, fraction in enumerate(summary["action_fractions"])
    )
    print(f"  {label} actions: {formatted}")


def print_attention_breakdown(label, summary):
    if "mean_attention_scores" not in summary:
        return
    formatted = " ".join(
        f"slot{index}={score:.3f}" for index, score in enumerate(summary["mean_attention_scores"])
    )
    print(f"  {label} attention: {formatted}")


def print_party_breakdown(label, summary):
    if "per_party" not in summary:
        return
    for party_name, party_summary in summary["per_party"].items():
        print(
            f"  {label} party={party_summary['party_id']}:{party_name}: reward={party_summary['reward']:.3f} "
            f"damage={party_summary['damage']:,.0f} steps={party_summary['steps']} "
            f"invalid={party_summary['invalid_actions']}"
        )
        print_action_breakdown(f"{label} {party_name}", party_summary)
        print_attention_breakdown(f"{label} {party_name}", party_summary)


def print_report_paths(summary):
    if "per_party" in summary:
        for party_name in summary["per_party"]:
            print(f"Generated output/{build_report_file_name(party_name)}")
        return
    print(f"Generated output/{build_report_file_name(summary.get('party_name'))}")


def build_report_file_name(party_name):
    if not party_name:
        return "rl_report.html"
    slug = "".join(char.lower() if char.isalnum() else "_" for char in party_name).strip("_")
    while "__" in slug:
        slug = slug.replace("__", "_")
    return f"rl_report_{slug}.html" if slug else "rl_report.html"


def print_summary_table(label, summary):
    """Print a per-party comparison table to stdout."""
    per_party = summary.get("per_party", {})
    if not per_party:
        return
    col_width = 14
    header_parts = [
        f"{'party':<20}",
        f"{'reward':>{col_width}}",
        f"{'damage':>{col_width}}",
        f"{'steps':>{col_width}}",
        f"{'invalid':>{col_width}}",
        f"{'invalid_rate':>{col_width}}",
        f"{'attn_max':>{col_width}}",
    ]
    print(f"\n  [{label}] per-party summary:")
    print("  " + " ".join(header_parts))
    print("  " + "-" * (20 + (col_width + 1) * 6))
    for party_name, party_summary in per_party.items():
        steps = max(1, party_summary["steps"])
        invalid_rate = party_summary["invalid_actions"] / steps
        attn_scores = party_summary.get("mean_attention_scores", [])
        attn_max = max(attn_scores) if attn_scores else 0.0
        action_fracs = party_summary.get("action_fractions", [])
        action_frac_str = " ".join(f"a{i}={f:.3f}" for i, f in enumerate(action_fracs))
        row_parts = [
            f"{party_name:<20}",
            f"{party_summary['reward']:>{col_width}.3f}",
            f"{party_summary['damage']:>{col_width},.0f}",
            f"{steps:>{col_width}}",
            f"{party_summary['invalid_actions']:>{col_width}}",
            f"{invalid_rate:>{col_width}.3f}",
            f"{attn_max:>{col_width}.3f}",
        ]
        print("  " + " ".join(row_parts))
        print(f"    actions: {action_frac_str}")


def save_eval_summary(label, summary):
    """Save per-party evaluation results to output/eval_summary.json."""
    os.makedirs(os.path.dirname(EVAL_SUMMARY_PATH), exist_ok=True)
    per_party = summary.get("per_party", {})
    results = []
    for party_name, party_summary in per_party.items():
        steps = max(1, party_summary["steps"])
        results.append(
            {
                "label": label,
                "party_name": party_name,
                "party_id": party_summary.get("party_id", -1),
                "reward": party_summary["reward"],
                "damage": party_summary["damage"],
                "steps": party_summary["steps"],
                "invalid_actions": party_summary["invalid_actions"],
                "invalid_action_rate": party_summary["invalid_actions"] / steps,
                "action_fractions": party_summary.get("action_fractions", []),
                "mean_attention_scores": party_summary.get("mean_attention_scores", []),
                "mean_top_probability": party_summary.get("mean_top_probability", 0.0),
            }
        )
    existing = []
    if os.path.exists(EVAL_SUMMARY_PATH):
        try:
            with open(EVAL_SUMMARY_PATH, "r", encoding="utf-8") as handle:
                existing = json.load(handle)
        except (json.JSONDecodeError, OSError):
            existing = []
    # Replace entries for the same label
    existing = [entry for entry in existing if entry.get("label") != label]
    existing.extend(results)
    with open(EVAL_SUMMARY_PATH, "w", encoding="utf-8") as handle:
        json.dump(existing, handle, indent=2)
    print(f"  Saved eval summary to {EVAL_SUMMARY_PATH}")


if __name__ == "__main__":
    main()
