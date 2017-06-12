/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/siteimport/trunk/siteimport-webapp/webapp/src/java/org/etudes/siteimport/webapp/FileImportServiceImpl.java $
 * $Id: FileImportServiceImpl.java 11000 2015-06-02 23:09:05Z mallikamt $
 ***********************************************************************************
 *
 * Copyright (c) 2013, 2015 Etudes, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **********************************************************************************/

package org.etudes.siteimport.webapp;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dom4j.Document;
import org.etudes.siteimport.api.FileImportService;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.site.api.Site;

/**
 * FileImportServiceImpl handles upload of content packages into a site.
 */
public class FileImportServiceImpl implements FileImportService
{
	/** Our log. */
	private static Log M_log = LogFactory.getLog(FileImportServiceImpl.class);

	protected static final String BLACKBOARD_ECOLLEGE_MANIFEST_FILENAME = "imsmanifest.xml";

	protected static int BUFFER_SIZE = 4096;

	protected static final String MOODLE_MANIFEST_FILENAME = "moodle.xml";

	protected static final String MOODLE2_MANIFEST_FILENAME = "moodle_backup.xml";

	/**
	 * Construct
	 */
	public FileImportServiceImpl()
	{
	}

	/**
	 * {@inheritDoc}
	 */
	public String importFromFile(String fromFileName, InputStream fromFileStream, String uploadType, Site toSite, String authenticatedUserId)
	{
		if ((fromFileName == null) || (fromFileStream == null) || (fromFileName.lastIndexOf('.') == -1) || (uploadType == null) || (toSite == null)
				|| (authenticatedUserId == null)) return null;

		String extension = fromFileName.substring(fromFileName.lastIndexOf('.') + 1).toLowerCase();
		if (!(extension.equals("zip") || extension.equals("mbz"))) return null;

		// get the import file stream into a local zip file, and unzip to a local directory
		String unzipDirectoryName = unzipStream(fromFileStream, fromFileName, toSite.getId());
		if (unzipDirectoryName == null) return null;

		String message = null;

		// process the unzipped files
		if (uploadType.equals("moodle"))
		{
			if (extension.equals("zip"))
			{
				Document manifest = readManifest(unzipDirectoryName, MOODLE_MANIFEST_FILENAME);
				BaseImportOtherLMS m = new ImportOtherLMSMoodle(manifest, unzipDirectoryName, toSite.getId(), authenticatedUserId);
				message = m.transferEntities();
			}
			else if (extension.equals("mbz"))
			{
				Document manifest = readManifest(unzipDirectoryName, MOODLE2_MANIFEST_FILENAME);
				BaseImportOtherLMS m = new ImportOtherLMSMoodle2(manifest, unzipDirectoryName, toSite.getId(), authenticatedUserId);
				message = m.transferEntities();
			}
		}
		else if (uploadType.equals("blackboard91"))
		{
			boolean multipleBB = checkIfMultipleBB(unzipDirectoryName);
			if (!multipleBB)
			{
			Document manifest = readManifest(unzipDirectoryName, BLACKBOARD_ECOLLEGE_MANIFEST_FILENAME);
			BaseImportOtherLMS m = new ImportOtherLMSBlackBoard(manifest, unzipDirectoryName, toSite.getId(), authenticatedUserId);
			message = m.transferEntities();
		}
			else
			{
				message = importBBMultiple(unzipDirectoryName, toSite.getId(), authenticatedUserId);
			}
		}
		else if (uploadType.equals("ecollege"))
		{
			Document manifest = readManifest(unzipDirectoryName, BLACKBOARD_ECOLLEGE_MANIFEST_FILENAME);
			BaseImportOtherLMS m = new ImportOtherLMSECollege(manifest, unzipDirectoryName, toSite.getId(), authenticatedUserId);
			message = m.transferEntities();
		}

		return message;
	}

	/**
	 * @return The ServerConfigurationService, via the component manager.
	 */
	private ServerConfigurationService serverConfigurationService()
	{
		return (ServerConfigurationService) ComponentManager.get(ServerConfigurationService.class);
	}

