/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/siteimport/trunk/siteimport-webapp/webapp/src/java/org/etudes/siteimport/webapp/ImportOtherLMSECollege.java $
 * $Id: ImportOtherLMSECollege.java 5941 2013-09-17 20:48:35Z ggolden $
 ***********************************************************************************
 *
 * Copyright (c) 2012, 2013 Etudes, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You may
 * obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 **********************************************************************************/

package org.etudes.siteimport.webapp;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.XPath;
import org.etudes.api.app.jforum.Forum;
import org.etudes.api.app.jforum.Topic;
import org.etudes.api.app.melete.ModuleObjService;
import org.etudes.api.app.melete.SectionObjService;
import org.etudes.mneme.api.Assessment;
import org.etudes.mneme.api.AssessmentPermissionException;
import org.etudes.mneme.api.AssessmentType;
import org.etudes.mneme.api.AttachmentService;
import org.etudes.mneme.api.EssayQuestion;
import org.etudes.mneme.api.Part;
import org.etudes.mneme.api.Pool;
import org.etudes.mneme.api.Question;
import org.etudes.mneme.api.QuestionPick;
import org.etudes.util.HtmlHelper;
import org.sakaiproject.content.api.ResourceType;
import org.sakaiproject.content.cover.ContentHostingService;
import org.sakaiproject.content.cover.ContentTypeImageService;
import org.sakaiproject.entity.api.Reference;
import org.sakaiproject.exception.IdUnusedException;

import org.sakaiproject.tool.cover.ToolManager;
import org.sakaiproject.util.FormattedText;
import org.sakaiproject.util.Validator;

public class ImportOtherLMSECollege extends BaseImportOtherLMS
{
	Map<String, String> uris = new HashMap();

	public ImportOtherLMSECollege(Document backUpDoc, String unzipBackUpLocation, String siteId, String userId)
	{
		super(siteId, unzipBackUpLocation, userId);
		this.backUpDoc = backUpDoc;
	}

