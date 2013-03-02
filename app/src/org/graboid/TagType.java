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

/**
 * This class represents the Mifare Classic tag types.
 * 
 * Knowing and representing the tag type is required to keep information about
 * the number of blocks and sectors of a card.
 */
public class TagType {
    private final static int BLOCKS_IN_SECTOR = 4;
    private final static int BLOCKS_IN_EXTENDED_SECTOR = 16;

    // The Mifare Classic singleton tag type instances
    public final static TagType MFC_MINI = new TagType(5, 5 * BLOCKS_IN_SECTOR);
    public final static TagType MFC_1k = new TagType(16, 16 * BLOCKS_IN_SECTOR);
    public final static TagType MFC_2k = new TagType(32, 32 * BLOCKS_IN_SECTOR);
    public final static TagType MFC_4k = new TagType(32 + 8, 32 * BLOCKS_IN_SECTOR + 8 * BLOCKS_IN_EXTENDED_SECTOR);

    private int mBlockCount;
    private int mSectorCount;

    /**
     * Get the TagType that has the specified number of sectors.
     * 
     * Assert false if a sector count that doesn't match any tag type is
     * supplied.
     * 
     * @param sectors
     * @return The tag type that matches the sector count requested
     */
    public static TagType getType(int sectors) {
        switch (sectors) {
        case 5:
            return MFC_MINI;
        case 16:
            return MFC_1k;
        case 32:
            return MFC_2k;
        case 32 + 8:
            return MFC_4k;
        }
        assert (false); // Should never get here
        return null;
    }

    /**
     * @return The total number of blocks for the tag type
     */
    public int getBlockCount() {
        return mBlockCount;
    }

    /**
     * @return The total number of sectors for the tag type
     */
    public int getSectorCount() {
        return mSectorCount;
    }

    /**
     * Private singleton constructor
     */
    private TagType(int sectors, int blocks) {
        mBlockCount = blocks;
        mSectorCount = sectors;
    }
}