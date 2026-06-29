import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  // Geteilte Capacitor-App-ID: muss fuer beide Plattformen gueltig sein
  // (Java-Package-Form, keine Bindestriche) - daher die Underscore-Form,
  // die zugleich die Android-applicationId ist.
  // Die iOS-Bundle-ID weicht ab (net.ev-monitor.app, Bindestrich) und wird
  // im Xcode-Projekt als PRODUCT_BUNDLE_IDENTIFIER gesetzt, da Apple keine
  // Unterstriche, Android keine Bindestriche erlaubt.
  appId: 'net.ev_monitor.app',
  appName: 'EV Monitor',
  webDir: 'dist',
  ios: {
    // WebView randlos (edge-to-edge): Inhalt darf unter Notch/Statusbar liegen.
    // 'always' rueckt den Inhalt zwar ein, zeigt ihn beim Scrollen aber im
    // eingerueckten Bereich oberhalb des Headers (Peek-through). Stattdessen volle
    // Flaeche + CSS env(safe-area-inset-*): die Header fuellen den Notch blickdicht.
    contentInset: 'never',
  },
  plugins: {
    // Capacitor-8 SystemBars steuert auf Android-Edge-to-Edge den Statusbar-Stil.
    // Deklarativ gesetzt = deterministisch (kein Startup-Race mit einem Runtime-setStyle).
    // 'LIGHT' = dunkle Icons fuer den hellen Navbar-Hintergrund (unser Light-Mode-Default);
    // Dark-Mode wird zur Laufzeit per useStatusBarTheme nachgezogen.
    SystemBars: {
      style: 'LIGHT',
    },
    // API-Calls laufen ueber die native HTTP-Schicht statt durch den WebView.
    // Damit entfaellt CORS komplett (eine reine Browser-Regel) - die App
    // erreicht Prod- wie lokales Backend ohne allowed-origins-Anpassung.
    CapacitorHttp: {
      enabled: true,
    },
    // Capgo Live-Updates, self-hosted. Der Updater fragt beim Start unseren
    // Endpoint nach einem neueren Web-Bundle. statsUrl leer = keine Geraete-Infos
    // an die Capgo-Cloud (voll self-hosted, DSGVO).
    CapacitorUpdater: {
      updateUrl: 'https://ev-monitor.net/api/app/updates',
      statsUrl: '',
      // Heruntergeladene Bundles erst beim naechsten Cold-Start anwenden, nicht
      // mitten in der Session beim Resume. Vermeidet einen ueberraschenden Reload
      // (Scrollposition/State weg) im Vordergrund. iOS wie Android gleich.
      autoUpdate: 'onLaunch',
    },
  },
};

export default config;
