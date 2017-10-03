package com.blundell.iotcore;

import android.content.res.Resources;
import android.util.Base64;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

class IotCorePasswordGenerator {

    private final String projectId;
    private final Resources resources;
    private final int privateKeyRawFileId;

    IotCorePasswordGenerator(String projectId, Resources resources, int privateKeyRawFileId) {
        this.projectId = projectId;
        this.resources = resources;
        this.privateKeyRawFileId = privateKeyRawFileId;
    }

    char[] createJwtRsaPassword() {
        try {
            byte[] privateKeyBytes = decodePrivateKey(resources, privateKeyRawFileId);
            return createJwtRsaPassword(projectId, privateKeyBytes).toCharArray();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algorithm not supported. (developer error)", e);
        } catch (InvalidKeySpecException e) {
            throw new IllegalStateException("Invalid Key spec. (developer error)", e);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read private key file.", e);
        }
    }

    private static byte[] decodePrivateKey(Resources resources, int privateKeyRawFileId) throws IOException {
        try(InputStream inStream = resources.openRawResource(privateKeyRawFileId)) {
            return Base64.decode(inputToString(inStream), Base64.DEFAULT);
        }
    }

    private static String inputToString(InputStream is) {
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    private static String createJwtRsaPassword(String projectId, byte[] privateKeyBytes) throws NoSuchAlgorithmException, InvalidKeySpecException {
        return createPassword(projectId, privateKeyBytes, SignatureAlgorithm.RS256); // for ES256 just change the enum
    }

    private static String createPassword(String projectId, byte[] privateKeyBytes, SignatureAlgorithm signatureAlgorithm) throws NoSuchAlgorithmException, InvalidKeySpecException {
        LocalDateTime now = LocalDateTime.now();
        // Create a JWT to authenticate this device. The device will be disconnected after the token
        // expires, and will have to reconnect with a new token. The audience field should always be set
        // to the GCP project id.
        Date issueDate = Date.from(now.minusDays(1).toInstant(ZoneOffset.MIN)); // TODO DATE HACK????
        Log.d("TUT", "JWT issue date: " + issueDate);
        JwtBuilder jwtBuilder =
                Jwts.builder()
                        .setIssuedAt(issueDate)
                        .setExpiration(Date.from(now.plusMinutes(20).toInstant(ZoneOffset.MIN)))
                        .setAudience(projectId);

        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(privateKeyBytes);
        KeyFactory kf = KeyFactory.getInstance(signatureAlgorithm.getValue());

        return jwtBuilder.signWith(signatureAlgorithm, kf.generatePrivate(spec)).compact();
    }

}