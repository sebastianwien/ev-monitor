package com.evmonitor.application;

import com.evmonitor.domain.AppBundle;
import com.evmonitor.domain.AppBundleRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Entscheidet, ob die native App ein neues Web-Bundle (Capgo Live-Update) ziehen soll.
 *
 * Es wird ausschliesslich roll-forward unterstuetzt: zurueckgegeben wird das neueste
 * veroeffentlichte Bundle, und nur wenn seine Semver-Version groesser ist als die
 * aktuell installierte. Ein Rollback erfolgt durch Veroeffentlichen einer hoeheren
 * Version mit dem alten Code.
 */
@Service
public class AppUpdateService {

    private final AppBundleRepository repository;

    public AppUpdateService(AppBundleRepository repository) {
        this.repository = repository;
    }

    /**
     * Wird vom Capgo-Client bei jedem App-Start aufgerufen, daher gecacht: das neueste
     * Bundle aendert sich nur beim Publish (dort wird der Cache geleert). Key = currentVersion.
     */
    @Cacheable("appUpdateCheck")
    @Transactional(readOnly = true)
    public Optional<AppBundle> findUpdateFor(String currentVersion) {
        return repository.findTopByOrderByCreatedAtDesc()
                .filter(latest -> isNewer(latest.getVersion(), currentVersion));
    }

    @Transactional(readOnly = true)
    public Optional<AppBundle> findByVersion(String version) {
        return repository.findByVersion(version);
    }

    /**
     * Registriert ein von der CI hochgeladenes Bundle. Der Dateiname wird serverseitig
     * aus der Version abgeleitet (kein vom Aufrufer kontrollierter Pfad).
     *
     * @throws IllegalArgumentException wenn die Version keine Semver-Version ist
     * @throws IllegalStateException    wenn die Version bereits existiert
     */
    @Transactional
    @CacheEvict(value = "appUpdateCheck", allEntries = true)
    public AppBundle publish(String version, String checksum) {
        if (parseSemver(version) == null) {
            throw new IllegalArgumentException("version must be semver: " + version);
        }
        if (repository.findByVersion(version).isPresent()) {
            throw new IllegalStateException("bundle already exists: " + version);
        }
        return repository.save(AppBundle.builder()
                .version(version)
                .checksum(checksum)
                .filename(version + ".zip")
                .build());
    }

    /**
     * Loescht Bundle-Zeilen aelter als {@code days} Tage, behaelt aber immer das neueste
     * (auch wenn es aelter ist - sonst gaebe es nach langer Pause kein Update mehr).
     * Die Zip-Dateien selbst rotiert die CI (Backend-Mount ist read-only).
     *
     * @return Anzahl geloeschter Zeilen
     */
    @Transactional
    public int purgeBundlesOlderThan(int days) {
        Optional<AppBundle> latest = repository.findTopByOrderByCreatedAtDesc();
        if (latest.isEmpty()) {
            return 0;
        }
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(days);
        return repository.deleteByCreatedAtBeforeAndIdNot(cutoff, latest.get().getId());
    }

    /**
     * True, wenn {@code candidate} eine hoehere Semver-Version ist als {@code current}.
     * Ist {@code current} keine parsebare Semver-Version (z.B. "builtin" bei Fresh-Installs),
     * gilt jedes echte Bundle als neuer.
     */
    static boolean isNewer(String candidate, String current) {
        int[] c = parseSemver(candidate);
        int[] cur = parseSemver(current);
        if (c == null) {
            return false;
        }
        if (cur == null) {
            return true;
        }
        for (int i = 0; i < 3; i++) {
            if (c[i] != cur[i]) {
                return c[i] > cur[i];
            }
        }
        return false;
    }

    /** Parst "major.minor.patch" zu drei Ints. Pre-Release-/Build-Suffixe werden ignoriert. */
    private static int[] parseSemver(String version) {
        if (version == null || version.isBlank()) {
            return null;
        }
        String core = version.split("[-+]", 2)[0];
        String[] parts = core.split("\\.");
        if (parts.length != 3) {
            return null;
        }
        int[] out = new int[3];
        for (int i = 0; i < 3; i++) {
            try {
                out[i] = Integer.parseInt(parts[i].trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return out;
    }
}
