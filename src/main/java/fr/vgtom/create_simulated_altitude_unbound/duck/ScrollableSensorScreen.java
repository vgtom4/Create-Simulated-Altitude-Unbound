package fr.vgtom.create_simulated_altitude_unbound.duck;

/**
 * Implemented by {@code AltitudeSensorScreen} through a mixin, so the scroll handler can reach the
 * sliders without naming a Simulated type.
 */
public interface ScrollableSensorScreen {
    /**
     * @param scrollDelta notches scrolled, positive upwards
     * @return true if a slider took the scroll and the event should stop here
     */
    boolean altitudeUnbound$scrollSlider(double mouseX, double mouseY, double scrollDelta);
}
