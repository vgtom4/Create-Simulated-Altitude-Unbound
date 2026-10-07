package fr.vgtom.create_simulated_altitude_unbound.duck;

import fr.vgtom.create_simulated_altitude_unbound.content.AltitudeRange;
import net.minecraft.world.entity.player.Player;

/**
 * Implemented by {@code AltitudeSensorBlockEntity} through a mixin. The block entity is where the
 * range belongs: Simulated already writes its NBT on both the save path and the client packet, so
 * hanging on there gets persistence and sync for free.
 *
 * <p>Nothing outside the mixin names a Simulated or Create type, which is what lets the rest of the
 * mod load on an installation that has neither.
 */
public interface CalibratableSensor {
    AltitudeRange altitudeUnbound$getRange();

    /** Sets the range without touching the world; for the mixin's own NBT and clipboard paths. */
    void altitudeUnbound$setRange(AltitudeRange range);

    /**
     * Server side: sets the range, syncs the sensor to every watching client and settles its redstone
     * output.
     */
    void altitudeUnbound$applyRange(AltitudeRange range);

    /**
     * Whether {@code player} is close enough to be operating this sensor.
     *
     * <p>Not a plain distance to its block position: a sensor mounted on a Simulated structure lives
     * in a sub-level plot, whose block coordinates are nowhere near where the structure — and the
     * player standing on it — actually are.
     */
    boolean altitudeUnbound$isOperableBy(Player player, double maxDistance);
}
