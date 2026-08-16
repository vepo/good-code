package dev.vepo.goodcode.sample.model;

public class CarHelper {
    public static boolean isFamilyCar(Car car) {
        return car.getDoors() >= 4;
    }
}
