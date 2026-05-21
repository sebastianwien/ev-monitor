---
title: "Genauerer Verbrauch dank modellspezifischer Ladeverluste"
description: "Statt pauschaler 5% DC und 10% AC berechnet EV Monitor den Ladeverlust jetzt pro Fahrzeugmodell aus echten Community-Ladevorgängen."
slug: "modellspezifische-ladeverluste"
date: "2026-05-21"
author: "Sebastian"
---

Wenn du ein EV lädst, kommt nie die volle Energie aus der Steckdose in der Batterie an. Ein Teil geht als Wärme verloren - im Onboard-Charger (AC), in der Thermoregelung, im BMS. Dieser Ladeverlust ist modellabhängig: ein BMW i3 mit kleinem AC-Charger verhält sich anders als ein VW ID.3 an der DC-Schnellladesäule.

Bisher hat EV Monitor pauschal 5% Verlust bei DC und 10% bei AC angenommen. Das ist eine sinnvolle Schätzung - aber eben eine Schätzung.

## Wie es jetzt funktioniert

Ab sofort berechnet EV Monitor den Ladeverlust pro Fahrzeugmodell aus echten Ladevorgängen der Community. Konkret: Ladevorgänge mit einem SoC-Hub von mindestens 75% - also von fast leer auf fast voll - werden ausgewertet. Dabei wird die tatsächlich geladene Energie (Netzseite) mit dem SoC-Delta mal Netto-Batteriekapazität verglichen. Der Median dieser Verhältnisse ergibt den modellspezifischen Ladeverlust, getrennt für AC und DC.

Die berechneten Werte gelten pro Fahrzeugkonfiguration - also für alle Autos mit derselben Modell- und Akkuvariante. Ein VW ID.3 Pro Performance profitiert von den Ladevorgängen aller anderen ID.3 Pro Performance in der Community, nicht nur vom eigenen.

Sobald mindestens 5 qualifizierte Ladevorgänge pro Modell und Ladetyp vorliegen, ersetzt der gemessene Wert die Pauschale. Für alle anderen greift weiterhin der globale Standardwert.

## Was das bedeutet

Dein berechneter Verbrauch spiegelt jetzt besser wider, was dein Fahrzeug wirklich verbraucht - und wird mit jedem neuen Ladelog der Community ein Stück genauer.
