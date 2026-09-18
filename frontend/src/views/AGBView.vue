<script setup lang="ts">
import { computed } from 'vue'
import { BoltIcon } from '@heroicons/vue/24/outline'

const email = computed(() => ['impressum', 'ev-monitor.net'].join('@'))
</script>

<template>
  <div class="min-h-screen bg-white dark:bg-gray-900">
    <!-- Navbar -->
    <nav class="border-b border-gray-200 dark:border-gray-700">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex justify-between items-center h-16">
          <router-link to="/" class="flex items-center space-x-2">
            <BoltIcon class="h-8 w-8 text-green-600" />
            <span class="text-xl font-bold text-gray-900 dark:text-gray-100">EV Monitor</span>
          </router-link>
          <router-link to="/modelle" class="text-sm font-medium text-gray-600 dark:text-gray-400 hover:text-green-700 transition">
            Modelle
          </router-link>
        </div>
      </div>
    </nav>

    <!-- Content -->
    <div class="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-16">
      <h1 class="text-4xl font-bold text-gray-900 dark:text-gray-100 mb-8">Allgemeine Geschäftsbedingungen (AGB)</h1>

      <div class="prose prose-lg text-gray-600 dark:text-gray-400 space-y-8">
        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">1. Geltungsbereich und Vertragspartner</h2>
          <p>
            Diese Allgemeinen Geschäftsbedingungen (AGB) gelten für die Nutzung der Plattform "EV Monitor"
            (Website ev-monitor.net sowie iOS- und Android-App, im Folgenden "Plattform" oder "Service").
          </p>
          <p class="mt-4">
            Vertragspartner ist Sebastian Wien, Cotheniusstraße 5, 10407 Berlin, Deutschland
            (im Folgenden "wir"). Kontakt: <a :href="`mailto:${email}`" class="text-green-600 hover:text-green-700">{{ email }}</a>.
          </p>
          <p class="mt-4">
            Das Angebot richtet sich an Verbraucher. Sie müssen mindestens 18 Jahre alt sein, um kostenpflichtige
            Funktionen zu buchen.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">2. Leistungsumfang</h2>
          <p>EV Monitor bietet folgende Leistungen:</p>
          <ul class="list-disc list-inside space-y-2">
            <li>Erfassung von Elektroauto-Ladevorgängen und Fahrten</li>
            <li>Statistische Auswertungen Ihrer Daten</li>
            <li>Vergleich mit WLTP-Herstellerangaben</li>
            <li>Zugriff auf anonymisierte Community-Statistiken</li>
            <li>Optional: automatischer Import über Fahrzeug- und Datenintegrationen (z. B. Tesla, Smartcar, VW Group, XPeng-Datenexport, Spritmonitor, go-e Wallbox)</li>
          </ul>
          <p class="mt-4">
            Der Kernfunktionsumfang ist <strong>kostenlos</strong>. Einzelne Premium-Funktionen sind
            kostenpflichtig (Abschnitt 4). Integrationen hängen von den Schnittstellen der jeweiligen Anbieter ab;
            wir schulden keine bestimmte Verfügbarkeit dieser Fremdschnittstellen.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">3. Registrierung und Nutzerkonto</h2>

          <h3 class="text-xl font-semibold text-gray-900 dark:text-gray-100 mt-6 mb-3">3.1 Vertragsschluss</h3>
          <p>
            Zur Nutzung ist eine Registrierung mit E-Mail-Adresse und Passwort oder über den Google-Login
            erforderlich. Mit Abschluss der Registrierung und Bestätigung der E-Mail-Adresse kommt der
            kostenlose Nutzungsvertrag zustande. Vertragssprache ist Deutsch. Diese AGB sind jederzeit unter
            ev-monitor.net/agb abrufbar und können gespeichert oder ausgedruckt werden; der Vertragstext wird
            von uns nicht darüber hinaus gespeichert.
          </p>

          <h3 class="text-xl font-semibold text-gray-900 dark:text-gray-100 mt-6 mb-3">3.2 Pflichten des Nutzers</h3>
          <ul class="list-disc list-inside space-y-2">
            <li>Sie machen bei der Registrierung wahrheitsgemäße Angaben</li>
            <li>Sie halten Ihr Passwort geheim und geben Ihr Konto nicht an Dritte weiter</li>
            <li>Sie haften für Aktivitäten unter Ihrem Konto, soweit Sie diese zu vertreten haben</li>
            <li>Bei Verdacht auf unbefugten Zugriff informieren Sie uns umgehend</li>
          </ul>

          <h3 class="text-xl font-semibold text-gray-900 dark:text-gray-100 mt-6 mb-3">3.3 Kontolöschung</h3>
          <p>
            Sie können Ihr Konto jederzeit in den Einstellungen löschen. Dabei werden Ihre Kontodaten und alle
            Daten, die Sie identifizieren, gelöscht. Erfasste Lade- und Fahrtwerte werden anonymisiert und ohne
            Bezug zu Ihrer Person für Community-Statistiken weiterverwendet. Daten, für die gesetzliche
            Aufbewahrungspflichten bestehen (z. B. Rechnungsdaten), werden bis zum Ablauf dieser Fristen
            aufbewahrt. Einzelheiten regelt die Datenschutzerklärung.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">4. Kostenpflichtige Funktionen (Premium)</h2>

          <h3 class="text-xl font-semibold text-gray-900 dark:text-gray-100 mt-6 mb-3">4.1 Bestellung und Preise</h3>
          <p>
            Premium-Funktionen werden als Abonnement mit monatlicher oder jährlicher Laufzeit angeboten. Preise,
            Laufzeit und Leistungsumfang werden vor der Bestellung angezeigt. Alle Preise verstehen sich als
            Endpreise inklusive gesetzlicher Umsatzsteuer, soweit diese anfällt. Der Vertrag über das
            Abonnement kommt zustande, wenn Sie den Bestellvorgang mit der Schaltfläche "Zahlungspflichtig
            bestellen" (oder einer gleichwertigen Bezeichnung) abschließen. Die Zahlungsabwicklung erfolgt über
            unseren Zahlungsdienstleister Stripe.
          </p>

          <h3 class="text-xl font-semibold text-gray-900 dark:text-gray-100 mt-6 mb-3">4.2 Testphase</h3>
          <p>
            Bei der ersten Buchung eines Abonnements gewähren wir eine kostenlose Testphase von 7 Tagen. Nach
            Ablauf der Testphase geht das Abonnement automatisch in ein kostenpflichtiges Abonnement über, sofern
            Sie nicht vorher kündigen. Die Testphase wird nur einmal pro Konto gewährt.
          </p>

          <h3 class="text-xl font-semibold text-gray-900 dark:text-gray-100 mt-6 mb-3">4.3 Laufzeit, Verlängerung und Kündigung</h3>
          <p>
            Das Abonnement verlängert sich automatisch um die jeweils gebuchte Laufzeit (ein Monat bzw. ein Jahr),
            wenn es nicht bis zum Ende der laufenden Laufzeit gekündigt wird. Sie können jederzeit zum Ende der
            laufenden Laufzeit kündigen, über die Schaltfläche "Abonnement kündigen" in den Einstellungen oder
            per E-Mail an <a :href="`mailto:${email}`" class="text-green-600 hover:text-green-700">{{ email }}</a>.
            Bis zum Ende der Laufzeit bleiben die Premium-Funktionen nutzbar. Bereits gezahlte Beträge werden
            für die restliche Laufzeit nicht erstattet, ausgenommen im Fall des Widerrufs (Abschnitt 5) oder einer
            von uns zu vertretenden Kündigung (Abschnitt 4.5).
          </p>

          <h3 class="text-xl font-semibold text-gray-900 dark:text-gray-100 mt-6 mb-3">4.4 Preisänderungen</h3>
          <p>
            Preisänderungen kündigen wir mindestens sechs Wochen vor Wirksamwerden per E-Mail an. Sie gelten erst
            für die nächste Verlängerung. Stimmen Sie einer Preisänderung nicht zu, können Sie bis zum
            Wirksamwerden kündigen; darauf weisen wir in der Ankündigung hin.
          </p>

          <h3 class="text-xl font-semibold text-gray-900 dark:text-gray-100 mt-6 mb-3">4.5 Kündigung durch uns</h3>
          <p>
            Wir können ein laufendes Abonnement nur aus wichtigem Grund fristlos kündigen, insbesondere bei
            schwerwiegenden Verstößen gegen Abschnitt 6. Stellen wir eine Premium-Funktion ein oder kündigen wir
            ohne wichtigen Grund, erstatten wir bereits gezahlte Beträge anteilig für die nicht genutzte Laufzeit.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">5. Widerrufsrecht für Verbraucher</h2>

          <h3 class="text-xl font-semibold text-gray-900 dark:text-gray-100 mt-6 mb-3">Widerrufsbelehrung</h3>
          <p>
            <strong>Widerrufsrecht.</strong> Sie haben das Recht, binnen vierzehn Tagen ohne Angabe von Gründen
            diesen Vertrag zu widerrufen. Die Widerrufsfrist beträgt vierzehn Tage ab dem Tag des Vertragsschlusses.
            Um Ihr Widerrufsrecht auszuüben, müssen Sie uns (Sebastian Wien, Cotheniusstraße 5, 10407 Berlin,
            E-Mail: <a :href="`mailto:${email}`" class="text-green-600 hover:text-green-700">{{ email }}</a>)
            mittels einer eindeutigen Erklärung (z. B. eine E-Mail) über Ihren Entschluss, diesen Vertrag zu
            widerrufen, informieren. Sie können dafür das unten stehende Muster-Widerrufsformular verwenden, das
            jedoch nicht vorgeschrieben ist. Zur Wahrung der Widerrufsfrist reicht es aus, dass Sie die Mitteilung
            über die Ausübung des Widerrufsrechts vor Ablauf der Widerrufsfrist absenden.
          </p>
          <p class="mt-4">
            <strong>Folgen des Widerrufs.</strong> Wenn Sie diesen Vertrag widerrufen, haben wir Ihnen alle
            Zahlungen, die wir von Ihnen erhalten haben, unverzüglich und spätestens binnen vierzehn Tagen ab dem
            Tag zurückzuzahlen, an dem die Mitteilung über Ihren Widerruf dieses Vertrags bei uns eingegangen ist.
            Für diese Rückzahlung verwenden wir dasselbe Zahlungsmittel, das Sie bei der ursprünglichen
            Transaktion eingesetzt haben, es sei denn, mit Ihnen wurde ausdrücklich etwas anderes vereinbart; in
            keinem Fall werden Ihnen wegen dieser Rückzahlung Entgelte berechnet. Haben Sie verlangt, dass die
            Dienstleistung während der Widerrufsfrist beginnen soll, so haben Sie uns einen angemessenen Betrag zu
            zahlen, der dem Anteil der bis zu dem Zeitpunkt, zu dem Sie uns von der Ausübung des Widerrufsrechts
            hinsichtlich dieses Vertrags unterrichten, bereits erbrachten Dienstleistungen im Vergleich zum
            Gesamtumfang der im Vertrag vorgesehenen Dienstleistungen entspricht.
          </p>

          <h3 class="text-xl font-semibold text-gray-900 dark:text-gray-100 mt-6 mb-3">Muster-Widerrufsformular</h3>
          <p>(Wenn Sie den Vertrag widerrufen wollen, dann füllen Sie bitte dieses Formular aus und senden Sie es zurück.)</p>
          <ul class="list-disc list-inside space-y-2 mt-2">
            <li>An: Sebastian Wien, Cotheniusstraße 5, 10407 Berlin, E-Mail: {{ email }}</li>
            <li>Hiermit widerrufe(n) ich/wir (*) den von mir/uns (*) abgeschlossenen Vertrag über die Erbringung der folgenden Dienstleistung (*)</li>
            <li>Bestellt am (*)</li>
            <li>Name des/der Verbraucher(s)</li>
            <li>Anschrift des/der Verbraucher(s)</li>
            <li>Unterschrift des/der Verbraucher(s) (nur bei Mitteilung auf Papier)</li>
            <li>Datum</li>
          </ul>
          <p class="mt-2 text-sm">(*) Unzutreffendes streichen.</p>

          <h3 class="text-xl font-semibold text-gray-900 dark:text-gray-100 mt-6 mb-3">Hinweis zur Testphase</h3>
          <p>
            Das Widerrufsrecht besteht unabhängig von der kostenlosen Testphase. Während der Testphase können Sie
            das Abonnement zudem jederzeit kostenfrei kündigen.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">6. Nutzungsregeln</h2>

          <h3 class="text-xl font-semibold text-gray-900 dark:text-gray-100 mt-6 mb-3">6.1 Erlaubte Nutzung</h3>
          <p>
            Der Service ist für die private Nutzung bestimmt, einschließlich der Nutzung für dienstlich
            überlassene Fahrzeuge durch die jeweilige Fahrerin oder den jeweiligen Fahrer. Eine gewerbliche
            Weiterverwertung der Plattform oder ihrer Daten ist nur mit unserer schriftlichen Zustimmung erlaubt.
          </p>

          <h3 class="text-xl font-semibold text-gray-900 dark:text-gray-100 mt-6 mb-3">6.2 Verbotene Nutzung</h3>
          <p>Folgende Handlungen sind untersagt:</p>
          <ul class="list-disc list-inside space-y-2">
            <li>Eingabe bewusst falscher oder irreführender Daten, insbesondere zur Manipulation von Statistiken</li>
            <li>Automatisiertes Auslesen (Scraping) der Plattform</li>
            <li>Umgehung von Zugriffsbeschränkungen oder Eingriffe in die Sicherheit der Plattform</li>
            <li>Überlastung der Server, etwa durch DoS-Angriffe</li>
            <li>Verbreitung von Malware oder schädlichem Code</li>
            <li>Belästigung anderer Nutzer</li>
          </ul>
          <p class="mt-4">
            Der Quellcode von EV Monitor ist als Open Source unter der GNU AGPL v3 veröffentlicht. Die Nutzung des
            Quellcodes richtet sich nach dieser Lizenz und wird durch diese AGB nicht eingeschränkt.
          </p>

          <h3 class="text-xl font-semibold text-gray-900 dark:text-gray-100 mt-6 mb-3">6.3 Community-Daten</h3>
          <p>
            Wenn Sie Daten teilen (z. B. Ladevorgänge, Fahrzeugangaben, Ladekurven), verpflichten Sie sich zu
            wahrheitsgemäßen Angaben. Bewusst falsche Daten können zur Sperrung Ihres Kontos führen.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">7. Haftung und Gewährleistung</h2>

          <h3 class="text-xl font-semibold text-gray-900 dark:text-gray-100 mt-6 mb-3">7.1 Verfügbarkeit</h3>
          <p>
            Wir bemühen uns um hohe Verfügbarkeit des Services. Für den kostenlosen Funktionsumfang wird keine
            bestimmte Verfügbarkeit geschuldet. Wartungsarbeiten werden nach Möglichkeit angekündigt.
          </p>

          <h3 class="text-xl font-semibold text-gray-900 dark:text-gray-100 mt-6 mb-3">7.2 Datenrichtigkeit</h3>
          <p>
            Statistiken, Verbrauchswerte und WLTP-Vergleiche basieren auf Nutzereingaben, importierten
            Fahrzeugdaten und Herstellerangaben. Sie dienen der Information und ersetzen keine Beratung.
            Wir übernehmen keine Gewähr für Richtigkeit, Vollständigkeit oder Aktualität dieser Daten.
          </p>

          <h3 class="text-xl font-semibold text-gray-900 dark:text-gray-100 mt-6 mb-3">7.3 Haftungsbeschränkung</h3>
          <p>
            Wir haften unbeschränkt bei Vorsatz und grober Fahrlässigkeit, bei Verletzung von Leben, Körper oder
            Gesundheit, nach dem Produkthaftungsgesetz sowie im Umfang einer übernommenen Garantie.
          </p>
          <p class="mt-4">
            Bei leicht fahrlässiger Verletzung einer wesentlichen Vertragspflicht (einer Pflicht, deren Erfüllung
            die ordnungsgemäße Durchführung des Vertrags überhaupt erst ermöglicht und auf deren Einhaltung Sie
            regelmäßig vertrauen dürfen) ist unsere Haftung auf den vertragstypischen, vorhersehbaren Schaden
            begrenzt. Im Übrigen ist die Haftung für leichte Fahrlässigkeit ausgeschlossen.
          </p>

          <h3 class="text-xl font-semibold text-gray-900 dark:text-gray-100 mt-6 mb-3">7.4 Fahrzeug-Integrationen</h3>
          <p>
            Integrationen (z. B. Tesla Fleet API, Smartcar, VW Group, Spritmonitor, go-e) nutzen Schnittstellen
            der jeweiligen Anbieter. Im Rahmen von Abschnitt 7.3 übernehmen wir keine Haftung für Änderungen,
            Einschränkungen oder Abschaltungen dieser Fremdschnittstellen, für fehlerhafte Datenübertragung
            durch den Anbieter oder für Zugriffe aufgrund von Ihnen kompromittierter Zugangsdaten. Zugangstoken
            werden verschlüsselt gespeichert; Sie können jede Verbindung jederzeit trennen.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">8. Rechte an Inhalten und Daten</h2>
          <p>
            Design, Texte und Markenzeichen der Plattform sind urheber- bzw. kennzeichenrechtlich geschützt. Der
            Quellcode steht unter der GNU AGPL v3 (Abschnitt 6.2).
          </p>
          <p class="mt-4">
            <strong>Ihre Daten:</strong> Sie behalten alle Rechte an Ihren eingegebenen Daten. Sie räumen uns
            das einfache, zeitlich unbeschränkte Recht ein, diese Daten in anonymisierter Form für
            Community-Statistiken und zur Verbesserung des Dienstes zu nutzen. Dieses Recht bleibt auch nach
            Löschung Ihres Kontos für die bereits anonymisierten Werte bestehen.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">9. Nutzung durch Dritte und öffentliche API</h2>

          <h3 class="text-xl font-semibold text-gray-900 dark:text-gray-100 mt-6 mb-3">9.1 Community-Statistiken</h3>
          <p>
            Die auf EV Monitor veröffentlichten Community-Statistiken (z. B. Realverbrauch, Reichweite,
            Ladekosten) beruhen auf anonymisierten Beiträgen registrierter Nutzer. Die Sammlung ist als
            Datenbank nach §§ 87a ff. UrhG geschützt.
          </p>

          <h3 class="text-xl font-semibold text-gray-900 dark:text-gray-100 mt-6 mb-3">9.2 Untersagte Nutzung durch Dritte</h3>
          <p>Ohne unsere ausdrückliche schriftliche Genehmigung ist es untersagt:</p>
          <ul class="list-disc list-inside space-y-2">
            <li>Inhalte, Statistiken oder Daten automatisiert auszulesen (Scraping, Crawling)</li>
            <li>wesentliche Teile der Daten auf eigenen Webseiten oder in Produkten einzubetten oder wiederzuverwenden</li>
            <li>Community-Daten für kommerzielle Zwecke zu nutzen oder weiterzuverbreiten</li>
          </ul>
          <p class="mt-4">
            Anfragen zur Nutzung richten Sie an die im Impressum genannte Kontaktadresse. Eine Genehmigung kann
            an Bedingungen geknüpft werden, insbesondere an eine Attribution mit Verlinkung auf die jeweilige
            Quellseite auf ev-monitor.net.
          </p>

          <h3 class="text-xl font-semibold text-gray-900 dark:text-gray-100 mt-6 mb-3">9.3 Öffentliche API</h3>
          <p>
            EV Monitor stellt eine Upload-API bereit. Ihre Nutzung ist ausschließlich zum Übermitteln eigener
            Ladedaten durch den jeweiligen API-Key-Inhaber erlaubt. Massenabfragen oder die automatisierte
            Auswertung fremder Daten über die API sind nicht gestattet.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">10. Änderungen der AGB</h2>
          <p>
            Wir können diese AGB ändern, soweit dies aus triftigem Grund erforderlich ist, insbesondere wegen
            geänderter Rechtslage oder Rechtsprechung, technischer Weiterentwicklung oder der Einführung neuer
            Funktionen. Änderungen, die das Verhältnis von Leistung und Gegenleistung zu Ihrem Nachteil
            verschieben oder wesentliche Pflichten begründen, bedürfen Ihrer ausdrücklichen Zustimmung.
          </p>
          <p class="mt-4">
            Sonstige Änderungen kündigen wir mindestens sechs Wochen vor Wirksamwerden per E-Mail an und weisen
            dabei auf Ihr Widerspruchsrecht und die Folgen des Schweigens hin. Widersprechen Sie nicht bis zum
            Wirksamwerden, gelten die geänderten AGB als angenommen. Widersprechen Sie, endet das
            Nutzungsverhältnis zum Zeitpunkt des Wirksamwerdens; ein laufendes Abonnement wird anteilig erstattet.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">11. Beendigung des Nutzungsverhältnisses</h2>
          <p>
            Der kostenlose Nutzungsvertrag kann von beiden Seiten jederzeit ohne Frist beendet werden, von Ihnen
            durch Löschung des Kontos. Bei Verstößen gegen Abschnitt 6 können wir Konten sperren oder löschen.
            Für Abonnements gilt Abschnitt 4.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">12. Streitbeilegung</h2>
          <p>
            Wir sind weder verpflichtet noch bereit, an Streitbeilegungsverfahren vor einer
            Verbraucherschlichtungsstelle teilzunehmen.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-4">13. Schlussbestimmungen</h2>
          <p>
            Es gilt deutsches Recht unter Ausschluss des UN-Kaufrechts (CISG). Zwingende
            Verbraucherschutzvorschriften des Staates, in dem Sie Ihren gewöhnlichen Aufenthalt haben, bleiben
            hiervon unberührt.
          </p>
          <p class="mt-4">
            Sollten einzelne Bestimmungen dieser AGB unwirksam sein, bleibt die Wirksamkeit der übrigen
            Bestimmungen unberührt.
          </p>
        </section>

        <section class="mt-8 pt-6 border-t border-gray-200 dark:border-gray-700">
          <p class="text-sm text-gray-500">
            Stand: September 2026
          </p>
        </section>
      </div>

      <div class="mt-12 pt-8 border-t border-gray-200 dark:border-gray-700">
        <router-link
          to="/"
          class="text-green-600 hover:text-green-700 font-medium"
        >
          ← Zurück zur Startseite
        </router-link>
      </div>
    </div>
  </div>
</template>
