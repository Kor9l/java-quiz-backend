package com.korl.javaquiz.security;

import at.favre.lib.crypto.bcrypt.BCrypt;
import at.favre.lib.crypto.bcrypt.LongPasswordStrategies;
import jakarta.enterprise.context.ApplicationScoped;

import java.nio.charset.StandardCharsets;

/**
 * BCrypt, in place of Spring Security's {@code BCryptPasswordEncoder}.
 *
 * <p>Deliberately the same parameters that class used: version {@code $2a} at cost 10, and
 * passwords longer than BCrypt's 72-byte limit truncated rather than rejected. Hashes written
 * by the Spring version therefore keep verifying, and hashes written now would verify there.
 *
 * <p>The work is exposed statically as well as through the bean because Flyway constructs
 * {@code V3__SeedAdmin} itself, outside CDI, and that migration needs to hash a password.
 */
@ApplicationScoped
public class PasswordHasher {

    private static final int COST = 10;
    private static final BCrypt.Version VERSION = BCrypt.Version.VERSION_2A;

    private static final BCrypt.Hasher HASHER =
            BCrypt.with(VERSION, LongPasswordStrategies.truncate(VERSION));
    private static final BCrypt.Verifyer VERIFIER =
            BCrypt.verifyer(VERSION, LongPasswordStrategies.truncate(VERSION));

    public static String hash(String raw) {
        return HASHER.hashToString(COST, raw.toCharArray());
    }

    public static boolean verify(String raw, String hash) {
        if (raw == null || hash == null || hash.isBlank()) {
            return false;
        }
        return VERIFIER.verify(raw.getBytes(StandardCharsets.UTF_8), hash.getBytes(StandardCharsets.UTF_8)).verified;
    }

    public String encode(String raw) {
        return hash(raw);
    }

    public boolean matches(String raw, String hash) {
        return verify(raw, hash);
    }
}
