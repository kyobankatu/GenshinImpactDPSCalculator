import gymnasium as gym
from gymnasium import spaces
import numpy as np
import socket
import json

class GenshinEnv(gym.Env):
    """
    Custom Environment that follows gym interface.
    Connects to Java Simulation via TCP Socket.
    """
    metadata = {'render.modes': ['human']}

    def __init__(self, host='127.0.0.1', port=5000):
        super(GenshinEnv, self).__init__()
        
        # Define Action Space (Reduced to 7: Normal, Skill, Burst, Swap0-3)
        self.action_space = spaces.Discrete(7)

        # [Energy, IsActive, CanSkill, CanBurst, IsBurstActive] * 4 chars + GlobalSwap + TimeRem + NextTarget(4) + Suggested(3) = 29
        self.observation_space = spaces.Box(low=0, high=1, shape=(29,), dtype=np.float32)
        
        self.host = host
        self.port = port
        self.sock = None

    def _connect(self):
        if self.sock is None:
            print(f"Connecting to Java Sim at {self.host}:{self.port}...")
            try:
                self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                self.sock.connect((self.host, self.port))
                print("Connected!")
            except ConnectionRefusedError:
                print("Connection failed. Make sure 'java RunRL' is running.")
                self.sock = None

    def step(self, action):
        if self.sock is None: self._connect()
        if self.sock is None: return np.zeros(self.observation_space.shape), 0, True, False, {}

        # 1. Send Action ID
        try:
            msg = str(action) + "\n"
            self.sock.sendall(msg.encode())
            
            # 2. Receive JSON
            response = self.sock.recv(4096).decode().strip()
            data = json.loads(response)
            
            obs = np.array(data['state'], dtype=np.float32)
            reward = data['reward']
            done = data['done']
            return obs, reward, done, False, {}
        except Exception as e:
            print(f"Error during step: {e}")
            self.close()
            return np.zeros(self.observation_space.shape), 0, True, False, {}

    def reset(self, seed=None, options=None):
        super().reset(seed=seed)
        if self.sock is None: self._connect()
        if self.sock is None: return np.zeros(self.observation_space.shape), {}

        command = "RESET"
        if options and "reset_command" in options:
            command = options["reset_command"]

        try:
            self.sock.sendall(f"{command}\n".encode())
            response = self.sock.recv(4096).decode().strip()
            data = json.loads(response)
            obs = np.array(data['state'], dtype=np.float32)
            return obs, {}
        except Exception as e:
            print(f"Error during reset: {e}")
            self.close()
            return np.zeros(self.observation_space.shape), {}

    def close(self):
        if self.sock:
            try:
                self.sock.sendall(b"QUIT\n")
                self.sock.close()
            except:
                pass
            self.sock = None
