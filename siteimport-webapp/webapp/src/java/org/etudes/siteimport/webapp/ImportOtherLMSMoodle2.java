/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/siteimport/trunk/siteimport-webapp/webapp/src/java/org/etudes/siteimport/webapp/ImportOtherLMSMoodle2.java $
 * $Id: ImportOtherLMSMoodle2.java 7250 2014-01-23 19:51:53Z rashmim $
 ***********************************************************************************
 *
 * Copyright (c) 2011, 2012, 2013 Etudes, Inc.
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
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.XPath;
import org.etudes.api.app.jforum.Category;
import org.etudes.api.app.jforum.Forum;
import org.etudes.api.app.jforum.Grade;
import org.etudes.api.app.jforum.Topic;
import org.etudes.api.app.melete.ModuleObjService;
import org.etudes.api.app.melete.SectionObjService;
import org.etudes.mneme.api.Assessment;
import org.etudes.mneme.api.AssessmentType;
import org.etudes.mneme.api.EssayQuestion;
import org.etudes.mneme.api.EssayQuestion.EssaySubmissionType;
import org.etudes.mneme.api.FillBlanksQuestion;
import org.etudes.mneme.api.MatchQuestion;
import org.etudes.mneme.api.MatchQuestion.MatchChoice;
import org.etudes.mneme.api.MultipleChoiceQuestion;
import org.etudes.mneme.api.Part;
import org.etudes.mneme.api.Pool;
import org.etudes.mneme.api.PoolDraw;
import org.etudes.mneme.api.Question;
import org.etudes.mneme.api.QuestionGrouping;
import org.etudes.mneme.api.QuestionPick;
import org.etudes.mneme.api.TrueFalseQuestion;
import org.etudes.util.HtmlHelper;
import org.sakaiproject.content.cover.ContentHostingService;
import org.sakaiproject.content.cover.ContentTypeImageService;
import org.sakaiproject.tool.cover.ToolManager;
import org.sakaiproject.user.cover.UserDirectoryService;
import org.sakaiproject.util.FormattedText;
import org.sakaiproject.util.StringUtil;

public class ImportOtherLMSMoodle2 extends BaseImportOtherLMS
{
	/*
	 * Inner class to store the question id and its corresponding poolId, questionId in mneme.
	 */
	private class QuestionCategoryPool
	{
		private String poolTitle = null;
		private String poolId = null;
		private String mnemeQuestionId = null;
		private String qtype = null;
		
		public QuestionCategoryPool(String poolId,String poolTitle, String mnemeQuestionId, String qtype)
		{
			this.mnemeQuestionId = mnemeQuestionId;
			this.poolId = poolId;
			this.poolTitle = poolTitle;
			this.qtype = qtype;
		}

		public String getMnemeQuestionId()
		{
			return this.mnemeQuestionId;
		}

		public String getPoolId()
		{
			return this.poolId;
		}
		
		public String getPoolTitle()
		{
			return this.poolTitle;
		}
		
		public String getQtype()
		{
			return this.qtype;
		}
	}
	
	private Document backUpDoc;

	/**
	 * 
	 * @param backUpDoc
	 * @param qtiDoc
	 * @param unzipBackUpLocation
	 * @param unzipLTILocation
	 */
	public ImportOtherLMSMoodle2(Document backUpDoc, String unzipBackUpLocation, String siteId, String userId)
	{
		super(siteId, unzipBackUpLocation, userId);
		this.backUpDoc = backUpDoc;
	}

	/**
	 * The main method which reads the document and calls the specific tool processing method to import in etudes.
	 * 
	 * @return The success message string which states how many got imported. "" if file can't be read or of bad format.
	 */
	public String transferEntities()
	{
		Element bkRoot = backUpDoc.getRootElement();
		List allChild = bkRoot.elements();
		String success_message = "You have successfully imported material from the Moodle 2.0 backup file.";
		XPath xpath = backUpDoc.createXPath("/moodle_backup/information/contents");

		// check for mod type to direct to right tools
		Element eleOrg = (Element) xpath.selectSingleNode(backUpDoc);
		if (eleOrg == null) return "";
		initializeServices();
		setBaseFolder(unzipBackUpLocation);

		// step 2: find the class discussions forum
		org.etudes.api.app.jforum.User u = null;
		u = jForumUserService.getBySakaiUserId(userId);
		int mainCategoryId = buildDiscussionCategory();

		// get the existing modules from the site
		List<ModuleObjService> existingModules = moduleService.getModules(siteId);
		HashMap<String, ModuleObjService> module_sections = new HashMap<String, ModuleObjService>();
		
		// get week dates 
		List<CalculatedDates> weekDates = getWeekDates();
		Element eleModules = eleOrg.element("sections");
		if (eleModules != null)
		{
			int moduleCount = -1;
			
			for (Iterator<?> iter = eleModules.elementIterator("section"); iter.hasNext();)
			{
				Element sectionElement = (Element) iter.next();
				module_sections = buildMeleteModule(sectionElement, weekDates, ++moduleCount, module_sections, existingModules);
			}
		}
		// bring in all pools of questions. 
		Map<String, QuestionCategoryPool> question_categories_pool = new HashMap<String, QuestionCategoryPool>();

		List<Element> questionCategoryList = getQuestionCategoryElement();
		if (questionCategoryList != null)
		{
			for (Element questionCategory : questionCategoryList)
			{
				buildPool(questionCategory, question_categories_pool);
			}
		}

		Element eleActivities = eleOrg.element("activities");
		if (eleActivities != null)
		{
			for (Iterator<?> iter = eleActivities.elementIterator("activity"); iter.hasNext();)
			{
				Element activityElement = (Element) iter.next();
				String title = ((Element) activityElement.selectSingleNode("title")).getText();
				Element actionNameElement = (Element) activityElement.selectSingleNode("modulename");
				String actionName = actionNameElement.getTextTrim();
				CalculatedDates activityDates = null;
				
				int number = 0;
				try
				{
					Map<String, String> moduleXmlContents = getContentFromModuleXml(activityElement);
					String sectionNumber = moduleXmlContents.get("sectionnumber");
					number = Integer.parseInt(sectionNumber);
					if (number > 0) activityDates = weekDates.get(number - 1);
					else activityDates = weekDates.get(0);
				}
				catch (Exception ex)
				{

				}
				Date activityStartDate = (activityDates != null) ? activityDates.getStartDate() : null;
				Date activityEndDate = (activityDates != null) ? activityDates.getDueDate() : null;
				Date activityUntilDate = (activityDates != null) ? activityDates.getUntilDate() : null;
				
				buildAssignment(activityElement, actionName, activityStartDate, activityEndDate, activityUntilDate, number);

				if (u != null)
				{
					buildDiscussions(activityElement, actionName, activityStartDate, activityEndDate,activityUntilDate,number, u, mainCategoryId);
				}

				buildSyllabusOrMeleteSection(activityElement, module_sections, actionName);

				buildTest(activityElement, question_categories_pool, module_sections, actionName,activityStartDate, activityEndDate, activityUntilDate, number);
				
				buildResourceFolders(activityElement, actionName);
			}
		}
		
		 checkClassDiscussionsForum(mainCategoryId);
		 
		// import unreferred files to /group collection
		 transferExtraCourseFiles();

		return success_message;
	}

	/**
	 * 
	 * @param annc_instance
	 * @param actionName
	 */
	private void buildAnnouncement(Element annc_instance, String actionName)
	{
		try
		{
			List<Element> announcements = getDiscussionElements(annc_instance);
			if (announcements == null) return;

			ArrayList<String> fetchAttachList = new ArrayList<String>();
			ArrayList<String> fetchAttachFileNamesList = new ArrayList<String>();
			ArrayList<Element> embedFiles = new ArrayList<Element>();
			getEmbedAndAttachmentFileNames(annc_instance, embedFiles, fetchAttachList, fetchAttachFileNamesList);

			for (Element announcement : announcements)
			{
				String topicSubject = announcement.selectSingleNode("subject").getText();
				// strip off html tags from the title
				topicSubject = FormattedText.convertFormattedTextToPlaintext(topicSubject);

				String topicBody = announcement.selectSingleNode("message").getText();
				Date releaseDate = null;
				if (announcement.selectSingleNode("created") != null) releaseDate = getDateTime(announcement.selectSingleNode("created").getText());

				buildAnnouncement(topicSubject, topicBody, releaseDate, null, embedFiles);
			}
		}
		catch (Exception e)
		{
			// do nothing
		}
	}

