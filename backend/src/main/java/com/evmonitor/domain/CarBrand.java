package com.evmonitor.domain;

import java.util.Arrays;
import java.util.List;

public enum CarBrand {

   // Deutschland
   AUDI("Audi"),
   BMW("BMW"),
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
      // --- TESLA (König der Effizienz und der minimalen Infos) ---
      // TODO: 75.0/79.0 kWh NMC-Varianten — wahrscheinlich Brutto (Netto ~73.8/~75.0 kWh) — verifizieren!
      // LFP-Varianten (57.5/60.0) sind korrekt netto (LFP = 100% nutzbar)
      MODEL_3(CarBrand.TESLA, "Model 3", 57.5, 75.0, 79.0),
      MODEL_Y(CarBrand.TESLA, "Model Y", 60.0, 75.0, 79.0),
      MODEL_S(CarBrand.TESLA, "Model S", 95.0),
      MODEL_X(CarBrand.TESLA, "Model X", 95.0),
      CYBERTRUCK(CarBrand.TESLA, "Cybertruck", 123.0),

      // --- VW (Die ID-Familie wächst unaufhaltsam) ---
      ID_3(CarBrand.VW, "ID.3", 45.0, 58.0, 77.0, 79.0),
      ID_4(CarBrand.VW, "ID.4", 52.0, 77.0, 82.0),
      ID_5(CarBrand.VW, "ID.5", 77.0),
      ID_7(CarBrand.VW, "ID.7", 77.0, 86.0),
      ID_BUZZ(CarBrand.VW, "ID. Buzz", 79.0, 86.0),
      ID_POLO(CarBrand.VW, "ID. Polo", 38.0, 56.0), // 2026er Prognose
      E_UP(CarBrand.VW, "e-up!", 32.3, 36.8),
      E_GOLF(CarBrand.VW, "e-Golf", 24.2, 35.8),

      // --- HYUNDAI & KIA (800V-Monster) ---
      IONIQ_5(CarBrand.HYUNDAI, "Ioniq 5", 58.0, 77.4, 84.0),
      // TODO: 63.0 kWh — evtl. Brutto (Netto ~61.1 kWh) — verifizieren!
      IONIQ_6(CarBrand.HYUNDAI, "Ioniq 6", 53.0, 63.0, 77.4, 84.0), // Facelift 2026
      IONIQ_ELECTRIC(CarBrand.HYUNDAI, "Ioniq Electric", 28.0, 38.3),
      KONA_ELECTRIC(CarBrand.HYUNDAI, "Kona Electric", 39.2, 48.6, 64.8, 65.4),
      EV_6(CarBrand.KIA, "EV6", 58.0, 77.4, 84.0),
      EV_9(CarBrand.KIA, "EV9", 76.1, 99.8),
      EV_3(CarBrand.KIA, "EV3", 58.3, 81.4),
      NIRO_EV(CarBrand.KIA, "Niro EV", 39.2, 64.8),
      E_SOUL(CarBrand.KIA, "e-Soul", 39.2, 64.0),

      // --- BMW (Bayerische Akku-Präzision) ---
      I3(CarBrand.BMW, "i3", 33.0, 42.2),
      I4(CarBrand.BMW, "i4", 67.0, 80.7),
      I5(CarBrand.BMW, "i5", 81.2),
      I7(CarBrand.BMW, "i7", 101.7),
      IX(CarBrand.BMW, "iX", 76.6, 105.2),
      IX1(CarBrand.BMW, "iX1", 64.7),
      IX2(CarBrand.BMW, "iX2", 64.7),
      IX3(CarBrand.BMW, "iX3", 74.0),
      IX3_NEUE_KLASSE(CarBrand.BMW, "iX3 (Neue Klasse)", 108.7), // Welcome to 2026

      // --- POLESTAR & VOLVO ---
      POLESTAR_2(CarBrand.POLESTAR, "Polestar 2", 67.0, 75.0, 78.0, 82.0),
      // TODO: 107.0 kWh — wahrscheinlich Brutto (Netto ~104 kWh) — verifizieren!
      POLESTAR_3(CarBrand.POLESTAR, "Polestar 3", 107.0),
      POLESTAR_4(CarBrand.POLESTAR, "Polestar 4", 94.0),
      EX_30(CarBrand.VOLVO, "EX30", 49.0, 64.0),
      EX_90(CarBrand.VOLVO, "EX90", 107.0),

