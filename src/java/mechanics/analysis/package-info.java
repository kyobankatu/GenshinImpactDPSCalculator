/**
 * Analysis snapshots should keep typed runtime identity and avoid embedding
 * report-facing display labels except where the sampled payload itself is
 * presentation data, such as buff display names.
 *
 * <p>Conversion to report labels belongs in {@code visualization}.
 */
package mechanics.analysis;