	/**
	 * abstract method implementation
	 */
	public String transferEntities()
	{
		String success_message = "You have successfully imported material from the eCollege export file.";
		Element bkRoot = backUpDoc.getRootElement();

		// set namespace uris
		List<Namespace> names = bkRoot.declaredNamespaces();

		for (Namespace u : names)
		{
			String prefix = u.getPrefix();
			// this is important. declaration is missing prefix for imscp.
			if (prefix.length() == 0) prefix = "imscp";
			uris.put(prefix, u.getURI());
		}
		
		// find tools tag and import resources
		XPath xpathTools = backUpDoc.createXPath("/imscp:manifest/eclgcp:tools/eclgcp:docsharing");
		xpathTools.setNamespaceURIs(uris);
		Element eleDocSharing = (Element) xpathTools.selectSingleNode(backUpDoc);

		if (eleDocSharing != null && eleDocSharing.elements("docsharingentry").size() > 0)
		{
			addResourcesTool();
			pushAdvisor();
			List<Element> docEntries = eleDocSharing.elements("docsharingentry");
			for (Element d : docEntries)
			{
				buildResourceItem(d);
			}
			popAdvisor();
		}      

        XPath xpathToolsLinks = backUpDoc.createXPath("/imscp:manifest/eclgcp:tools/eclgcp:webliography/eclgmd:weblioentries");
		xpathToolsLinks.setNamespaceURIs(uris);
		Element eleWebLink = (Element) xpathToolsLinks.selectSingleNode(backUpDoc);

		if (eleWebLink != null && eleWebLink.elements("weblioentry").size() > 0)
		{
			addResourcesTool();
			pushAdvisor();
			List<Element> webLinkEntries = eleWebLink.elements("weblioentry");
			String collectionId = ContentHostingService.getSiteCollection(this.siteId);
			String weblioFolder = createSubFolderCollection(collectionId, "Webliography", "Folder to store webliographies", null);
			for (Element l : webLinkEntries)
			{
				buildResourceLinkItem(weblioFolder, l);
			}
			popAdvisor();
		}      
		
		// site description
		XPath xpathCoursePref = backUpDoc.createXPath("/imscp:manifest/eclgcp:coursepreferences/eclgcp:coursegeneralinfo/eclgmd:coursedescription");
		xpathCoursePref.setNamespaceURIs(uris);
		Element elemSiteDesc = (Element) xpathCoursePref.selectSingleNode(backUpDoc);
		if(elemSiteDesc != null)
		{
			String siteDesc = elemSiteDesc.element("langstring").getTextTrim();
			addSiteDescription(siteDesc);
		}
		// find unit heading
		XPath xpathCourseHeading = backUpDoc.createXPath("/imscp:manifest/eclgcp:coursepreferences/eclgcp:coursegeneralinfo/eclgmd:unitheading");
		xpathCourseHeading.setNamespaceURIs(uris);
		Element elemUnitHeading = (Element) xpathCourseHeading.selectSingleNode(backUpDoc);
	
		String unitHeadingTitle = "Week";
		if (elemUnitHeading != null) unitHeadingTitle = elemUnitHeading.element("langstring").getTextTrim();
		
		// find organization tag
		XPath xpathItems = backUpDoc.createXPath("/imscp:manifest/imscp:organizations/imscp:organization");
		xpathItems.setNamespaceURIs(uris);
		Element eleOrg = (Element) xpathItems.selectSingleNode(backUpDoc);

		if (eleOrg == null) return success_message;

		initializeServices();
		int count = -1;
		// process item elements
		for (Iterator<?> iter = eleOrg.elementIterator("item"); iter.hasNext();)
		{
			Element organizationItem = (Element) iter.next();
			count ++;
			XPath courseItemPath = organizationItem.createXPath(".//imscp:metadata/eclgmd:extensions/eclgmd:coursehome");
			courseItemPath.setNamespaceURIs(uris);
			Element courseNode = (Element) courseItemPath.selectSingleNode(organizationItem);
			//uncomment it if users want syllabus from coursehome.vized -- rashmim
		//	if (courseNode != null) buildSyllabusItemsfromCourseContentResource(organizationItem);

			// look for item with syllabus
			XPath itemPath = organizationItem.createXPath(".//imscp:metadata/eclgmd:extensions/eclgmd:coursehome/eclgmd:syllabus");
			itemPath.setNamespaceURIs(uris);
			Element syllabusNode = (Element) itemPath.selectSingleNode(organizationItem);

			if (syllabusNode != null) buildSyllabusItems(syllabusNode);
			
			// look for announcements
			itemPath = null;
			itemPath = organizationItem.createXPath(".//imscp:metadata/eclgmd:extensions/eclgmd:coursehome/eclgmd:announcements/eclgmd:announcement");
			itemPath.setNamespaceURIs(uris);
			List<Element> anncNodes = itemPath.selectNodes(organizationItem);

			if (anncNodes != null)
			{
				for (Iterator<?> anncIter = anncNodes.iterator(); anncIter.hasNext();)
				{
					Element announcementItem = (Element) anncIter.next();
					buildAnnouncementItems(announcementItem);
				}
			}
			// if item title is null or &nbsp; then give it a default title
			String itemTitle = organizationItem.element("title").getText();
			if (itemTitle != null) itemTitle = itemTitle.trim();
			if (itemTitle == null || itemTitle.length() == 0 || itemTitle.equalsIgnoreCase("&nbsp;"))
			{
				itemTitle = unitHeadingTitle + " " + count;
				organizationItem.element("title").setText(itemTitle);
			}
			// default title end
			ModuleObjService module = buildMeleteModule(organizationItem);
			if (organizationItem.element("item") != null) buildMeleteSection(organizationItem, module);		
		}
			
		// get First resource and then process extra file tags		
		Element organizationItem = eleOrg.element("item");
		Element firstResource = getResourceElement(organizationItem);
		if (firstResource != null)
		{
			List<Element> files = firstResource.elements("file");
			if (files != null) addResourcesTool();
		
			for (Element e : files)
			{
				String fileName = e.attributeValue("href");
				//skip vized and xml file
				transferExtraCourseFiles(e,fileName, null, "");
			}
		
		}
		return success_message;
	}

