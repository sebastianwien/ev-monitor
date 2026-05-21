---
title: "Why does EV Monitor show higher consumption than my trip computer?"
description: "EV Monitor often shows higher consumption than your trip computer. This is not a bug - it's a difference in what's being measured."
slug: "consumption-methodology"
date: "2026-05-21"
author: "Sebastian"
---

EV Monitor often shows higher consumption than your trip computer. This is not a bug - it's a difference in what's being measured.

## Three measurement points, three different values

Every kWh that moves your car passes through several stages. Each is measured differently - and all values are correct, they just answer different questions.

<div class="space-y-1 my-6">

  <div class="bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 rounded-sm p-4">
    <div class="flex items-start gap-3">
      <div class="mt-0.5 flex-shrink-0 w-8 h-8 rounded-full bg-yellow-100 dark:bg-yellow-900 flex items-center justify-center">
        <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" class="w-4 h-4 text-yellow-600 dark:text-yellow-400"><path stroke-linecap="round" stroke-linejoin="round" d="m3.75 13.5 10.5-11.25L12 10.5h8.25L9.75 21.75 12 13.5H3.75Z" /></svg>
      </div>
      <div class="flex-1">
        <div class="flex items-baseline justify-between gap-2 flex-wrap">
          <span class="font-semibold text-gray-900 dark:text-gray-100">Grid / Charging station</span>
          <span class="text-sm font-mono font-bold text-yellow-700 dark:text-yellow-400">24.82 kWh</span>
        </div>
        <p class="text-sm text-gray-500 dark:text-gray-400 mt-0.5">What you pay for. Comes from the wallbox, charging station, or provider invoice.</p>
      </div>
    </div>
  </div>

  <div class="flex items-center gap-3 px-4 py-1">
    <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" class="w-4 h-4 text-gray-400 flex-shrink-0 ml-2"><path stroke-linecap="round" stroke-linejoin="round" d="M19.5 13.5 12 21m0 0-7.5-7.5M12 21V3" /></svg>
    <span class="text-sm text-gray-400 dark:text-gray-500">~0.82 kWh charging loss (heat, AC-DC conversion)</span>
  </div>

  <div class="bg-green-50 dark:bg-green-900/20 border border-green-200 dark:border-green-800 rounded-sm p-4">
    <div class="flex items-start gap-3">
      <div class="mt-0.5 flex-shrink-0 w-8 h-8 rounded-full bg-green-100 dark:bg-green-900 flex items-center justify-center">
        <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" class="w-4 h-4 text-green-600 dark:text-green-400"><path stroke-linecap="round" stroke-linejoin="round" d="m3.75 13.5 10.5-11.25L12 10.5h8.25L9.75 21.75 12 13.5H3.75Z" /></svg>
      </div>
      <div class="flex-1">
        <div class="flex items-baseline justify-between gap-2 flex-wrap">
          <span class="font-semibold text-gray-900 dark:text-gray-100">Arrived in battery</span>
          <span class="text-sm font-mono font-bold text-green-700 dark:text-green-400">24.00 kWh</span>
        </div>
        <p class="text-sm text-gray-500 dark:text-gray-400 mt-0.5">What the vehicle reports directly. Transmitted unmodified from vehicle APIs (e.g. via Smartcar) - EV Monitor removes charging losses for manually entered station values.</p>
        <div class="mt-2 inline-flex items-center gap-1.5 text-xs font-medium text-green-700 dark:text-green-400 bg-green-100 dark:bg-green-900/40 px-2 py-0.5 rounded-full">
          <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" class="w-3 h-3"><path stroke-linecap="round" stroke-linejoin="round" d="M9.348 14.651a3.75 3.75 0 0 1 0-5.303m5.304 0a3.75 3.75 0 0 1 0 5.303m-7.425 2.122a6.75 6.75 0 0 1 0-9.546m9.546 0a6.75 6.75 0 0 1 0 9.546M5.106 18.894c-3.808-3.808-3.808-9.98 0-13.789m13.788 0c3.808 3.808 3.808 9.98 0 13.789M12 12h.008v.008H12V12Zm.375 0a.375.375 0 1 1-.75 0 .375.375 0 0 1 .75 0Z" /></svg>
          Basis for EV Monitor consumption calculation
        </div>
      </div>
    </div>
  </div>

  <div class="flex items-center gap-3 px-4 py-1">
    <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" class="w-4 h-4 text-gray-400 flex-shrink-0 ml-2"><path stroke-linecap="round" stroke-linejoin="round" d="M19.5 13.5 12 21m0 0-7.5-7.5M12 21V3" /></svg>
    <span class="text-sm text-gray-400 dark:text-gray-500">Standby consumption: climate, vehicle systems, software while parked (1-3 kWh/day)</span>
  </div>

  <div class="bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-sm p-4">
    <div class="flex items-start gap-3">
      <div class="mt-0.5 flex-shrink-0 w-8 h-8 rounded-full bg-blue-100 dark:bg-blue-900 flex items-center justify-center">
        <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" class="w-4 h-4 text-blue-600 dark:text-blue-400"><path stroke-linecap="round" stroke-linejoin="round" d="M11.412 15.655 9.75 21.75l3.745-4.012M9.257 13.5H3.75l2.659-2.849m2.048-2.194L14.25 2.25 12 10.5h8.25l-4.707 5.043M6.75 15.75 4.5 19.5m6-6 6.75 6.75M3.75 3.75l16.5 16.5" /></svg>
      </div>
      <div class="flex-1">
        <div class="flex items-baseline justify-between gap-2 flex-wrap">
          <span class="font-semibold text-gray-900 dark:text-gray-100">Trip computer</span>
          <span class="text-sm font-mono font-bold text-blue-700 dark:text-blue-400">~15 kWh/100km</span>
        </div>
        <p class="text-sm text-gray-500 dark:text-gray-400 mt-0.5">What the motor drew during the trip. Standby losses before and after the trip are <strong>not</strong> included.</p>
      </div>
    </div>
  </div>

