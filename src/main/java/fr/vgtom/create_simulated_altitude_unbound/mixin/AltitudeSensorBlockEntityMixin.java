package fr.vgtom.create_simulated_altitude_unbound.mixin;

import java.util.List;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.simulated_team.simulated.content.blocks.altitude_sensor.AltitudeSensorBlockEntity;
import dev.simulated_team.simulated.data.SimLang;
import fr.vgtom.create_simulated_altitude_unbound.AltitudeUnbound;
import fr.vgtom.create_simulated_altitude_unbound.content.AltitudeRange;
import fr.vgtom.create_simulated_altitude_unbound.duck.CalibratableSensor;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives one sensor an altitude scale of its own.
 *
 * <p>Both conversions are replaced wholesale rather than patched piecemeal: everything the sensor
 * reports — {@code getValue}, the redstone signal, the numbers on the screen's sliders, the display
 * source, the ComputerCraft peripheral — is built from {@code toNormalHeight} and
 * {@code toWorldHeight}, so swapping the two end points of that mapping is the whole mechanism.
 */
@Mixin(AltitudeSensorBlockEntity.class)
public abstract class AltitudeSensorBlockEntityMixin implements CalibratableSensor {
    @Unique
    private AltitudeRange altitudeUnbound$range = AltitudeRange.AUTO;

    @Override
    public AltitudeRange altitudeUnbound$getRange() {
        return this.altitudeUnbound$range;
    }

    @Override
    public void altitudeUnbound$setRange(final AltitudeRange range) {
        this.altitudeUnbound$range = range == null ? AltitudeRange.AUTO : range;
    }

    @Override
    public boolean altitudeUnbound$isOperableBy(final Player player, final double maxDistance) {
        final AltitudeSensorBlockEntity self = (AltitudeSensorBlockEntity) (Object) this;
        final BlockPos pos = self.getBlockPos();
        final double maxSquared = maxDistance * maxDistance;

        if (player.distanceToSqr(Vec3.atCenterOf(pos)) <= maxSquared) {
            return true;
        }

        final Level level = self.getLevel();
        if (level == null) {
            return false;
        }

        // Where the sensor really is, which for one riding a structure is nowhere near its block
        // coordinates. Same projection the sensor uses to read its own altitude.
        final Vector3d world = Sable.HELPER.projectOutOfSubLevel(level, JOMLConversion.atCenterOf(pos));
        return player.distanceToSqr(world.x, world.y, world.z) <= maxSquared;
    }

    @Override
    public void altitudeUnbound$applyRange(final AltitudeRange range) {
        this.altitudeUnbound$setRange(range);
        // The sensor overrides notifyUpdate to re-evaluate its redstone output, so this both pushes
        // the new scale to every watching client and settles the signal in one call.
        ((AltitudeSensorBlockEntity) (Object) this).notifyUpdate();
    }

    /**
     * Maps a world Y onto the sensor's 0..1 scale over the calibrated range instead of the build limits.
     */
    @Inject(method = "toNormalHeight", at = @At("HEAD"), cancellable = true)
    private void altitudeUnbound$toNormalHeight(final float worldHeight, final CallbackInfoReturnable<Float> cir) {
        final AltitudeRange range = this.altitudeUnbound$range;
        if (!range.isUsable()) {
            return;
        }
        cir.setReturnValue(Mth.map(worldHeight, range.min(), range.max(), 0.0F, 1.0F));
    }

    /**
     * The inverse, which is what turns a slider position back into the altitude printed next to it.
     */
    @Inject(method = "toWorldHeight", at = @At("HEAD"), cancellable = true)
    private void altitudeUnbound$toWorldHeight(final float normalHeight, final CallbackInfoReturnable<Float> cir) {
        final AltitudeRange range = this.altitudeUnbound$range;
        if (!range.isUsable()) {
            return;
        }
        cir.setReturnValue(Mth.map(normalHeight, 0.0F, 1.0F, range.min(), range.max()));
    }

    @Inject(method = "write", at = @At("TAIL"))
    private void altitudeUnbound$write(final CompoundTag tag, final HolderLookup.Provider registries,
                                       final boolean clientPacket, final CallbackInfo ci) {
        this.altitudeUnbound$range.write(tag);
    }

    @Inject(method = "read", at = @At("TAIL"))
    private void altitudeUnbound$read(final CompoundTag tag, final HolderLookup.Provider registries,
                                      final boolean clientPacket, final CallbackInfo ci) {
        this.altitudeUnbound$range = AltitudeRange.read(tag);
    }

    /**
     * Carries the calibration along when a sensor is copied with a Clipboard, since the signal window
     * it travels with is meaningless without the scale it was measured against.
     */
    @Inject(method = "writeToClipboard", at = @At("TAIL"))
    private void altitudeUnbound$writeToClipboard(final HolderLookup.Provider registries, final CompoundTag tag,
                                                  final Direction side, final CallbackInfoReturnable<Boolean> cir) {
        this.altitudeUnbound$range.write(tag);
    }

    @Inject(method = "readFromClipboard", at = @At("RETURN"))
    private void altitudeUnbound$readFromClipboard(final HolderLookup.Provider registries, final CompoundTag tag,
                                                   final Player player, final Direction side, final boolean simulate,
                                                   final CallbackInfoReturnable<Boolean> cir) {
        if (simulate) {
            return;
        }
        this.altitudeUnbound$range = AltitudeRange.read(tag);
    }

    /**
     * Adds the scale in force under the height and air pressure Simulated already shows.
     */
    @Inject(method = "addToGoggleTooltip", at = @At("TAIL"))
    private void altitudeUnbound$addToGoggleTooltip(final List<Component> tooltip, final boolean isPlayerSneaking,
                                                    final CallbackInfoReturnable<Boolean> cir) {
        final Level level = ((AltitudeSensorBlockEntity) (Object) this).getLevel();
        if (level == null) {
            return;
        }

        final AltitudeRange effective = this.altitudeUnbound$range.resolve(level);
        final String modeKey = effective.custom() ? "mode.custom" : "mode.auto";

        SimLang.translate(AltitudeUnbound.MODID + ".altitude_sensor.scale",
                        Component.literal(String.format("%.0f", effective.min())).withStyle(ChatFormatting.AQUA),
                        Component.literal(String.format("%.0f", effective.max())).withStyle(ChatFormatting.AQUA))
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip, 2);

        SimLang.translate(AltitudeUnbound.MODID + ".altitude_sensor." + modeKey)
                .style(effective.custom() ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY)
                .forGoggles(tooltip, 2);
    }
}
