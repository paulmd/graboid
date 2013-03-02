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
import android.os.AsyncTask;

public abstract class MifareTask<Result> extends AsyncTask<MifareClassic, Integer, Result> implements IMifareTask,
        MifareIO.IProgressListener {

    private DomainState mState;
    private TaskFragment mFragment;
    private Exception mError;

    protected MifareTask(DomainState state, TaskFragment fragment) {
        mState = state;
        mFragment = fragment;
    }

    public void setFragment(TaskFragment fragment) {
        mFragment = fragment;
    }

    public void setDomainState(DomainState state) {
        mState = state;
    }

    protected DomainState getDomainState() {
        return mState;
    }

    public Exception Error() {
        return mError;
    }

    @Override
    public void cancel() {
        super.cancel(false);
    }

    @Override
    protected void onPreExecute() {
    }

    /**
     * Must not refer to the activity from this or the processMifareTag
     * override.
     */
    @Override
    protected Result doInBackground(MifareClassic... tagParam) {
        if (tagParam == null || tagParam.length != 1 || tagParam[0].getType() != MifareClassic.TYPE_CLASSIC) {
            mError = new Exception("Invalid tag type");
            return null;
        }

        MifareClassic tag = tagParam[0];

        try {
            // Use publishProgress(0 .. 100) from the prcessMifareTag function
            return processMifareTag(tag);

        } catch (IOException e) {
            mError = e;
            return null;
        }
    }

    @Override
    public void publishProgress(int progress) {
        super.publishProgress(progress);
    }

    @Override
    protected void onProgressUpdate(Integer... progress) {
        mFragment.updateProgress(progress[0]);
    }

    @Override
    protected void onPostExecute(Result data) {
        postProcessResult(data);
        mFragment.taskFinished();
    }

    /**
     * Must not modify the domain state in this method.
     */
    protected abstract Result processMifareTag(MifareClassic mfTag) throws IOException;

    protected abstract void postProcessResult(Result res);
}