package model.type;

/**
 * Weapon category used to determine the expected flat energy generated per
 * Normal or Charged Attack hit (the "NA energy" mechanic).
 *
 * <p>Expected energy per hit (1 / average hits to generate 1 Energy):
 * <ul>
 *   <li>SWORD   – base 10 %, +5 % per fail → avg 4.52 hits → ~0.2212 energy/hit</li>
 *   <li>BOW     – base  0 %, +5 % per fail → avg 6.29 hits → ~0.1590 energy/hit</li>
 *   <li>CLAYMORE– base  0 %, +10% per fail → avg 4.66 hits → ~0.2146 energy/hit</li>
 *   <li>POLEARM – base  0 %, +4 % per fail → avg 6.95 hits → ~0.1439 energy/hit</li>
 *   <li>CATALYST– base  0 %, +10% per fail → avg 4.66 hits → ~0.2146 energy/hit</li>
 * </ul>
 */
public enum WeaponType {
    SWORD,
    BOW,
    CLAYMORE,
    POLEARM,
    CATALYST
}
