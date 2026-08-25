package simulation.party;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import mechanics.rotation.ActionCapabilityStore;
import mechanics.rotation.PolicyAction;
import model.type.CharacterId;

/**
 * Registry of simulator party definitions shared by sample runners and RL.
 */
public final class PartyCatalog {
    private static final Map<String, PartyDefinition> DEFINITIONS = new LinkedHashMap<>();
    private static final Map<String, DatasetSplit> FINGERPRINT_SPLITS = new HashMap<>();
    private static final ActionCapabilityStore ACTION_CAPABILITIES = new ActionCapabilityStore();

    static {
        register(new FlinsParty2Definition());
        register(new RaidenPartyDefinition());
        register(new FlinsPartyDefinition());
        register(new FlinsYelanLunarChargedPartyDefinition());
        register(new FlinsMonaLunarChargedPartyDefinition());
        register(new AlhaithamHyperbloomPartyDefinition());
        register(new AlhaithamYelanQuickbloomPartyDefinition());
        register(new XiaoPlungePartyDefinition());
        register(new XiaoXianyunPartyDefinition());
        register(new XiaoFurinaPartyDefinition());
        register(new XiaoLanYanPartyDefinition());
        register(new WandererLanYanPartyDefinition());
        register(new FaruzanLanYanQuickswapPartyDefinition());
        register(new WandererDoubleHydroPartyDefinition());
        register(new WandererLaylaPartyDefinition());
        register(new WandererZhongliPartyDefinition());
        register(new WandererThomaPartyDefinition());
        register(new WandererVentiPartyDefinition());
        register(new WandererJeanPartyDefinition());
        register(new AyatoFurinaTaserPartyDefinition());
        register(new AyatoDoubleHydroPartyDefinition());
        register(new AyatoJeanTaserPartyDefinition());
        register(new AyatoOvervapePartyDefinition());
        register(new DilucXianyunVaporizePartyDefinition());
        register(new HuTaoXianyunVaporizePartyDefinition());
        register(new HuTaoLaylaVapemeltPartyDefinition());
        register(new ArlecchinoOverloadPartyDefinition());
        register(new ArlecchinoVaporizePartyDefinition());
        register(new ArlecchinoCitlaliMeltPartyDefinition());
        register(new ArlecchinoEmilieMonoPyroPartyDefinition());
        register(new ArlecchinoMonaVaporizePartyDefinition());
        register(new ArlecchinoMonaOvervapePartyDefinition());
        register(new ArlecchinoLaylaVapemeltPartyDefinition());
        register(new KeqingChevreuseOverloadPartyDefinition());
        register(new KeqingLanYanAggravatePartyDefinition());
        register(new YoimiyaChevreuseOverloadPartyDefinition());
        register(new YoimiyaDoubleHydroPartyDefinition());
        register(new YoimiyaIneffaVaporizePartyDefinition());
        register(new YoimiyaChioriPartyDefinition());
        register(new YoimiyaFischlVaporizePartyDefinition());
        register(new YoimiyaFurinaVaporizePartyDefinition());
        register(new YoimiyaEmilieBurningPartyDefinition());
        register(new YoimiyaLaylaVapemeltPartyDefinition());
        register(new SucroseElectroChargedPartyDefinition());
        register(new SucroseDoubleHydroTaserPartyDefinition());
        register(new ColleiSucroseHyperbloomPartyDefinition());
        register(new NahidaBeidouHyperbloomPartyDefinition());
        register(new NahidaDoubleHydroHyperbloomPartyDefinition());
        register(new NahidaRaidenDoubleHydroHyperbloomPartyDefinition());
        register(new NaviaZhongliPartyDefinition());
        register(new NaviaChioriPartyDefinition());
        register(new NaviaChioriQuickPartyDefinition());
        register(new NaviaChioriPlungePartyDefinition());
        register(new NaviaFurinaPartyDefinition());
        register(new NaviaDoubleHydroPartyDefinition());
        register(new ArlecchinoLunarChargedPartyDefinition());
        register(new ArlecchinoIneffaOverloadPartyDefinition());
        register(new AlhaithamSpreadPartyDefinition());
        register(new GanyuFreezePartyDefinition());
        register(new GamingMeltPartyDefinition());
        register(new TighnariSpreadPartyDefinition());
        register(new NingguangCrystallizePartyDefinition());
    }

    private PartyCatalog() {
    }

