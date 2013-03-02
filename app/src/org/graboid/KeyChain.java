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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * This class represents the keys used to read and write a Mifare Classic tag.
 * 
 * It's a mapping between the sectors of a card and access keys. There are two
 * keys (A & B) per sector.
 */
public class KeyChain implements Parcelable {
    public final static int KEY_SIZE = 6;
    private final static int A_KEY = 0;
    private final static int B_KEY = 1;

    private final static byte[] DEFAULT_KEY = new byte[6];

    // [A|B][sector][key data]
    private byte[][][] mKeys;

    /**
     * Create a KeyChain for a given tag type.
     * 
     * @param tagType
     */
    public KeyChain(TagType tagType) {
        mKeys = new byte[2][tagType.getSectorCount()][];
        for (int i = 0; i < tagType.getSectorCount(); ++i) {
            mKeys[A_KEY][i] = DEFAULT_KEY;
            mKeys[B_KEY][i] = DEFAULT_KEY;
        }
    }

    /**
     * @return the number of sectors (key pairs) in the key chain
     */
    public int getSectorCount() {
        return mKeys[0].length;
    }

    /**
     * Get the A key for the given sector
     * 
     * @param sector
     *            The sector to request a key for
     * @return The key data (KEY_SIZE byte array)
     */
    public byte[] getKeyA(int sector) {
        assert (sector >= 0 && sector < getSectorCount());
        return mKeys[A_KEY][sector];
    }

    /**
     * Get the B key for the given sector
     * 
     * @param sector
     *            The sector to request a key for
     * @return The key data (KEY_SIZE byte array)
     */
    public byte[] getKeyB(int sector) {
        assert (sector >= 0 && sector < getSectorCount());
        return mKeys[B_KEY][sector];
    }

    /**
     * Set the A key
     * 
     * @param sector
     *            The sector to set a key for
     * @param key
     *            The new key (KEY_SIZE byte array)
     */
    public void setKeyA(int sector, byte[] key) {
        assert (sector >= 0 && sector < getSectorCount());
        assert (key != null && key.length == KEY_SIZE);
        mKeys[A_KEY][sector] = key;
    }

    /**
     * Set the B key
     * 
     * @param sector
     *            The sector to set a key for
     * @param key
     *            The new key (KEY_SIZE byte array)
     */
    public void setKeyB(int sector, byte[] key) {
        assert (sector >= 0 && sector < getSectorCount());
        assert (key != null && key.length == KEY_SIZE);
        mKeys[B_KEY][sector] = key;
    }

    /**
     * De-serialize the key chain from an input stream and return a matching
     * KeyChain instance.
     * 
     * The data format is a text file with one A/B key pair per line, expressed
     * in hex, separated with a space. Comments are lines prefixed with '#'
     * 
     * @param in
     *            The stream containing the key data.
     * @return The new de-serialized keychain instance
     * @throws IOException
     */
    public static KeyChain Read(InputStream in) throws IOException {

        int maxSectors = TagType.MFC_4k.getSectorCount();
        int sector = 0;
        byte[][][] data = new byte[2][maxSectors][KEY_SIZE];

        BufferedReader reader = new BufferedReader(new InputStreamReader(in));

        // Start parsing line by line
        String line = reader.readLine();
        while (line != null && sector < maxSectors) {
            line = line.trim();

            // Skip comments and empty lines
            if (line.length() == 0 || line.startsWith("#")) {
                line = reader.readLine();
                continue;
            }

            // Split A|B keys
            String[] ab = line.split(" ");
            if (ab.length != 2 || ab[0].length() != KEY_SIZE * 2 || ab[1].length() != KEY_SIZE * 2)
                throw new IOException("Invalid key file format");

            // Read byte by byte
            for (int i = 0; i < KEY_SIZE; ++i) {
                data[A_KEY][sector][i] = (byte) Integer.parseInt(ab[A_KEY].substring(i * 2, (i + 1) * 2), 16);
                data[B_KEY][sector][i] = (byte) Integer.parseInt(ab[B_KEY].substring(i * 2, (i + 1) * 2), 16);
            }

            ++sector;
            line = reader.readLine();
        }

        // Move the data in to a KeyChain object
        TagType type = TagType.getType(sector);
        if (type == null)
            throw new IOException("Invalid key file; illegal key number");

        KeyChain k = new KeyChain(type);
        k.mKeys = data;

        return k;
    }

    /**
     * Serialize the key chain and write it to an output stream.
     * 
     * The data will be padded up to the size of the largest key chain for the
     * largest tag type to ensure that all tag types generate the same amount of
     * data.
     * 
     * @param t
     *            The tag to serialize
     * @param out
     *            The output stream to receive the data
     * @throws IOException
     */
    public static void Write(KeyChain k, OutputStream out) throws IOException {

        // Write each sector: A[spc]B[nl]
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out));
        for (int i = 0; i < k.getSectorCount(); ++i) {
            for (byte b : k.getKeyA(i))
                writer.write(String.format("%02x", b));
            writer.write(" ");
            for (byte b : k.getKeyB(i))
                writer.write(String.format("%02x", b));
            writer.write("\n");
        }

        // Pad to 4k tag size
        for (int i = 0; i < TagType.MFC_4k.getSectorCount() - k.getSectorCount(); ++i)
            writer.write("#PADINGPADINGPADINGPADING\n");

        writer.close();
    }

    // -- Parcelable impl --------------------------------------------

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        int sectorCount = mKeys[0].length;
        dest.writeInt(sectorCount);
        for (int i = 0; i < sectorCount; ++i) {
            dest.writeByteArray(mKeys[A_KEY][i]);
            dest.writeByteArray(mKeys[B_KEY][i]);
        }
    }

    public static final Parcelable.Creator<KeyChain> CREATOR = new Parcelable.Creator<KeyChain>() {
        public KeyChain createFromParcel(Parcel in) {
            int sectorCount = in.readInt();

            KeyChain keys = new KeyChain(TagType.getType(sectorCount));
            for (int i = 0; i < sectorCount; ++i) {
                keys.mKeys[A_KEY][i] = in.createByteArray();
                keys.mKeys[B_KEY][i] = in.createByteArray();
            }

            return keys;
        }

        public KeyChain[] newArray(int size) {
            return new KeyChain[size];
        }
    };
}
