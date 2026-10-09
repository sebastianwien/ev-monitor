package com.evmonitor.application.codelogin;

import com.evmonitor.domain.AuthProvider;
import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasswordlessAccountServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordlessAccountService service = new PasswordlessAccountService(userRepository);
    private static final SignupAttribution FIRSTEV = new SignupAttribution("firstev", "tile", "volkswagen_id_3", "de");

    @Test
    void newAddressGetsVerifiedAccountWithoutPasswordAndAttribution() {
        when(userRepository.findByEmail("neu@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User user = service.findOrCreateVerified("neu@example.com", FIRSTEV, AuthProvider.LOCAL);

        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getPasswordHash()).isNull();
        assertThat(user.getUsername()).isEqualTo("neu");
        assertThat(user.getUtmSource()).isEqualTo("firstev");
        assertThat(user.getUtmCampaign()).isEqualTo("volkswagen_id_3");
        assertThat(user.getRegistrationLocale()).isEqualTo("de");
        assertThat(user.getAuthProvider()).isEqualTo(AuthProvider.LOCAL);
    }

    @Test
    void verifiedAccountIsReturnedUntouched() {
        User existing = User.createVerifiedLocalUser("a@example.com", "a_user", "hash");
        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(existing));

        User user = service.findOrCreateVerified("a@example.com", FIRSTEV, AuthProvider.LOCAL);

        assertThat(user).isSameAs(existing);
        verify(userRepository, never()).updatePassword(any(), any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void unverifiedAccountLosesForeignPasswordAndBecomesVerified() {
        User pending = User.createNewLocalUser("a@example.com", "a_user", "attacker-hash");
        User claimed = pending.toBuilder().passwordHash(null).emailVerified(true).build();
        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(pending));
        when(userRepository.findById(pending.getId())).thenReturn(Optional.of(claimed));

        User user = service.findOrCreateVerified("a@example.com", FIRSTEV, AuthProvider.LOCAL);

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(userRepository).updatePassword(org.mockito.ArgumentMatchers.eq(pending.getId()), hash.capture());
        assertThat(hash.getValue()).isNull();
        verify(userRepository).markEmailVerified(pending.getId());
        assertThat(user.isEmailVerified()).isTrue();
    }
}
