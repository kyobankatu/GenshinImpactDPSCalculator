package mechanics.rl;

import java.util.List;
import java.util.function.Supplier;

import mechanics.optimization.ProfileAction;
import model.entity.BurstStateProvider;
import model.entity.Character;
import model.type.CharacterId;
import simulation.CombatSimulator;
import simulation.action.CharacterActionRequest;
import visualization.HtmlReportGenerator;
import visualization.VisualLogger;

/**
 * Java-native RL environment backed directly by {@link CombatSimulator}.
 */
public class RLEnvironment {
    public static final int STATE_SIZE = 29;

    private final Supplier<CombatSimulator> simFactory;
    private final List<RotationPhase> teacherRotation;
    private final RLTrainingConfig config;

    private CombatSimulator currentSim;
    private int episodeCount;
    private int stepCount;
    private int nextRotationIndex;
    private int currentProfileIndex;
    private double lastSwapTime = -999.0;
    private boolean generatingReport;

    public RLEnvironment(
            Supplier<CombatSimulator> simFactory,
            List<RotationPhase> teacherRotation,
            RLTrainingConfig config) {
        this.simFactory = simFactory;
        this.teacherRotation = teacherRotation;
        this.config = config != null ? config : new RLTrainingConfig();
    }

    public double[] reset() {
        return reset(false);
    }

    public double[] reset(boolean generateReport) {
        currentSim = simFactory.get();
        currentSim.setLoggingEnabled(generateReport);
        if (generateReport) {
            VisualLogger.getInstance().clear();
        }
        episodeCount++;
        stepCount = 0;
        nextRotationIndex = 0;
        currentProfileIndex = 0;
        lastSwapTime = -999.0;
        generatingReport = generateReport;

        if (config.fillEnergyOnReset) {
            for (Character character : currentSim.getPartyMembers()) {
                character.receiveFlatEnergy(character.getEnergyCost());
            }
        }
        return buildState();
    }

    public RLStepResult step(int requestedActionId) {
        if (currentSim == null) {
            reset(false);
        }

        int executedActionId = requestedActionId;
        double reward = 0.0;
        Character activeChar = currentSim.getActiveCharacter();

        if (config.teacherForcing && hasTeacherRotation()) {
            int requiredActionId = requiredTeacherActionId(activeChar);
            if (requiredActionId >= 0) {
                if (requestedActionId == requiredActionId) {
                    reward += config.teacherMatchBonus;
                } else {
                    reward += config.teacherCorrectionPenalty;
                    executedActionId = requiredActionId;
                }
            }
        }

        ActionValidation validation = validate(executedActionId, activeChar);
        double damageBefore = currentSim.getTotalDamage();
        if (!validation.valid) {
            currentSim.advanceTime(config.failedActionTimeCost);
            reward += validation.penalty;
        } else {
            execute(executedActionId, activeChar);
        }

        double damageDelta = currentSim.getTotalDamage() - damageBefore;
        reward += damageDelta / config.damageRewardScale;
        updateTeacherProgress(executedActionId, activeChar);

        boolean done = currentSim.getCurrentTime() >= config.maxEpisodeTime;
        if (generatingReport && done) {
            HtmlReportGenerator.generate("rl_report.html", VisualLogger.getInstance().getRecords(), currentSim);
            generatingReport = false;
        }

        stepCount++;
        return new RLStepResult(buildState(), reward, done, validation.valid, damageDelta,
                currentSim.getTotalDamage(), executedActionId, stepCount);
    }

    public CombatSimulator getCurrentSim() {
        return currentSim;
    }

    public int getStepCount() {
        return stepCount;
    }

    private ActionValidation validate(int actionId, Character activeChar) {
        if (actionId < 0 || actionId >= RLAction.SIZE) {
            return ActionValidation.invalid(config.missingCharacterPenalty);
        }
        RLAction action = RLAction.fromId(actionId);
        double now = currentSim.getCurrentTime();

        if (!action.isSwap()) {
            if (activeChar == null) {
                return ActionValidation.invalid(config.missingCharacterPenalty);
            }
            if (action == RLAction.SKILL && !activeChar.canSkill(now)) {
                return ActionValidation.invalid(config.invalidActionPenalty);
            }
            if (action == RLAction.BURST && !activeChar.canBurst(now)) {
                return ActionValidation.invalid(config.invalidActionPenalty);
            }
            return ActionValidation.valid();
        }

        Character target = currentSim.getCharacter(action.getTargetCharacterId());
        if (target == null) {
            return ActionValidation.invalid(config.missingCharacterPenalty);
        }
        if (activeChar != null && activeChar.getCharacterId() == target.getCharacterId()) {
            return ActionValidation.invalid(config.invalidActionPenalty);
        }
        if (now - lastSwapTime < config.swapCooldown) {
            return ActionValidation.invalid(config.invalidActionPenalty);
        }
        return ActionValidation.valid();
    }

    private void execute(int actionId, Character activeChar) {
        RLAction action = RLAction.fromId(actionId);
        if (action.isSwap()) {
            currentSim.switchCharacter(action.getTargetCharacterId());
            lastSwapTime = currentSim.getCurrentTime();
            return;
        }
        currentSim.performAction(activeChar.getCharacterId(), CharacterActionRequest.of(action.getActionKey()));
    }

