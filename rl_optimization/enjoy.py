import gymnasium as gym
from stable_baselines3 import PPO
from genshin_env import GenshinEnv
import numpy as np

def main():
    # 1. Load Environment
    env = GenshinEnv()
    
    # 2. Load Trained Model
    try:
        model = PPO.load("genshin_ppo_model")
        print("Loaded genshin_ppo_model.zip")
    except FileNotFoundError:
        print("Model file not found. Please train first.")
        return

    # 3. Run One Episode
    obs, _ = env.reset(options={"reset_command": "RESET_WITH_REPORT"})
    done = False
    total_reward = 0
    
    print("\n--- Optimal Rotation (AI) ---")
    
    # Action Map for readable output
    chars = ["Raiden", "Xingqiu", "Xiangling", "Bennett"]
    actions = ["Swap", "Attack", "Skill", "Burst"]
    
    while not done:
        action, _states = model.predict(obs, deterministic=True)
        
        # Decode Action for Display (New 7-Action Space)
        action_map = {
            0: "Active -> Attack",
            1: "Active -> Skill",
            2: "Active -> Burst",
            3: "Swap -> Raiden",
            4: "Swap -> Xingqiu",
            5: "Swap -> Xiangling",
            6: "Swap -> Bennett"
        }
        
        act_desc = action_map.get(int(action), f"Unknown ({action})")
        print(f"Action: {act_desc}")
        
        obs, reward, done, truncated, info = env.step(action)
        total_reward += reward

    print(f"\nTotal Reward (Normalized): {total_reward:,.2f}")
    print("Check the generated HTML report for actual Damage values.")
    env.close()

if __name__ == "__main__":
    main()
