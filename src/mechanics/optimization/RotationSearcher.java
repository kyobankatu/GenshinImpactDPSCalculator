package mechanics.optimization;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import simulation.CombatSimulator;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;
import model.entity.Character;
import model.type.CharacterId;
import mechanics.optimization.ProfileLoader; // Added import

/**
 * Finds the highest-damage rotation by exhaustively evaluating all combinations
 * of character order permutations and named action profiles.
 *
 * <p>The search space is:
 * <ul>
 *   <li><b>Order permutations</b> – all N! orderings of the party members.</li>
 *   <li><b>Profile combinations</b> – the Cartesian product of each character's
 *       available {@link ProfileLoader.ActionProfile}s.</li>
 * </ul>
 *
 * <p>All combinations are evaluated concurrently via a parallel stream.
 * {@link #findBestRotation} returns the {@link RotationPlan} with the highest
 * total damage within a 25-second window.
 */
public class RotationSearcher {

    /**
     * The result of a single rotation evaluation: the character cast order,
     * the action profile chosen for each character, and the total damage dealt.
     */
    public static class RotationPlan {
        /** Ordered list of character names defining the macro rotation sequence. */
        public List<String> order;

        /** The action profile selected for each character in this plan. */
        public Map<String, ProfileLoader.ActionProfile> profiles; // Changed map value

        /** Total damage accumulated during the evaluation window. */
        public double totalDamage;

        /**
         * @param order       cast order (character names in sequence)
         * @param profiles    per-character action profile selection
         * @param totalDamage total damage produced during the simulation window
         */
        public RotationPlan(List<String> order, Map<String, ProfileLoader.ActionProfile> profiles, double totalDamage) {
            this.order = order;
            this.profiles = profiles;
            this.totalDamage = totalDamage;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Macro[Dmg:%,.0f | Order:%s]", totalDamage, order));
            sb.append("\n    Profiles:");
            for (Map.Entry<String, ProfileLoader.ActionProfile> entry : profiles.entrySet()) {
                sb.append("\n      - ").append(entry.getKey()).append(": ").append(entry.getValue().name);
            }
            return sb.toString();
        }
    }

    /**
     * Searches all order permutations and profile combinations for the
     * highest-damage rotation.
     *
     * <p>Steps:
     * <ol>
     *   <li>Instantiates a temporary simulator to discover party member names.</li>
     *   <li>Loads action profiles for every character via {@link ProfileLoader}.</li>
     *   <li>Generates all N! order permutations and the Cartesian product of
     *       profile choices.</li>
     *   <li>Evaluates all resulting {@link RunnableEvaluation} tasks in parallel
     *       over a 25-second simulation window.</li>
     *   <li>Returns the plan with the maximum {@link RotationPlan#totalDamage}.</li>
     * </ol>
     *
     * @param simFactory supplier that creates a fresh, identically configured
     *                   {@link CombatSimulator} for each evaluation; called once
     *                   per task plus once for discovery
     * @return the best-scoring {@link RotationPlan} found
     * @throws RuntimeException if no rotation can be evaluated
     */
    public RotationPlan findBestRotation(Supplier<CombatSimulator> simFactory) {
        // 1. Get Character Names & Load Profiles
        CombatSimulator tempSim = simFactory.get();
        List<String> charNames = tempSim.getPartyMembers().stream()
                .map(Character::getName)
                .collect(Collectors.toList());

        Map<String, List<ProfileLoader.ActionProfile>> charProfiles = new HashMap<>();
        for (String name : charNames) {
            charProfiles.put(name, ProfileLoader.loadProfiles(name));
            System.out.println("Loaded Profiles for " + name + ": " + charProfiles.get(name));
        }

        // 2. Generate Order Permutations (24)
        List<List<String>> orderPermutations = generatePermutations(charNames);

        // 3. Generate Profile Combinations (Cartesian Product)
        List<Map<String, ProfileLoader.ActionProfile>> profileCombinations = generateProfileCombinations(charNames,
                charProfiles);

        // 4. Create Evaluation Tasks
        List<RunnableEvaluation> tasks = new ArrayList<>();
        for (List<String> order : orderPermutations) {
            for (Map<String, ProfileLoader.ActionProfile> combination : profileCombinations) {
                tasks.add(new RunnableEvaluation(simFactory, order, combination));
            }
        }

        System.out.println("[RotationSearcher] Evaluating " + tasks.size() + " rotation patterns in parallel...");
        long startTime = System.currentTimeMillis();

        // 5. Run Parallel Stream
        RotationPlan bestPlan = tasks.parallelStream()
                .map(RunnableEvaluation::evaluate)
                .max((p1, p2) -> Double.compare(p1.totalDamage, p2.totalDamage))
                .orElseThrow(() -> new RuntimeException("No rotation found"));

        long duration = System.currentTimeMillis() - startTime;
        System.out.println("[RotationSearcher] Search Complete in " + duration + "ms.");
        System.out.println("[RotationSearcher] Best Rotation: " + bestPlan);

        return bestPlan;
    }

