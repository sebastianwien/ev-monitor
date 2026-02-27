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
        // 3 Users (all with password: 123!"§)
        User user1 = createUser("test1@ev-monitor.net", "max_e_driver");
        User user2 = createUser("test2@ev-monitor.net", "anna_ampere");
        User user3 = createUser("test3@ev-monitor.net", "kurt_kilowatt");

        System.out.println("👤 Created 3 test users (password: 123!\"§)");

        // 6 Cars (2 per user, using real CarBrand.CarModel enums with specific capacities)
        Car car1 = createCar(user1, CarBrand.CarModel.MODEL_3, new BigDecimal("75.0"), 2023, "B-EV 1234");
        Car car2 = createCar(user1, CarBrand.CarModel.ID_3, new BigDecimal("58.0"), 2021, "B-EV 5678");
        Car car3 = createCar(user2, CarBrand.CarModel.IONIQ_5, new BigDecimal("77.4"), 2024, "M-EV 9012");
        Car car4 = createCar(user2, CarBrand.CarModel.I4, new BigDecimal("80.7"), 2022, "M-EV 3456");
        Car car5 = createCar(user3, CarBrand.CarModel.POLESTAR_2, new BigDecimal("78.0"), 2023, "HH-EV 7890");
        Car car6 = createCar(user3, CarBrand.CarModel.MG4, new BigDecimal("64.0"), 2020, "HH-EV 1122");

        System.out.println("🚗 Created 6 cars (2 per user)");

        // Charging logs with odometer data (typical EV consumption: 15-18 kWh/100km)
        int totalLogs = 0;
        totalLogs += generateChargingLogs(car1, 70, 22500, 16.0); // Tesla M3: ~16 kWh/100km
        totalLogs += generateChargingLogs(car2, 60,  8200, 17.0); // VW ID.3: ~17 kWh/100km
        totalLogs += generateChargingLogs(car3, 80, 14000, 18.0); // Ioniq 5: ~18 kWh/100km
        totalLogs += generateChargingLogs(car4, 50, 31000, 19.0); // BMW i4: ~19 kWh/100km
        totalLogs += generateChargingLogs(car5, 70,  5500, 17.5); // Polestar 2: ~17.5 kWh/100km
        totalLogs += generateChargingLogs(car6, 40, 19000, 16.5); // MG4: ~16.5 kWh/100km

        System.out.println("⚡ Created " + totalLogs + " charging logs");
    }

    private User createUser(String email, String username) {
        String passwordHash = passwordEncoder.encode("123!\"§");
        User user = User.createSeedUser(email, username, passwordHash);
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

    /**
     * Seasonal consumption multipliers for Germany (month index 1-12).
     * EV winter penalty: cabin + battery heating, cold cell performance.
     * EV summer bonus: good battery temps, though AC adds ~5% back.
     *
     * Source basis: typical real-world EV data (e.g. ~25-30% more in Jan vs. July)
     */
    private static final double[] SEASONAL_MULTIPLIER = {
        0.0,  // [0] unused (months are 1-based)
        1.28, // Jan  – cold (-5°C avg), heavy heating
        1.22, // Feb  – still cold, gradually warming
        1.10, // Mar  – cool, heating tapering off
        1.02, // Apr  – mild, minimal HVAC
        0.95, // May  – warm, AC not yet heavy
        0.93, // Jun  – warm/hot, slight AC penalty
        0.92, // Jul  – hottest month, lowest consumption
        0.93, // Aug  – similar to July
        0.97, // Sep  – mild, occasional heating starts
        1.06, // Oct  – cool, heating returns
        1.17, // Nov  – cold, significant heating load
        1.24  // Dec  – cold, holiday short trips (less efficient)
    };

    private int generateChargingLogs(Car car, int count, int startOdometer, double avgConsumptionKwhPer100km) {
        LocalDate startDate = LocalDate.now().minusYears(1);
        int currentOdometer = startOdometer;

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

            // Seasonal consumption: winter up to +28%, summer down to -8%
            int month = chargeDate.getMonthValue();
            double seasonalFactor = SEASONAL_MULTIPLIER[month];

            // Odometer: advance by distance driven (kWh / consumption * 100km)
            // ±8% random variance on top of seasonal factor
            double consumption = avgConsumptionKwhPer100km
                    * seasonalFactor
                    * (0.92 + random.nextDouble() * 0.16);
            int kmDriven = (int) Math.round(kwhCharged / consumption * 100.0);
            currentOdometer += kmDriven;

            // Max charging power: realistic for the car (AC 11kW, DC 50-150kW), occasionally null
            BigDecimal maxChargingPower = null;
            if (random.nextDouble() > 0.2) { // 80% of logs have charging power data
                double[] powerOptions = {11.0, 22.0, 50.0, 100.0, 150.0};
                maxChargingPower = BigDecimal.valueOf(powerOptions[random.nextInt(powerOptions.length)]);
            }

            EvLog log = EvLog.createNew(
                    car.getId(),
                    BigDecimal.valueOf(Math.round(kwhCharged * 100.0) / 100.0),
                    BigDecimal.valueOf(Math.round(costEur * 100.0) / 100.0),
                    minutes,
                    getRandomBerlinGeohash(),
                    currentOdometer,
                    maxChargingPower,
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