      // --- CHINA-FRAKTION (Blade Batteries everywhere) ---
      BYD_ATTO_3(CarBrand.BYD, "Atto 3", 60.5),
      BYD_SEAL(CarBrand.BYD, "Seal", 61.4, 82.5),
      BYD_DOLPHIN(CarBrand.BYD, "Dolphin", 44.9, 60.4),
      MG4(CarBrand.MG, "MG4", 51.0, 64.0, 77.0),
      NIO_ET5(CarBrand.NIO, "ET5", 75.0, 100.0, 150.0), // 150 kWh Solid-State!
      XPENG_G6(CarBrand.XPENG, "G6", 66.0, 80.0, 87.5), // 80.0 = chinesische Variante

      // --- MERCEDES (Luxus-Kapazitäten) ---
      // Netto-Werte: EQA/EQB haben eine einzige Batterie (66.5 kWh netto / 70.5 kWh brutto)
      EQA(CarBrand.MERCEDES, "EQA", 66.5),
      EQB(CarBrand.MERCEDES, "EQB", 66.5),
      EQC(CarBrand.MERCEDES, "EQC", 80.0),
      EQE(CarBrand.MERCEDES, "EQE", 89.0, 90.6),
      EQE_SUV(CarBrand.MERCEDES, "EQE SUV", 90.6, 96.0),
      // EQS/EQS SUV: 118 kWh ist Bruttowert, alle Varianten teilen 107.8/108.4 kWh netto
      EQS(CarBrand.MERCEDES, "EQS", 107.8),
      EQS_SUV(CarBrand.MERCEDES, "EQS SUV", 108.4),
      EQV(CarBrand.MERCEDES, "EQV", 90.0),

      // --- AUDI (Vorsprung durch E-Technik) ---
      A6_E_TRON(CarBrand.AUDI, "A6 e-tron", 83.0, 100.0),
      E_TRON(CarBrand.AUDI, "e-tron", 71.0, 95.0),
      E_TRON_GT(CarBrand.AUDI, "e-tron GT", 93.4),
      Q4_E_TRON(CarBrand.AUDI, "Q4 e-tron", 52.0, 77.0, 82.0),
      Q6_E_TRON(CarBrand.AUDI, "Q6 e-tron", 83.0, 100.0),
      // TODO: A6/Q6 e-tron 100.0 kWh — wahrscheinlich Brutto, Netto ~94.9 kWh — verifizieren!
      // TODO: Q8 e-tron 106.0 kWh — war 114.0 kWh (Brutto), korrigiert auf Netto — verifizieren!
      Q8_E_TRON(CarBrand.AUDI, "Q8 e-tron", 95.0, 106.0),

      // --- PORSCHE (Sportwagen-Elektrifizierung) ---
      TAYCAN(CarBrand.PORSCHE, "Taycan", 79.2, 93.4, 105.0),
      TAYCAN_CROSS_TURISMO(CarBrand.PORSCHE, "Taycan Cross Turismo", 79.2, 93.4),
      MACAN_ELECTRIC(CarBrand.PORSCHE, "Macan Electric", 95.0, 100.0),

      // --- SMART (Kleinstwagen neu erfunden) ---
      SMART_1(CarBrand.SMART, "#1", 49.0, 66.0),
      SMART_3(CarBrand.SMART, "#3", 49.0, 66.0),
      FORTWO_EQ(CarBrand.SMART, "fortwo EQ", 17.6),
      FORFOUR_EQ(CarBrand.SMART, "forfour EQ", 17.6),

      // --- OPEL (Stellantis E-Offensive) ---
      CORSA_E(CarBrand.OPEL, "Corsa-e", 50.0, 51.0, 54.0),
      ASTRA_E(CarBrand.OPEL, "Astra Electric", 54.0),
      MOKKA_E(CarBrand.OPEL, "Mokka-e", 50.0, 54.0),
      COMBO_E(CarBrand.OPEL, "Combo-e", 50.0),
      VIVARO_E(CarBrand.OPEL, "Vivaro-e", 50.0, 75.0),

      // --- PEUGEOT (Lion goes electric) ---
      E_208(CarBrand.PEUGEOT, "e-208", 50.0, 51.0),
      E_2008(CarBrand.PEUGEOT, "e-2008", 50.0, 54.0),
      E_308(CarBrand.PEUGEOT, "e-308", 54.0),
      E_5008(CarBrand.PEUGEOT, "e-5008", 73.0, 96.0),
      E_RIFTER(CarBrand.PEUGEOT, "e-Rifter", 50.0),