    public static void register(PartyDefinition definition) {
        validate(definition);
        String key = normalize(definition.name());
        if (DEFINITIONS.containsKey(key)) {
            throw new IllegalArgumentException("Duplicate party name: " + definition.name());
        }
        DatasetSplit previous = FINGERPRINT_SPLITS.putIfAbsent(
                definition.loadoutFingerprint(), definition.datasetSplit());
        if (previous != null) {
            String detail = previous == definition.datasetSplit()
                    ? "Duplicate loadout fingerprint: "
                    : "Cross-split loadout fingerprint: ";
            throw new IllegalArgumentException(detail + definition.loadoutFingerprint());
        }
        DEFINITIONS.put(key, definition);
    }

    public static PartyDefinition require(String partyName) {
        PartyDefinition definition = find(partyName);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown party: " + partyName + ". Available parties: "
                    + availablePartyNames());
        }
        return definition;
    }

    public static PartyDefinition find(String partyName) {
        if (partyName == null || partyName.isBlank()) {
            return null;
        }
        return DEFINITIONS.get(normalize(partyName));
    }

    public static Collection<PartyDefinition> all() {
        return new ArrayList<>(DEFINITIONS.values());
    }

    public static List<PartyDefinition> rlEnabled() {
        return DEFINITIONS.values().stream()
                .filter(PartyDefinition::rlEnabled)
                .collect(Collectors.toList());
    }

    /** Returns RL-enabled definitions assigned to one immutable dataset split. */
    public static List<PartyDefinition> rlEnabled(DatasetSplit split) {
        if (split == null) {
            throw new IllegalArgumentException("Dataset split must not be null");
        }
        return DEFINITIONS.values().stream()
                .filter(PartyDefinition::rlEnabled)
                .filter(definition -> definition.datasetSplit() == split)
                .collect(Collectors.toList());
    }

    public static String availablePartyNames() {
        return DEFINITIONS.values().stream()
                .map(PartyDefinition::name)
                .collect(Collectors.joining(", "));
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private static void validate(PartyDefinition definition) {
        if (definition == null || definition.name() == null || definition.name().isBlank()
                || definition.displayName() == null || definition.displayName().isBlank()
                || definition.datasetSplit() == null
                || definition.loadoutFingerprint() == null
                || definition.loadoutFingerprint().isBlank()
                || !Double.isFinite(definition.rotationCycleSeconds())
                || definition.rotationCycleSeconds() <= 0.0) {
            throw new IllegalArgumentException("Invalid party definition metadata");
        }
        CharacterId[] order = definition.partyOrder();
        if (order == null || order.length != 4) {
            throw new IllegalArgumentException("Party must contain exactly four slots: " + definition.name());
        }
        Set<CharacterId> unique = new HashSet<>();
        for (CharacterId characterId : order) {
            if (characterId == null || !unique.add(characterId)) {
                throw new IllegalArgumentException("Party slots must be non-null and unique: " + definition.name());
            }
            if (!ACTION_CAPABILITIES.contains(characterId)) {
                throw new IllegalArgumentException(
                        "Missing action capabilities for " + characterId + " in " + definition.name());
            }
        }
        Map<CharacterId, Set<PolicyAction>> required = definition.requiredActionCapabilities();
        if (required == null || !unique.containsAll(required.keySet())) {
            throw new IllegalArgumentException("Required actions reference a non-party character: " + definition.name());
        }
        for (Map.Entry<CharacterId, Set<PolicyAction>> entry : required.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isEmpty()) {
                throw new IllegalArgumentException("Required action set must not be empty: " + entry.getKey());
            }
            for (PolicyAction action : entry.getValue()) {
                if (action == null || !action.requiresCharacterCapability()
                        || !ACTION_CAPABILITIES.supports(entry.getKey(), action)) {
                    throw new IllegalArgumentException(
                            "Unsupported required action " + action + " for " + entry.getKey());
                }
            }
        }
        int activeSlot = 0;
        for (int actionId : definition.baselinePolicyActions()) {
            PolicyAction action = PolicyAction.fromId(actionId);
            if (action.isSwap()) {
                activeSlot = action.getTargetSlot();
            } else if (action.requiresCharacterCapability()
                    && !ACTION_CAPABILITIES.supports(order[activeSlot], action)) {
                throw new IllegalArgumentException(
                        "Baseline action " + action + " is unsupported for " + order[activeSlot]);
            }
        }
    }
}
