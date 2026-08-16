package dev.vepo.goodcode.sample.model;

/**
 * A simple, immutable GPS coordinate. Declared as a record to make sure the
 * good-code report understands records as their own kind of type.
 */
public record GpsPosition(double latitude, double longitude) {

    public boolean isValid() {
        return latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180;
    }
}
