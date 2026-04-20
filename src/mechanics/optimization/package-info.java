/**
 * Optimization is allowed to depend on external profile-file formats through
 * dedicated adapters such as {@link mechanics.optimization.ProfileFileAdapter}.
 *
 * <p>Internal search logic should prefer typed identifiers like
 * {@link model.type.CharacterId} and keep file-name translation at the package
 * boundary.
 */
package mechanics.optimization;
