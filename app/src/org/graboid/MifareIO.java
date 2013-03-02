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

import android.nfc.tech.MifareClassic;

/**
 * This class is the Mifare IO interface for the android activities.
 */
public class MifareIO {
    public interface IProgressListener {
        void publishProgress(int progress);
    }

    private MifareClassic mTag;
    private KeyChain mKeys;

    private IProgressListener mProgressListener;

    /**
     * Create an instance of the helper class to interface a tag using some
     * keys.
     * 
     * @param tag
     *            The Mifare tag to interface
     * @param keys
     *            The keys to use in communication.
     */
    public MifareIO(MifareClassic tag, KeyChain keys, IProgressListener progressListener) {
        assert (tag != null && keys != null);

        mTag = tag;
        mKeys = keys;
        mProgressListener = progressListener;
    }

    /**
     * Read all blocks from the Mifare tag and return the data in a Tag object.
     * 
     * @return A Tag object containing the data of the Mifare tag.
     * @throws IOException
     */
    public Tag read() throws IOException {

        int sectors = mTag.getSectorCount();
        Tag t = new Tag(TagType.getType(sectors));

        if (t.getSectorCount() > mKeys.getSectorCount())
            throw new IOException("Too few keys");

        mTag.connect();

        try {

            for (int s = 0; s < sectors; ++s) {

                byte[] aKey = mKeys.getKeyA(s);
                byte[] bKey = mKeys.getKeyB(s);

                // Authenticate for each sector (try A key, then B key)
                if (!mTag.authenticateSectorWithKeyA(s, aKey) && !mTag.authenticateSectorWithKeyB(s, bKey))
                    throw new IOException("Auth error");

                // Read every block of the sector
                int blockOffset = mTag.sectorToBlock(s);
                int lastBlock = blockOffset + mTag.getBlockCountInSector(s);
                for (int b = blockOffset; b < lastBlock; ++b) {
                    byte[] readBuffer = mTag.readBlock(b);

                    // Manually transfer key data to tag since it is usually not
                    // readable
                    if (b == lastBlock - 1) {
                        for (int i = 0; i < Tag.KEY_SIZE; ++i) {
                            readBuffer[i] = aKey[i];
                            readBuffer[Tag.BLOCK_SIZE - Tag.KEY_SIZE + i] = bKey[i];
                        }
                    }

                    t.setBlock(b, readBuffer);
                    if (mProgressListener != null)
                        mProgressListener.publishProgress((100 * b) / t.getBlockCount());
                }
            }

            return t;

        } finally {
            mTag.close();
        }
    }

    /**
     * Write all the blocks in a Tag to the Mifare tag.
     * 
     * @param t
     * @throws IOException
     */
    public void write(Tag t) throws IOException {

        if (t.getSectorCount() > mKeys.getSectorCount())
            throw new IOException("Too few keys");

        mTag.connect();

        int sectors = mTag.getSectorCount();

        try {

            for (int s = 0; s < sectors; ++s) {

                // Authenticate for each sector (try B key, then A key)
                if (!mTag.authenticateSectorWithKeyB(s, mKeys.getKeyB(s))
                        && !mTag.authenticateSectorWithKeyA(s, mKeys.getKeyA(s)))
                    throw new IOException("Auth error");

                // Write to tag. Skip block 0 and the trailer of each sector
                int blockOffset = mTag.sectorToBlock(s);
                int lastBlock = blockOffset + mTag.getBlockCountInSector(s);

                // Skip block 0
                blockOffset = blockOffset == 0 ? 1 : blockOffset;

                for (int b = blockOffset; b < lastBlock; ++b) {
                    mTag.writeBlock(b, t.getBlock(b));
                    if (mProgressListener != null)
                        mProgressListener.publishProgress((100 * b) / t.getBlockCount());
                }
            }
        } finally {
            mTag.close();
        }
    }

    /**
     * Test if all the keys in the KeyChain are valid, i.e. can be used for
     * authenticating.
     * 
     * @return true if all A and B keys can be used to authenticate, false
     *         otherwise.
     * @throws IOException
     */
    public boolean testKeys() throws IOException {

        // Tag and key sector count mismatch.
        // Need at least as many keys as there are sectors.
        if (mTag.getSectorCount() > mKeys.getSectorCount())
            return false;

        mTag.connect();

        try {
            for (int i = 0; i < mTag.getSectorCount(); ++i) {
                if (!mTag.authenticateSectorWithKeyA(i, mKeys.getKeyA(i))
                        || !mTag.authenticateSectorWithKeyB(i, mKeys.getKeyB(i))) {
                    return false;
                }
            }
        } finally {
            mTag.close();
        }

        return true;
    }

}
