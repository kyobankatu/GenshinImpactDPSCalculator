package mechanics.rl;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
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
    public static final model.type.CharacterId[] PARTY_ORDER = {
            model.type.CharacterId.FLINS,
            model.type.CharacterId.INEFFA,
            model.type.CharacterId.COLUMBINA,
            model.type.CharacterId.SUCROSE
    };

    private static final CachedBuild FLINS_BUILD = createFlinsBuild();
    private static final CachedBuild INEFFA_BUILD = createIneffaBuild();
    private static final CachedBuild COLUMBINA_BUILD = createColumbinaBuild();
    private static final CachedBuild SUCROSE_BUILD = createSucroseBuild();

    private FlinsParty2RLSimulatorFactory() {
    }

    public static Supplier<CombatSimulator> supplier() {
        return FlinsParty2RLSimulatorFactory::create;
    }

    public static RLPartySpec spec() {
        return new RLPartySpec("FlinsParty2", PARTY_ORDER, supplier());
    }

    public static RLEpisodeFactory episodeFactory(EpisodeConfig baseConfig) {
        return new SinglePartyRLEpisodeFactory(spec(), baseConfig);
    }

    public static CombatSimulator create() {
        CombatSimulator sim = new CombatSimulator();
        sim.setLoggingEnabled(false);
        sim.setEnemy(new Enemy(90));
        setupParty(sim);
        return sim;
    }

    private static void setupParty(CombatSimulator sim) {
        model.character.Flins flins = new model.character.Flins(new model.weapon.PrimordialJadeWingedSpear(), null);
        flins.setArtifacts(new model.artifact.NightOfTheSkysUnveiling(copyStats(FLINS_BUILD.stats)));
        flins.setArtifactRolls(new HashMap<>(FLINS_BUILD.rolls));
        sim.addCharacter(flins);

        model.character.Ineffa ineffa = new model.character.Ineffa(new model.weapon.CalamityQueller(), null);
        ineffa.setArtifacts(new model.artifact.SilkenMoonsSerenade(copyStats(INEFFA_BUILD.stats)));
        ineffa.setArtifactRolls(new HashMap<>(INEFFA_BUILD.rolls));
        sim.addCharacter(ineffa);

        model.character.Columbina columbina = new model.character.Columbina(
                new model.weapon.NocturnesCurtainCall(), null);
        columbina.setArtifacts(new model.artifact.AubadeOfMorningstarAndMoon(copyStats(COLUMBINA_BUILD.stats)));
        columbina.setArtifactRolls(new HashMap<>(COLUMBINA_BUILD.rolls));
        sim.addCharacter(columbina);

        model.character.Sucrose sucrose = new model.character.Sucrose(new model.weapon.SunnyMorningSleepIn(), null);
        sucrose.setArtifacts(new model.artifact.ViridescentVenerer(copyStats(SUCROSE_BUILD.stats)));
        sucrose.setArtifactRolls(new HashMap<>(SUCROSE_BUILD.rolls));
        sim.addCharacter(sucrose);

        ResonanceManager.applyResonances(sim);
        sim.updateMoonsign();
    }

    private static CachedBuild createFlinsBuild() {
        model.character.Flins flins = new model.character.Flins(new model.weapon.PrimordialJadeWingedSpear(), null);
        ArtifactOptimizer.OptimizationConfig config = new ArtifactOptimizer.OptimizationConfig();
        config.mainStatSands = StatType.ATK_PERCENT;
        config.mainStatGoblet = StatType.ATK_PERCENT;
        config.mainStatCirclet = StatType.CRIT_DMG;
        config.subStatPriority = Arrays.asList(StatType.CRIT_DMG, StatType.CRIT_RATE,
                StatType.ATK_PERCENT, StatType.ENERGY_RECHARGE);
        config.minER = 1.0;
        ArtifactOptimizer.OptimizationResult result = ArtifactOptimizer.generate(
                config, flins.getBaseStats(), flins.getWeapon().getStats(), new StatsContainer());
        return new CachedBuild(copyStats(result.stats), new HashMap<>(result.rolls));
    }

    private static CachedBuild createIneffaBuild() {
        model.character.Ineffa ineffa = new model.character.Ineffa(new model.weapon.CalamityQueller(), null);
        ArtifactOptimizer.OptimizationConfig config = new ArtifactOptimizer.OptimizationConfig();
        config.mainStatSands = StatType.ATK_PERCENT;
        config.mainStatGoblet = StatType.ATK_PERCENT;
        config.mainStatCirclet = StatType.CRIT_RATE;
        config.subStatPriority = Arrays.asList(StatType.CRIT_DMG, StatType.CRIT_RATE,
                StatType.ATK_PERCENT, StatType.ENERGY_RECHARGE);
        config.minER = 1.2;
        ArtifactOptimizer.OptimizationResult result = ArtifactOptimizer.generate(
                config, ineffa.getBaseStats(), ineffa.getWeapon().getStats(), new StatsContainer());
        return new CachedBuild(copyStats(result.stats), new HashMap<>(result.rolls));
    }

    private static CachedBuild createColumbinaBuild() {
        model.character.Columbina columbina = new model.character.Columbina(
                new model.weapon.NocturnesCurtainCall(), null);
        ArtifactOptimizer.OptimizationConfig config = new ArtifactOptimizer.OptimizationConfig();
        config.mainStatSands = StatType.ENERGY_RECHARGE;
        config.mainStatGoblet = StatType.HP_PERCENT;
        config.mainStatCirclet = StatType.CRIT_RATE;
        config.subStatPriority = Arrays.asList(StatType.CRIT_DMG, StatType.CRIT_RATE,
                StatType.HP_PERCENT, StatType.ENERGY_RECHARGE);
        config.minER = 1.6;
        ArtifactOptimizer.OptimizationResult result = ArtifactOptimizer.generate(
                config, columbina.getBaseStats(), columbina.getWeapon().getStats(), new StatsContainer());
        return new CachedBuild(copyStats(result.stats), new HashMap<>(result.rolls));
    }

    private static CachedBuild createSucroseBuild() {
        model.character.Sucrose sucrose = new model.character.Sucrose(new model.weapon.SunnyMorningSleepIn(), null);
        ArtifactOptimizer.OptimizationConfig config = new ArtifactOptimizer.OptimizationConfig();
        config.mainStatSands = StatType.ELEMENTAL_MASTERY;
        config.mainStatGoblet = StatType.ELEMENTAL_MASTERY;
        config.mainStatCirclet = StatType.ELEMENTAL_MASTERY;
        config.subStatPriority = Arrays.asList(StatType.ELEMENTAL_MASTERY, StatType.ENERGY_RECHARGE);
        config.minER = 1.4;
        ArtifactOptimizer.OptimizationResult result = ArtifactOptimizer.generate(
                config, sucrose.getBaseStats(), sucrose.getWeapon().getStats(), new StatsContainer());
        return new CachedBuild(copyStats(result.stats), new HashMap<>(result.rolls));
    }

    private static StatsContainer copyStats(StatsContainer source) {
        StatsContainer copy = new StatsContainer();
        source.forEach(copy::add);
        return copy;
    }

    private static class CachedBuild {
        private final StatsContainer stats;
        private final Map<StatType, Integer> rolls;

        private CachedBuild(StatsContainer stats, Map<StatType, Integer> rolls) {
            this.stats = stats;
            this.rolls = rolls;
        }
    }
}
