package com.jasonweinzierl.chatroom;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * chatroom
 *
 * Passwords should be stored in a {@code char[]} so that it can be filled
 * with zeros after use instead of lingering on the heap and elsewhere.
 *
 * @author JasonWeinzierl
 * @version 2019-04-19
 */
public class PasswordAuthentication
{
    /**
     * Identifier prefix for this class.
     */
    public static final String ID = "$32$";

    public static final int DEFAULT_STRENGTH = 65536;

    private static final String ALGORITHM = "PBKDF2WithHmacSHA1";

    private static final int KEY_LENGTH = 128;

    private static final Pattern LAYOUT = Pattern.compile("\\$32\\$(\\d+)\\$(.{43})");

    private final SecureRandom random;

    private final int strength;

    public PasswordAuthentication() {
        this(DEFAULT_STRENGTH);
    }

    public PasswordAuthentication(int strength) {
        this.strength = strength;
        this.random = new SecureRandom();
    }

    /**
     * Hash password for storage
     *
     * @param password password to hash
     * @return secure auth token to be stored for later
     */
    public String hash(char []password) {
        // salt
        byte []salt = new byte[KEY_LENGTH / 8];
        random.nextBytes(salt);

        // hash
        byte []derivedKey = pbkdf2(password, salt, this.strength);

        // copy into hash
        byte []hash = new byte[salt.length + derivedKey.length];
        System.arraycopy(salt, 0, hash, 0, salt.length);
        System.arraycopy(derivedKey, 0, hash, salt.length, derivedKey.length);

        // unite identifier, strength, and hash
        return PasswordAuthentication.ID + this.strength + '$' + Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }

    /**
     * Authenticate with a password and stored token
     *
     * @param password password to verify
     * @param authToken authentication token
     * @return true if match
     */
    public boolean verify(char []password, String authToken) {
        // make sure proper format (identifier, strength, salt, hashed password)
        Matcher matcher = PasswordAuthentication.LAYOUT.matcher(authToken);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid authentication token format.");
        }

        // get hash from token
        byte []hash = Base64.getUrlDecoder().decode(matcher.group(2));

        // hash password to be verified
        int strength = Integer.parseInt(matcher.group(1));
        byte []salt = Arrays.copyOfRange(hash, 0, KEY_LENGTH / 8);
        byte []check = pbkdf2(password, salt, strength);

        // compare password with hash
        int zero = 0;
        for (int i = 0; i < check.length; i++) {
            zero |= hash[salt.length + i] ^ check[i];
        }
        return zero == 0;
    }

    private static byte []pbkdf2(char []password, byte []salt, int strength) {
        // set up key material
        KeySpec spec = new PBEKeySpec(password, salt, strength, KEY_LENGTH);

        // hash with factory
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(PasswordAuthentication.ALGORITHM);
            return factory.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException n) {
            throw new IllegalStateException("Missing algorithm: " + PasswordAuthentication.ALGORITHM, n);
        } catch (InvalidKeySpecException i) {
            throw new IllegalStateException("Invalid SecretKeyFactory", i);
        }
    }
}