	/**
	 * Delete a file or directory and all its files
	 * 
	 * @param delfile
	 *        File object
	 * @param meToo
	 *        if true, delete the initial directory / file
	 */
	protected void deleteFiles(File delfile, boolean meToo)
	{
		if (delfile.isDirectory())
		{
			File files[] = delfile.listFiles();
			int i = files.length;
			while (i > 0)
				deleteFiles(files[--i], true);
		}
		if (meToo) delfile.delete();
	}

	/**
	 * Read the zip file manifest.
	 * 
	 * @param unzipDirectoryName
	 *        The location of the unzipped zip file.
	 * @param manifestName
	 *        The file name of the manifest.
	 * @return The manifest as a parsed DOM document.
	 */
	protected Document readManifest(String unzipDirectoryName, String manifestName)
	{
		File manifest = new File(unzipDirectoryName + File.separator + manifestName);
		Document document = XMLHelper.parseFile(manifest);
		return document;
	}

	/**
	 * write zip file to disk
	 * 
	 * @param zis
	 * @param name
	 * @throws IOException
	 */
	protected void unzip(ZipInputStream zis, String name) throws IOException
	{
		BufferedOutputStream dest = null;
		try
		{
			// write the files to the disk
			dest = new BufferedOutputStream(new FileOutputStream(name), BUFFER_SIZE);
			byte data[] = new byte[BUFFER_SIZE];
			int count;
			while ((count = zis.read(data, 0, BUFFER_SIZE)) != -1)
			{
				dest.write(data, 0, count);
			}
		}
		finally
		{
			if (dest != null) dest.close();
		}
	}

	/**
	 * unzip the file and write to disk
	 * 
	 * @param zipFileName
	 *        The name of the zip file
	 * @param dirpath
	 *        The name of the directory in which to unzip the file.
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	protected void unzipFile(String zipFileName, String dirpath) throws IOException
	{
		ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(new File(zipFileName))));
		ZipEntry entry;
		try
		{
			while ((entry = zis.getNextEntry()) != null)
			{
				if (entry.isDirectory())
				{

				}
				else if (entry.getName().lastIndexOf('\\') != -1)
				{
					String filenameincpath = entry.getName();

					String actFileNameIncPath = dirpath;

					while (filenameincpath.indexOf('\\') != -1)
					{
						String subFolName = filenameincpath.substring(0, filenameincpath.indexOf('\\'));

						File subfol = new File(actFileNameIncPath + File.separator + subFolName);
						if (!subfol.exists()) subfol.mkdirs();

						actFileNameIncPath = actFileNameIncPath + File.separator + subFolName;

						filenameincpath = filenameincpath.substring(filenameincpath.indexOf('\\') + 1);
					}

					String filename = entry.getName().substring(entry.getName().lastIndexOf('\\') + 1);
					try
					{
						unzip(zis, actFileNameIncPath + File.separator + filename);
					}
					catch (IOException e)
					{
						M_log.warn("unzipFile: unable to extract: " + filename + " : " + e);
					}
				}
				else if (entry.getName().lastIndexOf('/') != -1)
				{
					String filenameincpath = entry.getName();

					String actFileNameIncPath = dirpath;

					while (filenameincpath.indexOf('/') != -1)
					{
						String subFolName = filenameincpath.substring(0, filenameincpath.indexOf('/'));
						File subfol = new File(actFileNameIncPath + File.separator + subFolName);
						if (!subfol.exists()) subfol.mkdirs();

						actFileNameIncPath = actFileNameIncPath + File.separator + subFolName;

						filenameincpath = filenameincpath.substring(filenameincpath.indexOf('/') + 1);
					}

					String filename = entry.getName().substring(entry.getName().lastIndexOf('/') + 1);
					try
					{
						unzip(zis, actFileNameIncPath + File.separator + filename);
					}
					catch (IOException e)
					{
						M_log.warn("unzipFile: unable to extract: " + filename + " : " + e);
					}
				}
				else
				{
					try
					{
						unzip(zis, dirpath + File.separator + entry.getName());
					}
					catch (IOException e)
					{
						M_log.warn("unzipFile: unable to extract: " + entry.getName() + " : " + e);
					}
				}
			}
		}
		finally
		{
			zis.close();
		}
	}

	protected String unzipStream(InputStream stream, String fileName, String uniquePathComponent)
	{
		// make a directory for this unzip, using the configured base path and the unique component
		String base = serverConfigurationService().getString("moodle.importDir", "") + File.separator + "import" + uniquePathComponent;
		File dir = new File(base);

		// if it does not yet exist, make it (and all the directories up to it)
		if (!dir.exists())
		{
			dir.mkdirs();
		}
		// otherwise make sure it is empty
		else
		{
			deleteFiles(dir, false);
		}

		// chunk the stream to a zip file
		String zipFileName = base + File.separator + fileName;
		FileOutputStream fout = null;
		try
		{
			fout = new FileOutputStream(new File(zipFileName));
			byte[] buf = new byte[BUFFER_SIZE];
			int n;
			while ((n = stream.read(buf)) >= 0)
			{
				fout.write(buf, 0, n);
			}
		}
		catch (IOException e)
		{
			M_log.warn("unzipStream: unable to write stream file: " + fileName + " to path: " + base);
			return null;
		}
		finally
		{
			try
			{
				fout.close();
				stream.close();
			}
			catch (IOException e)
			{
				M_log.warn("closing streams: " + e);
			}
		}

		// unzip to a directory of the same name, without the extension
		String unzipDirName = base + File.separator + fileName.substring(0, fileName.lastIndexOf('.'));
		File unzipDir = new File(unzipDirName);
		unzipDir.mkdirs();

		// unzip the file
		try
		{
			unzipFile(zipFileName, unzipDirName);
		}
		catch (IOException e)
		{
			M_log.warn("unzipStream: unable to unzip stream file: " + zipFileName);
			return null;
		}

		return unzipDirName;
	}
	
	/**
	 * Checks to see if this folder contains only zip files in it.
	 * 
	 * @param unzipDirName Name of directory
	 * @return true if all files are zip files, false if not
	 */
	private boolean checkIfMultipleBB(String unzipDirName)
	{
		File dir = new File(unzipDirName);
		if (!dir.exists()) return false;
		if (!dir.isDirectory()) return false;
		
		File[] listFiles = dir.listFiles();
		
		if (listFiles.length == 0) return false;
		
		boolean allZip = false;
		for (int i=0; i<listFiles.length; i++)
		{
			String fileName = listFiles[i].getName();
			String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
			
			if (listFiles[i].isFile() && extension != null && extension.equals("zip"))
			{
				allZip = true;
}
			else
			{
				allZip = false;
				break;
			}
		}
		return allZip;
	}
	
