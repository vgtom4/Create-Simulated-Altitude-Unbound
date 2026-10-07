package fr.vgtom.create_simulated_altitude_unbound.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.ContraptionMatrices;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import dev.simulated_team.simulated.content.blocks.altitude_sensor.AltitudeSensorMovementBehaviour;
import fr.vgtom.create_simulated_altitude_unbound.content.AltitudeRange;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Keeps the needle honest on a sensor that is riding a Create contraption.
 *
 * <p>The contraption renderer does not go through the block entity, so it maps the block's height
 * against the build limits itself. It does read the block entity's saved NBT for the slider window,
 * which is where the calibrated range lives too.
 */
@Mixin(AltitudeSensorMovementBehaviour.class)
public abstract class AltitudeSensorMovementBehaviourMixin {
    // require = 0: purely cosmetic, and only about the dial on a moving contraption. Not worth
    // crashing the game over if Simulated ever stops normalising the height this way.
    @ModifyExpressionValue(method = "renderInContraption", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;map(DDDDD)D"))
    private double altitudeUnbound$useCalibratedRange(final double original, final MovementContext context,
                                                      final VirtualRenderWorld renderWorld,
                                                      final ContraptionMatrices matrices,
                                                      final MultiBufferSource buffer) {
        if (context.blockEntityData == null || context.position == null) {
            return original;
        }

        final AltitudeRange range = AltitudeRange.read(context.blockEntityData);
        if (!range.isUsable()) {
            return original;
        }

        return Mth.map(context.position.y, range.min(), range.max(), 0.0, 1.0);
    }
}
