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
import java.io.InputStream;

import org.graboid.DomainState.State;
import org.graboid.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.DownloadManager.Query;
import android.app.DownloadManager.Request;
import android.app.FragmentManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareClassic;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class GrabActivity extends Activity implements DomainState.IDomainStateListener {

    private DomainState mState;

    private ImageView mImageView;
    private TextView mTextView;

    FragmentManager mFragmentManager;
    static final String TASK_FRAGMENT_TAG = "task_fragment";

    private NfcAdapter mNfcAdapter;
    private PendingIntent mPendingIntent;
    private IntentFilter[] mFilters;
    private String[][] mTechLists;

    private DownloadManager mDownloadManager;
    private BroadcastReceiver mReceiver;
    private long mDownloadId = -1;
    static final private String DOWNLOAD_ID_TAG = "DOWNLOAD_ID_TAG";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.grab_layout);

        mImageView = (ImageView) findViewById(R.id.state_image_view);
        mTextView = (TextView) findViewById(R.id.text_view);

        // Create or restore state
        if (savedInstanceState == null) {
            // Create state
            try {
                mState = new DomainState("passwd".toCharArray(), getExternalFilesDir(null));
            } catch (Exception e) {
                // TODO Show some message here
                finish();
                return;
            }
        } else {
            // Restore state
            mState = savedInstanceState.getParcelable(DomainState.BUNDLE_TAG);
            mDownloadId = savedInstanceState.getLong(DOWNLOAD_ID_TAG);
        }

        // Update the domain state for the task fragment
        mFragmentManager = getFragmentManager();
        TaskFragment taskFragment = (TaskFragment) mFragmentManager.findFragmentByTag(TASK_FRAGMENT_TAG);
        if (taskFragment != null)
            taskFragment.setDomainState(mState);

        // Handle downloads (also registered in onResume - need both for intent
        // handling and normal callback)
        mDownloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        registerDownloadReceiver();

        // Sync with domain state
        mState.registerListener(this);
        refreshState();

        // Setup NFC intent processing
        initializeNFC();

        // Intents triggering the app start
        if (savedInstanceState == null)
            resolveIntent(getIntent());
    }

    @Override
    public void onNewIntent(Intent intent) {
        setIntent(intent);
        resolveIntent(intent);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(DomainState.BUNDLE_TAG, mState);
        outState.putLong(DOWNLOAD_ID_TAG, mDownloadId);
    }

    /*
     * @Override public void onStart() { super.onStart(); }
     */
    @Override
    public void onPause() {
        enableNfc(false);
        unregisterDownloadReceiver();
        mState.unregisterListener(this);
        super.onPause();
    }

    /*
     * @Override public void onStop() { super.onStop(); }
     */
    @Override
    public void onResume() {
        super.onResume();
        enableNfc(true);
        registerDownloadReceiver();

        // Sync with domain state
        mState.registerListener(this);
        refreshState();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.grab_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (mState != null) {
            menu.findItem(R.id.menu_clear_keys).setEnabled(mState.hasKeys());
            menu.findItem(R.id.menu_clear_tag).setEnabled(mState.hasTag());
            menu.findItem(R.id.menu_fuse_acl).setEnabled(mState.hasTag());
        }
        return super.onPrepareOptionsMenu(menu);
    }

    public void menuInfo(MenuItem item) {
        Intent intent = new Intent(this, InfoActivity.class);
        startActivity(intent);
    }

    public void menuFuseACL(MenuItem item) {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this)
                .setTitle(R.string.menu_fuse_acl_confirm_title).setMessage(R.string.menu_fuse_acl_confirm_message);
        dialogBuilder.setPositiveButton(R.string.menu_fuse_acl_confirm_ok, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                if (mState.hasTag()) {
                    mState.getTag().fuseACL();
                    Toast.makeText(GrabActivity.this, getString(R.string.tag_acl_fused), Toast.LENGTH_SHORT).show();
                }

            }
        }).setNegativeButton(R.string.menu_fuse_acl_confirm_cancel, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        }).create();
        dialogBuilder.create().show();

    }

    public void menuClearKeys(MenuItem item) {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this)
                .setTitle(R.string.menu_clear_keys_confirm_title);
        if (mState.getState() == State.CLEAN) {
            dialogBuilder.setMessage(R.string.menu_clear_keys_confirm_message);
        } else {
            dialogBuilder.setMessage(R.string.menu_clear_keys_and_tag_confirm_message);
        }
        dialogBuilder.setPositiveButton(R.string.menu_clear_keys_confirm_ok, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                mState.clearKeys();
            }
        }).setNegativeButton(R.string.menu_clear_keys_confirm_cancel, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        }).create();
        dialogBuilder.create().show();
    }

    public void menuClearTag(MenuItem item) {
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(R.string.menu_clear_tag_confirm_title)
                .setMessage(R.string.menu_clear_tag_confirm_message)
                .setPositiveButton(R.string.menu_clear_tag_confirm_ok, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        mState.clearTag();
                    }
                }).setNegativeButton(R.string.menu_clear_tag_confirm_cancel, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                }).create();
        dialog.show();
    }

    public void menuImportDefault(MenuItem item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.key_import_default_dialog_title).setItems(R.array.default_keyfile_desc,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        String localPath = getResources().getStringArray(R.array.default_keyfile_uri)[which];
                        Uri localUri = Uri.parse(localPath);
                        if (!mState.hasKeys()) {
                            importKeys(localUri);
                        } else {
                            importKeysWithConfirmation(localUri);
                        }
                    }
                });
        builder.create().show();
    }

    public void tap(View view) {
        mState.activate();
    }

    @Override
    public void StateChanged(DomainState newState) {
        mState = newState;
        refreshState();
    }

    private void refreshState() {
        invalidateOptionsMenu();

        if (!mState.hasKeys()) {
            mTextView.setText(R.string.nokey_text);
            mImageView.setImageResource(R.drawable.nokey);
            return;
        }

        switch (mState.getState()) {
        case CLEAN:
            mTextView.setText(R.string.clean_text);
            mImageView.setImageResource(R.drawable.clean);
            break;
        case RECORDING:
            mTextView.setText(R.string.recording_text);
            mImageView.setImageResource(R.drawable.recording);
            break;
        case LOADED:
            mTextView.setText(R.string.loaded_text);
            mImageView.setImageResource(R.drawable.loaded);
            break;
        case REPLAYING:
            mTextView.setText(R.string.replay_text);
            mImageView.setImageResource(R.drawable.replaying);
            break;
        default:
            break;
        }
    }

    private void initializeNFC() {
        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
        mPendingIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
        mFilters = new IntentFilter[] { new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED) };
        mTechLists = new String[][] { new String[] { MifareClassic.class.getName() } };
    }

    private void enableNfc(boolean enable) {
        if (mNfcAdapter == null)
            return;

        if (enable)
            mNfcAdapter.enableForegroundDispatch(this, mPendingIntent, mFilters, mTechLists);
        else
            mNfcAdapter.disableForegroundDispatch(this);
    }

    private void unregisterDownloadReceiver() {
        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
            mReceiver = null;
        }
    }

    private void registerDownloadReceiver() {
        if (mReceiver != null)
            return;

        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();

                if (mDownloadId == -1)
                    return; // Not waiting for download, ignore

                if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action))
                    return; // Only interested in completed downloads

                Query query = new Query();
                query.setFilterById(mDownloadId);
                Cursor c = mDownloadManager.query(query);
                if (c.moveToFirst()) {
                    if (DownloadManager.STATUS_SUCCESSFUL != c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS)))
                        return; // Only interested in successful downloads

                    Uri localUri = Uri.parse(c.getString(c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)));
                    if (!mState.hasKeys()) {
                        importKeys(localUri);
                    } else {
                        importKeysWithConfirmation(localUri);
                    }

                    mDownloadId = -1;
                }
            }
        };

        registerReceiver(mReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    private void resolveIntent(final Intent intent) {
        String action = intent.getAction();

        // Tag discovered
        if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)) {
            Parcelable tags = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            if (tags != null)
                tagDetected((Tag) tags);
        }

        // Keys file opened
        if (Intent.ACTION_VIEW.equals(action)) {
            final Uri uri = intent.getData();
            if (!mState.hasKeys()) {
                importKeys(uri);
            } else {
                importKeysWithConfirmation(uri);
            }
        }

        // Key file sent to activity
        if (Intent.ACTION_SEND.equals(action)) {
            // HTTP import
            final String strExtra = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (strExtra != null && strExtra.startsWith("http")) {
                Request request = new Request(Uri.parse(strExtra));
                mDownloadId = mDownloadManager.enqueue(request);
                Toast.makeText(GrabActivity.this, getString(R.string.key_import_download_started), Toast.LENGTH_LONG)
                        .show();
                return;
            }

            // File import
            final Uri uri = (Uri) intent.getExtras().get(Intent.EXTRA_STREAM);
            if (uri != null && uri.getScheme().equals("file")) {
                if (!mState.hasKeys()) {
                    importKeys(uri);
                } else {
                    importKeysWithConfirmation(uri);
                }
            }
        }
    }

    private void importKeys(final Uri uri) {
        try {
            KeyChain kc = null;

            if (uri.getScheme().equals("content")) {
                // Import from content stream
                InputStream inStream = getContentResolver().openInputStream(uri);
                kc = FileIO.importKeyChain(inStream);
            } else if (uri.getScheme().equals("file") && uri.getPath().contains("android_asset")) {
                // Import from asset file
                InputStream inStream = getAssets().open(uri.getLastPathSegment());
                kc = FileIO.importKeyChain(inStream);
            } else if (uri.getScheme().equals("file")) {
                // Import from local file
                File f = new File(uri.getPath());
                if (!f.exists())
                    throw new Exception("No such file");
                kc = FileIO.importKeyChain(f);
            } else {
                throw new Exception("Unsupported import scheme");
            }

            mState.setKeys(kc);
            Toast.makeText(GrabActivity.this, getString(R.string.key_import_success), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(GrabActivity.this, getString(R.string.err_key_import), Toast.LENGTH_SHORT).show();
        }
    }

    private void importKeysWithConfirmation(final Uri uri) {
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(R.string.key_import_confirm_title)
                .setMessage(R.string.key_import_confirm_message)
                .setPositiveButton(R.string.key_import_confirm_ok, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        importKeys(uri);
                    }
                }).setNegativeButton(R.string.key_import_confirm_cancel, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        Toast.makeText(GrabActivity.this, getString(R.string.key_import_aborted), Toast.LENGTH_SHORT)
                                .show();
                    }
                }).create();
        dialog.show();
    }

    private void tagDetected(Tag tag) {
        MifareClassic mifareTag = MifareClassic.get(tag);
        if (mifareTag == null) {
            Toast.makeText(this, getString(R.string.err_unknown_card_type), Toast.LENGTH_SHORT).show();
            return;
        }

        if (!mState.hasKeys()) {
            Toast.makeText(this, R.string.tag_no_keys, Toast.LENGTH_SHORT).show();
        } else if (mState.getState() == DomainState.State.CLEAN) {
            Toast.makeText(this, R.string.tag_not_recording, Toast.LENGTH_SHORT).show();
        } else if (mState.getState() == DomainState.State.LOADED) {
            Toast.makeText(this, R.string.tag_not_replaying, Toast.LENGTH_SHORT).show();
        } else if (mState.getState() == DomainState.State.RECORDING) {
            readTag(mifareTag);
        } else if (mState.getState() == DomainState.State.REPLAYING) {
            writeTag(mifareTag);
        }
    }

    public void readTag(MifareClassic tag) {
        // Test key compatibility
        if (tag.getSectorCount() > mState.getKeys().getSectorCount()) {
            Toast.makeText(this, R.string.err_to_few_keys, Toast.LENGTH_SHORT).show();
            return;
        }

        // Create a retaining fragment and start the task
        TaskFragment taskFragment = new TaskFragment();
        ReadMifareTask readTask = new ReadMifareTask(mState, taskFragment);
        taskFragment.initialize(mState, readTask, getString(R.string.read_tag_progress));
        taskFragment.show(mFragmentManager, TASK_FRAGMENT_TAG);
        readTask.execute(tag);
    }

    public void writeTag(MifareClassic tag) {
        // Test key compatibility
        if (tag.getSectorCount() > mState.getKeys().getSectorCount()) {
            Toast.makeText(this, R.string.err_to_few_keys, Toast.LENGTH_SHORT).show();
            return;
        }

        // Get UID from tag and cmp with
        byte[] newUID = tag.getTag().getId();
        byte[] recordedUID = mState.getTag().getUID();
        for (int i = 0; i < recordedUID.length; i++) {
            if (newUID[i] != recordedUID[i]) {
                Toast.makeText(this, R.string.err_wrong_uid, Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // Create a retaining fragment and start the task
        TaskFragment taskFragment = new TaskFragment();
        WriteMifareTask writeTask = new WriteMifareTask(mState, taskFragment);
        taskFragment.initialize(mState, writeTask, getString(R.string.write_tag_progress));
        taskFragment.show(mFragmentManager, TASK_FRAGMENT_TAG);
        writeTask.execute(tag);
    }
}
