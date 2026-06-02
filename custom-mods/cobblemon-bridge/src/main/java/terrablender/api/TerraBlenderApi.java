package terrablender.api;

/**
 * <b>Compatibility stub</b> for the Fabric-side TerraBlender entrypoint interface.
 *
 * <p>NeoForge TerraBlender 4.x doesn't ship this interface — its mod-discovery story is the
 * NeoForge {@code @Mod} class, not a Fabric-Loader-iterable entrypoint. But Sinytra Connector
 * loads Fabric mods (e.g. Legendary Monuments 7.8) whose classes implement
 * {@code terrablender.api.TerraBlenderApi}, compiled against the older Fabric-side TerraBlender
 * jar that DID export it. Without this stub, JVM class resolution of any such class throws
 * {@code NoClassDefFoundError: terrablender/api/TerraBlenderApi}.
 *
 * <p>This file lets that resolution succeed. It does NOT register anything with NeoForge
 * TerraBlender by itself — that's the job of {@link com.cobblemonbridge.adapters.LegendaryMonumentsTerraBlenderShim},
 * which reflectively invokes {@link #onTerraBlenderInitialized()} on the Fabric mod's
 * implementation at the right point in server startup.
 *
 * <p><b>Why an interface, not a class.</b> LM's bytecode declares this as its superinterface
 * ({@code implements terrablender.api.TerraBlenderApi}). Class resolution checks the supertype
 * exists and is a kind-compatible type — so the stub has to match the original kind (interface)
 * and package.
 *
 * <p><b>Removal condition.</b> Drop this file the day NeoForge TerraBlender ships
 * {@code terrablender.api.TerraBlenderApi} again. Until then, the JVM finds ours, no harm done.
 * If both classpaths ever ship one we'd hit a duplicate-class error at load — recoverable, but
 * grep for this file on TB upgrades.
 */
public interface TerraBlenderApi {
    /** Fabric-side entrypoint hook. Implementations call {@link Regions#register(Region)} here. */
    void onTerraBlenderInitialized();
}