      // --- CITROEN (Komfort trifft Elektro) ---
      E_C4(CarBrand.CITROEN, "ë-C4", 50.0, 54.0),
      E_C5_AIRCROSS(CarBrand.CITROEN, "ë-C5 Aircross", 50.0),
      E_BERLINGO(CarBrand.CITROEN, "ë-Berlingo", 50.0),
      AMI(CarBrand.CITROEN, "Ami", 5.5),

      // --- SKODA (Simply Electric) ---
      ENYAQ(CarBrand.SKODA, "Enyaq", 52.0, 55.0, 62.0, 77.0, 82.0),
      ENYAQ_COUPE(CarBrand.SKODA, "Enyaq Coupé", 77.0, 82.0),
      ELROQ(CarBrand.SKODA, "Elroq", 55.0, 63.0, 82.0),

      // --- SEAT & CUPRA (Sportlich elektrisch) ---
      MII_ELECTRIC(CarBrand.SEAT, "Mii electric", 32.3, 36.8),
      CUPRA_BORN(CarBrand.CUPRA, "Born", 45.0, 58.0, 77.0),
      CUPRA_TAVASCAN(CarBrand.CUPRA, "Tavascan", 77.0),

      // --- FORD (Mustang goes electric) ---
      MUSTANG_MACH_E(CarBrand.FORD, "Mustang Mach-E", 68.0, 75.7, 88.0, 91.0),
      F_150_LIGHTNING(CarBrand.FORD, "F-150 Lightning", 98.0, 131.0),
      E_TRANSIT(CarBrand.FORD, "E-Transit", 68.0, 89.0),
      EXPLORER_EV(CarBrand.FORD, "Explorer", 52.0, 77.0, 79.0),

      // --- CHEVROLET (GM Elektro-Comeback) ---
      BOLT_EV(CarBrand.CHEVROLET, "Bolt EV", 60.0, 65.0),
      BOLT_EUV(CarBrand.CHEVROLET, "Bolt EUV", 65.0),
      BLAZER_EV(CarBrand.CHEVROLET, "Blazer EV", 85.0),
      EQUINOX_EV(CarBrand.CHEVROLET, "Equinox EV", 85.0),

      // --- RIVIAN (Adventure-EVs) ---
      R1T(CarBrand.RIVIAN, "R1T", 105.0, 135.0, 149.0, 180.0),
      R1S(CarBrand.RIVIAN, "R1S", 105.0, 135.0, 149.0, 180.0),
      R2(CarBrand.RIVIAN, "R2", 100.0),

      // --- LUCID (Luxus-Effizienz-Monster) ---
      AIR(CarBrand.LUCID, "Air", 88.0, 112.0, 118.0),
      GRAVITY(CarBrand.LUCID, "Gravity", 112.0, 120.0),

      // --- FISKER (Nachhaltiges Design) ---
      OCEAN(CarBrand.FISKER, "Ocean", 70.0, 106.0, 113.0),
      PEAR(CarBrand.FISKER, "Pear", 75.0),

      // --- GENESIS (Luxus aus Korea) ---
      GV60(CarBrand.GENESIS, "GV60", 77.4),
      GV70_ELECTRIFIED(CarBrand.GENESIS, "GV70 Electrified", 77.4),
      G80_ELECTRIFIED(CarBrand.GENESIS, "G80 Electrified", 87.2),
      GV90(CarBrand.GENESIS, "GV90", 100.0),

      // --- TOYOTA (Endlich dabei) ---
      BZ4X(CarBrand.TOYOTA, "bZ4X", 71.4),
      BZ3(CarBrand.TOYOTA, "bZ3", 65.3),
      PROACE_ELECTRIC(CarBrand.TOYOTA, "Proace Electric", 50.0, 75.0),

      // --- LEXUS (Hybrid-König wagt E-Schritt) ---
      UX_300E(CarBrand.LEXUS, "UX 300e", 54.3, 72.8),
      RZ_450E(CarBrand.LEXUS, "RZ 450e", 71.4),

      // --- NISSAN (Leaf-Pioniere) ---
      LEAF(CarBrand.NISSAN, "Leaf", 40.0, 59.0, 62.0),
      ARIYA(CarBrand.NISSAN, "Ariya", 63.0, 66.0, 87.0, 91.0),
      TOWNSTAR_EV(CarBrand.NISSAN, "Townstar EV", 45.0),

