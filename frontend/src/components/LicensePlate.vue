<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ plate: string }>()

// FE-Engschrift ab 9 Zeichen (z.B. "M-EV 1234E")
const isLong = computed(() => (props.plate ?? '').replace(/\s/g, '').length >= 9)

// de-DE → 'DE', de-AT → 'AT', de-CH → 'CH', alles andere → 'NEUTRAL'
type PlateStyle = 'DE' | 'AT' | 'CH' | 'NEUTRAL'
const style = computed<PlateStyle>(() => {
  const lang = navigator.language ?? ''
  if (lang === 'de-DE') return 'DE'
  if (lang === 'de-AT') return 'AT'
  if (lang === 'de-CH' || lang === 'fr-CH' || lang === 'it-CH') return 'CH'
  return 'NEUTRAL'
})

const euCountryCode = computed(() => style.value === 'AT' ? 'A' : 'D')
</script>

<template>
  <!-- DE / AT: EU-Band links mit Sternen -->
  <div v-if="style === 'DE' || style === 'AT'" class="plate-outer">
    <div class="eu-band">
      <div class="eu-stars">
        <span v-for="i in 12" :key="i" class="star" :style="{ transform: `rotate(${i * 30}deg) translateY(-5px)` }">★</span>
      </div>
      <span class="eu-country">{{ euCountryCode }}</span>
    </div>
    <span class="plate-text" :class="{ 'plate-text--eng': isLong }">{{ (plate ?? '').toUpperCase() }}</span>
  </div>

  <!-- CH: roter Rand, rotes Band links mit weißem Kreuz -->
  <div v-else-if="style === 'CH'" class="plate-outer plate-outer--ch">
    <div class="ch-band">
      <svg viewBox="0 0 20 20" class="ch-cross" aria-hidden="true">
        <rect x="8" y="3" width="4" height="14" fill="white" />
        <rect x="3" y="8" width="14" height="4" fill="white" />
      </svg>
    </div>
    <span class="plate-text" :class="{ 'plate-text--eng': isLong }">{{ (plate ?? '').toUpperCase() }}</span>
  </div>

  <!-- NEUTRAL: kein Band, schlichtes weißes Schild -->
  <div v-else class="plate-outer plate-outer--neutral">
    <span class="plate-text" :class="{ 'plate-text--eng': isLong }">{{ (plate ?? '').toUpperCase() }}</span>
  </div>
</template>

<style scoped>
@font-face {
  font-family: 'FE-Schrift';
  src: url('/fonts/GL-Nummernschild-Mtl.ttf') format('truetype');
  font-display: swap;
}
@font-face {
  font-family: 'FE-Schrift-Eng';
  src: url('/fonts/GL-Nummernschild-Eng.ttf') format('truetype');
  font-display: swap;
}

.plate-outer {
  display: inline-flex;
  align-items: stretch;
  background: #fff;
  border: 2.5px solid #111;
  border-radius: 3px;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0,0,0,0.2), inset 0 0 0 1px rgba(255,255,255,0.5);
  height: 34px;
}

.plate-outer--ch {
  border-color: #D00;
  border-width: 2.5px;
}

.plate-outer--neutral {
  /* kein Band, nur Schild */
}

/* ── EU-Band (DE / AT) ───────────────────────────── */
.eu-band {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: #003399;
  width: 22px;
  padding: 2px 0;
  gap: 1px;
  flex-shrink: 0;
}

.eu-stars {
  position: relative;
  width: 12px;
  height: 12px;
}

.star {
  position: absolute;
  top: 50%;
  left: 50%;
  font-size: 3px;
  color: #FFD700;
  transform-origin: 0 0;
  line-height: 1;
  margin-left: -1px;
  margin-top: -1px;
}

.eu-country {
  font-size: 8px;
  font-weight: 900;
  color: #fff;
  letter-spacing: 0;
  line-height: 1;
  font-family: Arial, sans-serif;
}

/* ── CH-Band ─────────────────────────────────────── */
.ch-band {
  display: flex;
  align-items: center;
  justify-content: center;
  background: #D00;
  width: 22px;
  flex-shrink: 0;
}

.ch-cross {
  width: 14px;
  height: 14px;
}

/* ── Kennzeichen-Text ────────────────────────────── */
.plate-text {
  display: flex;
  align-items: center;
  padding: 0 10px;
  font-family: 'FE-Schrift', 'Arial Narrow', Arial, sans-serif;
  font-size: 19px;
  font-weight: 400;
  color: #111;
  letter-spacing: 0.04em;
  white-space: nowrap;
}

.plate-text--eng {
  font-family: 'FE-Schrift-Eng', 'Arial Narrow', Arial, sans-serif;
  font-size: 18px;
}
</style>
