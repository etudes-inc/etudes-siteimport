/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/siteimport/trunk/siteimport-api/api/src/java/org/etudes/siteimport/api/SiteImporter.java $
 * $Id: SiteImporter.java 6366 2013-11-21 17:13:28Z ggolden $
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

/**
 * One that participates in site import.
 */
public interface SiteImporter
{

	/**
	 * Import my content from one site to another,
	 * 
	 * @param userId
	 *        The id of the user running the import.
	 * @param fromSite
	 *        The source site id.
	 * @param toSite
	 *        The destination site id.
	 */
	void importFromSite(String userId, String fromSite, String toSite);

	/**
	 * @return the tool id supported for import from site.
	 */
	String getToolId();
}
