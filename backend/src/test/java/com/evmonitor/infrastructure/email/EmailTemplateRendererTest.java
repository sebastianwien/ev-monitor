package com.evmonitor.infrastructure.email;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EmailTemplateRendererTest {

    @Test
    void keepsEnabledSectionsAndDropsTheRest() {
        String template = "a<!--if:x-->X<!--end:x-->b<!--if:y-->Y\nY<!--end:y-->c";

        assertThat(EmailTemplateRenderer.applySections(template, Set.of("x"))).isEqualTo("aXbc");
        assertThat(EmailTemplateRenderer.applySections(template, Set.of("y"))).isEqualTo("abY\nYc");
        assertThat(EmailTemplateRenderer.applySections(template, Set.of())).isEqualTo("abc");
    }

    @Test
    void sectionsMayNest() {
        String template = "<!--if:outer-->o<!--if:inner-->i<!--end:inner-->O<!--end:outer-->";

        assertThat(EmailTemplateRenderer.applySections(template, Set.of("outer"))).isEqualTo("oO");
        assertThat(EmailTemplateRenderer.applySections(template, Set.of("outer", "inner"))).isEqualTo("oiO");
        assertThat(EmailTemplateRenderer.applySections(template, Set.of("inner"))).isEmpty();
    }
}
