package simulation.party;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import mechanics.element.ResonanceManager;
import mechanics.optimization.ArtifactOptimizer;
import mechanics.rotation.PolicyAction;
import model.artifact.EmblemOfSeveredFate;
import model.artifact.NoblesseOblige;
import model.character.Bennett;
import model.character.RaidenShogun;
import model.character.Xiangling;
import model.character.Xingqiu;
import model.entity.Enemy;
import model.stats.StatsContainer;
import model.type.CharacterId;
import model.type.StatType;
import model.weapon.SkywardBlade;
import model.weapon.SkywardSpine;
import model.weapon.TheCatch;
import model.weapon.WolfFang;
import simulation.CombatSimulator;

/**
 * Shared Raiden National party definition for sample and RL execution.
 */
public final class RaidenPartyDefinition extends AbstractPartyDefinition {
    private static final long SKYWARD_SPINE_PROC_SEED = 20260801L;
    private static final CharacterId[] PARTY_ORDER = {
            CharacterId.RAIDEN_SHOGUN,
            CharacterId.XINGQIU,
            CharacterId.XIANGLING,
            CharacterId.BENNETT
    };

    @Override
    public String name() {
        return "RaidenParty";
    }

    @Override
    public String displayName() {
        return "Genshin DPS Calculator: Raiden National Simulation (Refactored)";
    }

    @Override
    public CharacterId[] partyOrder() {
        return PARTY_ORDER.clone();
    }

    @Override
    public String loadoutFingerprint() {
        return "loadout-v1:RAIDEN_SHOGUN-c6-SkywardSpine-EmblemOfSeveredFate"
                + ":XINGQIU-c6-WolfFang-EmblemOfSeveredFate"
                + ":XIANGLING-c6-TheCatch-EmblemOfSeveredFate"
                + ":BENNETT-c6-SkywardBlade-NoblesseOblige";
    }

    @Override
    public double rotationCycleSeconds() {
        return 21.0;
    }

    @Override
    public int[] baselinePolicyActions() {
        return policyActions(
                PolicyAction.SKILL_PRESS,
                PolicyAction.SWAP_SLOT_1, PolicyAction.BURST,
                PolicyAction.SKILL_PRESS, PolicyAction.NORMAL,
                PolicyAction.SWAP_SLOT_3, PolicyAction.BURST,
                PolicyAction.SKILL_PRESS, PolicyAction.NORMAL,
                PolicyAction.SWAP_SLOT_2, PolicyAction.BURST,
                PolicyAction.SKILL_PRESS, PolicyAction.NORMAL,
                PolicyAction.SWAP_SLOT_0, PolicyAction.BURST,
                PolicyAction.NORMAL, PolicyAction.NORMAL,
                PolicyAction.CHARGE);
    }

    @Override
    public Map<CharacterId, List<StatType>> optimizationTargets() {
        Map<CharacterId, List<StatType>> targets = new LinkedHashMap<>();
        targets.put(CharacterId.RAIDEN_SHOGUN,
                Arrays.asList(StatType.CRIT_RATE, StatType.CRIT_DMG, StatType.ATK_PERCENT));
        targets.put(CharacterId.XINGQIU,
                Arrays.asList(StatType.CRIT_RATE, StatType.CRIT_DMG, StatType.ATK_PERCENT));
        targets.put(CharacterId.XIANGLING,
                Arrays.asList(StatType.CRIT_RATE, StatType.CRIT_DMG, StatType.ATK_PERCENT,
                        StatType.ELEMENTAL_MASTERY));
        return targets;
    }

    @Override
    public CombatSimulator createSimulator(
            Map<CharacterId, Double> erTargets,
            Map<CharacterId, Map<StatType, Integer>> partyManualRolls) {
        CombatSimulator sim = new CombatSimulator();
        sim.setEnemy(new Enemy(90));
        setupParty(sim, safeErTargets(erTargets), safeRolls(partyManualRolls));
        return sim;
    }

