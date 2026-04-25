import argparse
import os
import subprocess
import sys
import time
from pathlib import Path

import wandb

from train_recurrent_ppo import run_training


REPO_ROOT = Path(__file__).resolve().parents[3]
DEFAULT_GRADLE_USER_HOME = os.path.expanduser("~/.gradle-user-home")


def main():
    args = parse_args()
    run = wandb.init(
        project=args.project,
        entity=args.entity,
        mode=args.mode,
        group=args.group,
        job_type="rollout_sweep",
    )
    try:
        config = build_config(args, run.config)
        service, service_log = launch_rollout_service(args, config["rollout_workers"], run.id)
        try:
            time.sleep(args.startup_delay_sec)
            if service.poll() is not None:
                raise RuntimeError("Java rollout service exited before training started.")
            training_args = build_training_args(args, config)
            run.name = f"sweep_e{config['envs']}_w{config['rollout_workers']}_{run.id}"
            run_training(training_args, run=run)
        finally:
            stop_rollout_service(service, service_log)
    finally:
        wandb.finish()


def parse_args():
    parser = argparse.ArgumentParser(description="Run a W&B sweep trial for recurrent PPO with a local Java rollout service.")
    parser.add_argument("--project", default="genshin-recurrent-ppo", help="W&B project name")
    parser.add_argument("--entity", default=None, help="W&B entity/team")
    parser.add_argument("--group", default="rollout-sweep", help="W&B group for sweep trials")
    parser.add_argument("--mode", choices=("online", "offline", "disabled"), default="online", help="W&B mode")
    parser.add_argument("--preset", default="debug", help="training preset to use as the base config")
    parser.add_argument("--host", default="127.0.0.1", help="Java rollout host")
    parser.add_argument("--port", type=int, default=5005, help="Java rollout port")
    parser.add_argument("--bind-host", default="127.0.0.1", help="Java rollout bind host")
    parser.add_argument("--java-rollout-workers", type=int, default=None, help="fallback Java worker override when the sweep config does not set one")
    parser.add_argument("--gradle-user-home", default=os.environ.get("GRADLE_USER_HOME", DEFAULT_GRADLE_USER_HOME), help="Gradle user home for the build step")
    parser.add_argument("--java-home", default=os.environ.get("JAVA_HOME"), help="JAVA_HOME for launching the rollout service")
    parser.add_argument("--startup-delay-sec", type=float, default=3.0, help="seconds to wait before checking the rollout service")
    return parser.parse_args()


def build_config(args, sweep_config):
    config = {
        "seed": int(sweep_config.get("seed", 1234)),
        "updates": int(sweep_config.get("updates", 200)),
        "rollout_length": int(sweep_config.get("rollout_length", 128)),
        "envs": int(sweep_config.get("envs", 16)),
        "hidden_size": int(sweep_config.get("hidden_size", 256)),
        "ppo_epochs": int(sweep_config.get("ppo_epochs", 4)),
        "minibatch_size": int(sweep_config.get("minibatch_size", 512)),
        "gamma": float(sweep_config.get("gamma", 0.99)),
        "gae_lambda": float(sweep_config.get("gae_lambda", 0.95)),
        "clip_range": float(sweep_config.get("clip_range", 0.20)),
        "learning_rate": float(sweep_config.get("learning_rate", 3e-4)),
        "value_coefficient": float(sweep_config.get("value_coefficient", 0.5)),
        "entropy_coefficient": float(sweep_config.get("entropy_coefficient", 0.03)),
        "entropy_final_coefficient": float(sweep_config.get("entropy_final_coefficient", 0.008)),
        "max_grad_norm": float(sweep_config.get("max_grad_norm", 0.5)),
        "checkpoint_interval": int(sweep_config.get("checkpoint_interval", 10)),
        "evaluation_interval": int(sweep_config.get("evaluation_interval", 10)),
        "rollout_workers": int(sweep_config.get("rollout_workers", args.java_rollout_workers or 8)),
    }
    return config


def launch_rollout_service(args, rollout_workers, run_id):
    env = os.environ.copy()
    env["GRADLE_USER_HOME"] = args.gradle_user_home
    if args.java_home:
        env["JAVA_HOME"] = args.java_home
        env["PATH"] = f"{Path(args.java_home) / 'bin'}:{env['PATH']}"
    subprocess.run(
        ["./gradlew", "classes"],
        cwd=REPO_ROOT,
        env=env,
        check=True,
    )
    log_dir = REPO_ROOT / "logs"
    log_dir.mkdir(parents=True, exist_ok=True)
    log_path = log_dir / f"sweep_rollout_{run_id}_{args.port}.log"
    handle = open(log_path, "w", encoding="utf-8")
    process = subprocess.Popen(
        [
            "java",
            "-cp",
            "build/classes/java/main",
            "sample.ServeRLJava",
            str(args.port),
            args.bind_host,
            str(rollout_workers),
        ],
        cwd=REPO_ROOT,
        env=env,
        stdout=handle,
        stderr=subprocess.STDOUT,
    )
    return process, handle


def stop_rollout_service(process, handle):
    if process.poll() is None:
        process.terminate()
        try:
            process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=5)
    handle.close()


def build_training_args(args, config):
    return argparse.Namespace(
        preset=args.preset,
        seed=config["seed"],
        host=args.host,
        port=args.port,
        ports=None,
        endpoints=None,
        updates=config["updates"],
        rollout_length=config["rollout_length"],
        envs=config["envs"],
        hidden_size=config["hidden_size"],
        ppo_epochs=config["ppo_epochs"],
        minibatch_size=config["minibatch_size"],
        gamma=config["gamma"],
        gae_lambda=config["gae_lambda"],
        clip_range=config["clip_range"],
        learning_rate=config["learning_rate"],
        value_coefficient=config["value_coefficient"],
        entropy_coefficient=config["entropy_coefficient"],
        entropy_final_coefficient=config["entropy_final_coefficient"],
        max_grad_norm=config["max_grad_norm"],
        checkpoint_interval=config["checkpoint_interval"],
        evaluation_interval=config["evaluation_interval"],
        rollout_workers=config["rollout_workers"],
        wandb=True,
        wandb_project=args.project,
        wandb_entity=args.entity,
        wandb_run_name=None,
        wandb_group=args.group,
        wandb_mode=args.mode,
    )


if __name__ == "__main__":
    try:
        main()
    except subprocess.CalledProcessError as exc:
        print(f"Gradle command failed: {exc}", file=sys.stderr)
        raise
