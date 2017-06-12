/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/siteimport/trunk/siteimport-webapp/webapp/src/java/org/etudes/siteimport/webapp/SiteImportServiceImpl.java $
 * $Id: SiteImportServiceImpl.java 10795 2015-05-10 05:04:05Z rashmim $
 ***********************************************************************************
 *
 * Copyright (c) 2013, 2014, 2015 Etudes, Inc.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.archives.api.ArchivesService;
import org.etudes.homepage.api.HomePageService;
import org.etudes.siteimport.api.SiteImportService;
import org.etudes.siteimport.api.SiteImporter;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.content.api.ContentHostingService;
import org.sakaiproject.entity.api.EntityManager;
import org.sakaiproject.entity.api.EntityProducer;
import org.sakaiproject.entity.api.EntityTransferrer;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.site.api.Group;
import org.sakaiproject.site.api.PubDatesService;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SitePage;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.site.api.ToolConfiguration;
import org.sakaiproject.tool.api.Tool;
import org.sakaiproject.tool.api.ToolManager;
import org.sakaiproject.util.ArrayUtil;

/**
 * SiteImportServiceImpl handles import of content from one site to another.
 */
public class SiteImportServiceImpl implements SiteImportService
{
	/** Our log. */
	private static Log M_log = LogFactory.getLog(SiteImportServiceImpl.class);

	/** Map of tool id -> importer. */
	protected Map<String, SiteImporter> importers = new HashMap<String, SiteImporter>();

	/** the tools that do site import - in desired presentation order. */
	protected String[] siteImportToolIds =
	{ "e3.homepage", "sakai.coursemap", "sakai.schedule", "sakai.announcements", "sakai.chat", "sakai.syllabus", "sakai.melete", "sakai.mneme",
			"sakai.jforum.tool", "sakai.resources", "sakai.gradebook.tool", "e3.gradebook", "sakai.iframe", "e3.configure", "e3.siteroster" };

	/**
	 * Construct
	 */
	public SiteImportServiceImpl()
	{
	}