    @Override
    public void executeRotation(CombatSimulator sim) {
        sim.switchCharacter(CharacterId.RAIDEN_SHOGUN);
        sim.getEnergyDistributor().scheduleKQMSEnemyParticles();
        skill(sim, CharacterId.RAIDEN_SHOGUN);

        sim.switchCharacter(CharacterId.XINGQIU);
        burst(sim, CharacterId.XINGQIU);
        skill(sim, CharacterId.XINGQIU);
        normal(sim, CharacterId.XINGQIU);

        sim.switchCharacter(CharacterId.BENNETT);
        burst(sim, CharacterId.BENNETT);
        normal(sim, CharacterId.BENNETT);
        skill(sim, CharacterId.BENNETT);

        sim.switchCharacter(CharacterId.XIANGLING);
        burst(sim, CharacterId.XIANGLING);
        normal(sim, CharacterId.XIANGLING);
        skill(sim, CharacterId.XIANGLING);
        normal(sim, CharacterId.XIANGLING);

        sim.switchCharacter(CharacterId.RAIDEN_SHOGUN);
        burst(sim, CharacterId.RAIDEN_SHOGUN);
        for (int i = 0; i < 3; i++) {
            normal(sim, CharacterId.RAIDEN_SHOGUN);
            normal(sim, CharacterId.RAIDEN_SHOGUN);
            normal(sim, CharacterId.RAIDEN_SHOGUN);
            charge(sim, CharacterId.RAIDEN_SHOGUN);
        }
        normal(sim, CharacterId.RAIDEN_SHOGUN);
        charge(sim, CharacterId.RAIDEN_SHOGUN);
        sim.advanceTime(0.1);
        normal(sim, CharacterId.RAIDEN_SHOGUN);
        skill(sim, CharacterId.RAIDEN_SHOGUN);

        sim.switchCharacter(CharacterId.BENNETT);
        skill(sim, CharacterId.BENNETT);
        normal(sim, CharacterId.BENNETT);

        sim.switchCharacter(CharacterId.XIANGLING);
        normal(sim, CharacterId.XIANGLING);

        sim.switchCharacter(CharacterId.BENNETT);
        skill(sim, CharacterId.BENNETT);
        normal(sim, CharacterId.BENNETT);

        sim.switchCharacter(CharacterId.XIANGLING);
        normal(sim, CharacterId.XIANGLING);

        double remaining = 21.0 - sim.getCurrentTime();
        if (remaining > 0) {
            sim.advanceTime(remaining);
        }
    }

