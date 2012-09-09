/*
*
 * Copyright (c) 2008-2009, Motorola, Inc.
 * Copyright (C) ST-Ericsson SA 2010
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * - Neither the name of the Motorola, Inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.stericsson.bluetooth.ftp;

import com.stericsson.bluetooth.R;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.CharArrayBuffer;
import android.database.ContentObserver;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.media.MediaScannerConnection.MediaScannerConnectionClient;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.Process;
import android.provider.MediaStore;
import android.util.Log;

import com.google.android.collect.Lists;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.lang.IndexOutOfBoundsException;
import java.util.ArrayList;

import javax.obex.ObexTransport;

/**
 * Performs the background Bluetooth FTP transfer. It also starts thread to accept incoming FTP
 * connection.
 */
public class BluetoothFtpService extends Service {

	private static final boolean D = Constants.DEBUG;
	private static final boolean V = Constants.VERBOSE;

	private boolean userAccepted = false;

	private class BluetoothShareContentObserver extends ContentObserver {

		public BluetoothShareContentObserver() {
			super(new Handler());
		}

		@Override
		public void onChange(boolean selfChange) {
			if (V) Log.v(TAG, "ContentObserver received notification");
			updateFromProvider();
		}
	}

	private static final String TAG = "BtFtp Service";

	/** Observer to get notified when the content observer's data changes */
	private BluetoothShareContentObserver mObserver;

	/** Class to handle Notification Manager updates */
	private BluetoothFtpNotification mNotifier;

	private boolean mPendingUpdate;

	private UpdateThread mUpdateThread;

	private ArrayList<BluetoothFtpShareInfo> mShares;

	private ArrayList<BluetoothFtpBatch> mBatchs;

	private BluetoothFtpTransfer mTransfer;

	private BluetoothFtpTransfer mServerTransfer;

	private int mBatchId;

	/**
	 * Array used when extracting strings from content provider
	 */
	private CharArrayBuffer mOldChars;

	/**
	 * Array used when extracting strings from content provider
	 */
	private CharArrayBuffer mNewChars;

	private BluetoothAdapter mAdapter;

	private PowerManager mPowerManager;

	private BluetoothFtpRfcommListener mSocketListener;

	private boolean mListenStarted = false;

	private boolean mMediaScanInProgress;

	private int mIncomingRetries = 0;

	private ObexTransport mPendingConnection = null;

	private BluetoothFtpObexServerSession mServerSession;

	private static BluetoothDevice mRemoteDevice = null;
	private static BluetoothSocket mConnSocket = null;

	@Override
	public IBinder onBind(Intent arg0) {
		throw new UnsupportedOperationException("Cannot bind to Bluetooth FTP Service");
	}

