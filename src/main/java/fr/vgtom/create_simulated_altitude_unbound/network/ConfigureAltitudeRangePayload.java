package fr.vgtom.create_simulated_altitude_unbound.network;

import fr.vgtom.create_simulated_altitude_unbound.AltitudeUnbound;
import fr.vgtom.create_simulated_altitude_unbound.content.AltitudeRange;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: calibrate the sensor at {@code pos} to {@code range}.
 *
 * <p>Nothing travels back the other way — the server applies the range and then calls
 * {@code notifyUpdate()}, so the sensor's own block entity packet carries the new values to every
 * client watching it, including the one that sent this.
 */
public record ConfigureAltitudeRangePayload(BlockPos pos, AltitudeRange range) implements CustomPacketPayload {
    public static final Type<ConfigureAltitudeRangePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AltitudeUnbound.MODID, "configure_range"));

    public static final StreamCodec<ByteBuf, ConfigureAltitudeRangePayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ConfigureAltitudeRangePayload::pos,
            ByteBufCodecs.BOOL, payload -> payload.range().custom(),
            ByteBufCodecs.FLOAT, payload -> payload.range().min(),
            ByteBufCodecs.FLOAT, payload -> payload.range().max(),
            (pos, custom, min, max) -> new ConfigureAltitudeRangePayload(pos, new AltitudeRange(custom, min, max)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
