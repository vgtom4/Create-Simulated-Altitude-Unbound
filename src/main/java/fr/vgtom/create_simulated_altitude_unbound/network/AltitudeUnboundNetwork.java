package fr.vgtom.create_simulated_altitude_unbound.network;

import com.mojang.logging.LogUtils;
import fr.vgtom.create_simulated_altitude_unbound.AltitudeUnbound;
import fr.vgtom.create_simulated_altitude_unbound.content.AltitudeRange;
import fr.vgtom.create_simulated_altitude_unbound.duck.CalibratableSensor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

@EventBusSubscriber(modid = AltitudeUnbound.MODID)
public final class AltitudeUnboundNetwork {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Generous: the screen stays open while the player moves, and a structure moves under them. */
    private static final double MAX_REACH = 16.0;

    private AltitudeUnboundNetwork() {
    }

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        // Optional: this packet is only ever a client asking for something, so a server without this
        // mod — or a client without it — should still be able to connect.
        final PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToServer(ConfigureAltitudeRangePayload.TYPE, ConfigureAltitudeRangePayload.CODEC,
                AltitudeUnboundNetwork::applyRange);
    }

    // Every refusal is logged: a calibration that silently fails to stick is the hardest kind of bug
    // to tell apart from one that was never sent.
    private static void applyRange(final ConfigureAltitudeRangePayload payload, final IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }

        final AltitudeRange range = payload.range();
        if (range.custom() && !AltitudeRange.isValid(range.min(), range.max())) {
            LOGGER.debug("Refusing the invalid altitude range {} for the sensor at {}", range, payload.pos());
            return;
        }

        final BlockPos pos = payload.pos();
        final Level level = player.level();

        if (!level.isLoaded(pos)) {
            LOGGER.debug("Refusing to calibrate the sensor at {}: that position is not loaded", pos);
            return;
        }

        final BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof CalibratableSensor sensor)) {
            LOGGER.debug("Refusing to calibrate {}: not an altitude sensor but {}", pos, blockEntity);
            return;
        }

        if (!sensor.altitudeUnbound$isOperableBy(player, MAX_REACH)) {
            LOGGER.debug("Refusing to calibrate the sensor at {}: {} is {} away",
                    pos, player.getName().getString(), Math.sqrt(player.distanceToSqr(pos.getCenter())));
            return;
        }

        sensor.altitudeUnbound$applyRange(range.custom() ? range : AltitudeRange.AUTO);
    }
}
