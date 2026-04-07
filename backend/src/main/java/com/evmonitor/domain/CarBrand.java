package com.evmonitor.domain;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
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
      MODEL_3(CarBrand.TESLA, VehicleCategory.SEDAN, "Model 3",
            cap(57.5, "Standard Range+"),
            cap(75.0, "Long Range"),
            cap(79.0, "Performance")),
      MODEL_Y(CarBrand.TESLA, VehicleCategory.SUV, "Model Y",
            cap(60.0, "Standard Range"),
            cap(75.0, "Long Range"),
            cap(79.0, "Performance")),
      MODEL_S(CarBrand.TESLA, VehicleCategory.LUXURY, "Model S",
            cap(75.0),
            cap(95.0)),
      MODEL_S_PLAID(CarBrand.TESLA, VehicleCategory.LUXURY, "Model S Plaid",
            cap(95.0)),
      MODEL_S_PERFORMANCE(CarBrand.TESLA, VehicleCategory.LUXURY, "Model S Performance",
            cap(95.0)),
      MODEL_X(CarBrand.TESLA, VehicleCategory.LARGE_SUV, "Model X",
            cap(95.0)),
      CYBERTRUCK(CarBrand.TESLA, VehicleCategory.PICKUP, "Cybertruck",
            cap(123.0)),

      // --- VW ---
      ID_3(CarBrand.VW, VehicleCategory.COMPACT, "ID.3",
            cap(45.0, "Pure"),
            cap(58.0, "Pro"),
            cap(77.0, "Pro S"),
            cap(79.0, "Tour")),
      ID_4(CarBrand.VW, VehicleCategory.SUV, "ID.4",
            cap(52.0, "Pure"),
            cap(77.0, "Pro"),
            cap(82.0, "Pro S")),
      ID_5(CarBrand.VW, VehicleCategory.SUV, "ID.5",
            cap(77.0, "Pro")),
      ID_7(CarBrand.VW, VehicleCategory.SEDAN, "ID.7",
            cap(77.0, "Pro"),
            cap(86.0, "GTX")),
      ID_BUZZ(CarBrand.VW, VehicleCategory.VAN, "ID. Buzz",
            cap(79.0),
            cap(86.0)),
      ID_POLO(CarBrand.VW, VehicleCategory.CITY_CAR, "ID. Polo",
            cap(38.0),
            cap(56.0)), // 2026er Prognose
      E_UP(CarBrand.VW, VehicleCategory.CITY_CAR, "e-up!",
            cap(32.3),
            cap(36.8)),
      E_GOLF(CarBrand.VW, VehicleCategory.COMPACT, "e-Golf",
            cap(24.2),
            cap(35.8)),

      // --- HYUNDAI & KIA ---
      IONIQ_5(CarBrand.HYUNDAI, VehicleCategory.SUV, "Ioniq 5",
            cap(58.0, "Standard Range"),
            cap(77.4, "Long Range"),
            cap(84.0, "Extended Range")),
      // TODO: 63.0 kWh — evtl. Brutto (Netto ~61.1 kWh) — verifizieren!
      IONIQ_6(CarBrand.HYUNDAI, VehicleCategory.SEDAN, "Ioniq 6",
            cap(53.0, "Standard Range"),
            cap(63.0),
            cap(77.4, "Long Range"),
            cap(84.0)), // Facelift 2026
      IONIQ_ELECTRIC(CarBrand.HYUNDAI, VehicleCategory.COMPACT, "Ioniq Electric",
            cap(28.0),
            cap(38.3)),
      KONA_ELECTRIC(CarBrand.HYUNDAI, VehicleCategory.COMPACT, "Kona Electric",
            cap(39.2),
            cap(48.6),
            cap(64.8),
            cap(65.4)),
      EV_6(CarBrand.KIA, VehicleCategory.SEDAN, "EV6",
            cap(58.0, "Standard Range"),
            cap(77.4, "Long Range"),
            cap(84.0, "Long Range Extended")),
      EV_9(CarBrand.KIA, VehicleCategory.LARGE_SUV, "EV9",
            cap(76.1, "Standard Range"),
            cap(99.8, "Long Range")),
      EV_3(CarBrand.KIA, VehicleCategory.COMPACT, "EV3",
            cap(58.3),
            cap(81.4)),
      NIRO_EV(CarBrand.KIA, VehicleCategory.COMPACT, "Niro EV",
            cap(39.2),
            cap(64.8)),
      E_SOUL(CarBrand.KIA, VehicleCategory.COMPACT, "e-Soul",
            cap(39.2),
            cap(64.0)),

      // --- MINI ---
      // Cooper SE F56: 28.9 kWh netto (32.6 kWh brutto) — verifiziert
      // TODO: J01-Modelle (Cooper E/SE, Aceman E/SE) — Netto-Werte ca. 90% von Brutto, bitte gegen ev-database.org prüfen
      MINI_COOPER_SE_F56(CarBrand.MINI, VehicleCategory.CITY_CAR, "Cooper SE (2020-2023)",
            cap(28.9)),
      MINI_COOPER_E(CarBrand.MINI, VehicleCategory.CITY_CAR, "Cooper E",
            cap(37.0)),
      MINI_COOPER_SE(CarBrand.MINI, VehicleCategory.CITY_CAR, "Cooper SE",
            cap(49.0)),
      MINI_ACEMAN_E(CarBrand.MINI, VehicleCategory.COMPACT, "Aceman E",
            cap(37.0)),
      MINI_ACEMAN_SE(CarBrand.MINI, VehicleCategory.COMPACT, "Aceman SE",
            cap(49.0)),
      // TODO: Countryman SE Netto-Wert verifizieren (Brutto ~64.7 kWh)
      MINI_COUNTRYMAN_SE(CarBrand.MINI, VehicleCategory.SUV, "Countryman SE",
            cap(64.0)),

      // --- BMW ---
      I3(CarBrand.BMW, VehicleCategory.COMPACT, "i3",
            cap(33.0),
            cap(42.2)),
      I4(CarBrand.BMW, VehicleCategory.SEDAN, "i4",
            cap(67.0, "eDrive35"),
            cap(80.7, "eDrive40 / M50")),
      I5(CarBrand.BMW, VehicleCategory.SEDAN, "i5",
            cap(81.2)),
      I7(CarBrand.BMW, VehicleCategory.LUXURY, "i7",
            cap(101.7)),
      IX(CarBrand.BMW, VehicleCategory.LARGE_SUV, "iX",
            cap(76.6),
            cap(105.2)),
      IX1(CarBrand.BMW, VehicleCategory.COMPACT, "iX1",
            cap(64.7)),
      IX2(CarBrand.BMW, VehicleCategory.COMPACT, "iX2",
            cap(64.7)),
      IX3(CarBrand.BMW, VehicleCategory.SUV, "iX3",
            cap(74.0)),
      IX3_NEUE_KLASSE(CarBrand.BMW, VehicleCategory.SUV, "iX3 (Neue Klasse)",
            cap(108.7)), // Welcome to 2026

      // --- POLESTAR & VOLVO ---
      POLESTAR_2(CarBrand.POLESTAR, VehicleCategory.SEDAN, "Polestar 2",
            cap(67.0, "Standard Range"),
            cap(75.0),
            cap(78.0, "Long Range"),
            cap(82.0, "Long Range (2024)")),
      // TODO: 107.0 kWh — wahrscheinlich Brutto (Netto ~104 kWh) — verifizieren!
      POLESTAR_3(CarBrand.POLESTAR, VehicleCategory.LARGE_SUV, "Polestar 3",
            cap(107.0)),
      POLESTAR_4(CarBrand.POLESTAR, VehicleCategory.SUV, "Polestar 4",
            cap(94.0)),
      EX_30(CarBrand.VOLVO, VehicleCategory.COMPACT, "EX30",
            cap(49.0),
            cap(64.0)),
      EX_90(CarBrand.VOLVO, VehicleCategory.LARGE_SUV, "EX90",
            cap(107.0)),

      // --- CHINA-FRAKTION ---
      BYD_ATTO_3(CarBrand.BYD, VehicleCategory.SUV, "Atto 3",
            cap(60.5)),
      BYD_SEAL(CarBrand.BYD, VehicleCategory.SEDAN, "Seal",
            cap(61.4, "Standard Range"),
            cap(82.5, "Long Range")),
      BYD_DOLPHIN(CarBrand.BYD, VehicleCategory.COMPACT, "Dolphin",
            cap(44.9),
            cap(60.4)),
      MG4(CarBrand.MG, VehicleCategory.COMPACT, "MG4",
            cap(51.0),
            cap(64.0),
            cap(77.0)),
      NIO_ET5(CarBrand.NIO, VehicleCategory.SEDAN, "ET5",
            cap(75.0),
            cap(100.0),
            cap(150.0)), // 150 kWh Solid-State!
      XPENG_G6(CarBrand.XPENG, VehicleCategory.SUV, "G6",
            cap(66.0),
            cap(80.0),
            cap(87.5)), // 80.0 = chinesische Variante

      // --- MERCEDES ---
      // Netto-Werte: EQA/EQB haben eine einzige Batterie (66.5 kWh netto / 70.5 kWh brutto)
      EQA(CarBrand.MERCEDES, VehicleCategory.COMPACT, "EQA",
            cap(66.5)),
      EQB(CarBrand.MERCEDES, VehicleCategory.COMPACT, "EQB",
            cap(66.5)),
      EQC(CarBrand.MERCEDES, VehicleCategory.LARGE_SUV, "EQC",
            cap(80.0)),
      EQE(CarBrand.MERCEDES, VehicleCategory.SEDAN, "EQE",
            cap(89.0),
            cap(90.6)),
      EQE_SUV(CarBrand.MERCEDES, VehicleCategory.SUV, "EQE SUV",
            cap(90.6),
            cap(96.0)),
      // EQS/EQS SUV: 118 kWh ist Bruttowert, alle Varianten teilen 107.8/108.4 kWh netto
      EQS(CarBrand.MERCEDES, VehicleCategory.LUXURY, "EQS",
            cap(107.8)),
      EQS_SUV(CarBrand.MERCEDES, VehicleCategory.LARGE_SUV, "EQS SUV",
            cap(108.4)),
      EQV(CarBrand.MERCEDES, VehicleCategory.VAN, "EQV",
            cap(90.0)),

      // --- AUDI ---
      A6_E_TRON(CarBrand.AUDI, VehicleCategory.SEDAN, "A6 e-tron",
            cap(83.0),
            cap(100.0)),
      E_TRON(CarBrand.AUDI, VehicleCategory.LARGE_SUV, "e-tron",
            cap(71.0),
            cap(95.0)),
      E_TRON_GT(CarBrand.AUDI, VehicleCategory.SPORTS, "e-tron GT",
            cap(93.4)),
      Q4_E_TRON(CarBrand.AUDI, VehicleCategory.SUV, "Q4 e-tron",
            cap(52.0, "35 e-tron"),
            cap(77.0),
            cap(82.0)),
      Q6_E_TRON(CarBrand.AUDI, VehicleCategory.SUV, "Q6 e-tron",
            cap(83.0),
            cap(100.0)),
      // TODO: A6/Q6 e-tron 100.0 kWh — wahrscheinlich Brutto, Netto ~94.9 kWh — verifizieren!
      // TODO: Q8 e-tron 106.0 kWh — war 114.0 kWh (Brutto), korrigiert auf Netto — verifizieren!
      Q8_E_TRON(CarBrand.AUDI, VehicleCategory.LARGE_SUV, "Q8 e-tron",
            cap(95.0),
            cap(106.0)),

      // --- PORSCHE ---
      TAYCAN(CarBrand.PORSCHE, VehicleCategory.SPORTS, "Taycan",
            cap(79.2),
            cap(93.4),
            cap(105.0)),
      TAYCAN_CROSS_TURISMO(CarBrand.PORSCHE, VehicleCategory.SPORTS, "Taycan Cross Turismo",
            cap(79.2),
            cap(93.4)),
      MACAN_ELECTRIC(CarBrand.PORSCHE, VehicleCategory.SUV, "Macan Electric",
            cap(95.0),
            cap(100.0)),

      // --- SMART ---
      SMART_1(CarBrand.SMART, VehicleCategory.COMPACT, "#1",
            cap(49.0),
            cap(66.0)),
      SMART_3(CarBrand.SMART, VehicleCategory.COMPACT, "#3",
            cap(49.0),
            cap(66.0)),
      FORTWO_EQ(CarBrand.SMART, VehicleCategory.CITY_CAR, "fortwo EQ",
            cap(17.6)),
      FORFOUR_EQ(CarBrand.SMART, VehicleCategory.CITY_CAR, "forfour EQ",
            cap(17.6)),

      // --- OPEL ---
      AMPERA_E(CarBrand.OPEL, VehicleCategory.COMPACT, "Ampera-e",
            cap(60.0)),
      CORSA_E(CarBrand.OPEL, VehicleCategory.CITY_CAR, "Corsa-e",
            cap(50.0),
            cap(51.0),
            cap(54.0)),
      ASTRA_E(CarBrand.OPEL, VehicleCategory.COMPACT, "Astra Electric",
            cap(54.0)),
      MOKKA_E(CarBrand.OPEL, VehicleCategory.COMPACT, "Mokka-e",
            cap(50.0),
            cap(54.0)),
      COMBO_E(CarBrand.OPEL, VehicleCategory.VAN, "Combo-e",
            cap(50.0)),
      VIVARO_E(CarBrand.OPEL, VehicleCategory.VAN, "Vivaro-e",
            cap(50.0),
            cap(75.0)),

      // --- PEUGEOT ---
      E_208(CarBrand.PEUGEOT, VehicleCategory.COMPACT, "e-208",
            cap(50.0),
            cap(51.0)),
      E_2008(CarBrand.PEUGEOT, VehicleCategory.COMPACT, "e-2008",
            cap(50.0),
            cap(54.0)),
      E_308(CarBrand.PEUGEOT, VehicleCategory.COMPACT, "e-308",
            cap(54.0)),
      E_5008(CarBrand.PEUGEOT, VehicleCategory.SUV, "e-5008",
            cap(73.0),
            cap(96.0)),
      E_RIFTER(CarBrand.PEUGEOT, VehicleCategory.VAN, "e-Rifter",
            cap(50.0)),

      // --- CITROEN ---
      E_C4(CarBrand.CITROEN, VehicleCategory.COMPACT, "ë-C4",
            cap(50.0),
            cap(54.0)),
      E_C5_AIRCROSS(CarBrand.CITROEN, VehicleCategory.SUV, "ë-C5 Aircross",
            cap(50.0)),
      E_BERLINGO(CarBrand.CITROEN, VehicleCategory.VAN, "ë-Berlingo",
            cap(50.0)),
      AMI(CarBrand.CITROEN, VehicleCategory.CITY_CAR, "Ami",
            cap(5.5)),

      // --- SKODA ---
      ENYAQ(CarBrand.SKODA, VehicleCategory.SUV, "Enyaq",
            cap(52.0),
            cap(55.0, "iV 50"),
            cap(62.0, "iV 60"),
            cap(77.0),
            cap(82.0, "iV 80")),
      ENYAQ_COUPE(CarBrand.SKODA, VehicleCategory.SUV, "Enyaq Coupé",
            cap(77.0),
            cap(82.0)),
      ELROQ(CarBrand.SKODA, VehicleCategory.COMPACT, "Elroq",
            cap(55.0),
            cap(63.0),
            cap(82.0)),

      // --- SEAT & CUPRA ---
      MII_ELECTRIC(CarBrand.SEAT, VehicleCategory.CITY_CAR, "Mii electric",
            cap(32.3),
            cap(36.8)),
      CUPRA_BORN(CarBrand.CUPRA, VehicleCategory.COMPACT, "Born",
            cap(45.0),
            cap(58.0),
            cap(77.0)),
      CUPRA_TAVASCAN(CarBrand.CUPRA, VehicleCategory.SUV, "Tavascan",
            cap(77.0)),

      // --- FORD ---
      MUSTANG_MACH_E(CarBrand.FORD, VehicleCategory.SUV, "Mustang Mach-E",
            cap(68.0, "Standard Range"),
            cap(75.7),
            cap(88.0, "Extended Range"),
            cap(91.0)),
      F_150_LIGHTNING(CarBrand.FORD, VehicleCategory.PICKUP, "F-150 Lightning",
            cap(98.0),
            cap(131.0)),
      E_TRANSIT(CarBrand.FORD, VehicleCategory.VAN, "E-Transit",
            cap(68.0),
            cap(89.0)),
      EXPLORER_EV(CarBrand.FORD, VehicleCategory.SUV, "Explorer",
            cap(52.0),
            cap(77.0),
            cap(79.0)),

      // --- CHEVROLET ---
      BOLT_EV(CarBrand.CHEVROLET, VehicleCategory.COMPACT, "Bolt EV",
            cap(60.0),
            cap(65.0)),
      BOLT_EUV(CarBrand.CHEVROLET, VehicleCategory.COMPACT, "Bolt EUV",
            cap(65.0)),
      BLAZER_EV(CarBrand.CHEVROLET, VehicleCategory.SUV, "Blazer EV",
            cap(85.0)),
      EQUINOX_EV(CarBrand.CHEVROLET, VehicleCategory.SUV, "Equinox EV",
            cap(85.0)),

      // --- RIVIAN ---
      R1T(CarBrand.RIVIAN, VehicleCategory.PICKUP, "R1T",
            cap(105.0),
            cap(135.0),
            cap(149.0),
            cap(180.0)),
      R1S(CarBrand.RIVIAN, VehicleCategory.LARGE_SUV, "R1S",
            cap(105.0),
            cap(135.0),
            cap(149.0),
            cap(180.0)),
      R2(CarBrand.RIVIAN, VehicleCategory.SUV, "R2",
            cap(100.0)),

      // --- LUCID ---
      AIR(CarBrand.LUCID, VehicleCategory.LUXURY, "Air",
            cap(88.0),
            cap(112.0),
            cap(118.0)),
      GRAVITY(CarBrand.LUCID, VehicleCategory.LARGE_SUV, "Gravity",
            cap(112.0),
            cap(120.0)),

      // --- FISKER ---
      OCEAN(CarBrand.FISKER, VehicleCategory.SUV, "Ocean",
            cap(70.0),
            cap(106.0),
            cap(113.0)),
      PEAR(CarBrand.FISKER, VehicleCategory.COMPACT, "Pear",
            cap(75.0)),

      // --- GENESIS ---
      GV60(CarBrand.GENESIS, VehicleCategory.COMPACT, "GV60",
            cap(77.4)),
      GV70_ELECTRIFIED(CarBrand.GENESIS, VehicleCategory.LARGE_SUV, "GV70 Electrified",
            cap(77.4)),
      G80_ELECTRIFIED(CarBrand.GENESIS, VehicleCategory.SEDAN, "G80 Electrified",
            cap(87.2)),
      GV90(CarBrand.GENESIS, VehicleCategory.LARGE_SUV, "GV90",
            cap(100.0)),

      // --- TOYOTA ---
      BZ4X(CarBrand.TOYOTA, VehicleCategory.SUV, "bZ4X",
            cap(71.4)),
      BZ3(CarBrand.TOYOTA, VehicleCategory.SEDAN, "bZ3",
            cap(65.3)),
      PROACE_ELECTRIC(CarBrand.TOYOTA, VehicleCategory.VAN, "Proace Electric",
            cap(50.0),
            cap(75.0)),

      // --- LEXUS ---
      UX_300E(CarBrand.LEXUS, VehicleCategory.COMPACT, "UX 300e",
            cap(54.3),
            cap(72.8)),
      RZ_450E(CarBrand.LEXUS, VehicleCategory.SUV, "RZ 450e",
            cap(71.4)),

      // --- NISSAN ---
      LEAF(CarBrand.NISSAN, VehicleCategory.COMPACT, "Leaf",
            cap(40.0, "40 kWh"),
            cap(59.0),
            cap(62.0, "e+ 62 kWh")),
      ARIYA(CarBrand.NISSAN, VehicleCategory.SUV, "Ariya",
            cap(63.0),
            cap(66.0),
            cap(87.0),
            cap(91.0)),
      TOWNSTAR_EV(CarBrand.NISSAN, VehicleCategory.VAN, "Townstar EV",
            cap(45.0)),

      // --- MAZDA ---
      MX_30(CarBrand.MAZDA, VehicleCategory.COMPACT, "MX-30",
            cap(35.5)),
      MX_30_REX(CarBrand.MAZDA, VehicleCategory.COMPACT, "MX-30 R-EV",
            cap(17.8)),

      // --- HONDA ---
      E(CarBrand.HONDA, VehicleCategory.CITY_CAR, "e",
            cap(35.5)),
      E_NY1(CarBrand.HONDA, VehicleCategory.COMPACT, "e:Ny1",
            cap(68.8)),

      // --- SUBARU ---
      SOLTERRA(CarBrand.SUBARU, VehicleCategory.SUV, "Solterra",
            cap(71.4)),

      // --- JAGUAR ---
      I_PACE(CarBrand.JAGUAR, VehicleCategory.LARGE_SUV, "I-Pace",
            cap(84.7),
            cap(90.0)),

      // --- LAND ROVER ---
      RANGE_ROVER_ELECTRIC(CarBrand.LAND_ROVER, VehicleCategory.LARGE_SUV, "Range Rover Electric",
            cap(100.0)),

      // --- LOTUS ---
      ELETRE(CarBrand.LOTUS, VehicleCategory.LARGE_SUV, "Eletre",
            cap(112.0)),
      EMEYA(CarBrand.LOTUS, VehicleCategory.SPORTS, "Emeya",
            cap(102.0)),

      // --- RENAULT ---
      RENAULT_5(CarBrand.RENAULT, VehicleCategory.CITY_CAR, "Renault 5",
            cap(40.0),
            cap(52.0)),
      ZOE(CarBrand.RENAULT, VehicleCategory.CITY_CAR, "Zoe",
            cap(41.0, "Z.E. 40"),
            cap(52.0, "Z.E. 50"),
            cap(55.0)),
      MEGANE_E_TECH(CarBrand.RENAULT, VehicleCategory.COMPACT, "Megane E-Tech",
            cap(40.0),
            cap(60.0)),
      SCENIC_E_TECH(CarBrand.RENAULT, VehicleCategory.SUV, "Scenic E-Tech",
            cap(60.0),
            cap(87.0)),
      KANGOO_E_TECH(CarBrand.RENAULT, VehicleCategory.VAN, "Kangoo E-Tech",
            cap(45.0)),
      TWINGO_E_TECH(CarBrand.RENAULT, VehicleCategory.CITY_CAR, "Twingo E-Tech",
            cap(22.0)),

      // --- DACIA ---
      DACIA_SPRING(CarBrand.DACIA, VehicleCategory.CITY_CAR, "Spring",
            cap(26.8),
            cap(33.0)),
      DACIA_BIGSTER_ELECTRIC(CarBrand.DACIA, VehicleCategory.SUV, "Bigster Electric",
            cap(60.0)),

      // --- FIAT ---
      FIAT_500E(CarBrand.FIAT, VehicleCategory.CITY_CAR, "500e",
            cap(21.3),
            cap(37.3),
            cap(42.0)),
      FIAT_600E(CarBrand.FIAT, VehicleCategory.COMPACT, "600e",
            cap(54.0)),

      // --- CHINA-FRAKTION erweitert ---
      BYD_HAN(CarBrand.BYD, VehicleCategory.SEDAN, "Han",
            cap(85.4)),
      BYD_TANG(CarBrand.BYD, VehicleCategory.LARGE_SUV, "Tang",
            cap(86.4),
            cap(108.8)),
      BYD_SEAGULL(CarBrand.BYD, VehicleCategory.CITY_CAR, "Seagull",
            cap(30.1),
            cap(38.9)),
      MG5(CarBrand.MG, VehicleCategory.COMPACT, "MG5",
            cap(50.3),
            cap(61.1)),
      MG_ZS_EV(CarBrand.MG, VehicleCategory.COMPACT, "MG ZS EV",
            cap(51.0),
            cap(72.6)),
      MG_MARVEL_R(CarBrand.MG, VehicleCategory.SUV, "Marvel R",
            cap(70.0)),
      NIO_ET7(CarBrand.NIO, VehicleCategory.SEDAN, "ET7",
            cap(75.0),
            cap(100.0),
            cap(150.0)),
      NIO_ES6(CarBrand.NIO, VehicleCategory.SUV, "ES6",
            cap(75.0),
            cap(100.0)),
      XPENG_P7(CarBrand.XPENG, VehicleCategory.SEDAN, "P7",
            cap(60.2),
            cap(70.8),
            cap(80.9)),
      XPENG_P7_PLUS(CarBrand.XPENG, VehicleCategory.SEDAN, "P7+",
            cap(60.0),
            cap(76.3),
            cap(85.0)),
      XPENG_G9(CarBrand.XPENG, VehicleCategory.LARGE_SUV, "G9",
            cap(78.2),
            cap(98.0)),
      ZEEKR_001(CarBrand.ZEEKR, VehicleCategory.SEDAN, "001",
            cap(86.0),
            cap(100.0)),
      ZEEKR_X(CarBrand.ZEEKR, VehicleCategory.COMPACT, "X",
            cap(64.0)),
      ORA_FUNKY_CAT(CarBrand.ORA, VehicleCategory.CITY_CAR, "Funky Cat",
            cap(48.0),
            cap(63.0)),

      // --- SONSTIGE ---
      SONSTIGE_CUSTOM(CarBrand.SONSTIGE, VehicleCategory.COMPACT, "Sonstiges Modell");

      private final CarBrand brand;
      private final VehicleCategory category;
      private final String displayName;
      private final List<CapacityEntry> capacityEntries;

      CarModel(CarBrand brand, VehicleCategory category, String displayName, CapacityEntry... caps) {
         this.brand = brand;
         this.category = category;
         this.displayName = displayName;
         this.capacityEntries = List.of(caps);
      }

      private static CapacityEntry cap(double kWh) {
         return new CapacityEntry(kWh, null);
      }

      private static CapacityEntry cap(double kWh, String variantName) {
         return new CapacityEntry(kWh, variantName);
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

      public List<CapacityEntry> getCapacityEntries() {
         return capacityEntries;
      }

      public Optional<String> variantNameFor(BigDecimal capacityKwh) {
         return capacityEntries.stream()
             .filter(c -> BigDecimal.valueOf(c.kWh()).compareTo(capacityKwh) == 0)
             .findFirst()
             .map(CapacityEntry::variantName);
      }

      public String getDisplayName() {
         return displayName;
      }
   }
}