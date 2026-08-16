package dev.vepo.goodcode.sample.model;

public class Car extends Vehicle {

    public enum FuelType {
        PETROL,
        DIESEL,
        ELECTRIC,
        HYBRID
    }

    private final FuelType fuelType;
    private int doors;

    public Car(String brand, String model, FuelType fuelType, int doors) {
        super(brand, model);
        this.fuelType = fuelType;
        this.doors = doors;
    }

    public FuelType getFuelType() {
        return fuelType;
    }

    public int getDoors() {
        return doors;
    }

    public void setDoors(int doors) {
        this.doors = doors;
    }

    @Override
    public String describe() {
        return getBrand() + " " + getModel() + " (" + fuelType + ", " + doors + " doors)";
    }

    /**
     * Small helper class kept nested on purpose, to make sure the report
     * correctly tells nested types apart from top-level ones.
     */
    public static class ServiceRecord {

        private final int atMileage;
        private final String description;

        public ServiceRecord(int atMileage, String description) {
            this.atMileage = atMileage;
            this.description = description;
        }

        public int getAtMileage() {
            return atMileage;
        }

        public String getDescription() {
            return description;
        }
    }
}
