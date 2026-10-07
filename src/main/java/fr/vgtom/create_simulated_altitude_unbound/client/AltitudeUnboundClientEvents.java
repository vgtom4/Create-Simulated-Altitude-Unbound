package fr.vgtom.create_simulated_altitude_unbound.client;

import fr.vgtom.create_simulated_altitude_unbound.AltitudeUnbound;
import fr.vgtom.create_simulated_altitude_unbound.duck.ScrollableSensorScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * The sensor screen does not override {@code mouseScrolled}, so there is no method to inject into.
 * NeoForge's screen event reaches it without having to merge an override onto someone else's class.
 */
@EventBusSubscriber(modid = AltitudeUnbound.MODID, value = Dist.CLIENT)
public final class AltitudeUnboundClientEvents {
    private AltitudeUnboundClientEvents() {
    }

    @SubscribeEvent
    public static void onMouseScrolled(final ScreenEvent.MouseScrolled.Pre event) {
        if (event.getScreen() instanceof ScrollableSensorScreen screen
                && screen.altitudeUnbound$scrollSlider(event.getMouseX(), event.getMouseY(),
                        event.getScrollDeltaY())) {
            event.setCanceled(true);
        }
    }
}
