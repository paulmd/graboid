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

import android.os.Parcel;
import android.os.Parcelable;

/**
 * This class represents a Mifare classic tag.
 * 
 * It provides data access and serialization.
 */
public class Tag implements Parcelable {

    /**
     * Number of bytes in a block
     */
    public final static int BLOCK_SIZE = 16;

    /**
     * Number of bytes in key
     */
    public final static int KEY_SIZE = 6;

    /**
     * Number of bytes in a UID
     */
    public final static int UID_SIZE = 4;

    private TagType mType;
    private byte[][] mData;

    /**
     * Create a new tag of the specified type.
     */
    public Tag(TagType type) {
        mType = type;
        mData = new byte[type.getBlockCount()][BLOCK_SIZE];
    }

    /**
     * @return The tags total number of blocks
     */
    public int getBlockCount() {
        return mType.getBlockCount();
    }

    /**
     * @return The tags total number of sectors
     */
    public int getSectorCount() {
        return mType.getSectorCount();
    }

    /**
     * Get the tag data of a specified block.
     * 
     * @param block
     *            The block to access
     * @return block The block data, a byte array of BLOCK_SIZE length.
     */
    public byte[] getBlock(int block) {
        assert (block >= 0 && block < mData.length);
        return mData[block];
    }

    /**
     * Set the tag data of a specified block.
     * 
     * @param block
     *            The block to modify
     * @param data
     *            The new block data, a byte array of BLOCK_SIZE length
     */
    public void setBlock(int block, byte[] data) {
        assert (block >= 0 && block < mData.length);
        assert (data != null && data.length == BLOCK_SIZE);
        mData[block] = data;
    }

    /**
     * Get the tag UID, i.e. the first UID_SIZE bytes of the first block.
     * 
     * @return The tag UID
     */
    public byte[] getUID() {
        byte[] uid = new byte[UID_SIZE];
        for (int i = 0; i < UID_SIZE; ++i)
            uid[i] = mData[0][i];
        return uid;
    }

    /**
     * @return A string representation of the tag UID.
     */
    public String getUIDString() {
        StringBuilder sb = new StringBuilder();
        for (byte b : getUID())
            sb.append(String.format("%02x", b));
        return sb.toString();
    }

    /**
     * Prevent any further changes to the ACL
     */
    public boolean fuseACL() {
        if (mType == TagType.MFC_1k || mType == TagType.MFC_2k) {
            // 16 or 32 sectors with 4 block each
            for (int b = 3; b < mType.getBlockCount(); b += 4)
                fuseACLBlock(b);
        } else if (mType == TagType.MFC_4k) {
            // 32 sectors with 4 block each
            for (int b = 3; b < 32 * 4; b += 4)
                fuseACLBlock(b);
            // then 8 sectors with 16 blocks each
            for (int b = 32 * 4 + 15; b < mType.getBlockCount(); b += 16)
                fuseACLBlock(b);
        } else {
            return false;
        }

        return true;
    }

    private void fuseACLBlock(int block) {
        // C1 C2 C3 : 1 0 0 -> B key to write keys, no way to change ACL
        // alt. C1 C2 C3 : 1 1 1 -> no way to write keys, no way to change ACL

        // 1 - - - 0 - - -
        mData[block][6] = (byte) (((int) mData[block][6] | 0x80) & 0xF7);

        // 1 - - - 1 - - -
        mData[block][7] = (byte) ((int) mData[block][7] | 0x88);

        // 0 - - - 0 - - -
        mData[block][8] = (byte) ((int) mData[block][8] & 0x77);
    }

    /**
     * De-serialize the tag from an input stream and return a matching tag
     * instance.
     * 
     * @param in
     *            The stream containing the tag data.
     * @return The new de-serialized tag instance
     * @throws IOException
     */
    public static Tag Read(InputStream in) throws IOException {

        // Read the tag type
        TagType type = TagType.getType(in.read());
        if (type == null)
            throw new IOException("Invalid tag data; illegal type");

        // Read the data, one block at the time
        Tag t = new Tag(type);
        for (int i = 0; i < type.getBlockCount(); ++i) {
            int read = in.read(t.mData[i], 0, BLOCK_SIZE);
            if (read != BLOCK_SIZE)
                throw new IOException("Invalid tag data; underflow");
        }

        return t;
    }

    /**
     * Serialize the tag and write it to an output stream.
     * 
     * The data will be padded up to the size of the largest tag type to ensure
     * that all tag types generate the same amount of data.
     * 
     * @param t
     *            The tag to serialize
     * @param out
     *            The output stream to receive the data
     * @throws IOException
     */
    public static void Write(Tag t, OutputStream out) throws IOException {

        // Save the tag type
        out.write((byte) t.mType.getSectorCount());

        // Write the data
        byte[][] data = t.mData; // Local variable optimization
        for (byte[] block : data)
            out.write(block);

        // Pad up to 4k size
        int maxSize = 4096;
        assert (maxSize - data.length * BLOCK_SIZE >= 0);
        out.write(new byte[maxSize - data.length * BLOCK_SIZE]);

        out.close();
    }

    // -- Parcelable impl --------------------------------------------

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mType.getSectorCount());
        for (byte[] block : mData)
            dest.writeByteArray(block);
    }

    public static final Parcelable.Creator<Tag> CREATOR = new Parcelable.Creator<Tag>() {
        public Tag createFromParcel(Parcel in) {
            TagType t = TagType.getType(in.readInt());

            Tag tag = new Tag(t);
            for (int i = 0; i < tag.getBlockCount(); ++i)
                tag.setBlock(i, in.createByteArray());

            return tag;
        }

        public Tag[] newArray(int size) {
            return new Tag[size];
        }
    };
}
