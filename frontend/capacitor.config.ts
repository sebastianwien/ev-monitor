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
    // Verhindert, dass Inhalt unter Notch/Statusbar rutscht.
    contentInset: 'always',
  },
  plugins: {
    // API-Calls laufen ueber die native HTTP-Schicht statt durch den WebView.
    // Damit entfaellt CORS komplett (eine reine Browser-Regel) - die App
    // erreicht Prod- wie lokales Backend ohne allowed-origins-Anpassung.
    CapacitorHttp: {
      enabled: true,
    },
  },
};

export default config;
