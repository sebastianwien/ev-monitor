package com.evmonitor.infrastructure.seed;

import com.evmonitor.domain.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Seeds the database with test data for development.
 * Only runs when profile "dev" is active.
 *
 * Creates:
 * - 3 test users (test1@ev-monitor.net, test2@ev-monitor.net, test3@ev-monitor.net)
 * - 6 cars (2 per user, various EV models with realistic battery capacities)
 * - ~210 charging logs (70 per active car, distributed over 1 year)
 */
@Component
@Profile("dev")
public class DevDataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final CarRepository carRepository;
    private final EvLogRepository evLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final Random random = new Random();

    public DevDataSeeder(UserRepository userRepository, CarRepository carRepository,
                         EvLogRepository evLogRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.carRepository = carRepository;
        this.evLogRepository = evLogRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (userRepository.findByEmail("test1@ev-monitor.net").isPresent()) {
            System.out.println("⚠️  Database already contains test users. Skipping seed data.");
            return;
        }

        System.out.println("🌱 Seeding database with test data...");
        seedTestData();
        System.out.println("✅ Seed data created successfully!");
    }

    private void seedTestData() {
        // 3 Users (all with password: Test1234!)
        User user1 = createUser("test1@ev-monitor.net");
        User user2 = createUser("test2@ev-monitor.net");
        User user3 = createUser("test3@ev-monitor.net");

        System.out.println("👤 Created 3 test users (password: Test1234!)");

        // 6 Cars (2 per user, using real CarBrand.CarModel enums with specific capacities)
        Car car1 = createCar(user1, CarBrand.CarModel.MODEL_3, new BigDecimal("75.0"), 2023, "B-EV 1234");
        Car car2 = createCar(user1, CarBrand.CarModel.ID_3, new BigDecimal("58.0"), 2021, "B-EV 5678");
        Car car3 = createCar(user2, CarBrand.CarModel.IONIQ_5, new BigDecimal("77.4"), 2024, "M-EV 9012");
        Car car4 = createCar(user2, CarBrand.CarModel.I4, new BigDecimal("80.7"), 2022, "M-EV 3456");
        Car car5 = createCar(user3, CarBrand.CarModel.POLESTAR_2, new BigDecimal("78.0"), 2023, "HH-EV 7890");
        Car car6 = createCar(user3, CarBrand.CarModel.MG4, new BigDecimal("64.0"), 2020, "HH-EV 1122");

        System.out.println("🚗 Created 6 cars (2 per user)");

        // Charging logs: ~70 per car (1-2 charges per week over 1 year)
        int totalLogs = 0;
        totalLogs += generateChargingLogs(car1, 70);
        totalLogs += generateChargingLogs(car2, 60);
        totalLogs += generateChargingLogs(car3, 80);
        totalLogs += generateChargingLogs(car4, 50);
        totalLogs += generateChargingLogs(car5, 70);
        totalLogs += generateChargingLogs(car6, 40);

        System.out.println("⚡ Created " + totalLogs + " charging logs");
    }

    private User createUser(String email) {
        String passwordHash = passwordEncoder.encode("Test1234!");
        User user = User.createNewLocalUser(email, passwordHash);
        return userRepository.save(user);
    }

    private Car createCar(User user, CarBrand.CarModel model, BigDecimal batteryKwh, int year, String licensePlate) {
        // Estimate power (kW) based on battery size (rough estimate: 2kW per kWh)
        BigDecimal powerKw = batteryKwh.multiply(new BigDecimal("2.0"));

        Car car = Car.createNew(
                user.getId(),
                model,
                year,
                licensePlate,
                null, // trim
                batteryKwh,
                powerKw
        );
        return carRepository.save(car);
    }

    private int generateChargingLogs(Car car, int count) {
        LocalDate startDate = LocalDate.now().minusYears(1);

        for (int i = 0; i < count; i++) {
            // 1-2 charges per week: every 3-7 days
            LocalDate chargeDate = startDate.plusDays((long) i * 5 + random.nextInt(3));

            // Charge between 30-90% of battery capacity
            double batteryKwh = car.getBatteryCapacityKwh().doubleValue();
            double chargedPercent = 0.3 + random.nextDouble() * 0.6;
            double kwhCharged = batteryKwh * chargedPercent;

            // Cost: 0.25-0.50 €/kWh (average 0.35€/kWh)
            double costPerKwh = 0.25 + random.nextDouble() * 0.25;
            double costEur = kwhCharged * costPerKwh;

            // Duration: ~30min per 20kWh at 40kW charger (with variance)
            int baseMinutes = (int) (kwhCharged * 1.5);
            int minutes = baseMinutes + random.nextInt(15);

            // Random time during the day (8am - 10pm)
            int hour = 8 + random.nextInt(14);
            int minute = random.nextInt(60);
            LocalDateTime chargeTime = chargeDate.atTime(hour, minute);

            // Random Berlin area geohash (5km precision)
            String geohash = getRandomBerlinGeohash();

            EvLog log = EvLog.createNew(
                    car.getId(),
                    BigDecimal.valueOf(Math.round(kwhCharged * 100.0) / 100.0), // 2 decimals
                    BigDecimal.valueOf(Math.round(costEur * 100.0) / 100.0),    // 2 decimals
                    minutes,
                    geohash,
                    null, // odometerKm (not available in seed data)
                    null, // maxChargingPowerKw (not available in seed data)
                    chargeTime
            );

            evLogRepository.save(log);
        }

        return count;
    }

    /**
     * Returns a random geohash for Berlin area.
     * Berlin center: 52.52°N, 13.405°E -> geohash "u33d"
     * Nearby areas: u33c, u33f, u33g, u336, u339, etc.
     */
    private String getRandomBerlinGeohash() {
        String[] berlinGeohashes = {
                "u33db", "u33dc", "u33dd", "u33de", "u33df", // Berlin Mitte
                "u33c9", "u33cb", "u33cc", "u33cd", "u33ce", // Wedding
                "u33f8", "u33fb", "u33fc", "u33fd", "u33fe", // Prenzlauer Berg
                "u33g0", "u33g1", "u33g2", "u33g3", "u33g4", // Friedrichshain
                "u3368", "u336b", "u336c", "u336d", "u336e", // Kreuzberg
                "u3398", "u339b", "u339c", "u339d", "u339e"  // Neukölln
        };
        return berlinGeohashes[random.nextInt(berlinGeohashes.length)];
    }
}
