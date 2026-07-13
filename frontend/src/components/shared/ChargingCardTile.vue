<script setup lang="ts">
import {
  cardDesign, cardContainerStyle, cardChipStyle, cardTextColor, cardSubTextColor,
} from '../../composables/useChargingCardDesign'

/**
 * Die Ladekarte als Kachel im Kreditkarten-Look. Reine Optik - wer sie klickbar
 * braucht (Log-Formular: Karte fuer die Ladung waehlen), packt sie in einen Button.
 * Optik kommt aus der Karten-ID, damit dieselbe Karte ueberall gleich aussieht.
 *
 * Die Kachel fuellt ihren Container - die Groesse bestimmt also die Umgebung
 * (fester Chip im Formular, mitwachsende Zeilenhoehe in der Verwaltung).
 */
defineProps<{
  id: string
  title: string
  /** Zweite Zeile, z.B. der AC-Preis. */
  subtitle?: string | null
}>()
</script>

<template>
  <div class="rounded-sm overflow-hidden relative select-none" :style="cardContainerStyle(id)">
    <!-- Stripe: diagonaler Farbkeil rechts -->
    <div v-if="cardDesign(id) === 'stripe'"
      class="absolute inset-y-0 right-0 w-14 skew-x-[-8deg] translate-x-4 pointer-events-none"
      style="background: linear-gradient(160deg, #059669 0%, #0891b2 100%);" />

    <!-- Circles: ueberlappende Halbkreise unten rechts -->
    <template v-else-if="cardDesign(id) === 'circles'">
      <div class="absolute -bottom-4 right-3 w-14 h-14 rounded-full opacity-60 pointer-events-none" style="background: #dc2626;" />
      <div class="absolute -bottom-4 right-8 w-14 h-14 rounded-full opacity-60 pointer-events-none" style="background: #ea580c;" />
    </template>

    <div class="relative z-10 h-full p-2.5 flex flex-col justify-between text-left">
      <div class="w-5 h-3.5 rounded-[3px]" :style="cardChipStyle(id)" />
      <div>
        <div class="text-[11px] font-bold leading-tight truncate" :style="{ color: cardTextColor(id) }">
          {{ title }}
        </div>
        <div v-if="subtitle" class="text-[10px] leading-tight mt-0.5" :style="{ color: cardSubTextColor(id) }">
          {{ subtitle }}
        </div>
      </div>
    </div>
  </div>
</template>
