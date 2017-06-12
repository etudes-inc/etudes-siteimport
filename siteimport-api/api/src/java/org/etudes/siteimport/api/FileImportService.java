/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/siteimport/trunk/siteimport-api/api/src/java/org/etudes/siteimport/api/FileImportService.java $
 * $Id: FileImportService.java 5941 2013-09-17 20:48:35Z ggolden $
 ***********************************************************************************
 *
 * Copyright (c) 2013 Etudes, Inc.
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

package org.etudes.siteimport.api;

import java.io.InputStream;

import org.sakaiproject.site.api.Site;

/**
 * FileImportService ...
 */
public interface FileImportService
{
	/**
	 * Import content from one site to another, assuring that the destination site has needed tools installed.
	 * 
	 * @param fromFileName
	 *        The source file name.
	 * @param fromFileStream
	 *        The source file stream.
	 * @param uploadType
	 *        The type of import.
	 * @param toSite
	 *        The destination site.
	 * @param authenticatedUserId
	 *        The authenticated user performing the import.
	 * @return status message for user.
	 */
	String importFromFile(String fromFileName, InputStream fromFileStream, String uploadType, Site toSite, String authenticatedUserId);
}
