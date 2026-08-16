package dev.vepo.goodcode.sample.model;

/**
 * A generic, abstract vehicle. Kept intentionally small: this file exists to
 * give the good-code report something realistic to count.
 */
public abstract class Vehicle {

    private final String brand;
    private final String model;
    protected int mileage;

    protected Vehicle(String brand, String model) {
        this.brand = brand;
        this.model = model;
        this.mileage = 0;
    }

    public String getBrand() {
        return brand;
    }

    public String getModel() {
        return model;
    }

    public int getMileage() {
        return mileage;
    }

    public void drive(int kilometers) {
        if (kilometers < 0) {
            throw new IllegalArgumentException("kilometers must be >= 0");
        }
        mileage += kilometers;
    }

    public abstract String describe();
}