    private void setupParty(CombatSimulator sim, Map<CharacterId, Double> erTargets,
            Map<CharacterId, Map<StatType, Integer>> partyManualRolls) {
        Random skywardSpineRandom = new Random(SKYWARD_SPINE_PROC_SEED);
        RaidenShogun raiden = new RaidenShogun(new SkywardSpine(skywardSpineRandom::nextDouble), null);
        ArtifactOptimizer.OptimizationConfig raidenConfig = new ArtifactOptimizer.OptimizationConfig();
        raidenConfig.mainStatSands = StatType.ENERGY_RECHARGE;
        raidenConfig.mainStatGoblet = StatType.ELECTRO_DMG_BONUS;
        raidenConfig.mainStatCirclet = StatType.CRIT_RATE;
        raidenConfig.subStatPriority = Arrays.asList(StatType.ENERGY_RECHARGE, StatType.CRIT_RATE,
                StatType.CRIT_DMG, StatType.ATK_PERCENT);
        double calcER = erTargets.getOrDefault(CharacterId.RAIDEN_SHOGUN, 1.0);
        System.out.println("   [Setup] Raiden Shogun Calculated ER: " + String.format("%.1f", calcER * 100) + "%");
        raidenConfig.minER = Math.max(calcER, 2.50);
        if (partyManualRolls.containsKey(CharacterId.RAIDEN_SHOGUN)) {
            raidenConfig.manualRolls = partyManualRolls.get(CharacterId.RAIDEN_SHOGUN);
        }
        ArtifactOptimizer.OptimizationResult resultRaiden = ArtifactOptimizer.generate(
                raidenConfig,
                raiden.getBaseStats(),
                raiden.getWeapon().getStats(),
                emblemSetBonusStats());
        raiden.setArtifacts(new EmblemOfSeveredFate(resultRaiden.stats));
        raiden.setArtifactRolls(resultRaiden.rolls);
        sim.addCharacter(raiden);

        Xingqiu xingqiu = new Xingqiu(new WolfFang(), null);
        ArtifactOptimizer.OptimizationConfig xqConfig = new ArtifactOptimizer.OptimizationConfig();
        xqConfig.mainStatSands = StatType.ATK_PERCENT;
        xqConfig.mainStatGoblet = StatType.HYDRO_DMG_BONUS;
        xqConfig.mainStatCirclet = StatType.CRIT_RATE;
        xqConfig.subStatPriority = Arrays.asList(StatType.ENERGY_RECHARGE, StatType.CRIT_RATE,
                StatType.CRIT_DMG, StatType.ATK_PERCENT);
        xqConfig.minER = erTargets.getOrDefault(CharacterId.XINGQIU, 1.0);
        if (partyManualRolls.containsKey(CharacterId.XINGQIU)) {
            xqConfig.manualRolls = partyManualRolls.get(CharacterId.XINGQIU);
        }
        ArtifactOptimizer.OptimizationResult resultXq = ArtifactOptimizer.generate(
                xqConfig, xingqiu.getBaseStats(), xingqiu.getWeapon().getStats(), emblemSetBonusStats());
        xingqiu.setArtifacts(new EmblemOfSeveredFate(resultXq.stats));
        xingqiu.setArtifactRolls(resultXq.rolls);
        sim.addCharacter(xingqiu);

        Xiangling xiangling = new Xiangling(new TheCatch(), null);
        xiangling.setChiliPickupAssumed(true);
        ArtifactOptimizer.OptimizationConfig xlConfig = new ArtifactOptimizer.OptimizationConfig();
        xlConfig.mainStatSands = StatType.ATK_PERCENT;
        xlConfig.mainStatGoblet = StatType.PYRO_DMG_BONUS;
        xlConfig.mainStatCirclet = StatType.CRIT_RATE;
        xlConfig.subStatPriority = Arrays.asList(StatType.ENERGY_RECHARGE, StatType.CRIT_RATE,
                StatType.CRIT_DMG, StatType.ATK_PERCENT, StatType.ELEMENTAL_MASTERY);
        xlConfig.minER = erTargets.getOrDefault(CharacterId.XIANGLING, 1.0);
        if (partyManualRolls.containsKey(CharacterId.XIANGLING)) {
            xlConfig.manualRolls = partyManualRolls.get(CharacterId.XIANGLING);
        }
        ArtifactOptimizer.OptimizationResult resultXl = ArtifactOptimizer.generate(
                xlConfig, xiangling.getBaseStats(), xiangling.getWeapon().getStats(), emblemSetBonusStats());
        xiangling.setArtifacts(new EmblemOfSeveredFate(resultXl.stats));
        xiangling.setArtifactRolls(resultXl.rolls);
        sim.addCharacter(xiangling);

        Bennett bennett = new Bennett(new SkywardBlade(), null);
        ArtifactOptimizer.OptimizationConfig bennettConfig = new ArtifactOptimizer.OptimizationConfig();
        bennettConfig.mainStatSands = StatType.ENERGY_RECHARGE;
        bennettConfig.mainStatGoblet = StatType.HP_PERCENT;
        bennettConfig.mainStatCirclet = StatType.HP_PERCENT;
        bennettConfig.subStatPriority = Arrays.asList(StatType.ENERGY_RECHARGE, StatType.HP_PERCENT,
                StatType.HP_FLAT);
        bennettConfig.minER = erTargets.getOrDefault(CharacterId.BENNETT, 1.0);
        if (partyManualRolls.containsKey(CharacterId.BENNETT)) {
            bennettConfig.manualRolls = partyManualRolls.get(CharacterId.BENNETT);
        }
        ArtifactOptimizer.OptimizationResult resultBennett = ArtifactOptimizer.generate(
                bennettConfig,
                bennett.getBaseStats(),
                bennett.getWeapon().getStats(),
                new NoblesseOblige(new StatsContainer()).getStats());
        bennett.setArtifacts(new NoblesseOblige(resultBennett.stats));
        bennett.setArtifactRolls(resultBennett.rolls);
        sim.addCharacter(bennett);

        ResonanceManager.applyResonances(sim);
    }

    /**
     * Returns the static stats supplied by the equipped Emblem set.
     *
     * @return a fresh set-stat container for artifact allocation
     */
    private static StatsContainer emblemSetBonusStats() {
        return new EmblemOfSeveredFate(new StatsContainer()).getStats();
    }
}
