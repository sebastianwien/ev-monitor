package com.evmonitor.application.codelogin;

/** Where a passwordless sign-up came from (UTM) and which language its mails use. All nullable. */
public record SignupAttribution(String utmSource, String utmMedium, String utmCampaign, String locale) {}