</div>

## The critical gap: what EV Monitor cannot see

Between charging end and trip start - and between trip end and the next charge - time passes. During this time, the SoC drops. EV Monitor has no access to the exact SoC value *at trip start*. Only the two measurement points at charging ends are known.

<div class="relative space-y-0 my-6">

  <div class="flex items-center gap-4">
    <div class="w-28 flex-shrink-0 text-right text-xs text-gray-500 dark:text-gray-400 leading-tight">Charge end<br><span class="font-semibold text-gray-700 dark:text-gray-300">Apr 11</span></div>
    <div class="flex-shrink-0 w-3 h-3 rounded-full bg-green-500 ring-2 ring-white dark:ring-gray-900 z-10"></div>
    <div class="flex-1">
      <div class="flex items-center gap-2">
        <div class="h-5 rounded bg-green-500" style="width: 87%"></div>
        <span class="text-sm font-bold text-green-700 dark:text-green-400 whitespace-nowrap">87% SoC</span>
      </div>
      <div class="text-xs text-green-700 dark:text-green-400 mt-0.5">EV Monitor knows this value</div>
    </div>
  </div>

  <div class="flex items-stretch gap-4">
    <div class="w-28 flex-shrink-0"></div>
    <div class="flex-shrink-0 flex flex-col items-center"><div class="w-px flex-1 bg-orange-300 dark:bg-orange-700"></div></div>
    <div class="flex items-center py-2"><span class="text-xs text-orange-600 dark:text-orange-400 italic">Standby loss (duration unknown)</span></div>
  </div>

  <div class="flex items-center gap-4">
    <div class="w-28 flex-shrink-0 text-right text-xs text-gray-500 dark:text-gray-400 leading-tight">Trip start</div>
    <div class="flex-shrink-0 w-3 h-3 rounded-full bg-gray-300 dark:bg-gray-600 ring-2 ring-white dark:ring-gray-900 z-10"></div>
    <div class="flex-1">
      <div class="flex items-center gap-2">
        <div class="h-5 rounded bg-gray-300 dark:bg-gray-600" style="width: 82%"></div>
        <span class="text-sm font-bold text-gray-400 dark:text-gray-500 whitespace-nowrap">~82%?</span>
      </div>
      <div class="text-xs text-gray-400 dark:text-gray-500 mt-0.5">EV Monitor does <strong>not</strong> know this value</div>
    </div>
  </div>

  <div class="flex items-stretch gap-4">
    <div class="w-28 flex-shrink-0"></div>
    <div class="flex-shrink-0 flex flex-col items-center"><div class="w-px flex-1 bg-blue-400 dark:bg-blue-600"></div></div>
    <div class="flex items-center py-2"><span class="text-xs text-blue-600 dark:text-blue-400 italic">Trip 236 km - what the trip computer sees</span></div>
  </div>

  <div class="flex items-center gap-4">
    <div class="w-28 flex-shrink-0 text-right text-xs text-gray-500 dark:text-gray-400 leading-tight">Trip end</div>
    <div class="flex-shrink-0 w-3 h-3 rounded-full bg-gray-300 dark:bg-gray-600 ring-2 ring-white dark:ring-gray-900 z-10"></div>
    <div class="flex-1">
      <div class="flex items-center gap-2">
        <div class="h-5 rounded bg-gray-300 dark:bg-gray-600" style="width: 29%"></div>
        <span class="text-sm font-bold text-gray-400 dark:text-gray-500 whitespace-nowrap">~29%?</span>
      </div>
      <div class="text-xs text-gray-400 dark:text-gray-500 mt-0.5">EV Monitor does <strong>not</strong> know this value</div>
    </div>
  </div>

  <div class="flex items-stretch gap-4">
    <div class="w-28 flex-shrink-0"></div>
    <div class="flex-shrink-0 flex flex-col items-center"><div class="w-px flex-1 bg-orange-300 dark:bg-orange-700"></div></div>
    <div class="flex items-center py-2"><span class="text-xs text-orange-600 dark:text-orange-400 italic">Standby loss (duration unknown)</span></div>
  </div>

  <div class="flex items-center gap-4">
    <div class="w-28 flex-shrink-0 text-right text-xs text-gray-500 dark:text-gray-400 leading-tight">Charge start<br><span class="font-semibold text-gray-700 dark:text-gray-300">Apr 16</span></div>
    <div class="flex-shrink-0 w-3 h-3 rounded-full bg-green-500 ring-2 ring-white dark:ring-gray-900 z-10"></div>
    <div class="flex-1">
      <div class="flex items-center gap-2">
        <div class="h-5 rounded bg-green-500" style="width: 26%"></div>
        <span class="text-sm font-bold text-green-700 dark:text-green-400 whitespace-nowrap">26% SoC</span>
      </div>
      <div class="text-xs text-green-700 dark:text-green-400 mt-0.5">EV Monitor knows this value</div>
    </div>
  </div>

