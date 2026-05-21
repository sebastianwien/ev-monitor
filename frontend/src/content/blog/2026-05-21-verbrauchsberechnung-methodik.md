---
title: "Warum zeigt EV Monitor mehr Verbrauch als mein Bordcomputer?"
description: "EV Monitor zeigt oft einen höheren Verbrauch als der Bordcomputer. Das ist kein Fehler - sondern ein Unterschied darin, was gemessen wird."
slug: "verbrauchsberechnung-methodik"
date: "2026-05-21"
author: "Sebastian"
---

EV Monitor zeigt dir oft einen höheren Verbrauch als dein Bordcomputer. Das ist kein Fehler - sondern ein Unterschied darin, was gemessen wird.

## Drei Messpunkte, drei verschiedene Werte

Jede kWh, die dein Auto bewegt, durchläuft mehrere Stationen. An jeder wird anders gemessen - und alle Werte sind korrekt, sie beantworten nur verschiedene Fragen.

<div class="space-y-1 my-6">

  <div class="bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 rounded-sm p-4">
    <div class="flex items-start gap-3">
      <div class="mt-0.5 flex-shrink-0 w-8 h-8 rounded-full bg-yellow-100 dark:bg-yellow-900 flex items-center justify-center">
        <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" class="w-4 h-4 text-yellow-600 dark:text-yellow-400"><path stroke-linecap="round" stroke-linejoin="round" d="m3.75 13.5 10.5-11.25L12 10.5h8.25L9.75 21.75 12 13.5H3.75Z" /></svg>
      </div>
      <div class="flex-1">
        <div class="flex items-baseline justify-between gap-2 flex-wrap">
          <span class="font-semibold text-gray-900 dark:text-gray-100">Stromnetz / Ladesäule</span>
          <span class="text-sm font-mono font-bold text-yellow-700 dark:text-yellow-400">24,82 kWh</span>
        </div>
        <p class="text-sm text-gray-500 dark:text-gray-400 mt-0.5">Was du bezahlst. Kommt von der Wallbox, der Ladestation oder der Abrechnung des Anbieters.</p>
      </div>
    </div>
  </div>

  <div class="flex items-center gap-3 px-4 py-1">
    <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" class="w-4 h-4 text-gray-400 flex-shrink-0 ml-2"><path stroke-linecap="round" stroke-linejoin="round" d="M19.5 13.5 12 21m0 0-7.5-7.5M12 21V3" /></svg>
    <span class="text-sm text-gray-400 dark:text-gray-500">~0,82 kWh Ladeverlust (Wärme, AC-DC-Wandlung)</span>
  </div>

  <div class="bg-green-50 dark:bg-green-900/20 border border-green-200 dark:border-green-800 rounded-sm p-4">
    <div class="flex items-start gap-3">
      <div class="mt-0.5 flex-shrink-0 w-8 h-8 rounded-full bg-green-100 dark:bg-green-900 flex items-center justify-center">
        <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" class="w-4 h-4 text-green-600 dark:text-green-400"><path stroke-linecap="round" stroke-linejoin="round" d="m3.75 13.5 10.5-11.25L12 10.5h8.25L9.75 21.75 12 13.5H3.75Z" /></svg>
      </div>
      <div class="flex-1">
        <div class="flex items-baseline justify-between gap-2 flex-wrap">
          <span class="font-semibold text-gray-900 dark:text-gray-100">Im Akku angekommen</span>
          <span class="text-sm font-mono font-bold text-green-700 dark:text-green-400">24,00 kWh</span>
        </div>
        <p class="text-sm text-gray-500 dark:text-gray-400 mt-0.5">Was das Fahrzeug selbst meldet. Wird von Fahrzeug-APIs (z.B. über Smartcar) direkt übermittelt - EV Monitor rechnet für manuell eingetragene Ladesäulenwerte den Ladeverlust heraus.</p>
        <div class="mt-2 inline-flex items-center gap-1.5 text-xs font-medium text-green-700 dark:text-green-400 bg-green-100 dark:bg-green-900/40 px-2 py-0.5 rounded-full">
          <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" class="w-3 h-3"><path stroke-linecap="round" stroke-linejoin="round" d="M9.348 14.651a3.75 3.75 0 0 1 0-5.303m5.304 0a3.75 3.75 0 0 1 0 5.303m-7.425 2.122a6.75 6.75 0 0 1 0-9.546m9.546 0a6.75 6.75 0 0 1 0 9.546M5.106 18.894c-3.808-3.808-3.808-9.98 0-13.789m13.788 0c3.808 3.808 3.808 9.98 0 13.789M12 12h.008v.008H12V12Zm.375 0a.375.375 0 1 1-.75 0 .375.375 0 0 1 .75 0Z" /></svg>
          Basis für EV Monitor Verbrauchsberechnung
        </div>
      </div>
    </div>
  </div>

  <div class="flex items-center gap-3 px-4 py-1">
    <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" class="w-4 h-4 text-gray-400 flex-shrink-0 ml-2"><path stroke-linecap="round" stroke-linejoin="round" d="M19.5 13.5 12 21m0 0-7.5-7.5M12 21V3" /></svg>
    <span class="text-sm text-gray-400 dark:text-gray-500">Standby-Verbrauch: Klimatisierung, Fahrzeug-Systeme, Software im Stand (1-3 kWh/Tag)</span>
  </div>

  <div class="bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-sm p-4">
    <div class="flex items-start gap-3">
      <div class="mt-0.5 flex-shrink-0 w-8 h-8 rounded-full bg-blue-100 dark:bg-blue-900 flex items-center justify-center">
        <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" class="w-4 h-4 text-blue-600 dark:text-blue-400"><path stroke-linecap="round" stroke-linejoin="round" d="M11.412 15.655 9.75 21.75l3.745-4.012M9.257 13.5H3.75l2.659-2.849m2.048-2.194L14.25 2.25 12 10.5h8.25l-4.707 5.043M6.75 15.75 4.5 19.5m6-6 6.75 6.75M3.75 3.75l16.5 16.5" /></svg>
      </div>
      <div class="flex-1">
        <div class="flex items-baseline justify-between gap-2 flex-wrap">
          <span class="font-semibold text-gray-900 dark:text-gray-100">Bordcomputer</span>
          <span class="text-sm font-mono font-bold text-blue-700 dark:text-blue-400">~15 kWh/100km</span>
        </div>
        <p class="text-sm text-gray-500 dark:text-gray-400 mt-0.5">Was der Motor während der Fahrt gezogen hat. Standby-Verluste vor und nach der Fahrt sind hier <strong>nicht</strong> enthalten.</p>
      </div>
    </div>
  </div>

