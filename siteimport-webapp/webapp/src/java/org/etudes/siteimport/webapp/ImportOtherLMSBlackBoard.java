/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/siteimport/trunk/siteimport-webapp/webapp/src/java/org/etudes/siteimport/webapp/ImportOtherLMSBlackBoard.java $
 * $Id: ImportOtherLMSBlackBoard.java 11236 2015-07-13 21:13:29Z mallikamt $
 ***********************************************************************************
 *
 * Copyright (c) 2012, 2013, 2015 Etudes, Inc.
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
import java.io.FileInputStream;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.XPath;
import org.etudes.api.app.jforum.Forum;
import org.etudes.api.app.jforum.Grade;
import org.etudes.api.app.jforum.Topic;
import org.etudes.api.app.melete.ModuleObjService;
import org.etudes.api.app.melete.SectionObjService;
import org.etudes.mneme.api.Assessment;
import org.etudes.mneme.api.AssessmentPermissionException;
import org.etudes.mneme.api.AssessmentType;
import org.etudes.mneme.api.EssayQuestion;
import org.etudes.mneme.api.FillBlanksQuestion;
import org.etudes.mneme.api.MatchQuestion;
import org.etudes.mneme.api.MatchQuestion.MatchChoice;
import org.etudes.mneme.api.MultipleChoiceQuestion;
import org.etudes.mneme.api.Part;
import org.etudes.mneme.api.PartDetail;
import org.etudes.mneme.api.Pool;
import org.etudes.mneme.api.PoolDraw;
import org.etudes.mneme.api.Question;
import org.etudes.mneme.api.QuestionGrouping;
import org.etudes.mneme.api.QuestionPick;
import org.etudes.mneme.api.QuestionService;
import org.etudes.mneme.api.TrueFalseQuestion;
import org.etudes.util.HtmlHelper;
import org.sakaiproject.content.api.ContentResource;
import org.sakaiproject.content.cover.ContentHostingService;
import org.sakaiproject.content.cover.ContentTypeImageService;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.entity.api.ResourcePropertiesEdit;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.time.api.Time;
import org.sakaiproject.util.FormattedText;
import org.sakaiproject.util.Validator;

public class ImportOtherLMSBlackBoard extends BaseImportOtherLMS
{
	private class ChoiceStatus
	{
		private String choice = null;
		private Boolean status = null;
		
		public ChoiceStatus(String choice, Boolean status)
		{
			this.choice = choice;
			this.status = status;
		}
		
		public String getChoice()
		{
			return this.choice;
		}
		
		public Boolean getStatus()
		{
			return this.status;
		}
		
		public void setStatus(Boolean status)
		{
			this.status = status;
		}
	}
	
	private class MatchQuestionOptions
	{
		private List<String> options = null;
		private String questionText = null;

		public MatchQuestionOptions(String questionText, List<String> options)
		{
			this.questionText = questionText;
			this.options = options;
		}

		public List<String> getOptions()
		{
			return this.options;
		}

		public String getQuestionText()
		{
			return this.questionText;
		}
	}
	
	private class OutcomeValues
	{
		Float points;
		Integer tries;
		Date dueDate;
		String contentId;

		public void setPoints(Float points)
		{
			this.points = points;
		}

		public void setTries(Integer tries)
		{
			this.tries = tries;
		}

		public void setDueDate(Date dueDate)
		{
			this.dueDate = dueDate;
		}
		
		public void setContentId(String contentId)
		{
			this.contentId = contentId;
		}

		public Float getPoints()
		{
			return points;
		}

		public Integer getTries()
		{
			return tries;
		}

		public Date getDueDate()
		{
			return dueDate;
		}
		
		public String getContentId()
		{
			return contentId;
		}

	}
	
	public static final Map<String, String> orValueMap = new HashMap<String, String>()
	{
		{
			put("true_false.true", "True");
			put("true_false.false", "False");
			put("yes_no.true", "Yes");
			put("yes_no.false", "No");
			put("agree_disagree.true", "Agree");
			put("agree_disagree.false", "Disagree");
			put("right_wrong.true", "Right");
			put("right_wrong.false", "Wrong");
		}
	};

	private Map<String, String> refPoolMap = new LinkedHashMap<String, String>();
	private Map<String, OutcomeValues> assnIdEleMap = new LinkedHashMap<String, OutcomeValues>();
	private Map<String, OutcomeValues> discussionGradeMap = new LinkedHashMap<String, OutcomeValues>();
	private Map<String, String> testRefMap = new LinkedHashMap<String, String>();
	private Map<String, String> categoryTitleMap = new LinkedHashMap<String, String>();
	private Map<String, String> questionTitleMap = new LinkedHashMap<String, String>();
	private Map<String, String> bbqidQuestionMap = new LinkedHashMap<String, String>();
	private List<String> nonEssayPools = new ArrayList();
	
	/**
	 * Constructor
	 * 
	 * @param backUpDoc
	 * @param unzipBackUpLocation
	 * @param siteId
	 * @param userId
	 */
	public ImportOtherLMSBlackBoard(Document backUpDoc, String unzipBackUpLocation, String siteId, String userId)
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
		String success_message = "You have successfully imported material from the BlackBoard export file.";

		XPath xpathResources = backUpDoc.createXPath("/manifest/resources/resource[@type='course/x-bb-coursetoc']");
	
		List<Element> eleResources = xpathResources.selectNodes(backUpDoc);
		if (eleResources == null) return success_message;
		initializeServices();
		
		buildAnnouncements();
		transferGradebook();
		buildDiscussions();
		transferPools();
		transferTests();
        transferAssns();
        createTestsForPools();
        removeEmptyPools();

		for (Iterator<?> iter = eleResources.iterator(); iter.hasNext();)
		{
			try
			{
				Element toolResourceElement = (Element)iter.next();
				M_log.debug("eleResources PROCESSED:" + toolResourceElement.attributeValue("title"));
				byte[] datFileContents = readBBFile(toolResourceElement.attributeValue("file"), toolResourceElement.attributeValue("base"));
				Map<String, String> tocContents = processBBCourseTOCDatFile(datFileContents);
				if (tocContents.size() == 0) continue;
				
	//			if (tocContents.containsKey("ISENABLED") && tocContents.get("ISENABLED").equalsIgnoreCase("false")) continue;
					
				if (tocContents.containsKey("INTERNALHANDLE") && tocContents.get("INTERNALHANDLE").equals("content"))
				{
					XPath xpathItem = backUpDoc.createXPath("/manifest/organizations/organization/item[@identifierref='"
							+ toolResourceElement.attributeValue("identifier") + "']");
					Element organizationItemElement = (Element) xpathItem.selectSingleNode(backUpDoc);
					String toolItemTitle = organizationItemElement.selectSingleNode("title").getText();
					// get /manifest/organizations/organization/item@TITLE=lessons/item element generally has title as --TOP--
					if (organizationItemElement == null) continue;
					
					// check first child item if its --TOP-- then skip it
					Element checkTitle = organizationItemElement.element("item");					
						
					if (tocContents.containsKey("LABEL") && tocContents.get("LABEL").contains("Syllabus"))
					{
						if (checkTitle != null && checkTitle.selectSingleNode("title") != null
								&& checkTitle.selectSingleNode("title").getText().equals("--TOP--"))
							organizationItemElement = organizationItemElement.element("item");
						if (organizationItemElement != null)
						buildSyllabusItems(organizationItemElement);						
					}
					else if (tocContents.containsKey("LABEL") && tocContents.get("LABEL").contains("Resource"))
					{
						if (checkTitle != null && checkTitle.selectSingleNode("title") != null
								&& checkTitle.selectSingleNode("title").getText().equals("--TOP--"))
							organizationItemElement = organizationItemElement.element("item");
						if (organizationItemElement != null)
							buildResourceItems(organizationItemElement, null);	
					}
					else
					{
						organizationItemElement = organizationItemElement.element("item");
						ModuleObjService module = buildMeleteModule(organizationItemElement, toolItemTitle);
						// now top items are module and nested item are sections
						for (Iterator<?> iterMod = organizationItemElement.elementIterator("item"); iterMod.hasNext();)
						{
							Element organizationModuleElement = (Element) iterMod.next();
							if (checkItemHasLessonDocuments(organizationModuleElement))
							{
								Element moduleParent = organizationModuleElement.getParent();
								String parentTitle = moduleParent.selectSingleNode("title").getText();
								ModuleObjService otherModule = buildMeleteModule(organizationModuleElement, parentTitle);
								if (otherModule != null) buildMeleteSectionItems(organizationModuleElement, otherModule, false, null);
							}
							else
							{
								buildMeleteSectionItem(organizationModuleElement, module);
							}
						}
						// refresh and check for invalid
						module = moduleService.getModule(module.getModuleId());
						if (module.getSections() == null || module.getSections().size() == 0)
						{
							List<ModuleObjService> invalidModules = new ArrayList<ModuleObjService>();
							invalidModules.add(module);
							moduleService.deleteModules(invalidModules, siteId, userId);
						}
					} // else melete
				}
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}

		return success_message;
	}	
	
