package fr.vgtom.create_simulated_altitude_unbound.mixin;

import java.util.List;
import java.util.Set;

import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Keeps the jar harmless when Create Simulated is not installed.
 *
 * <p>Every mixin here targets a Simulated class, so without that mod the config has nothing to apply
 * to and would only produce load errors. Declaring the dependency optional in the mod metadata is
 * not enough on its own — mixin runs before any of that is consulted — so the mod list is checked
 * here instead.
 */
public class AltitudeUnboundMixinPlugin implements IMixinConfigPlugin {
    private boolean simulatedPresent;

    @Override
    public void onLoad(final String mixinPackage) {
        this.simulatedPresent = LoadingModList.get().getModFileById("simulated") != null;
    }

    @Override
    public boolean shouldApplyMixin(final String targetClassName, final String mixinClassName) {
        return this.simulatedPresent;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(final Set<String> myTargets, final Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(final String targetClassName, final ClassNode targetClass, final String mixinClassName,
                         final IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(final String targetClassName, final ClassNode targetClass, final String mixinClassName,
                          final IMixinInfo mixinInfo) {
    }
}