	/**
	 * {@inheritDoc}
	 */
	public String[] getArchiveToolsForImport(String archiveSiteId)
	{
		Set<String> tools = archivesService().getToolIds(archiveSiteId);

		// assure home, setup and roster
		if (!tools.contains("e3.homepage")) tools.add("e3.homepage");
		if (!tools.contains("e3.configure")) tools.add("e3.configure");
		if (!tools.contains("e3.siteroster")) tools.add("e3.siteroster");

		// use the order of the siteImportToolIds array
		List<String> orderedList = new ArrayList<String>();
		for (String toolId : this.siteImportToolIds)
		{
			if (tools.contains(toolId)) orderedList.add(toolId);
		}

		String[] rv = new String[0];
		return orderedList.toArray(rv);
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	public String[] getSiteToolsForImport(Site site)
	{
		List<String> list = new ArrayList<String>();

		// get the site's tool names
		List<ToolConfiguration> tools = (List<ToolConfiguration>) site.getTools(this.siteImportToolIds);

		boolean seenIframe = false;
		boolean seenHome = false;
		boolean seenConfigure = false;
		boolean seenRoster = false;
		for (ToolConfiguration t : tools)
		{
			// ignore news
			if (t.getToolId().equals("sakai.news"))
			{
				continue;
			}
			else if (t.getToolId().equals("e3.homepage"))
			{
				seenHome = true;
			}
			else if (t.getToolId().equals("e3.configure"))
			{
				seenConfigure = true;
			}
			else if (t.getToolId().equals("e3.siteroster"))
			{
				seenRoster = true;
			}

			if (t.getToolId().equals("sakai.iframe"))
			{
				if (!seenIframe)
				{
					list.add(t.getToolId());
				}
				seenIframe = true;
			}
			else
			{
				list.add(t.getToolId());
			}
		}

		// assure we have these, if we didn't see them in the real list
		if (!seenHome)
		{
			list.add("e3.homepage");
		}
		if (!seenConfigure)
		{
			list.add("e3.configure");
		}
		if (!seenRoster)
		{
			list.add("e3.siteroster");
		}

		// use the order of the siteImportToolIds array
		List<String> orderedList = new ArrayList<String>();
		for (String toolId : this.siteImportToolIds)
		{
			if (list.contains(toolId)) orderedList.add(toolId);
		}

		String[] rv = new String[0];
		return orderedList.toArray(rv);
	}

	/**
	 * {@inheritDoc}
	 */
	public void importFromArchive(String archiveSiteId, String[] tools, Site toSite)
	{
		// TODO: auth

		if ((tools.length == 1) && (tools[0].equals("all")))
		{
			// get the archived tools, and add the missing "sakai.siteinfo"
			Set<String> toolsSet = archivesService().getToolIds(archiveSiteId);
			toolsSet.add("e3.siteroster");
			toolsSet.add("e3.configure");
			tools = toolsSet.toArray(tools);
		}

		// make sure the site has all the tools it needs
		for (String toolId : tools)
		{
			if (assureTool(toolId, toSite))
			{
				// refresh the site
				try
				{
					toSite = this.siteService().getSite(toSite.getId());
				}
				catch (IdUnusedException e)
				{
					M_log.warn("importFromArchive - reaquiring site: " + toSite.getId() + " : " + e);
				}
			}
		}

		Set<String> toolsSet = new HashSet<String>();
		toolsSet.addAll(Arrays.asList(tools));

		try
		{
			archivesService().importSiteImmediate(archiveSiteId, toSite.getId(), toolsSet);
		}
		catch (PermissionException e)
		{
			M_log.warn("importFromArchive: site: " + toSite.getId() + " : " + e);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public void importFromSite(String userId, Site fromSite, String[] tools, Site toSite) throws PermissionException
	{
		// TODO: auth

//		// convert any old sites
//		if (updateSiteTools(userId, toSite))
//		{
//			// refresh the site
//			try
//			{
//				toSite = this.siteService().getSite(toSite.getId());
//			}
//			catch (IdUnusedException e)
//			{
//				M_log.warn("importFromSite - reaquiring site: " + toSite.getId() + " : " + e);
//			}
//		}

		// tools as list
		if ((tools.length == 1) && (tools[0].equals("all"))) tools = getSiteToolsForImport(fromSite);

		// scan for groups, resources, coursemap, collect the rest
		boolean importSiteInfo = false;
		boolean importResources = false;
		boolean importCoursemap = false;
		boolean importGroups = false;
		boolean importHomePage = false;
		boolean importe3Gradebook = false;
		
		List<String> toolsList = new ArrayList<String>();
		for (String toolId : tools)
		{
			if (toolId.equals("sakai.siteinfo") || toolId.equals("e3.configure"))
				importSiteInfo = true;
			else if (toolId.equals("e3.siteroster"))
				importGroups = true;
			else if (toolId.equals("sakai.resources"))
				importResources = true;
			else if (toolId.equals("sakai.coursemap"))
				importCoursemap = true;
			else if (toolId.equals("e3.gradebook"))
				importe3Gradebook = true;
			else if (toolId.equals("sakai.gradebook.tool") && !toolsList.contains("e3.gradebook"))
				toolsList.add("e3.gradebook");
			else if (toolId.equals("e3.homepage"))
			{
				importHomePage = true;
				toolsList.add(toolId);
			}
			else
				toolsList.add(toolId);			
		}

		// import groups (and site configuration details) first
		if (importGroups)
		{
			if (importSiteGroups(fromSite, toSite))
			{
				// refresh the site
				try
				{
					toSite = this.siteService().getSite(toSite.getId());
				}
				catch (IdUnusedException e)
				{
					M_log.warn("importFromSite - reaquiring site: " + toSite.getId() + " : " + e);
				}
			}
		}

		if (importSiteInfo)
		{
			// configuration details
			boolean changed = false;
			if (!toSite.userEditedPubDates())
			{
				// Note: this does NOT save the site!
				this.pubDatesService().transferDates(fromSite.getId(), toSite);
				changed = true;
			}
			if (!toSite.userEditedSkin())
			{
				toSite.setSkin(fromSite.getSkin());
				toSite.setIconUrl(fromSite.getIconUrl());
				changed = true;
			}

			changed = importSiteExternalServices(fromSite, toSite) || changed;
			
			if (changed)
			{
				// save the site
				try
				{
					this.siteService().save(toSite);
				}
				catch (IdUnusedException e)
				{
					M_log.warn("importFromSite - saving site: " + toSite.getId() + " : " + e);
				}
				catch (PermissionException e)
				{
					M_log.warn("importFromSite - saving site: " + toSite.getId() + " : " + e);
				}

				// refresh the site
				try
				{
					toSite = this.siteService().getSite(toSite.getId());
				}
				catch (IdUnusedException e)
				{
					M_log.warn("importFromSite - reaquiring site: " + toSite.getId() + " : " + e);
				}
			}
		}

		// next, resources
		if (importResources)
		{
			if (assureTool("sakai.resources", toSite))
			{
				// refresh the site
				try
				{
					toSite = this.siteService().getSite(toSite.getId());
				}
				catch (IdUnusedException e)
				{
					M_log.warn("doSiteImport - reaquiring site: " + toSite.getId() + " : " + e);
				}
			}

			String fromSiteCollectionId = this.contentHostingService().getSiteCollection(fromSite.getId());
			String siteCollectionId = this.contentHostingService().getSiteCollection(toSite.getId());

			transferCopyEntities(userId, "sakai.resources", fromSiteCollectionId, siteCollectionId);
		}

		// site resources if resource or homepage is selected (TODO: or any other tool using site resources)
		if (importResources || importHomePage)
		{
			transferCopyEntities(userId, "e3.resources", fromSite.getId(), toSite.getId());
		}

		// all the others
		for (String toolId : toolsList)
		{
			if (assureTool(toolId, toSite))
			{
				// refresh the site
				try
				{
					toSite = this.siteService().getSite(toSite.getId());
				}
				catch (IdUnusedException e)
				{
					M_log.warn("doSiteImport - reaquiring site: " + toSite.getId() + " : " + e);
				}
			}

			transferCopyEntities(userId, toolId, fromSite.getId(), toSite.getId());
		}
		// e3 gradebook after mneme and jforum
		if (importe3Gradebook)
		{
			if (assureTool("e3.gradebook", toSite))
			{
				// refresh the site
				try
				{
					toSite = this.siteService().getSite(toSite.getId());
				}
				catch (IdUnusedException e)
				{
					M_log.warn("doSiteImport - reaquiring site: " + toSite.getId() + " : " + e);
				}
			}

			transferCopyEntities(userId, "e3.gradebook", fromSite.getId(), toSite.getId());
		}
		
		// coursemap last
		if (importCoursemap)
		{
			if (assureTool("sakai.coursemap", toSite))
			{
				// refresh the site
				try
				{
					toSite = this.siteService().getSite(toSite.getId());
				}
				catch (IdUnusedException e)
				{
					M_log.warn("doSiteImport - reaquiring site: " + toSite.getId() + " : " + e);
				}
			}

			transferCopyEntities(userId, "sakai.coursemap", fromSite.getId(), toSite.getId());
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public void registerImporter(SiteImporter importer)
	{
		this.importers.put(importer.getToolId(), importer);
	}

	/**
	 * {@inheritDoc}
	 */
	public void unregisterImporter(SiteImporter importer)
	{
		this.importers.remove(importer);
	}

	/**
	 * If the site does not have the current tool structure, update it.
	 * 
	 * @param site
	 *        The site to check / convert.
	 * @return true if the site was changed, false if not.
	 */
	@SuppressWarnings("unchecked")
	public boolean updateSiteTools(String userId, Site site)
	{
		// if it has the old site info tool
		if (site.getToolForCommonId("sakai.siteinfo") != null)
		{
			// change the home page
			SitePage homePage = (SitePage) site.getPages().get(0);
			if ((homePage != null) && ("Home".equalsIgnoreCase(homePage.getTitle())))
			{
				List<ToolConfiguration> tools = new ArrayList<ToolConfiguration>();
				tools.addAll(homePage.getTools());
				for (ToolConfiguration t : tools)
				{
					homePage.removeTool(t);
				}

				homePage.setLayout(0);
				Tool homeTool = toolManager().getTool("e3.homepage");
				homePage.addTool(homeTool);
			}

			// adjust the site info page
			String[] desiredToolIds = new String[1];
			desiredToolIds[0] = "sakai.siteinfo";
			List<SitePage> pages = site.getPages();
			for (SitePage page : pages)
			{
				Collection<ToolConfiguration> found = page.getTools(desiredToolIds);
				if (!found.isEmpty())
				{
					List<ToolConfiguration> tools = new ArrayList<ToolConfiguration>();
					tools.addAll(page.getTools());
					for (ToolConfiguration t : tools)
					{
						page.removeTool(t);
					}

					Tool configureTool = toolManager().getTool("e3.configure");
					page.addTool(configureTool);
					page.setTitle(configureTool.getTitle());

					break;
				}
			}

			// add a roster page
			Tool rosterTool = toolManager().getTool("e3.siteroster");
			SitePage page = site.addPage();
			page.setTitle(rosterTool.getTitle());
			page.addTool(rosterTool);

			// save
			try
			{
				siteService().save(site);
			}
			catch (IdUnusedException e)
			{
				M_log.warn("updateSiteTools -  site: " + site.getId() + " : " + e);
			}
			catch (PermissionException e)
			{
				M_log.warn("updateSiteTools -  site: " + site.getId() + " : " + e);
			}

			// move site info URL and description to home page items
			homePageService().convertFromSiteInfo(userId, site.getId(), false);

			return true;
		}

		return false;
	}

	/**
	 * @return The ArchiveService, via the component manager.
	 */
	private ArchivesService archivesService()
	{
		return (ArchivesService) ComponentManager.get(ArchivesService.class);
	}

	/**
	 * @return The ContentHostingService, via the component manager.
	 */
	private ContentHostingService contentHostingService()
	{
		return (ContentHostingService) ComponentManager.get(ContentHostingService.class);
	}
	/**
	 * @return The HomePageService, via the component manager.
	 */
	private HomePageService homePageService()
	{
		return (HomePageService) ComponentManager.get(HomePageService.class);
	}

	/**
	 * @return The EntityManager, via the component manager.
	 */
	private EntityManager entityManager()
	{
		return (EntityManager) ComponentManager.get(EntityManager.class);
	}

	/**
	 * @return The PubDatesService, via the component manager.
	 */
	private PubDatesService pubDatesService()
	{
		return (PubDatesService) ComponentManager.get(PubDatesService.class);
	}

	/**
	 * @return The SiteService, via the component manager.
	 */
	private SiteService siteService()
	{
		return (SiteService) ComponentManager.get(SiteService.class);
	}

	/**
	 * @return The ToolManager, via the component manager.
	 */
	private ToolManager toolManager()
	{
		return (ToolManager) ComponentManager.get(ToolManager.class);
	}

	/**
	 * Make sure the site has the tool - except for web content and news, which don't need to be added before import happens.
	 * 
	 * @param toolId
	 *        The toolId to check, and add if missing.
	 * @param site
	 *        The site.
	 * @return true if the site was changed, false if not.
	 */
	protected boolean assureTool(String toolId, Site site)
	{
		if (toolId.equals("sakai.iframe") || toolId.equals("sakai.news") || toolId.equals("sakai.siteinfo") || toolId.equals("e3.configure") || toolId.equals("e3.siteroster") || toolId.equals("sakai.gradebook.tool")) return false;

		ToolConfiguration existing = site.getToolForCommonId(toolId);
		if (existing != null) return false;

		Tool tool = toolManager().getTool(toolId);
		if (tool != null)
		{
			SitePage page = site.addPage();
			page.setTitle(tool.getTitle());
			page.addTool(tool);

			// save
			try
			{
				siteService().save(site);
			}
			catch (IdUnusedException e)
			{
				M_log.warn("assureTool -  site: " + site.getId() + " : " + e);
			}
			catch (PermissionException e)
			{
				M_log.warn("assureTool -  site: " + site.getId() + " : " + e);
			}

			return true;
		}

		return false;
	}

	/**
	 * Transfer external services (matched by the title) from the site to new site.
	 * 
	 * @param fromSite
	 *        The site to get external services from.
	 * @param site
	 *        The site to add external services to.
	 * @return true if changed, false if not.
	 */
	protected boolean importSiteExternalServices(Site fromSite, Site site)
	{
		boolean changed = false;
		
		Collection<ToolConfiguration> fromTcs = fromSite.getTools("sakai.iframe");
		Collection<ToolConfiguration> siteTcs = site.getTools("sakai.iframe");
		
		for (ToolConfiguration ftc : fromTcs)
		{
			String fromPageTitle = ftc.getContainingPage().getTitle();
			if ("Yes".equalsIgnoreCase(ftc.getPlacementConfig().getProperty("thirdPartyService")))
			{
				String toolTitle = ftc.getTitle();
				boolean found = false;
				for (ToolConfiguration tc : siteTcs)
				{
					String tcPageTitle = tc.getContainingPage().getTitle();
					if (fromPageTitle.equalsIgnoreCase(tcPageTitle) || ftc.getTitle().equalsIgnoreCase(tc.getTitle()))
					{
						found = true;
						break;
					}
				}
				if (!found)
				{
					changed = true;
					Tool tr = toolManager().getTool("sakai.iframe");
					SitePage page = site.addPage();
					page.setTitle(ftc.getContainingPage().getTitle());
					ToolConfiguration tool = page.addTool();
					tool.setTool("sakai.iframe", tr);
					tool.setTitle(toolTitle);
					
					Properties config = tool.getPlacementConfig();
					Iterator keys = ftc.getPlacementConfig().keySet().iterator();
					// transfer all properties including LTI if the tool hasn't set for its own
					while (keys.hasNext())
					{
						String k = (String) keys.next();
						if (!config.containsKey(k)) config.setProperty(k, ftc.getPlacementConfig().getProperty(k));
					}
					page.setPopup(ftc.getContainingPage().isPopUp());					
				}
			}
		}
		
		return changed;
	}
	
	/**
	 * If the fromSite has any groups that are not in the site (matched by title), add these to the site.
	 * 
	 * @param fromSite
	 *        The site to get groups from.
	 * @param site
	 *        The site to add groups to.
	 * @return true if changed, false if not.
	 */
	@SuppressWarnings("unchecked")
	protected boolean importSiteGroups(Site fromSite, Site site)
	{
		boolean changed = false;
		for (Group fromGroup : (Collection<Group>) (fromSite.getGroups()))
		{
			// skip sections
			if (fromGroup.getProperties().getProperty("sections_category") != null) continue;

			// skip if site already has group by this title
			boolean found = false;
			for (Group group : (Collection<Group>) (site.getGroups()))
			{
				if (group.getTitle().equals(fromGroup.getTitle()))
				{
					found = true;
					break;
				}
			}
			if (!found)
			{
				Group group = site.addGroup();
				group.getProperties().addProperty("group_prop_wsetup_created", Boolean.TRUE.toString());
				group.setTitle(fromGroup.getTitle());
				group.setDescription(fromGroup.getDescription());
				changed = true;
			}
		}

		if (changed)
		{
			// save the site
			try
			{
				this.siteService().save(site);
			}
			catch (IdUnusedException e)
			{
				M_log.warn("importSiteGroups - saving site: " + site.getId() + " : " + e);
			}
			catch (PermissionException e)
			{
				M_log.warn("importSiteGroups - saving site: " + site.getId() + " : " + e);
			}
		}

		return changed;
	}

	/**
	 * Transfer a copy of all entities from another context for any entity producer that claims this tool id.
	 * 
	 * @param userId
	 *        The id of the user doing them import.
	 * @param toolId
	 *        The tool id.
	 * @param fromContext
	 *        The context to import from.
	 * @param toContext
	 *        The context to import into.
	 */
	@SuppressWarnings("unchecked")
	protected void transferCopyEntities(String userId, String toolId, String fromContext, String toContext)
	{
		// for all entity producers that do the entityTransferrer
		for (EntityProducer ep : (List<EntityProducer>) (this.entityManager().getEntityProducers()))
		{
			if (ep instanceof EntityTransferrer)
			{
				try
				{
					EntityTransferrer et = (EntityTransferrer) ep;

					// if this producer claims this tool id
					if (ArrayUtil.contains(et.myToolIds(), toolId))
					{
						et.transferCopyEntities(fromContext, toContext, null);
					}
				}
				catch (Throwable t)
				{
					M_log.warn("transferCopyEntities from: " + fromContext + " to: " + toContext + " : " + t);
				}
			}
		}

		// for anyone registered
		SiteImporter si = this.importers.get(toolId);
		if (si != null)
		{
			si.importFromSite(userId, fromContext, toContext);
		}
	}
}
