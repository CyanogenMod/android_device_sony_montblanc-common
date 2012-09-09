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

import android.database.Cursor;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.provider.MediaStore;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import javax.obex.HeaderSet;
import javax.obex.ObexTransport;
import javax.obex.Operation;
import javax.obex.ResponseCodes;
import javax.obex.ServerRequestHandler;
import javax.obex.ServerSession;

/**
 * This class runs as an OBEX server
 */
public class BluetoothFtpObexServerSession extends ServerRequestHandler
		implements BluetoothFtpObexSession {

	private static final String TAG = "BtFtpObexServer";
	private static final boolean D = Constants.DEBUG;
	private static final boolean V = Constants.VERBOSE;

	private ObexTransport mTransport;

	private Context mContext;

	private Handler mCallback = null;

	/* the current transfer info */
	private BluetoothFtpShareInfo mInfo;

	private ServerSession mSession;

	private WakeLock mWakeLock;

	private WakeLock mPartialWakeLock;

	private String mPathString = Constants.ROOT_FOLDER_DIR;

	private String mUserDefinedRootDir = null;

	private volatile boolean mPreReject = false;

	private volatile static int sDeleteFolderCounter = 0;
	private volatile static int sDeleteFileCounter = 0;
	private volatile static int sPutFileCounter = 0;
	private volatile static int sGetFileCounter = 0;

	public BluetoothFtpObexServerSession(Context context, ObexTransport transport) {
		mContext = context;
		mTransport = transport;
		PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
		mWakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP
				| PowerManager.ON_AFTER_RELEASE, TAG);
		mPartialWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
		mUserDefinedRootDir = BluetoothFtpFileUtility.getRootDirectory(mContext);
		mPathString = mUserDefinedRootDir;
		if (D) {
			Log.d(TAG, "after initialization, mPathString is " + mPathString);
			Log.d(TAG, "User defined root dir is: " + mUserDefinedRootDir);
		}
	}

	public void unblock() {

	}

	/**
	 * Called when connection is accepted from remote, to retrieve the first Header then wait for
	 * user confirmation
	 */
	public void preStart() {
		if (D) {
			Log.d(TAG, "connection accepted from remote");
			Log.d(TAG, "acquire full WakeLock");
		}
		mWakeLock.acquire();
		try {
			if (D) Log.d(TAG, "Create ServerSession with transport " + mTransport.toString());
			mSession = new ServerSession(mTransport, this, null);
		} catch (IOException e) {
			Log.e(TAG, "Create server session error" + e);
		}
	}

	/**
	 * Called from BluetoothFtpTransfer to start the "Transfer"
	 */
	public void start(Handler handler) {
		if (D) Log.d(TAG, "Start!");
		mCallback = handler;
	}

	/**
	 * Called from BluetoothFtpTransfer to cancel the "Transfer" Otherwise, server should end by
	 * itself.
	 */
	public void stop() {
		if (D) Log.d(TAG, "Stop!");
		if (mSession != null) {
			try {
				mSession.close();
				mTransport.close();
			} catch (IOException e) {
				Log.e(TAG, "close mTransport error" + e);
			}
		}
		mCallback = null;
		mSession = null;
	}

	public void addShare(BluetoothFtpShareInfo info) {
		if (D) Log.d(TAG, "addShare for id " + info.mId);
		mInfo = info;
	}

	@Override
	public int onPut(Operation op) {
		if (D) Log.d(TAG, "onPut called");
		synchronized (op) {
			if (D) Log.d(TAG, "onPut mPathString is " + mPathString);

			HeaderSet request;
			String name;
			Long length;
			int obexResponse = ResponseCodes.OBEX_HTTP_OK;
			BluetoothFtpFileUtility fileUtility = new BluetoothFtpFileUtility();
			try {
				mPreReject = false;
				request = op.getReceivedHeader();

				if (V) Constants.logHeader(request);
				name = (String) request.getHeader(HeaderSet.NAME);
				length = (Long) request.getHeader(HeaderSet.LENGTH);

				int isReadOnly = fileUtility.isDirReadOnly(mPathString);

				if (length == 0) {
					if (D) Log.w(TAG, "length is 0, reject the transfer");
					mPreReject = true;
					obexResponse = ResponseCodes.OBEX_HTTP_LENGTH_REQUIRED;
				} else if (name == null || name.equals("")) {
					if (D) Log.w(TAG, "name is null or empty, reject the transfer");
					mPreReject = true;
					obexResponse = ResponseCodes.OBEX_HTTP_BAD_REQUEST;
				} else if (isReadOnly == Constants.DIR_READ_ONLY ) {
					if (D) Log.w(TAG, "directory is read only. reject the transfer");
					mPreReject = true;
					obexResponse = ResponseCodes.OBEX_HTTP_FORBIDDEN;
				}
				if (mPreReject && obexResponse != ResponseCodes.OBEX_HTTP_OK) {
					// some bad implemented client won't send disconnect
					return obexResponse;
				} else {
					String sType = Constants.getMimeType(name);
					sPutFileCounter++;
					obexResponse = receiveFile(op);
					if (ResponseCodes.OBEX_HTTP_OK == obexResponse && sType != null) {
						String sPath = setStoragePath(name, false);
						if (D) Log.d(TAG, "ExternalStorageDir  " + sPath);
						Intent fileIntent = new Intent(Constants.INBOUND_FILE_TRANSFER_SCANNABLE_SUCCESS);
						fileIntent.putExtra(Constants.SCANPATH, sPath);
						fileIntent.putExtra(Constants.MIMETYPE, sType);
						mContext.sendBroadcast(fileIntent);
					}
				}
			} catch (IOException e) {
				Log.e(TAG, e.toString());
				obexResponse = ResponseCodes.OBEX_HTTP_BAD_REQUEST;
			}
			return obexResponse;
		}
	}

	/**
	 * This method is called to receive an incoming file.
	 *
	 * @param op operation object
	 * @return response code to be sent to the client
	 */
	private synchronized int receiveFile(Operation op) {
		int responseCode = ResponseCodes.OBEX_HTTP_OK;
		BufferedOutputStream boStream = null;
		InputStream iStream = null;
		HeaderSet request = null;
		String name = null;
		Long length = (long) 0;
		File file = null;
		FileOutputStream foStream = null;

		try {
			iStream = op.openInputStream();
			request = op.getReceivedHeader();
			name = (String) request.getHeader(HeaderSet.NAME);
			length = (Long) request.getHeader(HeaderSet.LENGTH);
			file = new File(mPathString, name);
			foStream = new FileOutputStream(file);
		} catch (FileNotFoundException e) {
			Log.e(TAG, "error while opening file");
			responseCode = ResponseCodes.OBEX_HTTP_BAD_REQUEST;
		} catch (IOException e1) {
			Log.e(TAG, "Error when openInputStream in receiving file");
			responseCode = ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
		}

		if (responseCode == ResponseCodes.OBEX_HTTP_OK) {
			if (D) Log.d(TAG, "allocating BOS length: " + 0x10000);

			boStream = new BufferedOutputStream(foStream, 0x10000);
			int outputBufferSize = op.getMaxPacketSize();
			byte[] b = new byte[outputBufferSize];
			int readLength = 0;
			long timestamp = 0;
			int position = 0;

			try {
				while (position != length.intValue()) {

					if (V) timestamp = System.currentTimeMillis();
					readLength = iStream.read(b);

					if (readLength == -1) {
						if (D) Log.d(TAG, "Receive file reached stream end at position" + position);
						break;
					}

					boStream.write(b, 0, readLength);
					position += readLength;

					if (D) Log.d(TAG, "Receive file position = " + position + " readLength "
							+ readLength + " bytes took "
							+ (System.currentTimeMillis() - timestamp) + " ms");
					if (V) Log.v(TAG, "writing file");

				}

			} catch (IOException e1) {
				Log.e(TAG, "Error when receiving file");
				Intent fileIntent = new Intent(BluetoothShare.INBOUND_FILE_TRANSFER_ERROR);
				Intent folderIntent = new Intent(BluetoothShare.INBOUND_FOLDER_TRANSFER_ERROR);
				mContext.sendBroadcast(fileIntent);
				mContext.sendBroadcast(folderIntent);
				responseCode = ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
				//Delete partially downloaded file
				if(!file.delete()) {
					Log.e(TAG, "Could not delete partial file");
				}
			} finally {
				try {
					if (boStream != null) {
						boStream.close();
					}
					if (iStream != null) {
						iStream.close();
					}
				} catch (IOException e1) {
					Log.e(TAG, "Error when closing stream after send");
					responseCode = ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
				}
			}
		}
		return responseCode;
	}

	@Override
	public int onConnect(HeaderSet request, HeaderSet reply) {
		if (D) Log.d(TAG, "onConnect called");
		if (V) Constants.logHeader(request);

		if (mPathString == null) {
			return ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
		}
		try {
			byte[] uuid = (byte[]) request.getHeader(HeaderSet.TARGET);

			if (uuid != null) {
				if (V) Log.v(TAG, "onConnect(): uuid =" + Arrays.toString(uuid));
				reply.setHeader(HeaderSet.WHO, uuid);
			}
			else {
				if (V) Log.v(TAG, "onConnect(): no uuid");
				return ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
			}
		} catch (IOException e) {
			if (D) Log.d(TAG, "error while retrieving TARGET header");
			Log.e(TAG, e.toString());
			return ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
		}
		return ResponseCodes.OBEX_HTTP_OK;
	}

	@Override
	public void onDisconnect(HeaderSet req, HeaderSet resp) {
		if (D) Log.d(TAG, "onDisconnect");
		mPathString = mUserDefinedRootDir;
		resp.responseCode = ResponseCodes.OBEX_HTTP_OK;
	}

	@Override
	public void onClose() {
		if (V) Log.v(TAG, "release WakeLock");
		if (mWakeLock.isHeld()) {
			mWakeLock.release();
		}
		if (mPartialWakeLock.isHeld()) {
			mPartialWakeLock.release();
		}
		/* onClose could happen even before start() where mCallback is set */
		if (mCallback != null) {
			Message msg = Message.obtain(mCallback);
			msg.what = BluetoothFtpObexSession.MSG_SESSION_COMPLETE;
			msg.obj = mInfo;
			msg.sendToTarget();
		}
	}

	@Override
	public int onGet(Operation op) {
		if (D) Log.d(TAG, "onGet called");

		String name;
		byte[] uuid;
		byte[] bytesInFile = null;
		StringBuffer strBuffer = new StringBuffer();
		OutputStream outStream = null;

		BluetoothFtpFileUtility fileUtility = new BluetoothFtpFileUtility();

		try {
			HeaderSet request = op.getReceivedHeader();
			name = (String) request.getHeader(HeaderSet.NAME);
			uuid = (byte[]) request.getHeader(HeaderSet.TARGET);

			if (D) Constants.logHeader(request);

			synchronized (strBuffer) {
				if (mPathString == null) {
					return ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
				}
				if (D) Log.d(TAG, "initial onGet mPathString is " + mPathString);

				if (name != null) {
					int isFile = fileUtility.isFileOrFolder(mPathString, name);
					if (isFile == Constants.IS_FILE) {
						if (D) Log.d(TAG, "Reading requested file...");
						sGetFileCounter++;
						return sendFile(op);
					} else if (isFile == Constants.IS_FOLDER){
						strBuffer.append(BluetoothFtpXmlFormer.createXml(mPathString));
						bytesInFile = strBuffer.toString().getBytes();
					}
					else
					{
						if (D) Log.d(TAG, "Can not access file/folder.");
						return ResponseCodes.OBEX_HTTP_NO_CONTENT;
					}
				} else {
					strBuffer.append(BluetoothFtpXmlFormer.createXml(mPathString));
					bytesInFile = strBuffer.toString().getBytes();

					// Generating transfer success events.
					if (sDeleteFileCounter > 1) {
						if (D) Log.d(TAG, "sending multiple files delete success event");
						Intent intent = new Intent(BluetoothShare.MULTIPLE_FILE_DELETE_SUCCESS);
						mContext.sendBroadcast(intent);
					}
					if (sDeleteFolderCounter > 1) {
						if (D) Log.d(TAG, "sending multiple folders delete success event");
						Intent intent = new Intent(BluetoothShare.MULTIPLE_FOLDER_DELETE_SUCCESS);
						mContext.sendBroadcast(intent);
					}
					if (sPutFileCounter > 1) {

						if (D) Log.d(TAG, "sending multiple files put success event");
						Intent intent = new Intent(BluetoothShare.MULTIPLE_FILE_PUT_SUCCESS);
						mContext.sendBroadcast(intent);
					}
					if (sGetFileCounter > 1) {
						if (D) Log.d(TAG, "sending multiple files get success event");
						Intent intent = new Intent(BluetoothShare.MULTIPLE_FILE_GET_SUCCESS);
						mContext.sendBroadcast(intent);
					}
					sDeleteFileCounter = 0;
					sGetFileCounter = 0;
					sPutFileCounter = 0;
					sDeleteFolderCounter = 0;

				}

				if (bytesInFile != null) {
					if (bytesInFile.length > 0) {
						strBuffer.delete(0, strBuffer.length());
					} else if (bytesInFile.length == 0) {
						if (V) Log.v(TAG, "File length is 0");
						return ResponseCodes.OBEX_HTTP_NO_CONTENT;
					} else {
						return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
					}
				}

			}

			HeaderSet response = new HeaderSet();
			response.setHeader(HeaderSet.NAME, "");
			response.setHeader(HeaderSet.TYPE, "x-obex/folder-listing");
			response.setHeader(HeaderSet.LENGTH, new Long(bytesInFile.length));
			if (uuid != null) {
				response.setHeader(HeaderSet.WHO, uuid);
			}
			op.sendHeaders(response);

			try {
				outStream = op.openDataOutputStream();
				outStream.write(bytesInFile);
				if (V) Log.v(TAG, "data sent successfully to the remote client");
				outStream.flush();
			} catch (IOException e) {
				Log.e(TAG, "exception in sending xml to client " + e.toString());
			} finally {
				try {
					outStream.close();
				} catch (IOException e) {
					Log.e(TAG, "Exception in closing outStream" + e.toString());
				}
			}
		} catch (IOException e) {
			Log.e(TAG, e.toString());
		}
		return ResponseCodes.OBEX_HTTP_OK;
	}

	/**
	 * This method is called when a file is to be sent to the client.
	 *
	 * @param op operation object
	 * @return response code to be sent to the client
	 */
	private int sendFile(Operation op) {

		int responseCode = ResponseCodes.OBEX_HTTP_OK;
		BufferedInputStream biStream = null;
		OutputStream oStream = null;
		HeaderSet request = null;
		String name = null;
		File file = null;
		FileInputStream fiStream = null;
		Long fileLength = (long) 0;

		HeaderSet response = new HeaderSet();
		response.setHeader(HeaderSet.NAME, "");
		response.setHeader(HeaderSet.TYPE, "x-obex/folder-listing");

		try {
			oStream = op.openOutputStream();
			request = op.getReceivedHeader();
			name = (String) request.getHeader(HeaderSet.NAME);
			if(name == null || name == ""){
				return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
			}
			file = new File(mPathString, name);
			fileLength = file.length();
			fiStream = new FileInputStream(file);
		} catch (FileNotFoundException e) {
			Log.e(TAG, e.toString());
			responseCode = ResponseCodes.OBEX_HTTP_BAD_REQUEST;
		} catch (IOException e) {
			Log.e(TAG, "error while openOutputStream" + e.toString());
			responseCode = ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
		}

		if (responseCode == ResponseCodes.OBEX_HTTP_OK) {
			if (V) Log.v(TAG, "allocating total BOS length: " + 0x10000);
			biStream = new BufferedInputStream(fiStream, 0x10000);
			int position = 0;
			int inputBufferSize = op.getMaxPacketSize();
			byte[] buffer = new byte[inputBufferSize];
			int readLength = 0;
			response.setHeader(HeaderSet.LENGTH, fileLength);
			try {
				op.sendHeaders(response);

				while (position != fileLength) {
					if (V) Log.v(TAG, "sending file...");
					readLength = biStream.read(buffer, 0, inputBufferSize);
					if (readLength == -1) {
						if (D) Log.v(TAG, "end of file");
						break;
					}
					oStream.write(buffer, 0, readLength);
					if (D) {
						Log.d(TAG, "Max size of packet: " + inputBufferSize);
						Log.d(TAG, "packet length sent: " + readLength);
					}
					position += readLength;
				}
				Intent fileIntent = new Intent(BluetoothShare.OUTBOUND_FILE_TRANSFER_SUCCESS);
				Intent folderIntent = new Intent(BluetoothShare.OUTBOUND_FOLDER_TRANSFER_SUCCESS);
				mContext.sendBroadcast(fileIntent);
				mContext.sendBroadcast(folderIntent);
			} catch (IOException e) {
				Log.e(TAG, "Error while sending file");
				Log.e(TAG, e.toString());
				Intent fileIntent = new Intent(BluetoothShare.OUTBOUND_FILE_TRANSFER_ERROR);
				Intent folderIntent = new Intent(BluetoothShare.OUTBOUND_FOLDER_TRANSFER_ERROR);
				mContext.sendBroadcast(fileIntent);
				mContext.sendBroadcast(folderIntent);
				responseCode = ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
			} finally {
				try {
					if (biStream != null) biStream.close();
					if (fiStream != null) fiStream.close();
					if (oStream != null) oStream.close();
				} catch (IOException e) {
					Log.e(TAG, "Error when closing stream after send");
					responseCode = ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
				}
			}
		}

		return responseCode;
	}

	@Override
	public int onSetPath(HeaderSet request, HeaderSet reply, boolean backup, boolean create) {
		if (D) Log.d(TAG, "onSetPath called");

		String name;
		byte[] uuid;
		BluetoothFtpFileUtility fileUtility = new BluetoothFtpFileUtility();
		if (D) Log.d(TAG, "initial mPathString in onSetPath " + mPathString);
		try {
			name = (String) request.getHeader(HeaderSet.NAME);
			uuid = (byte[]) request.getHeader(HeaderSet.TARGET);

			if (D) Constants.logHeader(request);

			if (mPreReject) {
				mPreReject = false;
				return ResponseCodes.OBEX_HTTP_PRECON_FAILED;
			} else {
					/*Don`t create directory if it does not exist.*/
					if (create == false) {
						if (backup == true) {
							if (mPathString.indexOf(Constants.PATH_SEPARATOR) == mPathString
									.lastIndexOf(Constants.PATH_SEPARATOR)) {
								mPathString = mUserDefinedRootDir;
								/*We are already on top.*/
								return ResponseCodes.OBEX_HTTP_NOT_FOUND;
							} else {
								mPathString = mPathString.substring(mPathString
										.indexOf(Constants.PATH_SEPARATOR), mPathString
										.lastIndexOf(Constants.PATH_SEPARATOR));
							}
						} else {
							// go backward/forward
							if (name == null) {
								mPathString = mUserDefinedRootDir;
							} else {
								int temp = fileUtility.checkPathValidity(mPathString, name);
								if(temp == Constants.INVALID_PATH_REQ){
									return ResponseCodes.OBEX_HTTP_NOT_FOUND;
								}
								if(temp == Constants.FILE_ACCESS_DENIED){
									return ResponseCodes.OBEX_HTTP_UNAUTHORIZED;
								}
								mPathString = mPathString.concat(Constants.PATH_SEPARATOR).concat(name);
							}
							Intent intent = new Intent(BluetoothShare.SET_PATH_BACK_SUCCESS);
							mContext.sendBroadcast(intent);
							intent = new Intent(BluetoothShare.SET_PATH_CREATE_SUCCESS);
							mContext.sendBroadcast(intent);
						}

					} else {
						if (backup == false) {
							// client requested to create a folder
							int response = fileUtility.createFolder(mPathString, name);
							if( response == Constants.FOLDER_CREATED_SUCCESSFULLY){
								if (V) Log.v(TAG, "folder created successfully");

								if (!(mPathString.equals(Constants.ROOT_FOLDER_DIR))) {
									mPathString = mPathString.concat(Constants.PATH_SEPARATOR)
											.concat(name);
								} else {
									mPathString = mPathString.concat(name);
								}
								Intent folderIntent = new Intent(
										BluetoothShare.CREATE_FOLDER_SUCCESS);
								mContext.sendBroadcast(folderIntent);
								return ResponseCodes.OBEX_HTTP_OK;
							} else {
								if (V) Log.v(TAG, "folder creation is unauthorized");
								Intent intent1 = new Intent(BluetoothShare.CREATE_FOLDER_ERROR);
								mContext.sendBroadcast(intent1);
								return ResponseCodes.OBEX_HTTP_UNAUTHORIZED;
							}
						}
					}
				}

			if (uuid != null) {
				reply.setHeader(HeaderSet.WHO, uuid);
			}
		} catch (IOException e) {
			Log.e(TAG, "exception caught in onSetPath " + e.toString());
			return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
		}
		return ResponseCodes.OBEX_HTTP_OK;
	}

	@Override
	public int onDelete(HeaderSet request, HeaderSet reply) {
		if (D) Log.d(TAG, "onDelete called");
		if (D) Log.d(TAG, "mPreReject is " + mPreReject);
		int mClientRequestType = 0;

		if (!mPreReject) {
			BluetoothFtpFileUtility fileUtility = new BluetoothFtpFileUtility();
			String name;
			int response;
			try {
				name = (String) request.getHeader(HeaderSet.NAME);
				if (D) Log.d(TAG, "incoming header name in onDelete is " + name);
				mClientRequestType = fileUtility.isFileOrFolder(mPathString, name);
				if (D) Log.d(TAG, "delete request type is " + mClientRequestType);
				switch (mClientRequestType) {
				case Constants.IS_FILE:
					if (fileUtility.deleteFile(mPathString, name)) {
						if (V) Log.v(TAG, mPathString + "/" + name + " file deleted successfully");
						String sType = Constants.getMimeType(name);
						String sPath = setStoragePath(name, false);
						if (D) Log.d(TAG, "ExternalStorageDir  " + sPath);
						Intent fileIntent = new Intent(BluetoothShare.DELETE_FILE_SUCCESS);
						fileIntent.putExtra(Constants.DELETE_PATH, sPath);
						fileIntent.putExtra(Constants.MIMETYPE, sType);
						mContext.sendBroadcast(fileIntent);
						return ResponseCodes.OBEX_HTTP_OK;
					} else {
						Intent fileIntent = new Intent(BluetoothShare.DELETE_FILE_ERROR);
						mContext.sendBroadcast(fileIntent);
						return ResponseCodes.OBEX_HTTP_UNAUTHORIZED;
					}
				case Constants.IS_FOLDER:
					response = fileUtility.deleteFolder(new File(mPathString, name), mDeleteFolderHandler);
					switch (response) {
					case Constants.FOLDER_DELETED:
						if (V) Log.v(TAG, "Folder deleted successfully");
						sDeleteFolderCounter++;
						Intent folderIntent = new Intent(BluetoothShare.DELETE_FOLDER_SUCCESS);
						mContext.sendBroadcast(folderIntent);
						return ResponseCodes.OBEX_HTTP_OK;
					case Constants.FOLDER_HAS_NON_EXTN_FILE:
						if (D) Log.v(TAG, "Folder has non extn files. Denying deletion");
						mPreReject = true;
						folderIntent = new Intent(BluetoothShare.DELETE_FOLDER_ERROR);
						mContext.sendBroadcast(folderIntent);
						return ResponseCodes.OBEX_HTTP_PRECON_FAILED;
					case Constants.FILE_ACCESS_DENIED:
						folderIntent = new Intent(BluetoothShare.DELETE_FOLDER_ERROR);
						mContext.sendBroadcast(folderIntent);
						return ResponseCodes.OBEX_HTTP_UNAUTHORIZED;

					default:
						return ResponseCodes.OBEX_HTTP_UNAUTHORIZED;
					}
				default:
					return ResponseCodes.OBEX_HTTP_FORBIDDEN;
				}
			} catch (IOException e) {
				Log.e(TAG, "IOException in onDelete " + e.toString());
				return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
			}
		} else {
			mPreReject = false;
			return ResponseCodes.OBEX_HTTP_PRECON_FAILED;
		}
	}

	/**
	 * File path (including file name) resolver using Environment.getExternalStorageDirectory() API
	 *
	 * @param fname location of file name to be resolved
	 * @param bAbsolutePath whether the first argument (fname) is already a file path form
	 * @return resolved object's path on local external storage
	 */
	private String setStoragePath(String fname, boolean bAbsolutePath) {
		int findex = -1;
		String subFolder = "/";
		if (D) Log.d(TAG, "fname: " + fname + " bAbsolutePath: " + bAbsolutePath);

		try {
			if (bAbsolutePath) {
				findex = fname.indexOf(Constants.PATH_SEPARATOR, 1);
			} else {
				findex = mPathString.indexOf(Constants.PATH_SEPARATOR, 1);
			}

			if (findex > -1) {
				subFolder = bAbsolutePath ? fname.substring(findex) : mPathString.substring(findex) + "/";
			}
			if (subFolder.equals("/")) {
				return Environment.getExternalStorageDirectory() + "/" + fname;
			} else {
				if (bAbsolutePath) {
					return Environment.getExternalStorageDirectory() + subFolder;
				} else {
					return Environment.getExternalStorageDirectory() + subFolder + fname;
				}
			}
		} catch (Exception e) {
			Log.e(TAG, "setStoragePath exception " + e.toString());
			return null;
		}
	}

	/**
	 * Handler for message reception when deleting files/folders
	 */
	private Handler mDeleteFolderHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			if (V) Log.v(TAG, "mDeleteFolderHandler handle msg = " + msg.what);
			switch (msg.what) {
			case Constants.MSG_FILE_DELETED:
				if (D) Log.d(TAG, "mDeleteFolderHandler msg object = " + msg.obj);
				String sType = Constants.getMimeType(msg.obj.toString());
				String sPath = setStoragePath(msg.obj.toString(), true);
				if (D) Log.d(TAG, "ExternalStorageDir  " + sPath);
				Intent fileIntent = new Intent(BluetoothShare.DELETE_FILE_SUCCESS);
				fileIntent.putExtra(Constants.DELETE_PATH, sPath);
				fileIntent.putExtra(Constants.MIMETYPE, sType);
				mContext.sendBroadcast(fileIntent);
				break;
			}
		}
	};
}
