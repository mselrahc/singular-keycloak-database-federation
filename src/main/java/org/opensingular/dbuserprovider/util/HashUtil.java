package org.opensingular.dbuserprovider.util;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.binary.StringUtils;
import org.apache.commons.codec.digest.DigestUtils;

import com.google.common.collect.ImmutableMap;

import at.favre.lib.crypto.bcrypt.BCrypt;
import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import de.mkammerer.argon2.Argon2Factory.Argon2Types;

public class HashUtil {
    private static final Map<String, Argon2Types> ARGON2TYPES = ImmutableMap.of(
        "Argon2d", Argon2Types.ARGON2d,
        "Argon2i", Argon2Types.ARGON2i,
        "Argon2id", Argon2Types.ARGON2id
    );
    private static final Map<Argon2Types, Argon2> ARGON2 = ImmutableMap.of(
        Argon2Types.ARGON2d, Argon2Factory.create(Argon2Types.ARGON2d),
        Argon2Types.ARGON2i, Argon2Factory.create(Argon2Types.ARGON2i),
        Argon2Types.ARGON2id, Argon2Factory.create(Argon2Types.ARGON2id)
    );
    
    private static boolean isArgon2(String alg) {
        return alg.contains("Argon2");
    }

    private static boolean isBlowfish(String alg) {
        return alg.toLowerCase().contains("blowfish");
    }

    public static String hash(String plain, String alg) {
        if (isBlowfish(alg)) {
            return BCrypt.withDefaults().hashToString(14, plain.toCharArray());
        }

        if (isArgon2(alg)) {
            return ARGON2.get(ARGON2TYPES.get(alg)).hash(4, 125000, 2, plain.toCharArray());
        } 

        if(alg.equals("PBKDF2-SHA256")){
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            return new PBKDF2SHA256HashingUtil(plain, salt, 650000).hashPassword();
        }

        MessageDigest digest = DigestUtils.getDigest(alg);
        byte[] pwdBytes = StringUtils.getBytesUtf8(plain);
        return Hex.encodeHexString(digest.digest(pwdBytes));
    }

    public static boolean verify(String hash, String plain, String alg) {
        if (isBlowfish(alg)) {
            return !hash.isEmpty() && BCrypt.verifyer().verify(plain.toCharArray(), hash).verified;
        } 
        
        if (isArgon2(alg)) {
            return !hash.isEmpty() && ARGON2.get(ARGON2TYPES.get(alg)).verify(hash, plain.toCharArray());
        } 
        
        if(alg.equals("PBKDF2-SHA256")){
            String[] components = hash.split("\\$");
            return new PBKDF2SHA256HashingUtil(plain, components[2].getBytes(), Integer.parseInt(components[1])).validatePassword(components[3]);
        }

        MessageDigest digest   = DigestUtils.getDigest(alg);
        byte[]        pwdBytes = StringUtils.getBytesUtf8(plain);
        return Objects.equals(Hex.encodeHexString(digest.digest(pwdBytes)), hash);
    }
}
