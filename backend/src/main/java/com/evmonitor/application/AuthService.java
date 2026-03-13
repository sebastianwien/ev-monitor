package com.evmonitor.application;

import com.evmonitor.domain.EmailVerificationToken;
import com.evmonitor.domain.EmailVerificationTokenRepository;
import com.evmonitor.domain.PasswordResetToken;
import com.evmonitor.domain.PasswordResetTokenRepository;
import com.evmonitor.domain.User;
import com.evmonitor.domain.UserRepository;
import com.evmonitor.infrastructure.email.EmailService;
import com.evmonitor.infrastructure.security.JwtService;
import com.evmonitor.infrastructure.security.UserPrincipal;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AuthService {

    private static final int MAX_REFERRALS = 20;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final EmailVerificationTokenRepository tokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailService emailService;
    private final CoinLogService coinLogService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuthenticationManager authenticationManager,
            EmailVerificationTokenRepository tokenRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            EmailService emailService,
            CoinLogService coinLogService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.tokenRepository = tokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.emailService = emailService;
        this.coinLogService = coinLogService;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email is already in use.");
        }

        String username = resolveUsername(request.username(), request.email());

        String encodedPassword = passwordEncoder.encode(request.password());

        // Check if user has referral code
        java.util.UUID referrerId = null;
        if (request.referralCode() != null && !request.referralCode().isBlank()) {
            User referrer = userRepository.findByReferralCode(request.referralCode().toUpperCase())
                    .filter(r -> userRepository.countVerifiedReferrals(r.getId()) < MAX_REFERRALS)
                    .orElse(null);
            if (referrer != null) {
                referrerId = referrer.getId();
            }
        }

        // Check if user has campaign tracking data (utm_*)
        boolean hasCampaignData = (request.utmSource() != null && !request.utmSource().isBlank())
                               || (request.utmMedium() != null && !request.utmMedium().isBlank())
                               || (request.utmCampaign() != null && !request.utmCampaign().isBlank());

        User user;
        if (hasCampaignData) {
            user = User.createNewLocalUserWithCampaign(
                    request.email(),
                    username,
                    encodedPassword,
                    referrerId,
                    request.utmSource(),
                    request.utmMedium(),
                    request.utmCampaign());
        } else if (referrerId != null) {
            user = User.createNewLocalUserWithReferrer(request.email(), username, encodedPassword, referrerId);
        } else {
            user = User.createNewLocalUser(request.email(), username, encodedPassword);
        }

        User savedUser = userRepository.save(user);

        EmailVerificationToken verificationToken = EmailVerificationToken.createFor(savedUser.getId());
        tokenRepository.save(verificationToken);

        emailService.sendVerificationEmail(savedUser.getEmail(), verificationToken.getToken());

        return new RegisterResponse("PENDING_VERIFICATION", savedUser.getEmail());
    }

    @Transactional
    public AuthResponse verifyEmail(String token) {
        EmailVerificationToken verificationToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("INVALID_TOKEN"));

        if (verificationToken.isExpired()) {
            tokenRepository.deleteById(verificationToken.getId());
            throw new IllegalArgumentException("TOKEN_EXPIRED");
        }

        userRepository.markEmailVerified(verificationToken.getUserId());
        tokenRepository.deleteById(verificationToken.getId());

        User user = userRepository.findById(verificationToken.getUserId())
                .orElseThrow(() -> new IllegalStateException("User not found after verification"));

        if (user.getReferredByUserId() != null) {
            long referralCount = userRepository.countVerifiedReferrals(user.getReferredByUserId());
            if (referralCount <= MAX_REFERRALS) {
                coinLogService.awardCoinsForEvent(user.getReferredByUserId(), CoinLogService.CoinEvent.REFERRAL_INVITED, null);
            }
            coinLogService.awardCoinsForEvent(user.getId(), CoinLogService.CoinEvent.REFERRAL_WELCOME, null);
        }

        String jwtToken = jwtService.generateToken(UserPrincipal.create(user));
        return new AuthResponse(jwtToken, user.getId(), user.getEmail(), user.getRole(), user.isSeedData(), user.isPremium());
    }

    @Transactional
    public void resendVerification(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            if (user.isEmailVerified()) {
                return;
            }

            // Rate limit: refuse if last token was created less than 1 minute ago
            tokenRepository.findMostRecentByUserId(user.getId()).ifPresent(recent -> {
                if (recent.getCreatedAt().isAfter(LocalDateTime.now().minusMinutes(1))) {
                    throw new IllegalArgumentException("RATE_LIMITED");
                }
            });

            tokenRepository.deleteByUserId(user.getId());
            EmailVerificationToken newToken = EmailVerificationToken.createFor(user.getId());
            tokenRepository.save(newToken);
            emailService.sendVerificationEmail(user.getEmail(), newToken.getToken());
        });
    }

    @Transactional
    public void forgotPassword(String email) {
        // Always return silently — never reveal whether email exists (privacy)
        userRepository.findByEmail(email).ifPresent(user -> {
            passwordResetTokenRepository.deleteByUserId(user.getId()); // invalidate any old token
            PasswordResetToken resetToken = PasswordResetToken.createFor(user.getId());
            passwordResetTokenRepository.save(resetToken);
            emailService.sendPasswordResetEmail(user.getEmail(), resetToken.getToken());
        });
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("INVALID_TOKEN"));

        if (resetToken.isExpired()) {
            passwordResetTokenRepository.deleteById(resetToken.getId());
            throw new IllegalArgumentException("TOKEN_EXPIRED");
        }

        String hashedPassword = passwordEncoder.encode(newPassword);
        userRepository.updatePassword(resetToken.getUserId(), hashedPassword);
        passwordResetTokenRepository.deleteById(resetToken.getId());
    }

    private static final String DEMO_ACCOUNT_EMAIL = "test1@ev-monitor.net";

    public AuthResponse demoLogin() {
        User user = userRepository.findByEmail(DEMO_ACCOUNT_EMAIL)
                .filter(User::isSeedData)
                .orElseThrow(() -> new IllegalStateException("Demo account not available"));
        String jwtToken = jwtService.generateDemoToken(UserPrincipal.create(user));
        return new AuthResponse(jwtToken, user.getId(), user.getEmail(), user.getRole(), true, false);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .or(() -> userRepository.findByUsername(request.email()))
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(user.getEmail(), request.password()));

        if (!user.isEmailVerified()) {
            throw new IllegalArgumentException("EMAIL_NOT_VERIFIED");
        }

        String jwtToken = jwtService.generateToken(UserPrincipal.create(user));
        return new AuthResponse(jwtToken, user.getId(), user.getEmail(), user.getRole(), user.isSeedData(), user.isPremium());
    }

    private String resolveUsername(String requested, String email) {
        // Use provided username if valid and available
        if (requested != null && !requested.isBlank() && requested.matches("^[a-zA-Z0-9_]{3,20}$")) {
            if (userRepository.existsByUsername(requested)) {
                throw new IllegalArgumentException("Username is already taken.");
            }
            return requested;
        }
        // Auto-generate from email prefix
        String base = email.split("@")[0].replaceAll("[^a-zA-Z0-9_]", "_");
        if (base.length() < 3) base = base + "___";
        if (base.length() > 20) base = base.substring(0, 20);
        String candidate = base;
        int suffix = 1;
        while (userRepository.existsByUsername(candidate)) {
            String s = String.valueOf(suffix++);
            candidate = base.substring(0, Math.min(base.length(), 20 - s.length())) + s;
        }
        return candidate;
    }
}
