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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import android.os.Parcel;
import android.os.Parcelable;

public class DomainState implements Parcelable {

    private List<IDomainStateListener> mListeners = new ArrayList<IDomainStateListener>();

    public static final String BUNDLE_TAG = "GRABOID_DOMAIN_STATE";

    public enum State {
        CLEAN, RECORDING, LOADED, REPLAYING
    }

    private Tag mTag = null;
    private KeyChain mKeys = null;
    private State mState;
    private char[] mPasswd;

    private FileIO mFileIO;

    public DomainState(char[] password, File extFileDir) throws Exception {
        assert (password != null);
        mPasswd = password;

        mFileIO = new FileIO(mPasswd, extFileDir);

        if (mFileIO.hasKeyChain())
            mKeys = mFileIO.loadKeyChain();

        if (mFileIO.hasTag()) {
            mTag = mFileIO.loadTag();
            mState = State.LOADED;
        } else {
            mState = State.CLEAN;
        }
    }

    public void activate() {
        if (!hasKeys())
            return;

        switch (mState) {
        case CLEAN:
            mState = State.RECORDING;
            break;
        case RECORDING:
            mState = State.CLEAN;
            break;
        case LOADED:
            mState = State.REPLAYING;
            break;
        case REPLAYING:
            mState = State.LOADED;
            break;
        default:
            break;
        }

        notifyListeners();
    }

    // Transition state from an active reading or writing state to a non active
    // state.
    public void deActivate() {
        switch (mState) {
        case RECORDING:
            mState = State.CLEAN;
            break;
        case REPLAYING:
            mState = State.LOADED;
            break;
        default:
            break;
        }

        notifyListeners();
    }

    public State getState() {
        return mState;
    }

    public boolean hasKeys() {
        return mKeys != null;
    }

    public void clearKeys() {
        clearTag();
        mKeys = null;
        mFileIO.deleteKeyChain();
        notifyListeners();
    }

    public KeyChain getKeys() {
        return mKeys;
    }

    public boolean setKeys(KeyChain newKeys) {
        clearKeys();
        mKeys = newKeys;

        try {
            mFileIO.saveKeyChain(mKeys);
        } catch (Exception e) {
            clearKeys();
            return false;
        } finally {
            notifyListeners();
        }

        return true;
    }

    public boolean hasTag() {
        return mTag != null;
    }

    public void clearTag() {
        mTag = null;
        mFileIO.deleteTag();
        mState = State.CLEAN;

        notifyListeners();
    }

    public Tag getTag() {
        return mTag;
    }

    public boolean setTag(Tag tag) {
        clearTag();
        mTag = tag;

        try {
            mFileIO.saveTag(mTag);
        } catch (Exception e) {
            clearTag();
            return false;
        } finally {
            notifyListeners();
        }

        mState = State.LOADED;

        return true;
    }

    // -- Parcelable impl --------------------------------------------

    // Private ctor. for use in deserialization
    private DomainState() {
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(mTag, flags);
        dest.writeParcelable(mKeys, flags);
        dest.writeInt(mState.ordinal());

        dest.writeInt(mPasswd.length);
        dest.writeCharArray(mPasswd);
        dest.writeString(mFileIO.getWorkingDir().getAbsolutePath());
    }

    public static final Parcelable.Creator<DomainState> CREATOR = new Parcelable.Creator<DomainState>() {
        public DomainState createFromParcel(Parcel in) {

            DomainState ds = new DomainState();

            ds.mTag = in.readParcelable(Tag.class.getClassLoader());
            ds.mKeys = in.readParcelable(KeyChain.class.getClassLoader());
            ds.mState = State.values()[in.readInt()];

            char[] password = new char[in.readInt()];
            in.readCharArray(password);

            File workingDir = new File(in.readString());
            try {
                ds.mFileIO = new FileIO(password, workingDir);
            } catch (Exception e) {
                return null;
            }

            return ds;
        }

        public DomainState[] newArray(int size) {
            return new DomainState[size];
        }
    };

    public void registerListener(IDomainStateListener listener) {
        if (!mListeners.contains(listener))
            mListeners.add(listener);
    }

    public void unregisterListener(IDomainStateListener listener) {
        mListeners.remove(listener);
    }

    private void notifyListeners() {
        for (IDomainStateListener listener : mListeners) {
            listener.StateChanged(this);
        }
    }

    public static interface IDomainStateListener {
        void StateChanged(DomainState newState);
    }
}
