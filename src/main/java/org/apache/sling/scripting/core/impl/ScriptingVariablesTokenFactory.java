/*
 * Copyright 2016 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sling.scripting.core.impl;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for generating tokens for the
 * ScriptingVariablesConsolePlugin + SlingBindingsVariablesListJsonServlet
 * transactions.
 */
final class ScriptingVariablesTokenFactory {

    /** The logger. */
    private static final Logger LOGGER = LoggerFactory.getLogger(ScriptingVariablesTokenFactory.class);

    /**
     * The maximum duration in milliseconds that a token is considered valid
     */
    private static final long MAX_TOKEN_DURATION = TimeUnit.MINUTES.toMillis(5);

    /**
     * The name of the HMAC function to calculate the hash code of the payload
     * with the token.
     */
    private static final String HMAC_SHA256 = "HmacSHA256";

    private static final SecretKeySpec SECRET_KEY;
    static {
        final SecureRandom secureRandom = new SecureRandom();
        byte[] b = new byte[20];
        secureRandom.nextBytes(b);
        SECRET_KEY = new SecretKeySpec(b, HMAC_SHA256);
    }

    private ScriptingVariablesTokenFactory() {
        // private constructor to hide the implicit public one.
    }

    /**
     * Creates a new token
     * 
     * @return the new token
     */
    public static String createToken() {
        return Long.toString(System.currentTimeMillis());
    }

    /**
     * Generate the hash value for the specified token
     * 
     * @param token the token to generate the hash for
     * @return the hash for the token
     */
    public static String toHash(String token) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac sha256HMAC = Mac.getInstance(HMAC_SHA256);
        sha256HMAC.init(SECRET_KEY);

        return convertBytesToHex(sha256HMAC.doFinal(token.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Check if the supplied token matches the supplied hash matches
     *
     * @param token the token value to check
     * @param hash the expected hash value
     *
     * @return true if the token was valid, false otherwise
     */
    public static boolean isValid(String token, String hash) throws InvalidKeyException, NoSuchAlgorithmException {
        boolean valid = false;
        if (token != null && hash != null) {
            // parse the token as a timestamp long
            long tokenTime = -1;
            try {
                tokenTime = Long.parseLong(token);
            } catch (NumberFormatException nfe) {
                LOGGER.warn("Failed to parse token", nfe);
            }

            if (tokenTime > 0) {
                // check if the token is within the allowed duration window
                long timeDiff = System.currentTimeMillis() - tokenTime;
                if (timeDiff < MAX_TOKEN_DURATION) {
                    // duration is ok, so check the hash against what is expected
                    String candidateHash = toHash(token);
                    valid = hash.equals(candidateHash);
                }
            }
        }
        return valid;
    }

    /**
     * convert bytes to a hex string
     * @param bytes the value to convert
     * @return converted string
     */
    private static String convertBytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte temp : bytes) {
            result.append(String.format("%02x", temp));
        }
        return result.toString();
    }
}