</div>

<div class="bg-orange-50 dark:bg-orange-900/20 border border-orange-200 dark:border-orange-800 rounded-sm p-4 my-4">
  <div class="flex gap-2 items-start">
    <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" class="w-5 h-5 text-orange-600 dark:text-orange-400 flex-shrink-0 mt-0.5"><path stroke-linecap="round" stroke-linejoin="round" d="m11.25 11.25.041-.02a.75.75 0 0 1 1.063.852l-.708 2.836a.75.75 0 0 0 1.063.853l.041-.021M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Zm-9-3.75h.008v.008H12V8.25Z" /></svg>
    <div class="text-sm text-orange-800 dark:text-orange-300"><strong>What this means:</strong> EV Monitor only sees the drop from 87% to 26% - 61 percentage points. Whether 57% is from driving and 4% from standby, or 59% driving and 2% standby, cannot be measured from the outside. But the total energy demand for this period is real - and EV Monitor captures it fully.</div>
  </div>
</div>

## How EV Monitor calculates

EV Monitor captures the energy between two charging events - not just during the trip. This includes standby losses that are genuinely lost and show up in the next electricity bill.

```
Consumption = (kWh charged + SoC correction) / distance × 100
```

<div class="bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-sm p-4 my-4">
  <div class="flex gap-2 items-start">
    <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" class="w-5 h-5 text-blue-600 dark:text-blue-400 flex-shrink-0 mt-0.5"><path stroke-linecap="round" stroke-linejoin="round" d="m11.25 11.25.041-.02a.75.75 0 0 1 1.063.852l-.708 2.836a.75.75 0 0 0 1.063.853l.041-.021M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Zm-9-3.75h.008v.008H12V8.25Z" /></svg>
    <div class="text-sm text-blue-800 dark:text-blue-300"><strong>What is the SoC correction?</strong><br>You never charge from 0% to 100%. The correction accounts for the battery being fuller or emptier after this charge than after the previous one - so only the consumption of the actually driven distance is calculated, nothing more, nothing less.</div>
  </div>
</div>

## Why EV Monitor shows more than the trip computer

This is not a bug. The trip computer only measures what the drivetrain needs during the trip. Energy consumed by the vehicle while parked does not appear on the trip computer, but is genuinely lost and needs to be recharged.

<div class="grid sm:grid-cols-2 gap-4 my-6">
  <div class="bg-gray-50 dark:bg-gray-700 rounded-sm p-4 border border-gray-200 dark:border-gray-600">
    <div class="text-2xl font-bold text-green-600 dark:text-green-400 mb-1">+1.75-2 kWh</div>
    <div class="text-sm text-gray-600 dark:text-gray-400">Average difference between real consumption and trip computer display, according to independent research.</div>
  </div>
  <div class="bg-gray-50 dark:bg-gray-700 rounded-sm p-4 border border-gray-200 dark:border-gray-600">
    <div class="text-2xl font-bold text-orange-500 dark:text-orange-400 mb-1">1-3 kWh/day</div>
    <div class="text-sm text-gray-600 dark:text-gray-400">Typical standby consumption of an EV while parked, depending on vehicle, temperature, and software version.</div>
  </div>
</div>

## The inevitable uncertainty

No method is perfect - that's the nature of measurement:

- **SoC values** are stored by EV Monitor as whole percentage points. 1% corresponds to roughly 0.5-0.7 kWh depending on battery size - a measurable inaccuracy.
- **kWh values from the vehicle** are reported by the vehicle BMS or manufacturer API and passed through unmodified by services like Smartcar. Precision depends on the car manufacturer.
- **Wallbox and charging station measurements** have manufacturer-dependent measurement tolerances.
- **Standby losses** vary significantly depending on outside temperature, software version, and usage patterns.

EV Monitor uses the median across multiple charging events to smooth out outliers, and flags individual trips with a plausibility indicator when the calculated value appears unusual.
