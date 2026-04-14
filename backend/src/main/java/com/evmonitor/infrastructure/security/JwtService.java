package com.evmonitor.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration.ms}")
    private long jwtExpirationInMs;

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String generateToken(UserDetails userDetails) {
        Map<String, Object> extraClaims = new HashMap<>();
        if (userDetails instanceof UserPrincipal principal) {
            extraClaims.put("userId", principal.getUser().getId().toString());
            extraClaims.put("username", principal.getUser().getUsername());
            extraClaims.put("demoAccount", principal.getUser().isSeedData());
            extraClaims.put("authProvider", principal.getUser().getAuthProvider().name());
            extraClaims.put("role", principal.getUser().getRole());
            extraClaims.put("premium", principal.getUser().isPremium());
            if (principal.getUser().getCountry() != null) {
                extraClaims.put("country", principal.getUser().getCountry());
            }
            extraClaims.put("registeredAt", principal.getUser().getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE));
        }
        return generateToken(extraClaims, userDetails);
    }

    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(userDetails.getUsername())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + jwtExpirationInMs))
                .signWith(getSigningKey())
                .compact();
    }

    public String generateDemoToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        if (userDetails instanceof UserPrincipal principal) {
            claims.put("userId", principal.getUser().getId().toString());
            claims.put("username", principal.getUser().getUsername());
            claims.put("demoAccount", true);
            claims.put("authProvider", principal.getUser().getAuthProvider().name());
        }
        long oneHourMs = 3_600_000L;
        return Jwts.builder()
                .claims(claims)
                .subject(userDetails.getUsername())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + oneHourMs))
                .signWith(getSigningKey())
                .compact();
    }

    public String generateImpersonationToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        if (userDetails instanceof UserPrincipal principal) {
            claims.put("userId", principal.getUser().getId().toString());
            claims.put("username", principal.getUser().getUsername());
            claims.put("demoAccount", principal.getUser().isSeedData());
            claims.put("authProvider", principal.getUser().getAuthProvider().name());
        }
        claims.put("impersonatedBy", "admin");
        long oneHourMs = 3_600_000L;
        return Jwts.builder()
                .claims(claims)
                .subject(userDetails.getUsername())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + oneHourMs))
                .signWith(getSigningKey())
                .compact();
    }

    public String generateUnsubscribeToken(String email) {
        return Jwts.builder()
                .subject(email)
                .claim("purpose", "unsubscribe")
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000))
                .signWith(getSigningKey())
                .compact();
    }

    public String extractEmailFromUnsubscribeToken(String token) {
        Claims claims = extractAllClaims(token);
        if (!"unsubscribe".equals(claims.get("purpose"))) {
            throw new IllegalArgumentException("Invalid token purpose");
        }
        return claims.getSubject();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