</div>

## Der entscheidende Punkt: Was EV Monitor nicht sieht

Zwischen Ladeende und Fahrtbeginn - und zwischen Fahrtende und dem nächsten Laden - vergeht Zeit. In dieser Zeit sinkt der SoC. EV Monitor hat keinen Zugriff auf den genauen SoC-Wert *beim Start der Fahrt*. Nur die zwei Messpunkte an den Ladeenden sind bekannt.

<div class="relative space-y-0 my-6">

  <div class="flex items-center gap-4">
    <div class="w-28 flex-shrink-0 text-right text-xs text-gray-500 dark:text-gray-400 leading-tight">Ladeende<br><span class="font-semibold text-gray-700 dark:text-gray-300">11.04.</span></div>
    <div class="flex-shrink-0 w-3 h-3 rounded-full bg-green-500 ring-2 ring-white dark:ring-gray-900 z-10"></div>
    <div class="flex-1">
      <div class="flex items-center gap-2">
        <div class="h-5 rounded bg-green-500" style="width: 87%"></div>
        <span class="text-sm font-bold text-green-700 dark:text-green-400 whitespace-nowrap">87% SoC</span>
      </div>
      <div class="text-xs text-green-700 dark:text-green-400 mt-0.5">EV Monitor kennt diesen Wert</div>
    </div>
  </div>

  <div class="flex items-stretch gap-4">
    <div class="w-28 flex-shrink-0"></div>
    <div class="flex-shrink-0 flex flex-col items-center"><div class="w-px flex-1 bg-orange-300 dark:bg-orange-700"></div></div>
    <div class="flex items-center py-2"><span class="text-xs text-orange-600 dark:text-orange-400 italic">Standby-Verlust (Dauer unbekannt)</span></div>
  </div>

  <div class="flex items-center gap-4">
    <div class="w-28 flex-shrink-0 text-right text-xs text-gray-500 dark:text-gray-400 leading-tight">Fahrtbeginn</div>
    <div class="flex-shrink-0 w-3 h-3 rounded-full bg-gray-300 dark:bg-gray-600 ring-2 ring-white dark:ring-gray-900 z-10"></div>
    <div class="flex-1">
      <div class="flex items-center gap-2">
        <div class="h-5 rounded bg-gray-300 dark:bg-gray-600" style="width: 82%"></div>
        <span class="text-sm font-bold text-gray-400 dark:text-gray-500 whitespace-nowrap">~82%?</span>
      </div>
      <div class="text-xs text-gray-400 dark:text-gray-500 mt-0.5">EV Monitor kennt diesen Wert <strong>nicht</strong></div>
    </div>
  </div>

  <div class="flex items-stretch gap-4">
    <div class="w-28 flex-shrink-0"></div>
    <div class="flex-shrink-0 flex flex-col items-center"><div class="w-px flex-1 bg-blue-400 dark:bg-blue-600"></div></div>
    <div class="flex items-center py-2"><span class="text-xs text-blue-600 dark:text-blue-400 italic">Fahrt 236 km - das sieht der Bordcomputer</span></div>
  </div>

  <div class="flex items-center gap-4">
    <div class="w-28 flex-shrink-0 text-right text-xs text-gray-500 dark:text-gray-400 leading-tight">Fahrtende</div>
    <div class="flex-shrink-0 w-3 h-3 rounded-full bg-gray-300 dark:bg-gray-600 ring-2 ring-white dark:ring-gray-900 z-10"></div>
    <div class="flex-1">
      <div class="flex items-center gap-2">
        <div class="h-5 rounded bg-gray-300 dark:bg-gray-600" style="width: 29%"></div>
        <span class="text-sm font-bold text-gray-400 dark:text-gray-500 whitespace-nowrap">~29%?</span>
      </div>
      <div class="text-xs text-gray-400 dark:text-gray-500 mt-0.5">EV Monitor kennt diesen Wert <strong>nicht</strong></div>
    </div>
  </div>

  <div class="flex items-stretch gap-4">
    <div class="w-28 flex-shrink-0"></div>
    <div class="flex-shrink-0 flex flex-col items-center"><div class="w-px flex-1 bg-orange-300 dark:bg-orange-700"></div></div>
    <div class="flex items-center py-2"><span class="text-xs text-orange-600 dark:text-orange-400 italic">Standby-Verlust (Dauer unbekannt)</span></div>
  </div>

  <div class="flex items-center gap-4">
    <div class="w-28 flex-shrink-0 text-right text-xs text-gray-500 dark:text-gray-400 leading-tight">Ladestart<br><span class="font-semibold text-gray-700 dark:text-gray-300">16.04.</span></div>
    <div class="flex-shrink-0 w-3 h-3 rounded-full bg-green-500 ring-2 ring-white dark:ring-gray-900 z-10"></div>
    <div class="flex-1">
      <div class="flex items-center gap-2">
        <div class="h-5 rounded bg-green-500" style="width: 26%"></div>
        <span class="text-sm font-bold text-green-700 dark:text-green-400 whitespace-nowrap">26% SoC</span>
      </div>
      <div class="text-xs text-green-700 dark:text-green-400 mt-0.5">EV Monitor kennt diesen Wert</div>
    </div>
  </div>

