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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.os.Environment;

/**
 * This class is the file IO interface for the android activities.
 * 
 * It stores tags and keys in encrypted files on the external storage (typically
 * SD-card).
 * 
 * It has functionality for loading and saving tag data, key chains, crypto,
 * salt, etc..
 * 
 */
public class FileIO {
    private static final String TAG_FILE_NAME = "tag";
    private static final String KEY_FILE_NAME = "keys";
    private static final String SALT_FILE_NAME = "salt";

    private File mTagFile;
    private File mKeyFile;
    private File mSaltFile;
    private File mWorkingDir;

    private CryptoIO mCrypto;

    /**
     * @param password
     *            The password to use as a key for the encryption and
     *            decryption.
     * @param workingDir
     *            The directory where all files will be read from and written
     *            to.
     * 
     * @throws Exception
     */
    public FileIO(char[] password, File workingDir) throws Exception {
        mWorkingDir = workingDir;
        mTagFile = new File(workingDir, TAG_FILE_NAME);
        mKeyFile = new File(workingDir, KEY_FILE_NAME);
        mSaltFile = new File(workingDir, SALT_FILE_NAME);

        mCrypto = new CryptoIO(password, getSalt());
    }

    /**
     * @return The applications working directory
     */
    public File getWorkingDir() {
        return mWorkingDir;
    }

    /**
     * @return true if the tag file exists on the external storage.
     */
    public boolean hasTag() {
        return mTagFile.exists();
    }

    /**
     * Remove the tag stored on the external storage (if it exists).
     */
    public void deleteTag() {
        if (mTagFile.exists())
            mTagFile.delete();
    }

    /**
     * Load a tag from file and return it.
     * 
     * @return The tag loaded from file
     * @throws Exception
     */
    public Tag loadTag() throws Exception {
        InputStream in = Load(mTagFile);
        return Tag.Read(in);
    }

    /**
     * Save a tag to file. Overwrite the file if it exists.
     * 
     * @param t
     *            The tag to store
     * @throws Exception
     */
    public void saveTag(Tag t) throws Exception {
        // Serialize the tag and save it
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Tag.Write(t, out);
        Save(mTagFile, out);
    }

    /**
     * @return true if the key chain file exists on the external storage.
     */
    public boolean hasKeyChain() {
        return mKeyFile.exists();
    }

    /**
     * Remove the key chain stored on the external storage (if it exists).
     */
    public void deleteKeyChain() {
        if (mKeyFile.exists())
            mKeyFile.delete();
    }

    /**
     * Load a key chain from file and return it
     * 
     * @return The key chain loaded from file
     * @throws Exception
     */
    public KeyChain loadKeyChain() throws Exception {
        InputStream in = Load(mKeyFile);
        return KeyChain.Read(in);
    }

    /**
     * Load a key chain from an non-encrypted file
     * 
     * @return
     * @throws Exception
     */
    public static KeyChain importKeyChain(File f) throws Exception {
        InputStream in = new FileInputStream(f);
        return KeyChain.Read(in);
    }

    /**
     * Load a key chain from an non-encrypted inputstream
     * 
     * @return
     * @throws Exception
     */
    public static KeyChain importKeyChain(InputStream in) throws Exception {
        return KeyChain.Read(in);
    }

    /**
     * Save a key chain to file. Overwrite the file if it exists.
     * 
     * @param t
     *            The key chain to store
     * @throws Exception
     */
    public void saveKeyChain(KeyChain k) throws Exception {
        // Serialize the keys and save
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        KeyChain.Write(k, out);
        Save(mKeyFile, out);
    }

    private static void assertRWAccess() throws IOException {
        String state = Environment.getExternalStorageState();
        if (!Environment.MEDIA_MOUNTED.equals(state) && !Environment.MEDIA_MOUNTED_READ_ONLY.equals(state))
            throw new IOException("Can't access external storage");
    }

    private InputStream Load(File f) throws Exception {
        assertRWAccess();
        if (!f.exists())
            throw new IOException("File not found");

        // Decrypt the file
        InputStream isCipherText = new FileInputStream(f);
        ByteArrayOutputStream outClear = new ByteArrayOutputStream();
        mCrypto.decrypt(isCipherText, outClear);

        // Pipe clear text output to input
        return new ByteArrayInputStream(outClear.toByteArray());
    }

    private void Save(File f, ByteArrayOutputStream out) throws Exception {
        assertRWAccess();

        // Pipe clear text out stream to an in stream
        InputStream inClearText = new ByteArrayInputStream(out.toByteArray());

        // Encrypt data stream and send it to a file
        OutputStream outCipherText = new FileOutputStream(f);
        mCrypto.encrypt(inClearText, outCipherText);
    }

    // Return password salt from the salt file. Generate it if it doesn't exist.
    private byte[] getSalt() throws Exception {
        assertRWAccess();

        byte[] salt;
        if (mSaltFile.exists()) {
            // Salt file exists, read it.
            InputStream is = null;
            try {
                is = new FileInputStream(mSaltFile);
                salt = new byte[CryptoIO.SALT_SIZE];
                int read = is.read(salt);
                if (read != CryptoIO.SALT_SIZE)
                    throw new IOException("Illegal salt file length");
            } finally {
                if (is != null)
                    is.close();
            }
        } else {
            // No salt file. Create it.
            salt = CryptoIO.makeSalt();

            OutputStream out = null;
            try {
                out = new FileOutputStream(mSaltFile);
                out.write(salt);
            } finally {
                if (out != null)
                    out.close();
            }
        }

        return salt;
    }
}
