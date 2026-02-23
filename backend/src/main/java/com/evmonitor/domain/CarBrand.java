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
   WEY("WEY"),
   AIWAYS("Aiways"),
   MAXUS("Maxus"),
   VOYAH("Voyah"),
   HONGI("Hongqi"),
   AVATR("Avatr"),
   LEAPMOTOR("Leapmotor"),
   SERES("Seres"),
   GEELY("Geely"),
   CHERY("Chery"),

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

      // --- HYUNDAI & KIA (800V-Monster) ---
      IONIQ_5(CarBrand.HYUNDAI, "Ioniq 5", 58.0, 77.4, 84.0),
      IONIQ_6(CarBrand.HYUNDAI, "Ioniq 6", 53.0, 63.0, 77.4, 84.0), // Facelift 2026
      EV_6(CarBrand.KIA, "EV6", 58.0, 77.4, 84.0),
      EV_9(CarBrand.KIA, "EV9", 76.1, 99.8),
      EV_3(CarBrand.KIA, "EV3", 58.3, 81.4),

      // --- BMW (Bayerische Akku-Präzision) ---
      I4(CarBrand.BMW, "i4", 67.0, 80.7),
      I5(CarBrand.BMW, "i5", 81.2),
      I7(CarBrand.BMW, "i7", 101.7),
      IX1(CarBrand.BMW, "iX1", 64.7),
      IX3_NEUE_KLASSE(CarBrand.BMW, "iX3 (Neue Klasse)", 85.0, 100.0), // Welcome to 2026

      // --- POLESTAR & VOLVO ---
      POLESTAR_2(CarBrand.POLESTAR, "Polestar 2", 67.0, 75.0, 78.0, 82.0),
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
      XPENG_G6(CarBrand.XPENG, "G6", 66.0, 87.5),

      // --- MERCEDES (Luxus-Kapazitäten) ---
      EQA(CarBrand.MERCEDES, "EQA", 66.5, 70.5),
      EQE(CarBrand.MERCEDES, "EQE", 89.0, 90.6),
      EQS(CarBrand.MERCEDES, "EQS", 107.8, 118.0),

      // --- DER REST ---
      RENAULT_5(CarBrand.RENAULT, "Renault 5", 40.0, 52.0),
      DACIA_SPRING(CarBrand.DACIA, "Spring", 26.8),
      FIAT_500E(CarBrand.FIAT, "500e", 21.3, 37.3),

      UNKNOWN(CarBrand.SONSTIGE, "Unbekannt", 50.0);

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

      public List<Double> getCapacities() {
         return capacities;
      }

      public String getDisplayName() {
         return displayName;
      }
   }
}