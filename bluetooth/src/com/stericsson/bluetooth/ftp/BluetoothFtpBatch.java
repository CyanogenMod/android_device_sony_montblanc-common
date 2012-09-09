/*
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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.util.Log;

import com.google.android.collect.Lists;

import java.io.File;
import java.util.ArrayList;

/**
 * This class stores information about a batch of FTP shares that should be
 * transferred in one session.
 */
/*There are a few cases:
 * 1. create a batch for a single file to send
 * 2. create a batch for multiple files to send
 * 3. add additional file(s) to existing batch to send
 * 4. create a batch for receive single file
 * 5. add additional file to existing batch to receive (this only happens as the server
 * session notify more files to receive)
 * 6. Cancel sending a single file
 * 7. Cancel sending a file from multiple files (implies cancel the transfer, rest of
 * the unsent files are also cancelled)
 * 8. Cancel receiving a single file from multiple files
 * 9. Cancel receiving a file (implies cancel the transfer, no additional files will be received)
 */

public class BluetoothFtpBatch {
    private static final String TAG = "BtFtpBatch";
    private static final boolean D = Constants.DEBUG;
    private static final boolean V = Constants.VERBOSE;

    public int mId;
    public int mStatus;

    public final long mTimestamp;
    public final int mDirection;
    public final BluetoothDevice mDestination;

    private BluetoothFtpBatchListener mListener;

    private final ArrayList<BluetoothFtpShareInfo> mShares;
    private final Context mContext;

    /**
     * An interface for notifying when BluetoothFtpTransferBatch is changed
     */
    public interface BluetoothFtpBatchListener {
        /**
         * Called to notify when a share is added into the batch
         * @param id , BluetoothFtpShareInfo.id
         */
        public void onShareAdded(int id);

        /**
         * Called to notify when a share is deleted from the batch
         * @param id , BluetoothFtpShareInfo.id
         */
        public void onShareDeleted(int id);

        /**
         * Called to notify when the batch is cancelled
         */
        public void onBatchCanceled();
    }

    /**
     * A batch is always created with at least one ShareInfo
     * @param context, Context
     * @param info, BluetoothFtpShareInfo
     */
	public BluetoothFtpBatch(Context context, BluetoothFtpShareInfo info) {
		BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
		mContext = context;
		mShares = Lists.newArrayList();
		mTimestamp = info.mTimestamp;
		mDirection = info.mDirection;
		mDestination = adapter.getRemoteDevice(info.mDestination);
		mStatus = Constants.BATCH_STATUS_PENDING;
		mShares.add(info);
		if (V) Log.v(TAG, "New Batch created for info " + info.mId);
	}

    /**
     * Add one share into the batch.
     */
    /* There are 2 cases:
     * 1. Service scans the databases and it's multiple send
     * 2. Service receives database update and know additional file should be received
     */
	public void addShare(BluetoothFtpShareInfo info) {
		mShares.add(info);
		if (mListener != null) {
			mListener.onShareAdded(info.mId);
		}
	}

    /**
     * Delete one share from the batch. Not used now.
     */
    /*It should only be called under requirement that cancel one single share, but not to
     * cancel the whole batch. Currently we assume "cancel" is to cancel whole batch.
     */
	public void deleteShare(BluetoothFtpShareInfo info) {
		if (info.mStatus == BluetoothShare.STATUS_RUNNING) {
			info.mStatus = BluetoothShare.STATUS_CANCELED;
			if (info.mDirection == BluetoothShare.DIRECTION_INBOUND && info.mFilename != null) {
				new File(info.mFilename).delete();
			}
		}
		if (mListener != null) {
			mListener.onShareDeleted(info.mId);
		}
	}

    /**
     * Cancel the whole batch.
     */
    /* 1) If the batch is running, stop the transfer
     * 2) Go through mShares list and mark all incomplete share as CANCELED status
     * 3) update ContentProvider for these cancelled transfer
     */
	public void cancelBatch() {
		if (V) Log.v(TAG, "batch " + this.mId + " is cancelled");
		if (mListener != null) {
			mListener.onBatchCanceled();
		}

		for (int i = mShares.size() - 1; i >= 0; i--) {
			BluetoothFtpShareInfo info = mShares.get(i);

			if (info.mStatus < 200) {
				if (info.mDirection == BluetoothShare.DIRECTION_INBOUND && info.mFilename != null) {
					new File(info.mFilename).delete();
				}
				if (V) Log.v(TAG, "Cancel batch for info " + info.mId);
				Constants.updateShareStatus(mContext, info.mId, BluetoothShare.STATUS_CANCELED);
			}
		}
		mShares.clear();
	}

    /** check if a specific share is in this batch */
	public boolean hasShare(BluetoothFtpShareInfo info) {
		return mShares.contains(info);
	}

    /** if this batch is empty */
	public boolean isEmpty() {
		return (mShares.size() == 0);
	}

	/**
	 * Register a listener for the batch change.
	 * @param listener listener object of BluetoothFtpBatchListener
	 */
	public void registerListern(BluetoothFtpBatchListener listener) {
		mListener = listener;
	}

    /**
     * Get the first pending ShareInfo of the batch
     * @return BluetoothFtpShareInfo, for the first pending share, or null if
     *         none exists
     */
	public BluetoothFtpShareInfo getPendingShare() {
		for (int i = 0; i < mShares.size(); i++) {
			BluetoothFtpShareInfo share = mShares.get(i);
			if (share.mStatus == BluetoothShare.STATUS_PENDING) {
				return share;
			}
		}
		return null;
	}
}
