/*
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
 * - Neither the name of the ST-Ericsson SA nor the names of its contributors
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

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * This class has some file utility methods
 */
public class BluetoothFtpFileUtility {

	private static final String TAG = "BluetoothFtpFileUtility";
	private static final boolean D = Constants.DEBUG;
	private static final boolean V = Constants.VERBOSE;

	public BluetoothFtpFileUtility() {

	}

	/**
	 * This method creates a folder to the specified path.
	 *
	 * @param path Absolute path where folder has to be created.
	 * @param folderName Name of the folder to be created.
	 * @return response Code of create folder operation.
	 */
	public int createFolder(String path, String folderName) {
		int response = Constants.FILE_ACCESS_DENIED;
		try {
			File file = new File(path, folderName);
			if (file.exists()) {
				if (D) Log.d(TAG, "folder already exists");
			} else {
				file.mkdir();
			}
			if (file.exists()) {
				response = Constants.FOLDER_CREATED_SUCCESSFULLY;
			} else {
				Log.e(TAG, "folder not created");
			}
		} catch (SecurityException e) {
			Log.e(TAG, e.toString());

		} catch (NullPointerException npe) {
			Log.e(TAG, npe.toString());
		}
		return response;
	}

	/**
	 * This method determines whether provided name is a file or a folder.
	 *
	 * @param fileName name of the file/folder to be pulled with absolute path
	 * @return IS_FOLDER or IS_FILE or error code.
	 */
	public int isFileOrFolder(String filePath, String fileName) {
		int resultCode = Constants.IS_FILE;

		try {
			File file = new File(filePath, fileName);
			if (!file.isFile()) {
				resultCode = Constants.IS_FOLDER;
			}
		} catch (NullPointerException npe) {
			Log.e(TAG, npe.toString());
			resultCode = Constants.BAD_FILE_NAME;
		} catch (SecurityException e) {
			Log.e(TAG, e.toString());
			resultCode = Constants.FILE_ACCESS_DENIED;
		}

		return resultCode;
	}

	/**
	 * This method deletes a file requested by the client.
	 *
	 * @param path Absolute path of the file
	 * @param fileName Name of the file to be deleted
	 * @return true if file is deleted successfully else false
	 */
	public boolean deleteFile(String path, String fileName) {
		boolean isFileDeleted = false;
		try {
			File file = new File(path, fileName);
			if (file.canWrite()) {
				file.delete();
				isFileDeleted = true;
			} else {
				if (V) Log.v(TAG, "file is read only. Cannot be deleted");
			}
		} catch (SecurityException e) {
			Log.e(TAG, e.toString());
		} catch (NullPointerException npe) {
			Log.e(TAG, npe.toString());
		}
		return isFileDeleted;
	}

	/**
	 * This method determines whether a directory is read only or not.
	 *
	 * @param path Absolute path of the directory.
	 * @return DIR_READ_ONLY or DIR_NOT_READ_ONLY or error code.
	 */
	public int isDirReadOnly(String path) {
		if (V) Log.v(TAG, "path to perform read-only check: " + path);
		int resultCode = Constants.DIR_READ_ONLY;

		try {
			File file = new File(path);
			if (file.canWrite()) {
				if (V) Log.v(TAG, "directory is not read only");
				resultCode = Constants.DIR_NOT_READ_ONLY;
			} else {
				if (V) Log.v(TAG, "directory is read only");
			}
		}
		catch (NullPointerException npe) {
			Log.e(TAG, npe.toString());
			resultCode = Constants.BAD_FILE_NAME;
		}
		return resultCode;
	}

	/**
	 * This method is used to delete an empty and non-empty folder.
	 *
	 * @param ftpFile file object of folder to be deleted
	 * @param callbackHandler handler reporting back info about object being deleted
	 * @return Response code of folder delete operation.
	 */
	public int deleteFolder(File ftpFile, Handler callbackHandler) {
		int responseCode = Constants.FILE_ACCESS_DENIED;
		File[] fileList;
		try {
			fileList = ftpFile.listFiles();
			if (fileList != null) {
				for (File temp : fileList) {
					if (temp.isFile()) {
						if (!temp.delete()) {
							Log.e(TAG, temp.getName() + " could not be deleted");
							return Constants.FILE_ACCESS_DENIED;
						}
						if (V) Log.v(TAG, "file deleted");
						if (callbackHandler != null) {
							Message msg = Message.obtain();
							msg.setTarget(callbackHandler);
							msg.what = Constants.MSG_FILE_DELETED;
							msg.obj = temp.getAbsolutePath();
							msg.sendToTarget();
						}
					} else {
						if (D) Log.d(TAG, "recursively deleting...");
						responseCode = deleteFolder(temp, callbackHandler);
						if (Constants.FOLDER_DELETED != responseCode){
							return responseCode;
						}
					}
				}
			}
			if (ftpFile.delete()) {
				responseCode = Constants.FOLDER_DELETED;
			} else {
				Log.e(TAG, ftpFile.getName() + " could not be deleted");
				responseCode = Constants.FILE_ACCESS_DENIED;
			}
		} catch (SecurityException e) {
			Log.e(TAG, e.toString());
			responseCode = Constants.FILE_ACCESS_DENIED;
		}
		return responseCode;
	}

	/**
	 * Gets the root directory set by user.
	 *
	 * @param Context current context.
	 * @return Root directory path.
	 */
	public static String getRootDirectory(Context context) {
		String rootDir = null;
		FileInputStream fis = null;
		InputStreamReader isr = null;
		char[] inputBuffer = null;
		int pathLength = 0;
		try {
			fis = context.openFileInput(Constants.ROOT_CONTAINER_FILE);
			pathLength = fis.available();
			isr = new InputStreamReader(fis);
			inputBuffer = new char[pathLength];
			isr.read(inputBuffer);
			rootDir = new String(inputBuffer);
		} catch (FileNotFoundException e) {
			Log.e(TAG, e.toString());
			rootDir = Constants.ROOT_FOLDER_DIR;
		} catch (IOException e) {
			Log.e(TAG, e.toString());
			rootDir = Constants.ROOT_FOLDER_DIR;
		} finally {
			try {
				if (isr != null) {
					isr.close();
				}
				if (fis != null) {
					fis.close();
				}
			} catch (IOException e) {
				Log.e(TAG, e.toString());
			}
		}
		if (!(new File(rootDir.trim()).exists())) {
			return Constants.ROOT_FOLDER_DIR;
		}
		return rootDir.trim();
	}

	/**
	 * This method checks whether supplied name of the file is correct or not. Thereby determining
	 * the existence of the path.
	 *
	 * @param path Absolute path.
	 * @param name Name of the directory.
	 * @return Response code
	 */
	public int checkPathValidity(String path, String name){
		int response = Constants.RESPONSE_OK;
		try {
			File file = new File(path, name);
			if(!file.exists()){
				if (D) Log.d(TAG, "invalid path browsing request");
				response = Constants.INVALID_PATH_REQ;
			}
		} catch (SecurityException e) {
			Log.e(TAG, e.toString());
			response = Constants.FILE_ACCESS_DENIED;
		} catch (NullPointerException e) {
			Log.e(TAG, e.toString());
			response = Constants.INVALID_PATH_REQ;
		}
		return response;
	}
}
