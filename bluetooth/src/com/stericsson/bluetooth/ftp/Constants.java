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

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.webkit.MimeTypeMap;

import java.io.IOException;
import java.util.regex.Pattern;

import javax.obex.HeaderSet;

/**
 * Bluetooth FTP internal constants definition
 */
public class Constants {

	/** Tag used for debugging/logging */
    public static final String TAG = "BluetoothFtp";

    /**
     * The intent that gets sent when the service must wake up for a retry Note:
     * only retry Outbound transfer
     */
    public static final String ACTION_RETRY = "android.btftp.intent.action.RETRY";

    /** the intent that gets sent when clicking a successful transfer */
    public static final String ACTION_OPEN = "android.btftp.intent.action.OPEN";

    /** the intent that gets sent when clicking an incomplete/failed transfer */
    public static final String ACTION_LIST = "android.btftp.intent.action.LIST";

    /**
     * the intent that gets sent when deleting the notification of a completed
     * transfer
     */
    public static final String ACTION_HIDE = "android.btftp.intent.action.HIDE";

    /**
     * the intent that gets sent when clicking a incoming file confirm
     * notification
     */
    public static final String ACTION_INCOMING_FILE_CONFIRM = "android.btftp.intent.action.CONFIRM";

    public static final String THIS_PACKAGE_NAME = "com.stericsson.bluetooth";

    /**
     * The column that is used to remember whether the media scanner was invoked
     */
    public static final String MEDIA_SCANNED = "scanned";

    public static final int MEDIA_SCANNED_NOT_SCANNED = 0;

    public static final int MEDIA_SCANNED_SCANNED_OK = 1;

    public static final int MEDIA_SCANNED_SCANNED_FAILED = 2;

	/**
	 * The MIME type(s) of we could share to other device.
	 */
    public static final String[] ACCEPTABLE_SHARE_OUTBOUND_TYPES = new String[] {
        "image/*",
    };

	/**
	 * The MIME type(s) of we could not share to other device.
	 */
    public static final String[] UNACCEPTABLE_SHARE_OUTBOUND_TYPES = new String[] {
        "virus/*",
    };

	/**
	 * The MIME type(s) of we could accept from other device. This is in essence a "white list" of
	 * acceptable types. Today, restricted to images, audio, video and certain text types.
	 */
    public static final String[] ACCEPTABLE_SHARE_INBOUND_TYPES = new String[] {
        "image/*",
        "video/*",
        "audio/*",
        "text/plain",
        "text/html",
    };

    /**
     * The MIME type(s) used during check/resolving mime type when object is about to be deleted
     */
    public static final String MIME_IMAGE_TYPE = "image/*";
    public static final String MIME_AUDIO_TYPE = "audio/*";
    public static final String MIME_VIDEO_TYPE = "video/*";

    /**
	 * The MIME type(s) of we could not accept from other device.
	 */
    public static final String[] UNACCEPTABLE_SHARE_INBOUND_TYPES = new String[] {
        "text/x-vcalendar",
        "text/x-vcard",
    };

	/** Where we store Bluetooth received files on the external storage */
	public static final String DEFAULT_STORE_SUBDIR = "/bluetooth";

	/**
	 * Debug level logging
	 */
	public static final boolean DEBUG = false;

	/**
	 * Verbose level logging
	 */
	public static final boolean VERBOSE = false;

	/** use TCP socket instead of Rfcomm Socket to develop */
	public static final boolean USE_TCP_DEBUG = false;

	/** use simple TCP server started from TestActivity */
	public static final boolean USE_TCP_SIMPLE_SERVER = false;

	public static final int TCP_DEBUG_PORT = 21;

	/** represents default ftp root directory */
	public static String ROOT_FOLDER_DIR = "/usr";

	/** represents path separator */
	public static final String PATH_SEPARATOR = "/";

	/** constants defining remote client requests and permissions */
	public static final int RESPONSE_OK = 0;

	public static final int FOLDER_BROWSING_REQUEST = 1;

	public static final int IS_FILE = 2;

