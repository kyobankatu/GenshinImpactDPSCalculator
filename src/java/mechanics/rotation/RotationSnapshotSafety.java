package mechanics.rotation;

import simulation.party.PartyDefinition;

/** Evaluates explicit exact-loadout admission for snapshot-backed search. */
public final class RotationSnapshotSafety {
    private RotationSnapshotSafety() {
    }

    /** Returns the fail-closed snapshot-search admission for one party loadout. */
    public static Assessment assess(PartyDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("definition must not be null");
        }
        String fingerprint = definition.loadoutFingerprint();
        if (!definition.supportsExactSnapshotRestore()) {
            return new Assessment(
                    false,
                    fingerprint,
                    "party loadout has no audited exact snapshot-restore contract");
        }
        return new Assessment(true, fingerprint, "audited exact snapshot restore");
    }

    /** Immutable admission decision carried by a rotation scenario. */
    public static final class Assessment {
        public final boolean admitted;
        public final String loadoutFingerprint;
        public final String reason;

        private Assessment(boolean admitted, String loadoutFingerprint, String reason) {
            if (loadoutFingerprint == null
                    || loadoutFingerprint.isBlank()
                    || reason == null
                    || reason.isBlank()) {
                throw new IllegalArgumentException("snapshot assessment fields are required");
            }
            this.admitted = admitted;
            this.loadoutFingerprint = loadoutFingerprint;
            this.reason = reason;
        }

        static Assessment unscoped(String fingerprint) {
            return new Assessment(false, fingerprint, "scenario has no party snapshot audit");
        }

        /** Rejects search while preserving source replay construction. */
        public void requireSearchAdmission() {
            if (!admitted) {
                throw new IllegalStateException(
                        "Rotation search snapshot admission rejected: "
                                + loadoutFingerprint + ": " + reason);
            }
        }
    }
}
