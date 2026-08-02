package simulation.party;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mechanics.element.ResonanceManager;
import mechanics.optimization.ArtifactOptimizer;
import model.artifact.AubadeOfMorningstarAndMoon;
import model.artifact.NightOfTheSkysUnveiling;
import model.artifact.SilkenMoonsSerenade;
import model.artifact.ViridescentVenerer;
import model.character.Columbina;
import model.character.Flins;
import model.character.Ineffa;
import model.character.Sucrose;
import model.entity.Enemy;
import model.type.CharacterId;
import model.type.StatType;
import model.weapon.CalamityQueller;
import model.weapon.NocturnesCurtainCall;
import model.weapon.PrimordialJadeWingedSpear;
import model.weapon.SunnyMorningSleepIn;
import simulation.CombatSimulator;

/**
 * Shared FlinsParty2 definition for sample reports and RL.
 */
public final class FlinsParty2Definition extends AbstractPartyDefinition {
    private static final CharacterId[] PARTY_ORDER = {
            CharacterId.FLINS,
            CharacterId.INEFFA,
            CharacterId.COLUMBINA,
            CharacterId.SUCROSE
    };

    @Override
    public String name() {
        return "FlinsParty2";
    }

    @Override
    public String displayName() {
        return "Genshin DPS Calculator: Flins Party Simulation (Refactored)";
    }

    @Override
    public CharacterId[] partyOrder() {
        return PARTY_ORDER.clone();
    }

    @Override
    public boolean publishDocsReport() {
        return true;
    }

    @Override
    public Map<CharacterId, List<StatType>> optimizationTargets() {
        Map<CharacterId, List<StatType>> targets = new HashMap<>();
        targets.put(CharacterId.FLINS, Arrays.asList(StatType.CRIT_RATE, StatType.CRIT_DMG, StatType.ATK_PERCENT));
        targets.put(CharacterId.INEFFA, Arrays.asList(StatType.CRIT_RATE, StatType.CRIT_DMG, StatType.ATK_PERCENT));
        targets.put(CharacterId.COLUMBINA, Arrays.asList(StatType.CRIT_RATE, StatType.CRIT_DMG, StatType.HP_PERCENT));
        targets.put(CharacterId.SUCROSE, Arrays.asList(StatType.ELEMENTAL_MASTERY));
        return targets;
    }

    @Override
    public CombatSimulator createSimulator(
            Map<CharacterId, Double> erTargets,
            Map<CharacterId, Map<StatType, Integer>> partyManualRolls) {
        CombatSimulator sim = new CombatSimulator();
        sim.setEnemy(new Enemy(100));
        setupParty(sim, safeErTargets(erTargets), safeRolls(partyManualRolls));
        sim.updateMoonsign();
        return sim;
    }

