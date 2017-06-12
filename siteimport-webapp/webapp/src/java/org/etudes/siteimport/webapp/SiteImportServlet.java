/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/siteimport/trunk/siteimport-webapp/webapp/src/java/org/etudes/siteimport/webapp/SiteImportServlet.java $
 * $Id: SiteImportServlet.java 5927 2013-09-11 23:01:47Z ggolden $
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

package org.etudes.siteimport.webapp;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.siteimport.api.FileImportService;
import org.etudes.siteimport.api.SiteImportService;
import org.sakaiproject.component.cover.ComponentManager;

/**
 * The SiteImportServlet servlet gives life to the FileImportService and SiteImportService implementations.
 */
@SuppressWarnings("serial")
public class SiteImportServlet extends HttpServlet
{
	/** Our log. */
	private static Log M_log = LogFactory.getLog(SiteImportServlet.class);

	/**
	 * Shutdown the servlet.
	 */
	public void destroy()
	{
		ComponentManager.loadComponent(FileImportService.class, null);
		ComponentManager.loadComponent(SiteImportService.class, null);

		M_log.info("destroy()");
		super.destroy();
	}

	/**
	 * Access the Servlet's information display.
	 * 
	 * @return servlet information.
	 */
	public String getServletInfo()
	{
		return "SiteImportServlet";
	}

	/**
	 * Initialize the servlet.
	 * 
	 * @param config
	 *        The servlet config.
	 * @throws ServletException
	 */
	public void init(ServletConfig config) throws ServletException
	{
		super.init(config);

		FileImportService fileImportService = new FileImportServiceImpl();
		ComponentManager.loadComponent(FileImportService.class, fileImportService);

		SiteImportService siteImportService = new SiteImportServiceImpl();
		ComponentManager.loadComponent(SiteImportService.class, siteImportService);

		M_log.info("init()");
	}
}