	@Override
	public void onCreate() {
		super.onCreate();
		mAdapter = BluetoothAdapter.getDefaultAdapter();
		mSocketListener = new BluetoothFtpRfcommListener(mAdapter);
		mShares = Lists.newArrayList();
		mBatchs = Lists.newArrayList();
		mObserver = new BluetoothShareContentObserver();
		getContentResolver().registerContentObserver(BluetoothShare.CONTENT_URI, true, mObserver);
		mBatchId = 1;
		mNotifier = new BluetoothFtpNotification(this);
		mNotifier.mNotificationMgr.cancelAll();
		mNotifier.updateNotification();
		mNotifier.finishNotification();
		trimDatabase();
		InputStream fin = null;
		FileOutputStream fos = null;
		OutputStreamWriter osw = null;

		try {
			fin = openFileInput(Constants.ROOT_CONTAINER_FILE);
		} catch (FileNotFoundException e) {
			 Log.e(TAG, e.toString());
		}
		finally {
			try {
				if (fin != null) {
					fin.close();
				}
				else {
					fos = openFileOutput(Constants.ROOT_CONTAINER_FILE, MODE_PRIVATE);
					osw = new OutputStreamWriter(fos);
					osw.write(Constants.ROOT_CONTAINER_FILE_PATH);
				}
				if (osw != null) {
					osw.flush();
					osw.close();
				}
				if (fos != null) {
					fos.close();
				}
			} catch (IOException e) {
				Log.d(TAG, e.toString());
			}
		}

		registerReceiver(mBluetoothReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
		registerReceiver(mBluetoothReceiver, new IntentFilter(Constants.AUTHORIZE_ALLOWED_ACTION));
		registerReceiver(mBluetoothReceiver, new IntentFilter(Constants.AUTHORIZE_DISALLOWED_ACTION));
		registerReceiver(mBluetoothReceiver, new IntentFilter(Constants.INBOUND_FILE_TRANSFER_SCANNABLE_SUCCESS));
		registerReceiver(mBluetoothReceiver, new IntentFilter(BluetoothShare.DELETE_FILE_SUCCESS));

		if (V) BluetoothFtpPreference.getInstance(this).dump();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (V) Log.v(TAG, "Service onStartCommand");
		int retCode = super.onStartCommand(intent, flags, startId);
		if (retCode == START_STICKY) {
			if (mAdapter == null) {
				Log.w(TAG, "Local BT device is not enabled");
			} else {
				startListenerDelayed();
			}
			updateFromProvider();
		}
		return retCode;
	}

	private void startListenerDelayed() {
		if (!mListenStarted) {
			if (mAdapter.isEnabled()) {
				if (V) Log.v(TAG, "Starting RfcommListener in 9 seconds");
				mHandler.sendMessageDelayed(mHandler.obtainMessage(START_LISTENER), 9000);
				mListenStarted = true;
			}
		}
	}

	private static final int START_LISTENER = 1;

	private static final int MEDIA_SCANNED = 2;

	private static final int MEDIA_SCANNED_FAILED = 3;

	private static final int MSG_INCOMING_CONNECTION_RETRY = 4;

	private Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			if (V) Log.v(TAG, "Handler(): got msg = " + msg.what);
			switch (msg.what) {
			case START_LISTENER:
				if (mAdapter.isEnabled()) {
					startSocketListener();
					if (D) Log.d(TAG, "FTP started listening to the socket");
				}
				break;
			case MEDIA_SCANNED:
				if (V) Log.v(TAG, "Update mInfo.id " + msg.arg1 + " for data uri= " + msg.obj.toString());
				if (msg.arg1 > -1) {
					ContentValues updateValues = new ContentValues();
					Uri contentUri = Uri.parse(msg.obj.toString());
					updateValues.put(Constants.MEDIA_SCANNED, Constants.MEDIA_SCANNED_SCANNED_OK);
					updateValues.put(BluetoothShare.URI, msg.obj.toString()); // update
					updateValues.put(BluetoothShare.MIMETYPE, getContentResolver().getType(Uri.parse(msg.obj.toString())));
					getContentResolver().update(contentUri, updateValues, null, null);
				}
				synchronized (BluetoothFtpService.this) {
					mMediaScanInProgress = false;
					if (D) Log.d(TAG, "MEDIA_SCANNED : mMediaScanInProgress = false");
				}
				break;
			case MEDIA_SCANNED_FAILED:
				if (V) Log.v(TAG, "Update mInfo.id " + msg.arg1 + " for MEDIA_SCANNED_FAILED");
				if (msg.arg1 > -1) {
					ContentValues updateValues1 = new ContentValues();
					Uri contentUri1 = Uri.parse(BluetoothShare.CONTENT_URI + "/" + msg.arg1);
					updateValues1.put(Constants.MEDIA_SCANNED, Constants.MEDIA_SCANNED_SCANNED_FAILED);
					getContentResolver().update(contentUri1, updateValues1, null, null);
				}
				synchronized (BluetoothFtpService.this) {
					mMediaScanInProgress = false;
					if (D) Log.d(TAG, "MEDIA_SCANNED_FAILED : mMediaScanInProgress = false");
				}
				break;
			case Constants.MSG_USER_TIMEOUT:
				Intent authIntent = new Intent(Constants.USER_CONFIRM_TIMEOUT_ACTION);
				if (V) Log.v(TAG, " USER_TIMEOUT occured....");
				sendBroadcast(authIntent);
				cleanupConnSession();
				break;
			case Constants.MSG_AUTHORIZE_INCOMING_BTFTP_CONNECTION:
				mConnSocket = (BluetoothSocket)msg.obj;
				if (mConnSocket != null) mRemoteDevice = mConnSocket.getRemoteDevice();
				if (D) Log.d(TAG, "Authorize incoming connection...");
				if (mRemoteDevice != null) {
					boolean trust = mRemoteDevice.getTrustState();
					if (V) Log.v(TAG, "GetTrustState() = " + trust);
					if (trust && mConnSocket != null) {
						if (V) Log.v(TAG, "incomming connection accepted from: "
							+ getRemoteDeviceName() + " automatically as trusted device");
						setupConnMessage(mConnSocket);
					} else {
						startFtpActivity(Constants.AUTHORIZE_REQUEST_ACTION);
						Message lmsg = Message.obtain(mHandler);
						lmsg.what = Constants.MSG_USER_TIMEOUT;
						mHandler.sendMessageDelayed(lmsg, Constants.USER_CONFIRM_TIMEOUT_VALUE_IN_MS);
					}
				}
				break;
			case Constants.MSG_INCOMING_BTFTP_CONNECTION:
				if (D) Log.d(TAG, "Get incoming connection");
				if (V) Log.v(TAG, "listened one incoming connection");
				BluetoothFtpRfcommTransport transport = (BluetoothFtpRfcommTransport) msg.obj;
				/*
				 * Strategy for incoming connections:
				 * 1. If there is no ongoing transfer, no on-hold connection, start it.
				 * 2. If there is on-hold connection, reject directly
				 * 3. Used for TCP DEBUG. Not real strategy.
				 * 4. If there is ongoing transfer, hold it for 20 seconds(1 * seconds * 20 times)
				 */
				if (mBatchs.size() == 0 && mPendingConnection == null) {
					createServerSession(transport);
				} else {
					if (mPendingConnection != null) {
						Log.w(TAG, "FTP busy! Reject connection");
						try {
							transport.close();
						} catch (IOException e) {
							Log.e(TAG, "FTP close tranport error");
						}
					} else if (Constants.USE_TCP_DEBUG && !Constants.USE_TCP_SIMPLE_SERVER) {
						Log.i(TAG, "FTP Start Obex Server in TCP DEBUG mode");
						createServerSession(transport);
					} else {
						Log.i(TAG, "FTP busy! Retry after 1 second");
						mIncomingRetries = mIncomingRetries + 1;
						mPendingConnection = transport;
						Message msg1 = Message.obtain(mHandler);
						msg1.what = MSG_INCOMING_CONNECTION_RETRY;
						mHandler.sendMessageDelayed(msg1, 1000);
					}
				}
				break;
			case MSG_INCOMING_CONNECTION_RETRY:
				if (mBatchs.size() == 0) {
					Log.i(TAG, "Start Obex Server");
					createServerSession(mPendingConnection);
					mIncomingRetries = 0;
					mPendingConnection = null;
				} else {
					if (mIncomingRetries == 20) {
						Log.w(TAG, "Retried 20 seconds, reject connection");
						try {
							mPendingConnection.close();
						} catch (IOException e) {
							Log.e(TAG, "close tranport error");
						}
						mIncomingRetries = 0;
						mPendingConnection = null;
					} else {
						Log.i(TAG, "FTP busy! Retry after 1 second");
						mIncomingRetries = mIncomingRetries + 1;
						Message msg2 = Message.obtain(mHandler);
						msg2.what = MSG_INCOMING_CONNECTION_RETRY;
						mHandler.sendMessageDelayed(msg2, 1000);
					}
				}
				break;
			}
		}
	};

	private void startSocketListener() {
		mSocketListener.start(mHandler);
		if (V) Log.v(TAG, "RfcommListener started");
	}

	@Override
	public void onDestroy() {
		if (D) Log.d(TAG, "Service onDestroy");
		super.onDestroy();
		mNotifier.finishNotification();
		getContentResolver().unregisterContentObserver(mObserver);
		unregisterReceiver(mBluetoothReceiver);
		mSocketListener.stop();
	}

	/* Suppose we auto accept an incoming FTP connection */
	private void createServerSession(ObexTransport transport) {
		if (V) Log.v(TAG, "creating server session");
		mServerSession = new BluetoothFtpObexServerSession(this, transport);
		mServerSession.preStart();
		if (D) Log.d(TAG, "Get ServerSession " + mServerSession.toString()
				+ " for incoming connection" + transport.toString());
		if (V) Log.v(TAG, "Server session created successfully");
	}

	private final BroadcastReceiver mBluetoothReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			boolean bRemoveTimeoutMsg = true;

			if (V) Log.v(TAG, "Local receiver got intent: " + action);
			if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
				if (V) Log.v(TAG, "Bluetooth state changed");
				switch (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
				case BluetoothAdapter.STATE_ON:
					if (V) Log.v(TAG, "Event BT state on received");
					startSocketListener();
					break;
				case BluetoothAdapter.STATE_TURNING_OFF:
					if (V) Log.v(TAG, "Event BT state turning off received");
					mSocketListener.stop();
					mListenStarted = false;
					synchronized (BluetoothFtpService.this) {
						if (mUpdateThread == null) {
							stopSelf();
						}
					}
					break;
				}
			} else if (action.equals(Constants.AUTHORIZE_ALLOWED_ACTION)) {
				boolean bAlwaysAllowed = intent.getBooleanExtra(Constants.EXTRA_AUTHORIZE_ALWAYS_ALLOWED, false);
				if (D) Log.d(TAG, "AlwaysAllowed = " + bAlwaysAllowed);

				if (mRemoteDevice != null && mConnSocket != null) {
					if (bAlwaysAllowed) {
						boolean result = mRemoteDevice.setTrust(true);
						if (V) Log.v(TAG, "setTrust() result=" + result);
					}
					setupConnMessage(mConnSocket);
				}
			} else if (action.equals(Constants.AUTHORIZE_DISALLOWED_ACTION)) {
				cleanupConnSession();
			} else if (action.equals(Constants.INBOUND_FILE_TRANSFER_SCANNABLE_SUCCESS)) {
				String scanPath = intent.getStringExtra(Constants.SCANPATH);
				if (scanPath != null) {
					if (D) Log.d(TAG, Constants.SCANPATH  + ": " + scanPath);
					String mimeType = intent.getStringExtra(Constants.MIMETYPE);
					if (mimeType != null) {
						if (D) Log.d(TAG, Constants.MIMETYPE + ": "  + mimeType);
						scanFile(scanPath, mimeType);
					}
				}
			} else if (action.equals(BluetoothShare.DELETE_FILE_SUCCESS)) {
				String pathToDelete = intent.getStringExtra(Constants.DELETE_PATH);
				String mimeType = intent.getStringExtra(Constants.MIMETYPE);
				if (updateMediaStore(pathToDelete, mimeType)) {
					if (D) Log.d(TAG, "External Media store backend updated");
				} else {
					if (D) Log.d(TAG, "External Media store backend NOT updated");
				}
			} else {
				bRemoveTimeoutMsg = false;
			}
			if (bRemoveTimeoutMsg) {
				mHandler.removeMessages(Constants.MSG_USER_TIMEOUT);
			}
		}
	};

	private void updateFromProvider() {
		if (V) Log.v(TAG, "updateFromProvider called");
		synchronized (BluetoothFtpService.this) {
			mPendingUpdate = true;
			if (mUpdateThread == null) {
				mUpdateThread = new UpdateThread();
				mUpdateThread.start();
			}
		}
	}

	private class UpdateThread extends Thread {
		public UpdateThread() {
			super("Bluetooth Share Service");
		}

		@Override
		public void run() {
			Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

			boolean keepService = false;
			for (;;) {
				synchronized (BluetoothFtpService.this) {
					if (mUpdateThread != this) {
						throw new IllegalStateException(
								"multiple UpdateThreads in BluetoothFtpService");
					}
					if (V) Log.v(TAG, "pendingUpdate is " + mPendingUpdate
							+ " keepUpdateThread is " + keepService + " sListenStarted is "
							+ mListenStarted);
					if (!mPendingUpdate) {
						mUpdateThread = null;
						if (!keepService && !mListenStarted) {
							stopSelf();
							break;
						}
						mNotifier.updateNotification();
						mNotifier.finishNotification();
						return;
					}
					mPendingUpdate = false;
				}
				Cursor cursor = getContentResolver().query(BluetoothShare.CONTENT_URI, null, null,
						null, BluetoothShare._ID);

				if (cursor == null) {
					return;
				}

				cursor.moveToFirst();

				int arrayPos = 0;

				keepService = false;
				boolean isAfterLast = cursor.isAfterLast();

				int idColumn = cursor.getColumnIndexOrThrow(BluetoothShare._ID);
				/*
				 * Walk the cursor and the local array to keep them in sync. The key to the
				 * algorithm is that the ids are unique and sorted both in the cursor and in the
				 * array, so that they can be processed in order in both sources at the same time:
				 * at each step, both sources point to the lowest id that hasn't been processed from
				 * that source, and the algorithm processes the lowest id from those two
				 * possibilities. At each step: -If the array contains an entry that's not in the
				 * cursor, remove the entry, move to next entry in the array. -If the array contains
				 * an entry that's in the cursor, nothing to do, move to next cursor row and next
				 * array entry. -If the cursor contains an entry that's not in the array, insert a
				 * new entry in the array, move to next cursor row and next array entry.
				 */
				while (!isAfterLast || arrayPos < mShares.size()) {
					if (isAfterLast) {
						// We're beyond the end of the cursor but there's still
						// some
						// stuff in the local array, which can only be junk
						if (V) Log.v(TAG, "Array update: trimming " + mShares.get(arrayPos).mId
								+ " @ " + arrayPos);

						if (shouldScanFile(arrayPos)) {
							scanFile(null, arrayPos);
						}
						deleteShare(arrayPos); // this advances in the array
					} else {
						int id = cursor.getInt(idColumn);

						if (arrayPos == mShares.size()) {
							insertShare(cursor, arrayPos);
							if (V) Log.v(TAG, "Array update: inserting " + id + " @ " + arrayPos);
							if (shouldScanFile(arrayPos) && (!scanFile(cursor, arrayPos))) {
								keepService = true;
							}
							if (visibleNotification(arrayPos)) {
								keepService = true;
							}
							if (needAction(arrayPos)) {
								keepService = true;
							}

							++arrayPos;
							cursor.moveToNext();
							isAfterLast = cursor.isAfterLast();
						} else {
							int arrayId = mShares.get(arrayPos).mId;

							if (arrayId < id) {
								if (V) Log.v(TAG, "Array update: removing " + arrayId + " @ "
										+ arrayPos);
								if (shouldScanFile(arrayPos)) {
									scanFile(null, arrayPos);
								}
								deleteShare(arrayPos);
							} else if (arrayId == id) {
								// This cursor row already exists in the stored
								// array
								updateShare(cursor, arrayPos, userAccepted);
								if (shouldScanFile(arrayPos) && (!scanFile(cursor, arrayPos))) {
									keepService = true;
								}
								if (visibleNotification(arrayPos)) {
									keepService = true;
								}
								if (needAction(arrayPos)) {
									keepService = true;
								}

								++arrayPos;
								cursor.moveToNext();
								isAfterLast = cursor.isAfterLast();
							} else {
								// This cursor entry didn't exist in the stored
								// array
								if (V) Log.v(TAG, "Array update: appending " + id + " @ "
										+ arrayPos);
								insertShare(cursor, arrayPos);

								if (shouldScanFile(arrayPos) && (!scanFile(cursor, arrayPos))) {
									keepService = true;
								}
								if (visibleNotification(arrayPos)) {
									keepService = true;
								}
								if (needAction(arrayPos)) {
									keepService = true;
								}
								++arrayPos;
								cursor.moveToNext();
								isAfterLast = cursor.isAfterLast();
							}
						}
					}
				}

				mNotifier.updateNotification();

				cursor.close();
			}
		}

	}

	private void insertShare(Cursor cursor, int arrayPos) {
		BluetoothFtpShareInfo info = new BluetoothFtpShareInfo(
				cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare._ID)),
				cursor.getString(cursor.getColumnIndexOrThrow(BluetoothShare.URI)),
				cursor.getString(cursor.getColumnIndexOrThrow(BluetoothShare.FILENAME_HINT)),
				cursor.getString(cursor.getColumnIndexOrThrow(BluetoothShare._DATA)),
				cursor.getString(cursor.getColumnIndexOrThrow(BluetoothShare.MIMETYPE)),
				cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare.DIRECTION)),
				cursor.getString(cursor.getColumnIndexOrThrow(BluetoothShare.DESTINATION)),
				cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare.VISIBILITY)),
				cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare.USER_CONFIRMATION)),
				cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare.STATUS)),
				cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare.TOTAL_BYTES)),
				cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare.CURRENT_BYTES)),
				cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare.TIMESTAMP)),
				cursor.getInt(cursor.getColumnIndexOrThrow(Constants.MEDIA_SCANNED)) != Constants.MEDIA_SCANNED_NOT_SCANNED);

		if (V) {
			Log.v(TAG, "Service adding new entry");
			Log.v(TAG, "ID      : " + info.mId);
			// Log.v(TAG, "URI     : " + ((info.mUri != null) ? "yes" : "no"));
			Log.v(TAG, "URI     : " + info.mUri);
			Log.v(TAG, "HINT    : " + info.mHint);
			Log.v(TAG, "FILENAME: " + info.mFilename);
			Log.v(TAG, "MIMETYPE: " + info.mMimetype);
			Log.v(TAG, "DIRECTION: " + info.mDirection);
			Log.v(TAG, "DESTINAT: " + info.mDestination);
			Log.v(TAG, "VISIBILI: " + info.mVisibility);
			Log.v(TAG, "CONFIRM : " + info.mConfirm);
			Log.v(TAG, "STATUS  : " + info.mStatus);
			Log.v(TAG, "TOTAL   : " + info.mTotalBytes);
			Log.v(TAG, "CURRENT : " + info.mCurrentBytes);
			Log.v(TAG, "TIMESTAMP : " + info.mTimestamp);
			Log.v(TAG, "SCANNED : " + info.mMediaScanned);
		}

		mShares.add(arrayPos, info);

		/* Mark the info as failed if it's in invalid status */
		if (info.isObsolete()) {
			Constants.updateShareStatus(this, info.mId, BluetoothShare.STATUS_UNKNOWN_ERROR);
		}
		/*
		 * Add info into a batch. The logic is
		 * 1) Only add valid and readyToStart info
		 * 2) If there is no batch, create a batch and insert this transfer into batch, then run the batch
		 * 3) If there is existing batch and timestamp match, insert transfer into batch
		 * 4) If there is existing batch and timestamp does not match, create a new batch and put in queue
		 */

		if (info.isReadyToStart()) {
			if (info.mDirection == BluetoothShare.DIRECTION_OUTBOUND) {
				/* check if the file exists */
				InputStream i;
				try {
					i = getContentResolver().openInputStream(Uri.parse(info.mUri));
				} catch (FileNotFoundException e) {
					Log.e(TAG, "Can't open file for OUTBOUND info " + info.mId);
					Constants.updateShareStatus(this, info.mId, BluetoothShare.STATUS_BAD_REQUEST);
					return;
				} catch (SecurityException e) {
					Log.e(TAG, "Exception:" + e.toString() + " for OUTBOUND info " + info.mId);
					Constants.updateShareStatus(this, info.mId, BluetoothShare.STATUS_BAD_REQUEST);
					return;
				}

				try {
					i.close();
				} catch (IOException ex) {
					Log.e(TAG, "IO error when close file for OUTBOUND info " + info.mId);
					return;
				}
			}
			if (mBatchs.size() == 0) {
				BluetoothFtpBatch newBatch = new BluetoothFtpBatch(this, info);
				newBatch.mId = mBatchId;
				mBatchId++;
				mBatchs.add(newBatch);
				if (info.mDirection == BluetoothShare.DIRECTION_OUTBOUND) {
					if (V) Log.v(TAG, "Service create new Batch " + newBatch.mId
							+ " for OUTBOUND info " + info.mId);
					mTransfer = new BluetoothFtpTransfer(this, mPowerManager, newBatch);
				} else if (info.mDirection == BluetoothShare.DIRECTION_INBOUND) {
					if (V) Log.v(TAG, "Service create new Batch " + newBatch.mId
							+ " for INBOUND info " + info.mId);
					mServerTransfer = new BluetoothFtpTransfer(this, mPowerManager, newBatch,
							mServerSession);
				}

				if (info.mDirection == BluetoothShare.DIRECTION_OUTBOUND && mTransfer != null) {
					if (V) Log.v(TAG, "Service start transfer new Batch " + newBatch.mId
							+ " for info " + info.mId);
					mTransfer.start();
				} else if (info.mDirection == BluetoothShare.DIRECTION_INBOUND
						&& mServerTransfer != null) {
					if (V) Log.v(TAG, "Service start server transfer new Batch " + newBatch.mId
							+ " for info " + info.mId);
					mServerTransfer.start();
				}

			} else {
				int i = findBatchWithTimeStamp(info.mTimestamp);
				if (i != -1) {
					if (V) Log.v(TAG, "Service add info " + info.mId + " to existing batch "
							+ mBatchs.get(i).mId);
					mBatchs.get(i).addShare(info);
				} else {
					// There is ongoing batch
					BluetoothFtpBatch newBatch = new BluetoothFtpBatch(this, info);
					newBatch.mId = mBatchId;
					mBatchId++;
					mBatchs.add(newBatch);
					if (V) Log.v(TAG, "Service add new Batch " + newBatch.mId + " for info "
							+ info.mId);
					if (Constants.USE_TCP_DEBUG && !Constants.USE_TCP_SIMPLE_SERVER) {
						// only allow concurrent serverTransfer in debug mode
						if (info.mDirection == BluetoothShare.DIRECTION_INBOUND) {
							if (V) Log.v(TAG, "TCP_DEBUG start server transfer new Batch "
									+ newBatch.mId + " for info " + info.mId);
							mServerTransfer = new BluetoothFtpTransfer(this, mPowerManager,
									newBatch, mServerSession);
							mServerTransfer.start();
						}
					}
				}
			}
		}
	}

	private void updateShare(Cursor cursor, int arrayPos, boolean userAccepted) {
		if (V) Log.v(TAG, "updateShare called");
		BluetoothFtpShareInfo info = mShares.get(arrayPos);
		int statusColumn = cursor.getColumnIndexOrThrow(BluetoothShare.STATUS);

		info.mId = cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare._ID));
		info.mUri = stringFromCursor(info.mUri, cursor, BluetoothShare.URI);
		info.mHint = stringFromCursor(info.mHint, cursor, BluetoothShare.FILENAME_HINT);
		info.mFilename = stringFromCursor(info.mFilename, cursor, BluetoothShare._DATA);
		info.mMimetype = stringFromCursor(info.mMimetype, cursor, BluetoothShare.MIMETYPE);
		info.mDirection = cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare.DIRECTION));
		info.mDestination = stringFromCursor(info.mDestination, cursor, BluetoothShare.DESTINATION);
		int newVisibility = cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare.VISIBILITY));

		boolean confirmed = false;
		int newConfirm = cursor.getInt(cursor
				.getColumnIndexOrThrow(BluetoothShare.USER_CONFIRMATION));

		if (info.mVisibility == BluetoothShare.VISIBILITY_VISIBLE
				&& newVisibility != BluetoothShare.VISIBILITY_VISIBLE
				&& (BluetoothShare.isStatusCompleted(info.mStatus) || newConfirm == BluetoothShare.USER_CONFIRMATION_PENDING)) {
			mNotifier.mNotificationMgr.cancel(info.mId);
		}

		info.mVisibility = newVisibility;

		if (info.mConfirm == BluetoothShare.USER_CONFIRMATION_PENDING
				&& newConfirm != BluetoothShare.USER_CONFIRMATION_PENDING) {
			confirmed = true;
		}
		info.mConfirm = cursor.getInt(cursor
				.getColumnIndexOrThrow(BluetoothShare.USER_CONFIRMATION));
		int newStatus = cursor.getInt(statusColumn);

		if (!BluetoothShare.isStatusCompleted(info.mStatus)
				&& BluetoothShare.isStatusCompleted(newStatus)) {
			mNotifier.mNotificationMgr.cancel(info.mId);
		}

		info.mStatus = newStatus;
		info.mTotalBytes = cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare.TOTAL_BYTES));
		info.mCurrentBytes = cursor.getInt(cursor
				.getColumnIndexOrThrow(BluetoothShare.CURRENT_BYTES));
		info.mTimestamp = cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare.TIMESTAMP));
		info.mMediaScanned = (cursor.getInt(cursor.getColumnIndexOrThrow(Constants.MEDIA_SCANNED)) != Constants.MEDIA_SCANNED_NOT_SCANNED);

		if (confirmed) {
			if (V) Log.v(TAG, "Service handle info " + info.mId + " confirmed");
			/* Inbounds transfer get user confirmation, so we start it */
			int i = findBatchWithTimeStamp(info.mTimestamp);
			if (i != -1) {
				BluetoothFtpBatch batch = mBatchs.get(i);
				if (mServerTransfer != null && batch.mId == mServerTransfer.getBatchId()) {
					mServerTransfer.setConfirmed();
				}
			}
		}
		int i = findBatchWithTimeStamp(info.mTimestamp);
		if (i != -1) {
			BluetoothFtpBatch batch = mBatchs.get(i);
			if (batch.mStatus == Constants.BATCH_STATUS_FINISHED
					|| batch.mStatus == Constants.BATCH_STATUS_FAILED) {

				if (V) Log.v(TAG, "Batch.mStatus is " + batch.mStatus);

				if (batch.mDirection == BluetoothShare.DIRECTION_OUTBOUND) {
					if (mTransfer == null) {
						Log.e(TAG, "Unexpected error! mTransfer is null");
					} else if (batch.mId == mTransfer.getBatchId()) {
						mTransfer.stop();
					} else {
						Log.e(TAG, "Unexpected error! batch id " + batch.mId
								+ " doesn't match mTransfer id " + mTransfer.getBatchId());
					}
					mTransfer = null;
				} else {
					if (mServerTransfer == null) {
						Log.e(TAG, "Unexpected error! mServerTransfer is null");
					} else if (batch.mId == mServerTransfer.getBatchId()) {
						mServerTransfer.stop();
					} else {
						Log.e(TAG, "Unexpected error! batch id " + batch.mId
								+ " doesn't match mServerTransfer id "
								+ mServerTransfer.getBatchId());
					}
					mServerTransfer = null;
				}
				removeBatch(batch);
			}
		}
	}

	/**
	 * Removes the local copy of the info about a share.
	 */
	private void deleteShare(int arrayPos) {
		BluetoothFtpShareInfo info = mShares.get(arrayPos);

		/*
		 * Delete arrayPos from a batch. The logic is
		 * 1) Search existing batch for the info
		 * 2) Cancel the batch
		 * 3) If the batch become empty delete the batch
		 */
		int i = findBatchWithTimeStamp(info.mTimestamp);
		if (i != -1) {
			BluetoothFtpBatch batch = mBatchs.get(i);
			if (batch.hasShare(info)) {
				if (V) Log.v(TAG, "Service cancel batch for share " + info.mId);
				batch.cancelBatch();
			}
			if (batch.isEmpty()) {
				if (V) Log.v(TAG, "Service remove batch  " + batch.mId);
				removeBatch(batch);
			}
		}
		mShares.remove(arrayPos);
	}

	private String stringFromCursor(String old, Cursor cursor, String column) {
		if (V) Log.v(TAG, "stringFromCursor called");
		int index = cursor.getColumnIndexOrThrow(column);
		if (old == null) {
			return cursor.getString(index);
		}
		if (mNewChars == null) {
			mNewChars = new CharArrayBuffer(128);
		}
		cursor.copyStringToBuffer(index, mNewChars);
		int length = mNewChars.sizeCopied;
		if (length != old.length()) {
			return cursor.getString(index);
		}
		if (mOldChars == null || mOldChars.sizeCopied < length) {
			mOldChars = new CharArrayBuffer(length);
		}
		char[] oldArray = mOldChars.data;
		char[] newArray = mNewChars.data;
		old.getChars(0, length, oldArray, 0);
		for (int i = length - 1; i >= 0; --i) {
			if (oldArray[i] != newArray[i]) {
				return new String(newArray, 0, length);
			}
		}
		return old;
	}

	private int findBatchWithTimeStamp(long timestamp) {
		for (int i = mBatchs.size() - 1; i >= 0; i--) {
			if (mBatchs.get(i).mTimestamp == timestamp) {
				return i;
			}
		}
		return -1;
	}

	private void removeBatch(BluetoothFtpBatch batch) {
		if (V) Log.v(TAG, "Remove batch " + batch.mId);
		mBatchs.remove(batch);
		BluetoothFtpBatch nextBatch;
		if (mBatchs.size() > 0) {
			for (int i = 0; i < mBatchs.size(); i++) {
				nextBatch = mBatchs.get(i);
				if (nextBatch.mStatus == Constants.BATCH_STATUS_RUNNING) {
					// we have a running batch
					return;
				} else {
					// just finish a transfer, start pending outbound transfer
					if (nextBatch.mDirection == BluetoothShare.DIRECTION_OUTBOUND) {
						if (V) Log.v(TAG, "Start pending outbound batch " + nextBatch.mId);
						mTransfer = new BluetoothFtpTransfer(this, mPowerManager, nextBatch);
						mTransfer.start();
						return;
					} else if (nextBatch.mDirection == BluetoothShare.DIRECTION_INBOUND
							&& mServerSession != null) {
						// have to support pending inbound transfer
						// if an outbound transfer and incoming socket happens
						// together
						if (V) Log.v(TAG, "Start pending inbound batch " + nextBatch.mId);
						mServerTransfer = new BluetoothFtpTransfer(this, mPowerManager, nextBatch,
								mServerSession);
						mServerTransfer.start();
						if (nextBatch.getPendingShare() != null) {
							if (nextBatch.getPendingShare().mConfirm == BluetoothShare.USER_CONFIRMATION_CONFIRMED) {
								mServerTransfer.setConfirmed();
							}
						}
						return;
					}
				}
			}
		}
	}

	private boolean needAction(int arrayPos) {
		BluetoothFtpShareInfo info = mShares.get(arrayPos);
		if (BluetoothShare.isStatusCompleted(info.mStatus)) {
			return false;
		}
		return true;
	}

	private boolean visibleNotification(int arrayPos) {
		BluetoothFtpShareInfo info = mShares.get(arrayPos);
		return info.hasCompletionNotification();
	}

	private boolean scanFile(Cursor cursor, int arrayPos) {
		BluetoothFtpShareInfo info = mShares.get(arrayPos);
		synchronized (BluetoothFtpService.this) {
			if (D) Log.d(TAG, "Scanning file " + info.mFilename);
			if (!mMediaScanInProgress) {
				mMediaScanInProgress = true;
				new MediaScannerNotifier(this, info, mHandler);
				return true;
			} else {
				return false;
			}
		}
	}

	private boolean shouldScanFile(int arrayPos) {
		BluetoothFtpShareInfo info = mShares.get(arrayPos);
		return BluetoothShare.isStatusSuccess(info.mStatus)
				&& info.mDirection == BluetoothShare.DIRECTION_INBOUND && !info.mMediaScanned;
	}

	private boolean scanFile(String filePathToScan, String fileMimeType) {
		synchronized (BluetoothFtpService.this) {
			if (D) Log.d(TAG, "Scanning file: " + filePathToScan);
			if (!mMediaScanInProgress) {
				mMediaScanInProgress = true;
				new MediaScannerNotifier(this, filePathToScan, fileMimeType, mHandler);
				return true;
			} else {
				return false;
			}
		}
	}

	private void trimDatabase() {
		Cursor cursor = getContentResolver().query(BluetoothShare.CONTENT_URI,
				new String[] { BluetoothShare._ID }, BluetoothShare.STATUS + " >= '200'", null,
				BluetoothShare._ID);
		if (cursor == null) {
			// This isn't good - if we can't do basic queries in our database,
			// nothing's gonna work
			Log.e(TAG, "null cursor in trimDatabase");
			return;
		}
		if (cursor.moveToFirst()) {
			int numDelete = cursor.getCount() - Constants.MAX_RECORDS_IN_DATABASE;
			int columnId = cursor.getColumnIndexOrThrow(BluetoothShare._ID);
			while (numDelete > 0) {
				getContentResolver().delete(
						ContentUris.withAppendedId(BluetoothShare.CONTENT_URI, cursor
								.getLong(columnId)), null, null);
				if (!cursor.moveToNext()) {
					break;
				}
				numDelete--;
			}
		}
		cursor.close();
	}

	private void startFtpActivity(String action) {
		if (mRemoteDevice != null) {
			Intent authIntent = new Intent();
			authIntent.setClass(getApplicationContext(), BluetoothFtpActivity.class);
			authIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			authIntent.setAction(action);
			getApplicationContext().startActivity(authIntent);
		}
	}

	public static String getRemoteDeviceName() {
		if (mRemoteDevice != null) {
			return mRemoteDevice.getName();
		} else {
			return null;
		}
	}

	private static void cleanupConnSession()
	{
		try {
			if (mConnSocket != null) mConnSocket.close();
			} catch (IOException ex) {
				Log.e(TAG, "CloseSocket error: " + ex);
			}
		mRemoteDevice = null;
		mConnSocket = null;
	}

	private void setupConnMessage(BluetoothSocket sBTSocket)
	{
		BluetoothFtpRfcommTransport transport = new BluetoothFtpRfcommTransport(sBTSocket);
		Message lmsg = Message.obtain(mHandler);
		lmsg.what = Constants.MSG_INCOMING_BTFTP_CONNECTION;
		lmsg.obj = transport;
		mHandler.sendMessage(lmsg);
		if (D) Log.d(TAG, "MSG_INCOMING_BTFTP_CONNECTION sent...");
	}

	private static class MediaScannerNotifier implements MediaScannerConnectionClient {

		private MediaScannerConnection mConnection;

		private BluetoothFtpShareInfo mInfo;
		private String mFileToScan, mFileMimeType;

		private Context mContext;

		private Handler mCallback;

		public MediaScannerNotifier(Context context, BluetoothFtpShareInfo info, Handler handler) {
			mContext = context;
			mInfo = info;
			mFileToScan = null;
			mFileMimeType = null;
			mCallback = handler;
			mConnection = new MediaScannerConnection(mContext, this);
			if (D) Log.d(TAG, "Connecting to MediaScannerConnection: triggered by BluetoothFtpShareInfo");
			mConnection.connect();
		}

		public MediaScannerNotifier(Context context, String fileToScan, String mimeType, Handler handler) {
			mContext = context;
			mInfo = null;
			mFileToScan = fileToScan;
			mFileMimeType = mimeType;
			mCallback = handler;
			mConnection = new MediaScannerConnection(mContext, this);
			if (D) Log.d(TAG, "Connecting to MediaScannerConnection: triggered by mFile2Scan");
			mConnection.connect();
		}

		public void onMediaScannerConnected() {
			if (D) Log.d(TAG, "MediaScannerConnection onMediaScannerConnected");
			if (mInfo != null) {
				mConnection.scanFile(mInfo.mFilename, mInfo.mMimetype);
				if (D) Log.d(TAG, "onMediaScannerConnected: triggered by mInfo");
			} else {
				mConnection.scanFile(mFileToScan, mFileMimeType);
				if (D) Log.d(TAG, "onMediaScannerConnected: triggered by mFile2Scan");
			}
		}

		public void onScanCompleted(String path, Uri uri) {
			try {
				if (V) {
					Log.v(TAG, "MediaScannerConnection onScanCompleted");
					Log.v(TAG, "MediaScannerConnection path is " + path);
					Log.v(TAG, "MediaScannerConnection Uri is " + uri);
				}
				Message msg = Message.obtain();
				msg.setTarget(mCallback);
				msg.arg1 = (mInfo != null) ? mInfo.mId : -1;
				msg.what = (uri != null) ? MEDIA_SCANNED : MEDIA_SCANNED_FAILED;
				msg.obj = (uri != null) ? uri : "".toString();
				msg.sendToTarget();
			} catch (Exception ex) {
				Log.e(TAG, "!!!MediaScannerConnection exception: " + ex);
			} finally {
				if (V) Log.v(TAG, "MediaScannerConnection disconnect");
				mConnection.disconnect();
			}
		}
	}

	private boolean updateMediaStore(String path, String mimeType) {
		Cursor aCursor = null;
		boolean retUpdated = false;
		Uri uriMediaStoreURI = null;

		if (Constants.mimeTypeMatches(mimeType, Constants.MIME_IMAGE_TYPE)) {
			uriMediaStoreURI = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
		} else if (Constants.mimeTypeMatches(mimeType, Constants.MIME_AUDIO_TYPE)) {
			uriMediaStoreURI = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
		} else if (Constants.mimeTypeMatches(mimeType, Constants.MIME_VIDEO_TYPE)) {
			uriMediaStoreURI = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
		}

		if (uriMediaStoreURI != null) {
			if (D) Log.d(TAG, uriMediaStoreURI + "\r\n" + "media file path: " + path);

			try {
				aCursor = getContentResolver().query(uriMediaStoreURI,
										new String[] {MediaStore.MediaColumns.DATA, "_id"},
										MediaStore.MediaColumns.DATA + " = ?",
										new String[] {path}, null);
				int aCount = aCursor.getCount();
				if (aCount > 0 && aCursor.moveToNext()) {
					long rec_id = aCursor.getInt(1);
					if (D) {
						String data = aCursor.getString(0);
						Log.d(TAG,"media file record: " + data + "\r\n" + "URI = " + uriMediaStoreURI + "/" + rec_id);
					}
					/* removing record from images and thumbnails table as well */
					getContentResolver().delete(Uri.parse(uriMediaStoreURI + "/" + rec_id), null, null);
					getContentResolver().notifyChange(Uri.parse(uriMediaStoreURI + "/" + rec_id), null);
					retUpdated = true;
				}
			} catch (Exception e) {
				Log.e(TAG, "!Handling BluetoothShare.DELETE_FILE_SUCCESS got exception: " + e);
			} finally {
				if (aCursor != null ) aCursor.close();
			}
		}

		return retUpdated;
	}
}
