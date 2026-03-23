package com.evmonitor.domain;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public enum CarBrand {

   // Deutschland
   AUDI("Audi"),
   BMW("BMW"),
   MINI("Mini"),
   MERCEDES("Mercedes-Benz"),
   PORSCHE("Porsche"),
   VW("Volkswagen"),
   SMART("Smart"),
   OPEL("Opel"),

   // Europa (Rest)
   RENAULT("Renault"),
   PEUGEOT("Peugeot"),
   FIAT("Fiat"),
   CITROEN("Citroën"),
   SKODA("Škoda"),
   SEAT("Seat"),
   CUPRA("Cupra"),
   VOLVO("Volvo"),
   POLESTAR("Polestar"),
   JAGUAR("Jaguar"),
   LAND_ROVER("Land Rover"),
   DACIA("Dacia"),
   LOTUS("Lotus"),

   // USA
   TESLA("Tesla"),
   FORD("Ford"),
   CHEVROLET("Chevrolet"),
   LUCID("Lucid"),
   RIVIAN("Rivian"),
   FISKER("Fisker"),

   // Asien (Japan & Korea)
   HYUNDAI("Hyundai"),
   KIA("Kia"),
   GENESIS("Genesis"),
   TOYOTA("Toyota"),
   LEXUS("Lexus"),
   NISSAN("Nissan"),
   MAZDA("Mazda"),
   HONDA("Honda"),
   SUBARU("Subaru"),

   // China (Die Europa-Offensive)
   BYD("BYD"),
   MG("MG Motor"),
   NIO("Nio"),
   XPENG("Xpeng"),
   ZEEKR("Zeekr"),
   ORA("ORA"),

   // Sonstige
   SONSTIGE("Andere Marke");

   private final String displayString;

   CarBrand(String displayString) {
      this.displayString = displayString;
   }

   public String getDisplayString() {
      return displayString;
   }

   public enum CarModel {
      // --- TESLA ---
      // TODO: 75.0/79.0 kWh NMC-Varianten — wahrscheinlich Brutto (Netto ~73.8/~75.0 kWh) — verifizieren!
      // LFP-Varianten (57.5/60.0) sind korrekt netto (LFP = 100% nutzbar)
      MODEL_3(CarBrand.TESLA, VehicleCategory.SEDAN, "Model 3", 57.5, 75.0, 79.0),
      MODEL_Y(CarBrand.TESLA, VehicleCategory.SUV, "Model Y", 60.0, 75.0, 79.0),
      MODEL_S(CarBrand.TESLA, VehicleCategory.LUXURY, "Model S", 75.0, 95.0),
      MODEL_S_PLAID(CarBrand.TESLA, VehicleCategory.LUXURY, "Model S Plaid", 95.0),
      MODEL_S_PERFORMANCE(CarBrand.TESLA, VehicleCategory.LUXURY, "Model S Performance", 95.0),
      MODEL_X(CarBrand.TESLA, VehicleCategory.LARGE_SUV, "Model X", 95.0),
      CYBERTRUCK(CarBrand.TESLA, VehicleCategory.PICKUP, "Cybertruck", 123.0),

      // --- VW ---
      ID_3(CarBrand.VW, VehicleCategory.COMPACT, "ID.3", 45.0, 58.0, 77.0, 79.0),
      ID_4(CarBrand.VW, VehicleCategory.SUV, "ID.4", 52.0, 77.0, 82.0),
      ID_5(CarBrand.VW, VehicleCategory.SUV, "ID.5", 77.0),
      ID_7(CarBrand.VW, VehicleCategory.SEDAN, "ID.7", 77.0, 86.0),
      ID_BUZZ(CarBrand.VW, VehicleCategory.VAN, "ID. Buzz", 79.0, 86.0),
      ID_POLO(CarBrand.VW, VehicleCategory.CITY_CAR, "ID. Polo", 38.0, 56.0), // 2026er Prognose
      E_UP(CarBrand.VW, VehicleCategory.CITY_CAR, "e-up!", 32.3, 36.8),
      E_GOLF(CarBrand.VW, VehicleCategory.COMPACT, "e-Golf", 24.2, 35.8),

      // --- HYUNDAI & KIA ---
      IONIQ_5(CarBrand.HYUNDAI, VehicleCategory.SUV, "Ioniq 5", 58.0, 77.4, 84.0),
      // TODO: 63.0 kWh — evtl. Brutto (Netto ~61.1 kWh) — verifizieren!
      IONIQ_6(CarBrand.HYUNDAI, VehicleCategory.SEDAN, "Ioniq 6", 53.0, 63.0, 77.4, 84.0), // Facelift 2026
      IONIQ_ELECTRIC(CarBrand.HYUNDAI, VehicleCategory.COMPACT, "Ioniq Electric", 28.0, 38.3),
      KONA_ELECTRIC(CarBrand.HYUNDAI, VehicleCategory.COMPACT, "Kona Electric", 39.2, 48.6, 64.8, 65.4),
      EV_6(CarBrand.KIA, VehicleCategory.SEDAN, "EV6", 58.0, 77.4, 84.0),
      EV_9(CarBrand.KIA, VehicleCategory.LARGE_SUV, "EV9", 76.1, 99.8),
      EV_3(CarBrand.KIA, VehicleCategory.COMPACT, "EV3", 58.3, 81.4),
      NIRO_EV(CarBrand.KIA, VehicleCategory.COMPACT, "Niro EV", 39.2, 64.8),
      E_SOUL(CarBrand.KIA, VehicleCategory.COMPACT, "e-Soul", 39.2, 64.0),

      // --- MINI ---
      // Cooper SE F56: 28.9 kWh netto (32.6 kWh brutto) — verifiziert
      // TODO: J01-Modelle (Cooper E/SE, Aceman E/SE) — Netto-Werte ca. 90% von Brutto, bitte gegen ev-database.org prüfen
      MINI_COOPER_SE_F56(CarBrand.MINI, VehicleCategory.CITY_CAR, "Cooper SE (2020-2023)", 28.9),
      MINI_COOPER_E(CarBrand.MINI, VehicleCategory.CITY_CAR, "Cooper E", 37.0),
      MINI_COOPER_SE(CarBrand.MINI, VehicleCategory.CITY_CAR, "Cooper SE", 49.0),
      MINI_ACEMAN_E(CarBrand.MINI, VehicleCategory.COMPACT, "Aceman E", 37.0),
      MINI_ACEMAN_SE(CarBrand.MINI, VehicleCategory.COMPACT, "Aceman SE", 49.0),
      // TODO: Countryman SE Netto-Wert verifizieren (Brutto ~64.7 kWh)
      MINI_COUNTRYMAN_SE(CarBrand.MINI, VehicleCategory.SUV, "Countryman SE", 64.0),

      // --- BMW ---
      I3(CarBrand.BMW, VehicleCategory.COMPACT, "i3", 33.0, 42.2),
      I4(CarBrand.BMW, VehicleCategory.SEDAN, "i4", 67.0, 80.7),
      I5(CarBrand.BMW, VehicleCategory.SEDAN, "i5", 81.2),
      I7(CarBrand.BMW, VehicleCategory.LUXURY, "i7", 101.7),
      IX(CarBrand.BMW, VehicleCategory.LARGE_SUV, "iX", 76.6, 105.2),
      IX1(CarBrand.BMW, VehicleCategory.COMPACT, "iX1", 64.7),
      IX2(CarBrand.BMW, VehicleCategory.COMPACT, "iX2", 64.7),
      IX3(CarBrand.BMW, VehicleCategory.SUV, "iX3", 74.0),
      IX3_NEUE_KLASSE(CarBrand.BMW, VehicleCategory.SUV, "iX3 (Neue Klasse)", 108.7), // Welcome to 2026

      // --- POLESTAR & VOLVO ---
      POLESTAR_2(CarBrand.POLESTAR, VehicleCategory.SEDAN, "Polestar 2", 67.0, 75.0, 78.0, 82.0),
      // TODO: 107.0 kWh — wahrscheinlich Brutto (Netto ~104 kWh) — verifizieren!
      POLESTAR_3(CarBrand.POLESTAR, VehicleCategory.LARGE_SUV, "Polestar 3", 107.0),
      POLESTAR_4(CarBrand.POLESTAR, VehicleCategory.SUV, "Polestar 4", 94.0),
      EX_30(CarBrand.VOLVO, VehicleCategory.COMPACT, "EX30", 49.0, 64.0),
      EX_90(CarBrand.VOLVO, VehicleCategory.LARGE_SUV, "EX90", 107.0),

      // --- CHINA-FRAKTION ---
      BYD_ATTO_3(CarBrand.BYD, VehicleCategory.SUV, "Atto 3", 60.5),
      BYD_SEAL(CarBrand.BYD, VehicleCategory.SEDAN, "Seal", 61.4, 82.5),
      BYD_DOLPHIN(CarBrand.BYD, VehicleCategory.COMPACT, "Dolphin", 44.9, 60.4),
      MG4(CarBrand.MG, VehicleCategory.COMPACT, "MG4", 51.0, 64.0, 77.0),
      NIO_ET5(CarBrand.NIO, VehicleCategory.SEDAN, "ET5", 75.0, 100.0, 150.0), // 150 kWh Solid-State!
      XPENG_G6(CarBrand.XPENG, VehicleCategory.SUV, "G6", 66.0, 80.0, 87.5), // 80.0 = chinesische Variante

      // --- MERCEDES ---
      // Netto-Werte: EQA/EQB haben eine einzige Batterie (66.5 kWh netto / 70.5 kWh brutto)
      EQA(CarBrand.MERCEDES, VehicleCategory.COMPACT, "EQA", 66.5),
      EQB(CarBrand.MERCEDES, VehicleCategory.COMPACT, "EQB", 66.5),
      EQC(CarBrand.MERCEDES, VehicleCategory.LARGE_SUV, "EQC", 80.0),
      EQE(CarBrand.MERCEDES, VehicleCategory.SEDAN, "EQE", 89.0, 90.6),
      EQE_SUV(CarBrand.MERCEDES, VehicleCategory.SUV, "EQE SUV", 90.6, 96.0),
      // EQS/EQS SUV: 118 kWh ist Bruttowert, alle Varianten teilen 107.8/108.4 kWh netto
      EQS(CarBrand.MERCEDES, VehicleCategory.LUXURY, "EQS", 107.8),
      EQS_SUV(CarBrand.MERCEDES, VehicleCategory.LARGE_SUV, "EQS SUV", 108.4),
      EQV(CarBrand.MERCEDES, VehicleCategory.VAN, "EQV", 90.0),

      // --- AUDI ---
      A6_E_TRON(CarBrand.AUDI, VehicleCategory.SEDAN, "A6 e-tron", 83.0, 100.0),
      E_TRON(CarBrand.AUDI, VehicleCategory.LARGE_SUV, "e-tron", 71.0, 95.0),
      E_TRON_GT(CarBrand.AUDI, VehicleCategory.SPORTS, "e-tron GT", 93.4),
      Q4_E_TRON(CarBrand.AUDI, VehicleCategory.SUV, "Q4 e-tron", 52.0, 77.0, 82.0),
      Q6_E_TRON(CarBrand.AUDI, VehicleCategory.SUV, "Q6 e-tron", 83.0, 100.0),
      // TODO: A6/Q6 e-tron 100.0 kWh — wahrscheinlich Brutto, Netto ~94.9 kWh — verifizieren!
      // TODO: Q8 e-tron 106.0 kWh — war 114.0 kWh (Brutto), korrigiert auf Netto — verifizieren!
      Q8_E_TRON(CarBrand.AUDI, VehicleCategory.LARGE_SUV, "Q8 e-tron", 95.0, 106.0),

      // --- PORSCHE ---
      TAYCAN(CarBrand.PORSCHE, VehicleCategory.SPORTS, "Taycan", 79.2, 93.4, 105.0),
      TAYCAN_CROSS_TURISMO(CarBrand.PORSCHE, VehicleCategory.SPORTS, "Taycan Cross Turismo", 79.2, 93.4),
      MACAN_ELECTRIC(CarBrand.PORSCHE, VehicleCategory.SUV, "Macan Electric", 95.0, 100.0),

      // --- SMART ---
      SMART_1(CarBrand.SMART, VehicleCategory.COMPACT, "#1", 49.0, 66.0),
      SMART_3(CarBrand.SMART, VehicleCategory.COMPACT, "#3", 49.0, 66.0),
      FORTWO_EQ(CarBrand.SMART, VehicleCategory.CITY_CAR, "fortwo EQ", 17.6),
      FORFOUR_EQ(CarBrand.SMART, VehicleCategory.CITY_CAR, "forfour EQ", 17.6),

      // --- OPEL ---
      AMPERA_E(CarBrand.OPEL, VehicleCategory.COMPACT, "Ampera-e", 60.0),
      CORSA_E(CarBrand.OPEL, VehicleCategory.CITY_CAR, "Corsa-e", 50.0, 51.0, 54.0),
      ASTRA_E(CarBrand.OPEL, VehicleCategory.COMPACT, "Astra Electric", 54.0),
      MOKKA_E(CarBrand.OPEL, VehicleCategory.COMPACT, "Mokka-e", 50.0, 54.0),
      COMBO_E(CarBrand.OPEL, VehicleCategory.VAN, "Combo-e", 50.0),
      VIVARO_E(CarBrand.OPEL, VehicleCategory.VAN, "Vivaro-e", 50.0, 75.0),

      // --- PEUGEOT ---
      E_208(CarBrand.PEUGEOT, VehicleCategory.COMPACT, "e-208", 50.0, 51.0),
      E_2008(CarBrand.PEUGEOT, VehicleCategory.COMPACT, "e-2008", 50.0, 54.0),
      E_308(CarBrand.PEUGEOT, VehicleCategory.COMPACT, "e-308", 54.0),
      E_5008(CarBrand.PEUGEOT, VehicleCategory.SUV, "e-5008", 73.0, 96.0),
      E_RIFTER(CarBrand.PEUGEOT, VehicleCategory.VAN, "e-Rifter", 50.0),

      // --- CITROEN ---
      E_C4(CarBrand.CITROEN, VehicleCategory.COMPACT, "ë-C4", 50.0, 54.0),
      E_C5_AIRCROSS(CarBrand.CITROEN, VehicleCategory.SUV, "ë-C5 Aircross", 50.0),
      E_BERLINGO(CarBrand.CITROEN, VehicleCategory.VAN, "ë-Berlingo", 50.0),
      AMI(CarBrand.CITROEN, VehicleCategory.CITY_CAR, "Ami", 5.5),

      // --- SKODA ---
      ENYAQ(CarBrand.SKODA, VehicleCategory.SUV, "Enyaq", 52.0, 55.0, 62.0, 77.0, 82.0),
      ENYAQ_COUPE(CarBrand.SKODA, VehicleCategory.SUV, "Enyaq Coupé", 77.0, 82.0),
      ELROQ(CarBrand.SKODA, VehicleCategory.COMPACT, "Elroq", 55.0, 63.0, 82.0),

      // --- SEAT & CUPRA ---
      MII_ELECTRIC(CarBrand.SEAT, VehicleCategory.CITY_CAR, "Mii electric", 32.3, 36.8),
      CUPRA_BORN(CarBrand.CUPRA, VehicleCategory.COMPACT, "Born", 45.0, 58.0, 77.0),
      CUPRA_TAVASCAN(CarBrand.CUPRA, VehicleCategory.SUV, "Tavascan", 77.0),

      // --- FORD ---
      MUSTANG_MACH_E(CarBrand.FORD, VehicleCategory.SUV, "Mustang Mach-E", 68.0, 75.7, 88.0, 91.0),
      F_150_LIGHTNING(CarBrand.FORD, VehicleCategory.PICKUP, "F-150 Lightning", 98.0, 131.0),
      E_TRANSIT(CarBrand.FORD, VehicleCategory.VAN, "E-Transit", 68.0, 89.0),
      EXPLORER_EV(CarBrand.FORD, VehicleCategory.SUV, "Explorer", 52.0, 77.0, 79.0),

      // --- CHEVROLET ---
      BOLT_EV(CarBrand.CHEVROLET, VehicleCategory.COMPACT, "Bolt EV", 60.0, 65.0),
      BOLT_EUV(CarBrand.CHEVROLET, VehicleCategory.COMPACT, "Bolt EUV", 65.0),
      BLAZER_EV(CarBrand.CHEVROLET, VehicleCategory.SUV, "Blazer EV", 85.0),
      EQUINOX_EV(CarBrand.CHEVROLET, VehicleCategory.SUV, "Equinox EV", 85.0),

      // --- RIVIAN ---
      R1T(CarBrand.RIVIAN, VehicleCategory.PICKUP, "R1T", 105.0, 135.0, 149.0, 180.0),
      R1S(CarBrand.RIVIAN, VehicleCategory.LARGE_SUV, "R1S", 105.0, 135.0, 149.0, 180.0),
      R2(CarBrand.RIVIAN, VehicleCategory.SUV, "R2", 100.0),

      // --- LUCID ---
      AIR(CarBrand.LUCID, VehicleCategory.LUXURY, "Air", 88.0, 112.0, 118.0),
      GRAVITY(CarBrand.LUCID, VehicleCategory.LARGE_SUV, "Gravity", 112.0, 120.0),

      // --- FISKER ---
      OCEAN(CarBrand.FISKER, VehicleCategory.SUV, "Ocean", 70.0, 106.0, 113.0),
      PEAR(CarBrand.FISKER, VehicleCategory.COMPACT, "Pear", 75.0),

      // --- GENESIS ---
      GV60(CarBrand.GENESIS, VehicleCategory.COMPACT, "GV60", 77.4),
      GV70_ELECTRIFIED(CarBrand.GENESIS, VehicleCategory.LARGE_SUV, "GV70 Electrified", 77.4),
      G80_ELECTRIFIED(CarBrand.GENESIS, VehicleCategory.SEDAN, "G80 Electrified", 87.2),
      GV90(CarBrand.GENESIS, VehicleCategory.LARGE_SUV, "GV90", 100.0),

      // --- TOYOTA ---
      BZ4X(CarBrand.TOYOTA, VehicleCategory.SUV, "bZ4X", 71.4),
      BZ3(CarBrand.TOYOTA, VehicleCategory.SEDAN, "bZ3", 65.3),
      PROACE_ELECTRIC(CarBrand.TOYOTA, VehicleCategory.VAN, "Proace Electric", 50.0, 75.0),

      // --- LEXUS ---
      UX_300E(CarBrand.LEXUS, VehicleCategory.COMPACT, "UX 300e", 54.3, 72.8),
      RZ_450E(CarBrand.LEXUS, VehicleCategory.SUV, "RZ 450e", 71.4),

      // --- NISSAN ---
      LEAF(CarBrand.NISSAN, VehicleCategory.COMPACT, "Leaf", 40.0, 59.0, 62.0),
      ARIYA(CarBrand.NISSAN, VehicleCategory.SUV, "Ariya", 63.0, 66.0, 87.0, 91.0),
      TOWNSTAR_EV(CarBrand.NISSAN, VehicleCategory.VAN, "Townstar EV", 45.0),

      // --- MAZDA ---
      MX_30(CarBrand.MAZDA, VehicleCategory.COMPACT, "MX-30", 35.5),
      MX_30_REX(CarBrand.MAZDA, VehicleCategory.COMPACT, "MX-30 R-EV", 17.8),

      // --- HONDA ---
      E(CarBrand.HONDA, VehicleCategory.CITY_CAR, "e", 35.5),
      E_NY1(CarBrand.HONDA, VehicleCategory.COMPACT, "e:Ny1", 68.8),

      // --- SUBARU ---
      SOLTERRA(CarBrand.SUBARU, VehicleCategory.SUV, "Solterra", 71.4),

      // --- JAGUAR ---
      I_PACE(CarBrand.JAGUAR, VehicleCategory.LARGE_SUV, "I-Pace", 84.7, 90.0),

      // --- LAND ROVER ---
      RANGE_ROVER_ELECTRIC(CarBrand.LAND_ROVER, VehicleCategory.LARGE_SUV, "Range Rover Electric", 100.0),

      // --- LOTUS ---
      ELETRE(CarBrand.LOTUS, VehicleCategory.LARGE_SUV, "Eletre", 112.0),
      EMEYA(CarBrand.LOTUS, VehicleCategory.SPORTS, "Emeya", 102.0),

      // --- RENAULT ---
      RENAULT_5(CarBrand.RENAULT, VehicleCategory.CITY_CAR, "Renault 5", 40.0, 52.0),
      ZOE(CarBrand.RENAULT, VehicleCategory.CITY_CAR, "Zoe", 41.0, 52.0, 55.0),
      MEGANE_E_TECH(CarBrand.RENAULT, VehicleCategory.COMPACT, "Megane E-Tech", 40.0, 60.0),
      SCENIC_E_TECH(CarBrand.RENAULT, VehicleCategory.SUV, "Scenic E-Tech", 60.0, 87.0),
      KANGOO_E_TECH(CarBrand.RENAULT, VehicleCategory.VAN, "Kangoo E-Tech", 45.0),
      TWINGO_E_TECH(CarBrand.RENAULT, VehicleCategory.CITY_CAR, "Twingo E-Tech", 22.0),

      // --- DACIA ---
      DACIA_SPRING(CarBrand.DACIA, VehicleCategory.CITY_CAR, "Spring", 26.8, 33.0),
      DACIA_BIGSTER_ELECTRIC(CarBrand.DACIA, VehicleCategory.SUV, "Bigster Electric", 60.0),

      // --- FIAT ---
      FIAT_500E(CarBrand.FIAT, VehicleCategory.CITY_CAR, "500e", 21.3, 37.3, 42.0),
      FIAT_600E(CarBrand.FIAT, VehicleCategory.COMPACT, "600e", 54.0),

      // --- CHINA-FRAKTION erweitert ---
      BYD_HAN(CarBrand.BYD, VehicleCategory.SEDAN, "Han", 85.4),
      BYD_TANG(CarBrand.BYD, VehicleCategory.LARGE_SUV, "Tang", 86.4, 108.8),
      BYD_SEAGULL(CarBrand.BYD, VehicleCategory.CITY_CAR, "Seagull", 30.1, 38.9),
      MG5(CarBrand.MG, VehicleCategory.COMPACT, "MG5", 50.3, 61.1),
      MG_ZS_EV(CarBrand.MG, VehicleCategory.COMPACT, "MG ZS EV", 51.0, 72.6),
      MG_MARVEL_R(CarBrand.MG, VehicleCategory.SUV, "Marvel R", 70.0),
      NIO_ET7(CarBrand.NIO, VehicleCategory.SEDAN, "ET7", 75.0, 100.0, 150.0),
      NIO_ES6(CarBrand.NIO, VehicleCategory.SUV, "ES6", 75.0, 100.0),
      XPENG_P7(CarBrand.XPENG, VehicleCategory.SEDAN, "P7", 60.2, 70.8, 80.9),
      XPENG_P7_PLUS(CarBrand.XPENG, VehicleCategory.SEDAN, "P7+", 60.0, 76.3, 85.0),
      XPENG_G9(CarBrand.XPENG, VehicleCategory.LARGE_SUV, "G9", 78.2, 98.0),
      ZEEKR_001(CarBrand.ZEEKR, VehicleCategory.SEDAN, "001", 86.0, 100.0),
      ZEEKR_X(CarBrand.ZEEKR, VehicleCategory.COMPACT, "X", 64.0),
      ORA_FUNKY_CAT(CarBrand.ORA, VehicleCategory.CITY_CAR, "Funky Cat", 48.0, 63.0);

      private final CarBrand brand;
      private final VehicleCategory category;
      private final String displayName;
      private final List<Double> capacities;

      CarModel(CarBrand brand, VehicleCategory category, String displayName, Double... caps) {
         this.brand = brand;
         this.category = category;
         this.displayName = displayName;
         this.capacities = List.of(caps);
      }

      public static List<CarModel> byBrand(CarBrand brand) {
         return Arrays.stream(values())
             .filter(m -> m.brand == brand)
             .toList();
      }

      public static List<CarModel> byCategory(VehicleCategory category) {
         return Stream.of(values())
             .filter(m -> m.category == category)
             .toList();
      }

      public CarBrand getBrand() {
         return brand;
      }

      public VehicleCategory getCategory() {
         return category;
      }

      public List<Double> getCapacities() {
         return capacities;
      }

      public String getDisplayName() {
         return displayName;
      }
   }
}