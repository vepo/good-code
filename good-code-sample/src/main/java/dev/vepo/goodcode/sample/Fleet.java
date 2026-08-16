package dev.vepo.goodcode.sample;

import dev.vepo.goodcode.sample.model.Vehicle;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps track of a group of vehicles. Nothing fancy, just enough logic to give
 * the report a couple of methods with real bodies.
 */
public class Fleet {

    private final String name;
    private final List<Vehicle> vehicles = new ArrayList<>();

    public Fleet(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void add(Vehicle vehicle) {
        vehicles.add(vehicle);
    }

    public int size() {
        return vehicles.size();
    }

    public int totalMileage() {
        int total = 0;
        for (Vehicle vehicle : vehicles) {
            total += vehicle.getMileage();
        }
        return total;
    }

    public List<Vehicle> getVehicles() {
        return vehicles;
    }
}
