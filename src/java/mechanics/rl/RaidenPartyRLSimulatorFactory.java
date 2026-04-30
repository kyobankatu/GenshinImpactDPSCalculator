package mechanics.rl;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import mechanics.element.ResonanceManager;
import mechanics.optimization.ArtifactOptimizer;
import model.entity.Enemy;
import model.stats.StatsContainer;
import model.type.CharacterId;
import model.type.StatType;
import simulation.CombatSimulator;

/**
 * Creates the fixed Raiden/Xingqiu/Xiangling/Bennett simulator used by multi-party RL.
 */
public final class RaidenPartyRLSimulatorFactory {
    public static final CharacterId[] PARTY_ORDER = {
            CharacterId.RAIDEN_SHOGUN,
            CharacterId.XINGQIU,
            CharacterId.XIANGLING,
            CharacterId.BENNETT
    };

    private static final CachedBuild RAIDEN_BUILD = createRaidenBuild();
    private static final CachedBuild XINGQIU_BUILD = createXingqiuBuild();
    private static final CachedBuild XIANGLING_BUILD = createXianglingBuild();
    private static final CachedBuild BENNETT_BUILD = createBennettBuild();

    private RaidenPartyRLSimulatorFactory() {
    }

    public static Supplier<CombatSimulator> supplier() {
        return RaidenPartyRLSimulatorFactory::create;
    }

    public static RLPartySpec spec() {
        return new RLPartySpec("RaidenParty", PARTY_ORDER, supplier());
    }

    public static CombatSimulator create() {
        CombatSimulator sim = new CombatSimulator();
        sim.setEnemy(new Enemy(90));
        setupParty(sim);
        sim.updateMoonsign();
        return sim;
    }

    public static RLEpisodeFactory episodeFactory(EpisodeConfig baseConfig) {
        return new SinglePartyRLEpisodeFactory(spec(), baseConfig);
    }

    private static void setupParty(CombatSimulator sim) {
        model.character.RaidenShogun raiden = new model.character.RaidenShogun(new model.weapon.SkywardSpine(), null);
        raiden.setArtifacts(new model.artifact.EmblemOfSeveredFate(copyStats(RAIDEN_BUILD.stats)));
        raiden.setArtifactRolls(new HashMap<>(RAIDEN_BUILD.rolls));
        sim.addCharacter(raiden);

        model.character.Xingqiu xingqiu = new model.character.Xingqiu(new model.weapon.WolfFang(), null);
        xingqiu.setArtifacts(new model.artifact.EmblemOfSeveredFate(copyStats(XINGQIU_BUILD.stats)));
        xingqiu.setArtifactRolls(new HashMap<>(XINGQIU_BUILD.rolls));
        sim.addCharacter(xingqiu);

        model.character.Xiangling xiangling = new model.character.Xiangling(new model.weapon.TheCatch(), null);
        xiangling.setArtifacts(new model.artifact.EmblemOfSeveredFate(copyStats(XIANGLING_BUILD.stats)));
        xiangling.setArtifactRolls(new HashMap<>(XIANGLING_BUILD.rolls));
        sim.addCharacter(xiangling);

        model.character.Bennett bennett = new model.character.Bennett(new model.weapon.SkywardBlade(), null);
        bennett.setArtifacts(new model.artifact.NoblesseOblige(copyStats(BENNETT_BUILD.stats)));
        bennett.setArtifactRolls(new HashMap<>(BENNETT_BUILD.rolls));
        sim.addCharacter(bennett);

        ResonanceManager.applyResonances(sim);
    }

    private static CachedBuild createRaidenBuild() {
        model.character.RaidenShogun raiden = new model.character.RaidenShogun(new model.weapon.SkywardSpine(), null);
        ArtifactOptimizer.OptimizationConfig config = new ArtifactOptimizer.OptimizationConfig();
        config.mainStatSands = StatType.ENERGY_RECHARGE;
        config.mainStatGoblet = StatType.ELECTRO_DMG_BONUS;
        config.mainStatCirclet = StatType.CRIT_RATE;
        config.subStatPriority = Arrays.asList(
                StatType.ENERGY_RECHARGE, StatType.CRIT_RATE, StatType.CRIT_DMG, StatType.ATK_PERCENT);
        config.minER = 2.50;
        ArtifactOptimizer.OptimizationResult result = ArtifactOptimizer.generate(
                config, raiden.getBaseStats(), raiden.getWeapon().getStats(), new StatsContainer());
        return new CachedBuild(copyStats(result.stats), new HashMap<>(result.rolls));
    }

    private static CachedBuild createXingqiuBuild() {
        model.character.Xingqiu xingqiu = new model.character.Xingqiu(new model.weapon.WolfFang(), null);
        ArtifactOptimizer.OptimizationConfig config = new ArtifactOptimizer.OptimizationConfig();
        config.mainStatSands = StatType.ATK_PERCENT;
        config.mainStatGoblet = StatType.HYDRO_DMG_BONUS;
        config.mainStatCirclet = StatType.CRIT_RATE;
        config.subStatPriority = Arrays.asList(
                StatType.ENERGY_RECHARGE, StatType.CRIT_RATE, StatType.CRIT_DMG, StatType.ATK_PERCENT);
        config.minER = 1.5;
        ArtifactOptimizer.OptimizationResult result = ArtifactOptimizer.generate(
                config, xingqiu.getBaseStats(), xingqiu.getWeapon().getStats(), new StatsContainer());
        return new CachedBuild(copyStats(result.stats), new HashMap<>(result.rolls));
    }

    private static CachedBuild createXianglingBuild() {
        model.character.Xiangling xiangling = new model.character.Xiangling(new model.weapon.TheCatch(), null);
        ArtifactOptimizer.OptimizationConfig config = new ArtifactOptimizer.OptimizationConfig();
        config.mainStatSands = StatType.ATK_PERCENT;
        config.mainStatGoblet = StatType.PYRO_DMG_BONUS;
        config.mainStatCirclet = StatType.CRIT_RATE;
        config.subStatPriority = Arrays.asList(
                StatType.ENERGY_RECHARGE, StatType.CRIT_RATE, StatType.CRIT_DMG,
                StatType.ATK_PERCENT, StatType.ELEMENTAL_MASTERY);
        config.minER = 1.6;
        ArtifactOptimizer.OptimizationResult result = ArtifactOptimizer.generate(
                config, xiangling.getBaseStats(), xiangling.getWeapon().getStats(), new StatsContainer());
        return new CachedBuild(copyStats(result.stats), new HashMap<>(result.rolls));
    }

    private static CachedBuild createBennettBuild() {
        model.character.Bennett bennett = new model.character.Bennett(new model.weapon.SkywardBlade(), null);
        ArtifactOptimizer.OptimizationConfig config = new ArtifactOptimizer.OptimizationConfig();
        config.mainStatSands = StatType.ENERGY_RECHARGE;
        config.mainStatGoblet = StatType.HP_PERCENT;
        config.mainStatCirclet = StatType.HP_PERCENT;
        config.subStatPriority = Arrays.asList(StatType.ENERGY_RECHARGE, StatType.HP_PERCENT, StatType.HP_FLAT);
        config.minER = 1.8;
        ArtifactOptimizer.OptimizationResult result = ArtifactOptimizer.generate(
                config, bennett.getBaseStats(), bennett.getWeapon().getStats(), new StatsContainer());
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