	/**
	 * 
	 * @param organizationItemElement
	 */
	private void buildAssignment(Element assignment_activity, String actionName, Date weekStartDate, Date weekEndDate, Date weekUntilDate, int number)
	{
		if (!"assignment".equals(actionName)) return;

		String desc = null;
		Integer tries = new Integer(1);
		EssayQuestion.EssaySubmissionType assignmentType = EssayQuestion.EssaySubmissionType.both;
		String points = "10";
		try
		{
			Map<String, String> contents = getContentsDatFile(getDataFile(assignment_activity, actionName), 1);
			if (contents.isEmpty() || (!contents.containsKey("root") && !contents.get("root").equals("assignment"))) return;

			String title = "New Assignment";
			title = ((Element) assignment_activity.selectSingleNode("title")).getText();

			Assessment assmt = createNewAssessment(title);
			if (assmt == null) return;

			assmt.setTitle(title);
			assmt.setType(AssessmentType.assignment);

			Pool newPool = poolService.newPool(siteId);
			if (newPool == null) return;

			newPool.setTitle(title);
			Question question = null;
			if (contents.containsKey("assignmenttype"))
			{
				String type = contents.get("assignmenttype");
				// offline means task like go to museum etc
				if (("offline").equals(type))
				{
					assignmentType = EssayQuestion.EssaySubmissionType.none;
					question = createTaskQuestion(contents.get("intro"), newPool);
					
				}
				else if (("online").equals(type)) assignmentType = EssayQuestion.EssaySubmissionType.inline;
			}

			if (question == null) question = (EssayQuestion) createEssayQuestion(null, contents.get("intro"), assignmentType, newPool);

			Part part = assmt.getParts().addPart();
			QuestionPick questionPick = part.addPickDetail(question);
			Date openDate = null;
			Date dueDate = null;
			
			if (contents.containsKey("timeavailable") && contents.get("timeavailable") != null)
			{
				String timeAvStr = contents.get("timeavailable");
				if (timeAvStr != null && timeAvStr.length() != 0 && !timeAvStr.equals("0")) openDate = getDateTime(timeAvStr);
			}

			if (openDate != null)
			{
				assmt.getDates().setOpenDate(openDate);
				if (weekStartDate != null && openDate.before(weekStartDate)) assmt.getDates().setOpenDate(weekStartDate);
				if (weekStartDate != null && openDate.after(weekStartDate))
				{
					if (weekEndDate != null && openDate.after(weekEndDate)) openDate = weekStartDate;
				}
			}
			else assmt.getDates().setOpenDate(weekStartDate);
			
			if (contents.containsKey("timedue") && contents.get("timedue") != null)
			{
				String timeAvStr = contents.get("timedue");
				if (timeAvStr != null && timeAvStr.length() != 0 && !timeAvStr.equals("0")) dueDate = getDateTime(timeAvStr);
			}

			if (dueDate != null)
			{
				assmt.getDates().setDueDate(dueDate);
				if (weekStartDate != null && dueDate.before(weekStartDate)) assmt.getDates().setDueDate(weekEndDate);
				if (weekEndDate != null && dueDate.after(weekEndDate)) assmt.getDates().setDueDate(weekEndDate);
			}
			else assmt.getDates().setDueDate(weekEndDate);
			// from General section..it should stay open
			if (number == 0)
			{
				assmt.getDates().setDueDate(null);
				assmt.getDates().setAcceptUntilDate(weekUntilDate);
			}
						
			if (contents.containsKey("preventlate"))
			{
				String prevStr = contents.get("preventlate");
				if (prevStr.equals("0") && assmt.getDates().getDueDate() != null)
				{
					GregorianCalendar gc1 = new GregorianCalendar();
					gc1.setTime(assmt.getDates().getDueDate());
					gc1.add(java.util.Calendar.DATE, 2);
					assmt.getDates().setAcceptUntilDate(gc1.getTime());
				}
			}

			// TODO:rubrics and embed
			// If grading info is missing, by default give 10 points to make assignment valid
			if (contents.containsKey("grade")) points = contents.get("grade");
			questionPick.setPoints(new Float(points).floatValue());

			// If pool has one question then only assign points
			if (newPool.getNumQuestions() <= 1) newPool.setPoints(new Float(points).floatValue());

			if (contents.containsKey("resubmit")) tries = new Integer(contents.get("resubmit"));
			assmt.setTries(tries);

			assmt.getGrading().setGradebookIntegration(Boolean.TRUE);
			if (assmt.getParts().getTotalPoints().floatValue() <= 0) assmt.setNeedsPoints(Boolean.FALSE);

			assessmentService.saveAssessment(assmt);
			poolService.savePool(newPool);
		}
		catch (Exception e)
		{
			// skip it do nothing
			e.printStackTrace();
		}
	}

	/**
	 * 
	 * @param jforum_instance
	 * @param actionName
	 * @param forum_id
	 * @param postedBy
	 * @param all
	 */
	private void buildDiscussions(Element jforum_instance, String actionName, Date weekStartDate, Date weekEndDate, Date weekUntilDate, int number, org.etudes.api.app.jforum.User postedBy, int mainCategoryId)
	{
		if (!"forum".equals(actionName)) return;
		int discussForum = getClassDiscussionsForum();
		if (discussForum == 0) return;

		int discussCategoryId = mainCategoryId;
		String subject = null;
		String body = null;
		String gradePoints = null;
		Date openDate = null;
		Date dueDate = null;
		int minPosts = 0;
		int oneMinPosts = 1;
		ArrayList<String> fetchAttachList = new ArrayList<String>();
		ArrayList<String> fetchAttachFileNamesList = new ArrayList<String>();
		ArrayList<Element> embedFiles = new ArrayList<Element>();

		try
		{
			Map<String, String> contents = getContentsDatFile(getDataFile(jforum_instance, actionName), 1);
			if (!contents.containsKey("root") && !contents.get("root").equals("forum")) return;

			subject = contents.get("name");
			if (subject == null) return;

			String type = contents.get("type");
			if ("news".equals(type))
			{				
				buildAnnouncement(jforum_instance, actionName);
				return;
			}

			if (contents.containsKey("assessed") && !"0".equals(contents.get("assessed"))) minPosts = Integer.parseInt(contents.get("assessed"));

			body = contents.get("intro");

			if (contents.containsKey("scale") && !"0".equals(contents.get("scale")))
			{
				gradePoints = contents.get("scale");
				Integer gpoints = (gradePoints != null && gradePoints.length() != 0) ? new Integer(gradePoints) : null;
				if (gpoints != null && gpoints.intValue() < 0)
				{
					gradePoints = null;
				}
				
				if (contents.containsKey("assesstimestart"))
				{
					String oDate = contents.get("assesstimestart");
					if (oDate != null && oDate.length() != 0 && !oDate.equals("0")) openDate = getDateTime(oDate);
				}

				if (contents.containsKey("assesstimefinish"))
				{
					String dDate = contents.get("assesstimefinish");
					if (dDate != null && dDate.length() != 0 && !dDate.equals("0")) dueDate = getDateTime(dDate);
				}
			}

			// look for groupid and make gradeable category
			// read from module.xml to look for sectionNumber and groupmode
			Map<String, String> moduleContents = getContentsDatFile(getDataFile(jforum_instance, "module"), 0);

			String categoryTitle = "Week " + moduleContents.get("sectionnumber") + " Discussions";
			if (moduleContents.containsKey("groupmode") && !"0".equals(moduleContents.get("groupmode")))
				discussCategoryId = buildDiscussionGradableCategory(categoryTitle, gradePoints, minPosts);

			if (openDate == null && moduleContents.containsKey("availablefrom"))
			{
				String oDate = moduleContents.get("availablefrom");
				if (oDate != null && oDate.length() != 0 && !oDate.equals("0")) openDate = getDateTime(oDate);
			}

			if (dueDate == null && moduleContents.containsKey("availableuntil"))
			{
				String dDate = moduleContents.get("availableuntil");
				if (dDate != null && dDate.length() != 0 && !dDate.equals("0")) dueDate = getDateTime(dDate);
			}
			
			if (openDate != null)
			{
				if (weekStartDate != null && openDate.before(weekStartDate)) openDate = weekStartDate;
				if (weekStartDate != null && openDate.after(weekStartDate))
				{
					if (weekEndDate != null && openDate.after(weekEndDate)) openDate = weekStartDate;
				}
			}
			else
				openDate = weekStartDate;
			
			if (dueDate != null)
			{
				if (weekStartDate != null && dueDate.before(weekStartDate)) dueDate = weekEndDate;
				if (weekEndDate != null && dueDate.after(weekEndDate)) dueDate = weekEndDate;
			}
			else dueDate = weekEndDate;
			// from General section..it should stay open
			if (number == 0)
			{
				dueDate = null;
			}
			// read inforef/fileref/file/id from inforref.xml for attachments and embedded media
			getEmbedAndAttachmentFileNames(jforum_instance, embedFiles, fetchAttachList, fetchAttachFileNamesList);

			// build discussions based on type. If single then add as topic...for others if gradable then add as grade by forum otherwise normal forum.
			if ("single".equals(type))
			{
				List<Topic> all = jForumPostService.getForumTopics(discussForum);
				buildDiscussionTopics(subject, body, "Normal", null, embedFiles, null, gradePoints, openDate, dueDate, minPosts, discussForum,
						postedBy, all);
			}
			else
			{
				if (discussCategoryId == 0) return;
				List<Forum> allForums = jForumForumService.getCategoryForums(discussCategoryId);

				if ("blog".equals(type) && gradePoints != null)
					discussForum = buildDiscussionForumsWithDescriptionTopic(subject, body, "NORMAL", "gradeTopics", gradePoints, openDate, dueDate,
							minPosts, discussCategoryId, userId, allForums, postedBy, embedFiles);
				else
				{
					if (gradePoints == null || gradePoints.length() == 0)
						discussForum = buildDiscussionForumsWithDescriptionTopic(subject, body, "NORMAL", "nograde", gradePoints, openDate, dueDate,
								minPosts, discussCategoryId, userId, allForums, postedBy, embedFiles);
					else
						discussForum = buildDiscussionForumsWithDescriptionTopic(subject, body, "NORMAL", "gradeForum", gradePoints, openDate,
								dueDate, (minPosts > 0) ? minPosts : oneMinPosts, discussCategoryId, userId, allForums, postedBy, embedFiles);
				}
			}

			List<Element> discussions = getDiscussionElements(jforum_instance);
			if (discussions == null || discussions.size() == 0)
				return;
			else
			{ // update forum to be reply_only
				Forum f = jForumForumService.getForum(discussForum);
				f.setType(Forum.ForumType.REPLY_ONLY.getType());
				f.setModifiedBySakaiUserId(UserDirectoryService.getCurrentUser().getId());
				jForumForumService.modifyForum(f);
			}
			 
			for (Element discuss : discussions)
			{
				String topicSubject = discuss.selectSingleNode("subject").getText();
				String topicBody = discuss.selectSingleNode("message").getText();
				if (discussForum != 0) return;

				buildDiscussionTopics(topicSubject, topicBody, "Normal", null, embedFiles, null, gradePoints, openDate, dueDate, minPosts,
						discussForum, postedBy, jForumPostService.getForumTopics(discussForum));
			}
		}
		catch (Exception e)
		{
			M_log.warn("buildDiscussions: " + e);
			e.printStackTrace();
		}

	}