	/**
	 * Unzips and imports multiple BB files
	 * 
	 * @param mainDirName Directory used for extraction
	 * @param toSiteId Site ID being imported into
	 * @param authenticatedUserId
	 *        The authenticated user performing the import.
	 * @return
	 */
	private String importBBMultiple(String mainDirName, String toSiteId, String authenticatedUserId)
	{
		File dir = new File(mainDirName);
		if (!dir.exists()) return null;
		if (!dir.isDirectory()) return null;
		
		File[] listFiles = dir.listFiles();
		
		if (listFiles.length == 0) return null;
		
		String message = null;
		boolean importFailed = false;
		for (int i=0; i<listFiles.length; i++)
		{
			String fileName = listFiles[i].getName();
			String path = listFiles[i].getAbsolutePath();
			String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
			if (listFiles[i].isFile() && extension != null && extension.equals("zip"))
			{
				String unzipDirName = path.substring(0, path.lastIndexOf('.'));
				File unzipDir = new File(unzipDirName);
				unzipDir.mkdirs();
				// unzip the file
				try
				{
					unzipFile(path, unzipDirName);
					Document manifest = readManifest(unzipDirName, BLACKBOARD_ECOLLEGE_MANIFEST_FILENAME);
					BaseImportOtherLMS m = new ImportOtherLMSBlackBoard(manifest, unzipDirName, toSiteId, authenticatedUserId);
					message = m.transferEntities();
					if (message == null) importFailed = true;
				}
				catch (IOException e)
				{
					M_log.warn("unzipStream: unable to unzip multiple: " + path);
					importFailed = true;
				}
			}
		}
		if (importFailed) message = "An error has occurred during the import process.";
		return message;
	}
}