    // --- Helpers ---

    /**
     * Recursively generates all permutations of the given list.
     *
     * @param original list of strings to permute
     * @return all N! orderings of the input list
     */
    private List<List<String>> generatePermutations(List<String> original) {
        if (original.isEmpty()) {
            List<List<String>> result = new ArrayList<>();
            result.add(new ArrayList<>());
            return result;
        }
        String first = original.get(0);
        List<List<String>> remainingPerms = generatePermutations(original.subList(1, original.size()));
        List<List<String>> allPerms = new ArrayList<>();
        for (List<String> perm : remainingPerms) {
            for (int i = 0; i <= perm.size(); i++) {
                List<String> temp = new ArrayList<>(perm);
                temp.add(i, first);
                allPerms.add(temp);
            }
        }
        return allPerms;
    }

    /**
     * Generates the Cartesian product of each character's available profiles.
     *
     * @param names             ordered list of character names
     * @param availableProfiles map of character name to their loaded profiles
     * @return all combinations where each character has exactly one profile selected
     */
    private List<Map<String, ProfileLoader.ActionProfile>> generateProfileCombinations(
            List<String> names,
            Map<String, List<ProfileLoader.ActionProfile>> availableProfiles) {

        List<Map<String, ProfileLoader.ActionProfile>> result = new ArrayList<>();
        generateRecursive(result, new HashMap<>(), names, 0, availableProfiles);
        return result;
    }

    /**
     * Recursive helper that builds profile combinations by stepping through
     * characters in {@code names} one index at a time.
     *
     * @param result    accumulator for completed combinations
     * @param current   partially built combination for the current recursion frame
     * @param names     ordered list of all character names
     * @param index     current character index being assigned
     * @param available all available profiles per character
     */
    private void generateRecursive(
            List<Map<String, ProfileLoader.ActionProfile>> result,
            Map<String, ProfileLoader.ActionProfile> current,
            List<String> names,
            int index,
            Map<String, List<ProfileLoader.ActionProfile>> available) {

        if (index == names.size()) {
            result.add(new HashMap<>(current));
            return;
        }

        String name = names.get(index);
        List<ProfileLoader.ActionProfile> options = available.get(name);
        for (ProfileLoader.ActionProfile option : options) {
            current.put(name, option);
            generateRecursive(result, current, names, index + 1, available);
            current.remove(name);
        }
    }

    // --- Evaluation Task ---

    /**
     * Encapsulates a single (order, profiles) combination to be evaluated in the
     * parallel stream.  Each instance runs an independent 25-second simulation.
     */
    private static class RunnableEvaluation {
        private Supplier<CombatSimulator> factory;
        private List<String> order;
        private Map<String, ProfileLoader.ActionProfile> profiles;

        /**
         * @param factory  supplier for a fresh simulator
         * @param order    cast order for this evaluation
         * @param profiles per-character profile selection for this evaluation
         */
        public RunnableEvaluation(Supplier<CombatSimulator> factory, List<String> order,
                Map<String, ProfileLoader.ActionProfile> profiles) {
            this.factory = factory;
            this.order = order;
            this.profiles = profiles;
        }