	/**
	 * Builds melete modules for all /moodle_backup/information/contents/sections/section. If summary is there add as overview section.
	 * 
	 * @param sectionElement
	 * @param module_sections
	 * @param existingModules
	 * @return Map of newly created module objects keyed by sectionid
	 */
	private HashMap<String, ModuleObjService> buildMeleteModule(Element sectionElement, List<CalculatedDates> weekDates, int num, HashMap<String, ModuleObjService> module_sections,
			List<ModuleObjService> existingModules)
	{
		Element number = (Element) sectionElement.selectSingleNode("title");
		Element sectionIdElement = (Element) sectionElement.selectSingleNode("sectionid");
		String sectionId = sectionIdElement.getTextTrim();

		String summaryText = null;
		Date startDate = null;
		Date endDate = null;
		Date allowUntil = null;
		try
		{
			Map<String, String> contents = getContentsDatFile(getDataFile(sectionElement, "section"), 0);
			summaryText = contents.get("summary");			
		}
		catch (Exception e)
		{
			// do nothing
		}

		String checkModuleTitle = "Week " + num;

		if (num == 0) checkModuleTitle = "Course Information";

		if (weekDates != null && num <= weekDates.size())
		{
			if (num == 0)
			{
				CalculatedDates dates = weekDates.get(0);
				startDate = dates.getStartDate();
				allowUntil = dates.getUntilDate();
			}
			else
			{
				CalculatedDates dates = weekDates.get(num - 1);
				startDate = dates.getStartDate();
				endDate = dates.getDueDate();
				allowUntil = dates.getUntilDate();
			}

			ModuleObjService module = findOrAddModule(checkModuleTitle, 0, summaryText, startDate, endDate, allowUntil, existingModules, null, null,
					null);
			module_sections.put(sectionId, module);
		}
		return module_sections;
	}

	/**
	 * 
	 * @param activityElement
	 * @param title
	 * @param contents
	 * @param module_sections
	 */
	private void buildMeleteSectionFromDescription(Element activityElement, String title, String contents,
			HashMap<String, ModuleObjService> module_sections)
	{
		try
		{
			Map<String, String> moduleContents = getContentFromModuleXml(activityElement);
			String sectionId = moduleContents.get("sectionid");

			if (!module_sections.containsKey(sectionId)) return;

			ModuleObjService module = module_sections.get(sectionId);

			if (sectionExists(title, module)) return;
			SectionObjService section = buildSection(title, module);
			// save section
			section = buildTypeEditorSection(section, contents, module, null, title, null, null);
		}
		catch (Exception e)
		{
			// do nothing
		}
	}
	
	/**
	 * Build Folder under Resources tool and add files under the folder.
	 * Enable Resources tool if site doesn't have it.
	 * 
	 * @param folder_instance
	 * @param actionName
	 */
	private void buildResourceFolders(Element folder_instance, String actionName)
	{
		try
		{
			if (!"folder".equals(actionName)) return;

			Map<String, String> contents = getContentsDatFile(getDataFile(folder_instance, actionName), 1);
			if (contents.isEmpty() || (!contents.containsKey("root") && !contents.get("root").equals("folder"))) return;

			String folderName = "Untitled Folder";
			String folderDescription = "";

			if (contents.containsKey("name")) folderName = contents.get("name");
			if (contents.containsKey("intro")) folderDescription = contents.get("intro");
			folderDescription = HtmlHelper.cleanAndAssureAnchorTarget(folderDescription, true);

			addResourcesTool();
			pushAdvisor();
			String collectionId = ContentHostingService.getSiteCollection(this.siteId);
			String folder_id = createSubFolderCollection(collectionId, folderName, folderDescription, null);

			// read inforef.xml for file references
			List<String> ids = getFileReferences(getDataFile(folder_instance, "inforef"));
			File f = new File(unzipBackUpLocation + File.separator + "files");

			for (String id : ids)
			{
				Element file = getFileElement(id);
				if (file.selectSingleNode("filesize") != null && !file.selectSingleNode("filesize").getText().equals("0"))
				{
					String displayName = file.selectSingleNode("filename").getText();
					String fileName = file.selectSingleNode("contenthash").getText();
					fileName = findFileinDirectory(f, fileName);

					String res_mime_type = displayName.substring(displayName.lastIndexOf(".") + 1);
					res_mime_type = ContentTypeImageService.getContentType(res_mime_type);

					byte[] content_data = readDatafromFile(fileName);
					if (content_data == null) return;

					if (!(importedCourseFiles.contains(fileName)))
					{
						buildResourceToolItem(folder_id, displayName, "", "", content_data, res_mime_type);
						importedCourseFiles.add(fileName);
					}
				}
			}
			popAdvisor();
		}
		catch (Exception e)
		{
			// do nothing
		}
	}
	
	/**
	 * Build melete sections. If action is url then build typelink otherwise typeeditor.
	 * 
	 * @param activityElement
	 *        activity element
	 * @param module_sections
	 *        map of modules created keyed by sectionid.
	 * @param actionName
	 */
	private void buildSyllabusOrMeleteSection(Element activityElement, HashMap<String, ModuleObjService> module_sections, String actionName)
	{
		String title = ((Element) activityElement.selectSingleNode("title")).getText();
		Element sectionIdElement = (Element) activityElement.selectSingleNode("sectionid");
		String sectionId = sectionIdElement.getTextTrim();

		ArrayList<String> fetchAttachList = new ArrayList<String>();
		ArrayList<String> fetchAttachFileNamesList = new ArrayList<String>();
		ArrayList<Element> embedFiles = new ArrayList<Element>();

		// build syllabus
		if (title.contains("Syllabus") || title.contains("syllabus") || title.contains("SYLLABUS"))
		{
			buildSyllabus(activityElement, actionName);
			return;
		}

		if (!("url".equals(actionName) || "page".equals(actionName) || "resource".equals(actionName))) return;

		if (!module_sections.containsKey(sectionId)) return;

		ModuleObjService module = module_sections.get(sectionId);
		String section_content = "";

		if (sectionExists(title, module)) return;
		SectionObjService section = buildSection(title, module);

		try
		{
			Map<String, String> contents = getContentsDatFile(getDataFile(activityElement, actionName), 1);
			if (contents.isEmpty()
					|| (!contents.containsKey("root") && (!contents.get("root").equals("url") || !contents.get("root").equals("page") || !contents
							.get("root").equals("resource")))) return;

			if (contents.containsKey("intro") && contents.get("intro") != null)
			{
				String intro = contents.get("intro");
				if (title != null && intro != null && !title.equals(intro)) section_content = intro + "\n";
			}

			// read inforef/fileref/file/id from inforref.xml for attachments and embedded media
			getEmbedAndAttachmentFileNames(activityElement, embedFiles, fetchAttachList, fetchAttachFileNamesList);

			if (contents.containsKey("externalurl") && contents.get("externalurl") != null)
			{
				String check_name = contents.get("externalurl");
				section_content = buildTypeLinkUploadResource(check_name, check_name, section_content, null, null);
			}

			if (contents.containsKey("content") && contents.get("content") != null)
			{
				section_content = contents.get("content");
				section_content = cleanText(section_content);
			}

			if ("resource".equals(actionName))
			{
				for (int i = 0; i < fetchAttachList.size(); i++)
				{
					String attachFile = fetchAttachList.get(i);
					String name = attachFile;
					if (fetchAttachFileNamesList != null) name = fetchAttachFileNamesList.get(i);
					section_content = buildTypeLinkUploadResource(attachFile, name, section_content, null, null);
				}
			}

			// save section
			section = buildTypeEditorSection(section, section_content, module, null, title, embedFiles, null);
		}
		catch (Exception e)
		{
			// do nothing
		}

	}