	public static final int IS_FOLDER = 3;

	public static final int FILE_ACCESS_DENIED = 4;

	public static final int FOLDER_DELETED = 5;

	public static final int FOLDER_NOT_EMPTY = 6;

	public static final int FOLDER_CREATE_REQUEST = 7;

	public static final int FOLDER_CREATED_SUCCESSFULLY = 8;

	public static final int FILE_PULL_REQUEST = 9;

	public static final int FOLDER_HAS_NON_EXTN_FILE = 10;

	public static final int DIR_READ_ONLY = 11;

	public static final int DIR_NOT_READ_ONLY = 12;

	public static final int INVALID_PATH_REQ = 13;

	public static final int BAD_FILE_NAME = 14;


	/** use emulator to debug */
	public static final boolean USE_EMULATOR_DEBUG = false;

	public static final int MAX_RECORDS_IN_DATABASE = 20;

	public static final int BATCH_STATUS_PENDING = 0;

	public static final int BATCH_STATUS_RUNNING = 1;

	public static final int BATCH_STATUS_FINISHED = 2;

	public static final int BATCH_STATUS_FAILED = 3;

	public static final String BLUETOOTHFTP_NAME_PREFERENCE = "btftp_names";

	public static final String BLUETOOTHFTP_CHANNEL_PREFERENCE = "btftp_channels";

	public static String filename_SEQUENCE_SEPARATOR = "-";

	public static final String ROOT_CONTAINER_FILE = "root.config";

	public static String ROOT_CONTAINER_FILE_PATH = "/sdcard";

	/**
	 * Internal server handler control messages
	 */
	public static final int MSG_INCOMING_BTFTP_CONNECTION = 100;
	public static final int MSG_AUTHORIZE_INCOMING_BTFTP_CONNECTION = 101;
	public static final int MSG_USER_TIMEOUT = 102;
	public static final int MSG_AUTH_TIMEOUT = 103;
	/**
	 * Timeout value in ms to release authorization hook
	 */
	public static final int USER_CONFIRM_TIMEOUT_VALUE_IN_MS = 30000;
	/**
	* Intent indicating incoming connection request which is sent to
	* BluetoothFtpActivity
	*/
	public static final String AUTHORIZE_REQUEST_ACTION = "com.stericsson.bluetooth.ftp.authorizerequest";

	/**
	* Intent indicating incoming connection request accepted by user which is
	* sent from BluetoothFtpActivity
	*/
	public static final String AUTHORIZE_ALLOWED_ACTION = "com.stericsson.bluetooth.ftp.authorizeallowed";

	/**
	* Intent indicating incoming connection request denied by user which is
	* sent from BluetoothFtpActivity
	*/
	public static final String AUTHORIZE_DISALLOWED_ACTION = "com.stericsson.bluetooth.ftp.authorizedisallowed";

	/**
	* Intent indicating timeout for user confirmation, which is sent to
	* BluetoothFtpActivity
	*/
	public static final String USER_CONFIRM_TIMEOUT_ACTION = "com.stericsson.bluetooth.ftp.userconfirmtimeout";

	/**
	* Intent Extra name indicating always allowed which is sent from
	* BluetoothFtpActivity
	*/
	public static final String EXTRA_AUTHORIZE_ALWAYS_ALLOWED = "com.stericsson.bluetooth.ftp.alwaysallowed";

	/**
	* Intent indicating media file was uploaded, sent to BluetoothFtpService
	*/
	public static final String INBOUND_FILE_TRANSFER_SCANNABLE_SUCCESS = "com.stericsson.bluetooth.ftp.INBOUND_FILE_TRANSFER_SCANNABLE_SUCCESS";

	/**
	* Extra info carried with intent INBOUND_FILE_TRANSFER_SCANNABLE_SUCCESS
	*/
	public static final String SCANPATH = "SCANNABLE_SCANPATH";

	/**
	* Extra info carried with intent INBOUND_FILE_TRANSFER_SCANNABLE_SUCCESS
	*/
	public static final String MIMETYPE = "SCANNABLE_MIMETYPE";
	public static final String DELETE_PATH = "DELETE_PATH";
	public static final int MSG_FILE_DELETED = 1;


