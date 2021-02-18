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

import java.security.SecureRandom;

import org.apache.commons.collections4.map.LRUMap;

/**
 * Factory for generating one-time-use tokens for the 
 * ScriptingVariablesConsolePlugin + SlingBindingsVariablesListJsonServlet
 * transactions.
 */
final class ScriptingVariablesNonceFactory {
    private static final String EMPTY_STRING = "";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    // use LRUMap so it is not possible to grow indefinitely if isValid is
    //  never called after nextNonce is called
    private static final LRUMap<String, String> validNonceSet = new LRUMap<>(100);

    /**
     * Generate a random use once value
     * 
     * @return random nonce value
     */
    public static String nextNonce() {
        byte[] nonce = new byte[8];
        SECURE_RANDOM.nextBytes(nonce);
        String nonceHex = convertBytesToHex(nonce);
        validNonceSet.put(nonceHex, EMPTY_STRING);
        return nonceHex;
    }

    /**
     * Check if the supplied value matches one of the
     * previously generated single use values
     * 
     * @return true if the nonce was valid, false otherwise
     */
    public static boolean isValid(String nonce) {
        Object remove = validNonceSet.remove(nonce);
        return remove != null;
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
