package net.ev_monitor.app;

import android.app.Application;
import android.util.Log;

import java.io.File;

/**
 * Loescht beim Prozessstart - VOR der ersten Activity und damit vor der WebView-
 * Initialisierung - eine evtl. noch vorhandene PWA-Service-Worker-Registrierung.
 *
 * Hintergrund: Frueher ausgelieferte Bundles haben einen Service-Worker registriert.
 * In der Android-WebView serviert der seinen alten, gecachten App-Stand und ueberdeckt
 * so neue Bundles (z.B. fehlt die Bottom-Nav). Die native App braucht NIE einen SW
 * (Assets gebundelt, Updates via Capgo).
 *
 * Bewusst in der Application (nicht in MainActivity.onCreate): Arbeit VOR
 * super.onCreate() der Activity stoert die Edge-to-Edge-WebView-Initialisierung und
 * laesst das Layout auf 0x0 kollabieren. Die Application.onCreate laeuft frueh genug
 * (vor dem WebView-Storage-Zugriff) und beruehrt die Activity nicht.
 *
 * Chirurgisch: nur das "Service Worker"-Verzeichnis (inkl. dessen CacheStorage);
 * localStorage (Login) bleibt erhalten. No-op, sobald nichts mehr da ist.
 */
public class EvMonitorApplication extends Application {

    private static final String TAG = "EvMonitor";

    @Override
    public void onCreate() {
        super.onCreate();
        deleteStaleServiceWorker();
    }

    private void deleteStaleServiceWorker() {
        try {
            String dataDir = getApplicationInfo().dataDir;
            // Chromium-Layout der WebView (Default-Profil; aeltere Layouts ohne "Default").
            String[] candidates = {
                    dataDir + "/app_webview/Default/Service Worker",
                    dataDir + "/app_webview/Service Worker",
            };
            for (String path : candidates) {
                File dir = new File(path);
                if (dir.exists()) {
                    deleteRecursive(dir);
                    Log.i(TAG, "Removed stale WebView service worker: " + path);
                }
            }
        } catch (Exception e) {
            // Best-effort: ein fehlgeschlagener Cleanup darf den App-Start nie blockieren.
            Log.w(TAG, "Service worker cleanup failed (non-critical)", e);
        }
    }

    private void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