	/**
	 * 
	 * Build all Announcements. Reads resource of type resource/x-bb-announcement and parses the file and creates announcements
	 */
	private void buildAnnouncements()
	{
		XPath xpathResources = backUpDoc.createXPath("/manifest/resources/resource[@type='resource/x-bb-announcement']");

		List<Element> announcementResources = xpathResources.selectNodes(backUpDoc);
		if (announcementResources == null) return;

		for (Iterator<?> iter = announcementResources.iterator(); iter.hasNext();)
		{
			try
			{
				Element resourceElement = (Element) iter.next();
				if (resourceElement == null) continue;

				byte[] datFileContents = readBBFile(resourceElement.attributeValue("file"), resourceElement.attributeValue("base"));
				Map<String, String> contents = processBBAnnouncementDatFile(datFileContents);
				if (contents == null) return;

				String subject = "Untitled Announcement";
				String body = null;
				Date releaseDate = null;

				if (contents.get("TITLE") != null) subject = contents.get("TITLE");
				if (contents.get("DESCRIPTION_TEXT") != null)
				{
					body = contents.get("DESCRIPTION_TEXT");
					body = fixAnchorTitle(body);
				}
				if (contents.get("RELEASEDATE") != null) releaseDate = getDateFromString(contents.get("RELEASEDATE"));
				buildAnnouncement(subject, body, releaseDate, resourceElement.attributeValue("base"), resourceElement.selectNodes("file"));
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Checks if the string is empty or contains only the &#8203; code after the html stripped out
	 * @param str String to check
	 * @return true if the string is empty or contains only the &#8203; code after the html stripped out, false otherwise
	 */
	private boolean checkEmptyCode(String str)
	{
		String value;
		if (str == null || str.trim().length() == 0) return true;
		value = HtmlHelper.stripBadEncodingCharacters(FormattedText.convertFormattedTextToPlaintext(str));
		value = value.replace("\n", " ");
		value = FormattedText.decodeNumericCharacterReferences(value);
		value = Validator.escapeHtml(value);
		if (value.trim().length() == 0) return true;
		if (value.equals("&#8203;")) return true;
		return false;
	}
	
	
	private Question buildCommonElements(Element itemEle, Question question)
	{
		String qtext;
		String gfeed;
		if (itemEle != null)
		{
			boolean qtextEmptyFlag = false;
			if (itemEle.selectSingleNode("presentation/flow/flow[@class='QUESTION_BLOCK']") != null)
			{
				qtext = processPresentation((Element)(itemEle.selectSingleNode("presentation/flow/flow[@class='QUESTION_BLOCK']")));
				if (qtext != null && qtext.length() != 0) 
				{
					if (checkEmptyCode(qtext)) question.getPresentation().setText("Answer the following.");
					else question.getPresentation().setText(qtext);
				}
				else qtextEmptyFlag = true;
			}
			if (qtextEmptyFlag)
			{
				String name = getAttributeValue(itemEle, "title");
				if (name != null && name.length() != 0)
				{
					question.getPresentation().setText(name);
				}
			}
			
			if (itemEle.selectSingleNode("itemfeedback[@ident='correct']") != null)
			{
				Element itemFeedEle = (Element) itemEle.selectSingleNode("itemfeedback[@ident='correct']");
				if (itemFeedEle != null)
				{
					if (itemFeedEle.selectSingleNode("flow_mat[@class='Block']") != null)
					{
						gfeed = processPresentation((Element)(itemFeedEle.selectSingleNode("flow_mat[@class='Block']")));
						if (gfeed != null && gfeed.length() != 0) question.setFeedback(gfeed);
					}
				}
			}
		}
		return question;
	}
	
	/**
	 * Look for discussions and create them
	 */
	private void buildDiscussions()
	{
		XPath xpathResources = backUpDoc.createXPath("/manifest/resources/resource[@type='resource/x-bb-discussionboard']");

		List<Element> discussionResources = xpathResources.selectNodes(backUpDoc);
		if (discussionResources == null) return;

		org.etudes.api.app.jforum.User postedBy = jForumUserService.getBySakaiUserId(userId);
		// category
		int mainCategoryId = buildDiscussionCategory();
		if (mainCategoryId == 0) return;
				
		List<Forum> allForums = jForumForumService.getCategoryForums(mainCategoryId);
		for (Iterator<?> iter = discussionResources.iterator(); iter.hasNext();)
		{
			try
			{
				Element resourceElement = (Element) iter.next();
				if (resourceElement == null) continue;

				byte[] datFileContents = readBBFile(resourceElement.attributeValue("file"), resourceElement.attributeValue("base"));
				Map<String, String> contents = processBBDiscussionDatFile(datFileContents);
				if (contents == null) continue;

				int forumId = buildDiscussionForum(mainCategoryId, contents, allForums);
				if (forumId == 0) continue;

				// check for message tag to create topics
				List<Topic> allDiscussions = jForumPostService.getForumTopics(forumId);

				Document contentsDOM = XMLHelper.getSaxReader().read(new ByteArrayInputStream(datFileContents));
				Element root = contentsDOM.getRootElement();

				List<Element> messageList = root.selectNodes("MESSAGETHREADS/MSG");

				if (messageList == null || messageList.size() == 0) continue;

				Forum workForum = jForumForumService.getForum(forumId);
				Date openDate = null;
				Date dueDate = null;
				
				//check if it has dates then move it for all topics
			    if (workForum.getGradeType() == Grade.GradeType.TOPIC.getType())
			    {			    	
			    	if (workForum.getAccessDates() != null && workForum.getAccessDates().getOpenDate() != null)
			    		openDate = workForum.getAccessDates().getOpenDate();
			    	if (workForum.getAccessDates() != null && workForum.getAccessDates().getDueDate() != null)
			    		dueDate = workForum.getAccessDates().getDueDate();
			    	
			    	workForum.getAccessDates().setDueDate(null);
			    	workForum.getAccessDates().setOpenDate(null);
			    	workForum.setModifiedBySakaiUserId(userId);
			    	jForumForumService.modifyForum(workForum);
			    }
			    
				for (Iterator<Element> iterMsg = messageList.iterator(); iterMsg.hasNext();)
				{
					Element e = iterMsg.next();
					String gradePoints = null;
					int minPosts = 0;
					// There is no way to find topics created by author only

					Element msgTitle = (Element) e.selectSingleNode("TITLE");
					List<Element> embedFilesFromContentDat = e.selectNodes("FILELIST");

					// FORUM/MESSAGETHREADS[1]/MSG[1]/LINKREFID[1] for grades
					// linkrefid value == gradebook dat file
					// /GRADEBOOK/OUTCOMEDEFINITIONS[1]/OUTCOMEDEFINITION[7]/EXTERNALREF[1]@value
					Element msgGrade = (Element) e.selectSingleNode("LINKREFID");
					if (msgGrade != null)
					{
						String gradeHandle = msgGrade.attributeValue("value");
						if (gradeHandle != null && gradeHandle.length() > 0)
						{
							OutcomeValues gradebookObject = discussionGradeMap.get(gradeHandle);
							if (gradebookObject != null)
							{
								gradePoints = (gradebookObject.getPoints() != null) ? gradebookObject.getPoints().toString() : null;
								minPosts = (gradebookObject.getTries() != null) ? gradebookObject.getTries().intValue() : 1;
							}
						}
					}
					buildDiscussionTopics(msgTitle.attributeValue("value"), e.selectSingleNode("MESSAGETEXT/TEXT").getText(), "Normal",
							resourceElement.attributeValue("base"), resourceElement.selectNodes("file"), embedFilesFromContentDat, gradePoints, openDate,
							dueDate, minPosts, forumId, postedBy, allDiscussions);
				}
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Builds JForum Forums based on properties.
	 * @param mainCategoryId
	 *   Id of the category "Main"
	 * @param contents
	 *   Map containing the dat file contents
	 * @param allForums
	 * 	 All existing forums under the category
	 * @return
	 */
	private int buildDiscussionForum(int mainCategoryId, Map<String, String> contents, List<Forum> allForums) throws Exception
	{
		String subject = null;
		String body = null;
		String forumType = null;
		String gradeType = "nograde";
		String gradePoints = null;
		Date openDate = null;
		Date dueDate = null;
		int minPosts = 0;

		if (contents.get("TITLE") != null) subject = contents.get("TITLE");
		if (subject == null || subject.length() == 0) return 0;

		if (contents.get("DESCRIPTION_TEXT") != null)
		{
			body = contents.get("DESCRIPTION_TEXT");
			body = fixAnchorTitle(body);
		}
		if (body == null || body.length() == 0) body = subject;
			
		if (contents.get("ALLOWNEWTHREADS") != null)
		{
			forumType = contents.get("ALLOWNEWTHREADS");
			forumType = (forumType.equals("true")) ? "NORMAL" : "REPLY_ONLY";
		}
		
		if (contents.get("ALLOWFORUMGRADING") != null && contents.get("ALLOWFORUMGRADING").equals("true"))
		{
			// gradehandle value == gradebook dat file /GRADEBOOK/OUTCOMEDEFINITIONS[1]/OUTCOMEDEFINITION[7]/EXTERNALREF[1]@value
			String gradeHandle = contents.get("FORUMGRADEHANDLE");
			if (gradeHandle != null && gradeHandle.length() > 0)
			{
				gradeType = "gradeForum";
				OutcomeValues gradebookObject = discussionGradeMap.get(gradeHandle);
				if (gradebookObject != null && gradebookObject.getPoints() != null)
				{				
					gradePoints = (gradebookObject.getPoints() != null) ? gradebookObject.getPoints().toString() : null;			
					minPosts = (gradebookObject.getTries() != null) ? gradebookObject.getTries() : 0;
				}
			}
		}
		else if (contents.get("ALLOWTHREADGRADING") != null && contents.get("ALLOWTHREADGRADING").equals("true"))
		{
			gradeType = "gradeTopics";
		}
		
		if (contents.get("OPENDATE") != null) openDate = getDateFromString(contents.get("OPENDATE"));
		if (contents.get("ENDDATE") != null) dueDate = getDateFromString(contents.get("ENDDATE"));

		// build forums
		return buildDiscussionForums(subject, body, forumType, gradeType, gradePoints, openDate, dueDate, minPosts, mainCategoryId, userId, allForums);
	}
		
	private Question buildEssayQuestion(Element itemEle, EssayQuestion essayQuestion)
	{
		if (M_log.isDebugEnabled()) M_log.debug("Entering buildEssayQuestion...");

		if (itemEle != null)
		{
			essayQuestion = (EssayQuestion) buildCommonElements(itemEle, (Question) essayQuestion);
            if (itemEle.selectSingleNode("itemfeedback[@ident='solution']") != null)
			{
				Element itSolEle = (Element) itemEle.selectSingleNode("itemfeedback[@ident='solution']");
				String ansText = getElementValue(itSolEle, "solution/solutionmaterial/flow_mat/material/mat_extension/mat_formattedtext");
				if (ansText != null && ansText.length() != 0)
				{
					essayQuestion = buildEssayQuestion(essayQuestion, itSolEle, ansText);
				}
			}

		}
		if (M_log.isDebugEnabled()) M_log.debug("Exiting buildEssayQuestion...");
		return essayQuestion;

	}
	
	/**
	 * Returns the long answer of the element's varequal node if there is atleast one value that is more than 3 words, null otherwise
	 * @param ele Element to check
	 * @return true if string has more than three words, false if not
	 */
	private String checkLongAnswer(Element itemEle)
	{
		List<Element> rcElements = itemEle.selectNodes("resprocessing/respcondition/conditionvar/varequal");
		boolean longAnswerExists = false;
		StringBuffer longAnsBuf = new StringBuffer();
		
		if (rcElements != null && rcElements.size() > 0)
		{
			longAnsBuf.append("Answer:");
			longAnsBuf.append("<BR>");
			for (Iterator<?> rcIter = rcElements.iterator(); rcIter.hasNext();)
			{
				Element rcEle = (Element) rcIter.next();
				if (rcEle == null) continue;
				String str = (String) getElementValue(rcEle);
				if (str != null)
				{
					String input = str.trim(); 
					if (input.length() > 0)
					{
						longAnsBuf.append(input);
						longAnsBuf.append("<BR>");
						int count = input.split("\\s+").length;
						if (count > 3)
						{
							longAnswerExists = true;
						}
					}
				}
			}
		}
	    if (longAnswerExists) return longAnsBuf.toString();
	    return null;
	}
	
	
	private Question buildFillBlanksQuestion(Element itemEle, FillBlanksQuestion fbQuestion)
	{
		StringBuffer fbStrBuf = new StringBuffer();
		Pattern uPa = Pattern.compile("_+");
		if (M_log.isDebugEnabled()) M_log.debug("Entering buildFillBlanksQuestion...");

		if (itemEle != null)
		{
			fbQuestion = (FillBlanksQuestion) buildCommonElements(itemEle, (Question) fbQuestion);

			if (fbQuestion.getResponseTextual().equals("true"))
			{
				List<Element> rcElements = itemEle.selectNodes("resprocessing/respcondition/conditionvar/varequal");

				if (rcElements != null && rcElements.size() > 0)
				{
					fbStrBuf.append("{");
					for (Iterator<?> rcIter = rcElements.iterator(); rcIter.hasNext();)
					{
						Element rcEle = (Element) rcIter.next();
						if (rcEle == null) continue;
						fbStrBuf.append((String) getElementValue(rcEle));
						fbStrBuf.append("|");
					}
					if (fbStrBuf.length() > 0 && fbStrBuf.toString().endsWith("|"))
					{
						fbStrBuf.deleteCharAt(fbStrBuf.length() - 1);
					}
					fbStrBuf.append("}");
				}
			}
			else
			{
				fbStrBuf.append("{");
				if (getElementValue(itemEle, "resprocessing/respcondition/conditionvar/vargte") != null)
				{
					fbStrBuf.append(getElementValue(itemEle, "resprocessing/respcondition/conditionvar/vargte"));
					fbStrBuf.append("|");
					if (getElementValue(itemEle, "resprocessing/respcondition/conditionvar/varlte") != null)
					{
						fbStrBuf.append(getElementValue(itemEle, "resprocessing/respcondition/conditionvar/varlte"));
					}
				}
				else
				{
					if (getElementValue(itemEle, "resprocessing/respcondition/conditionvar/varequal") != null)
					{
						fbStrBuf.append(getElementValue(itemEle, "resprocessing/respcondition/conditionvar/varequal"));
					}
				}
				fbStrBuf.append("}");
			}

			if (itemEle.selectSingleNode("presentation/flow/flow[@class='QUESTION_BLOCK']") != null)
			{
				String qtext = processPresentation((Element)(itemEle.selectSingleNode("presentation/flow/flow[@class='QUESTION_BLOCK']")));
				if (qtext != null && qtext.length() != 0)
				{
					Matcher m = uPa.matcher(qtext);
					if (qtext.contains("_"))
					{
						while (m.find())
						{
							if (fbStrBuf.toString() != null)
							{
								qtext = qtext.replaceAll("_+", fbStrBuf.toString());
							}
						}
					}
					else
					{
						qtext = qtext.concat(fbStrBuf.toString());
					}
					if (!qtext.contains("{") && !qtext.contains("}")) 
					{
						fbQuestion.setText(qtext+"{}");
						fbQuestion.setIsSurvey(new Boolean(true));
					}
					else fbQuestion.setText(qtext);
				}
			}

		}

		else
		{
			M_log.warn("Question element doesn't exist");
		}
		if (M_log.isDebugEnabled()) M_log.debug("Exiting buildFillBlanksQuestion...");
		return fbQuestion;
	}

	private Question buildFillMultipleBlanksQuestion(Element itemEle, FillBlanksQuestion fbQuestion)
	{
		Map<String, String> varValMap = new LinkedHashMap<String, String>();
		Element veEle = null;
		StringBuffer fbStrBuf = null;
		Pattern tokenPattern = Pattern.compile("\\[([^}]+)\\]");
		if (M_log.isDebugEnabled()) M_log.debug("Entering buildFillMultipleBlanksQuestion...");

		if (itemEle != null)
		{
			fbQuestion = (FillBlanksQuestion) buildCommonElements(itemEle, (Question) fbQuestion);

			List<Element> orElements = itemEle.selectNodes("resprocessing/respcondition/conditionvar/and/or");
			if (orElements != null && orElements.size() > 0)
			{
				for (Iterator<?> orIter = orElements.iterator(); orIter.hasNext();)
				{
					Element orEle = (Element) orIter.next();
					List<Element> veElements = orEle.selectNodes("varequal");
					fbStrBuf = new StringBuffer();
					if (veElements != null && veElements.size() > 0)
					{
						fbStrBuf.append("{");
						for (Iterator<?> veIter = veElements.iterator(); veIter.hasNext();)
						{
							veEle = (Element) veIter.next();
							if (veEle == null) continue;
							fbStrBuf.append((String) getElementValue(veEle));
							fbStrBuf.append("|");
						}
						if (fbStrBuf.length() > 0 && fbStrBuf.toString().endsWith("|"))
						{
							fbStrBuf.deleteCharAt(fbStrBuf.length() - 1);
						}
						fbStrBuf.append("}");
						varValMap.put(getAttributeValue(veEle, "respident"), fbStrBuf.toString());
					}
				}
			}
			if (itemEle.selectSingleNode("presentation/flow/flow[@class='QUESTION_BLOCK']") != null)
			{
				String qtext = processPresentation((Element)(itemEle.selectSingleNode("presentation/flow/flow[@class='QUESTION_BLOCK']")));
				if (qtext != null && qtext.length() != 0)
				{
					for (String key : varValMap.keySet())
					{
						qtext = qtext.replaceAll("\\[" + key + "\\]", varValMap.get(key));
					}
					if (!qtext.contains("{") && !qtext.contains("}")) 
					{
						fbQuestion.setText(qtext+"{}");
						fbQuestion.setIsSurvey(new Boolean(true));
					}
					else fbQuestion.setText(qtext);
				}
			}
		}
		else
		{
			M_log.warn("Question element doesn't exist");
		}
		if (M_log.isDebugEnabled()) M_log.debug("Exiting buildFillMultipleBlanksQuestion...");
		return fbQuestion;
	}

	private Question buildMatchQuestion(Element itemEle, MatchQuestion mQuestion)
	{
		Map<String, MatchQuestionOptions> questOptMap = new LinkedHashMap<String, MatchQuestionOptions>();
		List<String> options = new ArrayList<String>();
		List<ChoiceStatus> choices = new ArrayList<ChoiceStatus>();
		List<MatchChoice> matchChoices = new ArrayList<MatchChoice>();

		String qId;
		String qText;
		String qSrcId;
		String choiceId;

		if (M_log.isDebugEnabled()) M_log.debug("Entering buildMatchQuestion...");

		if (itemEle != null)
		{
			mQuestion = (MatchQuestion) buildCommonElements(itemEle, (Question) mQuestion);

			// First process Match Question block a create a map of <Question id>,<Question text,Options list>
			if (itemEle.selectSingleNode("presentation/flow[@class='Block']/flow[@class='RESPONSE_BLOCK']") != null)
			{
				Element flowRespEle = (Element) itemEle.selectSingleNode("presentation/flow[@class='Block']/flow[@class='RESPONSE_BLOCK']");
				List flowBlkList = flowRespEle.selectNodes("flow[@class='Block']");
				if (flowBlkList != null)
				{
					for (Iterator flItr = flowBlkList.iterator(); flItr.hasNext();)
					{
						Element flowBlkEle = (Element) flItr.next();
						qId = getAttributeValue(flowBlkEle, "response_lid", "ident");
						if (qId != null && qId.trim().length() != 0)
						{
							qText = processPresentation(flowBlkEle);
							//qText = getElementValue(flowBlkEle, "flow/material/mat_extension/mat_formattedtext");
							if (flowBlkEle.selectSingleNode("response_lid/render_choice/flow_label") != null)
							{
								Element flowLabEle = (Element) flowBlkEle.selectSingleNode("response_lid/render_choice/flow_label");
								List repLabList = flowLabEle.selectNodes("response_label");
								if (repLabList != null)
								{
									options = new ArrayList<String>();
									for (Iterator rlItr = repLabList.iterator(); rlItr.hasNext();)
									{
										Element rlEle = (Element) rlItr.next();
										options.add((String) getAttributeValue(rlEle, "ident"));
									}
								}
							}
							questOptMap.put(qId, new MatchQuestionOptions(qText, options));
							options = null;
						}
					}
				}
			}
			// Create a list of Choices (text)
			if (itemEle.selectSingleNode("presentation/flow[@class='Block']/flow[@class='RIGHT_MATCH_BLOCK']") != null)
			{
				Element rtMatEle = (Element) itemEle.selectSingleNode("presentation/flow[@class='Block']/flow[@class='RIGHT_MATCH_BLOCK']");
				List rmBlkList = rtMatEle.selectNodes("flow[@class='Block']");
				if (rmBlkList != null)
				{
					for (Iterator rmItr = rmBlkList.iterator(); rmItr.hasNext();)
					{
						Element rmEle = (Element) rmItr.next();
						String choiceVal = processPresentation(rmEle);
						choices.add(new ChoiceStatus(choiceVal, new Boolean(false)));
					}
				}
			}

			// Process the matches
			if (itemEle.selectSingleNode("resprocessing") != null)
			{
				Element resEle = (Element) itemEle.selectSingleNode("resprocessing");
				List rcList = resEle.selectNodes("respcondition");
				if (rcList != null)
				{
					for (Iterator rcItr = rcList.iterator(); rcItr.hasNext();)
					{
						Element rcEle = (Element) rcItr.next();
						if (rcEle.selectSingleNode("conditionvar/varequal") != null)
						{
							Element vqEle = (Element) rcEle.selectSingleNode("conditionvar/varequal");
							qSrcId = getAttributeValue(vqEle, "respident");
							choiceId = getElementValue(vqEle);
							if (questOptMap.get(qSrcId) != null)
							{
								MatchQuestionOptions mqOpt = (MatchQuestionOptions) questOptMap.get(qSrcId);
								if (mqOpt.getOptions() != null & mqOpt.getOptions().size() > 0)
								{
									if ((mqOpt.getOptions()).indexOf(choiceId) != -1)
									{
										if (choices != null && choices.size() > 0)
										{
											matchChoices.add(new MatchChoice(mqOpt.getQuestionText(), choices.get(
													(mqOpt.getOptions()).indexOf(choiceId)).getChoice()));
											choices.get((mqOpt.getOptions()).indexOf(choiceId)).setStatus(new Boolean(true));
										}
									}

								}
							}
						}

					}
					for (Iterator chItr = choices.iterator(); chItr.hasNext();)
					{
						ChoiceStatus cs = (ChoiceStatus) chItr.next();
						if (cs.getStatus().booleanValue() == false)
						{
							mQuestion.setDistractor(cs.getChoice());
							break;
						}
					}
					if (matchChoices != null && matchChoices.size() > 0) 
					{
						mQuestion.setMatchPairs(matchChoices);
					}
					else
					{
						mQuestion.setIsSurvey(new Boolean(true));
					}
				}
			}
		}
		if (M_log.isDebugEnabled()) M_log.debug("Exiting buildMatchQuestion...");
		return mQuestion;
	}

	/**
	 * Builds melete module reading properties from the top <item> tag. If the item has content then it gets added as first section titled "overview".
	 * 
	 * @param organizationItemElement
	 *        <organization><item> tag
	 * @param parentItemTitle
	 *        title of the parent item tag
	 * @return Melete module object.
	 */
	private ModuleObjService buildMeleteModule(Element organizationItemElement, String parentItemTitle)
	{
		ModuleObjService module = null;

		// get the existing modules from the site
		List<ModuleObjService> existingModules = moduleService.getModules(siteId);
		//check what items it contains and to create the module or not
	//	if (!checkItemHasLessonDocuments(organizationItemElement)) return null;
		
		Date startDate = null;
		Date endDate = null;
		try
		{
			Element resourceElement = getResourceElement(organizationItemElement.attributeValue("identifierref"));

			// Create parent item title module module if (resourceElement == null)
			if (resourceElement == null)
			{
				module = findOrAddModule(parentItemTitle, 0, null, startDate, endDate, null, existingModules, null, null, null);
				return module;
			}

			List<Element> embedFiles = resourceElement.selectNodes("file");
			String resourceType = resourceElement.attributeValue("type");
			if (resourceType.equalsIgnoreCase("resource/x-bb-document"))
			{
				byte[] datFileContents = readBBFile(resourceElement.attributeValue("file"), resourceElement.attributeValue("base"));
				Map<String, String> contents = processBBDocumentDatFile(datFileContents);
				
				// <FILES> inside the content dat file
				List<Element> filesAttached = getFilesBBDocumentDatFile(datFileContents);
				
				// create parent item title module if (contents.size() == 0)
				if (contents.size() == 0)
				{
					module = findOrAddModule(parentItemTitle, 0, null, startDate, endDate, null, existingModules, resourceElement.attributeValue("base"), embedFiles,
							filesAttached);
				}
				else
				{
					if (contents.containsKey("CONTENTHANDLER")
							&& (contents.get("CONTENTHANDLER").equals("resource/x-bb-assignment")
								|| contents.get("CONTENTHANDLER").equals("resource/x-bb-asmt-test-link")
								|| contents.get("CONTENTHANDLER").equals("resource/x-bb-forumlink")
								||  contents.get("CONTENTHANDLER").equals("resource/x-bb-courselink"))) return null;
					
					String title = "Untitled Module";
					if (contents.containsKey("TITLE")) title = contents.get("TITLE");
					if (title.equals("--TOP--")) title = parentItemTitle;
					startDate = getDateFromString(contents.get("STARTDATE"));
					endDate = getDateFromString(contents.get("ENDDATE"));
					module = findOrAddModule(title, 0, contents.get("TEXT"), startDate, endDate, null, existingModules, resourceElement.attributeValue("base"), embedFiles,
							filesAttached);
				}
			}
		}
		catch (Exception e)
		{
			module = findOrAddModule(parentItemTitle, 0, null, startDate, endDate, null, existingModules, null, null, null);
		}
		return module;
	}

	/**
	 * Builds a section.
	 * 
	 * @param organizationItemElement
	 * @param module
	 */
	private void buildMeleteSectionItem(Element organizationItemElement, ModuleObjService module)
	{
		try
		{
			Element resourceElement = getResourceElement(organizationItemElement.attributeValue("identifierref"));
			if (resourceElement == null) return;
			byte[] datFileContents = readBBFile(resourceElement.attributeValue("file"), resourceElement.attributeValue("base"));

			String title = "untitled Section";
			String section_content = "";
			if (organizationItemElement.selectSingleNode("title") != null) title = organizationItemElement.selectSingleNode("title").getText();

			if (sectionExists(title, module)) return;

			Map<String, String> contents = processBBDocumentDatFile(datFileContents);
			if (contents.size() == 0) return;
			section_content = getMeleteSectionContentFromDatFile(resourceElement, datFileContents, contents);

			// save section
			if (section_content != null && section_content.length() > 0)
			{
				SectionObjService section = buildSection(title, module);

				// save section
				section = buildTypeEditorSection(section, section_content, module, resourceElement.attributeValue("base"),
						resourceElement.attributeValue("title"), resourceElement.selectNodes("file"), getFilesBBDocumentDatFile(datFileContents));
			}
		}
		catch (Exception e)
		{
			// skip it do nothing
			e.printStackTrace();
		}
	}
	
	/**
	 * Builds section. When item structure is item >> item where top item is a folder and has one item then consider it as one unit and build a section.
	 * @param organizationItemElement
	 * @param module
	 */
	private void buildMeleteSectionItems(Element organizationItemElement, ModuleObjService module, boolean makeSubSection, SectionObjService parentSection)
	{
		for (Iterator<?> iter = organizationItemElement.elementIterator("item"); iter.hasNext();)
		{			
			try
			{
				Element item = (Element) iter.next();
				Element resourceElement = getResourceElement(item.attributeValue("identifierref"));
				if (resourceElement == null) continue;
				byte[] datFileContents = readBBFile(resourceElement.attributeValue("file"), resourceElement.attributeValue("base"));
				
				String title = "untitled Section";
				String section_content = "";
				if (item.selectSingleNode("title") != null) title = item.selectSingleNode("title").getText();
				
				if (sectionExists(title, module)) continue;
				
				//When item structure is item >> item where top item is a folder and has one item then combine them as one
				if (item.elements("item") != null && item.elements("item").size() == 1)
				{
					section_content = combineMeleteSectionItems(item, section_content);
					Element leafElement = item.element("item");
					resourceElement = getResourceElement(leafElement.attributeValue("identifierref"));
					datFileContents = readBBFile(resourceElement.attributeValue("file"), resourceElement.attributeValue("base"));
				}
				
				// if item has many children then fetch them all. Create a top section with item and then subsections for its children.
				// This was found useful for Scott and Richard courses.
				if (item.elements("item") != null && item.elements("item").size() > 1)
				{
					SectionObjService nestedSection = buildSection(title, module);
					buildMeleteSectionItems(item, module, true,nestedSection);
					continue;
				//	section_content = combineMeleteSectionItems(item, section_content);	
				}
											
				// if item is a leaf node 
				if (item.elements("item")== null || item.elements("item").size() == 0)
				{
					Map<String, String> contents = processBBDocumentDatFile(datFileContents);
					if (contents.size() == 0) continue;					
					section_content = getMeleteSectionContentFromDatFile(resourceElement, datFileContents, contents);
				}				
				
				//save section
				if (section_content != null && section_content.length() > 0 )
				{
					SectionObjService section = buildSection(title, module);

					// save section
					section = buildTypeEditorSection(section, section_content, module, resourceElement.attributeValue("base"),
							resourceElement.attributeValue("title"), resourceElement.selectNodes("file"), getFilesBBDocumentDatFile(datFileContents));

					// create subsections
					 if (makeSubSection && parentSection != null)
					 moduleService.createSubSection(module, parentSection.getSectionId().toString(), section.getSectionId().toString());
				}				
			}
			catch (Exception e)
			{
				//skip it do nothing
				e.printStackTrace();
			}
			
		}		
	}

	/**
	 * Check if module should be created for this item tag. If all child nodes are of type link to assessment or to discussion and has no document then return false.
	 * 
	 * @param moduleItemElement
	 *        item Element
	 * @return
	 */
	private boolean checkItemHasLessonDocuments(Element moduleItemElement)
	{
		try
		{
			Element moduleResourceElement = getResourceElement(moduleItemElement.attributeValue("identifierref"));
			String moduleResourceType = moduleResourceElement.attributeValue("type");
			if (moduleResourceType.equalsIgnoreCase("resource/x-bb-document"))
			{
				byte[] moduleDatFileContents = readBBFile(moduleResourceElement.attributeValue("file"), moduleResourceElement.attributeValue("base"));
				Map<String, String> moduleContents = processBBDocumentDatFile(moduleDatFileContents);
				if (moduleContents.containsKey("CONTENTHANDLER")
						&& (moduleContents.get("CONTENTHANDLER").equals("resource/x-bb-lesson") 
						|| moduleContents.get("CONTENTHANDLER").equals("resource/x-bb-folder")))
				{
					List<Element> children = moduleItemElement.elements("item");
					if (children == null || children.size() == 0) return true;

					for (Element e : children)
					{
						Element resourceElement = getResourceElement(e.attributeValue("identifierref"));
						if (resourceElement == null) continue;
						String resourceType = resourceElement.attributeValue("type");
						if (!resourceType.equalsIgnoreCase("resource/x-bb-document")) continue;
						byte[] datFileContents = readBBFile(resourceElement.attributeValue("file"), resourceElement.attributeValue("base"));
						Map<String, String> contents = processBBDocumentDatFile(datFileContents);
						if (contents.containsKey("CONTENTHANDLER")
								&& (contents.get("CONTENTHANDLER").equals("resource/x-bb-assignment")
										|| contents.get("CONTENTHANDLER").equals("resource/x-bb-asmt-test-link")
										|| contents.get("CONTENTHANDLER").equals("resource/x-bb-forumlink") 
										|| contents.get("CONTENTHANDLER").equals("resource/x-bb-courselink")))
							continue;
						else if (contents.containsKey("CONTENTHANDLER") && contents.get("CONTENTHANDLER").equals("resource/x-bb-folder"))
						{
							boolean chk = checkItemHasLessonDocuments(e);
							if (chk) return true;
						}
						else
							return true;
					}					
				}				
			}
		}
		catch (Exception e)
		{
			return false;
		}
		return false;
	}
	
	/**
	 * Combine the content of nested item tags as one section content. This is mostly to handle folder structure.
	 * 
	 * @param parentItemElement
	 * @param wholeItemtext
	 * 
	 * @return
	 */	
	private String combineMeleteSectionItems(Element parentItemElement, String wholeItemtext) throws Exception
	{
		for (Iterator<?> iter = parentItemElement.elementIterator("item"); iter.hasNext();)
		{
			Element item = (Element) iter.next();

			if (item.elements("item") != null && item.elements("item").size() == 1)
			{
				item = item.element("item");
			}

			Element resourceElement = getResourceElement(item.attributeValue("identifierref"));
			if (resourceElement == null) continue;

			String resourceType = resourceElement.attributeValue("type");
			if (!resourceType.equalsIgnoreCase("resource/x-bb-document")) continue;

			byte[] datFileContents = readBBFile(resourceElement.attributeValue("file"), resourceElement.attributeValue("base"));
			Map<String, String> contents = processBBDocumentDatFile(datFileContents);
			if (contents.size() == 0) continue;

			String section_content = getMeleteSectionContentFromDatFile(resourceElement, datFileContents, contents);
			String title = "Untitled Section";
			if (contents.containsKey("TITLE")) title = contents.get("TITLE");

			if (!section_content.equals(""))
			{
				section_content = ("</hr><h4>").concat(title).concat("</h4>").concat(section_content);
				wholeItemtext = wholeItemtext.concat(section_content);
			}

			if (item.elements("item").size() > 1)
			{
				wholeItemtext = combineMeleteSectionItems(item, wholeItemtext);
			}
		}
		return wholeItemtext;
	}
	
	/**
	 * Read and translate the content.
	 * 
	 * @param resourceElement
	 * @param datFileContents
	 * @param contents
	 * @return
	 * @throws Exception
	 */
	private String getMeleteSectionContentFromDatFile(Element resourceElement, byte[] datFileContents, Map<String, String> contents) throws Exception
	{
		// <FILE> of resources tag
		List<Element> embedFiles = resourceElement.selectNodes("file");

		// <FILES> inside the content dat file
		List<Element> filesAttached = getFilesBBDocumentDatFile(datFileContents);

		String title = "Untitled Section";
		String linkUrl = null;				
		String section_content = "";

		if (contents.containsKey("CONTENTHANDLER")
				&& (contents.get("CONTENTHANDLER").equals("resource/x-bb-assignment")
						|| contents.get("CONTENTHANDLER").equals("resource/x-bb-asmt-test-link")
						|| contents.get("CONTENTHANDLER").equals("resource/x-bb-forumlink") 
						|| contents.get("CONTENTHANDLER").equals("resource/x-bb-courselink"))) return section_content;

		if (contents.containsKey("TITLE")) title = contents.get("TITLE");
		
		if (contents.containsKey("TEXT"))
			{
			section_content = contents.get("TEXT");
			section_content = fixAnchorTitle(section_content);
			}

		// external links
		if (contents.containsKey("CONTENTHANDLER") && contents.get("CONTENTHANDLER").equals("resource/x-bb-externallink")
				&& contents.containsKey("URL"))
		{
			linkUrl = contents.get("URL");
			if (linkUrl != null && !section_content.contains(linkUrl))
				section_content = buildTypeLinkUploadResource(linkUrl, linkUrl, section_content, resourceElement.attributeValue("base"), embedFiles);
		}

		// sometimes even <content><files> can be embedded file reference. if they are not then bring them as <a> tags
		if (filesAttached.size() != 0)
		{
			for (Element f : filesAttached)
			{
				String attach = findFileNamefromBBDocumentFilesTag(f, resourceElement.attributeValue("base"));
				if (attach == null) continue;
				String attachName = attach;
				byte[] content_data = readDatafromFile(attach);
				
				String attachType = null;
				if (f.selectSingleNode("FILEACTION") != null)
				{
					attachType = ((Element) f.selectSingleNode("FILEACTION")).attributeValue("value");
					if (f.selectSingleNode("NAME") != null) attachName = f.selectSingleNode("NAME").getText();
				}
				if ((attachType == null || "EMBED".equalsIgnoreCase(attachType)) && f.selectSingleNode("LINKNAME") != null) attachName = ((Element) f.selectSingleNode("LINKNAME")).attributeValue("value");
								
				String res_mime_type = attach.substring(attach.lastIndexOf(".") + 1);
				res_mime_type = ContentTypeImageService.getContentType(res_mime_type);
				
				// for upload html files which are in storage. affects richard's course
				if (("text/html").equals(res_mime_type) && content_data != null && contents.containsKey("CONTENTHANDLER") && contents.get("CONTENTHANDLER").equals("resource/x-bb-file") && ("EMBED").equals(attachType))
				{					
					section_content = new String(content_data);
					section_content = section_content.replace("﻿", "");
					if (section_content.indexOf("<frameset ") != -1)
						section_content = embedFramesets(section_content, resourceElement.attributeValue("base"));
					section_content = findAndUploadEmbeddedMedia(section_content, resourceElement.attributeValue("base"), embedFiles, null, null, "Melete");
					section_content = HtmlHelper.cleanAndAssureAnchorTarget(section_content, true);				
				}
				else
				if (f.selectSingleNode("NAME") == null || !section_content.contains(f.selectSingleNode("NAME").getText()))
					section_content = buildTypeLinkUploadResource(attach, attachName, section_content, resourceElement.attributeValue("base"), embedFiles);
			}
		}		
		return section_content;
	}

	/**
	 * Create Multiple choice question from res.dat file with Pool as root element
	 * 
	 * @param itemEle
	 * @param mcQuestion
	 * @return
	 */
	private Question buildPoolMCQuestion(Element itemEle, MultipleChoiceQuestion mcQuestion)
	{
		if (M_log.isDebugEnabled()) M_log.debug("Entering buildMCQuestion...");
		if (itemEle == null) return mcQuestion;

		// Setting this to empty string to avoid null check when comparing
		String correctAnsId = "";
		List<String> choiceList = new ArrayList<String>();
		Set<Integer> correctAnswers = new HashSet<Integer>();
		int choiceNumber = 0;

		try
		{
			Map<String, String> contents = processBBPoolQuestionsElement(itemEle);

			if (contents.containsKey("BODY_TEXT")) mcQuestion.getPresentation().setText(contents.get("BODY_TEXT"));

			if (contents.containsKey("Correct_Answer_id")) correctAnsId = contents.get("Correct_Answer_id");
			Set<String> keys = contents.keySet();

			String sz = contents.get("count");

			// if answer has positions
			boolean hasPositions = ("true".equalsIgnoreCase(contents.get("hasPosition"))) ? true : false;
			if (sz != null && hasPositions)
			{
				int size = Integer.parseInt(sz);
				choiceList = new ArrayList<String>(size);
				for (int i = 0; i < size; i++)
					choiceList.add("");
			}

			for (String key : keys)
			{
				if (!key.startsWith("Choices_")) continue;
				String id = key.substring(8);
		
				String position = contents.get("Position_" + id);
				String answerText = contents.get(key);

				if (position != null && hasPositions)
				{
					choiceList.set(Integer.parseInt(position) - 1, answerText);
					if (key.equalsIgnoreCase(correctAnsId)) correctAnswers.add(new Integer(position)-1);
				}
				else
				{
					choiceList.add(answerText);
					if (key.equalsIgnoreCase(correctAnsId)) correctAnswers.add(new Integer(choiceNumber));
				}
				choiceNumber++;
			}

			//feedback
			if (contents.containsKey("feedback")) mcQuestion.setFeedback(contents.get("feedback"));
			
			// answer choices
			mcQuestion.setAnswerChoices(choiceList);

			// correct answer
			mcQuestion.setCorrectAnswerSet(correctAnswers);

			// single / multiple select
			if (correctAnswers.size() <= 1)
				mcQuestion.setSingleCorrect(Boolean.TRUE);
			else
				mcQuestion.setSingleCorrect(Boolean.FALSE);
		}
		catch (Exception e)
		{
			M_log.debug(e.getMessage());
		}
		if (M_log.isDebugEnabled()) M_log.debug("Exiting buildMCQuestion...");
		return mcQuestion;
	}
	
	/**
	 * 
	 * @param itemEle
	 * @param mcQuestion
	 * @return
	 */
	private Question buildMultipleChoiceQuestion(Element itemEle, MultipleChoiceQuestion mcQuestion)
	{
		if (M_log.isDebugEnabled()) M_log.debug("Entering buildMultipleChoiceQuestion...");
 		if (itemEle != null)
		{
			// Setting this to empty string to avoid null check when comparing
			String correctAnsIdent = "";
			List<String> correctAnsIdentList = new ArrayList();
			mcQuestion = (MultipleChoiceQuestion) buildCommonElements(itemEle, (Question) mcQuestion);
			if (mcQuestion.getIsSurvey().booleanValue() == false)
			{
				if (mcQuestion.getSingleCorrect())
				{
					if (itemEle.selectSingleNode("resprocessing/respcondition[@title='correct']") != null)
					{
						Element rcEle = (Element) itemEle.selectSingleNode("resprocessing/respcondition[@title='correct']");
						correctAnsIdent = getElementValue(rcEle, "conditionvar/varequal");
					}
				}
				else
				{
					if (itemEle.selectSingleNode("resprocessing/respcondition[@title='correct']/conditionvar/and") != null)
					{
						Element rcEle = (Element) itemEle.selectSingleNode("resprocessing/respcondition[@title='correct']/conditionvar/and");
						if (rcEle.selectNodes("varequal") != null)
						{
							List<Element> veElements = rcEle.selectNodes("varequal");
							for (Iterator<?> veIter = veElements.iterator(); veIter.hasNext();)
							{
								Element veEle = (Element) veIter.next();

								if (veEle == null) continue;
								correctAnsIdentList.add(getElementValue(veEle));
							}	
						}
					}
				}
			}
			if (itemEle.selectSingleNode("presentation/flow/flow/response_lid/render_choice") != null)
			{
				Element renderChEle = (Element) itemEle.selectSingleNode("presentation/flow/flow/response_lid/render_choice");

				String shuffleValue = getAttributeValue(renderChEle, "shuffle");
				if (shuffleValue != null && shuffleValue.trim().length() != 0 && shuffleValue.trim().equals("Yes"))
					mcQuestion.setShuffleChoices(true);
				else
					mcQuestion.setShuffleChoices(false);
				if (renderChEle.selectNodes("flow_label") != null)
				{
					// TODO Answers also let you associate a file/link for choice
					List<String> answerChoices = new ArrayList<String>();
					Set<Integer> correctAnswers = new HashSet<Integer>();
					int i = 0;
					List<Element> flowLabelElements = renderChEle.selectNodes("flow_label");
					for (Iterator<?> flIter = flowLabelElements.iterator(); flIter.hasNext();)
					{
						Element flEle = (Element) flIter.next();

						if (flEle == null) continue;
						// TODO check if type here is HTML, only then do findandUploadEmbmedia, type is stored as attribute value
						String ansText = "";
						
						if (flEle.selectSingleNode("response_label") != null)
						{
							ansText = processPresentation((Element)(flEle.selectSingleNode("response_label")));
						}
						
						answerChoices.add(ansText);

						if (mcQuestion.getIsSurvey().booleanValue() == false)
						{
							String identValue = getAttributeValue(flEle, "response_label", "ident");
							if (mcQuestion.getSingleCorrect())
							{
								if (identValue != null && identValue.trim().length() != 0 && identValue.equals(correctAnsIdent))
								{
									correctAnswers.add(new Integer(i));
								}
							}
							else
							{
								if (identValue != null && identValue.trim().length() != 0 && correctAnsIdentList.contains(identValue))
								{
									correctAnswers.add(new Integer(i));
								}	
							}
							i++;
						}
					}
					if (answerChoices != null && answerChoices.size() > 0) mcQuestion.setAnswerChoices(answerChoices);
					if (mcQuestion.getIsSurvey().booleanValue() == false)
					{	
						if (correctAnswers != null && correctAnswers.size() > 0 && checkMultipleAnswers(answerChoices, correctAnswers) == true)
						{
							mcQuestion.setCorrectAnswerSet(correctAnswers);
						}
						else
						{
							mcQuestion.setIsSurvey(new Boolean(true));
						}
							
					}
				}
			}
		}
		else
		{
			M_log.warn("Question element doesn't exist");
		}
		if (M_log.isDebugEnabled()) M_log.debug("Exiting buildMultipleChoiceQuestion...");
		return mcQuestion;
	}
	
	private boolean checkMultipleAnswers(List<String> answerChoices, Set<Integer> correctAnswers)
	{
		boolean indexesOk = true;
		if (answerChoices == null || answerChoices.size() == 0) return false;
		if (correctAnswers == null || correctAnswers.size() == 0) return false;
		if (answerChoices.size() < correctAnswers.size()) return false;
		for (Integer answer : correctAnswers)
		{
			try
			{
				if (answerChoices.get(answer.intValue()) != null)
				{
					indexesOk = true;
				}
			}
			catch (Exception e)
			{
				indexesOk = false;
				break;
			}
		}
		return indexesOk;
	}
	
	private String processPresentation(Element presEle)
	{
		String ansText = "";
		String flowPath = null;
		if (presEle == null) return ansText;
		if (presEle.element("flow") != null) flowPath = "flow";
		if (presEle.element("flow_mat") != null) flowPath = "flow_mat";
		if (flowPath == null) return ansText;
		if (presEle.selectSingleNode(flowPath+"[@class='FORMATTED_TEXT_BLOCK']/material/mat_extension/mat_formattedtext") != null)
		{
			ansText = getElementValue(presEle,
					flowPath+"[@class='FORMATTED_TEXT_BLOCK']/material/mat_extension/mat_formattedtext");
			
			if (ansText != null && ansText.trim().length() != 0)
			{
				ansText = findUploadAndClean(ansText, presEle, "Mneme");
			}
			else
			{
				ansText = "";
			}
		}
		if (presEle.selectSingleNode(flowPath+"[@class='FILE_BLOCK']") != null)
		{
			Element imgEle = (Element) presEle.selectSingleNode(flowPath+"[@class='FILE_BLOCK']");
			ansText = buildImageElement(imgEle, ansText);
		}
		if (presEle.selectSingleNode(flowPath+"[@class='LINK_BLOCK']") != null)
		{
			Element linkEle = (Element) presEle.selectSingleNode(flowPath+"[@class='LINK_BLOCK']");
			ansText = buildLinkElement(linkEle, ansText);
		}
		return ansText;
	}
	
	private String buildImageElement(Element flEle, String qtext)
	{
		String newQtext = qtext;
		if (flEle != null)
		{
			String imgName = getAttributeValue(flEle, "material/matapplication", "uri");
			if (imgName != null && imgName.length() != 0)
			{
				newQtext = processEmbed(getFileName(flEle)+"/"+imgName, newQtext);
			}
		}
		return newQtext;
	}
	
	private String buildLinkElement(Element flEle, String qtext)
	{
		String newQtext = qtext;
		if (flEle != null)
		{
			String linkVal = getAttributeValue(flEle, "material/mattext", "uri");
			if (linkVal != null && linkVal.length() != 0)
			{
				String linkName = getElementValue(flEle, "material/mattext");
				if (linkName == null || linkName.trim().length() == 0) linkName = linkVal;
				newQtext = newQtext.concat("<a href=\"" + linkVal + "\" target=\"_blank\" alt=\"\">" + linkName + "</a>");
			}
		}
		return newQtext;
	}
	
	/**
	 * Build Resources items from the dat file
	 */
	private void buildResourceItems(Element organizationItemElement, String parentTitle)
	{
		if (organizationItemElement == null) return;
		try
		{	
			addResourcesTool();
			pushAdvisor();
			for (Iterator<?> iter = organizationItemElement.elementIterator("item"); iter.hasNext();)
			{
				Element item = (Element) iter.next();
				Element resourceElement = getResourceElement(item.attributeValue("identifierref"));
				if (resourceElement == null) continue;

				String resourceType = resourceElement.attributeValue("type");
				if (resourceType.equalsIgnoreCase("resource/x-bb-document"))
				{
					byte[] datFileContents = readBBFile(resourceElement.attributeValue("file"), resourceElement.attributeValue("base"));
					Map<String, String> contents = processBBDocumentDatFile(datFileContents);
					if (contents.size() == 0) continue;

					String title = "Untitled";
					if (contents.containsKey("TITLE")) title = contents.get("TITLE");

					if (contents.containsKey("CONTENTHANDLER") && contents.get("CONTENTHANDLER").equals("resource/x-bb-folder"))
					{
						buildFolder(contents, parentTitle, resourceElement.attributeValue("base"), resourceElement.selectNodes("file"));
					}
					else
					{
						// <FILES> inside the content dat file
						List<Element> filesAttached = getFilesBBDocumentDatFile(datFileContents);
						buildResourceItem(contents, parentTitle, resourceElement.attributeValue("base"), resourceElement.selectNodes("file"),
								filesAttached);
					}

					// build resource items for all child item tags
					if (item.elements("item").size() != 0) buildResourceItems(item, title);
				}
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
	}
	
	private void buildFolder(Map<String,String> contents,String parentTitle,String subFolder, List<Element> embedFiles)
	{
		String title = null;
		String folderTitle = null;
		String folderDesc = null;
		if (contents == null || contents.size() == 0) return;
		if (contents.containsKey("TITLE")) title = contents.get("TITLE");
		if (contents.containsKey("TEXT"))
		{
			folderDesc = contents.get("TEXT");
			folderDesc = findUploadAndClean(folderDesc, subFolder, embedFiles, "Resources");
		}
		if (parentTitle != null && parentTitle.trim().length() > 0)
			folderTitle = parentTitle + "/" + title;
		else
			folderTitle = title;
		createSubFolderCollection("/group/"+siteId+"/", folderTitle, folderDesc, null);

	}
	
	

	/**
	 * Create a resource item.
	 * 
	 * @param contents
	 * @param parentTitle
	 * @param subFolder
	 * @param embedFiles
	 * @throws Exception
	 */
	private void buildResourceItem(Map<String, String> contents, String parentTitle, String subFolder, List<Element> embedFiles, List<Element> insideFilesList) throws Exception
	{
		String title = null;
		// String display_name = null;
		String folderTitle = null;
		String folderDesc = null;
		String linkUrl = null;
		byte[] content_data = null;
		String res_mime_type = null;
		Time releaseDate = null;
		Time retractDate = null;

		if (contents == null || contents.size() == 0) return;
		if (contents.containsKey("TITLE")) title = contents.get("TITLE");

		if (parentTitle != null && parentTitle.trim().length() > 0) createSubFolderCollection("/group/"+ siteId+"/", parentTitle, "", null);
		if (parentTitle == null)
			folderTitle = "/group/" + siteId + "/";
		else
			folderTitle = "/group/" + siteId + "/" + parentTitle + "/";
		
		if (subFolder == null) subFolder = "";
		
		// For doc or pdf files
		if (contents.containsKey("CONTENTHANDLER") && contents.get("CONTENTHANDLER").equals("resource/x-bb-file") && insideFilesList != null
				&& insideFilesList.size() == 1)
		{
			Element filesAttach = insideFilesList.get(0);
			String attach = findFileNamefromBBDocumentFilesTag(filesAttach, subFolder);
			content_data = readDatafromFile(attach);
			res_mime_type = title.substring(title.lastIndexOf(".") + 1);
			res_mime_type = ContentTypeImageService.getContentType(res_mime_type);
		}
		else
		{
			// for html or image files
			if (contents.containsKey("TEXT"))
			{
				folderDesc = contents.get("TEXT");
				folderDesc = HtmlHelper.cleanAndAssureAnchorTarget(folderDesc, true);
				folderDesc = findAndUploadEmbeddedMedia(folderDesc, subFolder, embedFiles, insideFilesList, null, "Resources");

				if (contents.containsKey("CONTENTHANDLER") && contents.get("CONTENTHANDLER").equals("resource/x-bb-externallink")
						&& contents.containsKey("URL"))
				{
					linkUrl = contents.get("URL");
					folderDesc = folderDesc.concat("<br> <a target=\"_blank\" href=\"" + linkUrl + "\"> " + linkUrl + "</a>");
				}

				if (folderDesc != null && folderDesc.length() != 0)
				{
					content_data = folderDesc.getBytes();
					res_mime_type = "text/html";
				}
			}
		}

		if (contents.get("STARTDATE") != null) releaseDate = getTimeFromString(contents.get("STARTDATE"));
		if (contents.get("ENDDATE") != null) retractDate = getTimeFromString(contents.get("ENDDATE"));

		if (content_data == null) return;
	
		try
		{
			try
			{
				String valTitle = Validator.escapeResourceName(title);
				if (res_mime_type.equals("text/html"))
					ContentHostingService.checkResource(folderTitle + valTitle + ".html");
				else
					ContentHostingService.checkResource(folderTitle + valTitle);
			}
			catch (IdUnusedException e)
			{
				ResourcePropertiesEdit resourceProperties = ContentHostingService.newResourceProperties();
				resourceProperties.addProperty(ResourceProperties.PROP_DISPLAY_NAME, title);
				resourceProperties.addProperty(ResourceProperties.PROP_IS_COLLECTION, Boolean.FALSE.toString());

				ContentResource resource = ContentHostingService.addResource(title, folderTitle, MAXIMUM_ATTEMPTS_FOR_UNIQUENESS, res_mime_type,
						content_data, resourceProperties, null, false, releaseDate, retractDate, 0);
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}	
	
	/**
	 * Build Syllabus items from the dat file.
	 * 
	 * @param organizationItemElement
	 *        The item element
	 * 
	 */
	private void buildSyllabusItems(Element organizationItemElement)
	{
		if (organizationItemElement == null) return;
		for (Iterator<?> iter = organizationItemElement.elementIterator("item"); iter.hasNext();)
		{
			try
			{
				Element item = (Element) iter.next();

				Element resourceElement = getResourceElement(item.attributeValue("identifierref"));
				if (resourceElement == null) continue;

				List attrs = resourceElement.attributes();
				String resourceType = resourceElement.attributeValue("type");
				if (resourceType.equalsIgnoreCase("resource/x-bb-document"))
				{
					byte[] datFileContents = readBBFile(resourceElement.attributeValue("file"), resourceElement.attributeValue("base"));
					Map<String, String> contents = processBBDocumentDatFile(datFileContents);
					if (contents.size() == 0) continue;

					String asset = "";
					boolean draft = false;
					String title = "Untitled";
					String type = "item";
					String linkUrl = null;
					ArrayList<String> attach = new ArrayList<String>(0);

					if (contents.containsKey("TITLE")) title = contents.get("TITLE");
					if (contents.containsKey("TEXT"))
					{
						asset = contents.get("TEXT");
						asset = fixAnchorTitle(asset);
					}
					if (contents.containsKey("AVAILABLE") && contents.get("AVAILABLE").equals("false")) draft = true;
				    
					if (contents.containsKey("CONTENTHANDLER") && contents.get("CONTENTHANDLER").equals("resource/x-bb-externallink")
							&& contents.containsKey("URL"))
					{
						linkUrl = contents.get("URL");
					}

					if (asset.length() == 0 && linkUrl != null && linkUrl.length() != 0)
					{
						type = "redirectUrl";
					}
					else if (asset.length() != 0 && linkUrl != null && linkUrl.length() != 0)
					{
						if (linkUrl.startsWith("http://") || linkUrl.startsWith("https://"))
						{
							asset = asset.concat("\n <a target=\"_blank\" href=\"" + linkUrl + "\"> " + linkUrl + "</a>");
							linkUrl = null;
						}
					}
					
					// embedded file locations <resource><file>
					List<Element> embedFiles = resourceElement.selectNodes("file");
					
					// attachments
					if (linkUrl != null) attach.add(linkUrl);
					
					//<CONTENT><FILES>
					List<Element> filesAttached = getFilesBBDocumentDatFile(datFileContents);
					if (filesAttached.size() != 0)
					{
						for (Element f : filesAttached)
						{
							attach.add(findFileNamefromBBDocumentFilesTag(f,resourceElement.attributeValue("base")));
						}
					}
					String[] attachments = new String[attach.size()];
					buildSyllabus(title, asset, draft, type, attach.toArray(attachments), resourceElement.attributeValue("base"), embedFiles, filesAttached, null);

					// build syllabus for all child item tags
					if (item.elements("item").size() != 0) buildSyllabusItems(item);
				}
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
	}
	
	private Question buildTrueFalseQuestion(Element itemEle, TrueFalseQuestion tfQuestion)
	{
		if (M_log.isDebugEnabled()) M_log.debug("Entering buildTrueFalseQuestion...");

		if (itemEle != null)
		{
			tfQuestion = (TrueFalseQuestion) buildCommonElements(itemEle, (Question) tfQuestion);

			//TODO case attribute value..case sensitivity for responses
			if (itemEle.selectSingleNode("resprocessing/respcondition[@title='correct']") != null)
			{
				Element rcEle = (Element) itemEle.selectSingleNode("resprocessing/respcondition[@title='correct']");
				String ansText = getElementValue(rcEle, "conditionvar/varequal");
				if (ansText != null && ansText.length() > 0)
				{
					if (ansText.equals("true") || ansText.equals("true_false.true"))
						tfQuestion.setCorrectAnswer(true);
					else
						tfQuestion.setCorrectAnswer(false);
				}
				else
					tfQuestion.setIsSurvey(new Boolean(true));
			}
		}
		if (M_log.isDebugEnabled()) M_log.debug("Exiting buildTrueFalseQuestion...");
		return tfQuestion;

	}
	
	private Question buildTrueMultipleChoiceQuestion(Element itemEle, MultipleChoiceQuestion mcQuestion)
	{
		
		if (M_log.isDebugEnabled()) M_log.debug("Entering buildTrueMultipleChoiceQuestion...");

		if (itemEle != null)
		{
			// Setting this to empty string to avoid null check when comparing
			String correctAnsIdent = "";
			mcQuestion = (MultipleChoiceQuestion) buildCommonElements(itemEle, (Question) mcQuestion);
			if (mcQuestion.getIsSurvey().booleanValue() == false)
			{
				if (itemEle.selectSingleNode("resprocessing/respcondition[@title='correct']") != null)
				{
					Element rcEle = (Element) itemEle.selectSingleNode("resprocessing/respcondition[@title='correct']");
					correctAnsIdent = getElementValue(rcEle, "conditionvar/varequal");
				}
			}
			if (itemEle.selectSingleNode("presentation/flow/flow/response_lid/render_choice") != null)
			{
				Element renderChEle = (Element) itemEle.selectSingleNode("presentation/flow/flow/response_lid/render_choice");

				String shuffleValue = getAttributeValue(renderChEle, "shuffle");
				if (shuffleValue != null && shuffleValue.trim().length() != 0 && shuffleValue.trim().equals("Yes"))
					mcQuestion.setShuffleChoices(true);
				else
					mcQuestion.setShuffleChoices(false);
				if (renderChEle.selectNodes("flow_label/response_label") != null)
				{
					// TODO Answers also let you associate a file/link for choice
					List<String> answerChoices = new ArrayList<String>();
					Set<Integer> correctAnswers = new HashSet<Integer>();
					int i = 0;
					List<Element> respLabelElements = renderChEle.selectNodes("flow_label/response_label");
					for (Iterator<?> rlIter = respLabelElements.iterator(); rlIter.hasNext();)
					{
						Element rlEle = (Element) rlIter.next();

						if (rlEle == null) continue;
						// TODO check if type here is HTML, only then do findandUploadEmbmedia, type is stored as attribute value
						String ansText = getElementValue(rlEle, "flow_mat/material/mattext");

						if (ansText != null && ansText.trim().length() != 0)
						{
							if (orValueMap.get(ansText) != null)
								answerChoices.add((String) orValueMap.get(ansText));
							else
								answerChoices.add(ansText);
						}

						String identValue = getAttributeValue(rlEle, "ident");
						if (identValue != null && identValue.trim().length() != 0 && identValue.equals(correctAnsIdent))
						{
							correctAnswers.add(new Integer(i));
						}
						i++;

					}
					if (answerChoices != null && answerChoices.size() > 0) mcQuestion.setAnswerChoices(answerChoices);
					if (correctAnswers != null && correctAnswers.size() > 0 && checkMultipleAnswers(answerChoices, correctAnswers) == true) 
					{
						mcQuestion.setCorrectAnswerSet(correctAnswers);
					}
					else
					{
						mcQuestion.setIsSurvey(new Boolean(true));
					}

				}
			}
		}
		else
		{
			M_log.warn("Question element doesn't exist");
		}
		if (M_log.isDebugEnabled()) M_log.debug("Exiting buildMultipleChoiceQuestion...");
		return mcQuestion;
	}
		
	private boolean checkTfType(Element itemEle)
	{
		if (itemEle != null)
		{
			if (itemEle.selectSingleNode("resprocessing/respcondition[@title='correct']") != null)
			{
				Element rcEle = (Element) itemEle.selectSingleNode("resprocessing/respcondition[@title='correct']");
				String ansText = getElementValue(rcEle, "conditionvar/varequal");
				if (ansText.startsWith("true_false")) return true;
			}
		}
		return false;
	}
	
	/**
	 * Adds anchor tag title if its missing
	 * 
	 * @param s1
	 *        text
	 * @return modified text
	 */
	private String fixAnchorTitle(String s1)
	{
		try
		{
			Pattern pa = Pattern.compile("<a\\s+.*?/*>(.*?</a>)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);
			Pattern p_hrefAttribute = Pattern.compile("\\s*href\\s*=\\s*(\".*?\")", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);
			Pattern pa_end = Pattern.compile("\\s*</a>");

			Matcher m = pa.matcher(s1);
			StringBuffer sb = new StringBuffer();
			while (m.find())
			{
				String a_content = m.group(0);
				String a_title = m.group(1);

				// check if resource has title, if not add it
				Matcher m_aEnd = pa_end.matcher(a_title);
				if (m_aEnd.find())
				{
					// remove ending tag to see if title is there
					StringBuffer sb_blank = new StringBuffer();
					m_aEnd.appendReplacement(sb_blank, "");
					m_aEnd.appendTail(sb_blank);
					a_title = sb_blank.toString().trim();
					if (a_title.length() == 0)
					{
						Matcher m_href = p_hrefAttribute.matcher(a_content);
						if (m_href.find())
						{
							String href = m_href.group(1);
							if (href.length() > 0)
							{
								href = href.substring(1, href.length() - 1);
								href = href.replace("@X@", "/");
								if (href.lastIndexOf("/") != -1) href = href.substring(href.lastIndexOf("/") + 1, href.length());
								try
								{
									href = URLDecoder.decode(href, "UTF-8");
								}
								catch (Exception ex)
								{
									// nothing
								}
								a_content = a_content.replace("</a>", href + "</a>");
							}
						}
					}
				}
				// add quote replacement for / and $ characters
				m.appendReplacement(sb, Matcher.quoteReplacement(a_content));
			}
			m.appendTail(sb);
			return sb.toString();
		}
		catch (Exception e)
		{
			M_log.debug("error in fixing anchor title:" + e.getMessage());
		}
		return s1;
	}
	
	/**
	 * Find Attribute from the list.
	 * 
	 * @param name
	 *        attribute name
	 * @param attrs
	 *        List of attributes
	 * @return value of attribute
	 */
	private String findAttribute(String name, List attrs)
	{
		for (Iterator iter = attrs.iterator(); iter.hasNext();)
		{
			Attribute a = (Attribute) iter.next();
			M_log.debug(a.getName());
		}
		return null;
	}

	/**
	 * Find the file tags and read the location. Mostly file is in xml:base + <file> tag href. If we don't find <file> href then look in csfiles and base folder.
	 * 
	 * @param fileReference
	 * @param subFolder
	 * @param embedFiles
	 *        List of <resource><file> tag elements found in BB package
	 * @param tool
	 * @return
	 */
	private String findFileLocation(String fileReference, String subFolder, List<Element> embedFiles, String tool)
	{
		M_log.debug("findFileLocation fileReference:" + fileReference);
		String foundHref = null;
		if (subFolder == null) subFolder = "";

		if (embedFiles == null || embedFiles.size() == 0) return fileReference;

		String searchFile = fileReference;
		if (fileReference.lastIndexOf("/") != -1) searchFile = fileReference.substring(fileReference.lastIndexOf("/"));

		// if found in file tag then location is xml:base + file href
		for (Element f : embedFiles)
		{
			if (f.attributeValue("href").contains(searchFile))
			{
				foundHref = f.attributeValue("href");
				fileReference = subFolder.concat("/" + foundHref);
				return fileReference;
			}
		}
		// Not found in <file> so look in csfiles and base subfolder
		if (foundHref == null)
		{
			// if ("Melete".equals(tool) || "Syllabus".equals(tool)) fileReference = subFolder.concat("/embedded/" + fileReference);
			String s = findFileNameWhenNoFilesTag(fileReference, subFolder);
			if (s != null) fileReference = s.replace(unzipBackUpLocation, "");
		}

		return fileReference;
	}

	/**
	 * Find files specified in <CONTENT><FILES>.
	 * 
	 * @param f
	 * @param resourceBase
	 * @return
	 */
	private String findFileNamefromBBDocumentFilesTag(Element f, String resourceBase)
	{
		String attach = f.selectSingleNode("NAME").getText();
		// for names like /xid-356256_1
		if (attach != null && attach.lastIndexOf(".") == -1)
		{
			Element storage = (Element) f.selectSingleNode("STORAGETYPE");
			Element linkName = (Element) f.selectSingleNode("LINKNAME");
			if (storage.attributeValue("value").equals("CS") && linkName != null)
			{
				// physical file is stored with linkname + name i.e change linkName value otm022511c.mp3 to otm022511c__xid-356256_1.mp3
				// reference is xid-6527_1 and no files tag and physical location is /csfiles/home_dir/binac__xid-6527_1
				String newName = attach.replaceFirst("/", "__");
				newName = findFileNameWhenNoFilesTag(newName, resourceBase);
				if (newName != null) attach = newName.replace(unzipBackUpLocation, "");
			}
			else if (storage.attributeValue("value").equals("LOCAL"))
			{
				attach = resourceBase + "/" + linkName.attributeValue("value");
			}
		}
		else
		{
			attach = resourceBase + "/" + attach;
		}
		return attach;
	}
		
	/**
	 * Find webdav files in csFiles folder and then in base subfolder when there is no files tag to define it.
	 * 
	 * @param reference
	 * @param resourceBase
	 * @return
	 */
	private String findFileNameWhenNoFilesTag(String reference, String resourceBase)
	{
		File f = new File(unzipBackUpLocation + File.separator + "csfiles" + File.separator + "home_dir");
		String check = reference;
		if (check.lastIndexOf("/") != -1) check = check.substring(check.lastIndexOf("/") + 1);

		String found = findFileinDirectory(f, check);
		if (found != null) return found;
		// src="@X@EmbeddedFile.requestUrlStub@X@@@/891C8077CEFEF5A19BC3F68B87ADFE02/courses/1/201032485/content/_389449_1/embedded/mailenva.gif" is in resourceBase/embedded folder
		File fBase = new File(unzipBackUpLocation + File.separator + resourceBase + File.separator);
		found = findFileinDirectory(fBase, check);
		return found;
	}

	/**
	 * Parse the file and store them in a map.
	 * 
	 * @param datFileContents
	 *        file contents
	 * @return Map with parsed content.
	 */
	private List<Element> getFilesBBDocumentDatFile(byte[] datFileContents) throws Exception
	{
		List<Element> contents = new ArrayList<Element>(0);
		if (datFileContents == null || datFileContents.length == 0) return contents;

		Document contentsDOM = XMLHelper.getSaxReader().read(new ByteArrayInputStream(datFileContents));
		Element root = contentsDOM.getRootElement();

		if (root.selectSingleNode("FILES") != null)
		{
			Element element = (Element) root.selectSingleNode("FILES");
			contents = element.selectNodes("FILE");
		}
		return contents;
	}
	
	/**
	 * 
	 * @param datFileContents
	 * @return
	 * @throws Exception
	 */
	private Map<String, String> processBBAnnouncementDatFile(byte[] datFileContents) throws Exception
	{
		Map<String, String> contents = new HashMap<String, String>();
		if (datFileContents == null || datFileContents.length == 0) return contents;

		Document contentsDOM = XMLHelper.getSaxReader().read(new ByteArrayInputStream(datFileContents));
		Element root = contentsDOM.getRootElement();

		if (root.selectSingleNode("TITLE") != null)
		{
			Element element = (Element) root.selectSingleNode("TITLE");
			contents.put("TITLE", element.attributeValue("value"));
		}

		if (root.selectSingleNode("DESCRIPTION/TEXT") != null) contents.put("DESCRIPTION_TEXT", root.selectSingleNode("DESCRIPTION/TEXT").getText());

		if (root.selectSingleNode("ISPERMANENT") != null)
		{
			Element element = (Element) root.selectSingleNode("ISPERMANENT");
			contents.put("REUSE", element.attributeValue("value"));
		}

		if (root.selectSingleNode("DATES/RESTRICTSTART") != null)
		{
			Element element = (Element) root.selectSingleNode("DATES/RESTRICTSTART");
			contents.put("RELEASEDATE", element.attributeValue("value"));
		}

		if (root.selectSingleNode("DATES/RESTRICTEND") != null)
		{
			Element element = (Element) root.selectSingleNode("DATES/RESTRICTEND");
			contents.put("ENDDATE", element.attributeValue("value"));
		}

		if (root.selectSingleNode("ORDERNUM") != null)
		{
			Element element = (Element) root.selectSingleNode("ORDERNUM");
			contents.put("SEQ", element.attributeValue("value"));
		}
		return contents;
	}
	
	/**
	 * Parse coursetoc dat file.
	 * @param datFileContents
	 * @return
	 * @throws Exception
	 */
	private Map<String, String> processBBCourseTOCDatFile(byte[] datFileContents) throws Exception
	{
		Map<String, String> contents = new HashMap<String, String>();
		if (datFileContents == null || datFileContents.length == 0) return contents;

		Document contentsDOM = XMLHelper.getSaxReader().read(new ByteArrayInputStream(datFileContents));
		Element root = contentsDOM.getRootElement();
				
		if (root.selectSingleNode("LABEL") != null)
		{
			Element element = (Element) root.selectSingleNode("LABEL");
			contents.put("LABEL", element.attributeValue("value"));
		}
		
		if (root.selectSingleNode("INTERNALHANDLE") != null)
		{
			Element element = (Element) root.selectSingleNode("INTERNALHANDLE");
			contents.put("INTERNALHANDLE", element.attributeValue("value"));
		}
		
		if (root.selectSingleNode("FLAGS/ISENABLED") != null)
		{
			Element element = (Element) root.selectSingleNode("FLAGS/ISENABLED");
			contents.put("ISENABLED", element.attributeValue("value"));
		}		
		                              
		return contents;
	}
	
	/**
	 * Parse discussions dat file
	 * 
	 * @param datFileContents
	 * @return
	 * @throws Exception
	 */
	private Map<String, String> processBBDiscussionDatFile(byte[] datFileContents) throws Exception
	{
		Map<String, String> contents = new HashMap<String, String>();
		if (datFileContents == null || datFileContents.length == 0) return contents;

		Document contentsDOM = XMLHelper.getSaxReader().read(new ByteArrayInputStream(datFileContents));
		Element root = contentsDOM.getRootElement();

		if (root.selectSingleNode("TITLE") != null)
		{
			Element element = (Element) root.selectSingleNode("TITLE");
			contents.put("TITLE", element.attributeValue("value"));
		}

		if (root.selectSingleNode("DESCRIPTION/TEXT") != null) contents.put("DESCRIPTION_TEXT", root.selectSingleNode("DESCRIPTION/TEXT").getText());

		if (root.selectSingleNode("STARTDATE") != null)
		{
			Element element = (Element) root.selectSingleNode("STARTDATE");
			contents.put("OPENDATE", element.attributeValue("value"));
		}

		if (root.selectSingleNode("ENDDATE") != null)
		{
			Element element = (Element) root.selectSingleNode("ENDDATE");
			contents.put("ENDDATE", element.attributeValue("value"));
		}
	
        if (root.selectSingleNode("FLAGS/ALLOWNEWTHREADS") != null)
		{
			Element element = (Element) root.selectSingleNode("FLAGS/ALLOWNEWTHREADS");
			contents.put("ALLOWNEWTHREADS", element.attributeValue("value"));
		}                               
        
		if (root.selectSingleNode("FLAGS/ALLOWFORUMGRADING") != null)
		{
			Element element = (Element) root.selectSingleNode("FLAGS/ALLOWFORUMGRADING");
			contents.put("ALLOWFORUMGRADING", element.attributeValue("value"));
		}
		
		if (root.selectSingleNode("FLAGS/ALLOWTHREADGRADING") != null)
		{
			Element element = (Element) root.selectSingleNode("FLAGS/ALLOWTHREADGRADING");
			contents.put("ALLOWTHREADGRADING", element.attributeValue("value"));
		}
		
		if (root.selectSingleNode("FLAGS/FORUMGRADEHANDLE") != null)
		{
			Element element = (Element) root.selectSingleNode("FLAGS/FORUMGRADEHANDLE");
			contents.put("FORUMGRADEHANDLE", element.attributeValue("value"));
		}

		if (root.selectSingleNode("FLAGS/ALLOWFILEATTACHMENTS") != null)
		{
			Element element = (Element) root.selectSingleNode("FLAGS/ALLOWFILEATTACHMENTS");
			contents.put("ALLOWFILEATTACHMENTS", element.attributeValue("value"));
		}		
		
		return contents;
	}	
	
	/**
	 * Parse the file and store them in a map.
	 * 
	 * @param datFileContents
	 *        file contents
	 * @return Map with parsed content.
	 */
	private Map<String, String> processBBDocumentDatFile(byte[] datFileContents) throws Exception
	{
		Map<String, String> contents = new HashMap<String, String>();
		if (datFileContents == null || datFileContents.length == 0) return contents;

		Document contentsDOM = XMLHelper.getSaxReader().read(new ByteArrayInputStream(datFileContents));
		
		Element root = contentsDOM.getRootElement();
		if (root.selectSingleNode("TITLE") != null)
		{
			Element element = (Element) root.selectSingleNode("TITLE");
			contents.put("TITLE", element.attributeValue("value"));
		}
		if (root.selectSingleNode("BODY/TEXT") != null) contents.put("TEXT", root.selectSingleNode("BODY/TEXT").getText());
		if (root.selectSingleNode("CONTENTHANDLER") != null)
		{
			Element element = (Element) root.selectSingleNode("CONTENTHANDLER");
			contents.put("CONTENTHANDLER", element.attributeValue("value"));
		}
		if (root.selectSingleNode("URL") != null)
		{
			Element element = (Element) root.selectSingleNode("URL");
			contents.put("URL", element.attributeValue("value"));
		}
		
		if (root.selectSingleNode("DATES/START") != null)
		{
			Element element = (Element) root.selectSingleNode("DATES/START");
			contents.put("STARTDATE", element.attributeValue("value"));
		}
		if (root.selectSingleNode("DATES/END") != null)
		{
			Element element = (Element) root.selectSingleNode("DATES/END");
			contents.put("ENDDATE", element.attributeValue("value"));
		}

        if (root.selectSingleNode("FLAGS/ISAVAILABLE") != null)
  		{
  			Element element = (Element) root.selectSingleNode("FLAGS/ISAVAILABLE");
  			contents.put("AVAILABLE", element.attributeValue("value"));
  		}                              
		return contents;
	}
	
	/**
	 * 
	 * @param toolResourceElement
	 * @param datFileContents
	 * @param poolNames
	 * @param assmtNames
	 * @throws Exception
	 */
	private void processBBPoolDatFile(Element toolResourceElement, byte[] datFileContents, Document contentsDOM, String poolRefId,
			List<String> poolNames, List<String> assmtNames) throws Exception
	{
		boolean poolExists = false;
		boolean assmtExists = false;
		Element poolEle = contentsDOM.getRootElement();
		Assessment assmt = null;
		Part part = null;

		// title is title[@value]
		String title = "New Pool";
		Element titleElement = (Element) poolEle.selectSingleNode("TITLE");
		if (titleElement != null) title = titleElement.attributeValue("value");
		poolExists = checkPoolTitleExists(title, poolNames, siteId);
		if (poolExists) return;

		Pool newPool = poolService.newPool(siteId);
		if (newPool == null) return;

		newPool.setTitle(title);
		String desc = getElementValue(poolEle, "DESCRIPTION/TEXT");
		if (desc != null && desc.length() != 0)
		{
			desc = findUploadAndClean(desc, poolEle, "Mneme");
			newPool.setDescription(desc);
		}

		// create assessment for the same with one part
		assmtExists = checkAssmtTitleExists(title, assmtNames, siteId);
		if (!assmtExists)
		{
			// create test object
			assmt = assessmentService.newAssessment(siteId);
			assmt.setType(AssessmentType.test);
			assmt.setTitle(title);
			part = assmt.getParts().addPart();
		}

		// Iterate over Questionslist
		Element questionsList = (Element) poolEle.selectSingleNode("QUESTIONLIST");
		if (questionsList != null)
		{
			for (Iterator i = questionsList.elementIterator("QUESTION"); i.hasNext();)
			{
				Element questionElement = (Element) i.next();
				String id = questionElement.attributeValue("id");
				String className = questionElement.attributeValue("class");

				if (id == null) continue;

				Element actualQuestion = (Element) poolEle.selectSingleNode(className + "[@id='" + id + "']");
				if (actualQuestion == null) continue;

				Question question = processItemElement(actualQuestion, className, newPool);
				if (question != null)
				{
					questionService.saveQuestion(question);
					if (!assmtExists)
					{
						QuestionPick questionPick = part.addPickDetail(question);
						questionPick.setPoints(new Float("1.00"));
					}
				}
			}
		}
		poolService.savePool(newPool);
		if (!assmtExists) assessmentService.saveAssessment(assmt);
		// The ref pool map is created for use by random draws
		refPoolMap.put(poolRefId, newPool.getId());
	}
	
	/**
	 * Processes assessment and pool dat files
	 * 
	 * @param toolResourceElement
	 * @param datFileContents
	 * @param poolNames
	 * @param assmtNames
	 * @throws Exception
	 */
	private void processBBAssmtPoolDatFile(Element toolResourceElement, byte[] datFileContents, List<String> poolNames, List<String> assmtNames) throws Exception
	{
		String assmtType;
		String desc;
		Question question;
		Assessment assmt = null;
		Pool newPool = null;
		Part part = null;
		boolean poolExists = false;
		boolean assmtExists = false;
		
		String poolRefId = toolResourceElement.attributeValue("base");
		Document contentsDOM = XMLHelper.getSaxReader().read(new ByteArrayInputStream(datFileContents));
		if (contentsDOM != null)
		{
			addFileNameElement(contentsDOM, poolRefId);
			try
			{
				Element root = contentsDOM.getRootElement();
				String rootName = root.getName();
				
				if ("Pool".equalsIgnoreCase(rootName))
				{
					processBBPoolDatFile(toolResourceElement, datFileContents, contentsDOM, poolRefId, poolNames, assmtNames);
					return;
				}
					
				if ("questestinterop".equalsIgnoreCase(rootName))
				{
					Element assmtEle = (Element) root.selectSingleNode("assessment");
					if (assmtEle == null) return;
					assmtType = getElementValue(assmtEle, "assessmentmetadata/bbmd_assessmenttype");
					if (assmtType != null && assmtType.length() != 0)
					{
						// TODO, make the default title of the pool the same as the name of the file
						String title = "New Pool";

						title = assmtEle.attributeValue("title");
						poolExists = checkPoolTitleExists(title, poolNames, siteId);
						if (poolExists) return;

						if (assmtType.equals("Test") || assmtType.equals("Survey"))
						{
							assmtExists = checkAssmtTitleExists(title, assmtNames, siteId);
							if (!assmtExists)
							{
								// create test object
								assmt = assessmentService.newAssessment(siteId);
								if (assmtType.equals("Test")) assmt.setType(AssessmentType.test);
								if (assmtType.equals("Survey")) assmt.setType(AssessmentType.survey);
								assmt.setTitle(title);
							}
						}

						newPool = poolService.newPool(siteId);
						//This if block sets pool and assessment titles and description
						if (newPool != null)
						{
							newPool.setTitle(title);
							StringBuffer descInst = new StringBuffer();
							desc = getElementValue(assmtEle, "presentation_material/flow_mat/material/mat_extension/mat_formattedtext");
							if (desc != null && desc.length() != 0)
							{
								desc = findUploadAndClean(desc, assmtEle, "Mneme");
								descInst.append(desc);

							}
							String inst = getElementValue(assmtEle, "rubric/flow_mat/material/mat_extension/mat_formattedtext");
							if (inst != null && inst.length() != 0)
							{
								inst = findUploadAndClean(inst, assmtEle, "Mneme");
								descInst.append(inst);
							}
							if (descInst.length() > 0)
							{
								newPool.setDescription(descInst.toString());
								if (!assmtExists && (assmtType.equals("Test") || assmtType.equals("Survey")))
									assmt.getPresentation().setText(descInst.toString());
							}

							poolService.savePool(newPool);
							//The ref pool map is created for use by random draws
							refPoolMap.put(poolRefId, newPool.getId());
							if (!assmtExists && (assmtType.equals("Test") || assmtType.equals("Survey"))) assessmentService.saveAssessment(assmt);
						}
					}
					if (newPool != null && assmtEle.selectSingleNode("section") != null)
					{
						Element sectionEle = (Element) assmtEle.selectSingleNode("section");
						if (sectionEle.selectNodes("item") != null && ((List) sectionEle.selectNodes("item")).size() > 0)
						{
							// if (!assmtExists && (assmtType.equals("Test") || assmtType.equals("Survey"))) part = assmt.getParts().addPart();
							List<Element> itemElements = sectionEle.selectNodes("item");
							boolean partExists = false;
							//Item elements contain questions, we iterate through items
							for (Iterator<?> itemIter = itemElements.iterator(); itemIter.hasNext();)
							{
								Element itemEle = (Element) itemIter.next();

								if (itemEle == null) continue;
								
								//Check title of category
								String questionTitle = checkCategoryTitle(itemEle);
								//Process item element and associate question with pool
								question = processItemElement(itemEle, null ,newPool);
								//Add questions to a manual part of an assessment
								if (question != null)
								{
									if (!partExists && !assmtExists && (assmtType.equals("Test") || assmtType.equals("Survey")))
									{
										part = assmt.getParts().addPart();
										partExists = true;
									}
									if (!assmtExists && assmtType.equals("Survey"))
									{
										question.setIsSurvey(new Boolean(true));
									}
									question.setTitle(questionTitle);
									questionService.saveQuestion(question);
									if (!assmtExists && (assmtType.equals("Test") || assmtType.equals("Survey")))
									{
										QuestionPick questionPick = part.addPickDetail(question);
										String gradeStr = getElementValue(itemEle, "itemmetadata/qmd_absolutescore_max");
										if (gradeStr != null && gradeStr.length() != 0)
										{
											questionPick.setPoints(Float.parseFloat(gradeStr));
										}
									}
								}
							}//End for loop for items
							if (!assmtExists && (assmtType.equals("Test") || assmtType.equals("Survey"))) assessmentService.saveAssessment(assmt);
						}//End if items exist

						// If assessment only has random questions, there is no need to create a pool for it
						if (newPool.getNumQuestions().intValue() == 0) poolService.removePool(newPool);
						//Section tags within contain random and question blocks
						if (sectionEle.selectNodes("section") != null && ((List) sectionEle.selectNodes("section")).size() > 0)
						{
							// if (assmtType.equals("Test")) randomPart = assmt.getParts().addPart();
							List<Element> secElements = sectionEle.selectNodes("section");
							for (Iterator<?> secIter = secElements.iterator(); secIter.hasNext();)
							{
								Element secEle = (Element) secIter.next();

								if (secEle == null) continue;
								String secType = getElementValue(secEle, "sectionmetadata/bbmd_sectiontype");
								//Process random blocks
								if (secType != null && secType.length() > 0 && secType.equals("Random Block"))
								{
									String referencedPoolId = getElementValue(secEle, "selection_ordering/selection/sourcebank_ref");
									if (refPoolMap != null && refPoolMap.size() > 0)
									{
										String poolId = refPoolMap.get(referencedPoolId);
										if (poolId != null && !assmtExists && (assmtType.equals("Test") || assmtType.equals("Survey")))
											addRandomDraw(poolId, assmt, secEle);
									}
								}
								//Process question blocks
								if (secType != null && secType.length() > 0 && secType.equals("Question Block"))
								{
									String poolId = null;
									String bbQuestionId = null;
									String questionId = null;
									//Process linked questions, add a manual part for those
									if (secEle.selectSingleNode("selection_ordering/selection/or_selection/selection_param[@pname='singleLink']") != null)
									{
										String singleLinkVal = getElementValue(secEle,
												"selection_ordering/selection/or_selection/selection_param[@pname='singleLink']");
										if (singleLinkVal != null && singleLinkVal.equals("true"))
										{
											bbQuestionId = fetchBBQuestionId(secEle);
											if ((bbQuestionId != null) && (questionTitleMap != null) && (questionTitleMap.size() > 0))
											{
												questionId = (String) bbqidQuestionMap.get(bbQuestionId);
												if (questionId != null)
												{
													question = questionService.getQuestion(questionId);
													if (question != null && !assmtExists && (assmtType.equals("Test") || assmtType.equals("Survey")))
													{
														part = assmt.getParts().addPart();
														QuestionPick questionPick = part.addPickDetail(question);
														String gradeStr = getElementValue(secEle, "sectionmetadata/qmd_absolutescore_max");
														if (gradeStr != null && gradeStr.length() != 0)
														{
															questionPick.setPoints(Float.parseFloat(gradeStr));
														}
													}
												}
											}
										}
									}
									else
									{
										//Process questions that belong to categories, add a random draw for those
										bbQuestionId = fetchBBQuestionId(secEle);
										if ((bbQuestionId != null) && (questionTitleMap != null) && (questionTitleMap.size() > 0))
										{
											poolId = (String) questionTitleMap.get(bbQuestionId);
											if (poolId != null && !assmtExists && (assmtType.equals("Test") || assmtType.equals("Survey")))
												addRandomDraw(poolId, assmt, secEle);
										}
									}
								}
							}//For loop for inside section tags end
							if (!assmtExists && (assmtType.equals("Test") || assmtType.equals("Survey"))) assessmentService.saveAssessment(assmt);
						}//If condition for inside section end

						//Set assessment settings
						if (!assmtExists && assmtType.equals("Test"))
						{
							assmt.getGrading().setGradebookIntegration(Boolean.TRUE);

							if (assmt.getParts().getTotalPoints().floatValue() <= 0)
							{
								assmt.setNeedsPoints(Boolean.FALSE);
							}
						}
						if (!assmtExists && (assmtType.equals("Test") || assmtType.equals("Survey")))
						{
							// There may be empty parts that have no questions in them
							// assmt.getParts().removeEmptyParts();
							// If test has just one essay question, mark it as an assignment
							//If a test has atleast one question that is an essay, set auto release to false
							assmt = checkEssays(assmt);
							
							// If test has no points and all non-essay questions without an answer key
							// mark as survey
							assmt = checkIfSurvey(assmt);
							assessmentService.saveAssessment(assmt);
							String testTitle = toolResourceElement.attributeValue("title");
							XPath testResources = null;
							boolean processedSettings = false;
							// First do a title check to get settings
							try
							{
								testResources = backUpDoc.createXPath("/manifest/resources/resource[@bb:title='" + testTitle + "']");

								List<Element> eleTestResources = testResources.selectNodes(backUpDoc);

								// Get all test settings such as dates, time limit, tries etc
								if (eleTestResources != null && eleTestResources.size() > 0)
								{
									for (Iterator<?> testIter = eleTestResources.iterator(); testIter.hasNext();)
									{
										Element testElement = (Element) testIter.next();
										if (testElement.attributeValue("type").equals("resource/x-bb-document"))
										{
											String fileName = testElement.attributeValue("file");
											if (fileName != null)
											{
												datFileContents = readBBFile(fileName, testElement.attributeValue("base"));
												if (datFileContents == null || datFileContents.length == 0)
												{
													continue;
												}
												assmt = processDates(datFileContents, assmt, poolRefId);
												assmt = fetchAndProcessSettings(fileName, assmt);
												processedSettings = true;
											}
										}
										assessmentService.saveAssessment(assmt);
									}
								}
							}
							catch (Exception e)
							{
								M_log.debug("processBBAssmtPoolDatFile did not find resource entries with title");
							}
							// If title fails, check map to see if we can get associated files
							if (!processedSettings)
							{
								String fileName = null;
								if (assnIdEleMap != null && assnIdEleMap.size() > 0)
								{
									OutcomeValues oVal = (OutcomeValues) assnIdEleMap.get(poolRefId);
									if (oVal != null)
									{
										if (oVal.getContentId() != null)
										{
											fileName = oVal.getContentId() + ".dat";
											if (fileName != null)
											{
												datFileContents = readBBFile(fileName, oVal.getContentId());
												if (datFileContents != null && datFileContents.length > 0)
												{
													assmt = processDates(datFileContents, assmt, poolRefId);
												}
											}
										}
									}
								}

								if (testRefMap != null && testRefMap.size() > 0)
								{
									if (fileName != null && fileName.endsWith(".dat"))
									{
										String linkIdStr = testRefMap.get(fileName.substring(0, fileName.indexOf(".")));

										if (linkIdStr != null)
										{
											datFileContents = readBBFile(linkIdStr + ".dat", linkIdStr);
											if (datFileContents != null && datFileContents.length > 0)
											{
												assmt = fetchAndProcessSettings(fileName, assmt);
											}
										}
									}
								}
								assessmentService.saveAssessment(assmt);
							}
						}
					}
				}
			}
			catch (AssessmentPermissionException e)
			{
				M_log.warn("processBBAssmtPoolDatFile permission exception: " + e.toString());
				return;
			}
		}
		return;
	}

	/**
	 * Parse and read each element of Question_MultipleChoice or other type Question and put in Map
	 * @param questionElement
	 * @return
	 * @throws Exception
	 */
	private Map<String, String> processBBPoolQuestionsElement(Element questionElement) throws Exception
	{
		Map<String, String> contents = new HashMap<String, String>();

		if (questionElement.selectSingleNode("TITLE") != null)
		{
			Element element = (Element) questionElement.selectSingleNode("TITLE");
			contents.put("TITLE", element.attributeValue("value"));
		}

		if (questionElement.selectSingleNode("BODY/TEXT") != null)
		{
			String body = questionElement.selectSingleNode("BODY/TEXT").getText();
			body = findUploadAndClean(body, questionElement, "Mneme");
			contents.put("BODY_TEXT", body);
		}

		// correct answer
		Element correctAnswer = (Element) questionElement.selectSingleNode("GRADABLE/CORRECTANSWER");
		if (correctAnswer != null)
		{
			String correct_id = correctAnswer.attributeValue("answer_id");
			contents.put("Correct_Answer_id", "Choices_" + correct_id);
		}

		//feedback		
		Element feedback = (Element) questionElement.selectSingleNode("GRADABLE/FEEDBACK_WHEN_CORRECT");
		if (feedback != null) contents.put("feedback", feedback.getText());
		
		// answer choices
		List<Element> answers = questionElement.selectNodes("ANSWER");
		if (answers != null)
		{
			contents.put("count", Integer.toString(answers.size()));
			int count = 1;
			for (Element answer : answers)
			{
				String answerText = answer.selectSingleNode("TEXT").getText();
				answerText = findUploadAndClean(answerText, questionElement, "Mneme");
				contents.put("Choices_" + answer.attributeValue("id"), answerText);
				String answerPosition = answer.attributeValue("position");
				if (answerPosition != null) contents.put("Position_" + answer.attributeValue("id"), answerPosition);
			}
		}

		answers = questionElement.selectNodes("ANSWER[@position]");
		if (answers != null) contents.put("hasPosition", "true");

		return contents;
	}
	
	/**
	 * Returns the first question id from the sec element, used for question blocks/sets
	 * @param secEle
	 * @return
	 */
	private String fetchBBQuestionId(Element secEle)
	{
		String bbQuestionId = null;
		List<Element> selElements = secEle.selectNodes("selection_ordering/selection/or_selection/selection_metadata");
		for (Iterator<?> selIter = selElements.iterator(); selIter.hasNext();)
		{
			Element selEle = (Element) selIter.next();
			bbQuestionId = getElementValue(selEle);
			if (bbQuestionId != null)
			{
				break;
			}
		}
		return bbQuestionId;
	}

	/**
	 * Adds a part to an assessment, and adds a random draw to it with count and points
	 * 
	 * @param poolId
	 * @param assmt
	 * @param secEle
	 */
	private void addRandomDraw(String poolId, Assessment assmt, Element secEle)
	{

		Part randomPart = assmt.getParts().addPart();
		Pool pool = poolService.getPool(poolId);
		String countStr = getElementValue(secEle, "selection_ordering/selection/selection_number");
		String gradeStr = getElementValue(secEle, "sectionmetadata/qmd_absolutescore_max");
		if (countStr != null && countStr.length() != 0)
		{
			PoolDraw poolDraw = randomPart.addDrawDetail(pool, Integer.parseInt(countStr));
			if (gradeStr != null && gradeStr.length() != 0) poolDraw.setPoints(Float.parseFloat(gradeStr));
		}
	}
	
	/**
	 * Fetches the settings file name from testRefMap and sends it to process
	 * @param fileName
	 * @param assmt
	 * @return
	 * @throws Exception
	 */
	private Assessment fetchAndProcessSettings(String fileName, Assessment assmt) throws Exception
	{
		if (testRefMap != null && testRefMap.size() > 0)
		{
			if (fileName.endsWith(".dat"))
			{
				String linkIdStr = testRefMap.get(fileName.substring(0, fileName.indexOf(".")));

				if (linkIdStr != null)
				{
					byte[] datFileContents = readBBFile(linkIdStr + ".dat", null);
					if (datFileContents == null || datFileContents.length == 0)
					{
						return assmt;
					}
					assmt = processSettings(datFileContents, assmt);
				}
			}
		}
		return assmt;
	}
	
	/** Iterates through links type documents and generates a map. 
	 * 
	 * @return Map of test file to settings file
	 * @throws Exception
	 */
	private Map generateTestRefMap()  throws Exception
	{
		XPath linkResources = null;
		try
		{
			linkResources = backUpDoc.createXPath("/manifest/resources/resource[@type='resource/x-bb-link']");
		}
		catch (Exception e)
		{
			return null;
		}
		List<Element> linkTestResources = linkResources.selectNodes(backUpDoc);
		if (linkTestResources == null || linkTestResources.size() == 0) return null;
		
		for (Iterator<?> linkIter = linkTestResources.iterator(); linkIter.hasNext();)
		{
			Element linkElement = (Element) linkIter.next();
			byte[] datFileContents = readBBFile(linkElement.attributeValue("file"), linkElement.attributeValue("base"));
			if (datFileContents == null || datFileContents.length == 0)
			{
				continue;
			}
			Document contentsDOM = XMLHelper.getSaxReader().read(new ByteArrayInputStream(datFileContents));
			if (contentsDOM != null)
			{
				Element root = contentsDOM.getRootElement();
				String refIdStr = null;
				String  toIdStr = null;
				
				if (root.selectSingleNode("REFERRER") != null)
				{
					Element refEle = (Element) root.selectSingleNode("REFERRER[@type='CONTENT']");
					if (refEle != null)
					{
						refIdStr = getAttributeValue(refEle, "id");
					}
				}
				if (root.selectSingleNode("REFERREDTO") != null)
				{
					Element toEle = (Element) root.selectSingleNode("REFERREDTO[@type='COURSE_ASSESSMENT']");
					if (toEle != null)
					{
						toIdStr = getAttributeValue(toEle, "id");
					}
				}	
				if ((refIdStr != null) && (refIdStr.length() != 0) && (toIdStr != null) && (toIdStr.length() != 0))
				{
					testRefMap.put(refIdStr,toIdStr);
				}
			}
		}	
		return testRefMap;
	}
	
	/** Check if a test has just one essay question, if so convert it into an assignment
	 * SAK-373
	 * If a test has atlease one question that is an essay
	 * set auto release to false
	 * SAK-415
	 * @param assmt Assessment object
	 * @throws Exception
	 */
	private Assessment checkEssays(Assessment assmt) throws Exception
	{
		Integer numParts = assmt.getParts().getNumParts();
		if ((numParts == null) || (numParts.intValue() == 0)) return assmt;
		//Checking to see if this is a one essay test
		//If so, set it to manual release and assignment
		if (numParts.intValue() == 1)
		{
			Integer numQuestions = assmt.getParts().getFirst().getNumQuestions();
			if ((numQuestions == null) || (numQuestions.intValue() == 0))
			{
				return assmt;
			}
			if (numQuestions.intValue() == 1)
			{
				if (assmt.getParts().getFirst().getFirstQuestion().getType().equals("mneme:Essay"))
				{
					assmt.setType(AssessmentType.assignment);
					assmt.getGrading().setAutoRelease(new Boolean(false));
					return assmt;
				}
			}
		}
		
		boolean oneEssay = false;
		for (Iterator<?> partIter = assmt.getParts().getParts().iterator(); partIter.hasNext();)
		{
			Part part = (Part) partIter.next();
			for (Iterator<?> pIter = part.getDetails().iterator(); pIter.hasNext();)
			{
				PartDetail pDetail = (PartDetail) pIter.next();
				//Check if there is an essay in manual part
				if (pDetail instanceof QuestionPick)
				{
					Question question = (Question) ((QuestionPick)pDetail).getQuestion();
					if (question.getType().equals("mneme:Essay"))
					{
						oneEssay = true;
						assmt.getGrading().setAutoRelease(new Boolean(false));
						return assmt;
					}

				}
				//Check if we are drawing from a pool that contains non-essay questions
				//If not, it means we are drawing from an essay pool
				if (pDetail instanceof PoolDraw)
				{
					if (!nonEssayPools.contains(pDetail.getPoolId()))
					{
						oneEssay = true;
						assmt.getGrading().setAutoRelease(new Boolean(false));
						return assmt;
					}
				}
			}
		}
		//Set auto release to true only if test contains no essay questions
		//and no draws from pools that contain only essays
		if (!oneEssay) 
		{
			assmt.getGrading().setAutoRelease(new Boolean(true));
		}
		return assmt;
	}
	
	/**
	 * Check if assessment has 0 points and all its non-essay questions are surveys,
	 * mark this assessment and all its questions as surveys
	 * 
	 * @param assmt
	 * @return
	 * @throws Exception
	 */
	private Assessment checkIfSurvey(Assessment assmt) throws Exception
	{
		if (assmt == null) return assmt;
		if (assmt.getParts().getTotalPoints().floatValue() != 0.0) return assmt;
		boolean allSurveys = true;
		for (Part part : assmt.getParts().getParts())
		{
			for (Question question : part.getQuestions())
			{
				if (!question.getType().equals("mneme:Essay"))
				{
					if (question.getIsSurvey().booleanValue() == false)
					{
						allSurveys = false;
						return assmt;
					}
				}
			}
		}
		if (allSurveys)
		{
			for (Part part : assmt.getParts().getParts())
			{
				for (Question question : part.getQuestions())
				{
					question.setIsSurvey(true);
					questionService.saveQuestion(question);
				}
			}
			assmt.setType(AssessmentType.survey);
		}
		return assmt;
	}
		
	/**
	 * Process assignment dat file
	 * 
	 * @param toolResourceElement
	 * @param datFileContents
	 * @throws Exception
	 */
	private void processBBAsgnmtDatFile(Element toolResourceElement,byte[] datFileContents) throws Exception
	{
		String assmtType;
		String desc;
		Question question;
		Assessment assmt = null;
		Pool newPool = null;
		Part part = null;
		String title = "New Pool";
		boolean poolExists = false;
		boolean assmtExists = false;

		if (datFileContents == null || datFileContents.length == 0) return;
		// get all pools in the from context
		List<Pool> pools = poolService.getPools(siteId);
		List<String> poolNames = getPoolNames(pools);

		List<Assessment> assessments = assessmentService.getContextAssessments(siteId, null, Boolean.FALSE);
		List<String> assmtNames = getAssessmentNames(assessments);

		String assnRefId = toolResourceElement.attributeValue("base");
		Document contentsDOM = XMLHelper.getSaxReader().read(new ByteArrayInputStream(datFileContents));
		if (contentsDOM != null)
		{
			addFileNameElement(contentsDOM, assnRefId);
			
			try
			{
				Element root = contentsDOM.getRootElement();
				// TODO, make the default title of the pool the same as the name of the file
				title = getAttributeValue(root, "TITLE", "value");
				poolExists = checkPoolTitleExists(title, poolNames, siteId);
				assmtExists = checkAssmtTitleExists(title, assmtNames, siteId);
				if (poolExists) return;
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
					
					desc = getElementValue(root, "BODY/TEXT");
					if (desc != null && desc.length() != 0)
					{
						desc = findUploadAndClean(desc, root, "Mneme");
						question.getPresentation().setText(desc);
					}
					questionService.saveQuestion(question);
					
					if (!assmtExists)
					{
						assmt = processDates(datFileContents, assmt, assnRefId);
						part = assmt.getParts().addPart();
						QuestionPick questionPick = part.addPickDetail(question);
						if (assnIdEleMap != null && assnIdEleMap.size() > 0)
						{
							OutcomeValues oVal = (OutcomeValues) assnIdEleMap.get(assnRefId);
							if (oVal != null)
							{
								questionPick.setPoints(oVal.getPoints());
								assmt.setTries(oVal.getTries());
							}
						}

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
				M_log.warn("processBBAsgnmtDatFile permission exception: " + e.toString());
				return;
			}
		}
		return;
	}
	
	/**
	 * Parses gradebook dat file and stores information for assignments and discussions score.
	 * 
	 * @param datFileContents
	 *        Dat file contents
	 * @throws Exception
	 */
	private void processBBGradebookDatFile(byte[] datFileContents) throws Exception
	{
		if (datFileContents == null || datFileContents.length == 0) return;

		Document contentsDOM = XMLHelper.getSaxReader().read(new ByteArrayInputStream(datFileContents));
		if (contentsDOM == null) return;

		Element root = contentsDOM.getRootElement();
		Element outDefsEle = (Element) root.selectSingleNode("OUTCOMEDEFINITIONS");
		if (outDefsEle == null) return;
		for (Iterator<?> iter = outDefsEle.elementIterator("OUTCOMEDEFINITION"); iter.hasNext();)
		{
			Element outDefElement = (Element) iter.next();
			String doctypeVal = getAttributeValue(outDefElement, "SCORE_PROVIDER_HANDLE", "value");
			if (doctypeVal != null && doctypeVal.trim().length() != 0 && doctypeVal.equals("resource/x-bb-assessment"))
			{
				String idVal = getAttributeValue(outDefElement, "ASIDATAID", "value");
				OutcomeValues oVal = processOutDef(outDefElement, "tests");
				if (oVal != null) assnIdEleMap.put(idVal, oVal);
			}
			if (doctypeVal != null && doctypeVal.trim().length() != 0 && doctypeVal.equals("resource/x-bb-assignment"))
			{
				String idVal = getAttributeValue(outDefElement, "CONTENTID", "value");
				OutcomeValues oVal = processOutDef(outDefElement, "assignments");
				if (oVal != null) assnIdEleMap.put(idVal, oVal);
			}
			// for discussions
			if (outDefElement.selectSingleNode("EXTERNALREF") != null)
			{
				String idVal = getAttributeValue(outDefElement, "EXTERNALREF", "value");
				OutcomeValues oVal = processOutDef(outDefElement, "discussions");
				if (oVal != null) discussionGradeMap.put(idVal, oVal);
			}
		}
	}

	/**
	 * Process outcome definition element and assign it to outcome values object
	 * 
	 * @param outDefEle
	 * @param itemType
	 * @return
	 */
	private OutcomeValues processOutDef(Element outDefEle, String itemType)
	{
		if (outDefEle == null) return null;
		OutcomeValues oVal = new OutcomeValues();
		String gradeStr = getAttributeValue(outDefEle, "POINTSPOSSIBLE", "value");
		if (gradeStr != null && gradeStr.length() != 0)
		{
			oVal.setPoints(Float.parseFloat(gradeStr));
		}
		
		String timeDueStr = getAttributeValue(outDefEle, "DATES/DUE", "value");
		if (timeDueStr != null && timeDueStr.length() != 0)
		{
			if (timeDueStr.equals("0"))
				oVal.setDueDate(null);
			else
				oVal.setDueDate(getDateFromString(timeDueStr));
		}
		
		//for discussion tries 
		if (itemType != null && itemType.equals("discussions"))
		{
			String postTriesStr = getAttributeValue(outDefEle, "ACTIVITY_COUNT_COL_DEFS/ACTIVITY_COUNT_COL_DEF[1]/TRIGGER_COUNT", "value");
			if (postTriesStr != null && postTriesStr.length() != 0)
			{
				if (!postTriesStr.equals("0")) oVal.setTries(new Integer(postTriesStr));
			}
			else
				oVal.setTries(0);
		}
		
		if (itemType != null && itemType.equals("assignments"))
		{
			String triesStr = getAttributeValue(outDefEle, "MULTIPLEATTEMPTS", "value");
			if (triesStr != null && triesStr.length() != 0)
			{
				if (!triesStr.equals("0")) oVal.setTries(new Integer(triesStr));
			}
			else
				oVal.setTries(0);
		}
		
		if (itemType != null && itemType.equals("tests"))
		{
			String contentIdStr = getAttributeValue(outDefEle, "CONTENTID", "value");
			if (contentIdStr != null && contentIdStr.length() != 0)
			{
				oVal.setContentId(contentIdStr);
			}	
		}
		
		return oVal;
	}
	
	/**
	 * Determines open, allow until and due dates and assigns them to assessment
	 * 
	 * @param datFileContents
	 * @param assmt
	 * @param refId
	 * @return
	 * @throws Exception
	 */
	private Assessment processDates(byte[] datFileContents, Assessment assmt, String refId) throws Exception
	{
		if (datFileContents == null || datFileContents.length == 0 || assmt == null) return assmt;
		Document contentsDOM = XMLHelper.getSaxReader().read(new ByteArrayInputStream(datFileContents));
		if (contentsDOM != null)
		{

			Element root = contentsDOM.getRootElement();
			if (root.selectSingleNode("DATES") != null)
			{
				String timeAvStr = getAttributeValue(root, "DATES/START", "value");
				if (timeAvStr != null && timeAvStr.length() != 0)
				{
					if (timeAvStr.equals("0"))
						assmt.getDates().setOpenDate(null);
					else
						assmt.getDates().setOpenDate(getDateFromString(timeAvStr));
				}
				timeAvStr = getAttributeValue(root, "DATES/END", "value");
				if (timeAvStr != null && timeAvStr.length() != 0)
				{
					if (timeAvStr.equals("0"))
						assmt.getDates().setAcceptUntilDate(null);
					else
						assmt.getDates().setAcceptUntilDate(getDateFromString(timeAvStr));
				}
			}
		}
		if (assnIdEleMap != null && assnIdEleMap.size() > 0)
		{
			OutcomeValues oVal = (OutcomeValues) assnIdEleMap.get(refId);
			if (oVal != null)
			{
				assmt.getDates().setDueDate(oVal.getDueDate());
			}
		}
		return assmt;
	}
	
	/**
	 * Assigns settings to assessment such as question grouping, password, time limits, multiple attempts
	 * @param datFileContents
	 * @param assmt
	 * @return
	 * @throws Exception
	 */
	private Assessment processSettings(byte[] datFileContents, Assessment assmt) throws Exception
	{
		if (datFileContents == null || datFileContents.length == 0 || assmt == null) return assmt;
		Document contentsDOM = XMLHelper.getSaxReader().read(new ByteArrayInputStream(datFileContents));
		if (contentsDOM != null)
		{
			Element root = contentsDOM.getRootElement();
			String qGroupStr = getAttributeValue(root, "DELIVERYTYPE", "value");
			if (qGroupStr != null && qGroupStr.length() != 0)
			{
				if (qGroupStr.equals("QUESTION_BY_QUESTION")) assmt.setQuestionGrouping(QuestionGrouping.question);
				if (qGroupStr.equals("ALL_AT_ONCE")) assmt.setQuestionGrouping(QuestionGrouping.assessment);
			}
			
			String pword = getAttributeValue(root, "PASSWORD", "value");
			if (pword != null && pword.length() != 0)
			{
				assmt.getPassword().setPassword(pword);
			}
			String tlimitStr = getAttributeValue(root, "TIMELIMIT", "value");
			if (tlimitStr != null && tlimitStr.length() != 0 && !tlimitStr.equals("0"))
			{
				assmt.setTimeLimit(Long.parseLong(tlimitStr) * 60 * 1000);
			}
			String allowMultStr = getAttributeValue(root, "FLAGS/ALLOWMULTIPLEATTEMPTS", "value");
			boolean allowMult = false;
			if (allowMultStr != null && allowMultStr.length() != 0)
			{
				if (allowMultStr.equals("true"))
				{
					allowMult = true;
					assmt.setTries(null);
				}
			}
			
				String triesStr = getAttributeValue(root, "ATTEMPTCOUNT", "value");
				if (triesStr != null && triesStr.length() != 0)
				{
					if (triesStr.equals("0") || triesStr.equals(""))
						assmt.setTries(null);
					else
						assmt.setTries(new Integer(triesStr));
				}
			
			String flexStr = getAttributeValue(root, "FLAGS/ISBACKTRACKPROHIBITED", "value");
			if (flexStr != null && flexStr.length() != 0)
			{
				if (flexStr.equals("true")) assmt.setRandomAccess(false);
				else assmt.setRandomAccess(true);
			}
			String randStr = getAttributeValue(root, "FLAGS/RANDOMIZEQUESTIONS", "value");
			if (randStr != null && randStr.length() != 0)
			{
				for (Part part : assmt.getParts().getParts())
				{
					part.setRandomize(new Boolean(randStr).booleanValue());
				}
			}

		}

		return assmt;
	}
	
	
	/**
	 * Processes the item element, invokes methods depending on question type, and populates map 
	 * with question id
	 * @param itemEle
	 * @param pool
	 * @return
	 * @throws AssessmentPermissionException
	 */
	private Question processItemElement(Element itemEle, String qtype, Pool pool) throws AssessmentPermissionException
	{
		Question question = null;
		if (M_log.isDebugEnabled()) M_log.debug("Entering processItemElement(Question)...");

		if (itemEle == null) return question;

		if (qtype == null) qtype = getElementValue(itemEle, "itemmetadata/bbmd_questiontype");
		if (qtype == null || qtype.length() == 0) return question;

		if (qtype.equals("Multiple Choice") || qtype.equalsIgnoreCase("QUESTION_MULTIPLECHOICE"))
		{
			MultipleChoiceQuestion mcQuestion = questionService.newMultipleChoiceQuestion(pool);
			mcQuestion.setSingleCorrect(true);
			if(qtype.equalsIgnoreCase("QUESTION_MULTIPLECHOICE")) question = buildPoolMCQuestion(itemEle, mcQuestion);
			else question = buildMultipleChoiceQuestion(itemEle, mcQuestion);
		}
		if (qtype.equals("Multiple Answer"))
		{
			MultipleChoiceQuestion mcQuestion = questionService.newMultipleChoiceQuestion(pool);
			mcQuestion.setSingleCorrect(false);
			question = buildMultipleChoiceQuestion(itemEle, mcQuestion);
		}
		if (qtype.equals("Opinion Scale"))
		{
			MultipleChoiceQuestion mcQuestion = questionService.newMultipleChoiceQuestion(pool);
			mcQuestion.setSingleCorrect(false);
			mcQuestion.setIsSurvey(new Boolean(true));
			question = buildMultipleChoiceQuestion(itemEle, mcQuestion);
		}
		if (qtype.equals("Essay") || qtype.equals("Short Response"))
		{
			EssayQuestion essayQuestion = questionService.newEssayQuestion(pool);
			essayQuestion.setSubmissionType(EssayQuestion.EssaySubmissionType.inline);
			question = buildEssayQuestion(itemEle, essayQuestion);
		}
		if (qtype.equals("File Upload"))
		{
			EssayQuestion essayQuestion = questionService.newEssayQuestion(pool);
			essayQuestion.setSubmissionType(EssayQuestion.EssaySubmissionType.attachments);
			question = buildEssayQuestion(itemEle, essayQuestion);
		}
		if (qtype.equals("Fill in the Blank"))
		{
			String longAnswer = checkLongAnswer(itemEle);
			if (longAnswer == null)
			{
				FillBlanksQuestion fillBlanksQuestion = questionService.newFillBlanksQuestion(pool);
				question = buildFillBlanksQuestion(itemEle, fillBlanksQuestion);
			}
			else
			{
				EssayQuestion essayQuestion = questionService.newEssayQuestion(pool);
				essayQuestion.setSubmissionType(EssayQuestion.EssaySubmissionType.inline);
				essayQuestion.setFeedback(longAnswer);
				question = buildEssayQuestion(itemEle, essayQuestion);
			}
		}
		if (qtype.equals("Numeric"))
		{
			FillBlanksQuestion numFillBlanksQuestion = questionService.newFillBlanksQuestion(pool);
			numFillBlanksQuestion.setResponseTextual("false");
			question = buildFillBlanksQuestion(itemEle, numFillBlanksQuestion);
		}
		if (qtype.equals("Fill in the Blank Plus"))
		{
			FillBlanksQuestion fillBlanksQuestion = questionService.newFillBlanksQuestion(pool);
			question = buildFillMultipleBlanksQuestion(itemEle, fillBlanksQuestion);
		}
		if (qtype.equals("True/False"))
		{
			TrueFalseQuestion tfQuestion = questionService.newTrueFalseQuestion(pool);
			question = buildTrueFalseQuestion(itemEle, tfQuestion);
		}
		if (qtype.equals("Either/Or"))
		{
			boolean tfType = checkTfType(itemEle);
			if (tfType)
			{
				TrueFalseQuestion tfQuestion = questionService.newTrueFalseQuestion(pool);
				question = buildTrueFalseQuestion(itemEle, tfQuestion);
			}
			else
			{
				MultipleChoiceQuestion mcQuestion = questionService.newMultipleChoiceQuestion(pool);
				mcQuestion.setSingleCorrect(true);
				question = buildTrueMultipleChoiceQuestion(itemEle, mcQuestion);
			}
		}
		if (qtype.equals("Matching"))
		{
			MatchQuestion mQuestion = questionService.newMatchQuestion(pool);
			question = buildMatchQuestion(itemEle, mQuestion);
		}
		if (question != null)
		{
			// If there is atleast one question that is of non-essay type
			// add pool to non-essay pool list
			if (!qtype.equals("Essay") && !qtype.equals("Short Response") && !qtype.equals("File Upload"))
			{
				if (!nonEssayPools.contains(pool.getId())) nonEssayPools.add(pool.getId());
			}
			bbqidQuestionMap.put(getElementValue(itemEle, "itemmetadata/bbmd_asi_object_id"), question.getId());
		}

		if (M_log.isDebugEnabled()) M_log.debug("Exiting processItemElement...");
		return question;
	}
	
	/**
	 * Reads file from blackboard package
	 * 
	 * @param fileName
	 *        The file name
	 * @param baseName
	 *        folder name
	 * @return the contents as byte array
	 * 
	 * @throws Exception
	 */
	private byte[] readBBFile(String fileName, String baseName) throws Exception
	{
		File fileUploadResource = null;
		if (baseName != null)
		{
			fileUploadResource = new File(unzipBackUpLocation + File.separator + baseName);
			if (fileUploadResource.isDirectory())
			{
				fileUploadResource = new File(unzipBackUpLocation + File.separator + baseName + File.separator + fileName);
				if (fileUploadResource != null && !fileUploadResource.exists()) fileUploadResource = null;
			}
			else
				fileUploadResource = null;
		}
		if (fileUploadResource == null) fileUploadResource = new File(unzipBackUpLocation + File.separator + fileName);
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
	 * Creates pools with categories, if any
	 * @param poolNames
	 */
	private void createPoolCategories(List<String> poolNames)
	{
		processPoolCategories(poolNames);
		processItemCategories();
	}
	
	
	/**
	 * Iterates through categories file and creates a pool for each
	 * 
	 * @param poolNames
	 */
	private void processPoolCategories(List<String> poolNames)
	{
		XPath xpathResources = backUpDoc.createXPath("/manifest/resources/resource[@type='course/x-bb-category']");

		// check for resource type to get to pools
		List<Element> poolCategories = xpathResources.selectNodes(backUpDoc);
		if (poolCategories == null)
		{
			return;
		}
		
		for (Iterator<?> iter = poolCategories.iterator(); iter.hasNext();)
		{
			try
			{
				Element resourceElement = (Element) iter.next();

				if (resourceElement == null) continue;

				byte[] datFileContents = readBBFile(resourceElement.attributeValue("file"), resourceElement.attributeValue("base"));
				if (datFileContents == null || datFileContents.length == 0) continue;
				processCategoriesDatFile(datFileContents, poolNames);
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Processes categories dat file
	 * 
	 * @param datFileContents
	 * @param poolNames
	 * @throws Exception
	 */
	private void processCategoriesDatFile(byte[] datFileContents, List<String> poolNames) throws Exception
	{
		Document contentsDOM = XMLHelper.getSaxReader().read(new ByteArrayInputStream(datFileContents));
		if (contentsDOM != null)
		{
			Element root = contentsDOM.getRootElement();
			if (root.selectNodes("CATEGORY") != null && ((List) root.selectNodes("CATEGORY")).size() > 0)
			{
				List<Element> catElements = root.selectNodes("CATEGORY");
				for (Iterator<?> catIter = catElements.iterator(); catIter.hasNext();)
				{
					Element catEle = (Element) catIter.next();

					if (catEle == null) continue;
					processCatElement(catEle, poolNames);
				}
			}
		}
	}
	
	/**
	 * Processes each category element and creates a pool, also populates categoryTitleMap
	 * 
	 * @param catEle
	 * @param poolNames
	 */
	private void processCatElement(Element catEle, List<String> poolNames)
	{
		String poolId;
		if (catEle != null)
		{
			String catTitle = getElementValue(catEle, "TITLE");
			String bbCatId = getAttributeValue(catEle, "id");
			categoryTitleMap.put(bbCatId, catTitle);
			}
			}
	
	/**
	 * Processes item categories to determine question category relationship
	 * 
	 */
	private void processItemCategories()
	{
		XPath xpathResources = backUpDoc.createXPath("/manifest/resources/resource[@type='course/x-bb-itemcategory']");

		List<Element> itemCategories = xpathResources.selectNodes(backUpDoc);
		if (itemCategories == null)
		{
			return;
		}
		
		for (Iterator<?> iter = itemCategories.iterator(); iter.hasNext();)
		{
			try
			{
				Element resourceElement = (Element) iter.next();

				if (resourceElement == null) continue;

				byte[] datFileContents = readBBFile(resourceElement.attributeValue("file"), resourceElement.attributeValue("base"));
				if (datFileContents == null || datFileContents.length == 0) continue;
				processItemCategoriesDatFile(datFileContents);
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
	}

	/**
	 * Processes item categories dat file
	 * 
	 * @param datFileContents
	 * @throws Exception
	 */
	private void processItemCategoriesDatFile(byte[] datFileContents) throws Exception
	{
		Document contentsDOM = XMLHelper.getSaxReader().read(new ByteArrayInputStream(datFileContents));
		if (contentsDOM != null)
		{
			Element root = contentsDOM.getRootElement();
			if (root.selectNodes("ITEMCATEGORY") != null && ((List) root.selectNodes("ITEMCATEGORY")).size() > 0)
			{
				List<Element> itcatElements = root.selectNodes("ITEMCATEGORY");
				for (Iterator<?> catIter = itcatElements.iterator(); catIter.hasNext();)
				{
					Element itcatEle = (Element) catIter.next();

					if (itcatEle == null) continue;
					processItCatElement(itcatEle);
				}
			}
		}
	}

	/**
	 * Processes item category element and populates question title map
	 * @param itCatEle
	 */
	private void processItCatElement(Element itCatEle)
	{
		if (itCatEle != null)
		{
			String bbCatId = getAttributeValue(itCatEle, "CATEGORYID", "value");
			String bbQuestionId = getAttributeValue(itCatEle, "QUESTIONID", "value");
			String title = getTitleForCategory(bbCatId);
			if (title != null)
			{
				questionTitleMap.put(bbQuestionId, title);
			}
		}
	}
	
	/**
	 * Determines title for blackboard category id
	 * @param bbCatId
	 * @return title
	 */
	private String getTitleForCategory(String bbCatId)
	{
		String title = null;
		if ((categoryTitleMap != null) && (categoryTitleMap.size() > 0))
		{
			title = categoryTitleMap.get(bbCatId);
		}
		return title;
	}
	
	/**
	 * Fetch the title for this question
	 * 
	 * @param itemEle
	 * @return the title
	 */
	private String checkCategoryTitle(Element itemEle)
	{
		String catTitle = null;
		String bbQuestionId = getElementValue(itemEle, "itemmetadata/bbmd_asi_object_id");
		if (questionTitleMap != null && questionTitleMap.size() > 0)
		{
			catTitle = questionTitleMap.get(bbQuestionId);
			}
		return catTitle;
		}
	
	/**
	 * Removes empty pools
	 * 
	 */
	private void removeEmptyPools()
	{
		List<Pool> pools = poolService.getPools(siteId);

		if (pools == null || pools.size() == 0)
		{
			return;
		}

		for (Iterator<?> iter = pools.iterator(); iter.hasNext();)
		{
			Pool pool = (Pool) iter.next();
			try
			{
				if (pool.getNumQuestions().intValue() == 0) poolService.removePool(pool);
			}
			catch (AssessmentPermissionException e)
			{
				M_log.warn("removeEmptyPools permission exception: " + e.toString());
				return;
			}
		}
	}
						
	/**
	 * Reads through blackboard files to create pools
	 * 
	 */
	private void transferPools()
	{
		// get all pools in the from context
		List<Pool> pools = poolService.getPools(siteId);
		List<String> poolNames = getPoolNames(pools);

		createPoolCategories(poolNames);
		XPath xpathResources = backUpDoc.createXPath("/manifest/resources/resource[@type='assessment/x-bb-qti-pool'] | /manifest/resources/resource[@type='assessment/x-bb-pool']");

		// check for resource type to get to pools
		List<Element> poolResources = xpathResources.selectNodes(backUpDoc);
		if (poolResources == null)
		{
			return;
		}
		
		for (Iterator<?> iter = poolResources.iterator(); iter.hasNext();)
		{
			try
			{
				Element resourceElement = (Element) iter.next();

				if (resourceElement == null) continue;

				byte[] datFileContents = readBBFile(resourceElement.attributeValue("file"), resourceElement.attributeValue("base"));
				if (datFileContents == null || datFileContents.length == 0) continue;
				processBBAssmtPoolDatFile(resourceElement, datFileContents, poolNames, null);

			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
	}
	
	private void createTestsForPools()
	{
		Assessment assmt = null;
		boolean assmtExists = false;
		Part part = null;

		// get all pools in the from context
		List<Pool> pools = poolService.getPools(siteId);

		List<Assessment> assessments = assessmentService.getContextAssessments(siteId, null, Boolean.FALSE);
		List<String> assmtNames = getAssessmentNames(assessments);

		try
		{
			for (Pool pool : pools)
			{
				String title = pool.getTitle();

				assmtExists = checkAssmtTitleExists(title, assmtNames, siteId);
				if (!assmtExists)
				{
					// create test object
					assmt = assessmentService.newAssessment(siteId);
					assmt.setType(AssessmentType.test);
					assmt.setTitle(title);
					part = assmt.getParts().addPart();
				}

				List<Question> questionsList = questionService.findQuestions(pool, QuestionService.FindQuestionsSort.description_a, null, null, null, null, null, null);

				if (questionsList != null)
				{
					for (Iterator i = questionsList.iterator(); i.hasNext();)
					{
						Question question = (Question) i.next();

						if (question != null)
						{
							if (!assmtExists)
							{
								QuestionPick questionPick = part.addPickDetail(question);
								questionPick.setPoints(new Float("1.00"));
							}
						}
					}
				}
				if (!assmtExists) assessmentService.saveAssessment(assmt);
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	/**
	 * Reads through Blackboard files to create tests
	 */
	private void transferTests()
	{
		XPath xpathResources = backUpDoc.createXPath("/manifest/resources/resource[@type='assessment/x-bb-qti-test' or @type='assessment/x-bb-qti-survey']");

		List<Element> eleResources = xpathResources.selectNodes(backUpDoc);
		if (eleResources == null) return;

		// get all pools in the from context
		List<Pool> pools = poolService.getPools(siteId);
		List<String> poolNames = getPoolNames(pools);

		List<Assessment> assessments = assessmentService.getContextAssessments(siteId, null, Boolean.FALSE);
		List<String> assmtNames = getAssessmentNames(assessments);
		
		try
		{
			testRefMap = generateTestRefMap();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		for (Iterator<?> iter = eleResources.iterator(); iter.hasNext();)
		{
			try
			{
				Element toolResourceElement = (Element) iter.next();
				byte[] datFileContents = readBBFile(toolResourceElement.attributeValue("file"), toolResourceElement.attributeValue("base"));
				if (datFileContents == null || datFileContents.length == 0) continue;
				processBBAssmtPoolDatFile(toolResourceElement, datFileContents, poolNames, assmtNames);
				
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Reads through Blackboard files to create assignments
	 */
	private void transferAssns()
	{
		XPath xpathResources = backUpDoc.createXPath("/manifest/resources/resource[@type='resource/x-bb-document']");

		List<Element> eleResources = xpathResources.selectNodes(backUpDoc);
		if (eleResources == null) return;

		for (Iterator<?> iter = eleResources.iterator(); iter.hasNext();)
		{
			try
			{
				Element toolResourceElement = (Element) iter.next();
				byte[] datFileContents = readBBFile(toolResourceElement.attributeValue("file"), toolResourceElement.attributeValue("base"));
				if (datFileContents == null || datFileContents.length == 0) continue;
				Document contentsDOM = XMLHelper.getSaxReader().read(new ByteArrayInputStream(datFileContents));
				
				Element root = contentsDOM.getRootElement();
				String doctypeVal = getAttributeValue(root, "CONTENTHANDLER", "value");
				if (doctypeVal != null && doctypeVal.length() != 0 && (doctypeVal.equals("resource/x-bb-assignment")||doctypeVal.equals("resource/x-bbpi-selfpeer-type1")||doctypeVal.equals("resource/x-mdb-assignment")))
				{
					processBBAsgnmtDatFile(toolResourceElement, datFileContents);
				}
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Reads through gradebook and transfers it
	 */
	private void transferGradebook()
	{
		XPath xpathResources = backUpDoc.createXPath("/manifest/resources/resource[@type='course/x-bb-gradebook']");

		List<Element> eleResources = xpathResources.selectNodes(backUpDoc);
		if (eleResources == null) return;

		for (Iterator<?> iter = eleResources.iterator(); iter.hasNext();)
		{
			try
			{
				Element toolResourceElement = (Element) iter.next();
				byte[] datFileContents = readBBFile(toolResourceElement.attributeValue("file"), toolResourceElement.attributeValue("base"));
				if (datFileContents == null || datFileContents.length == 0) continue;
				processBBGradebookDatFile(datFileContents);
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
	}


	/**
	 * abstract method implementation to check all <file> tags are brought in
	 */
	protected String checkAllResourceFileTagReferenceTransferred(List<Element> embedFiles, String subFolder, String s, String title)
	{
		if (embedFiles == null || embedFiles.size() == 0) return s;
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

				if (check.lastIndexOf("__xid-") != -1)
				{
					String removeFromCheck = check.substring(check.lastIndexOf("__xid-") + 1);
					removeFromCheck = removeFromCheck.substring(0, removeFromCheck.lastIndexOf("."));
					check = check.replace(removeFromCheck, "");
				}
				check = check.substring(check.lastIndexOf("/") + 1);
				meleteCHService.checkResource(collectionId + check);
			}
			catch (Exception ex)
			{
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
		// for blackboard embedded media
		embeddedSrc = embeddedSrc.replace("../", "");
		embeddedSrc = embeddedSrc.replace("@X@", "/");

		// <img src="@X@EmbeddedFile.location@X@Rule5Dot.GIF"
		if (embeddedSrc.indexOf("/EmbeddedFile.location") != -1)
		{
			embeddedSrc = findFileLocation(embeddedSrc, subFolder, embedFiles, tool);
		}

		// <img src="@X@EmbeddedFile.requestUrlStub@X@@@/891C8077CEFEF5A19BC3F68B87ADFE02/courses/1/201032485/content/_389449_1/embedded/mailenva.gif" />
		// physical file is stored with linkname + name i.e change linkName value otm022511c.mp3 to /csfiles/home_dir/otm022511c__xid-356256_1.mp3

		if (embeddedSrc.indexOf("/EmbeddedFile.requestUrlStub") != -1)
		{
			boolean found = false;
			if (embedContentFiles != null && embedContentFiles.size() != 0)
			{
				for (Element e : embedContentFiles)
				{
					if (embeddedSrc.indexOf(e.selectSingleNode("NAME").getText()) != -1)
					{
						embeddedSrc = findFileNamefromBBDocumentFilesTag(e, subFolder);
						found = true;
						break;
					}
				}
			}

			// <img src= "@X@EmbeddedFile.requestUrlStub@X@bbcswebdav/xid-6527_1" where there is no files tag and physical file is /csfiles/home_dir/binac__xid-6527_1.jpg

			if (embedContentFiles == null || embedContentFiles.size() == 0 || found == false)
			{
				String s = findFileNameWhenNoFilesTag(embeddedSrc, subFolder);
				if (s != null) embeddedSrc = s.replace(unzipBackUpLocation, "");
			}
		}
		
		String[] returnStrings = new String[2];
		// physical location
		returnStrings[0] = embeddedSrc;
		// display name
		returnStrings[1] = embeddedSrc;
		return returnStrings;
	}
	
	/**
	 * If section content has framesets in it then find the frame src and embed the contents of frame src file as contents of section.
	 * 
	 * @param s1
	 *        section content which contains framesets
	 * @param subFolder
	 *        base folder if any
	 * @return
	 */
	private String embedFramesets(String s1, String subFolder)
	{
		try
		{
			Pattern pframeset = Pattern.compile("<frameset\\s+.*?/*>(.*?</frameset>)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);
			Pattern p_srcAttribute = Pattern.compile("\\s*src\\s*=\\s*(\".*?\")", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);

			Matcher m = pframeset.matcher(s1);
			StringBuffer sb = new StringBuffer();
			while (m.find())
			{
				String a_content = m.group(0);
				Matcher m_src = p_srcAttribute.matcher(a_content);
				while (m_src.find())
				{
					String src = m_src.group(1);
					if (src.length() <= 0) continue;

					// src is the link name like 01intro.html
					src = src.replaceAll("\"", "");
					String res_mime_type = src.substring(src.lastIndexOf(".") + 1);
					res_mime_type = ContentTypeImageService.getContentType(res_mime_type);
					if (!"text/html".equals(res_mime_type)) continue;

					src = src.substring(0, src.lastIndexOf("."));
					// look for the file which contains src name
					byte[] content_data = null;
					String fileName = findFileNameWhenNoFilesTag(src, subFolder);

					// read its contents
					if (fileName != null && fileName.length() > 0)
					{
						File f = new File(fileName);
						content_data = readDatafromFile(f);
					}
					// append contents to sb
					if (content_data != null && content_data.length > 0)
					{
						String s = new String(content_data, "UTF-8");
						sb = sb.append(s);
					}
				}
			}
			if (sb.length() > 0) return sb.toString();
		}
		catch (Exception e)
		{
			M_log.debug("error in processing frameset:" + e.getMessage());
		}
		return s1;
	}
}
