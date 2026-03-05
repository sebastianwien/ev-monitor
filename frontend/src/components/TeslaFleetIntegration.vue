<template>
  <div class="bg-white border border-gray-200 rounded-xl p-6 space-y-4">
    <div class="flex items-center gap-3">
      <div class="bg-gray-900 rounded-lg p-2">
        <svg class="h-5 w-5 text-white" viewBox="0 0 24 24" fill="currentColor">
          <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 14H9V8h2v8zm4 0h-2V8h2v8z"/>
        </svg>
      </div>
      <div>
        <h3 class="font-semibold text-gray-900">Tesla Import</h3>
        <p class="text-xs text-gray-500">Via offizieller Tesla Fleet API</p>
      </div>
      <span class="ml-auto text-xs bg-amber-100 text-amber-700 px-2 py-1 rounded-full font-medium">Kommt bald</span>
    </div>
    <p class="text-sm text-gray-600">
      Verbinde deinen Tesla Account über das offizielle OAuth2-Verfahren.
      Wir importieren dann deine bisherigen Supercharger-Sessions und prüfen täglich auf neue.
    </p>
    <div class="bg-blue-50 border border-blue-200 rounded-lg p-3 text-sm text-blue-800 space-y-1">
      <p class="font-medium">Was importiert wird:</p>
      <ul class="list-disc list-inside space-y-0.5 text-blue-700">
        <li>Supercharger-Sessions (historisch + zukünftig)</li>
        <li>Energie in kWh, Dauer, Standort</li>
        <li>Kosten (falls Supercharger mit Abrechnung)</li>
      </ul>
      <p class="text-xs text-blue-600 mt-2">Dieses Feature wird in Kürze als Premium-Feature verfügbar sein.</p>
    </div>
    <button
      disabled
      class="w-full flex items-center justify-center gap-2 bg-gray-300 text-gray-500 px-4 py-2.5 rounded-lg text-sm font-medium cursor-not-allowed"
    >
      Mit Tesla verbinden — demnächst verfügbar
    </button>
  </div>
</template>
