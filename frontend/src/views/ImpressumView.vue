<script setup lang="ts">
import { BoltIcon } from '@heroicons/vue/24/outline'
</script>

<template>
  <div class="min-h-screen bg-white">
    <!-- Navbar -->
    <nav class="border-b border-gray-200">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex justify-between items-center h-16">
          <router-link to="/" class="flex items-center space-x-2">
            <BoltIcon class="h-8 w-8 text-green-600" />
            <span class="text-xl font-bold text-gray-900">EV Monitor</span>
          </router-link>
        </div>
      </div>
    </nav>

    <!-- Content -->
    <div class="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-16">
      <h1 class="text-4xl font-bold text-gray-900 mb-8">Impressum</h1>

      <div class="prose prose-lg text-gray-600 space-y-8">
        <section>
          <h2 class="text-2xl font-semibold text-gray-900 mb-4">Angaben gemäß § 5 TMG</h2>
          <p>
            Sebastian Wien<br />
            Cotheniusstrasse 5<br />
            10407 Berlin<br />
            Deutschland
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 mb-4">Kontakt</h2>
          <p>
            E-Mail: impressum@ev-monitor.net<br />
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 mb-4">Verantwortlich für den Inhalt nach § 55 Abs. 2 RStV</h2>
          <p>
            Sebastian Wien<br />
            Cotheniusstrasse 5
            10407 Berlin
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 mb-4">EU-Streitschlichtung</h2>
          <p>
            Die Europäische Kommission stellt eine Plattform zur Online-Streitbeilegung (OS) bereit:
            <a
              href="https://ec.europa.eu/consumers/odr/"
              target="_blank"
              class="text-green-600 hover:text-green-700"
            >
              https://ec.europa.eu/consumers/odr/
            </a>
          </p>
          <p class="mt-4">
            Unsere E-Mail-Adresse finden Sie oben im Impressum.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 mb-4">Verbraucherstreitbeilegung / Universalschlichtungsstelle</h2>
          <p>
            Wir sind nicht bereit oder verpflichtet, an Streitbeilegungsverfahren vor einer
            Verbraucherschlichtungsstelle teilzunehmen.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 mb-4">Haftung für Inhalte</h2>
          <p>
            Als Diensteanbieter sind wir gemäß § 7 Abs.1 TMG für eigene Inhalte auf diesen Seiten nach den
            allgemeinen Gesetzen verantwortlich. Nach §§ 8 bis 10 TMG sind wir als Diensteanbieter jedoch nicht
            verpflichtet, übermittelte oder gespeicherte fremde Informationen zu überwachen oder nach Umständen
            zu forschen, die auf eine rechtswidrige Tätigkeit hinweisen.
          </p>
          <p class="mt-4">
            Verpflichtungen zur Entfernung oder Sperrung der Nutzung von Informationen nach den allgemeinen
            Gesetzen bleiben hiervon unberührt. Eine diesbezügliche Haftung ist jedoch erst ab dem Zeitpunkt der
            Kenntnis einer konkreten Rechtsverletzung möglich. Bei Bekanntwerden von entsprechenden
            Rechtsverletzungen werden wir diese Inhalte umgehend entfernen.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 mb-4">Haftung für Links</h2>
          <p>
            Unser Angebot enthält Links zu externen Websites Dritter, auf deren Inhalte wir keinen Einfluss haben.
            Deshalb können wir für diese fremden Inhalte auch keine Gewähr übernehmen. Für die Inhalte der
            verlinkten Seiten ist stets der jeweilige Anbieter oder Betreiber der Seiten verantwortlich.
          </p>
          <p class="mt-4">
            Die verlinkten Seiten wurden zum Zeitpunkt der Verlinkung auf mögliche Rechtsverstöße überprüft.
            Rechtswidrige Inhalte waren zum Zeitpunkt der Verlinkung nicht erkennbar. Eine permanente inhaltliche
            Kontrolle der verlinkten Seiten ist jedoch ohne konkrete Anhaltspunkte einer Rechtsverletzung nicht
            zumutbar. Bei Bekanntwerden von Rechtsverletzungen werden wir derartige Links umgehend entfernen.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 mb-4">Urheberrecht</h2>
          <p>
            Die durch die Seitenbetreiber erstellten Inhalte und Werke auf diesen Seiten unterliegen dem
            deutschen Urheberrecht. Die Vervielfältigung, Bearbeitung, Verbreitung und jede Art der Verwertung
            außerhalb der Grenzen des Urheberrechtes bedürfen der schriftlichen Zustimmung des jeweiligen Autors
            bzw. Erstellers.
          </p>
          <p class="mt-4">
            Downloads und Kopien dieser Seite sind nur für den privaten, nicht kommerziellen Gebrauch gestattet.
            Soweit die Inhalte auf dieser Seite nicht vom Betreiber erstellt wurden, werden die Urheberrechte
            Dritter beachtet. Insbesondere werden Inhalte Dritter als solche gekennzeichnet. Sollten Sie
            trotzdem auf eine Urheberrechtsverletzung aufmerksam werden, bitten wir um einen entsprechenden
            Hinweis. Bei Bekanntwerden von Rechtsverletzungen werden wir derartige Inhalte umgehend entfernen.
          </p>
        </section>

        <section>
          <h2 class="text-2xl font-semibold text-gray-900 mb-4">Datenquellen</h2>
          <p>
            WLTP-Daten stammen aus öffentlich zugänglichen Herstellerangaben und Community-Beiträgen.
            Alle Angaben ohne Gewähr. Für die Richtigkeit der Daten übernehmen wir keine Haftung.
          </p>
        </section>
      </div>

      <div class="mt-12 pt-8 border-t border-gray-200">
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
