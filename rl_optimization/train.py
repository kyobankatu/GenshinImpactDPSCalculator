import gymnasium as gym
from stable_baselines3 import PPO
from stable_baselines3.common.monitor import Monitor
from genshin_env import GenshinEnv
import time
import os

def main():
    # 1. Create Environment
    env = GenshinEnv()

    # Create log dir
    log_dir = "training_logs"
    os.makedirs(log_dir, exist_ok=True)
    
    # Wrap env
    env = Monitor(env, log_dir)

    # 2. Define Model
    model_path = "output/genshin_ppo_model.zip"
    
    if os.path.exists(model_path):
        print(f"Loading existing model from {model_path}...")
        # Load with specific objects to ensure continuity
        # Note: We must pass env to continue training on it
        model = PPO.load(model_path, env=env)
        
        # Update parameters if needed (optional, but good for tweaking)
        model.learning_rate = 0.0003
        model.ent_coef = 0.01
        
        print("Model loaded successfully. Resuming training...")
    else:
        print("Creating NEW PPO Agent...")
        policy_kwargs = dict(net_arch=[256, 256])
        model = PPO("MlpPolicy", env, verbose=1, 
                    learning_rate=0.0003, 
                    n_steps=64 * 200, 
                    batch_size=64,
                    ent_coef=0.01,
                    policy_kwargs=policy_kwargs)

    # 3. Train
    print("Starting Training (Press Ctrl+C to stop)...")
    try:
        # Train for longer (500k steps for convergence per user request)
        model.learn(total_timesteps=500000)
    except KeyboardInterrupt:
        print("\nTraining interrupted by user.")

    # 4. Save
    model.save("output/genshin_ppo_model")
    print("Model saved to output/genshin_ppo_model.zip")

    env.close()

    # 5. Plot
    try:
        from stable_baselines3.common import results_plotter
        import matplotlib.pyplot as plt
        
        print("Plotting learning curve...")
        results_plotter.plot_results([log_dir], 1e5, results_plotter.X_TIMESTEPS, "Genshin RL Learning Curve")
        plt.savefig("output/learning_curve.png")
        print("Curve saved to output/learning_curve.png")
    except ImportError:
        print("matplotlib not installed, skipping plot.")
    except Exception as e:
        print(f"Error plotting: {e}")

if __name__ == "__main__":
    main()
