package com.evmonitor.infrastructure.persistence;

import com.evmonitor.domain.SubscriptionTier;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Guards the {@code app_user_subscription_tier_check} constraint against enum drift.
 *
 * SUPPORTER was added to {@link SubscriptionTier} without a migration widening the CHECK
 * from V112, so the Stripe webhook confirming a Supporter purchase would have failed with
 * a constraint violation. Adding a tier in Java and forgetting the SQL is silent until a
 * real purchase hits production - this test makes it loud at build time.
 *
 * Deliberately parses the migration files instead of querying a database: the
 * Testcontainers-based schema tests are disabled on CI, so a DB-level assertion would not
 * actually run there.
 */
class SubscriptionTierConstraintTest {

    /** Matches the value list of any subscription_tier CHECK, across formatting variants. */
    private static final Pattern CHECK_LIST = Pattern.compile(
            "subscription_tier\\s+IN\\s*\\(([^)]*)\\)", Pattern.CASE_INSENSITIVE);

    private static final Pattern QUOTED_VALUE = Pattern.compile("'([^']*)'");

    /** Leading version number of a migration file, e.g. V112__foo.sql -> 112. */
    private static final Pattern VERSION = Pattern.compile("^V(\\d+)__");

    @Test
    void checkConstraintAllowsExactlyTheDeclaredTiers() throws IOException {
        String allowList = latestSubscriptionTierCheck();
        assertNotNull(allowList, "No subscription_tier CHECK constraint found in any migration");

        Set<String> allowed = new LinkedHashSet<>();
        Matcher m = QUOTED_VALUE.matcher(allowList);
        while (m.find()) allowed.add(m.group(1));

        Set<String> declared = Arrays.stream(SubscriptionTier.values())
                .map(Enum::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        assertEquals(declared, allowed,
                "SubscriptionTier and the app_user_subscription_tier_check constraint disagree. "
                        + "A new tier needs a migration widening the CHECK, otherwise writing it fails at runtime.");
    }

    /** Value list of the newest migration that (re)defines the constraint, or null if none does. */
    private String latestSubscriptionTierCheck() throws IOException {
        Resource[] migrations = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:db/migration/*.sql");

        return Arrays.stream(migrations)
                .sorted(Comparator.comparingInt(SubscriptionTierConstraintTest::versionOf))
                .map(SubscriptionTierConstraintTest::readQuietly)
                .map(CHECK_LIST::matcher)
                // A single file may drop and re-add - the last match in it wins.
                .map(matcher -> {
                    String last = null;
                    while (matcher.find()) last = matcher.group(1);
                    return last;
                })
                .filter(java.util.Objects::nonNull)
                .reduce((first, second) -> second)
                .orElse(null);
    }

    private static int versionOf(Resource resource) {
        String name = resource.getFilename() == null ? "" : resource.getFilename();
        Matcher m = VERSION.matcher(name);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private static String readQuietly(Resource resource) {
        try (var in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read migration " + resource.getFilename(), e);
        }
    }
}
