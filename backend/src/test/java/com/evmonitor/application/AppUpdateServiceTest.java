package com.evmonitor.application;

import com.evmonitor.domain.AppBundle;
import com.evmonitor.domain.AppBundleRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests fuer AppUpdateService - die Entscheidung, ob die App ein neues
 * Web-Bundle (Capgo Live-Update) ziehen soll.
 *
 * Regel: Es wird nur das neueste veroeffentlichte Bundle zurueckgegeben, und
 * nur wenn seine Semver-Version groesser ist als die aktuell installierte.
 * Fresh-Installs melden eine Nicht-Semver-Version ("builtin") und sollen das
 * neueste Bundle erhalten.
 */
class AppUpdateServiceTest {

    private final AppBundleRepository repository = mock(AppBundleRepository.class);
    private final AppUpdateService service = new AppUpdateService(repository);

    private AppBundle bundle(String version) {
        return AppBundle.builder()
                .version(version)
                .checksum("sum-" + version)
                .filename(version + ".zip")
                .build();
    }

    @Test
    void noUpdate_whenNoBundlePublished() {
        when(repository.findTopByOrderByCreatedAtDesc()).thenReturn(Optional.empty());

        assertThat(service.findUpdateFor("1.0.0")).isEmpty();
    }

    @Test
    void update_whenLatestIsNewer() {
        when(repository.findTopByOrderByCreatedAtDesc()).thenReturn(Optional.of(bundle("1.0.5")));

        assertThat(service.findUpdateFor("1.0.4"))
                .map(AppBundle::getVersion)
                .contains("1.0.5");
    }

    @Test
    void noUpdate_whenLatestEqualsCurrent() {
        when(repository.findTopByOrderByCreatedAtDesc()).thenReturn(Optional.of(bundle("1.0.5")));

        assertThat(service.findUpdateFor("1.0.5")).isEmpty();
    }

    @Test
    void noUpdate_whenLatestIsOlderThanInstalled() {
        when(repository.findTopByOrderByCreatedAtDesc()).thenReturn(Optional.of(bundle("1.0.3")));

        assertThat(service.findUpdateFor("1.0.5")).isEmpty();
    }

    @Test
    void update_forFreshInstall_withNonSemverCurrentVersion() {
        when(repository.findTopByOrderByCreatedAtDesc()).thenReturn(Optional.of(bundle("1.0.0")));

        assertThat(service.findUpdateFor("builtin"))
                .map(AppBundle::getVersion)
                .contains("1.0.0");
    }

    @Test
    void update_respectsMajorAndMinor_notLexicographic() {
        when(repository.findTopByOrderByCreatedAtDesc()).thenReturn(Optional.of(bundle("1.0.10")));

        // lexikografisch waere "1.0.10" < "1.0.9" - numerisch korrekt ist es groesser
        assertThat(service.findUpdateFor("1.0.9"))
                .map(AppBundle::getVersion)
                .contains("1.0.10");
    }

    @Test
    void publish_savesBundle_withFilenameDerivedFromVersion() {
        when(repository.findByVersion("1.0.42")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AppBundle saved = service.publish("1.0.42", "sha256-deadbeef");

        assertThat(saved.getVersion()).isEqualTo("1.0.42");
        assertThat(saved.getChecksum()).isEqualTo("sha256-deadbeef");
        assertThat(saved.getFilename()).isEqualTo("1.0.42.zip");
    }

    @Test
    void publish_rejectsNonSemverVersion() {
        assertThatThrownBy(() -> service.publish("not-semver", "sum"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publish_rejectsDuplicateVersion() {
        when(repository.findByVersion("1.0.42")).thenReturn(Optional.of(bundle("1.0.42")));

        assertThatThrownBy(() -> service.publish("1.0.42", "sum"))
                .isInstanceOf(IllegalStateException.class);
    }
}
