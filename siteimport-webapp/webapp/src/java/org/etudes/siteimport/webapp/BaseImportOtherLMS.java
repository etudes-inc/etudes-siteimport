/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/siteimport/trunk/siteimport-webapp/webapp/src/java/org/etudes/siteimport/webapp/BaseImportOtherLMS.java $
 * $Id: BaseImportOtherLMS.java 11491 2015-08-23 16:04:28Z mallikamt $
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.net.URLDecoder;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.XPath;
import org.etudes.api.app.jforum.Category;
import org.etudes.api.app.jforum.Forum;
import org.etudes.api.app.jforum.Grade;
import org.etudes.api.app.jforum.JForumCategoryService;
import org.etudes.api.app.jforum.JForumForumService;
import org.etudes.api.app.jforum.JForumPostService;
import org.etudes.api.app.jforum.JForumUserService;
import org.etudes.api.app.jforum.Post;
import org.etudes.api.app.jforum.Topic;
import org.etudes.api.app.melete.MeleteCHService;
import org.etudes.api.app.melete.ModuleObjService;
import org.etudes.api.app.melete.ModuleService;
import org.etudes.api.app.melete.ModuleShdatesService;
import org.etudes.api.app.melete.SectionObjService;
import org.etudes.api.app.melete.SectionService;
import org.etudes.component.app.melete.MeleteResource;
import org.etudes.component.app.melete.Module;
import org.etudes.component.app.melete.ModuleShdates;
import org.etudes.component.app.melete.Section;
import org.etudes.mneme.api.Assessment;
import org.etudes.mneme.api.AssessmentService;
import org.etudes.mneme.api.AttachmentService;
import org.etudes.mneme.api.EssayQuestion;
import org.etudes.mneme.api.EssayQuestion.EssaySubmissionType;
import org.etudes.mneme.api.Pool;
import org.etudes.mneme.api.PoolService;
import org.etudes.mneme.api.QuestionService;
import org.etudes.util.HtmlHelper;
import org.sakaiproject.announcement.api.AnnouncementChannel;
import org.sakaiproject.announcement.api.AnnouncementMessage;
import org.sakaiproject.announcement.api.AnnouncementMessageEdit;
import org.sakaiproject.announcement.api.AnnouncementService;
import org.sakaiproject.api.app.syllabus.SyllabusAttachment;
import org.sakaiproject.api.app.syllabus.SyllabusData;
import org.sakaiproject.api.app.syllabus.SyllabusItem;
import org.sakaiproject.api.app.syllabus.SyllabusManager;
import org.sakaiproject.authz.api.SecurityAdvisor;
import org.sakaiproject.authz.api.SecurityService;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.component.cover.ServerConfigurationService;
import org.sakaiproject.content.api.ContentCollection;
import org.sakaiproject.content.api.ContentCollectionEdit;
import org.sakaiproject.content.api.ContentResource;
import org.sakaiproject.content.cover.ContentHostingService;
import org.sakaiproject.content.cover.ContentTypeImageService;
import org.sakaiproject.entity.api.Entity;
import org.sakaiproject.entity.api.Reference;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.entity.api.ResourcePropertiesEdit;
import org.sakaiproject.entity.cover.EntityManager;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.IdUsedException;
import org.sakaiproject.message.api.MessageService;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SitePage;
import org.sakaiproject.site.api.ToolConfiguration;
import org.sakaiproject.site.cover.SiteService;
import org.sakaiproject.time.api.Time;
import org.sakaiproject.time.cover.TimeService;
import org.sakaiproject.tool.api.Tool;
import org.sakaiproject.tool.cover.ToolManager;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.user.cover.UserDirectoryService;
import org.sakaiproject.util.FormattedText;
import org.sakaiproject.util.Validator;

public abstract class BaseImportOtherLMS
{

	protected class FileExtensionFilter implements FilenameFilter
	{
		private String ext1 = ".dat";

		private String ext2 = ".xml";

		private String ext3 = ".zip";

		public boolean accept(File dir, String name)
		{
			if (!name.endsWith(ext1) && !name.endsWith(ext2) && !name.endsWith(ext3)) return true;
			return false;
		}

	}
	public static final int MAX_NAME_LENGTH = 150;
	
	/** Our logger. */
	protected static Log M_log = LogFactory.getLog(ImportOtherLMSMoodle.class);
	
	protected AnnouncementService announcementService;
	
	protected AssessmentService assessmentService;
	
	protected AttachmentService attachmentService;
	
	protected String baseFolder;
	
	protected Set<String> importedCourseFiles = new HashSet<String>();
	
	protected JForumCategoryService jForumCategoryService;
	
	protected JForumForumService jForumForumService;
	
	protected JForumPostService jForumPostService;
	
	protected JForumUserService jForumUserService;
	
	protected MeleteCHService meleteCHService;
	
	protected MessageService messageService;
	
	protected ModuleService moduleService;
	
	protected PoolService poolService;

	protected QuestionService questionService;

	protected SectionService sectionService;

	protected String siteId;

	protected SyllabusManager syllabusManager;
	
	protected SecurityService securityService;

	protected String unzipBackUpLocation;	
	
	protected String userId;
	
	protected Document backUpDoc;
	
	protected static final int MAXIMUM_ATTEMPTS_FOR_UNIQUENESS = 100;
	
	/*
	 * Inner class to store the week dates
	 */
	protected class CalculatedDates
	{
		private Date startDate;
		private Date dueDate;
		private Date untilDate;

		public CalculatedDates(Date startDate, Date dueDate, Date untilDate)
		{
			this.startDate = startDate;
			this.dueDate = dueDate;
			this.untilDate = untilDate;
		}

		public Date getStartDate()
		{
			return startDate;
		}

		public Date getDueDate()
		{
			return dueDate;
		}

		public Date getUntilDate()
		{
			return untilDate;
		}
	}
	
	/**
	 * 
	 * @param backUpDoc
	 * @param qtiDoc
	 * @param unzipBackUpLocation
	 * @param unzipLTILocation
	 */
	public BaseImportOtherLMS(String siteId, String unzipBackUpLocation, String userId)
	{
		this.siteId = siteId;
		this.unzipBackUpLocation = unzipBackUpLocation;
		this.userId = userId;
		this.baseFolder = unzipBackUpLocation;
	}

	/**
	 * main method which initiates import process.
	 * 
	 * @return
	 */
	public abstract String transferEntities();

	/**
	 * Add Resources tool to the site if the package has content for Resources tool.
	 */
	protected void addResourcesTool() 
	{
		try
		{
			Site site = SiteService.getSite(siteId);
			ToolConfiguration checkResourcesTool = site.getToolForCommonId("sakai.resources");
			if (checkResourcesTool == null)
			{
				Tool resourcesTool = ToolManager.getTool("sakai.resources");
				SitePage page = site.addPage();
				page.setTitle(resourcesTool.getTitle());
				page.addTool(resourcesTool);
				SiteService.save(site);
			}
		}
		catch (Exception e)
		{
			M_log.info("error on adding resources tool to the site:" + e.getMessage());
		}
	}
	
	/**
	 * Add site description 
	 * @param desc
	 * 	description string
	 */
	protected void addSiteDescription(String desc)
	{
		if (desc == null || desc.length() == 0) return;
		
		try
		{
			Site site = SiteService.getSite(siteId);
			site.setDescription(desc);
			SiteService.save(site);
		}
		catch (Exception e)
		{
			M_log.info("error on setting site description:" + e.getMessage());
		}
	}
	
