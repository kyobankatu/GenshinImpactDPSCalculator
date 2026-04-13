package mechanics.rl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import simulation.CombatSimulator;
import simulation.action.CharacterActionRequest;
import model.entity.Character;

public class RLServer {
    private int port;
    private Supplier<CombatSimulator> simFactory;
    private CombatSimulator currentSim;

    private List<RotationPhase> teacherRotation;

    // --- Dynamic Role Discovery Fields ---
    private java.util.Map<String, Double> damageHistory = new java.util.HashMap<>();
    private int episodeCount = 0;
    private String mainDps = null;

    // --- Parallel Rotation Search Fields ---
    private mechanics.optimization.RotationSearcher.RotationPlan goldenPlan = null;
    private int nextRotationIndex = 0;
    private int currentProfileIndex = 0; // Tracks which step of the profile we are on

    // Logging Control
    public static java.io.PrintStream originalOut = System.out;
    public static volatile boolean verbose = false;
    private Socket clientSocket;

    // File Logging
    private static java.io.PrintWriter fileLogWriter;
    static {
        try {
            fileLogWriter = new java.io.PrintWriter(new java.io.FileWriter("rl_server.log", false), true); // Overwrite
                                                                                                           // mode for
                                                                                                           // fresh logs
                                                                                                           // per run
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void logToConsole(String msg) {
        // Mute console output to prevent IO bottleneck during training
        // if (originalOut != null)
        // originalOut.println(msg);

        if (fileLogWriter != null) {
            fileLogWriter.println(msg);
        }
    }

    // Action Mapping: Integer ID -> Action Command
    // private List<ActionCommand> actionSpace; // Deprecated

    public RLServer(int port, Supplier<CombatSimulator> simFactory, List<RotationPhase> rotation) {
        this.port = port;
        this.simFactory = simFactory;
        this.teacherRotation = rotation;
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            logToConsole("RL Server starting on port " + port + "...");

            while (true) {
                logToConsole("Waiting for client...");
                try (Socket clientSocket = serverSocket.accept()) {
                    logToConsole("Client connected: " + clientSocket.getInetAddress());
                    this.clientSocket = clientSocket; // Assign to field
                    handleClient(clientSocket);
                } catch (IOException e) {
                    System.err.println("Client handling error: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleClient(Socket clientSocket) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

        String line;
        while ((line = in.readLine()) != null) {
            String response = processCommand(line);
            out.println(response);
            if (line.equals("QUIT"))
                break;
        }
    }

    private boolean generatingReport = false;
    private long reportStartTime = 0;

    private String processCommand(String command) {
        if (command.startsWith("RESET")) {
            currentSim = simFactory.get();
            stepCount = 0; // Reset step counter
            episodeCount++; // Increment episode counter
            nextRotationIndex = 0; // Reset rotation guide index

            // Smart Index Init: Align active character with Plan Start
            nextRotationIndex = 0;
            currentProfileIndex = 0; // Reset profile step

            if (goldenPlan != null && !goldenPlan.order.isEmpty()) {
                String targetStart = goldenPlan.order.get(0);
                // Force Align Active Character (Zero Cost)
                currentSim.setActiveCharacter(targetStart);
                logToConsole("[RL-Reset] Aligned Start Character to: " + targetStart);
            }

            // --- Force Full Energy for Training ---
            for (Character c : currentSim.getPartyMembers()) {
                c.receiveFlatEnergy(c.getEnergyCost());
            }

            lastSwapTime = -999.0; // Fix: Reset swap timer!

            if (command.equals("RESET_WITH_REPORT")) {
                generatingReport = true;
                currentSim.setLoggingEnabled(true);
                visualization.VisualLogger.getInstance().clear();
                reportStartTime = System.currentTimeMillis();
                System.out.println("--- Starting Simulation with Report Generation ---");
            } else {
                generatingReport = false;
                currentSim.setLoggingEnabled(false);
            }

            // Initial setup if needed (e.g. Energy)
            return getStateJson(0.0, false);
        }

        if (currentSim == null) {
            return "{\"error\": \"No active simulation. Send RESET first.\"}";
        }

        try {
            int actionId = Integer.parseInt(command);
            return executeAction(actionId);
        } catch (NumberFormatException e) {
            return "{\"error\": \"Invalid command format\"}";
        }
    }

    private double lastSwapTime = -999.0; // Track swap CD

    private String executeAction(int actionId) {
        if (actionId < 0 || actionId >= 7) {
            return getStateJson(-1.0, false);
        }

        double penalty = 0.0;
        boolean isValid = true;
        double now = currentSim.getCurrentTime();
        Character activeChar = currentSim.getActiveCharacter();
        double smartMoveBonus = 0.0;

        // --- TEACHER FORCING (PRE-EXECUTION) ---
        // We override the Agent's action decision BEFORE it executes.
        // This ensures the Simulation always follows the Teacher's Plan.

        if (teacherRotation != null && !teacherRotation.isEmpty()) {
            RotationPhase expectedPhase = teacherRotation.get(nextRotationIndex % teacherRotation.size());
            String currentPhaseCharName = expectedPhase.charName;
            boolean onCurrentChar = activeChar != null && activeChar.getName().equals(currentPhaseCharName);

            // Check Profile Done Status (for Optimization phase)
            Boolean profileDone = false;
            if (onCurrentChar) {
                List<String> actions = expectedPhase.actions;
                if (currentProfileIndex >= actions.size()) {
                    profileDone = true;
                } else {
                    String req = actions.get(currentProfileIndex);
                    if (req.equals("ATTACK_UNTIL_END")) {
                        if (!currentSim.getActiveCharacter().isBurstActive(currentSim.getCurrentTime())) {
                            profileDone = true;
                        }
                    }
                }
            }

            int requiredActionId = -1;

            if (onCurrentChar && !profileDone) {
                // STRICT PHASE
                String exp = expectedPhase.actions.get(currentProfileIndex);
                if (exp.equals("SKILL"))
                    requiredActionId = 1;
                else if (exp.equals("BURST"))
                    requiredActionId = 2;
                else
                    requiredActionId = 0;
            } else {
                // Between phases (phase done or recovery): force swap to target char
                String[] charOrder = { "Flins", "Ineffa", "Columbina", "Sucrose" };
                RotationPhase nextPhase = teacherRotation.get((nextRotationIndex + 1) % teacherRotation.size());
                String targetCharName = onCurrentChar ? nextPhase.charName : currentPhaseCharName;
                for (int i = 0; i < charOrder.length; i++) {
                    if (charOrder[i].equals(targetCharName)) {
                        requiredActionId = 3 + i;
                        break;
                    }
                }
                // allowOptimization stays false → strict forcing applies
            }

            // DECISION & OVERRIDE
            if (actionId == requiredActionId) {
                smartMoveBonus += 10.0;
            } else {
                smartMoveBonus -= 5.0;
                actionId = requiredActionId; // FORCE
                if (stepCount < 20 || episodeCount % 10 == 0)
                    logToConsole("[Teacher] Correction! Forced Action " + requiredActionId);
            }

            // PRE-CALCULATE STATE ADVANCE IMPLICATIONS
            // We need to know if this action WILL advance the state, to update indices
            // AFTER execution.
            // But we can just do it in Post-Execution block if we store variables?
            // Let's store "actionIsSwapToNext" or something.
            // Actually, we can just replicate the check at the end.
        }

        // --- Action Logic ---
        if (actionId <= 2)

        {
            // Active Character Action
            if (activeChar == null) {
                isValid = false;
                penalty = -10.0;
            } else {
                if (actionId == 1) { // Skill
                    if (!activeChar.canSkill(now)) {
                        isValid = false;
                        penalty = -50.0; // Heavy penalty for invalid Skill
                    }
                } else if (actionId == 2) { // Burst
                    if (!activeChar.canBurst(now)) {
                        isValid = false;
                        penalty = -50.0; // Heavy penalty for invalid Burst
                    }
                }
                // (Logic removed: Check moved to Reward Phase)
            }
        } else {
            // Swap Action (3-6)
            int targetIdx = actionId - 3;
            // Use HARDCODED order to match State Vector
            String[] charOrder = { "Flins", "Ineffa", "Columbina", "Sucrose" };

            if (targetIdx >= charOrder.length) {
                isValid = false;
                penalty = -10.0;
            } else {
                String targetName = charOrder[targetIdx];
                Character targetChar = currentSim.getCharacter(targetName);

                if (targetChar == null) {
                    isValid = false;
                    penalty = -10.0; // Char not found
                } else {
                    // Self-Swap Check
                    if (activeChar != null && activeChar.getName().equals(targetChar.getName())) {
                        isValid = false;
                        penalty = -50.0; // Heavy penalty for swapping to self
                    } else {
                        // Global CD Check
                        if (now - lastSwapTime < 1.0) {
                            isValid = false;
                            penalty = -50.0; // Heavy penalty for swap spam
                        }
                    }
                }
            }
        }

        if (!isValid) {
            currentSim.advanceTime(0.1); // Cost of failure
            boolean done = currentSim.getCurrentTime() >= 40.0;
            if (generatingReport && done) {
                try {
                    visualization.HtmlReportGenerator.generate("rl_report.html",
                            visualization.VisualLogger.getInstance().getRecords(), currentSim);
                    logToConsole("[RL-Report] Generated rl_report.html");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return getStateJson(penalty, done);
        }

        // --- Execution (Valid) ---
        double currentTotalDmg = currentSim.getTotalDamage();

        if (actionId <= 2) {
            if (actionId == 0)
                simFactoryActionRaw(activeChar, "attack");
            else if (actionId == 1)
                simFactoryActionRaw(activeChar, "skill");
            else if (actionId == 2)
                simFactoryActionRaw(activeChar, "burst");
        } else {
            int targetIdx = actionId - 3;
            String[] charOrder = { "Flins", "Ineffa", "Columbina", "Sucrose" };
            String targetName = charOrder[targetIdx];

            String oldChar = currentSim.getActiveCharacter().getName();
            currentSim.switchCharacter(targetName);
            lastSwapTime = currentSim.getCurrentTime();
            String newChar = currentSim.getActiveCharacter().getName();

            if (oldChar.equals(newChar)) {
                // logToConsole("[RL-Error] CRITICAL: Swap Failed! Tried to swap to " +
                // targetName + " but stayed on " + oldChar);
            } else {
                // logToConsole("[RL-Swap] Success: " + oldChar + " -> " + newChar);
            }
        }

        double newTotalDmg = currentSim.getTotalDamage();
        double damageDelta = (newTotalDmg - currentTotalDmg);
        double reward = damageDelta / 10000.0;

        // --- Dynamic Role Discovery Logic ---
        if (actionId == 0 && activeChar != null) { // Normal Attack
            if (episodeCount < 500) {
                // Phase 1: Exploration - Track Damage
                damageHistory.put(activeChar.getName(),
                        damageHistory.getOrDefault(activeChar.getName(), 0.0) + damageDelta);
            } else {
                // Phase 2: Exploitation
                if (mainDps == null) {
                    // Identify Main DPS
                    double maxDmg = -1.0;
                    for (java.util.Map.Entry<String, Double> entry : damageHistory.entrySet()) {
                        if (entry.getValue() > maxDmg) {
                            maxDmg = entry.getValue();
                            mainDps = entry.getKey();
                        }
                    }
                    if (mainDps == null)
                        mainDps = "Unknown"; // Fallback
                    logToConsole("[Auto-Role] Discovery Complete. Assigned Main DPS: " + mainDps
                            + " (Total Dmg Tracked: " + String.format("%,.0f", maxDmg) + ")");
                }

                // Bonus for Main DPS only
                // if (activeChar.getName().equals(mainDps)) {
                // reward += 1.0; // Incentivize Main DPS autos (Disabled to reduce noise)
                // }
            }
        }

        // --- GUIDANCE LOGIC ---
        // boolean recentlySwapped (removed usage in this block)

        // --- STATE UPDATE & REWARD FINALIZATION ---
        if (teacherRotation != null && !teacherRotation.isEmpty()) {
            // Apply Pre-calculated Bonus
            reward += smartMoveBonus;

            // Advance State Indices based on the Action that WAS executed
            RotationPhase expectedPhase = teacherRotation.get(nextRotationIndex % teacherRotation.size());
            String currentPhaseCharName = expectedPhase.charName;

            if (actionId >= 3) {
                // Swap happened
                String[] charOrder = { "Flins", "Ineffa", "Columbina", "Sucrose" };
                String swappedTo = charOrder[actionId - 3];
                RotationPhase nextPhase = teacherRotation.get((nextRotationIndex + 1) % teacherRotation.size());

                // If we swapped to the NEXT phase character
                if (swappedTo.equals(nextPhase.charName)) {
                    nextRotationIndex++;
                    currentProfileIndex = 0;
                    if (stepCount < 20 || episodeCount % 10 == 0)
                        logToConsole("[RL-Guide] Phase Advancing -> " + nextPhase.charName);
                }
            } else {
                // Action happened
                boolean onCurrentChar = activeChar != null && activeChar.getName().equals(currentPhaseCharName);
                if (onCurrentChar) {
                    boolean profileDone = false;
                    if (currentProfileIndex >= expectedPhase.actions.size()) {
                        profileDone = true;
                    } else {
                        String req = expectedPhase.actions.get(currentProfileIndex);
                        if (req.equals("ATTACK_UNTIL_END")) {
                            if (!currentSim.getActiveCharacter().isBurstActive(currentSim.getCurrentTime())) {
                                profileDone = true;
                            }
                        }
                    }

                    if (!profileDone) {
                        String exp = expectedPhase.actions.get(currentProfileIndex);
                        if (!exp.equals("ATTACK_UNTIL_END")) {
                            currentProfileIndex++;
                        }
                    }
                }
            }
        } else {
            // Fallback (Legacy)
            boolean recentlySwapped = (now - lastSwapTime) < 2.0;
            if (actionId == 1) {
                reward += 5.0;
                if (recentlySwapped)
                    reward += 10.0;
            } else if (actionId == 2) {
                reward += 10.0;
                if (recentlySwapped)
                    reward += 20.0;
            } else if (actionId >= 3) {
                reward += 0.1;
            }
        }

        boolean done = currentSim.getCurrentTime() >= 20.0;

        if (done) {
            logToConsole(
                    "--- Episode Done. Total DMG: " + String.format("%,.0f", currentSim.getTotalDamage()) + " ---");
            Character r = currentSim.getCharacter("Flins");
            if (r != null) {
                // ...
            }
        }

        if (generatingReport && done) {
            try {
                visualization.HtmlReportGenerator.generate("rl_report.html",
                        visualization.VisualLogger.getInstance().getRecords(), currentSim);
                logToConsole("[RL-Report] Generated rl_report.html");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Diagnostic Log (Temporary)
        // Log for ALL characters
        if (activeChar != null) {
            String diagPhase = goldenPlan != null ? goldenPlan.order.get(nextRotationIndex % goldenPlan.order.size())
                    : "?";
            logToConsole(String.format("[RL-Diag] Act=%s Phase=%s ProfIdx=%d ActId=%d Rew=%.1f",
                    activeChar.getName(), diagPhase, currentProfileIndex, actionId, reward));
        }

        return

        getStateJson(reward, done);
    }

    // Helper to execute string actions on char
    private void simFactoryActionRaw(Character c, String type) {
        currentSim.performAction(c.getName(), CharacterActionRequest.fromLegacy(type));
    }

    private int stepCount = 0; // Track steps in episode

    private String getStateJson(double reward, boolean done) {
        stepCount++;

        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"reward\": ").append(String.format("%.4f", reward)).append(", ");
        json.append("\"done\": ").append(done).append(", ");
        json.append("\"state\": [");

        // State Vector Construction
        // Per Char: [Energy, Active, CanSkill, CanBurst, IsBurstActive] -> 5 features *
        // 4 chars = 20
        // Plus 1 global feature (Global Swap Ready) = 21 features total
        List<Double> stateList = new ArrayList<>();

        // Fix Order: Flins, Ineffa, Columbina, Sucrose
        String[] charOrder = { "Flins", "Ineffa", "Columbina", "Sucrose" };
        double now = currentSim.getCurrentTime();

        for (String name : charOrder) {
            Character c = currentSim.getCharacter(name);
            if (c != null) {
                // 1. Energy (Normalized)
                double energy = c.getCurrentEnergy() / c.getEnergyCost();
                if (energy > 1.0)
                    energy = 1.0;
                stateList.add(energy);

                // 2. Is Active
                boolean isActive = currentSim.getActiveCharacter() != null &&
                        currentSim.getActiveCharacter().getName().equals(name);
                stateList.add(isActive ? 1.0 : 0.0);

                // 3. Can Skill (Ready & CD)
                stateList.add(c.canSkill(now) ? 1.0 : 0.0);

                // 4. Can Burst (Ready & CD & Energy)
                stateList.add(c.canBurst(now) ? 1.0 : 0.0);

                // 5. Is Burst Active?
                stateList.add(c.isBurstActive(now) ? 1.0 : 0.0);

                if (stepCount < 10) {
                    // logToConsole(String.format("[StateDetail] %s: En=%.2f Act=%b Skill=%b
                    // Burst=%b", name, energy, isActive, c.canSkill(now), c.canBurst(now)));
                    // if (isActive)
                    // logToConsole("[StateDetail] ACTIVE FOUND: " + name);
                }
            } else {
                stateList.add(0.0);
                stateList.add(0.0);
                stateList.add(0.0);
                stateList.add(0.0);
                stateList.add(0.0);
            }
        }

        // 6. Global Swap Ready (Single value)
        boolean canSwap = (now - lastSwapTime) >= 1.0;
        stateList.add(canSwap ? 1.0 : 0.0);

        // 7. Time Remaining (Normalized 0.0 - 1.0)
        // Helps the agent know if it needs to rush
        double maxTime = 40.0;
        double timeRem = Math.max(0, maxTime - now) / maxTime;
        stateList.add(timeRem);

        // GUIDANCE FEATURES
        int targetIndex = -1;
        double suggestAttack = 0.0;
        double suggestSkill = 0.0;
        double suggestBurst = 0.0;

        if (goldenPlan != null && currentSim.getActiveCharacter() != null) {
            String currentPhaseCharName = goldenPlan.order.get(nextRotationIndex % goldenPlan.order.size());
            boolean onCurrentChar = currentSim.getActiveCharacter().getName().equals(currentPhaseCharName);
            boolean profileDone = false;

            // Handle ATTACK_UNTIL_END completion logic
            if (onCurrentChar) {
                mechanics.optimization.ProfileLoader.ActionProfile profile = goldenPlan.profiles
                        .get(currentPhaseCharName);
                if (profile != null) {
                    if (currentProfileIndex >= profile.actions.size()) {
                        profileDone = true;
                    } else {
                        String req = profile.actions.get(currentProfileIndex);
                        if (req.equals("ATTACK_UNTIL_END")) {
                            // If Burst is NO LONGER active, consider this satisfied
                            if (!currentSim.getActiveCharacter().isBurstActive(currentSim.getCurrentTime())) {
                                profileDone = true;
                            }
                        }
                    }
                }
            }

            String effectiveTargetName = currentPhaseCharName;
            if (onCurrentChar && profileDone) {
                effectiveTargetName = goldenPlan.order.get((nextRotationIndex + 1) % goldenPlan.order.size());
            }

            // Calc One-Hot Target
            for (int i = 0; i < charOrder.length; i++) {
                if (charOrder[i].equals(effectiveTargetName)) {
                    targetIndex = i;
                    break;
                }
            }

            if (stepCount < 10) {
                // logToConsole("[DebugState] Index=" + nextRotationIndex + " Phase=" +
                // currentPhaseCharName + ...);
            }

            // Calc Suggest Action
            if (onCurrentChar && !profileDone) {
                mechanics.optimization.ProfileLoader.ActionProfile profile = goldenPlan.profiles
                        .get(currentPhaseCharName);
                String req = profile.actions.get(currentProfileIndex);
                if (req.equals("ATTACK") || req.equals("ATTACK_UNTIL_END"))
                    suggestAttack = 1.0;
                if (req.equals("SKILL"))
                    suggestSkill = 1.0;
                if (req.equals("BURST"))
                    suggestBurst = 1.0;
            }
        }

        for (int i = 0; i < 4; i++) {
            stateList.add((i == targetIndex) ? 1.0 : 0.0);
        }
        stateList.add(suggestAttack);
        stateList.add(suggestSkill);
        stateList.add(suggestBurst);

        // Append to JSON
        for (int i = 0; i < stateList.size(); i++) {
            json.append(String.format("%.3f", stateList.get(i)));
            if (i < stateList.size() - 1)
                json.append(", ");
        }

        json.append("]");
        json.append("}");

        // --- Debug Logging ---
        // Log if generating report OR if it's the start of an episode (to debug
        // training reset issues)
        if (generatingReport || stepCount < 5) {
            logToConsole("[StateDebug] " + json.toString());
        }

        return json.toString();
    }

    private List<ActionCommand> generateActionSpace() {
        List<ActionCommand> actions = new ArrayList<>();
        String[] chars = { "Flins", "Ineffa", "Columbina", "Sucrose" };

        for (String c : chars) {
            // Swap
            actions.add(s -> {
                s.switchCharacter(c);
                return true;
            });

            // Attack (N1)
            actions.add(s -> {
                s.performAction(c, "attack");
                return true;
            });

            // Skill
            actions.add(s -> {
                s.performAction(c, "skill");
                return true;
            });

            // Burst
            actions.add(s -> {
                s.performAction(c, "burst");
                return true;
            });
        }
        // Total 16 actions
        return actions;
    }

    // Use Functional Interface for Commands
    @FunctionalInterface
    interface ActionCommand {
        boolean execute(CombatSimulator sim);
    }
}
