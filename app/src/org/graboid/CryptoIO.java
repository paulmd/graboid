/**
 * Copyright (c) 2013 Paul Muad'Dib
 * 
 * This file is part of Graboid.
 * 
 * Graboid is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * Graboid is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with Graboid.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.graboid;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.security.spec.KeySpec;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * This class encapsulates functionality for encrypting and decrypting data
 * streams using AES in CBC mode with PKCS5 padding.
 */
public class CryptoIO {
    public static final int SALT_SIZE = 8;

    private static final int IV_SIZE = 16;
    private static final int BUFSIZE = 1024;
    private static final int KEY_ITERATIONS = 100;
    private static final int KEY_LENGTH = 256;

    private static SecureRandom prng;
    private byte[] mBuf = new byte[BUFSIZE];

    private SecretKey mSecretKey;
    private Cipher mCipher;

    /**
     * Create a new crypto helper object, configured with a specified password and salt.
     * @param password The password
     * @param salt The salt (SALT_SIZE long byte array)
     * @throws Exception
     */
    public CryptoIO(char[] password, byte[] salt) throws Exception {
        assert (password != null && password.length > 0);
        assert (salt != null && salt.length == SALT_SIZE);

        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        KeySpec keySpec = new PBEKeySpec(password, salt, KEY_ITERATIONS, KEY_LENGTH);
        SecretKey secret = factory.generateSecret(keySpec);
        mSecretKey = new SecretKeySpec(secret.getEncoded(), "AES");
        mCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
    }

    /**
     * Encrypt the in data stream and send it to the out stream.
     * @param in Clear text input stream
     * @param out Cipher text output stream
     * @throws Exception
     */
    public void encrypt(InputStream in, OutputStream out) throws Exception {

        // Gen new IV
        byte[] iv = makeIV();

        // Configure the cipher for encryption
        mCipher.init(Cipher.ENCRYPT_MODE, mSecretKey, new IvParameterSpec(iv));

        // Write IV to front of the stream
        out.write(iv);

        // Write the encrypted data
        processStream(in, new CipherOutputStream(out, mCipher));
    }

    /**
     * Decrypt the in data stream and send it to the out stream.
     * @param in Cipher text input stream
     * @param out Clear text output stream
     * @throws Exception
     */
    public void decrypt(InputStream in, OutputStream out) throws Exception {

        // Read IV from front of the stream
        byte[] iv = new byte[IV_SIZE];
        if (in.read(iv, 0, iv.length) < 0)
            throw new Exception();

        // Configure the cipher for decryption
        mCipher.init(Cipher.DECRYPT_MODE, mSecretKey, new IvParameterSpec(iv));

        // Read the encrypted data
        processStream(new CipherInputStream(in, mCipher), out);
    }

    private void processStream(InputStream in, OutputStream out) throws IOException {
        int read = 0;
        while ((read = in.read(mBuf)) >= 0)
            out.write(mBuf, 0, read);
        out.close();
    }

    public static byte[] makeSalt() throws Exception {
        return genRandomBytes(SALT_SIZE);
    }

    private static byte[] makeIV() throws Exception {
        return genRandomBytes(IV_SIZE);
    }

    private static byte[] genRandomBytes(int count) throws Exception {
        if (prng == null)
            prng = SecureRandom.getInstance("SHA1PRNG");

        byte[] bytes = new byte[count];
        prng.nextBytes(bytes);
        return bytes;
    }
}