	/**
	 * Adds File reference as attachment to the syllabus item.
	 * 
	 * @param fileName
	 *        attachment file name
	 * @return set of all attachments
	 */
	protected Set<SyllabusAttachment> addSyllabusAttachments(String fileName, String displayName)
	{
		Set<SyllabusAttachment> attachSet = new TreeSet<SyllabusAttachment>();

		try
		{
			String name = displayName;

			// remove folder name from display name
			name = name.replace("\\", "/");
			if (name.lastIndexOf("/") != -1) name = name.substring(name.lastIndexOf("/") + 1);

			byte[] content_data = readDatafromFile(fileName);
			if (content_data == null) return attachSet;
			ResourcePropertiesEdit props = ContentHostingService.newResourceProperties();
			props.addProperty(ResourceProperties.PROP_DISPLAY_NAME, name);

			String res_mime_type = name.substring(name.lastIndexOf(".") + 1);
			if (res_mime_type == null || res_mime_type.length() == 0) return attachSet;
			res_mime_type = ContentTypeImageService.getContentType(res_mime_type);

			ContentResource thisAttach = ContentHostingService.addAttachmentResource(name, siteId, ToolManager.getTool("sakai.syllabus").getTitle(),
					res_mime_type, content_data, props);

			SyllabusAttachment attachObj = syllabusManager.createSyllabusAttachmentObject(thisAttach.getId(), name);
			attachSet.add(attachObj);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		return attachSet;
	}
	
	/**
	 * Creates announcement.
	 * 
	 * @param subject
	 *        subject to be added
	 * @param body
	 *        message of announcement
	 * @param subFolder
	 * 		  subfolder if any to find embedded media       
	 * @throws Exception
	 */
	protected void buildAnnouncement(String subject, String body, Date releaseDate, String subFolder, List<Element> embedFiles) throws Exception
	{

		// site's channel is /announcement/channel/<site id>/main
		String channelRef = "/announcement/channel/" + siteId + "/main";

		AnnouncementChannel channel = null;
		try
		{
			channel = announcementService.getAnnouncementChannel(channelRef);
		}
		catch (IdUnusedException e)
		{
			try
			{
				// create the channel
				channel = announcementService.addAnnouncementChannel(channelRef);
			}
			catch (Exception ex)
			{
			}
		}

		if (channel == null) return;
		if (subject == null) return;

		if (body != null)
		{
			body = findAndUploadEmbeddedMedia(body, subFolder, embedFiles, null, null, "Announcements");

			// strip all comments
			body = HtmlHelper.cleanAndAssureAnchorTarget(body, true);
		}

		// skip if already added
		if (messageService != null)
		{
			List<AnnouncementMessage> allAnnouncements = messageService.getMessages(channelRef, null, 0, true, true, false);

			if (allAnnouncements != null)
			{
				for (AnnouncementMessage a : allAnnouncements)
				{
					String a_messageSubject = a.getAnnouncementHeader().getSubject().trim();
					if (a_messageSubject.equalsIgnoreCase(subject)) return;
					//if (a.getAnnouncementHeader().getSubject().equalsIgnoreCase(subject) && a.getBody().equalsIgnoreCase(body)) return;
				}
			}
		}
		// add new message
		AnnouncementMessage a = channel.addAnnouncementMessage(subject, true, null, body);

		// set the release date
		if (releaseDate == null) return;
		
		GregorianCalendar dateCal = new GregorianCalendar();
		dateCal.setTime(releaseDate);
		Time releaseTime = TimeService.newTime(dateCal);
		if (releaseTime == null) return;
		
		AnnouncementMessageEdit edit = channel.editAnnouncementMessage(a.getId());
		edit.getPropertiesEdit().addProperty(AnnouncementService.RELEASE_DATE, releaseTime.toString());
		channel.commitMessage(edit);
	}

	/**
	 * Find or create a gradable category
	 * @return
	 *  main category id
	 * @throws Exception
	 */
	protected int buildDiscussionGradableCategory(String categoryTitle, String gradePoints, int minPosts)
	{
		try
		{
			int mainCategoryId = 0;
			List<Category> categories = jForumCategoryService.getUserContextCategories(siteId, userId);
			if (categories != null && categories.size() != 0)
			{
				for (Category c : categories)
				{
					if (!c.getTitle().trim().equalsIgnoreCase(categoryTitle)) continue;
					mainCategoryId = c.getId();
				}
			}

			// create main category if missing
			if (mainCategoryId == 0)
			{
				Category category = jForumCategoryService.newCategory();
				category.setTitle(categoryTitle);
				category.setContext(siteId);
				category.setCreatedBySakaiUserId(userId);
				
				category.setGradable(Boolean.TRUE);
				Grade grade = category.getGrade();
				grade.setPoints(Float.parseFloat(gradePoints));
				grade.setType(Grade.GradeType.CATEGORY.getType());
				grade.setAddToGradeBook(true);
				grade.setMinimumPosts(minPosts);
				grade.setMinimumPostsRequired(true);
				
				mainCategoryId = jForumCategoryService.createCategory(category);
			}
			return mainCategoryId;
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return 0;
		}
	}
	/**
	 * Finds or creates Main category
	 * @return
	 */
	protected int buildDiscussionCategory()
	{
		try
		{
			int mainCategoryId = 0;
			List<Category> categories = jForumCategoryService.getUserContextCategories(siteId, userId);
			if (categories != null && categories.size() != 0)
			{
				for (Category c : categories)
				{
					if (!c.getTitle().trim().equalsIgnoreCase("Main")) continue;
					mainCategoryId = c.getId();
				}
			}

			// create main category if missing
			if (mainCategoryId == 0)
			{
				Category category = jForumCategoryService.newCategory();
				category.setTitle("Main");
				category.setContext(siteId);
				category.setCreatedBySakaiUserId(userId);

				mainCategoryId = jForumCategoryService.createCategory(category);
			}
			return mainCategoryId;
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return 0;
		}
	}
	
	/**
	 * 
	 * @param subject
	 * @param body
	 * @param gradePoints
	 * @param openDate
	 * @param dueDate
	 * @param minPosts
	 * @param mainCategoryId
	 * @param userId
	 * @return
	 * @throws Exception
	 */
	protected int buildDiscussionForums(String subject, String body, String forumType, String gradeType, String gradePoints, Date openDate, Date dueDate, int minPosts,
			int mainCategoryId, String userId, List<Forum> allForums) throws Exception
	{
		// check for duplicate
		if (allForums != null)
		{
			for (Forum f : allForums)
			{
				if (f.getName().equals(subject)) return f.getId();
			}
		}
		
		Forum forum = jForumForumService.newForum();
		
		if (subject.length() > 150) subject = subject.substring(0, 150);
		forum.setName(subject);
	
		body = HtmlHelper.cleanAndAssureAnchorTarget(body, true);
		String desc = FormattedText.convertFormattedTextToPlaintext(body);

		forum.setDescription(desc);
		forum.setCategoryId(mainCategoryId);
		forum.setCreatedBySakaiUserId(userId);

		if (forumType.equalsIgnoreCase("NORMAL"))
			forum.setType(Forum.ForumType.NORMAL.getType());
		else
			forum.setType(Forum.ForumType.REPLY_ONLY.getType());

		if (dueDate != null)
		{
			forum.getAccessDates().setDueDate(dueDate);
			forum.getAccessDates().setLocked(true);
		}
		if (openDate != null) forum.getAccessDates().setOpenDate(openDate);

		if (gradeType.equals("nograde"))
		{
			forum.setGradeType(Grade.GradeType.DISABLED.getType());
		}
		else if (gradeType.equals("gradeForum"))
		{
			forum.setGradeType(Grade.GradeType.FORUM.getType());
			if (gradePoints != null)
			{
				forum.setGradeType(Grade.GradeType.FORUM.getType());
				forum.getGrade().setAddToGradeBook(true);
				forum.getGrade().setPoints(Float.parseFloat(gradePoints));
				forum.getGrade().setMinimumPosts(1);
				forum.getGrade().setMinimumPostsRequired(true);
			}
			
			// if found in TRIGGER_COUNT then override
			if (minPosts != 0)
			{
				forum.getGrade().setMinimumPosts(minPosts);
				forum.getGrade().setMinimumPostsRequired(true);
			}
		}
		else if (gradeType.equals("gradeTopics"))
		{
			forum.setGradeType(Grade.GradeType.TOPIC.getType());
			forum.getGrade().setPoints(Float.parseFloat("0"));
		}
		
		int forumId = jForumForumService.createForum(forum);
		
		return forumId;
	}
	
	/**
	 * Create forums and for their description make a sticky reuse-able topic.
	 * @param subject
	 * @param body
	 * @param forumType
	 * @param gradeType
	 * @param gradePoints
	 * @param openDate
	 * @param dueDate
	 * @param minPosts
	 * @param mainCategoryId
	 * @param userId
	 * @param allForums
	 * @param postedBy
	 * @param embedFiles
	 * @return
	 * @throws Exception
	 */
	protected int buildDiscussionForumsWithDescriptionTopic(String subject, String body, String forumType, String gradeType, String gradePoints, Date openDate, Date dueDate, int minPosts,
			int mainCategoryId, String userId, List<Forum> allForums, org.etudes.api.app.jforum.User postedBy, List<Element> embedFiles) throws Exception
	{
		// check for duplicate
		if (allForums != null)
		{
			for (Forum f : allForums)
			{
				if (f.getName().equals(subject)) return f.getId();
			}
		}
		
		Forum forum = jForumForumService.newForum();
		boolean stickyDesc = false;
		
		if (subject.length() > 150) subject = subject.substring(0, 150);
		forum.setName(subject);
	
		body = HtmlHelper.cleanAndAssureAnchorTarget(body, true);
		
		// compare with no formatting loss
		String compareBody = body;
		if (compareBody != null)
		{
			compareBody = compareBody.replace("<p>", "");
			compareBody = compareBody.replace("</p>", "");
			//compareBody = compareBody.replace("<br />", "\n");
			compareBody = compareBody.trim();
		}
		
		String desc = FormattedText.convertFormattedTextToPlaintext(body);
		if (desc != null) desc = desc.trim();
		
		forum.setCategoryId(mainCategoryId);
		forum.setCreatedBySakaiUserId(userId);		
		
		if (compareBody != null && desc != null && !compareBody.equals(desc))
//		if (body != null && desc != null && !body.equals(desc))
		{
			// no desc and sticky topic reply_only
			forum.setDescription("");
			forum.setType(Forum.ForumType.REPLY_ONLY.getType());
			stickyDesc = true;
		}
		else
		{
			forum.setDescription(desc);
			if (forumType.equalsIgnoreCase("NORMAL"))
				forum.setType(Forum.ForumType.NORMAL.getType());
			else
				forum.setType(Forum.ForumType.REPLY_ONLY.getType());			
		}

		if (dueDate != null)
		{
			forum.getAccessDates().setDueDate(dueDate);
			forum.getAccessDates().setLocked(true);
		}
		if (openDate != null) forum.getAccessDates().setOpenDate(openDate);

		if (gradeType.equals("nograde"))
		{
			forum.setGradeType(Grade.GradeType.DISABLED.getType());
		}
		else if (gradeType.equals("gradeForum"))
		{
			forum.setGradeType(Grade.GradeType.FORUM.getType());
			if (gradePoints != null)
			{
				forum.setGradeType(Grade.GradeType.FORUM.getType());
				forum.getGrade().setAddToGradeBook(true);
				forum.getGrade().setPoints(Float.parseFloat(gradePoints));
				forum.getGrade().setMinimumPosts(1);
				forum.getGrade().setMinimumPostsRequired(true);
			}
			
			// if found in TRIGGER_COUNT then override
			if (minPosts != 0)
			{
				forum.getGrade().setMinimumPosts(minPosts);
				forum.getGrade().setMinimumPostsRequired(true);
			}
		}
		else if (gradeType.equals("gradeTopics"))
		{
			forum.setGradeType(Grade.GradeType.TOPIC.getType());
			forum.getGrade().setPoints(Float.parseFloat("0"));
		}
		
		int forumId = jForumForumService.createForum(forum);
		
		if (stickyDesc)
		{
			// if grade by topic then add details to the topic otherwise to forum
			if (gradeType.equals("gradeTopics"))
			{
				buildDiscussionTopics(subject, body, "Sticky", null, embedFiles, null, gradePoints, openDate, dueDate, minPosts,
						forumId, postedBy, jForumPostService.getForumTopics(forumId));
			}
			else buildDiscussionTopics(subject, body, "Sticky", null, embedFiles, null, null, null, null, 0,
					forumId, postedBy, jForumPostService.getForumTopics(forumId));
		}
		return forumId;
	}
	
	/**
	 * Creates re-usable topics in a forum
	 * 
	 * @param subject
	 *        Subject of topic
	 * @param body
	 *        Body of topic
	 * @param gradePoints
	 *        Max Points for grading
	 * @param openDate
	 *        Open date
	 * @param dueDate
	 *        due date
	 * @param minPosts
	 *        min posts required
	 * @param forum_id
	 *        To add topics in the forum
	 * @param postedBy
	 *        posted by user
	 * @param all
	 *        all discussions in the forum
	 * @throws Exception
	 */
	protected void buildDiscussionTopics(String subject, String body, String topicType, String subFolder, List<Element> embedFiles, List<Element> embedContentFiles, String gradePoints, Date openDate, Date dueDate, int minPosts, int forum_id,
			org.etudes.api.app.jforum.User postedBy, List<Topic> all) throws Exception
	{
		if (forum_id == 0) return;
		
		if (subject == null) return;

		if (subject.length() > 100) subject = subject.substring(0, 100);

		if (body != null)
		{
			// strip all comments
			body = HtmlHelper.cleanAndAssureAnchorTarget(body, true);			
		}

		// check for duplicate
		if (all != null)
		{
			for (Topic t : all)
			{
				if (t.getTitle().equals(subject)) return;
			}
		}

		// create topic
		Topic topic = jForumPostService.newTopic();
		topic.setForumId(forum_id);
		topic.setPostedBy(postedBy);
		topic.setTitle(subject);
		if (topicType.equalsIgnoreCase("Sticky"))
		{
			topic.setType(Topic.TopicType.STICKY.getType());
			topic.setStatus(Topic.TopicStatus.LOCKED.getStatus());
		}
		else
			topic.setType(Topic.TopicType.NORMAL.getType());

		topic.setExportTopic(true);

		if (gradePoints != null)
		{
			Grade grade = jForumPostService.newTopicGrade(topic);
			grade.setAddToGradeBook(true);
			grade.setPoints(Float.parseFloat(gradePoints));
			grade.setType(Grade.GradeType.TOPIC.getType());
			grade.setMinimumPostsRequired(true);
			grade.setMinimumPosts(1);
			
			// if min posts specified in TRIGGER_COUNT then override it
			if (minPosts != 0)
			{
				grade.setMinimumPostsRequired(true);
				grade.setMinimumPosts(minPosts);
			}

			if (openDate != null) topic.getAccessDates().setOpenDate(openDate);

			if (dueDate != null) topic.getAccessDates().setDueDate(dueDate);
			
			topic.setGradeTopic(true);
		}

		Post post = jForumPostService.newPost();
		post.setSubject(subject);
		post.setPostedBy(postedBy);
		body = findAndUploadEmbeddedMedia(body, subFolder, embedFiles, embedContentFiles, post, "JForum");
		body = cleanupImgTags(body);
		post.setText(body);
		post.setHtmlEnabled(false);

		topic.getPosts().add(post);
		jForumPostService.createTopic(topic, false);
		all.add(topic);
	}
	
	/**
	 * Build resource tool item if the item is not in meletedocs or in mnemedocs
	 * 
	 * @param fileNode
	 * @param readFrom
	 * @param fileName
	 * @param collectionId
	 */
	protected void buildExtraResourceItem(String readFrom, String fileName, String collectionId)
	{
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
				String res_mime_type = fileName.substring(fileName.lastIndexOf(".") + 1);
				res_mime_type = ContentTypeImageService.getContentType(res_mime_type);

				try
				{
					byte[] content_data = readDatafromFile(readFrom);
					if (content_data == null) return;
					addResourcesTool();
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
	 * @param content_name
	 * @param res_mime_type
	 * @param addCollectionId
	 * @param content_data
	 * @param res
	 * @return
	 */
	protected String buildMeleteSectionResource(String content_name, String res_mime_type, String addCollectionId, byte[] content_data) throws Exception
	{
		try
		{
		pushAdvisor();
		ResourcePropertiesEdit res = meleteCHService.fillInSectionResourceProperties(false, content_name, "");
		String resourceId = meleteCHService.addResourceItem(content_name, res_mime_type, addCollectionId, content_data, res);
		return resourceId;
		}
		finally
		{
			popAdvisor();
		}
	}

	/**
	 * 
	 * @param folderTitle
	 * @param title
	 * @param description
	 * @param creator
	 * @param content_data
	 * @param res_mime_type
	 * @throws Exception
	 */
	protected void buildResourceToolItem(String folderTitle, String title, String description, String creator, byte[] content_data,
			String res_mime_type) throws Exception
	{
		String displayName = title;
		try
		{
			String finalName = folderTitle + title;
			if (finalName.length() > ContentHostingService.MAXIMUM_RESOURCE_ID_LENGTH)
			{
				// leaving room for CHS inserted duplicate filenames -1 -2 etc
				int extraChars = finalName.length() - ContentHostingService.MAXIMUM_RESOURCE_ID_LENGTH + 3;
				title = title.substring(0, title.length() - extraChars);
			}
			title = Validator.escapeResourceName(title);
			ContentHostingService.checkResource(folderTitle + title);
		}
		catch (IdUnusedException e)
		{
			try
			{
				displayName = URLDecoder.decode(displayName, "UTF-8");
			}
			catch (Exception ex)
			{
				// do nothing
			}
			ResourcePropertiesEdit resourceProperties = ContentHostingService.newResourceProperties();
			resourceProperties.addProperty(ResourceProperties.PROP_DISPLAY_NAME, displayName);
			resourceProperties.addProperty(ResourceProperties.PROP_DESCRIPTION, description);
			resourceProperties.addProperty(ResourceProperties.PROP_IS_COLLECTION, Boolean.FALSE.toString());
			resourceProperties.addProperty(ResourceProperties.PROP_CREATOR, creator);

			ContentResource resource = ContentHostingService.addResource(title, folderTitle, MAXIMUM_ATTEMPTS_FOR_UNIQUENESS, res_mime_type,
					content_data, resourceProperties, null, false, null, null, 0);
		}
	}
	
	/**
	 * creates melete blank section with no content type.
	 * 
	 * @param title
	 *        Title of section
	 * @param module
	 *        Module object
	 * @return the newly created section object
	 */
	protected SectionObjService buildSection(String title, ModuleObjService module)
	{
		try
		{
			SectionObjService section = new Section();
			section.setTitle(title);
			String firstName = "";
			String lastName = "";
			try
			{
				firstName = UserDirectoryService.getUser(userId).getFirstName();
				lastName = UserDirectoryService.getUser(userId).getLastName();
			}
			catch (UserNotDefinedException e)
			{
				M_log.warn("build section: current user not found in uds: " + userId);
			}
			section.setContentType("notype");
			section.setTextualContent(true);

			// add section
			Integer sectionId = sectionService.insertSection(module, section, userId);
			section.setSectionId(sectionId);
			return section;
		}
		catch (Exception e)
		{

		}
		return null;
	}

	/**
	 * Creates syllabus item or redirectUrl link.
	 * 
	 * @param title
	 *        title of item
	 * @param asset
	 *        body of item
	 * @param check_name
	 *        List of attachment file names or redirect url
	 *  @param  subFolder
	 *  	  specify subfolder if any to find embedded media   
	 */
	protected void buildSyllabus(String title, String asset, boolean draft, String type, String[] check_name, String subFolder, List<Element> embedFiles, List<Element> embedContentFiles, String[] display_name) throws Exception
	{
		SyllabusItem syllabusItem = syllabusManager.getSyllabusItemByContextId(siteId);
		if (syllabusItem == null)
		{
			// create it if needed
			syllabusItem = syllabusManager.createSyllabusItem(UserDirectoryService.getCurrentUser().getId(), siteId, null);
		}

		// check for duplicates
		if (syllabusItemExists(title, syllabusItem)) return;

		Integer positionNo = null;

		if (type.equals("item"))
		{
			// for moodle 2 files
			String fileDisplayName = null;
			String fileName = null;
			
			if (check_name != null && check_name.length == 1 && display_name != null && display_name.length == 1)
			{
				fileDisplayName = display_name[0];
			}
			else if (check_name != null && check_name.length == 1)
			{
				fileDisplayName = check_name[0];
			}
			
			if ((asset == null || asset.length() == 0) && (check_name != null && check_name.length == 1))
			{
				// Instead of putting html as attachment , add it as asset only if body/text is null
				fileName = check_name[0];
				byte[] content_data = readDatafromFile(fileName);
				String res_mime_type = fileDisplayName.substring(fileDisplayName.lastIndexOf(".") + 1);
				res_mime_type = ContentTypeImageService.getContentType(res_mime_type);

				if ("text/html".equals(res_mime_type) && content_data != null)
				{
					asset = new String(content_data);
					// put check_name as null to avoid adding as attachment
					check_name = null;
				}
			}
			
			if (asset != null && asset.length() > 0)
			{
				asset = HtmlHelper.cleanAndAssureAnchorTarget(asset, true);
				asset = findAndUploadEmbeddedMedia(asset, subFolder, embedFiles, embedContentFiles, null, "Syllabus");
			}
			positionNo = new Integer(syllabusManager.findLargestSyllabusPosition(syllabusItem).intValue() + 1);

			// add it to the syllabus
			String status = "Posted";
			if (draft) status = "Draft";
			
			SyllabusData syData = syllabusManager.createSyllabusDataObject(title, positionNo, asset, "", status, "None");

			if (check_name != null && check_name.length != 0)
			{
				for (int i = 0; i < check_name.length; i++)
				{
					String attachFile = check_name[i];
					String name = attachFile;
					if (display_name != null)
						name = display_name[i];
					else if (fileName != null && fileName.lastIndexOf(File.separator) != -1) 
						name = fileName.substring(fileName.lastIndexOf(File.separator) + 1);
					
					if (attachFile == null || name == null || importedCourseFiles.contains(attachFile)) continue;
					
					syData.setAttachments(addSyllabusAttachments(attachFile, name));
					importedCourseFiles.add(attachFile);
				}
			}
			syllabusManager.addSyllabusToSyllabusItem(syllabusItem, syData);
		}
		else if (type.equals("redirectUrl") && check_name != null && check_name.length != 0)
		{
			// 3. if type is file and reference is http: then redirect
			String s = check_name[0];
			if (s.startsWith("http://") || s.startsWith("https://"))
			{
				syllabusItem.setRedirectURL(s);
				syllabusManager.saveSyllabusItem(syllabusItem);
			}
		}
	}

	/**
	 * Creates the typeEditor section's resource
	 * 
	 * @param section
	 *        Section
	 * @param s
	 *        The content of section
	 * @param module
	 *        the containing Module
	 *  @param subFolder
	 *  	  subfolder to find the embedded media. for BB, its xml:base value       
	 * @return the edited section
	 */
	protected SectionObjService buildTypeEditorSection(SectionObjService section, String s, ModuleObjService module, String subFolder, String title, List<Element> embedFiles, List<Element> embedContentFiles)
	{
		try
		{
			section.setContentType("typeEditor");

			// strip all other MS word comments
			s = HtmlHelper.cleanAndAssureAnchorTarget(s, true);
			s = findAndUploadEmbeddedMedia(s, subFolder, embedFiles, embedContentFiles, null, "Melete");

			s = checkAllResourceFileTagReferenceTransferred(embedFiles, subFolder, s, title);
			if (s == null || s.length() == 0) return section;
			
			byte[] content_data = s.getBytes();
			String content_name = "Section_" + section.getSectionId() + ".html";

			String addCollectionId = meleteCHService.getCollectionId(siteId, section.getContentType(), module.getModuleId());
			String resourceId = buildMeleteSectionResource(content_name, "text/html", addCollectionId, content_data);
			MeleteResource meleteResource = new MeleteResource();
			meleteResource.setResourceId(resourceId);

			// no license information found in backup file
			meleteResource.setLicenseCode(0);

			// associate with meleteresource etc
			sectionService.editSection(section, meleteResource, userId, true);
			return section;
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		
		return section;
	}
	
	/**
	 * Create the upload/link melete resource and appends the resource url to the section content
	 * @param check_name
	 * 	File to be added
	 * @param section_content
	 * 	section content so far
	 * @return
	 * section content plus the added resource url as <a>tag
	 * @throws Exception
	 */
	protected String buildTypeLinkUploadResource(String check_name, String display_name, String section_content, String subFolder, List<Element> embedFiles) throws Exception
	{
		byte[] content_data = null;
		String res_mime_type = null;

		if (check_name != null && check_name.startsWith("http://") || check_name.startsWith("https://") || check_name.startsWith("mailto:"))
		{
			content_data = check_name.getBytes();
			res_mime_type = "text/url";
		}
		else if (check_name != null)
		{
			display_name = display_name.replace("\\", "/");
			if (display_name.lastIndexOf("/") != -1) display_name = display_name.substring(display_name.lastIndexOf("/") + 1);
			
			if(subFolder == null) subFolder="";
//			check_name = findFileLocation(check_name, subFolder, embedFiles, "Melete");
			
			content_data = readDatafromFile(check_name);
			res_mime_type = check_name.substring(check_name.lastIndexOf(".") + 1);
			res_mime_type = ContentTypeImageService.getContentType(res_mime_type);

			if (res_mime_type.equals("text/html"))
			{
				String data = new String(content_data);
				data = findAndUploadEmbeddedMedia(data, subFolder , embedFiles , null, null, "Melete");
				data = HtmlHelper.cleanAndAssureAnchorTarget(data, true);				
				content_data = data.getBytes("UTF-8");
			}
			importedCourseFiles.add(check_name);
		}
		// add the resource now
		String addCollectionId = meleteCHService.getUploadCollectionId(siteId);
		String resourceId = buildMeleteSectionResource(display_name, res_mime_type, addCollectionId, content_data);
		String hrefString = meleteCHService.getResourceUrl(resourceId);
		hrefString = hrefString.replace(ServerConfigurationService.getServerUrl(), "");
		section_content = section_content.concat("<br/> <a target=\"_blank\" href=\"" + hrefString + "\" >" + display_name + "</a>");

		return section_content;
	}

	/**
	 * Checks if assmName exists in the list of assmtNames
	 * Also does a check in the db
	 * 
	 * @param assmtName
	 * @param assmtNames
	 * @param context
	 * @return
	 */
	protected boolean checkAssmtTitleExists(String assmtName, List<String> assmtNames, String context)
	{
		boolean titleExists = false;
		titleExists = checkTitleExists(assmtName, assmtNames);
		if (!titleExists)
		{
			titleExists = this.assessmentService.existsAssessmentTitle(assmtName, context);
		}
		return titleExists;
	}
	
	/**
	 * Checks if assmName exists in the list of assmtNames
	 * 
	 * @param assmtName
	 * @param assmtNames
	 * @param context
	 * @return
	 */
	protected boolean checkPoolTitleExists(String assmtName, List<String> assmtNames, String context)
	{
		boolean titleExists = false;
		titleExists = checkTitleExists(assmtName, assmtNames);
		if (!titleExists)
		{
			titleExists = this.poolService.existsPoolTitle(assmtName, context);
		}
		return titleExists;
	}
	
	/**
	 * Checks if assmName exists in the list of assmtNames
	 * 
	 * @param assmtName
	 * @param assmtNames
	 * @return
	 */
	protected boolean checkTitleExists(String assmtName, List<String> assmtNames)
	{
		if (assmtName == null || assmtName.trim().length() == 0) return false;
		if (assmtNames == null || assmtNames.size() == 0) return false;
		return assmtNames.contains(assmtName);
	}
	
	/**
	 * 
	 * @param mainCategoryId
	 */
	protected void checkClassDiscussionsForum(int mainCategoryId)
	{
		try
		{
			Category c = jForumCategoryService.getUserCategoryForums(mainCategoryId, userId);
			if (c == null) return;
			List<Forum> forums = c.getForums();
			boolean disableGrade = false;
			List<Topic> topics = null;
			Forum classDiscussForum = null;
			int forumtype = Forum.ForumType.REPLY_ONLY.getType();
			
			for (Forum forum : forums)
			{
				if (forum.getName().trim().equalsIgnoreCase("Class Discussions"))
				{
					int forum_id = forum.getId();
					topics = jForumPostService.getForumTopics(forum_id);
					classDiscussForum = forum;
					break;
				}
			}
			
			// step 2: if no topics or all topics grade is null then make forum ungradeable
			if (topics == null || topics.size() == 0)
			{
				disableGrade = true;
				forumtype = Forum.ForumType.NORMAL.getType();
			}
			else
			{
				boolean allGradeNull = false;
				for (Topic t : topics)
				{					
					allGradeNull = t.isGradeTopic() || allGradeNull ;						
				}
				disableGrade = !allGradeNull;
			}
			
			// step 3: make it NORMAL, ungraded only
			if (disableGrade)
			{
				if (classDiscussForum == null) return;
				classDiscussForum.setType(forumtype);
				classDiscussForum.setGradeType(Grade.GradeType.DISABLED.getType());
				classDiscussForum.setModifiedBySakaiUserId(userId);
				jForumForumService.modifyForum(classDiscussForum);
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	/**
	 * Creates an essay question
	 * 
	 * @param essayQuestion
	 * @param itemEle
	 * @param ansText
	 * @return
	 */
	protected EssayQuestion buildEssayQuestion(EssayQuestion essayQuestion, Element itemEle, String ansText)
	{
		if (ansText != null && ansText.length() != 0)
		{
			essayQuestion.setSubmissionType(EssaySubmissionType.inline);
			ansText = findUploadAndClean(ansText, itemEle, "Mneme");
			essayQuestion.setModelAnswer(ansText);
		}
		return essayQuestion;
	}
	
	/**
	 * Process embedded data for Mneme
	 * 
	 * @param fileName
	 * @param newQtext
	 * @return
	 */
	protected String processEmbed(String fileName, String newQtext)
	{
		if (fileName == null || fileName.trim().length() == 0) return newQtext;
		String imgResourceId = transferEmbeddedData(fileName, fileName, null, "Mneme");
		if (imgResourceId != null)
		{
			String imgUrl = attachmentService.processMnemeUrls(imgResourceId);
			newQtext = newQtext.concat("<img src=\"" + imgUrl + "\" alt=\"\"/>");
		}
		return newQtext;
	}
	
	/**
	 * Finds and uploads embedded media. Uses HTMLHelper to clean the text
	 * 
	 * @param text
	 * @param presEle
	 * @param tool
	 * @return
	 */
	protected String findUploadAndClean(String text, Element presEle, String tool)
	{
		String fileName = null;
		if (presEle != null) fileName = getFileName(presEle);
		String resText = findAndUploadEmbeddedMedia(text, fileName, getEmbedFiles(fileName), null, null, tool);
		resText = HtmlHelper.cleanAndAssureAnchorTarget(resText, true);
		return resText;
	}
	
	/**
	 * Finds and uploads embedded media. Uses HTMLHelper to clean the text
	 * 
	 * @param text
	 * @param subFolder
	 * @param embedFiles
	 * @param tool
	 * @return
	 */
	protected String findUploadAndClean(String text, String subFolder, List<Element> embedFiles,  String tool)
	{
		String resText = findAndUploadEmbeddedMedia(text, subFolder, embedFiles, null, null, tool);
		resText = HtmlHelper.cleanAndAssureAnchorTarget(resText, true);
		
		return resText;
	}

	/**
	 * Abstract method to check that all <file> of <resource> have been brought in.
	 * @param embedFiles
	 * @param subFolder
	 * @param s
	 * @param title
	 * @return
	 */
	abstract protected String checkAllResourceFileTagReferenceTransferred(List<Element> embedFiles, String subFolder, String s, String title);
	
	/**
	 * For jforum embedded images becomes attachments but the img tag stays in the content. We need to clean that up. 
	 * @param body
	 * @return
	 */
	protected String cleanupImgTags(String body)
	{
		try
		{
			Pattern pa = Pattern.compile("<img\\s+.*?/*>", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);
			Pattern p_srcAttribute = Pattern.compile("\\s*src\\s*=\\s*(\".*?\")", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);
				
			Matcher m = pa.matcher(body);
			StringBuffer sb = new StringBuffer();
			while (m.find())
			{
				String img_content = m.group(0);
				Matcher m_src = p_srcAttribute.matcher(img_content);
				if (!m_src.find())
				{
					m.appendReplacement(sb, "");
				}			
			}
			m.appendTail(sb);
			return sb.toString();
		}
		catch (Exception e)
		{
			M_log.debug("error in cleaning up blank img tags:" + e.getMessage());
		}
		return body;
	}
	
	/**
	 * 
	 * @param rootCollectionRef
	 * @param name
	 * @return
	 */
	protected String createSubFolderCollection(String rootCollectionRef, String name, String description, String alternateRef)
	{
		try
		{
			pushAdvisor();
			// Check if collection exists
			ContentCollection collection = ContentHostingService.getCollection(rootCollectionRef + Validator.escapeResourceName(name) + Entity.SEPARATOR);
			return collection.getId();
		}
		catch (IdUnusedException e)
		{
			// if not, create it
			ContentCollectionEdit edit = null;
			try
			{
				// if (!ContentHostingService.allowAddCollection(siteId)) return rootCollectionRef;
				edit = ContentHostingService.addCollection(rootCollectionRef + Validator.escapeResourceName(name) + Entity.SEPARATOR);
				ResourcePropertiesEdit props = edit.getPropertiesEdit();
				props.addProperty(ResourceProperties.PROP_DISPLAY_NAME, name);
				props.addProperty(ResourceProperties.PROP_DESCRIPTION, description);
				if (alternateRef != null) props.addProperty(ContentHostingService.PROP_ALTERNATE_REFERENCE, alternateRef);
				ContentHostingService.commitCollection(edit);
				return edit.getId();
			}
			catch (Exception e2)
			{
				if (edit != null) ContentHostingService.cancelCollection(edit);
				e2.printStackTrace();
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		finally
		{
			popAdvisor();
		}

		return null;
	}

	/**
	 * Abstract method to find the file location in the package. Each LMS has to write this method and give the location.
	 * 
	 * @param embeddedSrc
	 *        the embedded media file reference
	 * @param subFolder
	 *        xml:base value in case of blackboard
	 * @param embedFiles
	 *        List of <RESOURCE><FILE> elements
	 * @param embedContentFiles
	 *        List of <CONTENT><FILE> elements
	 * @param tool
	 *        Tool name which is trying to find the location
	 * @return
	 */
	protected abstract String[] getEmbeddedReferencePhysicalLocation(String embeddedSrc, String subFolder, List<Element> embedFiles, List<Element> embedContentFiles, String tool);
	
	/**
	 * Recursively search for a file in the physical directory.
	 * @param f
	 *  Directory to look into
	 * @param check
	 *  Part of file name something starting with xid__
	 * @return
	 */
	protected String findFileinDirectory(File f, String check)
	{
		if (f.exists() && f.isDirectory())
		{
			File[] csFiles = f.listFiles();
			for (File f1 : csFiles)
			{
				if (f1.isDirectory())
				{
					String gotIt = findFileinDirectory(f1, check);
					if (gotIt != null) return gotIt;
				}
				else if (f1.getName().indexOf(check) != -1 && !f1.getName().endsWith(".xml")) return f1.getPath();
			}
		}
		return null;
	}
	
	/**
	 * Parses the content to look for embedded media. If found, uploads it and adjusts the text with its url address.
	 * 
	 * @param content1
	 *        Content read from the file
	 * @param subFolder
	 * 		  Specify if embedded media is in subfolder
	 * @param embedFiles
	 * 		  List of file elements which has blackboard package embedded files location
	 * @param tool
	 * 		  Name of tool where its transferred       
	 * @return Content with right url address for embedded media
	 */
	protected String findAndUploadEmbeddedMedia(String content1, String subFolder, List<Element> embedFiles, List<Element> embedContentFiles, Post post, String tool)
	{
		String resourceUrl = null;
		try
		{
			if (content1 == null || content1.length() == 0) return content1;

			content1 = replaceMathContents(content1);
			Pattern p = Pattern.compile("(src|href|data|archive)[\\s]*=[\\s]*\"([^#\"]*)([#\"])", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
			Matcher m = p.matcher(content1);
			StringBuffer sb = new StringBuffer();
		
			if (subFolder == null) subFolder = "";
			
			while (m.find())
			{
				String replaceStr = m.group(1);
				String embeddedSrc = m.group(2);
						
				if (embeddedSrc.startsWith("http://") || embeddedSrc.startsWith("https://")) continue;
				if (embeddedSrc.startsWith("/access/")) continue;
				
				String[] returns = getEmbeddedReferencePhysicalLocation(embeddedSrc, subFolder, embedFiles, embedContentFiles, tool);
				
				embeddedSrc = returns[0];
				String name = returns[1];
				
				String newEmbedResourceId = transferEmbeddedData(embeddedSrc, name, post, tool);
				
				importedCourseFiles.add(embeddedSrc);
				
				if (newEmbedResourceId != null)
				{
					if ("JForum".equals(tool))
					{						
						m.appendReplacement(sb, "");
					}
					else if ("Mneme".equals(tool))
					{
						resourceUrl = attachmentService.processMnemeUrls(newEmbedResourceId);
						replaceStr = replaceStr.concat("=\"" + resourceUrl + m.group(3));
						m.appendReplacement(sb, replaceStr);
					}
					else
					{
						// replace with meletedocs address
						String serverUrl = ServerConfigurationService.getServerUrl();
						resourceUrl = meleteCHService.getResourceUrl(newEmbedResourceId);
						resourceUrl = resourceUrl.replace(serverUrl, "");
						replaceStr = replaceStr.concat("=\"" + resourceUrl + m.group(3));
						m.appendReplacement(sb, replaceStr);
					}
				}
			}
			m.appendTail(sb);

			return sb.toString();
		}
		catch (Exception e)
		{
			M_log.debug("error in checking text");
			e.printStackTrace();
		}
		return content1;
	}
	
	/**
	 * Find a module or add a module if it doesn't exists.
	 * 
	 * @param checkModuleTitle
	 *        to be added module's title
	 * @param num
	 *        The sequence number
	 * @param summaryText
	 *        text part of summary element. If not null, create a section titled "Overview"
	 * @param existingModules
	 *        all melete modules already in the site
	 * @return
	 */
	protected ModuleObjService findOrAddModule(String checkModuleTitle, int num, String summaryText, Date startDate, Date endDate, Date allowUntil, List<ModuleObjService> existingModules, String subFolder, List<Element> embedFiles, List<Element> embedContentFiles)
	{
		ModuleObjService module = new Module();
		if (checkModuleTitle != null)
		{
			module.setTitle(checkModuleTitle);
			module.setKeywords(checkModuleTitle);
		}
		else
			module.setTitle("Untitled Module");

		String firstName = "";
		String lastName = "";
		try
		{
			firstName = UserDirectoryService.getUser(userId).getFirstName();
			lastName = UserDirectoryService.getUser(userId).getLastName();
		}
		catch (UserNotDefinedException e)
		{
			M_log.warn("addModule: current user not found in uds: " + userId);
		}

		ModuleShdatesService dates = new ModuleShdates();
		dates.setStartDate(startDate);
		dates.setEndDate(endDate);
		dates.setAllowUntilDate(allowUntil);
		dates.setModule(module);

		try
		{
			// if module found return it
			if (existingModules != null && existingModules.size() != 0)
			{
				for (ModuleObjService m : existingModules)
				{
					if (m.getTitle().equals(checkModuleTitle))
					{
						return m;
					}					
				}
			}
			moduleService.insertProperties(module, dates, num, userId, siteId);
			existingModules.add(module);

			// add first section as overview if summary found
			if (summaryText == null || summaryText.trim().length() == 0 || summaryText.equals("$@NULL@$") || summaryText.equals("<p></p>") || summaryText.equals("&nbsp;")) return module;

			SectionObjService firstSection = buildSection("Overview", module);
			firstSection = buildTypeEditorSection(firstSection, summaryText, module, subFolder, checkModuleTitle, embedFiles, embedContentFiles);

			return module;
		}
		catch (Exception e)
		{
			M_log.warn("addModule: " + e);
		}
		return null;
	}
	
	/**
	 * Removes tags that don't have balanced double quotes
	 * 
	 * @param content1
	 *        The source html
	 * @return The cleaned up html after removing faulty tags
	 */
	protected String fixDoubleQuotes(String content1)
	{
		Pattern p1 = Pattern.compile("<.*?>", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);
		Pattern pDouble = Pattern.compile("\"");

		if (content1 == null) return content1;
		Matcher m = p1.matcher(content1);
		StringBuffer sb = new StringBuffer();

		while (m.find())
		{
			int countDblQuotes = 0;
			Matcher mDub = pDouble.matcher(m.group(0));
			while (mDub.find())
			{
				countDblQuotes = countDblQuotes + 1;
			}
			if (countDblQuotes > 0 && ((countDblQuotes % 2) != 0))
			{
				m.appendReplacement(sb, "");
			}
		}

		m.appendTail(sb);
		return sb.toString();

	}

	protected AnnouncementService getAnnouncementService()
	{
		return (AnnouncementService) ComponentManager.get(AnnouncementService.class);
	}

	protected List<String> getAssessmentNames(List<Assessment> assmts)
	{
		List assmtNames = new ArrayList();
		for (Assessment assmt : assmts)
		{
			assmtNames.add(assmt.getTitle());
		}
		return assmtNames;
	}

	protected AssessmentService getAssessmentService()
	{
		return (AssessmentService) ComponentManager.get(org.etudes.mneme.api.AssessmentService.class);
	}

	protected AttachmentService getAttachmentService()
	{
		return (AttachmentService) ComponentManager.get(AttachmentService.class);
	}

	protected String getAttributeValue(Element ele,String attrName)
	{
		return getAttributeValue(ele, null, attrName);
	}
	
	/**
	 * Returns attribute value. If path is not specified, the element is used and attribute value is returned. If path is specified, it is resolved on the element and the attribute value is returned. May return null or empty string.
	 * 
	 * @param ele
	 *        Element object
	 * @param path
	 *        String value of path
	 * @param attrName
	 *        Attribute name
	 * @return Attribute value of element with path resolved (if any) or null
	 */
	protected String getAttributeValue(Element ele, String path, String attrName)
	{
		if (ele == null) return null;
		if (attrName == null || attrName.trim().length() == 0) return null;
		if (path == null || path.trim().length() == 0) return ele.attributeValue(attrName);
		Element pathEle = (Element) ele.selectSingleNode(path);
		if (pathEle == null) return null;
		return pathEle.attributeValue(attrName);
	}
	
	/**
	 * 
	 * @return
	 */
	protected String getBaseFolder()
	{
		return baseFolder;
	}
	
	/**
	 * Adds an element called etudes_bb_filename with file name value
	 * to the root element of the document
	 * @param contentsDOM Document to add element to
	 * @param fileName Name of file
	 */
	protected void addFileNameElement(Document contentsDOM, String fileName)
	{
		if (contentsDOM == null) return;
		if (fileName == null || fileName.trim().length() == 0) return;
		Element root = contentsDOM.getRootElement();
		if (root == null) return;
		Node fileNode = root.addElement("etudes_bb_filename");
		fileNode.setText(fileName);
	}
	
	/**
	 * Given an element, determines the value of the file name stored in the etudes_bb_filename
	 * tag and returns it, if it exists
	 * @param itemEle Element in a document object
	 * @return Value of file name stored in etudes_bb_filename element, or null
	 */
	protected String getFileName(Element itemEle)
	{
		Document contentsDOM;
		String fileName = null;
		if (itemEle == null) return fileName;
		contentsDOM = itemEle.getDocument();
		if (contentsDOM == null) return null;
		Element root = contentsDOM.getRootElement();
		if (root == null) return null;
		return getElementValue(root, "etudes_bb_filename");
	}
	
	private List<Element> getEmbedFiles(String idrefVal)
	{
		List<Element> embedFiles = new ArrayList();
		if (idrefVal == null) return null;
		try
		{
			Element resourceElement = getResourceElement(idrefVal);

			if (resourceElement == null)
			{
				return null;
			}
			embedFiles = resourceElement.selectNodes("file");
		}
		catch (Exception e)
		{
			return null;
		}
		return embedFiles;
	}
	
	/**
	 * Get the corresponding resource object.
	 * 
	 * @param resName
	 *        The resource name
	 * @return Resource Element
	 * @throws Exception
	 */
	protected Element getResourceElement(String resName) throws Exception
	{
		XPath xpath = backUpDoc.createXPath("/manifest/resources/resource[@identifier = '" + resName + "']");
		return (Element) xpath.selectSingleNode(backUpDoc);
	}
	
	/**
	 * Checks if the forum exists and makes it reply only and gradeable by topic. It creates the forum if not existing.
	 * 
	 * @return id of Class Discussions forum
	 */
	protected int getClassDiscussionsForum()
	{
		int forum_id = 0;
		org.etudes.api.app.jforum.User u = jForumUserService.getBySakaiUserId(userId);
		try
		{
			if (u == null)
			{
				u = jForumUserService.createUser(userId);
			}
			jForumUserService.addUserToSiteUsers(siteId, u.getId());
		}
		catch (Exception e)
		{
			M_log.info("unable to access user information.");
		}
			
		try
		{
			// category
			int mainCategoryId = buildDiscussionCategory();
			if (mainCategoryId != 0)
			{
					//Category c = jForumCategoryService.getCategory(mainCategoryId);
					Category c = jForumCategoryService.getUserCategoryForums(mainCategoryId, userId);
					//List<Forum> forums = c.getForums();
					List<Forum> forums = c.getForums();
					for (Forum forum : forums)
					{
						if (forum.getName().trim().equalsIgnoreCase("Class Discussions"))
						{
							forum_id = forum.getId();
							// step 3: make it reply only
							forum.setType(Forum.ForumType.REPLY_ONLY.getType());
							forum.setGradeType(Grade.GradeType.TOPIC.getType());
							forum.setModifiedBySakaiUserId(userId);
							jForumForumService.modifyForum(forum);
							break;
						}
					}				
				} // if end

			// build Class Discussions Forum
			if (forum_id == 0 && mainCategoryId != 0)
			{
				Forum forum = jForumForumService.newForum();
				forum.setName("Class Discussions");
				forum.setType(Forum.ForumType.REPLY_ONLY.getType());
				forum.setGradeType(Grade.GradeType.TOPIC.getType());
				forum.setCreatedBySakaiUserId(userId);
				forum.setCategoryId(mainCategoryId);
				forum_id = jForumForumService.createForum(forum);
			}

		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		return forum_id;
	}
		
	/**
	 * Returns a java.util.date object converted from a string that represents a date in seconds in GMT
	 * 
	 * @param dateStr
	 *        String date in seconds in GMT
	 * @return Date object
	 */
	protected Date getDateTime(String dateStr)
	{
		Date date = new Date();
		Long longDateSecs = Long.parseLong(dateStr) - (8 * 3600);
		date.setTime((Long) (longDateSecs * 1000));
		return date;
	}
	
	/**
	 * Returns a java.util.date object converted from a string that is in the format yyyy-MM-dd hh:mm:ss
	 * 
	 * @param dateStr
	 *        String date in format yyyy-MM-dd hh:mm:ss
	 * @return Date object
	 */
	public Date getDateFromString(String dateStr)
	{
		Date date = null;
		 
		try
		{
			if (dateStr == null || dateStr.length() == 0 || dateStr.equals("")) return null;
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
			date = sdf.parse(dateStr);
		}
		catch (ParseException e)
		{
			return null;
		}
		return date;
	}
	
	/**
	 * Returns a java.util.date object converted from a string that is in the format mm/dd/yyyy
	 * @param dateStr
	 * dateStr is in short format
	 * @return
	 */
	public Date getShortDateFromString(String dateStr)
	{
		Date date = null;
		 
		try
		{
			if (dateStr == null || dateStr.length() == 0 || dateStr.equals("")) return null;
			SimpleDateFormat sdf = (SimpleDateFormat)DateFormat.getDateInstance(DateFormat.SHORT);
			date = sdf.parse(dateStr);
		}
		catch (ParseException e)
		{
			return null;
		}
		return date;
	}
	
	/**
	 * Gets a time object from a string date, throws parseException if there are format errors
	 * @param date String value of date
	 * @return Time Time object of string date, null otherwise
	 */
	protected Time getTimeFromString(String date){
		GregorianCalendar dateCal = new GregorianCalendar();
		Date endDate;
		Time endTime;
		if ((date == null)||(date.trim().length() == 0)) return null;
		try {
			SimpleDateFormat sdf = (SimpleDateFormat)DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
			endDate = sdf.parse(date);
			dateCal.setTime(endDate);
			endTime = TimeService.newTime(dateCal);
		} catch (ParseException e) {
			return null;
		}
		return endTime;
	}	    
	
	protected String getElementValue(Element ele)
	{
		return getElementValue(ele, null);
	}
	
	/**
	 * Returns text value of element. If path is not specified, text value of element is returned
	 * (could be empty string if there is no text). 
	 * 
	 * If path is specified, the path is resolved on the element and its text value is returned. 
	 * May return null or empty
	 * string when path isn't found or if text value doesn't exist
	 * 
	 * @param ele
	 *        Element object
	 * @param path
	 *        String value of path
	 * @return Text value of element with path resolved (if any) or null
	 */
	protected String getElementValue(Element ele, String path)
	{
		if (ele == null) return null;
		if (path == null || path.trim().length() == 0) return ele.getTextTrim();
		Element pathEle = (Element) ele.selectSingleNode(path);
		if (pathEle == null) return null;
		return pathEle.getTextTrim();
	}
	
	protected JForumCategoryService getJForumCategoryService()
	{
		return (JForumCategoryService) ComponentManager.get(JForumCategoryService.class);
	}
	
	protected JForumForumService getJForumForumService()
	{
		return (JForumForumService) ComponentManager.get(JForumForumService.class);
	}
		
	protected JForumPostService getJForumPostService()
	{
		return (JForumPostService) ComponentManager.get(JForumPostService.class);
	}
	
	protected JForumUserService getJForumUserService()
	{
		return (JForumUserService) ComponentManager.get(JForumUserService.class);
	}
	
	protected MeleteCHService getMeleteCHService()
	{
		return (MeleteCHService) ComponentManager.get(MeleteCHService.class);
	}
	
	protected MessageService getMessageService()
	{
		return (MessageService) ComponentManager.get(AnnouncementService.class);
	}

	protected ModuleService getModuleService()
	{
		return (ModuleService) ComponentManager.get(ModuleService.class);
	}
	
	/**
	 * Iterates through a list of pool objects and creates a list of pool titles
	 * @param pools
	 * @return
	 */
	protected List<String> getPoolNames(List<Pool> pools)
	{
		List poolNames = new ArrayList();
		for (Pool pool : pools)
		{
			poolNames.add(pool.getTitle());
		}
		return poolNames;
	}

	protected PoolService getPoolService()
	{
		return (PoolService) ComponentManager.get(org.etudes.mneme.api.PoolService.class);
	}
	
	protected QuestionService getQuestionService()
	{
		return (QuestionService) ComponentManager.get(org.etudes.mneme.api.QuestionService.class);
	}
	
	protected SectionService getSectionService()
	{
		return (SectionService) ComponentManager.get(SectionService.class);
	}
	
	protected SyllabusManager getSyllabusManager()
	{
		return (SyllabusManager) ComponentManager.get(SyllabusManager.class);
	}
	
	protected SecurityService getSecurityService()
	{
		return (SecurityService) ComponentManager.get(SecurityService.class);
	}
	
	/**
	 * Remove our security advisor.
	 */
	protected void popAdvisor()
	{
		getSecurityService().popAdvisor();
	}

	/**
	 * Setup a security advisor.
	 */
	protected void pushAdvisor()
	{
		// setup a security advisor
		getSecurityService().pushAdvisor(new SecurityAdvisor()
		{
			public SecurityAdvice isAllowed(String userId, String function, String reference)
			{
				return SecurityAdvice.ALLOWED;
			}
		});
	}

	
	protected void initializeServices()
	{
		announcementService = getAnnouncementService();
		assessmentService = getAssessmentService();
		attachmentService = getAttachmentService();
		jForumCategoryService = getJForumCategoryService();
		jForumForumService = getJForumForumService();
		jForumPostService = getJForumPostService();
		jForumUserService = getJForumUserService();
		meleteCHService = getMeleteCHService();
		moduleService = getModuleService();
		messageService = getMessageService();
		poolService = getPoolService();
		questionService = getQuestionService();
		sectionService = getSectionService();
		syllabusManager = getSyllabusManager();
		securityService = getSecurityService();
	}
	
	/**
	 * Checks if the tool is included in the site
	 * 
	 * @param toolName
	 *        tool name like sakai.jforum.tool.
	 * @return true if included in the site
	 */
	protected boolean isToolIncludedInSite(String toolName)
	{
		String toolId = null;
		try
		{
			Site site = SiteService.getSite(siteId);
			ToolConfiguration config = site.getToolForCommonId(toolName);
			if (config != null) toolId = config.getId();
		}
		catch (IdUnusedException e)
		{
			M_log.warn("isToolIncludedInSite: missing tool: " + siteId);
		}

		// no tool id?
		if (toolId == null) return false;
		return true;
	}
	
	/**
	 * check for math content and change it to wiris understandable code
	 * @param data
	 * @return
	 */
	public String replaceMathContents(String data)
	{
		if (data == null) return null;
		
		StringBuffer sb = new StringBuffer();

		// find the <applet> tags, and isolate the contents of a tag
		Pattern p = Pattern.compile("<(applet)\\s+.*?(/applet>)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

		// find the param tag with name as url_encoded_eq and get its value
		Pattern paramPattern = Pattern.compile("<param\\s+([^>]+)>", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);
		Pattern namePattern = Pattern.compile("name\\s*=\\s*[\"\'](url_encoded_eq)[\"\']", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);
		Pattern valuePattern = Pattern.compile("value\\s*=\\s*[\"\'](.*?)[\"\']", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);
		
		Matcher m = p.matcher(data);
		while (m.find())
		{
			String tagContents = m.group(0);

			// only if value has math stuff
			Matcher paramMatcher = paramPattern.matcher(tagContents);
			while (paramMatcher.find())
			{
				String paramValue = paramMatcher.group(0);
				Matcher eqMatcher = namePattern.matcher(paramValue);
				if (eqMatcher.find())
				{
					Matcher mathMatcher = valuePattern.matcher(paramValue);	
					if (mathMatcher.find())
					{
						String mathContents = mathMatcher.group(1);
						try
						{
							mathContents = URLDecoder.decode(mathContents, "UTF-8");
						}
						catch (Exception ex)
						{
						}
						if (mathContents.startsWith("<math")) tagContents = mathContents;
					}
				}
			} //param matcher
			m.appendReplacement(sb, Matcher.quoteReplacement(tagContents));
		}

		m.appendTail(sb);
		return sb.toString();
	}
	
	/**
	 * Reads the file contents.
	 * 
	 * @param fileUploadResource
	 *        File
	 * @return null if no data found
	 * @throws Exception
	 */
	protected byte[] readDatafromFile(File fileUploadResource) throws Exception
	{
		if (fileUploadResource.exists() && fileUploadResource.isFile())
		{
			FileInputStream fis = null;
			try
			{
				fis = new FileInputStream(fileUploadResource);

				byte buf[] = new byte[(int) fileUploadResource.length()];
				fis.read(buf);
				return buf;
			}
			catch (Exception ex)
			{
				throw ex;
			}
			finally
			{
				if (fis != null) fis.close();
			}
		}
		else
			return null;
	}
	
	/**
	 * 
	 * @param fileName
	 * @return
	 * @throws Exception
	 */
	protected byte[] readDatafromFile(String fileName) throws Exception
	{
		// different packages have different ways of paths
		File fileUploadResource = new File(fileName);
		if (fileUploadResource == null || !fileUploadResource.exists()) fileUploadResource = new File(unzipBackUpLocation + File.separator + fileName);
		if (fileUploadResource == null || !fileUploadResource.exists()) fileUploadResource = new File(unzipBackUpLocation + fileName);		
			
		if (fileUploadResource.exists() && fileUploadResource.isFile())
		{
			FileInputStream fis = null;
			try
			{
				fis = new FileInputStream(fileUploadResource);

				byte buf[] = new byte[(int) fileUploadResource.length()];
				fis.read(buf);
				return buf;
			}
			catch (Exception ex)
			{
				throw ex;
			}
			finally
			{
				if (fis != null) fis.close();
			}
		}
		else
			return null;
	}
	
	/**
	 * Checks if section with same title already exists in the module.
	 * 
	 * @param checkSectionTitle
	 *        to be added section's title
	 * @param module
	 *        melete module object
	 * @return true if section with same title exists.
	 */
	protected boolean sectionExists(String checkSectionTitle, ModuleObjService module)
	{
		if (module == null) return true;
		Map<Integer, SectionObjService> sections = module.getSections();
		if (sections == null) return false;
		Set<Integer> keys = sections.keySet();
		for (Iterator<Integer> i = keys.iterator(); i.hasNext();)
		{
			SectionObjService s = sections.get(i.next());
			if (s.getTitle().equals(checkSectionTitle)) return true;
		}
		return false;
	}
	
	/**
	 * 
	 * @param baseFolder
	 */
	protected void setBaseFolder(String baseFolder)
	{
		this.baseFolder = baseFolder;
	}

	/**
	 * Checks if syllabus data with same title already exists.
	 * 
	 * @param checkSyllabusTitle
	 *        to be added syllabus data's title
	 * @param syllabusItem
	 *        main syllabus Item
	 * @return true if exists
	 */
	protected boolean syllabusItemExists(String checkSyllabusTitle, SyllabusItem syllabusItem)
	{
		Set<SyllabusData> allItems = syllabusManager.getSyllabiForSyllabusItem(syllabusItem);
		for (Iterator<SyllabusData> i = allItems.iterator(); i.hasNext();)
		{
			SyllabusData s = (SyllabusData) i.next();
			if (s.getTitle().equals(checkSyllabusTitle)) return true;
		}
		return false;
	}
	
	/**
	 * Import embedded data.
	 * 
	 * @param fileName
	 *        Embedded media file name
	 * @return the embedded resource Id or null if file doesn't exist
	 */
	protected String transferEmbeddedData(String fileName, String name, Post post, String tool)
	{
		String addCollectionId = null;

		try
		{
			name = name.replace("\\", "/");
			if (name.lastIndexOf("/") != -1) name = name.substring(name.lastIndexOf("/") + 1);

			String res_mime_type = name.substring(name.lastIndexOf(".") + 1);
			res_mime_type = ContentTypeImageService.getContentType(res_mime_type);
			byte[] content_data = readDatafromFile(fileName);
			if (content_data == null || content_data.length == 0) return null;
			ResourcePropertiesEdit res = ContentHostingService.newResourceProperties();
			res.addProperty(ResourceProperties.PROP_DISPLAY_NAME, name);

			if ("JForum".equals(tool))
			{
				org.etudes.api.app.jforum.Attachment  attachment = jForumPostService.newAttachment(name, res_mime_type, "", content_data);
				post.setHasAttachments(true);
				post.getAttachments().add(attachment);
				// add to the list of imported files
				importedCourseFiles.add(fileName);
				return "";
			}
			if ("Mneme".equals(tool))
			{
				Reference ref = attachmentService.addAttachment(AttachmentService.MNEME_APPLICATION, siteId, AttachmentService.DOCS_AREA,
						AttachmentService.NameConflictResolution.rename, name, content_data, res_mime_type,
						AttachmentService.MNEME_THUMB_POLICY, AttachmentService.REFERENCE_ROOT);

				// add to the list of imported files
				importedCourseFiles.add(fileName);
				return ref.getId();
			}

			pushAdvisor();
			if ("Melete".equals(tool))
			{
				addCollectionId = meleteCHService.getUploadCollectionId(siteId);
				res.addProperty(ContentHostingService.PROP_ALTERNATE_REFERENCE, Entity.SEPARATOR + "meleteDocs");
			}
			else
			{
				addCollectionId = "/group/" + siteId + "/";
			}
			name = Validator.escapeResourceName(name);
			if (name.length() > MAX_NAME_LENGTH) name = name.substring(0, MAX_NAME_LENGTH);
			ContentResource importedResource = ContentHostingService.addResource(addCollectionId + name, res_mime_type, content_data, res, 0);

			// add to the list of imported files
			importedCourseFiles.add(fileName);
			return importedResource.getId();
		}
		catch (IdUsedException e)
		{
			// return a reference to the existing file
			Reference reference = EntityManager.newReference(ContentHostingService.getReference(addCollectionId + name));

			// add to the list of imported files
			importedCourseFiles.add(fileName);
			return reference.getId();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		finally
		{
			popAdvisor();
		}
		return null;
	}
	
	/**
	 * If the backup package has files not referenced in the material then bring them over in MeleteDocs collection.
	 */
	protected void transferExtraCourseFiles(File courseFilesDir, String collectionId, String parentName)
	{
		if (collectionId == null || collectionId.length() == 0) collectionId = ContentHostingService.getSiteCollection(this.siteId);

		try
		{
			FileExtensionFilter excludeDatXml = new FileExtensionFilter();
			File[] courseFilesinPackage = courseFilesDir.listFiles(excludeDatXml);

			if (courseFilesinPackage == null || courseFilesinPackage.length == 0) return;
			for (File file : courseFilesinPackage)
			{
				if (importedCourseFiles.contains(parentName + file.getName())) continue;

				if (file.isDirectory())
				{
					String subCollectionId = createSubFolderCollection(collectionId, file.getName(),"",null);
					transferExtraCourseFiles(file, subCollectionId, parentName + file.getName() + "/");
				}
				// if directory name skip it
				if (collectionId != null && file.getName().contains(".")) 
					{
						buildExtraResourceItem(file.getName(), file.getName(), collectionId);
					}
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	/**
	 * brings in unreferenced files
	 * 
	 * @param f
	 *        File
	 * @param collectionId
	 *        meleteDocs collection Id to add file
	 */
	protected String transferExtraMeleteFile(File f, String collectionId)
	{
		try
		{
			byte[] content_data = readDatafromFile(f);
			if (content_data == null || content_data.length == 0) return "";

			pushAdvisor();
			String name = f.getName();
			name = name.replace("\\", "/");
			if (name.lastIndexOf("/") != -1) name = name.substring(name.lastIndexOf("/") + 1);

			String res_mime_type = name.substring(name.lastIndexOf(".") + 1);
			res_mime_type = ContentTypeImageService.getContentType(res_mime_type);
			ResourcePropertiesEdit res = meleteCHService.fillInSectionResourceProperties(false, name, "");

			// add to the list of imported files
			importedCourseFiles.add(name);

			name = Validator.escapeResourceName(name);
			if (name.length() > MAX_NAME_LENGTH) name = name.substring(0, MAX_NAME_LENGTH);
			ContentResource cr = ContentHostingService.addResource(collectionId + name, res_mime_type, content_data, res, 0);
			return cr.getId();
		}
		catch (IdUsedException e)
		{
			// do nothing
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		finally
		{
			popAdvisor();
		}

		return "";
	}

}