	public static void updateShareStatus(Context context, int id, int status) {
		Uri contentUri = Uri.parse(BluetoothShare.CONTENT_URI + "/" + id);
		ContentValues updateValues = new ContentValues();
		updateValues.put(BluetoothShare.STATUS, status);
		context.getContentResolver().update(contentUri, updateValues, null, null);
		Constants.sendIntentIfCompleted(context, contentUri, status);
	}

	/*
	 * This function should be called whenever transfer status change to completed.
	 */
	public static void sendIntentIfCompleted(Context context, Uri contentUri, int status) {
		if (BluetoothShare.isStatusCompleted(status)) {
			Intent intent = new Intent(BluetoothShare.TRANSFER_COMPLETED_ACTION);
			intent.setClassName(THIS_PACKAGE_NAME, BluetoothFtpReceiver.class.getName());
			intent.setData(contentUri);
			context.sendBroadcast(intent);
		}
	}

	public static boolean mimeTypeMatches(String mimeType, String[] matchAgainst) {
		for (String matchType : matchAgainst) {
			if (mimeTypeMatches(mimeType, matchType)) {
				return true;
			}
		}
		return false;
	}

	public static boolean mimeTypeMatches(String mimeType, String matchAgainst) {
		if (mimeType == null) return false;
		Pattern p = Pattern.compile(matchAgainst.replaceAll("\\*", "\\.\\*"),
				Pattern.CASE_INSENSITIVE);
		return p.matcher(mimeType).matches();
	}

	public static void logHeader(HeaderSet hs) {
		Log.v(TAG, "Dumping HeaderSet " + hs.toString());
		try {

			Log.v(TAG, "COUNT : " + hs.getHeader(HeaderSet.COUNT));
			Log.v(TAG, "NAME : " + hs.getHeader(HeaderSet.NAME));
			Log.v(TAG, "TYPE : " + hs.getHeader(HeaderSet.TYPE));
			Log.v(TAG, "LENGTH : " + hs.getHeader(HeaderSet.LENGTH));
			Log.v(TAG, "TIME_ISO_8601 : " + hs.getHeader(HeaderSet.TIME_ISO_8601));
			Log.v(TAG, "TIME_4_BYTE : " + hs.getHeader(HeaderSet.TIME_4_BYTE));
			Log.v(TAG, "DESCRIPTION : " + hs.getHeader(HeaderSet.DESCRIPTION));
			Log.v(TAG, "TARGET : " + hs.getHeader(HeaderSet.TARGET));
			Log.v(TAG, "HTTP : " + hs.getHeader(HeaderSet.HTTP));
			Log.v(TAG, "WHO : " + hs.getHeader(HeaderSet.WHO));
			Log.v(TAG, "OBJECT_CLASS : " + hs.getHeader(HeaderSet.OBJECT_CLASS));
			Log.v(TAG, "APPLICATION_PARAMETER : " + hs.getHeader(HeaderSet.APPLICATION_PARAMETER));
		} catch (IOException e) {
			Log.e(TAG, "dump HeaderSet error " + e);
		}
	}


	public static String getMimeType(String filename) {
		String type = null;
		try {
			int dotIndex = filename.lastIndexOf(".");
			if (dotIndex < 0) {
				Log.d(TAG, "Uploaded file has no extension!");
			} else {
				String extension = filename.substring(dotIndex + 1).toLowerCase();
				MimeTypeMap map = MimeTypeMap.getSingleton();
				type = map.getMimeTypeFromExtension(extension);
				Log.d(TAG, "Mimetype derived from extension " + extension + " is " + type);
				if (type != null && Constants.mimeTypeMatches(type, Constants.ACCEPTABLE_SHARE_INBOUND_TYPES)) {
					type = type.toLowerCase();
				} else {
					type = null;
				}
			}
		} catch (Exception e) {
			Log.e(TAG, "getMimeType error " + e);
		}
		return type;
	}
}
