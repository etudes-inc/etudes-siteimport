/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/siteimport/trunk/siteimport-api/api/src/java/org/etudes/siteimport/api/SiteImportService.java $
 * $Id: SiteImportService.java 6855 2013-12-24 00:38:27Z ggolden $
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

import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.site.api.Site;

/**
 * SiteImportService ...
 */
public interface SiteImportService
{
	/**
	 * Get an array of the tools that the archived site has that are involved in site import, in our tool presentation order.
	 * 
	 * @param archiveSiteId
	 *        The archived site id.
	 * @return The array of tool ids.
	 */
	String[] getArchiveToolsForImport(String archiveSiteId);

	/**
	 * Get an array of the tools that the site has that are involved in site import, in our tool presentation order.
	 * 
	 * @param site
	 *        The site.
	 * @return The array of tool ids.
	 */
	String[] getSiteToolsForImport(Site site);

	/**
	 * Import from the archived site.
	 * 
	 * @param archiveSiteId
	 *        The archived site id.
	 * @param tools
	 *        An array of tool ids to import.
	 * @param site
	 *        The site.
	 */
	void importFromArchive(String archiveSiteId, String[] tools, Site toSite);

	/**
	 * Import content from one site to another, assuring that the destination site has needed tools installed.
	 * 
	 * @param userId
	 *        The user performing the import.
	 * @param fromSite
	 *        The source site.
	 * @param tools
	 *        An array of tool ids to import.
	 * @param toSite
	 *        The destination site.
	 * @throws PermissionException
	 *         if the user does not have permission to read the source and write to the destionation.
	 */
	void importFromSite(String userId, Site fromSite, String[] tools, Site toSite) throws PermissionException;

	/**
	 * Register an importer.
	 * 
	 * @param importer
	 *        The importer.
	 */
	void registerImporter(SiteImporter importer);

	/**
	 * Unregister an importer.
	 * 
	 * @param importer
	 *        The importer.
	 */
	void unregisterImporter(SiteImporter importer);

	/**
	 * If the site does not have the current tool structure, update it.
	 * 
	 * @param userId
	 *        The user id making the request.
	 * @param site
	 *        The site to check / convert.
	 * @return true if the site was changed, false if not.
	 */
	boolean updateSiteTools(String userId, Site site);
}
