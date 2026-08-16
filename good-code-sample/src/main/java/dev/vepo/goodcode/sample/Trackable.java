package dev.vepo.goodcode.sample;

import dev.vepo.goodcode.sample.model.GpsPosition;

/**
 * Anything that can report its current position.
 */
public interface Trackable {

    GpsPosition getPosition();

    default boolean isLocated() {
        return getPosition() != null;
    }
}