	/**
	 * 
	 * @param announcementItem
	 * @param uris
	 */
	private void buildAnnouncementItems(Element announcementItem)
	{
		try
		{
			Element resource = getResourceElement(announcementItem);

			String subject = "Untitled Announcement";
			String body = null;
			Date releaseDate = null;

			if (announcementItem.element("title") != null && announcementItem.element("title").element("langstring") != null)
			{
				subject = announcementItem.element("title").element("langstring").getTextTrim();
				// strip off html tags from the title
				subject = FormattedText.convertFormattedTextToPlaintext(subject);
			}

			String hrefValue = resource.attributeValue("href");
			body = readECollegeFile(hrefValue, resource.attributeValue("base"));

			if (announcementItem.element("startdatetime") != null)
			{
				Element starttime = announcementItem.element("startdatetime");
				String releaseDateStr = starttime.element("datetime").getTextTrim();
				releaseDate = getShortDateFromString(releaseDateStr);
			}
			buildAnnouncement(subject, body, releaseDate, resource.attributeValue("base"), resource.elements("file"));

		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

	}

	/**
	 * If the topic title starts with Group then we are assuming that its divided for groups and Category will become gradable.
	 * 
	 * @return true if topic titles starts with Group
	 */
	private boolean checkGradableCategory(String discussionDataFile)
	{
		boolean byGroups = false;
		try
		{
			Document contentsDOM = XMLHelper.getSaxReader().read(new ByteArrayInputStream(discussionDataFile.getBytes("UTF-8")));
			Element root = contentsDOM.getRootElement();

			List<Element> topicsList = root.selectNodes("topics/topic");
			if (topicsList == null || topicsList.size() == 0) return byGroups;
			int count = 0;
			for (Iterator<Element> iterMsg = topicsList.iterator(); iterMsg.hasNext();)
			{
				Element e = iterMsg.next();
				if (e.selectSingleNode("title").getText().startsWith("Group"))
				{
					count++;
					byGroups = true;
					if (count >= 2) break;
				}
			}
		}
		catch (Exception e)
		{
			// do nothing
		}
		return byGroups;
	}

	/**
	 * Check if topic title or text contains other than generic message "post your comments here"
	 * 
	 * @param discussionDataFile
	 * @return true if contains generic message
	 */
	private boolean checkAddTopics(String discussionDataFile)
	{
		boolean byForum = false;
		try
		{
			Document contentsDOM = XMLHelper.getSaxReader().read(new ByteArrayInputStream(discussionDataFile.getBytes()));
			Element root = contentsDOM.getRootElement();

			List<Element> topicsList = root.selectNodes("topics/topic");
			if (topicsList == null || topicsList.size() != 1) return byForum;

			if (topicsList.size() == 1)
			{
				Element e = topicsList.get(0);
				if (e.selectSingleNode("title").getText().contains("Post your comments here")
						|| e.selectSingleNode("text").getText().contains("Post your comments here")
						|| e.selectSingleNode("title").getText().contains("Post your responses here")) byForum = true;
			}
		}
		catch (Exception e)
		{
			// do nothing
		}
		return byForum;
	}

	/**
	 * If topics have Groups in title then create gradable category with normal forums 
	 * If topic titles or text contains Post your comments here or Post your responses here then create gradable forum 
	 * otherwise create grade by topics forum
	 * 
	 * @param organizationItem
	 * @return
	 */
	private void buildDiscussion(Element organizationItem)
	{
		String title = organizationItem.element("title").getText();
		String itemIdentifierRef = organizationItem.attributeValue("identifierref");
		XPath xpathResource = backUpDoc.createXPath("/imscp:manifest/imscp:resources/imscp:resource[@identifier='" + itemIdentifierRef + "']");
		xpathResource.setNamespaceURIs(uris);
		Element resource = (Element) xpathResource.selectSingleNode(backUpDoc);
		if (resource == null) return;
		
		org.etudes.api.app.jforum.User postedBy = jForumUserService.getBySakaiUserId(userId);
		String forumType = "REPLY_ONLY";
		String noGradePoints = null;
		String tenGradePoints = "10";
		Date openDate = null;
		Date dueDate = null;
		int noMinPosts = 0;
		int oneMinPosts = 1;

		try
		{
			String discussionDataFile = readECollegeFile(resource.attributeValue("href"), resource.attributeValue("base"));
			if(discussionDataFile == null || discussionDataFile.length() == 0) return;
			
			Map<String, String> contents = processECollegeDiscussionDataFile(discussionDataFile.getBytes("UTF-8"));
			if (contents == null || contents.size() == 0) return;

			// check if need to create gradable category
			boolean gradableCategory = checkGradableCategory(discussionDataFile);
			String subject = null;
			subject = contents.get("TITLE");
			if (subject.equalsIgnoreCase("Discussion") || subject.equalsIgnoreCase("Course Discussion"))
				subject = organizationItem.getParent().element("title").getText() + " " + subject;

			if (subject == null || subject.length() == 0) subject = title;

			Document contentsDOM = XMLHelper.getSaxReader().read(new ByteArrayInputStream(discussionDataFile.getBytes("UTF-8")));
			Element root = contentsDOM.getRootElement();

			List<Element> topicsList = root.selectNodes("topics/topic");

			if (topicsList == null || topicsList.size() == 0) return;

			// category
			int mainCategoryId = 0;
			if (gradableCategory)
			{
				mainCategoryId = buildDiscussionGradableCategory(subject, tenGradePoints, oneMinPosts);
				// As category description is missing create a section with description text
				if (contents.get("DESCRIPTION_TEXT") != null)
				{
					ModuleObjService module = buildMeleteModule(organizationItem.getParent());
					String sectionTitle = title + "-instructions";
					if (!sectionExists(sectionTitle, module))
					{
						SectionObjService section = buildSection(sectionTitle, module);
						section = buildTypeEditorSection(section, contents.get("DESCRIPTION_TEXT"), module, resource.attributeValue("base"),
								sectionTitle, resource.elements("file"), null);
					}
				}
			}
			else
				mainCategoryId = buildDiscussionCategory();
			if (mainCategoryId == 0) return;

			List<Forum> allForums = jForumForumService.getCategoryForums(mainCategoryId);
			int forumId = 0;

			if (gradableCategory)
			{
				// build "normal" forum
				for (Iterator<Element> iterMsg = topicsList.iterator(); iterMsg.hasNext();)
				{
					Element e = iterMsg.next();
					forumId = buildDiscussionForums(e.selectSingleNode("title").getText(), e.selectSingleNode("text").getText(), "NORMAL", "nograde",
							noGradePoints, openDate, dueDate, noMinPosts, mainCategoryId, userId, allForums);
				}
			}
			else
			{
				// default is grade by forum
				forumId = buildDiscussionForums(subject, contents.get("DESCRIPTION_TEXT"), forumType, "gradeForum", tenGradePoints, openDate,
						dueDate, oneMinPosts, mainCategoryId, userId, allForums);
				
				// if topic is different from generic message then add topics
				if (!checkAddTopics(discussionDataFile))
				{
					// check for message tag to create topics
					List<Topic> allDiscussions = jForumPostService.getForumTopics(forumId);

					for (Iterator<Element> iterMsg = topicsList.iterator(); iterMsg.hasNext();)
					{
						Element e = iterMsg.next();

						// since grade information is missing from Manifest we are by default giving 10 points and 1 minPost
						buildDiscussionTopics(e.selectSingleNode("title").getText(), e.selectSingleNode("text").getText(), "Normal",
								resource.attributeValue("base"), resource.elements("file"), null, noGradePoints, openDate, dueDate, noMinPosts,
								forumId, postedBy, allDiscussions);
					}
				}				
			}

		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	/**
	 * @param organizationItem
	 * @return
	 */
	private ModuleObjService buildMeleteModule(Element organizationItem)
	{
		List<ModuleObjService> existingModules = moduleService.getModules(siteId);

		String title = organizationItem.element("title").getText();
		String itemIdentifierRef = organizationItem.attributeValue("identifierref");
		String sectionText = null;
		List<Element> embedFiles = null;

		XPath xpathResource = backUpDoc.createXPath("/imscp:manifest/imscp:resources/imscp:resource[@identifier='" + itemIdentifierRef + "']");
		xpathResource.setNamespaceURIs(uris);
		Element resource = (Element) xpathResource.selectSingleNode(backUpDoc);
		if (resource == null) return null;
		if (resource.attributeValue("type") != null && resource.attributeValue("type").equals("text/html"))
		{
			String hrefValue = resource.attributeValue("href");
			sectionText = readECollegeFile(hrefValue, resource.attributeValue("base"));
			embedFiles = resource.elements("file");
		}
		ModuleObjService module = findOrAddModule(title, 0, sectionText, null, null,null, existingModules, resource.attributeValue("base"), embedFiles,
				null);
		return module;
	}

	/**
	 * 
	 * @param organizationItem
	 * @param module
	 */
	private void buildMeleteSection(Element organizationItem, ModuleObjService module)
	{
		try
		{
			for (Iterator<?> iter = organizationItem.elementIterator("item"); iter.hasNext();)
			{
				Element sectionItem = (Element) iter.next();

				// chck if discussion or section
				XPath itemPath = sectionItem.createXPath("imscp:metadata/eclgmd:extensions/eclgmd:itemsettings/eclgmd:contenttype");
				itemPath.setNamespaceURIs(uris);
				Element discussionNodeType = (Element) itemPath.selectSingleNode(sectionItem);
				if (discussionNodeType != null && ("Thread").equalsIgnoreCase(discussionNodeType.getTextTrim()))
				{
					buildDiscussion(sectionItem);
					continue;
				}
				
				// check if dropbox is true or false
				itemPath = sectionItem.createXPath(".//imscp:metadata/eclgmd:extensions/eclgmd:itemsettings/eclgmd:hasdropbox");
				itemPath.setNamespaceURIs(uris);
				Element dropBoxNodeType = (Element) itemPath.selectSingleNode(sectionItem);
				if (dropBoxNodeType != null && ("true").equals(dropBoxNodeType.getTextTrim()))
				{
					transferAssn(sectionItem);
					continue;
				}
				
				// TODO: if section Item has further child items then create subsections
				String title = sectionItem.element("title").getText();
				String itemIdentifierRef = sectionItem.attributeValue("identifierref");
				if (itemIdentifierRef == null || itemIdentifierRef.trim().length() == 0) continue;
				
				String sectionText = "";
				List<Element> embedFiles = null;

				if (module == null) continue;
				if (sectionExists(title, module)) continue;

				SectionObjService section = buildSection(title, module);
								
				XPath xpathResource = backUpDoc
						.createXPath("/imscp:manifest/imscp:resources/imscp:resource[@identifier='" + itemIdentifierRef + "']");
				xpathResource.setNamespaceURIs(uris);
				Element resource = (Element) xpathResource.selectSingleNode(backUpDoc);
		
				if (resource == null) return;
				
				if (resource.attributeValue("type") != null && resource.attributeValue("type").equals("text/html"))
				{
					String hrefValue = resource.attributeValue("href");
					sectionText = readECollegeFile(hrefValue, resource.attributeValue("base"));
					embedFiles = resource.elements("file");
					importedCourseFiles.add(hrefValue);
				}			
				else
				{
					// doc files etc type=application/msword
					String hrefValue = resource.attributeValue("href");
					sectionText = buildTypeLinkUploadResource(hrefValue, hrefValue, sectionText, resource.attributeValue("base"), resource.elements("file"));
				}
				// save section
				section = buildTypeEditorSection(section, sectionText, module, resource.attributeValue("base"), title, resource.elements("file"),
						null);
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	/**
	 * 
	 */
	protected void transferExtraCourseFiles(Element fileNode, String fileName, String collectionId, String parentName)
	{
		if (collectionId == null || collectionId.length() == 0) collectionId = "/group/" + siteId + "/";

		try
		{
			if (importedCourseFiles.contains(parentName + fileName)) return;

			if (fileName.contains("/"))
			{
				String collName = fileName.substring(0, fileName.indexOf("/"));
				String subCollectionId = createSubFolderCollection(collectionId, collName, "", null);
				transferExtraCourseFiles(fileNode, fileName.substring(fileName.indexOf("/") + 1), subCollectionId, parentName + collName + "/");
			}
			// if directory name skip it
			else if (collectionId != null && !fileName.contains("/") && fileName.contains(".")) 
			{
				// add file if not in mnemedocs, meletedocs or in group
				buildExtraResourceItem(fileNode, fileName, collectionId);
			}

		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	/**
	 * 
	 * @param fileNode
	 * @param fileName
	 * @param collectionId
	 */
	private void buildExtraResourceItem(Element fileNode, String fileName, String collectionId)
	{
		if (fileName.endsWith(".vized")) return;		
	
		try
		{
			String checkCollectionId = meleteCHService.getUploadCollectionId(siteId);
			meleteCHService.checkResource(checkCollectionId + fileName);
		}
		catch (IdUnusedException ide)
		{
			String checkCollectionId = "/private/mneme/" + siteId + "/docs/";
			Reference ref = attachmentService.getReference(checkCollectionId + fileName);
			if (ref.getId() == null && !ContentHostingService.isAttachmentResource(fileName))
			{
				String readFrom = fileNode.attributeValue("href");
				String res_mime_type = fileName.substring(fileName.lastIndexOf(".") + 1);
				res_mime_type = ContentTypeImageService.getContentType(res_mime_type);

				try
				{
					byte[] content_data = readDatafromFile(readFrom);
					if (content_data == null) return;
					pushAdvisor();
					buildResourceToolItem(collectionId, fileName, "", "", content_data, res_mime_type);
					popAdvisor();
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
		
	/**
	 * 
	 * @param resourceItemNode
	 */
	private void buildResourceItem(Element resourceItemNode) 
	{
		try
		{
			String title = null;
			String folderTitle = "/group/" + siteId + "/";
			String description = null;
			byte[] content_data = null;
			String res_mime_type = null;
			String creator = "";
			if (resourceItemNode.element("description") != null) description = resourceItemNode.element("description").getTextTrim();

			Element resourceElement = getResourceElement(resourceItemNode);
			if(resourceElement == null) return;
			String readFrom = resourceElement.attributeValue("href");
			res_mime_type = resourceElement.attributeValue("type");
			if (readFrom.contains(File.separator))
				title = readFrom.substring(readFrom.lastIndexOf(File.separator) + 1);
			else
				title = readFrom;

			/*
			 * create sub folders if href has / in it if (resourceElement != null && readFrom != null && readFrom.contains(File.separator)) { folderTitle = "/group/" + siteId + "/" + parentTitle + "/"; createFolder(folderTitle, ""); }
			 */

			content_data = readDatafromFile(readFrom);
			if (("text/html").equals(res_mime_type))
			{
				// for html or image files
				String data = new String(content_data, "UTF-8");
				data = HtmlHelper.cleanAndAssureAnchorTarget(data, true);
				data = findAndUploadEmbeddedMedia(data, "", resourceElement.elements("file"), null, null, "Resources");
				content_data = data.getBytes("UTF-8");
			}

			if (content_data == null) return;

			if (resourceItemNode.element("ownername") != null) creator = resourceItemNode.element("ownername").getTextTrim();

			buildResourceToolItem(folderTitle, title, description, creator, content_data, res_mime_type);
			
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	/**
	 * 
	 * @param weblioEntryItemNode
	 */
	private void buildResourceLinkItem(String folderTitle, Element weblioEntryItemNode) 
	{
		try
		{
			String title = null;
			String description = null;
			byte[] content_data = null;
			String res_mime_type = ResourceType.MIME_TYPE_URL;
			String webAddress = null;
			String creator = "";
			if (weblioEntryItemNode == null) return;
			if (weblioEntryItemNode.element("description") != null) description = weblioEntryItemNode.element("description").getTextTrim();

			if (weblioEntryItemNode.element("title") != null) title = weblioEntryItemNode.element("title").getTextTrim();

			webAddress = weblioEntryItemNode.element("webaddress").getTextTrim();
			if (!webAddress.startsWith("http://")) webAddress = "http://" + webAddress;
			if (webAddress != null) content_data = webAddress.getBytes("UTF-8");
			if (content_data == null) return;
			if (title == null) title = Validator.escapeResourceName(webAddress);
			
			if(weblioEntryItemNode.element("firstname") != null)
			creator = weblioEntryItemNode.element("firstname").getTextTrim();
			     
			if(weblioEntryItemNode.element("lastname") != null)
				creator = creator.concat(weblioEntryItemNode.element("lastname").getTextTrim());
				           
			buildResourceToolItem(folderTitle, title, description, creator, content_data, res_mime_type);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	/**
	 * 
	 * @param syllabusNode
	 */
	private void buildSyllabusItemsfromCourseContentResource(Element courseContentNode)
	{
		try
		{
			Element resource = getResourceElement(courseContentNode);
	
			String asset = "";
			boolean draft = false;
			String title = "Course Content Syllabus";
			String type = "item";
			List<Element> embedFiles = null;
			ArrayList<String> attach = new ArrayList<String>(0);
		
			List<Element> files = resource.elements("file");
			for (Element e : files)
			{
				String hrefValue = e.attributeValue("href");
				if (hrefValue.contains("Syllabus") || hrefValue.contains("syllabus") || hrefValue.contains("SYLLABUS")) attach.add(hrefValue);
			}
		
			String[] attachments = new String[attach.size()];

			// syllabus
			buildSyllabus(title, asset, draft, type, attach.toArray(attachments), resource.attributeValue("base"), embedFiles, null, null);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	/**
	 * 
	 * @param syllabusNode
	 */
	private void buildSyllabusItems(Element syllabusNode)
	{
		try
		{
			Element resource = getResourceElement(syllabusNode);
			if (resource == null) return;
			String asset = "";
			boolean draft = false;
			String title = "Untitled";
			String type = "item";
			String linkUrl = null;
			List<Element> embedFiles = null;
			ArrayList<String> attach = new ArrayList<String>(0);
			// title
			if (syllabusNode.element("syllabustitle") != null)
			{
				title = syllabusNode.element("syllabustitle").getText();
				if ((title.equals("") || title.equals("Untitled")) && syllabusNode.element("syllabustitle").element("langstring") != null)
				{
					title = syllabusNode.element("syllabustitle").element("langstring").getTextTrim();
				}
			}

			// type
			String hrefValue = resource.attributeValue("href");
			if (hrefValue != null && (hrefValue.startsWith("http://") || hrefValue.startsWith("https://")))
			{
				type = "redirectUrl";
				linkUrl = resource.attributeValue("href");
				if (linkUrl != null) attach.add(linkUrl);
			}
			else if (resource.attributeValue("type") != null && resource.attributeValue("type").equals("text/html"))
			{
				asset = readECollegeFile(hrefValue, resource.attributeValue("base"));
				embedFiles = resource.elements("file");
			}
			else
			{
				// attachments
				attach.add(hrefValue);
			}

			String[] attachments = new String[attach.size()];

			// syllabus
			buildSyllabus(title, asset, draft, type, attach.toArray(attachments), resource.attributeValue("base"), embedFiles, null, null);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	/**
	 * 
	 * @param node
	 * @param uris
	 * @return
	 */
	private Element getResourceElement(Element node)
	{
		String identifierRef = node.attributeValue("identifierref");
		XPath xpathResource = backUpDoc.createXPath("/imscp:manifest/imscp:resources/imscp:resource[@identifier='" + identifierRef + "']");
		xpathResource.setNamespaceURIs(uris);
		Element resource = (Element) xpathResource.selectSingleNode(backUpDoc);
		return resource;
	}

	/**
	 * 
	 * @param datFileContents
	 * @return
	 * @throws Exception
	 */
	private Map<String, String> processECollegeDiscussionDataFile(byte[] datFileContents) throws Exception
	{
		Map<String, String> contents = new HashMap<String, String>();
		if (datFileContents == null || datFileContents.length == 0) return contents;

		Document contentsDOM = XMLHelper.getSaxReader().read(new ByteArrayInputStream(datFileContents));
		Element root = contentsDOM.getRootElement();

		if (root.selectSingleNode("title") != null)
		{
			Element element = (Element) root.selectSingleNode("title");
			contents.put("TITLE", element.getTextTrim());
		}

		if (root.selectSingleNode("introductorytext") != null) contents.put("DESCRIPTION_TEXT", root.selectSingleNode("introductorytext").getText());

		return contents;
	}

	/**
	 * 
	 * @param fileName
	 * @param baseName
	 * @return
	 */
	private String readECollegeFile(String fileName, String baseName)
	{
		String content = null;
		if (fileName == null || fileName.length() == 0) return null;

		try
		{
			byte[] b = readDatafromFile(fileName);
			if (b == null) return null;
			content = new String(b, "UTF-8");
			if (content != null)
			{
				content = content.replaceAll("<radeditor>", "");
				content = content.replaceAll("</radeditor>", "");
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

		return content;
	}

	private void transferAssn(Element organizationItemElement)
	{
		String assmtType;
		String desc;
		Question question;
		Assessment assmt = null;
		Pool newPool = null;
		Part part = null;
		boolean poolExists = false;
		boolean assmtExists = false;

		try
		{
			Element resourceElement = getResourceElement(organizationItemElement);
			if (resourceElement == null)
			{
				return;
			}
			byte[] datFileContents = readDatafromFile(resourceElement.attributeValue("href"));
			if (datFileContents == null || datFileContents.length == 0) return;

			String title = "New Assignment";
			if (organizationItemElement.element("title") != null)
			{
				title = getElementValue(organizationItemElement.element("title"));
				if (title.trim().equalsIgnoreCase("Assignment")) title = getElementValue(organizationItemElement.getParent().element("title")) + " " + title;
			}

			// get all pools in the from context
			List<Pool> pools = poolService.getPools(siteId);
			List<String> poolNames = getPoolNames(pools);

			List<Assessment> assessments = assessmentService.getContextAssessments(siteId, null, Boolean.FALSE);
			List<String> assmtNames = getAssessmentNames(assessments);

			poolExists = checkTitleExists(title, poolNames);
			assmtExists = checkTitleExists(title, assmtNames);
			if (poolExists) return;
			try
			{
				if (!assmtExists)
				{
					// create test object
					assmt = assessmentService.newAssessment(siteId);
					assmt.setTitle(title);
					assmt.setType(AssessmentType.assignment);
				}

				newPool = poolService.newPool(siteId);
				if (newPool != null)
				{
					newPool.setTitle(title);
					question = questionService.newEssayQuestion(newPool);
					((EssayQuestion) question).setSubmissionType(EssayQuestion.EssaySubmissionType.both);

					if (resourceElement.attributeValue("type") != null && resourceElement.attributeValue("type").equals("text/html"))
					{
						desc = new String(datFileContents);
						if (desc != null && desc.length() != 0)
						{
							desc = findAndUploadEmbeddedMedia(desc, resourceElement.attributeValue("base"), resourceElement.elements("file"), null,
									null, "Mneme");
							desc = fixDoubleQuotes(desc);
							desc = HtmlHelper.cleanAndAssureAnchorTarget(desc, true);
						}
					}
					else
					{
						desc = "Please see attached for instructions.";
						String name = resourceElement.attributeValue("href");
						name = name.replace("\\", "/");
						if (name.lastIndexOf("/") != -1) name = name.substring(name.lastIndexOf("/") + 1);

						String res_mime_type = name.substring(name.lastIndexOf(".") + 1);
						res_mime_type = ContentTypeImageService.getContentType(res_mime_type);
						Reference ref = attachmentService.addAttachment(AttachmentService.MNEME_APPLICATION, siteId, AttachmentService.DOCS_AREA,
								AttachmentService.NameConflictResolution.keepExisting, name, datFileContents, res_mime_type,
								AttachmentService.MNEME_THUMB_POLICY, AttachmentService.REFERENCE_ROOT);
						question.getPresentation().addAttachment(ref);
					}
					question.getPresentation().setText(desc);
					questionService.saveQuestion(question);

					if (!assmtExists)
					{
						// assmt = processDates(datFileContents, assmt, assnRefId);
						part = assmt.getParts().addPart();
						QuestionPick questionPick = part.addPickDetail(question);

						//since grading info is missing. by default give 10 points to make assignment valid
						questionPick.setPoints(new Float("10").floatValue());
						assmt.setTries(new Integer(1));

						assmt.getGrading().setGradebookIntegration(Boolean.TRUE);
						if (assmt.getParts().getTotalPoints().floatValue() <= 0)
						{
							assmt.setNeedsPoints(Boolean.FALSE);
						}

						assessmentService.saveAssessment(assmt);
					}
					poolService.savePool(newPool);

				}
			}
			catch (AssessmentPermissionException e)
			{
				M_log.warn("transferAssn permission exception: " + e.toString());
				return;
			}
		}
		catch (Exception e)
		{
			// skip it do nothing
			e.printStackTrace();
		}
	}

	/**
	 * Abstract method implementation .
	 */
	protected String checkAllResourceFileTagReferenceTransferred(List<Element> embedFiles, String subFolder, String s, String title)
	{
		if (embedFiles == null || embedFiles.size() <= 1) return s;
		String unrefered = "";
		int count = 1;
		String collectionId = meleteCHService.getUploadCollectionId(siteId);
		for (Element e : embedFiles)
		{
			String fileRef = e.attributeValue("href");

			try
			{
				String check = fileRef;
				if (check.lastIndexOf("/") != -1) check = check.substring(check.lastIndexOf("/") + 1);
				check = check.substring(check.lastIndexOf("/") + 1);
				// if first resource coursehome.vized then we are skipping now and bring later as resources item
				if(check.contains("Home.vized")) return s;
				// skip vized files. they are already read.
				if(check.contains(".vized")) continue;
				if (importedCourseFiles != null && importedCourseFiles.contains(fileRef)) continue;
				meleteCHService.checkResource(collectionId + check);
			}
			catch (Exception ex)
			{
				if(subFolder == null) subFolder="";
				String id = transferExtraMeleteFile(new File(unzipBackUpLocation + "/" + subFolder + "/" + fileRef), collectionId);
				if (id.length() == 0) continue;
				String a_title = title + "_file" + count++;
				String a_content = "<li> <a target=\"_blank\" href=\"" + meleteCHService.getResourceUrl(id) + "\">" + a_title + "</a></li>";
				unrefered = unrefered.concat(a_content);
			}
		}
		if (unrefered.length() > 0) s = s.concat("<br> Additional Resources: <ul>" + unrefered + "</ul>");
		return s;
	}

	/**
	 * Abstract method implementation to find the actual file location in the package.
	 */
	protected String[] getEmbeddedReferencePhysicalLocation(String embeddedSrc, String subFolder, List<Element> embedFiles,
			List<Element> embedContentFiles, String tool)
	{
		try
		{
			embeddedSrc = java.net.URLDecoder.decode(embeddedSrc, "UTF-8");
		}
		catch (Exception decodex)
		{
			// do nothing
		}
		
		String[] returnStrings = new String[2];
		// physical location
		returnStrings[0] = embeddedSrc;
		// display name
		returnStrings[1] = embeddedSrc;
		return returnStrings;
	}
}