    private double[] buildState() {
        double[] state = new double[STATE_SIZE];
        int index = 0;
        double now = currentSim.getCurrentTime();

        for (CharacterId id : config.partyOrder) {
            Character character = currentSim.getCharacter(id);
            if (character != null) {
                state[index++] = Math.min(1.0, character.getCurrentEnergy() / character.getEnergyCost());
                state[index++] = currentSim.getActiveCharacter() != null
                        && currentSim.getActiveCharacter().getCharacterId() == id ? 1.0 : 0.0;
                state[index++] = character.canSkill(now) ? 1.0 : 0.0;
                state[index++] = character.canBurst(now) ? 1.0 : 0.0;
                state[index++] = isBurstActive(character, now) ? 1.0 : 0.0;
            } else {
                index += 5;
            }
        }

        state[index++] = (now - lastSwapTime) >= config.swapCooldown ? 1.0 : 0.0;
        state[index++] = Math.max(0.0, config.maxEpisodeTime - now) / config.maxEpisodeTime;

        Guidance guidance = currentGuidance();
        for (CharacterId id : config.partyOrder) {
            state[index++] = guidance.targetId == id ? 1.0 : 0.0;
        }
        state[index++] = guidance.suggestedAction == RLAction.ATTACK ? 1.0 : 0.0;
        state[index++] = guidance.suggestedAction == RLAction.SKILL ? 1.0 : 0.0;
        state[index] = guidance.suggestedAction == RLAction.BURST ? 1.0 : 0.0;
        return state;
    }

    private int requiredTeacherActionId(Character activeChar) {
        Guidance guidance = currentGuidance();
        if (guidance.targetId == null) {
            return -1;
        }
        boolean onTarget = activeChar != null && activeChar.getCharacterId() == guidance.targetId;
        if (!onTarget) {
            return RLAction.swapActionId(guidance.targetId);
        }
        return guidance.suggestedAction != null ? guidance.suggestedAction.getId() : -1;
    }

    private Guidance currentGuidance() {
        if (!hasTeacherRotation()) {
            return new Guidance(null, null);
        }

        RotationPhase phase = teacherRotation.get(nextRotationIndex % teacherRotation.size());
        Character active = currentSim.getActiveCharacter();
        boolean onPhaseCharacter = active != null && active.getCharacterId() == phase.characterId;
        boolean done = onPhaseCharacter && isCurrentProfileDone(phase);

        CharacterId targetId = phase.characterId;
        if (onPhaseCharacter && done) {
            targetId = teacherRotation.get((nextRotationIndex + 1) % teacherRotation.size()).characterId;
        }

        RLAction suggested = null;
        if (onPhaseCharacter && !done && currentProfileIndex < phase.actions.size()) {
            suggested = actionForProfileAction(phase.actions.get(currentProfileIndex));
        }
        return new Guidance(targetId, suggested);
    }

    private boolean isCurrentProfileDone(RotationPhase phase) {
        if (currentProfileIndex >= phase.actions.size()) {
            return true;
        }
        ProfileAction action = phase.actions.get(currentProfileIndex);
        return action == ProfileAction.ATTACK_UNTIL_END
                && !isBurstActive(currentSim.getActiveCharacter(), currentSim.getCurrentTime());
    }

    private void updateTeacherProgress(int executedActionId, Character activeBeforeAction) {
        if (!hasTeacherRotation()) {
            return;
        }
        RotationPhase phase = teacherRotation.get(nextRotationIndex % teacherRotation.size());
        if (executedActionId >= RLAction.SWAP_FLINS.getId()) {
            RotationPhase next = teacherRotation.get((nextRotationIndex + 1) % teacherRotation.size());
            RLAction action = RLAction.fromId(executedActionId);
            if (action.getTargetCharacterId() == next.characterId) {
                nextRotationIndex++;
                currentProfileIndex = 0;
            }
            return;
        }

        if (activeBeforeAction != null && activeBeforeAction.getCharacterId() == phase.characterId
                && currentProfileIndex < phase.actions.size()
                && phase.actions.get(currentProfileIndex) != ProfileAction.ATTACK_UNTIL_END) {
            currentProfileIndex++;
        }
    }

    private RLAction actionForProfileAction(ProfileAction action) {
        switch (action) {
            case SKILL:
                return RLAction.SKILL;
            case BURST:
                return RLAction.BURST;
            case ATTACK:
            case ATTACK_UNTIL_END:
            default:
                return RLAction.ATTACK;
        }
    }

    private boolean hasTeacherRotation() {
        return teacherRotation != null && !teacherRotation.isEmpty();
    }

    private boolean isBurstActive(Character character, double currentTime) {
        return character instanceof BurstStateProvider
                && ((BurstStateProvider) character).isBurstActive(currentTime);
    }

    private static final class Guidance {
        final CharacterId targetId;
        final RLAction suggestedAction;

        Guidance(CharacterId targetId, RLAction suggestedAction) {
            this.targetId = targetId;
            this.suggestedAction = suggestedAction;
        }
    }

    private static final class ActionValidation {
        final boolean valid;
        final double penalty;

        private ActionValidation(boolean valid, double penalty) {
            this.valid = valid;
            this.penalty = penalty;
        }

        static ActionValidation valid() {
            return new ActionValidation(true, 0.0);
        }

        static ActionValidation invalid(double penalty) {
            return new ActionValidation(false, penalty);
        }
    }
}
