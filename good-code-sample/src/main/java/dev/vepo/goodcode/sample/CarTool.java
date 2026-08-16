package dev.vepo.goodcode.sample;

import dev.vepo.goodcode.sample.model.Car;
import dev.vepo.goodcode.sample.model.CarHelper;

import java.util.List;

public class CarTool {

    public static void main(String[] args) {
        IO.println("Describe your car...");
        var brand =IO.readln("What is the brand?");
        var model= IO.readln("What is the model?");
        var fuelType = Car.FuelType.valueOf(IO.readln("What is the used fuel?").toUpperCase());
        var doors = Integer.parseInt(IO.readln("How many doors it has?"));
        var car = new Car(brand,model, fuelType, doors);
        IO.println("Is your car for family? %b".formatted(CarHelper.isFamilyCar(car)));

        var cars = List.of(new Car("Ford", "T 29", Car.FuelType.PETROL, 2),
                new Car("Volkswagen", "Fusca", Car.FuelType.PETROL, 2),
                new Car("Volkswagen", "Fusca", Car.FuelType.ELECTRIC, 4));
        cars.stream().filter(c -> c.getFuelType() == Car.FuelType.ELECTRIC)
                .forEach(electricCar -> IO.println("%s is electric!".formatted(electricCar)));
    }
}
