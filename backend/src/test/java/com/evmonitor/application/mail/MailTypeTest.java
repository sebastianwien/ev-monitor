package com.evmonitor.application.mail;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class MailTypeTest {

    /** Geht nur an den Admin (Wochenbericht), kein Nutzer-Mailing. */
    private static final Set<String> INTERNAL_TEMPLATES = Set.of("weekly-report.html");

    @Test
    void everyUserTemplate_hasExactlyOneCatalogEntry() throws Exception {
        Resource[] files = new PathMatchingResourcePatternResolver().getResources("classpath:email-templates/*.html");
        Set<String> templates = Arrays.stream(files).map(Resource::getFilename).filter(Objects::nonNull)
                .filter(f -> !INTERNAL_TEMPLATES.contains(f)).collect(Collectors.toSet());

        assertThat(Arrays.stream(MailType.values()).map(MailType::template))
                .doesNotHaveDuplicates()
                .containsExactlyInAnyOrderElementsOf(templates);
    }

    @Test
    void keys_areUniqueAndUsableAsUtmCampaign() {
        assertThat(Arrays.stream(MailType.values()).map(MailType::key)).doesNotHaveDuplicates()
                .allMatch(k -> k.matches("[a-z0-9-]+"));
    }

    @Test
    void requiredMails_haveNoGoal_andEveryEntryIsDocumented() {
        for (MailType t : MailType.values()) {
            if (t.kind() == MailType.Kind.PFLICHT) assertThat(t.goal()).as(t.key()).isNull();
            assertThat(t.title()).as(t.key()).isNotBlank();
            assertThat(t.purpose()).as(t.key()).isNotBlank();
            assertThat(t.trigger()).as(t.key()).isNotBlank();
            assertThat(t.audience()).as(t.key()).isNotBlank();
        }
    }

    @Test
    void fromKey_findsEntry_andRejectsUnknown() {
        assertThat(MailType.fromKey("re-engagement")).contains(MailType.RE_ENGAGEMENT);
        assertThat(MailType.fromKey("nope")).isEmpty();
        assertThat(MailType.fromKey(null)).isEmpty();
    }
}