</div>

<div class="bg-orange-50 dark:bg-orange-900/20 border border-orange-200 dark:border-orange-800 rounded-sm p-4 my-4">
  <div class="flex gap-2 items-start">
    <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" class="w-5 h-5 text-orange-600 dark:text-orange-400 flex-shrink-0 mt-0.5"><path stroke-linecap="round" stroke-linejoin="round" d="m11.25 11.25.041-.02a.75.75 0 0 1 1.063.852l-.708 2.836a.75.75 0 0 0 1.063.853l.041-.021M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Zm-9-3.75h.008v.008H12V8.25Z" /></svg>
    <div class="text-sm text-orange-800 dark:text-orange-300"><strong>Was das bedeutet:</strong> EV Monitor sieht nur den Sprung von 87% auf 26% - also 61 Prozentpunkte. Ob davon 57% auf die Fahrt und 4% auf Standby entfallen, oder 59% auf die Fahrt und 2% auf Standby, ist von außen nicht messbar. Der gesamte Energiebedarf dieser Periode ist aber real - und EV Monitor bildet ihn vollständig ab.</div>
  </div>
</div>

## Wie EV Monitor rechnet

EV Monitor erfasst die Energie zwischen zwei Ladevorgängen - nicht nur während der Fahrt. Das schließt Standby-Verluste ein, die real verloren gehen und sich in der nächsten Stromrechnung wiederfinden.

