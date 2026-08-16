package dev.vepo.goodcode.sample;

import dev.vepo.goodcode.sample.model.Car;
import dev.vepo.goodcode.sample.model.CarHelper;

public class CarTool {

    public static void main(String[] args) {
        IO.println("Describe your car...");
        var brand =IO.readln("What is the brand?");
        var model= IO.readln("What is the model?");
        var fuelType = Car.FuelType.valueOf(IO.readln("What is the used fuel?").toUpperCase());
        var doors = Integer.parseInt(IO.readln("How many doors it has?"));
        var car = new Car(brand,model, fuelType, doors);
        IO.println("Is your car for family? %b".formatted(CarHelper.isFamilyCar(car)));
    }
}
