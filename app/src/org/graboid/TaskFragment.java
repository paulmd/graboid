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

import org.graboid.R;

import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

public class TaskFragment extends DialogFragment {

    private IMifareTask mTask;
    private ProgressBar mProgressBar;
    private TextView mProgressFractionText;
    private String mProgressTitle;

    public void initialize(DomainState state, IMifareTask task, String progressTitle) {
        mProgressTitle = progressTitle;
        mTask = task;
        mTask.setFragment(this);
        mTask.setDomainState(state);
    }

    public void setDomainState(DomainState state) {
        if (mTask != null)
            mTask.setDomainState(state);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setStyle(STYLE_NO_TITLE, 0); // remove title from dialog

        // Retain instance across activity creation / destruction
        setRetainInstance(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.task_fragment, container);

        mProgressBar = (ProgressBar) view.findViewById(R.id.task_fragment_progress_bar);
        mProgressFractionText = (TextView) view.findViewById(R.id.task_fragment_progress_fraction_text_view);

        TextView message = (TextView) view.findViewById(R.id.task_fragment_message_text_view);
        message.setText(mProgressTitle);

        getDialog().setCanceledOnTouchOutside(false);

        return view;
    }

    @Override
    public void onDestroyView() {
        // Don't dismiss on rotation
        if (getDialog() != null && getRetainInstance())
            getDialog().setDismissMessage(null);
        super.onDestroyView();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        if (mTask != null)
            mTask.cancel();
    }

    @Override
    public void onResume() {
        super.onResume();

        // Resuming when the task is already done? Then just dismiss
        if (mTask == null)
            dismiss();
    }

    // Callback from the task
    public void updateProgress(int percent) {
        mProgressBar.setProgress(percent);
        mProgressFractionText.setText(percent + "/ 100");
    }

    // Callback from the task
    public void taskFinished() {
        // Dismiss dialog when task completes (unless it is already dismissed)
        if (isResumed())
            dismiss();

        mTask = null;
    }
}