package com.evmonitor.application.codelogin;

import com.evmonitor.application.seo.SeoPath;
import com.evmonitor.application.UsernameGenerator;
import com.evmonitor.domain.AuthProvider;
import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account for an address whose ownership was just proven (mail code, Google ID token).
 * New address: verified account without password, attribution from the gate.
 * Existing unverified account: someone may have registered the address with their own password
 * (pre-hijacking), so that password is dropped before the account is marked verified.
 */
@Service
@RequiredArgsConstructor
public class PasswordlessAccountService {

    private final UserRepository userRepository;

    @Transactional
    public User findOrCreateVerified(String email, SignupAttribution attribution, AuthProvider provider) {
        return userRepository.findByEmailIgnoreCase(email)
                .map(this::claimIfUnverified)
                .orElseGet(() -> create(email, attribution, provider));
    }

    private User claimIfUnverified(User user) {
        if (user.isEmailVerified()) return user;
        userRepository.updatePassword(user.getId(), null);
        userRepository.markEmailVerified(user.getId());
        return userRepository.findById(user.getId()).orElseThrow();
    }

    private User create(String email, SignupAttribution attribution, AuthProvider provider) {
        SignupAttribution a = attribution != null ? attribution : SignupAttribution.NONE;
        User user = User.newAccountBuilder(email, UsernameGenerator.fromEmail(email, userRepository::existsByUsername), null)
                .authProvider(provider)
                .emailVerified(true)
                .utmSource(a.utmSource()).utmMedium(a.utmMedium()).utmCampaign(a.utmCampaign())
                .referrerSource(a.referrerSource()).landingPath(SeoPath.landingPath(a.landingPath()))
                .registrationLocale(a.locale())
                .build();
        return userRepository.save(user);
    }
}
