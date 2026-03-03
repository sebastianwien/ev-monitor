package com.evmonitor.infrastructure.seed;

import com.evmonitor.application.CoinLogService;
import com.evmonitor.domain.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Seeds the database with test data for development.
 * Only runs when profile "dev" is active.
 *
 * Creates:
 * - 3 test users (test1@ev-monitor.net, test2@ev-monitor.net, test3@ev-monitor.net)
 * - 6 cars (2 per user, various EV models with realistic battery capacities)
 * - ~370 charging logs (distributed over 1 year)
 * - Matching Watt history for each user (car creation + log coins + WLTP contributions)
 */
@Component
@Profile({"dev", "seed"})
public class DevDataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final CarRepository carRepository;
    private final EvLogRepository evLogRepository;
    private final CoinLogRepository coinLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final Random random = new Random();

    public DevDataSeeder(UserRepository userRepository, CarRepository carRepository,
                         EvLogRepository evLogRepository, CoinLogRepository coinLogRepository,
                         PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.carRepository = carRepository;
        this.evLogRepository = evLogRepository;
        this.coinLogRepository = coinLogRepository;
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

        // Charging logs with odometer data (typical EV consumption: 15-19 kWh/100km)
        List<LocalDateTime> times1a = generateChargingLogs(car1, 70, 22500, 16.0); // Tesla M3
        List<LocalDateTime> times1b = generateChargingLogs(car2, 60,  8200, 17.0); // VW ID.3
        List<LocalDateTime> times2a = generateChargingLogs(car3, 80, 14000, 18.0); // Ioniq 5
        List<LocalDateTime> times2b = generateChargingLogs(car4, 50, 31000, 19.0); // BMW i4
        List<LocalDateTime> times3a = generateChargingLogs(car5, 70,  5500, 17.5); // Polestar 2
        List<LocalDateTime> times3b = generateChargingLogs(car6, 40, 19000, 16.5); // MG4

        int totalLogs = times1a.size() + times1b.size() + times2a.size()
                      + times2b.size() + times3a.size() + times3b.size();
        System.out.println("⚡ Created " + totalLogs + " charging logs");

        // Watt history matching the actual actions — cars added a few days before first charge
        seedCoinLogs(user1, merge(times1a, times1b),
                "WLTP data contribution: TESLA MODEL_3 (75.0 kWh)");
        seedCoinLogs(user2, merge(times2a, times2b),
                "WLTP data contribution: HYUNDAI IONIQ_5 (77.4 kWh)");
        seedCoinLogs(user3, merge(times3a, times3b),
                "WLTP data contribution: MG MG4 (64.0 kWh)");

        System.out.println("🪙 Created Watt history for all users");
    }

    /**
     * Creates coin logs for a user that match their actual activity:
     * - +20 Watt for first car (3 days before first charge)
     * - +5  Watt for second car (1 day before first charge)
     * - +25 Watt for first charging session
     * - +5  Watt for each subsequent session
     * - +50 Watt (SOCIAL_COIN) for one WLTP community contribution ~3 months in
     */
    private void seedCoinLogs(User user, List<LocalDateTime> chargingTimes, String wltpDescription) {
        UUID userId = user.getId();
        LocalDateTime firstCharge = chargingTimes.get(0);

        // Car creation coins — happened a few days before the user's first charge
        awardCoinAt(userId, CoinType.ACHIEVEMENT_COIN, 20,
                CoinLogService.ACTION_CAR_CREATED, firstCharge.minusDays(3));
        awardCoinAt(userId, CoinType.ACHIEVEMENT_COIN, 5,
                CoinLogService.ACTION_CAR_CREATED, firstCharge.minusDays(1));

        // One coin entry per charging session
        for (int i = 0; i < chargingTimes.size(); i++) {
            int coins = (i == 0) ? 25 : 5;
            awardCoinAt(userId, CoinType.ACHIEVEMENT_COIN, coins,
                    CoinLogService.ACTION_LOG_CREATED, chargingTimes.get(i).plusMinutes(5));
        }

        // WLTP community contribution — roughly 3 months into usage
        LocalDateTime wltpTime = firstCharge.plusDays(90);
        awardCoinAt(userId, CoinType.SOCIAL_COIN, 50, wltpDescription, wltpTime);
    }

    private void awardCoinAt(UUID userId, CoinType coinType, int amount,
                              String actionDescription, LocalDateTime at) {
        CoinLog log = new CoinLog(UUID.randomUUID(), userId, coinType, amount, actionDescription, at);
        coinLogRepository.save(log);
    }

    /** Merges two timestamp lists and returns them in chronological order. */
    private List<LocalDateTime> merge(List<LocalDateTime> a, List<LocalDateTime> b) {
        return Stream.concat(a.stream(), b.stream())
                .sorted()
                .toList();
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

    /**
     * Generates charging logs for a car and returns the timestamp of each log.
     * Timestamps are used to back-date matching Watt coin entries.
     */
    private List<LocalDateTime> generateChargingLogs(Car car, int count, int startOdometer,
                                                      double avgConsumptionKwhPer100km) {
        LocalDate startDate = LocalDate.now().minusYears(1);
        int currentOdometer = startOdometer;
        List<LocalDateTime> timestamps = new ArrayList<>(count);

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
            timestamps.add(chargeTime);

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

        return timestamps;
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
