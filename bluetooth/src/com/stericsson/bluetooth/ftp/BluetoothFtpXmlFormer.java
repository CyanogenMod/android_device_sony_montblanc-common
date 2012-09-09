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

import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;

/**
 * This class lists the directory structure in the XML
 */
public class BluetoothFtpXmlFormer {

	private static final String TAG = "BluetoothFtpXmlFormer";

	public BluetoothFtpXmlFormer() {

	}

	/**
	 * This method creates the directory structure in XML UTF-8 format.
	 *
	 * @param dir absolute path of the directory which contents has to be listed
	 * @return directory structure in UTF-8 XML format
	 */
	public static String createXml(String dir) {
		String rootTag = "folder-listing";
		String docDecl = " folder-listing " + "SYSTEM" + " \"obex-folder-listing.dtd\"";
		String fileTag = "file";
		String folderTag = "folder";
		XmlSerializer serializer = Xml.newSerializer();
		StringWriter writer = new StringWriter();
		try {
			serializer.setOutput(writer);
			serializer.startDocument("UTF-8", false);
			serializer.docdecl(docDecl);
			serializer.startTag("", rootTag);
			serializer.attribute("", "version", "1.0");
			serializer.startTag("", "parent-folder");

			serializer.endTag("", "parent-folder");

			File[] fileList = listDirectory(dir);

			if (fileList != null) {
				for (File file : fileList) {
					if (file.isDirectory()) {
						serializer.startTag("", folderTag);
						serializer.attribute("", "name", file.getName());
						serializer.attribute("", "size", new Long(file.length()).toString());
						serializer.attribute("", "modified", new Long(file.lastModified())
								.toString());
						serializer.attribute("", "user-perm", "R");
						serializer.endTag("", folderTag);
					} else if (file.isFile()) {
						serializer.startTag("", fileTag);
						serializer.attribute("", "name", file.getName());
						serializer.attribute("", "size", new Long(file.length()).toString());
						serializer.attribute("", "modified", new Long(file.lastModified())
								.toString());
						serializer.attribute("", "user-perm", "R");
						serializer.endTag("", fileTag);
					}
				}
			}

			serializer.endTag("", rootTag);
			serializer.endDocument();
			return writer.toString();
		} catch (IOException e) {
			Log.e(TAG, e.toString());
			return null;
		} catch (IllegalArgumentException e) {
			Log.e(TAG, e.toString());
			return null;
		} catch (IllegalStateException e) {
			Log.e(TAG, e.toString());
			return null;
		}

	}

	/**
	 * This method lists the contents of the directory.
	 *
	 * @param dir absolute path of the directory which contents has to be listed
	 * @return contents of the file. If no contents then returns null
	 */
	private static File[] listDirectory(String dir) {
		File file = new File(dir);

		File[] fileList = file.listFiles();

		return fileList;
	}

}
