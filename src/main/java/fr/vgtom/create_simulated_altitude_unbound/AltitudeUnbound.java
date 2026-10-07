package fr.vgtom.create_simulated_altitude_unbound;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Lets each Altitude Sensor from Create Simulated be calibrated over an altitude range of its own.
 *
 * <p>Simulated maps world height onto {@code [0, 1]} between the world's build limits, and the two
 * sliders of the sensor's screen then pick the window of that normalised range which spans redstone
 * 0 to 15. An aircraft flying above the build limit therefore sits past the top of the scale with no
 * way to calibrate for it. This mod replaces the {@code [minBuildHeight, maxBuildHeight]} end of
 * that mapping with a range the player types in, leaving the sliders — and everything downstream of
 * them: the redstone signal, the display source, the ComputerCraft peripheral — working exactly as
 * before, only over a scale that reaches as high as the aircraft does.
 *
 * <p>There is nothing to register: the whole mod is the mixins listed in
 * {@code create_simulated_altitude_unbound.mixins.json} plus the packet in
 * {@link fr.vgtom.create_simulated_altitude_unbound.network.AltitudeUnboundNetwork}.
 */
@Mod(AltitudeUnbound.MODID)
public class AltitudeUnbound {
    public static final String MODID = "create_simulated_altitude_unbound";

    private static final Logger LOGGER = LogUtils.getLogger();

    public AltitudeUnbound(final IEventBus modEventBus, final ModContainer modContainer) {
        LOGGER.debug("Altitude Unbound loaded");
    }
}