```
Verbrauch = (kWh geladen + SoC-Korrektur) / Strecke × 100
```

<div class="bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-sm p-4 my-4">
  <div class="flex gap-2 items-start">
    <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" class="w-5 h-5 text-blue-600 dark:text-blue-400 flex-shrink-0 mt-0.5"><path stroke-linecap="round" stroke-linejoin="round" d="m11.25 11.25.041-.02a.75.75 0 0 1 1.063.852l-.708 2.836a.75.75 0 0 0 1.063.853l.041-.021M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Zm-9-3.75h.008v.008H12V8.25Z" /></svg>
    <div class="text-sm text-blue-800 dark:text-blue-300"><strong>Was ist die SoC-Korrektur?</strong><br>Du lädst nie von 0% auf 100%. Die Korrektur gleicht aus, wenn der Akku nach diesem Laden voller oder leerer ist als nach dem vorherigen Laden - damit wird nur der Verbrauch der tatsächlich gefahrenen Strecke berechnet, nicht mehr und nicht weniger.</div>
  </div>
</div>

## Warum EV Monitor höher liegt als der Bordcomputer

Das ist kein Fehler. Der Bordcomputer misst nur was der Antrieb während der Fahrt braucht. Energie, die das Fahrzeug im Stand verbraucht, erscheint im Bordcomputer nicht, ist aber real verloren gegangen und muss nachgeladen werden.

<div class="grid sm:grid-cols-2 gap-4 my-6">
  <div class="bg-gray-50 dark:bg-gray-700 rounded-sm p-4 border border-gray-200 dark:border-gray-600">
    <div class="text-2xl font-bold text-green-600 dark:text-green-400 mb-1">+1,75-2 kWh</div>
    <div class="text-sm text-gray-600 dark:text-gray-400">Durchschnittliche Differenz zwischen realem Verbrauch und Bordcomputer-Anzeige laut unabhängiger Forschung.</div>
  </div>
  <div class="bg-gray-50 dark:bg-gray-700 rounded-sm p-4 border border-gray-200 dark:border-gray-600">
    <div class="text-2xl font-bold text-orange-500 dark:text-orange-400 mb-1">1-3 kWh/Tag</div>
    <div class="text-sm text-gray-600 dark:text-gray-400">Typischer Standby-Verbrauch eines E-Autos im geparkten Zustand, je nach Fahrzeug, Temperatur und Software-Version.</div>
  </div>
</div>

## Die unvermeidbare Unschärfe

Keine Methode ist perfekt - das liegt in der Natur der Messung:

- **SoC-Werte** werden von EV Monitor als ganze Prozentzahl gespeichert. 1% entspricht je nach Akkugröße ca. 0,5-0,7 kWh - das ist eine messbare Ungenauigkeit.
- **kWh-Werte vom Fahrzeug** werden vom Fahrzeug-BMS oder der Hersteller-API gemeldet und von Diensten wie Smartcar unverändert weitergegeben. Die Präzision hängt vom Fahrzeughersteller ab.
- **Wallbox- und Ladestationsmessungen** haben herstellerabhängige Messtoleranzen.
- **Standby-Verluste** variieren stark je nach Außentemperatur, Software-Version und Nutzungsverhalten.

EV Monitor verwendet den Median über mehrere Ladevorgänge um Ausreißer herauszuglätten, und markiert einzelne Trips mit einem Plausibilitäts-Indikator wenn der berechnete Wert ungewöhnlich erscheint.
