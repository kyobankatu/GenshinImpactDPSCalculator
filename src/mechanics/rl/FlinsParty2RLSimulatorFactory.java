package mechanics.rl;

import java.util.Arrays;
import java.util.function.Supplier;

import mechanics.element.ResonanceManager;
import mechanics.optimization.ArtifactOptimizer;
import model.entity.Enemy;
import model.stats.StatsContainer;
import model.type.StatType;
import simulation.CombatSimulator;

/**
 * Creates the fixed FlinsParty2 simulator used by Java-native RL.
 */
public final class FlinsParty2RLSimulatorFactory {
    private FlinsParty2RLSimulatorFactory() {
    }

    public static Supplier<CombatSimulator> supplier() {
        return FlinsParty2RLSimulatorFactory::create;
    }

    public static CombatSimulator create() {
        CombatSimulator sim = new CombatSimulator();
        sim.setEnemy(new Enemy(90));
        setupParty(sim);
        return sim;
    }

    private static void setupParty(CombatSimulator sim) {
        model.character.Flins flins = new model.character.Flins(new model.weapon.PrimordialJadeWingedSpear(), null);
        ArtifactOptimizer.OptimizationConfig flinsConfig = new ArtifactOptimizer.OptimizationConfig();
        flinsConfig.mainStatSands = StatType.ATK_PERCENT;
        flinsConfig.mainStatGoblet = StatType.ATK_PERCENT;
        flinsConfig.mainStatCirclet = StatType.CRIT_DMG;
        flinsConfig.subStatPriority = Arrays.asList(StatType.CRIT_DMG, StatType.CRIT_RATE,
                StatType.ATK_PERCENT, StatType.ENERGY_RECHARGE);
        flinsConfig.minER = 1.0;
        ArtifactOptimizer.OptimizationResult resultFlins = ArtifactOptimizer.generate(
                flinsConfig, flins.getBaseStats(), flins.getWeapon().getStats(), new StatsContainer());
        flins.setArtifacts(new model.artifact.NightOfTheSkysUnveiling(resultFlins.stats));
        flins.setArtifactRolls(resultFlins.rolls);
        sim.addCharacter(flins);

        model.character.Ineffa ineffa = new model.character.Ineffa(new model.weapon.CalamityQueller(), null);
        ArtifactOptimizer.OptimizationConfig ineffaConfig = new ArtifactOptimizer.OptimizationConfig();
        ineffaConfig.mainStatSands = StatType.ATK_PERCENT;
        ineffaConfig.mainStatGoblet = StatType.ATK_PERCENT;
        ineffaConfig.mainStatCirclet = StatType.CRIT_RATE;
        ineffaConfig.subStatPriority = Arrays.asList(StatType.CRIT_DMG, StatType.CRIT_RATE,
                StatType.ATK_PERCENT, StatType.ENERGY_RECHARGE);
        ineffaConfig.minER = 1.2;
        ArtifactOptimizer.OptimizationResult resultIneffa = ArtifactOptimizer.generate(
                ineffaConfig, ineffa.getBaseStats(), ineffa.getWeapon().getStats(), new StatsContainer());
        ineffa.setArtifacts(new model.artifact.SilkenMoonsSerenade(resultIneffa.stats));
        ineffa.setArtifactRolls(resultIneffa.rolls);
        sim.addCharacter(ineffa);

        model.character.Columbina columbina = new model.character.Columbina(
                new model.weapon.NocturnesCurtainCall(), null);
        ArtifactOptimizer.OptimizationConfig colConfig = new ArtifactOptimizer.OptimizationConfig();
        colConfig.mainStatSands = StatType.ENERGY_RECHARGE;
        colConfig.mainStatGoblet = StatType.HP_PERCENT;
        colConfig.mainStatCirclet = StatType.CRIT_RATE;
        colConfig.subStatPriority = Arrays.asList(StatType.CRIT_DMG, StatType.CRIT_RATE,
                StatType.HP_PERCENT, StatType.ENERGY_RECHARGE);
        colConfig.minER = 1.6;
        ArtifactOptimizer.OptimizationResult resultCol = ArtifactOptimizer.generate(
                colConfig, columbina.getBaseStats(), columbina.getWeapon().getStats(), new StatsContainer());
        columbina.setArtifacts(new model.artifact.AubadeOfMorningstarAndMoon(resultCol.stats));
        columbina.setArtifactRolls(resultCol.rolls);
        sim.addCharacter(columbina);

        model.character.Sucrose sucrose = new model.character.Sucrose(new model.weapon.SunnyMorningSleepIn(), null);
        ArtifactOptimizer.OptimizationConfig sucConfig = new ArtifactOptimizer.OptimizationConfig();
        sucConfig.mainStatSands = StatType.ELEMENTAL_MASTERY;
        sucConfig.mainStatGoblet = StatType.ELEMENTAL_MASTERY;
        sucConfig.mainStatCirclet = StatType.ELEMENTAL_MASTERY;
        sucConfig.subStatPriority = Arrays.asList(StatType.ELEMENTAL_MASTERY, StatType.ENERGY_RECHARGE);
        sucConfig.minER = 1.4;
        ArtifactOptimizer.OptimizationResult resultSuc = ArtifactOptimizer.generate(
                sucConfig, sucrose.getBaseStats(), sucrose.getWeapon().getStats(), new StatsContainer());
        sucrose.setArtifacts(new model.artifact.ViridescentVenerer(resultSuc.stats));
        sucrose.setArtifactRolls(resultSuc.rolls);
        sim.addCharacter(sucrose);

        ResonanceManager.applyResonances(sim);
        sim.updateMoonsign();
    }
}