	/**
	 * Build syllabus item or url from the activity element.
	 * 
	 * @param syllabus_instance
	 *        activity element
	 * @param actionName
	 */
	private void buildSyllabus(Element syllabus_instance, String actionName)
	{
		try
		{
			if (!("url".equals(actionName) || "page".equals(actionName) || "resource".equals(actionName))) return;

			String asset = "";
			boolean draft = false;
			String title = "Untitled";
			String type = null;
			String[] attach = null;
			String[] attachDisplayName = null;
			String check_name = null;
			ArrayList<String> fetchAttachList = new ArrayList<String>();
			ArrayList<String> fetchAttachFileNamesList = new ArrayList<String>();
			ArrayList<Element> embedFiles = new ArrayList<Element>();
			if (syllabus_instance.selectSingleNode("title") != null) title = ((Element) syllabus_instance.selectSingleNode("title")).getText();

			if ("url".equals(actionName))
				type = "redirectUrl";
			else
				type = "item";

			Map<String, String> contents = getContentsDatFile(getDataFile(syllabus_instance, actionName), 1);
			if (contents.isEmpty()
					|| (!contents.containsKey("root") && (!contents.get("root").equals("url") || !contents.get("root").equals("page") || !contents
							.get("root").equals("resource")))) return;

			if (contents.containsKey("externalurl") && contents.get("externalurl") != null)
			{
				check_name = contents.get("externalurl");
				attach = new String[1];
				attach[0] = check_name;
			}

			if (actionName.equals("page") && contents.containsKey("content") && contents.get("content") != null)
			{
				asset = contents.get("content");
				asset = cleanText(asset);
			}

			// attachments
			if (actionName.equals("resource"))
			{
				if (contents.containsKey("intro")) asset = contents.get("intro");
			}

			// read inforef/fileref/file/id from inforref.xml for attachments and embedded media
			getEmbedAndAttachmentFileNames(syllabus_instance, embedFiles, fetchAttachList, fetchAttachFileNamesList);
			if (fetchAttachList.size() > 0)
			{
				attach = (String[]) fetchAttachList.toArray(new String[fetchAttachList.size()]);
			}

			if (fetchAttachFileNamesList.size() > 0)
			{
				attachDisplayName = (String[]) fetchAttachFileNamesList.toArray(new String[fetchAttachFileNamesList.size()]);
			}

			buildSyllabus(title, asset, draft, type, attach, "", embedFiles, null, attachDisplayName);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	/**
	 * Creates pools of questions
	 * @param categoryElement
	 * 	the element from questions.xml file
	 * @param question_categories_pool
	 * Map which keys with question id from xml file and value is the private class object which contains respective pool, question id etc
	 */
	private void buildPool(Element categoryElement, Map<String, QuestionCategoryPool> question_categories_pool)
	{
		try
		{
			String title = "New Untitled Pool";
			title = categoryElement.selectSingleNode("name").getText();

			List<Pool> pools = poolService.getPools(siteId);
			List<String> poolNames = getPoolNames(pools);

			boolean poolExists = checkTitleExists(title, poolNames);
			if (poolExists) return;

			List<Element> questions = categoryElement.selectNodes("questions/question");
			if (questions == null || questions.size() == 0) return;

			Pool newPool = poolService.newPool(siteId);
			if (newPool == null) return;
			newPool.setTitle(title);
			for (Element questionElement : questions)
			{
				String id = questionElement.attributeValue("id");
				String qtype = questionElement.selectSingleNode("qtype").getText();
				if ("random".equals(qtype))
					question_categories_pool.put(id, new QuestionCategoryPool(newPool.getId(), newPool.getTitle(), null, qtype));

				Question question = createNewQuestion(categoryElement, questionElement, newPool);
				if (question == null) continue;

				question_categories_pool.put(id, new QuestionCategoryPool(newPool.getId(), newPool.getTitle(), question.getId(), qtype));
			}
			poolService.savePool(newPool);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	/**
	 * Build quizzes and tests
	 * @param quiz_activity
	 * @param actionName
	 */
	private void buildTest(Element quiz_activity, Map<String, QuestionCategoryPool> question_categories_pool, HashMap<String, ModuleObjService> module_sections, String actionName, Date weekStartDate,
			Date weekEndDate, Date weekUntilDate, int number)
	{
		if (!"quiz".equals(actionName)) return;

		try
		{
			Map<String, String> contents = getContentsDatFile(getDataFile(quiz_activity, actionName), 1);
			if (contents.isEmpty() || (!contents.containsKey("root") && !contents.get("root").equals("quiz"))) return;

			String title = "New Quiz";
			title = ((Element) quiz_activity.selectSingleNode("title")).getText();
			Integer tries = null;
			String points = "10";

			Assessment assmt = createNewAssessment(title);
			if (assmt == null) return;

			assmt.setType(AssessmentType.test);
			Part part = assmt.getParts().addPart();

			// If grading info is missing, by default give 10 points to make assignment valid
			if (contents.containsKey("grade")) points = contents.get("grade");
			// newPool.setPointsEdit(Float.parseFloat(points));

			// /question_categories/question_category[2]/questions[1]/question[1] @id from questions.xml is equal to
			// /activity/quiz[1]/question_instances[1]/question_instance[1]/question[1] from quiz.xml
			String questions = contents.get("questions");
			String[] ids = StringUtil.split(questions, ",");
			Date openDate = null;
			Date dueDate = null;
			
			ArrayList<String> processIds = new ArrayList<String>();

			findAndCreateRandomParts(quiz_activity, question_categories_pool, ids, part, processIds);

			// add parts for non-random questions
			for (String id : processIds)
			{
				if (!question_categories_pool.containsKey(id)) continue;
				
				String questionPoints = getQuestionPoints(quiz_activity, id);
				
				QuestionCategoryPool pp = question_categories_pool.get(id);
				String questionId = pp.getMnemeQuestionId();
				Question question = questionService.getQuestion(questionId);

				if (pp.getQtype().equals("description") && (questionPoints == null || questionPoints.equals("0.0000000")))
				{
					buildMeleteSectionFromDescription(quiz_activity, "Description Task", question.getPresentation().getText(), module_sections);
					questionService.removeQuestion(question);
					continue;
				}
				
				QuestionPick questionPick = part.addPickDetail(question);
				if (questionPoints == null) questionPoints = "1";
				questionPick.setPoints(Float.parseFloat(questionPoints));
			}
			
			assmt.setShowModelAnswer(false);
			// If pool has one question then only assign points
			// if (newPool.getNumQuestions() <= 1) newPool.setPoints(new Float(points).floatValue());

			// questionsperpage
			if (contents.containsKey("questionsperpage") && "1".equals(contents.get("questionsperpage")))
				assmt.setQuestionGrouping(QuestionGrouping.question);

			if (contents.containsKey("attempts_number")) tries = new Integer(contents.get("attempts_number"));
			if (tries != null && tries.intValue() != 0) assmt.setTries(tries);

			// shuffle questions
			if (contents.containsKey("shufflequestions") && "1".equals(contents.get("shufflequestions"))) part.setRandomize(true);

			// password
			if (contents.containsKey("password") && contents.get("password") != null && contents.get("password").length() != 0)
			{
				assmt.getPassword().setPassword(contents.get("password"));
			}
			// time open close
			if (contents.containsKey("timeopen"))
			{
				String oDate = contents.get("timeopen");
				if (oDate != null && oDate.length() != 0 && !oDate.equals("0")) openDate = getDateTime(oDate);
			}

			if (contents.containsKey("timeclose"))
			{
				String dDate = contents.get("timeclose");
				if (dDate != null && dDate.length() != 0 && !dDate.equals("0")) dueDate = getDateTime(dDate);
			}

			if (openDate != null)
			{
				assmt.getDates().setOpenDate(openDate);
				if (weekStartDate != null && openDate.before(weekStartDate)) assmt.getDates().setOpenDate(weekStartDate);
				if (weekStartDate != null && openDate.after(weekStartDate))
				{
					if (weekEndDate != null && openDate.after(weekEndDate)) openDate = weekStartDate;
				}
			}
			else assmt.getDates().setOpenDate(weekStartDate);
			
			if (dueDate != null)
			{
				assmt.getDates().setDueDate(dueDate);
				if (weekStartDate != null && dueDate.before(weekStartDate)) assmt.getDates().setDueDate(weekEndDate);
				if (weekEndDate != null && dueDate.after(weekEndDate)) assmt.getDates().setDueDate(weekEndDate);
			}
			else assmt.getDates().setDueDate(weekEndDate);
			// from General section..it should stay open
			if (number == 0)
			{
				assmt.getDates().setDueDate(null);
				assmt.getDates().setAcceptUntilDate(weekUntilDate);
			}
			
			// time limit
			if (contents.containsKey("timelimit") && !"0".equals(contents.get("timelimit")))
			{
				assmt.setHasTimeLimit(true);
				String limitStr = contents.get("timelimit");
				Long limit = new Long(limitStr);
				limit = new Long(limit.longValue() * 1000);
				assmt.setTimeLimit(limit);
			}

			// TODO: upon release

			assmt.getGrading().setGradebookIntegration(Boolean.TRUE);
			if (assmt.getParts().getTotalPoints().floatValue() <= 0) assmt.setNeedsPoints(Boolean.FALSE);

			assessmentService.saveAssessment(assmt);
		}
		catch (Exception e)
		{
			// skip it do nothing
			e.printStackTrace();
		}
	}

	
	
	/**
	 * 
	 * @param a
	 * @return
	 */
	private String cleanText(String a)
	{
		String cleanContent = a;
 		cleanContent = cleanContent.replaceAll("\n", "<br/>");
	
		return cleanContent;
	}
	
	/**
	 * Create assessment if it doesn't exist in the site.
	 * 
	 * @param title
	 * @return
	 * @throws Exception
	 */
	private Assessment createNewAssessment(String title) throws Exception
	{
		boolean poolExists = false;
		boolean assmtExists = false;

		// get all pools in the from context
		List<Pool> pools = poolService.getPools(siteId);
		List<String> poolNames = getPoolNames(pools);

		List<Assessment> assessments = assessmentService.getContextAssessments(siteId, null, Boolean.FALSE);
		List<String> assmtNames = getAssessmentNames(assessments);

		poolExists = checkTitleExists(title, poolNames);
		assmtExists = checkTitleExists(title, assmtNames);
		if (poolExists) return null;
		if (assmtExists) return null;

		// create test object
		Assessment assmt = assessmentService.newAssessment(siteId);
		assmt.setTitle(title);
		return assmt;
	}
	
	/**
	 * Create Question based on the type.
	 * 
	 * @param quiz_activity
	 * @param questionElement
	 * @param pool
	 * @return
	 * @throws Exception
	 */
	private Question createNewQuestion(Element quiz_activity, Element questionElement, Pool pool) throws Exception
	{
		// read type
		String questionType = questionElement.selectSingleNode("qtype").getText();
		Question question = null;

		if ("multichoice".equals(questionType) || "numerical".equals(questionType) || "multianswer".equals(questionType) || "gapselect".equals(questionType) || "ddwtos".equals(questionType))
			question = createMCQuestion(questionElement, pool);
		else if ("essay".equals(questionType) || "shortanswer".equals(questionType) || "calculated".equals(questionType))
			question = createEssayQuestion(questionElement, null, EssaySubmissionType.inline, pool);
		else if ("truefalse".equals(questionType))
			question = createTFQuestion(questionElement, pool);
		else if ("description".equals(questionType))
			question = createTaskQuestion(questionElement, pool);
		else if ("match".equals(questionType)) question = createMatchQuestion(questionElement, pool);
//		else if ("gapselect".equals(questionType)) question = createFillInQuestion(questionElement, pool);

		return question;
	}

	/**
	 * Create Multichoice type question.
	 * 
	 * @param questionElement
	 * @param pool
	 * @return
	 * @throws Exception
	 */
	private Question createMCQuestion(Element questionElement, Pool pool) throws Exception
	{
		// create the question
		MultipleChoiceQuestion question = this.questionService.newMultipleChoiceQuestion(pool);

		List<String> choices = new ArrayList<String>();
		Set<Integer> correctAnswers = new HashSet<Integer>();
		int choiceNumber = 0;

		if (questionElement.selectSingleNode("questiontext") == null) return null;

		String text = questionElement.selectSingleNode("questiontext").getText();
		question.getPresentation().setText(getPresentationText(questionElement, text));
		String qtype = questionElement.selectSingleNode("qtype").getText();
		String plugin = "plugin_qtype_" + qtype + "_question/answers";
		Element answersList = (Element) questionElement.selectSingleNode(plugin);
		if (answersList == null) return null;

		for (Iterator<?> iter = answersList.elementIterator("answer"); iter.hasNext();)
		{
			Element ansInstance = (Element) iter.next();
			if (ansInstance.selectSingleNode("answertext") == null) continue;
			String choiceText = ansInstance.selectSingleNode("answertext").getText();
			choiceText = HtmlHelper.cleanAndAssureAnchorTarget(choiceText, true);

			choices.add(choiceText);

			if (ansInstance.selectSingleNode("fraction") == null) continue;
			String fractionText = ansInstance.selectSingleNode("fraction").getText();
			if (fractionText.equals("1.0000000"))
			{
				correctAnswers.add(new Integer(choiceNumber));
				if (ansInstance.selectSingleNode("feedback") != null
						&& ((Element) ansInstance.selectSingleNode("feedback")).getTextTrim().length() > 0)
					question.setFeedback(((Element) ansInstance.selectSingleNode("feedback")).getTextTrim());
			}
			choiceNumber++;
		}
		
		try
		{
			// correct answers are in the question text like [[1]] jumps over the [[4]]
			if (("gapselect").equals(qtype) || "ddwtos".equals(qtype))
			{
				Pattern p = Pattern.compile("\\[\\[(\\d){1}\\]\\]", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
				Matcher m = p.matcher(text);
				StringBuffer sb = new StringBuffer();
				while (m.find())
				{
					M_log.debug("groups found" + m.groupCount() + ", full " + m.group(0));
					correctAnswers.add(Integer.parseInt(m.group(1)) - 1);
					m.appendReplacement(sb, "_____");
				}
				m.appendTail(sb);
				text = sb.toString();
				question.getPresentation().setText(text);
			}
		}
		catch (Exception e)
		{
		}
		
		// randomize
		Element randomize = (Element) questionElement.selectSingleNode("plugin_qtype_multichoice_question/multichoice/shuffleanswers");
		if (randomize != null && "1".equals(randomize.getText()))
			question.setShuffleChoices(true);
		else
			question.setShuffleChoices(false);

		// answer choices
		question.setAnswerChoices(choices);

		// correct answer
		question.setCorrectAnswerSet(correctAnswers);

		// correctfeedback
		Element correctfeedback = (Element) questionElement.selectSingleNode("plugin_qtype_multichoice_question/multichoice/correctfeedback");
		if (correctfeedback != null && correctfeedback.getTextTrim().length() > 0) question.setFeedback(correctfeedback.getTextTrim());

		// single / multiple select
		if (correctAnswers.size() <= 1)
			question.setSingleCorrect(Boolean.TRUE);
		else
			question.setSingleCorrect(Boolean.FALSE);

		// general feedback
		Element generalfeedback = (Element) questionElement.selectSingleNode("generalfeedback");
		if (generalfeedback != null && generalfeedback.getTextTrim().length() != 0) question.setFeedback(generalfeedback.getTextTrim());

		// hints
		Element hints = (Element) questionElement.selectSingleNode("question_hints/question_hint/hint");
		if (hints != null && hints.getTextTrim().length() != 0) question.setHints(hints.getTextTrim());

		// survey
		question.setIsSurvey(false);

		// save
		question.getTypeSpecificQuestion().consolidate("");
		this.questionService.saveQuestion(question);
		return question;
	}

	/**
	 * Creates a task question
	 * @param questionElement
	 * @param pool
	 * @return
	 * @throws Exception
	 */
	private Question createTaskQuestion(Element questionElement, Pool pool) throws Exception
	{
		// create the question
		Question question = this.questionService.newTaskQuestion(pool);

		if (questionElement.selectSingleNode("questiontext") == null) return null;

		String text = questionElement.selectSingleNode("questiontext").getText();
		question.getPresentation().setText(getPresentationText(questionElement, text));

		// general feedback
		Element generalfeedback = (Element) questionElement.selectSingleNode("generalfeedback");
		if (generalfeedback != null && generalfeedback.getTextTrim().length() != 0) question.setFeedback(generalfeedback.getTextTrim());

		// hints
		Element hints = (Element) questionElement.selectSingleNode("question_hints/question_hint/hint");
		if (hints != null && hints.getTextTrim().length() != 0) question.setHints(hints.getTextTrim());

		// survey
		question.setIsSurvey(false);

		// save
		question.getTypeSpecificQuestion().consolidate("");
		this.questionService.saveQuestion(question);
		return question;
	}

	/**
	 * Create task question for offline assignments
	 * @param introText
	 * @param pool
	 * @return
	 * @throws Exception
	 */
	private Question createTaskQuestion(String introText, Pool pool) throws Exception
	{
		// create the question
		Question question = this.questionService.newTaskQuestion(pool);
		
		if (introText == null) return null;
		question.getPresentation().setText(introText);

		// survey
		question.setIsSurvey(false);

		// save
		question.getTypeSpecificQuestion().consolidate("");
		this.questionService.saveQuestion(question);
		return question;
	}
	
	/**
	 * Create True-False type question.
	 * @param questionElement
	 * @param pool
	 * @return
	 * @throws Exception
	 */
	private Question createTFQuestion(Element questionElement, Pool pool) throws Exception
	{
		// create the question
		TrueFalseQuestion question = this.questionService.newTrueFalseQuestion(pool);

		if (questionElement.selectSingleNode("questiontext") == null) return null;

		String text = questionElement.selectSingleNode("questiontext").getText();
		question.getPresentation().setText(getPresentationText(questionElement, text));

		Element answersList = (Element) questionElement.selectSingleNode("plugin_qtype_truefalse_question/answers");
		if (answersList == null) return null;

		boolean isTrue = false;

		for (Iterator<?> iter = answersList.elementIterator("answer"); iter.hasNext();)
		{
			Element ansInstance = (Element) iter.next();

			if (ansInstance.selectSingleNode("fraction") == null) continue;			
			String fractionText = ansInstance.selectSingleNode("fraction").getText();
			if (fractionText.equals("1.0000000")) isTrue = new Boolean(ansInstance.selectSingleNode("answertext").getText()).booleanValue();

			if (isTrue && ansInstance.selectSingleNode("feedback") != null
					&& ((Element) ansInstance.selectSingleNode("feedback")).getTextTrim().length() > 0)
				question.setFeedback(((Element) ansInstance.selectSingleNode("feedback")).getTextTrim());
		}

		// answer choices
		question.setCorrectAnswer(new Boolean(isTrue));

		// general feedback
		Element generalfeedback = (Element) questionElement.selectSingleNode("generalfeedback");
		if (generalfeedback != null && generalfeedback.getTextTrim().length() != 0) question.setFeedback(generalfeedback.getTextTrim());

		// hints
		Element hints = (Element) questionElement.selectSingleNode("question_hints/question_hint/hint");
		if (hints != null && hints.getTextTrim().length() != 0) question.setHints(hints.getTextTrim());

		// survey
		question.setIsSurvey(false);

		// save
		question.getTypeSpecificQuestion().consolidate("");
		this.questionService.saveQuestion(question);
		return question;
	}

	/**
	 * Create Essay type question for assignments, essay and short answers.
	 * 
	 * @param activityElement
	 * @param questionElement
	 * @param assignment_text
	 * @param submissionType
	 * @param pool
	 * @return
	 * @throws Exception
	 */
	private Question createEssayQuestion(Element questionElement, String assignment_text,
			EssayQuestion.EssaySubmissionType submissionType, Pool pool) throws Exception
	{
		// create the question
		EssayQuestion question = this.questionService.newEssayQuestion(pool);
		String text = "";
		if (questionElement != null && questionElement.selectSingleNode("questiontext") != null)
		{
			text = questionElement.selectSingleNode("questiontext").getText();

		}
		else if (assignment_text != null) text = assignment_text;

		question.getPresentation().setText(getPresentationText(questionElement, text));

		question.setSubmissionType(submissionType);
				
		if (questionElement != null)
		{
			// plugin_qtype_calculated_question[1]/answers[1]/answer[1]/answertext[1] for modal answer
			String qtype = questionElement.selectSingleNode("qtype").getText();
			String plugin = "plugin_qtype_" + qtype + "_question/answers";
			Element answersList = (Element) questionElement.selectSingleNode(plugin);
			if (answersList != null)
			{
				String answer = "";
				for (Iterator<?> iter = answersList.elementIterator("answer"); iter.hasNext();)
				{
					Element ansInstance = (Element) iter.next();
					if (ansInstance.selectSingleNode("answertext") == null) continue;
					answer = answer.concat(" " + ansInstance.selectSingleNode("answertext").getText());
					answer = HtmlHelper.cleanAndAssureAnchorTarget(answer, true);
				}
				question.setModelAnswer(answer);
			}
			// general feedback
			Element generalfeedback = (Element) questionElement.selectSingleNode("generalfeedback");
			if (generalfeedback != null && generalfeedback.getTextTrim().length() != 0) question.setFeedback(generalfeedback.getTextTrim());

			// hints
			Element hints = (Element) questionElement.selectSingleNode("question_hints/question_hint/hint");
			if (hints != null && hints.getTextTrim().length() != 0) question.setHints(hints.getTextTrim());
		}
		
		// survey
		question.setIsSurvey(false);

		// save
		question.getTypeSpecificQuestion().consolidate("");
		this.questionService.saveQuestion(question);
		return question;
	}

	/**
	 * Create Match type questions
	 * 
	 * @param questionElement
	 * @param pool
	 * @return
	 * @throws Exception
	 */
	private Question createMatchQuestion(Element questionElement, Pool pool) throws Exception
	{
		MatchQuestion question = questionService.newMatchQuestion(pool);

		if (questionElement.selectSingleNode("questiontext") == null) return null;

		String text = questionElement.selectSingleNode("questiontext").getText();
		question.getPresentation().setText(getPresentationText(questionElement, text));

		List<MatchChoice> matchChoices = new ArrayList<MatchChoice>();

		Element matchList = (Element) questionElement.selectSingleNode("plugin_qtype_match_question/matches");
		if (matchList == null) return null;

		for (Iterator<?> iter = matchList.elementIterator("match"); iter.hasNext();)
		{
			Element matchInstance = (Element) iter.next();

			String qText = ((Element) matchInstance.selectSingleNode("questiontext")).getTextTrim();
			String aText = ((Element) matchInstance.selectSingleNode("answertext")).getTextTrim();

			if (qText != null && qText.length() != 0 && aText != null && aText.length() != 0) matchChoices.add(new MatchChoice(qText, aText));

			if ((qText == null || qText.length() == 0) && aText != null && aText.length() != 0) question.setDistractor(aText);
		}

		if (matchChoices != null && matchChoices.size() > 0) question.setMatchPairs(matchChoices);

		// general feedback
		Element generalfeedback = (Element) questionElement.selectSingleNode("generalfeedback");
		if (generalfeedback != null && generalfeedback.getTextTrim().length() != 0) question.setFeedback(generalfeedback.getTextTrim());

		// hints
		Element hints = (Element) questionElement.selectSingleNode("question_hints/question_hint/hint");
		if (hints != null && hints.getTextTrim().length() != 0) question.setHints(hints.getTextTrim());

		// survey
		question.setIsSurvey(false);

		// save
		question.getTypeSpecificQuestion().consolidate("");
		this.questionService.saveQuestion(question);

		return question;
	}

	private Question createFillInQuestion(Element questionElement, Pool pool) throws Exception
	{
		FillBlanksQuestion question = questionService.newFillBlanksQuestion(pool);

		if (questionElement.selectSingleNode("questiontext") == null) return null;

		String text = questionElement.selectSingleNode("questiontext").getText();
		question.getPresentation().setText(getPresentationText(questionElement, text));

		StringBuffer correctAnswers = new StringBuffer("{");
		List<StringBuffer> buildAnswers = new ArrayList<StringBuffer>();
		
		Element answersList = (Element) questionElement.selectSingleNode("plugin_qtype_gapselect_question/answers");
		if (answersList == null) return null;

		for (Iterator<?> iter = answersList.elementIterator("answer"); iter.hasNext();)
		{
			Element ansInstance = (Element) iter.next();
			if (ansInstance.selectSingleNode("answertext") == null) continue;
			String choiceText = ansInstance.selectSingleNode("answertext").getText();
			choiceText = HtmlHelper.cleanAndAssureAnchorTarget(choiceText, true);
				
			if (ansInstance.selectSingleNode("feedback") == null) continue;
			String feedbackText = ansInstance.selectSingleNode("feedback").getText();
			int number = Integer.parseInt(feedbackText);
			if (buildAnswers.size() >= number)
				buildAnswers.set(number - 1, buildAnswers.get(number - 1).append(choiceText +"|"));
			else buildAnswers.add(number - 1, new StringBuffer("{" + choiceText +"|"));
				
			if (ansInstance.selectSingleNode("fraction") == null) continue;
			String fractionText = ansInstance.selectSingleNode("fraction").getText();
			
			if (fractionText.equals("1.0000000"))
			{
				correctAnswers.append(choiceText +"|");				
			}
		}
		if (buildAnswers.size() > 0)
		{
			for (int i = 0; i < buildAnswers.size(); i++)
			{
				StringBuffer answer = buildAnswers.get(i);
				answer = answer.replace(answer.length() - 1, answer.length(), "}");
				text = text.replace("[[" + (i+1)+ "]]", answer);
			}							
		}
		question.getPresentation().setText(text);	
		question.setText(text);	
		// case sensitive
		question.setCaseSensitive(Boolean.FALSE);

		// mutually exclusive
		question.setAnyOrder(Boolean.FALSE);
		
		// general feedback
		Element generalfeedback = (Element) questionElement.selectSingleNode("generalfeedback");
		if (generalfeedback != null && generalfeedback.getTextTrim().length() != 0) question.setFeedback(generalfeedback.getTextTrim());

		// hints
		Element hints = (Element) questionElement.selectSingleNode("question_hints/question_hint/hint");
		if (hints != null && hints.getTextTrim().length() != 0) question.setHints(hints.getTextTrim());

		// survey
		question.setIsSurvey(false);

		// save
		question.getTypeSpecificQuestion().consolidate("");
		this.questionService.saveQuestion(question);

		return question;
	}

	/**
	 * Checks and separates random questions (randompool) and non-random questions (processIds) of a quiz. If yes, it creates the random part.
	 * 
	 * @param quiz_activity
	 * @param question_categories_pool
	 * @param ids
	 * @param part
	 * @param processIds
	 *        list of non-random question ids
	 */
	private void findAndCreateRandomParts(Element quiz_activity, Map<String, QuestionCategoryPool> question_categories_pool, String[] ids, Part part,
			List<String> processIds)
	{
		// random pool is keyed with pool_id and value is a list of question ids that way the count of list gives us the number of questions to be drawn from a pool
		Map<String, List<String>> randomPools = new HashMap<String, List<String>>();
		// seperate random questions and non-random

		for (String id : ids)
		{
			if (!question_categories_pool.containsKey(id)) continue;
			QuestionCategoryPool qp = question_categories_pool.get(id);
			String randomPoolId = qp.getPoolId();

			if ("random".equals(qp.getQtype()))
			{
				if (randomPools.containsKey(randomPoolId))
				{
					List<String> a = randomPools.get(randomPoolId);
					a.add(id);
					randomPools.put(randomPoolId, a);
				}
				else
				{
					ArrayList<String> processRandomIds = new ArrayList<String>();
					processRandomIds.add(id);
					randomPools.put(randomPoolId, processRandomIds);
				}
			}
			else
				processIds.add(id);
		}

		// add parts for random questions. If quiz contains random questions, find out if from different pools and add accordingly
		if (randomPools.size() == 0) return;

		Set<String> keys = randomPools.keySet();
		for (String key : keys)
		{
			List<String> count = randomPools.get(key);
			PoolDraw poolDraw = part.addDrawDetail(poolService.getPool(key), count.size());
			String questionPoints = getQuestionPoints(quiz_activity, count.get(0));
			if (questionPoints == null) questionPoints = "1";
			poolDraw.setPoints(Float.parseFloat(questionPoints));
		}
	}
	
	/**
	 * Get the embedded and attachments file names and store in embedfiles list n attach and their display names
	 * 
	 * @param instance
	 * @param embedFiles
	 * @param attach
	 * @param attachDisplayName
	 * @throws Exception
	 */
	private void getEmbedAndAttachmentFileNames(Element instance, ArrayList<Element> embedFiles, ArrayList<String> attach,
			ArrayList<String> attachDisplayName) throws Exception
	{
		List<String> ids = getFileReferences(getDataFile(instance, "inforef"));
		// match the id to files/file id attribute from files.xml
		// read filename, mimetype, contenthash and files/fb/contenthash is the file
		for (String id : ids)
		{
			Element file = getFileElement(id);
			if (file.selectSingleNode("filesize") != null && !file.selectSingleNode("filesize").getText().equals("0"))
			{
				embedFiles.add(file);
				if (attach == null || attachDisplayName == null) continue;
				String check_name = file.selectSingleNode("contenthash").getText();
				File f = new File(unzipBackUpLocation + File.separator + "files");
				String displayName = file.selectSingleNode("filename").getText();

				String found = findFileinDirectory(f, check_name);
				if (found == null) continue;
				attach.add(found);
				attachDisplayName.add(displayName);
			}
		}
	}

	/**
	 * Get the corresponding file element object from files.xml based on id
	 * 
	 * @param id
	 * @return
	 */
	private Element getFileElement(String id)
	{
		try
		{
			byte[] b = readDatafromFile("files.xml");
			if (b == null || b.length == 0) return null;

			XMLHelper.getSaxReader().setEncoding("UTF-8");
			Document contentsDOM = XMLHelper.getSaxReader().read(new ByteArrayInputStream(b));
			XPath xpath = contentsDOM.createXPath("/files/file[@id = '" + id + "']");
			return (Element) xpath.selectSingleNode(contentsDOM);

		}
		catch (Exception e)
		{
			return null;
		}
	}

	/**
	 * Get the corresponding file element object from files.xml
	 * 
	 * @param name
	 *        file name
	 * @return
	 */
	private Element getFileElementfromName(String name)
	{
		try
		{
			byte[] b = readDatafromFile("files.xml");
			if (b == null || b.length == 0) return null;

			XMLHelper.getSaxReader().setEncoding("UTF-8");
			Document contentsDOM = XMLHelper.getSaxReader().read(new ByteArrayInputStream(b));
			XPath xpath = contentsDOM.createXPath("/files/file[filename = '" + name + "']");
			return (Element) xpath.selectSingleNode(contentsDOM);
		}
		catch (Exception e)
		{
			return null;
		}
	}

	/**
	 *  Reads the contents of module.xml file. It reads data like section number, group id etc
	 * @param instance
	 * @return
	 * @throws Exception
	 */
	private Map<String, String> getContentFromModuleXml(Element instance) throws Exception
	{
		Map<String, String> moduleContents = getContentsDatFile(getDataFile(instance, "module"), 0);
		return moduleContents;	
	}
	
	/**
	 * Get the contents of file specified in directory element
	 * 
	 * @param element
	 * @param actionName
	 * @return
	 */
	private byte[] getDataFile(Element element, String actionName)
	{
		byte[] b = null;
		try
		{
			if (element == null) return b;
			Element directoryElement = (Element) element.selectSingleNode("directory");
			if (directoryElement.getTextTrim() != null)
			{
				String textFile = directoryElement.getTextTrim() + File.separator + actionName + ".xml";
				b = readDatafromFile(textFile);
			}
		}
		catch (Exception e)
		{
			b = null;
		}
		return b;
	}

	/**
	 * Read the contents of xml file and put in the map
	 * 
	 * @param datFileContents
	 * @return Map keyed by tag name and value is element text
	 * @throws Exception
	 */
	private Map<String, String> getContentsDatFile(byte[] datFileContents, int level) throws Exception
	{
		Map<String, String> contents = new HashMap<String, String>();
		if (datFileContents == null || datFileContents.length == 0) return contents;

		XMLHelper.getSaxReader().setEncoding("UTF-8");
		
		Document contentsDOM = XMLHelper.getSaxReader().read(new ByteArrayInputStream(datFileContents));
		Element root = contentsDOM.getRootElement();
		Element mainElement = root;

		if (level > 0)
		{
			mainElement = (Element) root.elements().get(0);
			if (!"activity".equals(root.getName())) return contents;
		}

		contents.put("root", mainElement.getName());
		
		for (Iterator<?> iter = mainElement.elementIterator(); iter.hasNext();)
		{
			Element element = (Element) iter.next();
			contents.put(element.getName(), element.getText());
		}
		return contents;
	}

	/**
	 * Gather the embedded and attachment files ids
	 * 
	 * @param datFileContents
	 *        contents from inforef.xml
	 * @return
	 * @throws Exception
	 */
	private List<String> getFileReferences(byte[] datFileContents) throws Exception
	{
		List<String> ids = new ArrayList<String>();
		if (datFileContents == null || datFileContents.length == 0) return ids;

		XMLHelper.getSaxReader().setEncoding("UTF-8");
		Document contentsDOM = XMLHelper.getSaxReader().read(new ByteArrayInputStream(datFileContents));
		Element root = contentsDOM.getRootElement();
		Element mainElement = root.element("fileref");

		if (mainElement == null) return ids;
		for (Iterator<?> iter = mainElement.elementIterator("file"); iter.hasNext();)
		{
			Element element = (Element) iter.next();
			ids.add(element.selectSingleNode("id").getText());
		}
		return ids;
	}

	/**
	 * Get the presentation text. clean the text for any question.
	 * 
	 * @param activityElement
	 *        element activity to read file path to look for embedded media
	 * @param text
	 *        text to clean
	 * @return
	 * @throws Exception
	 */
	private String getPresentationText(Element activityElement, String text) throws Exception
	{
		// read inforef/fileref/file/id from inforref.xml for attachments and embedded media
		ArrayList<String> fetchAttachList = new ArrayList<String>();
		ArrayList<String> fetchAttachFileNamesList = new ArrayList<String>();
		ArrayList<Element> embedFiles = new ArrayList<Element>();

		getEmbedAndAttachmentFileNames(activityElement, embedFiles, fetchAttachList, fetchAttachFileNamesList);

		if (text != null && text.length() != 0)
		{
			text = findAndUploadEmbeddedMedia(text, "", embedFiles, null, null, "Mneme");
			text = fixDoubleQuotes(text);
			text = HtmlHelper.cleanAndAssureAnchorTarget(text, true);	
		}
		text = cleanText(text);
		return text;
	}

	private List<Element> getQuestionCategoryElement()
	{
		try
		{
			byte[] b = readDatafromFile("questions.xml");
			if (b == null || b.length == 0) return null;

			XMLHelper.getSaxReader().setEncoding("UTF-8");
			Document contentsDOM = XMLHelper.getSaxReader().read(new ByteArrayInputStream(b));
			Element root = contentsDOM.getRootElement();

			// /question_categories/question_category
			XPath xpath = contentsDOM.createXPath("/question_categories/question_category");
			return xpath.selectNodes(contentsDOM);
		}
		catch (Exception e)
		{
			return null;
		}
	}
	
	/**
	 * Get the corresponding Question element from questions.xml file
	 * 
	 * @param id
	 *        question id read from quiz.xml
	 * @return
	 */
	private Element getQuestionElement(String id)
	{
		try
		{
			byte[] b = readDatafromFile("questions.xml");
			if (b == null || b.length == 0) return null;

			XMLHelper.getSaxReader().setEncoding("UTF-8");
			Document contentsDOM = XMLHelper.getSaxReader().read(new ByteArrayInputStream(b));
			Element root = contentsDOM.getRootElement();

			// /question_categories/question_category[2]/questions[1]/question[1]
			XPath xpath = contentsDOM.createXPath("/question_categories/question_category/questions/question[@id = '" + id + "']");
			return (Element) xpath.selectSingleNode(contentsDOM);

		}
		catch (Exception e)
		{
			return null;
		}

	}

	private List<Element> getDiscussionElements(Element element)
	{
		try
		{
			byte[] b = getDataFile(element, "forum");
			if (b == null || b.length == 0) return null;

			XMLHelper.getSaxReader().setEncoding("UTF-8");
			Document contentsDOM = XMLHelper.getSaxReader().read(new ByteArrayInputStream(b));

			XPath xpath = contentsDOM.createXPath("/activity/forum/discussions/discussion/posts/post[parent = '0']");
			return xpath.selectNodes(contentsDOM);		
		}
		catch (Exception e)
		{
			return null;
		}
	}
	
	/**
	 * Read the points from grade element
	 * 
	 * @param element
	 *        activity element to get the directory path
	 * @param id
	 *        question id
	 * @return
	 */
	private String getQuestionPoints(Element element, String id)
	{
		try
		{
			byte[] b = getDataFile(element, "quiz");
			if (b == null || b.length == 0) return null;

			XMLHelper.getSaxReader().setEncoding("UTF-8");
			Document contentsDOM = XMLHelper.getSaxReader().read(new ByteArrayInputStream(b));
			Element root = contentsDOM.getRootElement();

			XPath xpath = contentsDOM.createXPath("/activity/quiz/question_instances/question_instance[question = '" + id + "']");
			Element questionInstance = (Element) xpath.selectSingleNode(contentsDOM);
			if (questionInstance == null) return null;
			return questionInstance.selectSingleNode("grade").getText();
		}
		catch (Exception e)
		{
			return null;
		}
	}

	/**
	 * Calculate the week start date and end date. We have number of weeks and the start date, so calculate week's end date and other week's dates and course end date.
	 * 
	 * @return List of calculatedDates object with dates for a week.
	 */
	private List<CalculatedDates> getWeekDates()
	{
		ArrayList<CalculatedDates> weekDates = new ArrayList<CalculatedDates>();
		try
		{
			byte[] b = readDatafromFile("course/course.xml");
			if (b == null || b.length == 0) return null;

			XMLHelper.getSaxReader().setEncoding("UTF-8");
			Document contentsDOM = XMLHelper.getSaxReader().read(new ByteArrayInputStream(b));
			if (contentsDOM == null) return null;

			Element root = contentsDOM.getRootElement();
			if (root == null) return null;
// 			some courses have format as topics			
//			String format = root.selectSingleNode("format").getText();
//			if (!("weeks").equalsIgnoreCase(format)) return null;

			// start date of course
			String startDate = root.selectSingleNode("startdate").getText();
			Date sDate = getDateTime(startDate);			
			GregorianCalendar gc1 = new GregorianCalendar();
			gc1.setTime(sDate);

			// no. of weeks
			String countStr = root.selectSingleNode("numsections").getText();
			if (countStr == null || countStr.length() == 0) return null;
			int count = Integer.parseInt(countStr);

			// end of course date
			GregorianCalendar gcUntill = new GregorianCalendar();
			gcUntill.setTime(sDate);
			gcUntill.add(java.util.Calendar.DATE, (count * 7));
			Date allowUntill = gcUntill.getTime();

			for (int i = 0; i < count; i++)
			{
				if (i == 0)
					gc1.add(java.util.Calendar.DATE, 1);
				else
					gc1.add(java.util.Calendar.DATE, 7);

				GregorianCalendar gcDue = new GregorianCalendar();
				gcDue.setTime(gc1.getTime());
				gcDue.add(java.util.Calendar.DATE, 6);

				CalculatedDates cd = new CalculatedDates(gc1.getTime(), gcDue.getTime(), allowUntill);
				weekDates.add(cd);
			}
		}
		catch (Exception e)
		{
			return null;
		}
		return weekDates;
	}
	
	/**
	 * Bring in extra/unreferred files in Resources tool
	 */
	protected void transferExtraCourseFiles()
	{
		String collectionId = "/group/" + siteId + "/";

		try
		{
			byte[] b = readDatafromFile("files.xml");
			if (b == null || b.length == 0) return;

			XMLHelper.getSaxReader().setEncoding("UTF-8");
			Document contentsDOM = XMLHelper.getSaxReader().read(new ByteArrayInputStream(b));
			Element root = contentsDOM.getRootElement();

			for (Iterator<?> iter = root.elementIterator(); iter.hasNext();)
			{
				Element a = (Element) iter.next();
				String displayName = a.selectSingleNode("filename").getText();
				String fileName = a.selectSingleNode("contenthash").getText();
				String fileSize = a.selectSingleNode("filesize").getText();
				if (fileSize == null || fileSize.equals("0")) continue;

				File f = new File(unzipBackUpLocation + File.separator + "files");
				fileName = findFileinDirectory(f, fileName);

				// add file if not in mnemedocs, meletedocs or in group
				if (!(importedCourseFiles.contains(fileName) || importedCourseFiles.contains(displayName)))
				{
					buildExtraResourceItem( fileName, displayName, collectionId);
				}
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	/**
	 * abstract method implemented for moodle2.0
	 */
	protected String checkAllResourceFileTagReferenceTransferred(List<Element> embedFiles, String subFolder, String s, String title)
	{
		return s;
	}

	/**
	 * abstract method implemented for moodle2.0
	 */
	protected String[] getEmbeddedReferencePhysicalLocation(String embeddedSrc, String subFolder, List<Element> embedFiles,
			List<Element> embedContentFiles, String tool)
	{
		embeddedSrc = embeddedSrc.replace("$@FILEPHP@$", "");
		embeddedSrc = embeddedSrc.replace("$@SLASH@$", "/");
		embeddedSrc = embeddedSrc.replace("../", "");
		String name = embeddedSrc;

		if (embeddedSrc.indexOf("@@PLUGINFILE@@/") != -1)
		{
			String find = embeddedSrc.replace("@@PLUGINFILE@@/", "");
			Element file = getFileElementfromName(find);
			if (file != null)
			{
				File f = new File(unzipBackUpLocation + File.separator + "files");
				String check_name = file.selectSingleNode("contenthash").getText();
				String found = findFileinDirectory(f, check_name);
				if (found != null)
				{
					embeddedSrc = found;
					name = file.selectSingleNode("filename").getText();
				}
			}
		}

		String[] returnStrings = new String[2];
		// physical location
		returnStrings[0] = embeddedSrc;
		// display name
		returnStrings[1] = name;
		return returnStrings;
	}

}
