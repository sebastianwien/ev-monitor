package com.evmonitor.testutil;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wächter: Unterklassen der Integrationstest-Basisklassen erzwingen keinen eigenen Spring-Context.
 *
 * <p>Jede Kombination aus eigenen Mock-Feldern oder Context-Annotationen in einer Unterklasse
 * erzeugt einen weiteren Spring-Context pro Testlauf (5-8 s je Context in CI). Mocks und Spies
 * gehören deshalb in {@link AbstractIntegrationTest} oder {@link AbstractPostgresIT}.
 * Ausnahmen stehen mit Begründung in {@link #EXCEPTIONS}.
 */
class TestContextGuardTest {

    private static final Set<Class<?>> BASE_CLASSES = Set.of(
            AbstractIntegrationTest.class, AbstractServiceTest.class, AbstractPostgresIT.class);

    private static final Set<String> FORBIDDEN_FIELD_ANNOTATIONS = Set.of(
            "org.springframework.test.context.bean.override.mockito.MockitoBean",
            "org.springframework.test.context.bean.override.mockito.MockitoSpyBean",
            "org.springframework.boot.test.mock.mockito.MockBean",
            "org.springframework.boot.test.mock.mockito.SpyBean");

    private static final Set<String> FORBIDDEN_CLASS_ANNOTATIONS = Set.of(
            "org.springframework.test.context.TestPropertySource",
            "org.springframework.test.annotation.DirtiesContext",
            "org.springframework.context.annotation.Import");

    /**
     * Klassen (voller Name, da Testklassen package-private sind), die bewusst einen eigenen
     * Context brauchen, je Eintrag ein Satz Begründung.
     */
    private static final Map<String, String> EXCEPTIONS = Map.of(
            "com.evmonitor.infrastructure.web.PublicApiTripRateLimitIntegrationTest",
            "@TestPropertySource schaltet das Rate-Limiting an, das im Test-Profil für alle anderen aus ist",
            "com.evmonitor.application.PriceSuggestionEfficiencyIntegrationTest",
            "@TestPropertySource setzt eigene Schwellenwerte für die Effizienzprüfung");

    @Test
    void unterklassenDerBasisklassenErzwingenKeinenEigenenContext() {
        List<String> violations = new ArrayList<>();
        for (Class<?> testClass : findSubclassesOfBaseClasses()) {
            if (EXCEPTIONS.containsKey(testClass.getName())) {
                continue;
            }
            collectViolations(testClass, violations);
        }
        assertThat(violations)
                .withFailMessage("Diese Testklassen erzwingen einen eigenen Spring-Context. "
                        + "Mock in die Basisklasse ziehen, siehe Javadoc AbstractIntegrationTest. "
                        + "Begründete Ausnahmen in TestContextGuardTest.EXCEPTIONS eintragen.\n  "
                        + String.join("\n  ", violations))
                .isEmpty();
    }

    @Test
    void ausnahmenSindWeiterhinUnterklassenMitEigenemContext() {
        for (String className : EXCEPTIONS.keySet()) {
            List<String> violations = new ArrayList<>();
            collectViolations(loadClass(className), violations);
            assertThat(violations)
                    .withFailMessage("%s steht in EXCEPTIONS, braucht aber keinen eigenen Context mehr. "
                            + "Eintrag entfernen.", className)
                    .isNotEmpty();
        }
    }

    private static List<Class<?>> findSubclassesOfBaseClasses() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        BASE_CLASSES.forEach(base -> scanner.addIncludeFilter(new AssignableTypeFilter(base)));
        List<Class<?>> result = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents("com.evmonitor")) {
            result.add(loadClass(definition.getBeanClassName()));
        }
        assertThat(result).as("Classpath-Scan findet die Integrationstests").hasSizeGreaterThan(50);
        return result;
    }

    private static Class<?> loadClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Testklasse nicht gefunden: " + name, e);
        }
    }

    /** Prüft die Klasse, ihre Oberklassen bis zur Basisklasse und ihre Nested-Klassen. */
    private static void collectViolations(Class<?> testClass, List<String> violations) {
        for (Class<?> c = testClass; c != null && !BASE_CLASSES.contains(c); c = c.getSuperclass()) {
            for (Annotation annotation : c.getDeclaredAnnotations()) {
                if (FORBIDDEN_CLASS_ANNOTATIONS.contains(annotation.annotationType().getName())) {
                    violations.add(c.getName() + ": @" + annotation.annotationType().getSimpleName());
                }
            }
            for (Field field : c.getDeclaredFields()) {
                for (Annotation annotation : field.getDeclaredAnnotations()) {
                    if (FORBIDDEN_FIELD_ANNOTATIONS.contains(annotation.annotationType().getName())) {
                        violations.add(c.getName() + "." + field.getName() + ": @"
                                + annotation.annotationType().getSimpleName()
                                + " " + field.getType().getSimpleName());
                    }
                }
            }
        }
        for (Class<?> nested : testClass.getDeclaredClasses()) {
            collectViolations(nested, violations);
        }
    }
}