    @Override
    public void executeRotation(CombatSimulator sim) {
        for (int j = 0; j < 2; j++) {
            sim.switchCharacter(CharacterId.INEFFA);
            skill(sim, CharacterId.INEFFA);
            burst(sim, CharacterId.INEFFA);

            sim.switchCharacter(CharacterId.COLUMBINA);
            skill(sim, CharacterId.COLUMBINA);
            burst(sim, CharacterId.COLUMBINA);

            sim.switchCharacter(CharacterId.SUCROSE);
            skill(sim, CharacterId.SUCROSE);
            burst(sim, CharacterId.SUCROSE);

            sim.switchCharacter(CharacterId.FLINS);
            skill(sim, CharacterId.FLINS);
            skill(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            burst(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            skill(sim, CharacterId.FLINS);
            burst(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            skill(sim, CharacterId.FLINS);
            burst(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);

            sim.switchCharacter(CharacterId.SUCROSE);
            skill(sim, CharacterId.SUCROSE);
            normal(sim, CharacterId.SUCROSE);
            normal(sim, CharacterId.SUCROSE);
            normal(sim, CharacterId.SUCROSE);

            sim.switchCharacter(CharacterId.INEFFA);
            skill(sim, CharacterId.INEFFA);

            sim.switchCharacter(CharacterId.COLUMBINA);
            skill(sim, CharacterId.COLUMBINA);
            burst(sim, CharacterId.COLUMBINA);

            sim.switchCharacter(CharacterId.SUCROSE);
            skill(sim, CharacterId.SUCROSE);

            sim.switchCharacter(CharacterId.FLINS);
            skill(sim, CharacterId.FLINS);
            skill(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            burst(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            skill(sim, CharacterId.FLINS);
            burst(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            skill(sim, CharacterId.FLINS);
            burst(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);
            normal(sim, CharacterId.FLINS);

            sim.switchCharacter(CharacterId.SUCROSE);
            normal(sim, CharacterId.SUCROSE);
            normal(sim, CharacterId.SUCROSE);
        }
    }

    private void setupParty(CombatSimulator sim, Map<CharacterId, Double> erTargets,
            Map<CharacterId, Map<StatType, Integer>> partyManualRolls) {
        Flins flins = new Flins(new PrimordialJadeWingedSpear(), null);
        ArtifactOptimizer.OptimizationConfig flinsConfig = new ArtifactOptimizer.OptimizationConfig();
        flinsConfig.mainStatSands = StatType.ATK_PERCENT;
        flinsConfig.mainStatGoblet = StatType.ATK_PERCENT;
        flinsConfig.mainStatCirclet = StatType.CRIT_DMG;
        flinsConfig.subStatPriority = Arrays.asList(StatType.CRIT_DMG, StatType.CRIT_RATE,
                StatType.ATK_PERCENT, StatType.ENERGY_RECHARGE);
        flinsConfig.minER = erTargets.getOrDefault(CharacterId.FLINS, 1.0);
        if (partyManualRolls.containsKey(CharacterId.FLINS)) {
            flinsConfig.manualRolls = partyManualRolls.get(CharacterId.FLINS);
        }
        ArtifactOptimizer.OptimizationResult resultFlins = ArtifactOptimizer.generate(
                flinsConfig,
                flins.getBaseStats(),
                flins.getWeapon().getStats(),
                new NightOfTheSkysUnveiling().getStats());
        flins.setArtifacts(new NightOfTheSkysUnveiling(resultFlins.stats));
        flins.setArtifactRolls(resultFlins.rolls);
        sim.addCharacter(flins);

        Ineffa ineffa = new Ineffa(new CalamityQueller(), null);
        ArtifactOptimizer.OptimizationConfig ineffaConfig = new ArtifactOptimizer.OptimizationConfig();
        ineffaConfig.mainStatSands = StatType.ATK_PERCENT;
        ineffaConfig.mainStatGoblet = StatType.ATK_PERCENT;
        ineffaConfig.mainStatCirclet = StatType.CRIT_RATE;
        ineffaConfig.subStatPriority = Arrays.asList(StatType.CRIT_DMG, StatType.CRIT_RATE,
                StatType.ATK_PERCENT, StatType.ENERGY_RECHARGE);
        ineffaConfig.minER = erTargets.getOrDefault(CharacterId.INEFFA, 1.0);
        if (partyManualRolls.containsKey(CharacterId.INEFFA)) {
            ineffaConfig.manualRolls = partyManualRolls.get(CharacterId.INEFFA);
        }
        ArtifactOptimizer.OptimizationResult resultIneffa = ArtifactOptimizer.generate(
                ineffaConfig,
                ineffa.getBaseStats(),
                ineffa.getWeapon().getStats(),
                new SilkenMoonsSerenade().getStats());
        ineffa.setArtifacts(new SilkenMoonsSerenade(resultIneffa.stats));
        ineffa.setArtifactRolls(resultIneffa.rolls);
        sim.addCharacter(ineffa);

        Columbina columbina = new Columbina(new NocturnesCurtainCall(), null);
        ArtifactOptimizer.OptimizationConfig colConfig = new ArtifactOptimizer.OptimizationConfig();
        colConfig.mainStatSands = StatType.ENERGY_RECHARGE;
        colConfig.mainStatGoblet = StatType.HP_PERCENT;
        colConfig.mainStatCirclet = StatType.CRIT_RATE;
        colConfig.subStatPriority = Arrays.asList(StatType.CRIT_DMG, StatType.CRIT_RATE,
                StatType.HP_PERCENT, StatType.ENERGY_RECHARGE);
        colConfig.minER = erTargets.getOrDefault(CharacterId.COLUMBINA, 1.0);
        if (partyManualRolls.containsKey(CharacterId.COLUMBINA)) {
            colConfig.manualRolls = partyManualRolls.get(CharacterId.COLUMBINA);
        }
        ArtifactOptimizer.OptimizationResult resultCol = ArtifactOptimizer.generate(
                colConfig,
                columbina.getBaseStats(),
                columbina.getWeapon().getStats(),
                new AubadeOfMorningstarAndMoon().getStats());
        columbina.setArtifacts(new AubadeOfMorningstarAndMoon(resultCol.stats));
        columbina.setArtifactRolls(resultCol.rolls);
        sim.addCharacter(columbina);

        Sucrose sucrose = new Sucrose(
                new SunnyMorningSleepIn(), null, () -> 4.0);
        ArtifactOptimizer.OptimizationConfig sucConfig = new ArtifactOptimizer.OptimizationConfig();
        sucConfig.mainStatSands = StatType.ELEMENTAL_MASTERY;
        sucConfig.mainStatGoblet = StatType.ELEMENTAL_MASTERY;
        sucConfig.mainStatCirclet = StatType.ELEMENTAL_MASTERY;
        sucConfig.subStatPriority = Arrays.asList(StatType.ELEMENTAL_MASTERY, StatType.ENERGY_RECHARGE);
        sucConfig.minER = erTargets.getOrDefault(CharacterId.SUCROSE, 1.0);
        if (partyManualRolls.containsKey(CharacterId.SUCROSE)) {
            sucConfig.manualRolls = partyManualRolls.get(CharacterId.SUCROSE);
        }
        ArtifactOptimizer.OptimizationResult resultSuc = ArtifactOptimizer.generate(
                sucConfig,
                sucrose.getBaseStats(),
                sucrose.getWeapon().getStats(),
                new ViridescentVenerer().getStats());
        sucrose.setArtifacts(new ViridescentVenerer(resultSuc.stats));
        sucrose.setArtifactRolls(resultSuc.rolls);
        sim.addCharacter(sucrose);

        ResonanceManager.applyResonances(sim);
    }
}
