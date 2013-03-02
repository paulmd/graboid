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

public class ReadMifareTask extends MifareTask<Tag> {
    public ReadMifareTask(DomainState state, TaskFragment fragment) {
        super(state, fragment);
    }

    @Override
    protected Tag processMifareTag(MifareClassic mfTag) throws IOException {
        MifareIO mio = new MifareIO(mfTag, getDomainState().getKeys(), this);
        return mio.read();
    }

    @Override
    protected void postProcessResult(Tag res) {
        DomainState ds = getDomainState();
        if (ds != null) {
            ds.setTag(res);
            ds.deActivate();
        }
    }
}