        /**
         * Runs the simulation and returns a {@link RotationPlan} with the total
         * damage accumulated.
         *
         * <p>Before the main loop all characters are given full energy so burst
         * availability is not the bottleneck in the first cycle.  Each iteration
         * of the loop swaps to the next character in {@code order}, executes that
         * character's full action profile sequence, and advances to the next
         * character.  A minimal time advance guards against infinite loops when no
         * action advances the clock.
         *
         * @return evaluation result containing total damage
         */
        public RotationPlan evaluate() {
            CombatSimulator sim = factory.get();
            sim.setLoggingEnabled(false);

            // Simulation Loop
            double maxTime = 25.0; // Increased from 22.0
            int orderIndex = 0;
            double lastActionTime = -1.0;

            // Force Energy Full
            for (Character c : sim.getPartyMembers())
                c.receiveFlatEnergy(c.getEnergyCost());

            while (sim.getCurrentTime() < maxTime) {
                // 1. Swap
                String targetName = order.get(orderIndex % order.size());
                CharacterId targetId = CharacterId.fromName(targetName);

                // If not active, swap
                if (sim.getActiveCharacter() == null || sim.getActiveCharacter().getCharacterId() != targetId) {
                    sim.switchCharacter(targetName);
                }

                Character active = sim.getActiveCharacter();
                ProfileLoader.ActionProfile profile = profiles.get(targetName);

                // 2. Execute Action Profile Sequence
                for (ProfileAction action : profile.actions) {
                    processAction(active, action, sim, maxTime);
                    if (sim.getCurrentTime() >= maxTime)
                        break;
                }

                // Move to next character
                orderIndex++;

                // Advance minimal time if stuck (prevent infinite loop if no actions take time)
                if (sim.getCurrentTime() <= lastActionTime) {
                    sim.advanceTime(0.1);
                }
                lastActionTime = sim.getCurrentTime();
            }

            return new RotationPlan(order, profiles, sim.getTotalDamage());
        }

        /**
         * Dispatches a single action command string to the active character.
         *
         * <p>Recognised commands:
         * <ul>
         *   <li>{@code SKILL} – uses the character's elemental skill if off cooldown.</li>
         *   <li>{@code BURST} – uses the elemental burst if energy is full.</li>
         *   <li>{@code ATTACK} – executes one normal attack.</li>
         *   <li>{@code ATTACK_UNTIL_END} – repeatedly attacks while the burst window
         *       is active, then performs 3 more attacks if the burst window has closed.</li>
         * </ul>
         *
         * @param active  the currently on-field character
         * @param command the action command string (case-insensitive)
         * @param sim     the running simulator
         * @param maxTime simulation end time; used to abort {@code ATTACK_UNTIL_END}
         */
        private void processAction(Character active, ProfileAction command, CombatSimulator sim, double maxTime) {
            double now = sim.getCurrentTime();
            switch (command) {
                case SKILL:
                    if (active.canSkill(now))
                        sim.performAction(active.getName(), CharacterActionRequest.of(CharacterActionKey.SKILL));
                    break;
                case BURST:
                    if (active.canBurst(now))
                        sim.performAction(active.getName(), CharacterActionRequest.of(CharacterActionKey.BURST));
                    break;
                case ATTACK:
                    sim.performAction(active.getName(), CharacterActionRequest.of(CharacterActionKey.NORMAL));
                    break;
                case ATTACK_UNTIL_END:
                    // Attack until burst ends (Smart Field Time)
                    int safety = 0;
                    while (active.isBurstActive(sim.getCurrentTime()) && sim.getCurrentTime() < maxTime
                            && safety < 100) {
                        sim.performAction(active.getName(), CharacterActionRequest.of(CharacterActionKey.NORMAL));
                        safety++;
                    }
                    // If not active (e.g. physical Raiden?), do a few attacks anyway
                    if (!active.isBurstActive(sim.getCurrentTime())) {
                        for (int k = 0; k < 3; k++)
                            sim.performAction(active.getName(), CharacterActionRequest.of(CharacterActionKey.NORMAL));
                    }
                    break;
            }
        }
    }
}
