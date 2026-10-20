package com.evmonitor.application.codelogin;

/**
 * Where a passwordless sign-up came from (UTM, referrer host, first page) and which language its mails use.
 * All nullable. Landing path and referrer are raw browser values; the account service normalizes them.
 */
public record SignupAttribution(String utmSource, String utmMedium, String utmCampaign, String locale,
                                String referrerSource, String landingPath) {

    public static final SignupAttribution NONE = new SignupAttribution(null, null, null, null, null, null);

    public SignupAttribution(String utmSource, String utmMedium, String utmCampaign, String locale) {
        this(utmSource, utmMedium, utmCampaign, locale, null, null);
    }
}
