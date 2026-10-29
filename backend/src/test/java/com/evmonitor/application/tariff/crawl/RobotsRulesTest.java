package com.evmonitor.application.tariff.crawl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * robots.txt as Google reads it: our own group if one names us, else "*". Longest matching
 * rule wins, Allow wins a tie, "*" and "$" in paths. Bot lists aimed at AI crawlers do not
 * concern a price check that names itself.
 */
class RobotsRulesTest {

    private static final String AGENT = "EV-Monitor-Tarifcheck";

    @Test
    void noRobots_allowsEverything() {
        assertThat(RobotsRules.parse("", AGENT).allows("/tarife")).isTrue();
        assertThat(RobotsRules.parse(null, AGENT).allows("/tarife")).isTrue();
    }

    @Test
    void disallowAllForEveryone_blocks() {
        assertThat(RobotsRules.parse("User-agent: *\nDisallow: /\n", AGENT).allows("/tarife")).isFalse();
    }

    @Test
    void emptyDisallow_allows() {
        assertThat(RobotsRules.parse("User-agent: *\nDisallow:\n", AGENT).allows("/tarife")).isTrue();
    }

    @Test
    void wildcardRule_matchesOnlyWhatItSays() {
        RobotsRules aral = RobotsRules.parse("User-agent: *\nDisallow: \nDisallow:/*.html?*\n", AGENT);

        assertThat(aral.allows("/de/global/retail/pulse/tarife-bezahlmethoden.html")).isTrue();
        assertThat(aral.allows("/de/x.html?utm=1")).isFalse();
    }

    @Test
    void endAnchor_matchesOnlyTheEnd() {
        RobotsRules r = RobotsRules.parse("User-agent: *\nDisallow: /*.pdf$\n", AGENT);

        assertThat(r.allows("/preise.pdf")).isFalse();
        assertThat(r.allows("/preise.pdf.html")).isTrue();
    }

    @Test
    void groupNamingUs_replacesTheStarGroup() {
        String txt = "User-agent: *\nDisallow: /\n\nUser-agent: ev-monitor-tarifcheck\nAllow: /\n";

        assertThat(RobotsRules.parse(txt, AGENT).allows("/tarife")).isTrue();
    }

    @Test
    void groupForAiBots_doesNotConcernUs() {
        String enbw = "User-agent: GPTBot\nUser-agent: ClaudeBot\nDisallow: /\n\nUser-agent: *\nDisallow: /login\n";

        assertThat(RobotsRules.parse(enbw, AGENT).allows("/elektromobilitaet/produkte/ladetarife")).isTrue();
        assertThat(RobotsRules.parse(enbw, AGENT).allows("/login")).isFalse();
    }

    @Test
    void longestRuleWins_allowWinsATie() {
        RobotsRules r = RobotsRules.parse("User-agent: *\nDisallow: /e-mobilitaet\nAllow: /e-mobilitaet/autostrom\n", AGENT);

        assertThat(r.allows("/e-mobilitaet/autostrom")).isTrue();
        assertThat(r.allows("/e-mobilitaet/wallbox")).isFalse();
        assertThat(RobotsRules.parse("User-agent: *\nDisallow: /a\nAllow: /a\n", AGENT).allows("/a")).isTrue();
    }

    @Test
    void commentsAndCase_areIgnored() {
        RobotsRules r = RobotsRules.parse("# robots\nUSER-AGENT: *   # alle\ndisallow: /intern # nein\n", AGENT);

        assertThat(r.allows("/intern/x")).isFalse();
        assertThat(r.allows("/tarife")).isTrue();
    }
}