      // --- MAZDA (Zoom-Zoom elektrisch) ---
      MX_30(CarBrand.MAZDA, "MX-30", 35.5),
      MX_30_REX(CarBrand.MAZDA, "MX-30 R-EV", 17.8),

      // --- HONDA (e-Revolution) ---
      E(CarBrand.HONDA, "e", 35.5),
      E_NY1(CarBrand.HONDA, "e:Ny1", 68.8),

      // --- SUBARU (AWD goes electric) ---
      SOLTERRA(CarBrand.SUBARU, "Solterra", 71.4),

      // --- JAGUAR (British Luxury Electric) ---
      I_PACE(CarBrand.JAGUAR, "I-Pace", 84.7, 90.0),

      // --- LAND ROVER (Luxury Off-Road Electric) ---
      RANGE_ROVER_ELECTRIC(CarBrand.LAND_ROVER, "Range Rover Electric", 100.0),

      // --- LOTUS (Leichtbau trifft Elektro) ---
      ELETRE(CarBrand.LOTUS, "Eletre", 112.0),
      EMEYA(CarBrand.LOTUS, "Emeya", 102.0),

      // --- RENAULT (Französische E-Vielfalt) ---
      RENAULT_5(CarBrand.RENAULT, "Renault 5", 40.0, 52.0),
      ZOE(CarBrand.RENAULT, "Zoe", 41.0, 52.0, 55.0),
      MEGANE_E_TECH(CarBrand.RENAULT, "Megane E-Tech", 40.0, 60.0),
      SCENIC_E_TECH(CarBrand.RENAULT, "Scenic E-Tech", 60.0, 87.0),
      KANGOO_E_TECH(CarBrand.RENAULT, "Kangoo E-Tech", 45.0),
      TWINGO_E_TECH(CarBrand.RENAULT, "Twingo E-Tech", 22.0),

      // --- DACIA (Günstig elektrisch) ---
      DACIA_SPRING(CarBrand.DACIA, "Spring", 26.8, 33.0),
      DACIA_BIGSTER_ELECTRIC(CarBrand.DACIA, "Bigster Electric", 60.0),

      // --- FIAT (Italienische Elektro-Ikone) ---
      FIAT_500E(CarBrand.FIAT, "500e", 21.3, 37.3, 42.0),
      FIAT_600E(CarBrand.FIAT, "600e", 54.0),

      // --- CHINA-FRAKTION erweitert ---
      BYD_HAN(CarBrand.BYD, "Han", 85.4),
      BYD_TANG(CarBrand.BYD, "Tang", 86.4, 108.8),
      BYD_SEAGULL(CarBrand.BYD, "Seagull", 30.1, 38.9),
      MG5(CarBrand.MG, "MG5", 50.3, 61.1),
      MG_ZS_EV(CarBrand.MG, "MG ZS EV", 51.0, 72.6),
      MG_MARVEL_R(CarBrand.MG, "Marvel R", 70.0),
      NIO_ET7(CarBrand.NIO, "ET7", 75.0, 100.0, 150.0),
      NIO_ES6(CarBrand.NIO, "ES6", 75.0, 100.0),
      XPENG_P7(CarBrand.XPENG, "P7", 60.2, 70.8, 80.9),
      XPENG_P7_PLUS(CarBrand.XPENG, "P7+", 60.0, 76.3, 85.0),
      XPENG_G9(CarBrand.XPENG, "G9", 78.2, 98.0),
      ZEEKR_001(CarBrand.ZEEKR, "001", 86.0, 100.0),
      ZEEKR_X(CarBrand.ZEEKR, "X", 64.0),
      ORA_FUNKY_CAT(CarBrand.ORA, "Funky Cat", 48.0, 63.0);

      private final CarBrand brand;
      private final String displayName;
      private final List<Double> capacities;

      CarModel(CarBrand brand, String displayName, Double... caps) {
         this.brand = brand;
         this.displayName = displayName;
         this.capacities = List.of(caps);
      }

      // Hilfsmethode: Filtert Modelle nach Marke (für dein Dropdown)
      public static List<CarModel> byBrand(CarBrand brand) {
         return Arrays.stream(values())
             .filter(m -> m.brand == brand)
             .toList();
      }

      public CarBrand getBrand() {
         return brand;
      }

      public List<Double> getCapacities() {
         return capacities;
      }

      public String getDisplayName() {
         return displayName;
      }
   }
}