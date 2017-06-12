/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/siteimport/trunk/siteimport-webapp/webapp/src/java/org/etudes/siteimport/webapp/ImportOtherLMSMoodle.java $
 * $Id: ImportOtherLMSMoodle.java 5936 2013-09-13 22:14:18Z ggolden $
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

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.XPath;
import org.etudes.api.app.jforum.Topic;
import org.etudes.api.app.melete.ModuleObjService;
import org.etudes.api.app.melete.SectionObjService;
import org.etudes.api.app.melete.exception.MeleteException;
import org.etudes.mneme.api.Assessment;
import org.etudes.mneme.api.AssessmentPermissionException;
import org.etudes.mneme.api.AssessmentPolicyException;
import org.etudes.mneme.api.AssessmentType;
import org.etudes.mneme.api.EssayQuestion;
import org.etudes.mneme.api.EssayQuestion.EssaySubmissionType;
import org.etudes.mneme.api.MatchQuestion;
import org.etudes.mneme.api.MatchQuestion.MatchChoice;
import org.etudes.mneme.api.MultipleChoiceQuestion;
import org.etudes.mneme.api.Part;
import org.etudes.mneme.api.Pool;
import org.etudes.mneme.api.PoolDraw;
import org.etudes.mneme.api.Question;
import org.etudes.mneme.api.QuestionPick;
import org.etudes.mneme.api.TrueFalseQuestion;
import org.etudes.util.HtmlHelper;

public class ImportOtherLMSMoodle extends BaseImportOtherLMS
{
	private class CountPoints
	{
		private int count;
		private float points;

		public CountPoints(int count, float points)
		{
			this.count = count;
			this.points = points;
		}

		public int getCount()
		{
			return this.count;
		}

		public float getPoints()
		{
			return this.points;
		}
	}

	private class MnemeQuestionPool
	{
		private String mnemeQuestionId = null;
		private String poolId = null;

		public MnemeQuestionPool(String mnemeQuestionId, String poolId)
		{
			this.mnemeQuestionId = mnemeQuestionId;
			this.poolId = poolId;
		}

		public String getMnemeQuestionId()
		{
			return this.mnemeQuestionId;
		}

		public String getPoolId()
		{
			return this.poolId;
		}
	}

	private class QuestionPoints
	{
		private String questionId = null;
		private float points;

		public QuestionPoints(String questionId, float points)
		{
			this.questionId = questionId;
			this.points = points;
		}

		public float getPoints()
		{
			return this.points;
		}

		public String getQuestionId()
		{
			return this.questionId;
		}
	}

	

	private Document backUpDoc;
	
	private List<QuestionPoints> questionPointList = new ArrayList<QuestionPoints>();

	private Map<String, MnemeQuestionPool> mdlMnemeMap = new LinkedHashMap<String, MnemeQuestionPool>();

	/**
	 * 
	 * @param backUpDoc
	 * @param qtiDoc
	 * @param unzipBackUpLocation
	 * @param unzipLTILocation
	 */
	public ImportOtherLMSMoodle(Document backUpDoc, String unzipBackUpLocation, String siteId, String userId)
	{
		super(siteId,unzipBackUpLocation,userId);
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
		String success_message = "You have successfully imported material from the Moodle backup file.";
		XPath xpath = backUpDoc.createXPath("/MOODLE_BACKUP/INFO/DETAILS");
		List allModsList = bkRoot.selectNodes("/MOODLE_BACKUP/COURSE/MODULES/MOD");
		List allSectionsList = bkRoot.selectNodes("/MOODLE_BACKUP/COURSE/SECTIONS/SECTION/MODS/MOD");

		// check for mod type to direct to right tools
		Element eleOrg = (Element) xpath.selectSingleNode(backUpDoc);
		if (eleOrg == null) return "";
		initializeServices();
		setBaseFolder(unzipBackUpLocation + File.separator + "course_files");
		
		// get all the assessments in the from context
		List<Assessment> assessments = assessmentService.getContextAssessments(siteId, null, Boolean.FALSE);
		List<String> assmtNames = getAssessmentNames(assessments);
		// get all pools in the from context
		List<Pool> pools = poolService.getPools(siteId);
		List<String> poolNames = getPoolNames(pools);
	
		// step 2: find the class discussions forum
		int forumId = 0;
		org.etudes.api.app.jforum.User u = null;
		List<Topic> allDiscussions = null;

		transferQuestionCategories(poolNames);

		for (Iterator<?> iter = eleOrg.elementIterator("MOD"); iter.hasNext();)
		{
			Element detailsModElement = (Element) iter.next();
			// check if this backup file has content for the tool. if no, skip it.
			Element checkIfDataIncluded = detailsModElement.element("INCLUDED");
			if (checkIfDataIncluded.getText().equals("false")) continue;

			// read MOD/NAME element to find out which tool content to bring in
			Element checkModName = detailsModElement.element("NAME");
			String modName = checkModName.getText();

			// list of instance elements for lookup under COURSE/MODULES or COURSE/SECTIONS tag
			List instances = detailsModElement.selectNodes("INSTANCES/INSTANCE");
			if (instances == null || instances.size() == 0) continue;

			// list of COURSE/MODULES elements in accordance to instances
			List<Element> elements = findModuleElements(instances, allModsList);
			if (elements.size() == 0) continue;

			// transfer assignment content

			if ("assignment".equals(modName))
			{
				for (Element m : elements)
				{
					buildAssignment(m, assmtNames, poolNames);
				}
			}
			// transfer mneme content
			if ("quiz".equals(modName))
			{
				for (Element m : elements)
				{
					buildTest(m, assmtNames/* , poolNames */);
				}
			}

			// transfer jforum discussions and announcements
			if ("forum".equals(modName))
			{
				for (Element m : elements)
				{
					// read MOD/TYPE to further classify announcements and jforum discussions
					String modType = null;
					Element checkModType = m.element("TYPE");
					if (checkModType != null) modType = checkModType.getText();

					if ("news".equals(modType))
						buildAnnouncement(m);
					else
					{
						if (forumId == 0)
						{
							forumId = getClassDiscussionsForum();
							u = jForumUserService.getBySakaiUserId(userId);
						}
						if (allDiscussions == null) allDiscussions = jForumPostService.getForumTopics(forumId);

						if (u != null && forumId != 0) buildDiscussions(m, forumId, u, allDiscussions);
					}
				}
			}
		}
		
		int mainCategoryId = buildDiscussionCategory();
		checkClassDiscussionsForum(mainCategoryId);
		 
		// transfer melete content
		buildMelete(bkRoot, allModsList);
		
		// import unreferred files to meleteDocs collection
		transferExtraCourseFiles(new File(unzipBackUpLocation + File.separator + "course_files"), null, "");

		return success_message;
	}

	/**
	 * Parse the element to get announcement body and subject
	 * 
	 * @param annc_instance
	 */
	private void buildAnnouncement(Element annc_instance)
	{
		try
		{
			String subject = null;
			String body = null;
			if (annc_instance.selectSingleNode("NAME") != null)
				subject = annc_instance.selectSingleNode("NAME").getText();
			else
				return;

			if (annc_instance.selectSingleNode("INTRO") != null) body = annc_instance.selectSingleNode("INTRO").getText();

			buildAnnouncement(subject, body, null, null, null);
		}
		catch (Exception e)
		{
			M_log.warn("buildAnnouncement: " + e);
			e.printStackTrace();
		}
	}

	/**
	 * 
	 * @param assmt_instance
	 * @param assmtNames
	 * @param poolNames
	 * @return
	 */
	private boolean buildAssignment(Element assmt_instance, List<String> assmtNames, List<String> poolNames)
	{
		Pool pool = null;
		Assessment assmt = null;
		Question question = null;
		boolean assgnmtExists = false;
		boolean poolExists = false;
		if (M_log.isDebugEnabled()) M_log.debug("Entering buildAssignment...");
		
		if (assmt_instance.elements("NAME") != null && assmt_instance.elements("NAME").size() != 0)
		{
			Element titleEle = (Element) assmt_instance.elements("NAME").get(0);
			if (titleEle != null)
			{
				String title = titleEle.getTextTrim();
				if (title != null && title.length() != 0)
				{
					assgnmtExists = checkTitleExists(title, assmtNames);
					poolExists = checkTitleExists(title, poolNames);
				}
			}
		}
		if (assgnmtExists && poolExists) return true;
		try
		{
			// create the pool
			if (!poolExists) pool = poolService.newPool(siteId);

			if (!assgnmtExists)
			{
				// create assignment object
				assmt = assessmentService.newAssessment(siteId);
				assmt.setType(AssessmentType.assignment);
			}

			if (assmt_instance.elements("NAME") != null && assmt_instance.elements("NAME").size() != 0)
			{
				Element titleEle = (Element) assmt_instance.elements("NAME").get(0);
				if (titleEle != null)
				{
					String title = titleEle.getTextTrim();
					if (title != null && title.length() != 0)
					{
						if (!poolExists) pool.setTitle(title);
						if (!assgnmtExists) assmt.setTitle(title);
					}
				}
			}

			if (!poolExists)
			{
				poolService.savePool(pool);

				if (assmt_instance.elements("ASSIGNMENTTYPE") != null && assmt_instance.elements("ASSIGNMENTTYPE").size() != 0)
				{
					Element assmtTypeEle = (Element) assmt_instance.elements("ASSIGNMENTTYPE").get(0);
					if (assmtTypeEle != null)
					{
						String assmtType = assmtTypeEle.getTextTrim();
						if (assmtType != null && assmtType.length() != 0)
						{
							if (assmtType.equals("offline"))
							{
								question = questionService.newTaskQuestion(pool);
//								((EssayQuestion) question).setSubmissionType(EssayQuestion.EssaySubmissionType.none);
							}
							else
							{
								question = questionService.newEssayQuestion(pool);
								if (assmtType.equals("online"))
								{
									((EssayQuestion) question).setSubmissionType(EssayQuestion.EssaySubmissionType.inline);
								}
								else
								{
									((EssayQuestion) question).setSubmissionType(EssayQuestion.EssaySubmissionType.attachments);
								}
							}
						}
					}
				}
				if (assmt_instance.elements("DESCRIPTION") != null && assmt_instance.elements("DESCRIPTION").size() != 0)
				{
					Element descEle = (Element) assmt_instance.elements("DESCRIPTION").get(0);
					if (descEle != null)
					{
						String desc = descEle.getTextTrim();
						if (desc != null && desc.length() != 0)
						{
							desc = findAndUploadEmbeddedMedia(desc, null, null, null, null, "Mneme");
							desc = fixDoubleQuotes(desc);
							desc = HtmlHelper.cleanAndAssureAnchorTarget(desc, true);
							question.getPresentation().setText(desc);
						}
					}
				}
				questionService.saveQuestion(question);
			}

			if (!assgnmtExists && !poolExists)
			{
				if (assmt_instance.elements("RESUBMIT") != null && assmt_instance.elements("RESUBMIT").size() != 0)
				{
					Element triesEle = (Element) assmt_instance.elements("RESUBMIT").get(0);
					if (triesEle != null)
					{
						String tries = triesEle.getTextTrim();
						if (tries != null && tries.length() != 0)
						{
							if (tries.equals("1")) assmt.setTries(new Integer(2));
						}
					}
				}
				if (assmt_instance.elements("TIMEAVAILABLE") != null && assmt_instance.elements("TIMEAVAILABLE").size() != 0)
				{
					Element timeAvEle = (Element) assmt_instance.elements("TIMEAVAILABLE").get(0);
					if (timeAvEle != null)
					{
						String timeAvStr = timeAvEle.getTextTrim();
						if (timeAvStr != null && timeAvStr.length() != 0)
						{
							if (timeAvStr.equals("0"))
								assmt.getDates().setOpenDate(null);
							else
								assmt.getDates().setOpenDate(getDateTime(timeAvStr));
						}
					}
				}
				if (assmt_instance.elements("TIMEDUE") != null && assmt_instance.elements("TIMEDUE").size() != 0)
				{
					Element timeDueEle = (Element) assmt_instance.elements("TIMEDUE").get(0);
					if (timeDueEle != null)
					{
						String timeDueStr = timeDueEle.getTextTrim();
						if (timeDueStr != null && timeDueStr.length() != 0)
						{
							if (timeDueStr.equals("0"))
								assmt.getDates().setDueDate(null);
							else
								assmt.getDates().setDueDate(getDateTime(timeDueStr));
							if (assmt_instance.elements("PREVENTLATE") != null && assmt_instance.elements("PREVENTLATE").size() != 0)
							{
								Element prevEle = (Element) assmt_instance.elements("PREVENTLATE").get(0);
								if (prevEle != null)
								{
									String prevStr = prevEle.getTextTrim();
									if (prevStr != null && prevStr.length() != 0)
									{
										if (prevStr.equals("0") && !timeDueStr.equals("0"))
										{
											GregorianCalendar gc1 = new GregorianCalendar();
											gc1.setTime(getDateTime(timeDueStr));
											gc1.add(java.util.Calendar.DATE, 2);
											assmt.getDates().setAcceptUntilDate(gc1.getTime());
										}
									}
								}
							}
						}
					}
				}

				Part part = assmt.getParts().addPart();
				QuestionPick questionPick = part.addPickDetail(question);

				if (assmt_instance.elements("GRADE") != null && assmt_instance.elements("GRADE").size() != 0)
				{
					Element gradeEle = (Element) assmt_instance.elements("GRADE").get(0);
					if (gradeEle != null)
					{
						String gradeStr = gradeEle.getTextTrim();
						if (gradeStr != null && gradeStr.length() != 0)
						{
							questionPick.setPoints(Float.parseFloat(gradeStr));
						}
					}
				}

				try
				{
					assmt.getGrading().setGradebookIntegration(Boolean.TRUE);
					if (assmt.getParts().getTotalPoints().floatValue() <= 0)
					{
						assmt.setNeedsPoints(Boolean.FALSE);
					}
					assessmentService.saveAssessment(assmt);
				}
				catch (AssessmentPolicyException ep)
				{
					M_log.warn("buildAssignment policy exception: " + ep.toString());
					return false;
				}
			}
		}
		catch (AssessmentPermissionException e)
		{
			M_log.warn("buildAssignment permission exception: " + e.toString());
			return false;
		}

		if (M_log.isDebugEnabled()) M_log.debug("Exiting buildAssignment...");
		return true;

	}

	private Question buildCommonElements(Element questionEle, Question question)
	{
		if (questionEle != null)
		{
			boolean qtextEmptyFlag = false;
			if (questionEle.elements("QUESTIONTEXT") != null && questionEle.elements("QUESTIONTEXT").size() != 0)
			{
				Element qtextEle = (Element) questionEle.elements("QUESTIONTEXT").get(0);
				if (qtextEle != null)
				{
					String qtext = qtextEle.getTextTrim();
					if (qtext != null && qtext.length() != 0)
					{
						qtext = findUploadAndClean(qtext, null, "Mneme");
						question.getPresentation().setText(buildImageElement(questionEle, qtext));
					}
					else
					{
						qtextEmptyFlag = true;
					}
				}
				else
				{
					qtextEmptyFlag = true;
				}
			}
			else
			{
				qtextEmptyFlag = true;
			}
			// Use NAME element info if QUESTIONTEXT is empty
			if (qtextEmptyFlag)
			{
				if (questionEle.elements("NAME") != null && questionEle.elements("NAME").size() != 0)
				{
					Element nameEle = (Element) questionEle.elements("NAME").get(0);
					if (nameEle != null)
					{
						String name = nameEle.getTextTrim();
						if (name != null && name.length() != 0)
						{
							question.getPresentation().setText(buildImageElement(questionEle, name));
						}
					}
				}
			}
			if (questionEle.elements("GENERALFEEDBACK") != null && questionEle.elements("GENERALFEEDBACK").size() != 0)
			{
				Element gfeedEle = (Element) questionEle.elements("GENERALFEEDBACK").get(0);
				if (gfeedEle != null)
				{
					String gfeed = gfeedEle.getTextTrim();
					if (gfeed != null && gfeed.length() != 0)
					{
						gfeed = findUploadAndClean(gfeed, null, "Mneme");
						question.setFeedback(gfeed);
					}
				}
			}
		}
		return question;
	}

	/**
	 * Create re-usable topics into Class discussions forum.
	 * 
	 * @param jforum_instance
	 *        The MOD Element
	 * @param forum_id
	 *        Class discussions forum Id
	 * @param postedBy
	 */
	private void buildDiscussions(Element jforum_instance, int forum_id, org.etudes.api.app.jforum.User postedBy, List<Topic> all)
	{
		try
		{
			if (forum_id == 0) return;

			// step 4 : build topic from element
			String subject = null;
			String body = null;
			String gradePoints = null;
			Date openDate = null;
			Date dueDate = null;
			int minPosts = 0;

			if (jforum_instance.selectSingleNode("NAME") != null)
				subject = jforum_instance.selectSingleNode("NAME").getText();
			else
				return;

			if (jforum_instance.selectSingleNode("INTRO") != null) body = jforum_instance.selectSingleNode("INTRO").getText();

			if (jforum_instance.selectSingleNode("ASSESSED") != null)
			{
				int points = 0;
				int scale = 0;
				String value = jforum_instance.selectSingleNode("ASSESSED").getText();
				if (value != null && value.length() != 0) points = Integer.parseInt(value);

				String scaleValue = jforum_instance.selectSingleNode("SCALE").getText();
				if (scaleValue != null && scaleValue.length() != 0) scale = Integer.parseInt(scaleValue);

				if (points > 0 && scale > 0)
				{
					gradePoints = scaleValue;
				}

				String oDate = jforum_instance.selectSingleNode("ASSESSTIMESTART").getText();
				if (oDate != null && oDate.length() != 0 && !oDate.equals("0")) openDate = getDateTime(oDate);
				
				String dDate = jforum_instance.selectSingleNode("ASSESSTIMEFINISH").getText();
				if (dDate != null && dDate.length() != 0 && !dDate.equals("0")) dueDate = getDateTime(dDate);
			}
			// build discussions
			buildDiscussionTopics(subject, body,"Normal", null, null, null, gradePoints, openDate, dueDate, minPosts, forum_id, postedBy, all);
		}
		catch (Exception e)
		{
			M_log.warn("buildDiscussions: " + e);
			e.printStackTrace();
		}
	}

	private Question buildEssayQuestion(Element questionEle, EssayQuestion essayQuestion, Pool pool)
	{
		if (M_log.isDebugEnabled()) M_log.debug("Entering buildEssayQuestion...");

		if (questionEle != null)
		{
			essayQuestion = (EssayQuestion) buildCommonElements(questionEle, (Question) essayQuestion);

			essayQuestion.setSubmissionType(EssaySubmissionType.inline);

			if (questionEle.selectSingleNode("ANSWERS/ANSWER[FRACTION=\'1\']") != null)
			{
				Element ansEle = (Element) questionEle.selectSingleNode("ANSWERS/ANSWER[FRACTION=\'1\']");
				if (ansEle.elements("ANSWER_TEXT") != null && ansEle.elements("ANSWER_TEXT").size() != 0)
				{
					Element ansTextEle = (Element) ansEle.elements("ANSWER_TEXT").get(0);
					if (ansTextEle != null)
					{
						String ansText = ansTextEle.getTextTrim();
						if (ansText != null && ansText.length() != 0)
						{
							essayQuestion = buildEssayQuestion(essayQuestion, null, ansText);
						}
					}
				}
			}
			else
			{
				M_log.warn("Essay/Short Answer Question's answer doesn't exist");
			}
		}
		if (M_log.isDebugEnabled()) M_log.debug("Exiting buildEssayQuestion...");
		return essayQuestion;

	}

	private String buildImageElement(Element questionEle, String qtext)
	{
		String newQtext = qtext;
		if (questionEle.elements("IMAGE") != null && questionEle.elements("IMAGE").size() != 0)
		{
			Element imgEle = (Element) questionEle.elements("IMAGE").get(0);
			if (imgEle != null)
			{
				String imgName = imgEle.getTextTrim();
				if (imgName != null && imgName.length() != 0)
				{
					newQtext = processEmbed(imgName, newQtext);
				}
			}
		}
		return newQtext;
	}

	private Question buildMatchQuestion(Element questionEle, MatchQuestion mQuestion, Pool pool)
	{
		if (M_log.isDebugEnabled()) M_log.debug("Entering buildMatchQuestion...");

		if (questionEle != null)
		{
			mQuestion = (MatchQuestion) buildCommonElements(questionEle, (Question) mQuestion);

			if (questionEle.selectSingleNode("MATCHS") != null)
			{
				Element matchsEle = (Element) questionEle.selectSingleNode("MATCHS");
				List matchList = matchsEle.selectNodes("MATCH");
				List<MatchChoice> matchChoices = new ArrayList<MatchChoice>();
				int i = 0;
				for (Iterator matchItr = matchList.iterator(); matchItr.hasNext();)
				{
					Element matchInstance = (Element) matchItr.next();
					Element qTextEle = (Element) matchInstance.selectSingleNode("QUESTIONTEXT");
					Element aTextEle = (Element) matchInstance.selectSingleNode("ANSWERTEXT");
					if ((qTextEle != null) && (aTextEle != null))
					{
						String qText = qTextEle.getTextTrim();
						String aText = aTextEle.getTextTrim();

						if (qText != null && qText.length() != 0 && aText != null && aText.length() != 0)
						{
							matchChoices.add(new MatchChoice(qText, aText));
						}
						if ((qText == null || qText.length() == 0) && aText != null && aText.length() != 0)
						{
							mQuestion.setDistractor(aText);
						}
					}
				}
				if (matchChoices != null && matchChoices.size() > 0) mQuestion.setMatchPairs(matchChoices);
			}
		}
		else
		{
			M_log.warn("Question element doesn't exist");
		}
		if (M_log.isDebugEnabled()) M_log.debug("Exiting buildMatchQuestion...");
		return mQuestion;
	}

	private List<CalculatedDates> getWeekDates(Element bkRoot)
	{
		ArrayList<CalculatedDates> weekDates = new ArrayList<CalculatedDates>();
		try
		{
			// start date of course
			// /MOODLE_BACKUP/COURSE[1]/HEADER[1]/STARTDATE[1]
			String startDate =  bkRoot.selectSingleNode("/MOODLE_BACKUP/COURSE/HEADER/STARTDATE").getText();
			Date sDate = getDateTime(startDate);			
			GregorianCalendar gc1 = new GregorianCalendar();
			gc1.setTime(sDate);

			// no. of weeks
			String countStr = bkRoot.selectSingleNode("/MOODLE_BACKUP/COURSE/HEADER/NUMSECTIONS").getText();
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
	 * Build Melete modules and sections
	 * 
	 * @param bkRoot
	 *  Moodle Root Element
	 * @param allModsList
	 * 	List of all Modules/MOD elements
	 */
	private void buildMelete(Element bkRoot, List<Element> allModsList)
	{
		try
		{
			List<Element> justSectionsList = bkRoot.selectNodes("/MOODLE_BACKUP/COURSE/SECTIONS/SECTION");
			
			List<CalculatedDates> weekDates = getWeekDates(bkRoot);
			// get the existing modules from the site
			List<ModuleObjService> existingModules = moduleService.getModules(siteId);

			for (Element sectionElement : justSectionsList)
			{
				ModuleObjService module = null;

				Element modsofSectionElement = (Element) sectionElement.selectSingleNode("MODS");
				if (modsofSectionElement == null) continue;
				List<Element> modofSectionElement = modsofSectionElement.elements("MOD");
				if (modofSectionElement == null || modofSectionElement.size() == 0) continue;

				// 1. get the instances of book,label,resource
				List<String> modInstances = new ArrayList<String>(0);
				for (Element m : modofSectionElement)
				{
					String type = m.selectSingleNode("TYPE").getText();
					if ("resource".equals(type) || "book".equals(type) || "label".equals(type))
						modInstances.add(m.selectSingleNode("INSTANCE").getText());
				}
				if (modInstances.size() == 0) continue;
				List<Element> modulesModElement = findMeleteModuleElements(modInstances, allModsList);

				// 2. get number and check if module + number exists
				Element number = (Element) sectionElement.selectSingleNode("NUMBER");
				String summaryText = null;
				Date startDate = null;
				Date endDate = null;
				if (sectionElement.selectSingleNode("SUMMARY") != null) summaryText = sectionElement.selectSingleNode("SUMMARY").getText();

				if (number != null)
				{
					int num = Integer.parseInt(number.getTextTrim());
					String checkModuleTitle = "Week " + num;

					if (num == 0)
					{
						checkModuleTitle = "Course Information";
						if (weekDates != null)
						{
							startDate = weekDates.get(0).getStartDate();
						}
					}
					else
					{
						if (weekDates != null)
						{
							startDate = weekDates.get(num - 1).getStartDate();
						}
					}
					module = findOrAddModule(checkModuleTitle, 0, summaryText, startDate, endDate, null, existingModules, null, null, null);
				}
				// 3. create sections
				for (Element m : modulesModElement)
				{
					String checkSectionTitle = m.selectSingleNode("NAME").getText();
					if (checkSectionTitle.contains("Syllabus") || checkSectionTitle.contains("SYLLABUS") || checkSectionTitle.contains("syllabus"))
						buildSyllabus(m);
					else
					{
						// create module with same title as section if no module so far
						if (module == null) module = findOrAddModule(checkSectionTitle, 0, null, startDate, endDate,null, existingModules, null, null, null);
						if (sectionExists(checkSectionTitle, module)) continue;

						String type = m.selectSingleNode("MODTYPE").getText();
						if ("resource".equals(type))buildSection(m, module);
						else if ("book".equals(type))buildSectionFromBook(m, module);
						else buildSectionFromLabel(m, module);
					}
				}
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
		
	private Question buildMultipleChoiceQuestion(Element questionEle, MultipleChoiceQuestion mcQuestion, Pool pool)
	{
		if (M_log.isDebugEnabled()) M_log.debug("Entering buildMultipleChoiceQuestion...");

		if (questionEle != null)
		{
			mcQuestion = (MultipleChoiceQuestion) buildCommonElements(questionEle, (Question) mcQuestion);
			if (questionEle.selectSingleNode("MULTICHOICE") != null)
			{
				Element multEle = (Element) questionEle.selectSingleNode("MULTICHOICE");

				if (multEle.elements("SINGLE") != null && multEle.elements("SINGLE").size() != 0)
				{
					Element msingEle = (Element) multEle.elements("SINGLE").get(0);
					if (msingEle != null)
					{
						String multSing = msingEle.getTextTrim();
						if (multSing != null && multSing.length() != 0)
						{
							if (multSing.equals("1"))
								mcQuestion.setSingleCorrect(true);
							else
								mcQuestion.setSingleCorrect(false);
						}
					}
				}
				if (multEle.elements("SHUFFLEANSWERS") != null && multEle.elements("SHUFFLEANSWERS").size() != 0)
				{
					Element shuffEle = (Element) multEle.elements("SHUFFLEANSWERS").get(0);
					if (shuffEle != null)
					{
						String shuffStr = shuffEle.getTextTrim();
						if (shuffStr != null && shuffStr.length() != 0)
						{
							if (shuffStr.equals("1"))
								mcQuestion.setShuffleChoices(true);
							else
								mcQuestion.setShuffleChoices(false);
						}
					}
				}
			}
			if (questionEle.selectSingleNode("ANSWERS") != null)
			{
				Element answersEle = (Element) questionEle.selectSingleNode("ANSWERS");
				List answersList = answersEle.selectNodes("ANSWER");
				List<String> answerChoices = new ArrayList<String>();
				Set<Integer> correctAnswers = new HashSet<Integer>();
				int i = 0;
				for (Iterator ansItr = answersList.iterator(); ansItr.hasNext();)
				{
					Element ansInstance = (Element) ansItr.next();
					Element ansTextEle = (Element) ansInstance.selectSingleNode("ANSWER_TEXT");
					if (ansTextEle != null)
					{
						String ansText = ansTextEle.getTextTrim();

						if (ansText != null && ansText.length() != 0)
						{
							ansText = findUploadAndClean(ansText, null, "Mneme");
							answerChoices.add(ansText);
						}
					}

					Element fracEle = (Element) ansInstance.selectSingleNode("FRACTION");
					if (fracEle != null)
					{
						String fracStr = fracEle.getTextTrim();

						if (fracStr != null && fracStr.length() != 0)
						{
							if (!fracStr.equals("0"))
							{
								correctAnswers.add(new Integer(i));
							}
						}
					}
					i++;
				}
				if (answerChoices != null && answerChoices.size() > 0) mcQuestion.setAnswerChoices(answerChoices);
				if (correctAnswers != null && correctAnswers.size() > 0) mcQuestion.setCorrectAnswerSet(correctAnswers);
			}
		}
		else
		{
			M_log.warn("Question element doesn't exist");
		}
		if (M_log.isDebugEnabled()) M_log.debug("Exiting buildMultipleChoiceQuestion...");
		return mcQuestion;
	}

	private Assessment buildRandomQuestions(Assessment assmt, List<QuestionPoints> qPointList)
	{
		Pool extraPool = null;
		Question question = null;
		String title = null;
		String poolId = null;
		Map<String, CountPoints> poolCountPointsMap = null;
		
		if (M_log.isDebugEnabled()) M_log.debug("Entering buildRandomQuestions...");
		if (qPointList == null || qPointList.size() == 0) return assmt;

		poolCountPointsMap = new LinkedHashMap<String, CountPoints>();
		// Iterate through each question id in list
		for (ListIterator<QuestionPoints> k = qPointList.listIterator(); k.hasNext();)
		{
			QuestionPoints qPointObj = (QuestionPoints) k.next();
			if (qPointObj != null)
			{
				if (mdlMnemeMap != null && mdlMnemeMap.size() != 0)
				{
					// Get question's pool info and build poolCountPointsMap
					MnemeQuestionPool mqPool = (MnemeQuestionPool) mdlMnemeMap.get(qPointObj.getQuestionId());
					if (mqPool != null)
					{
						poolId = mqPool.getPoolId();
						if (poolId != null)
						{
							if (poolCountPointsMap.get(poolId) == null)
							{
								poolCountPointsMap.put(poolId, new CountPoints(1, qPointObj.getPoints()));
							}
							else
							{
								Iterator mapIt = poolCountPointsMap.entrySet().iterator();
								while (mapIt.hasNext())
								{
									Map.Entry entry = (Map.Entry) mapIt.next();
									if (entry.getKey().equals(poolId))
									{
										CountPoints countPoints = (CountPoints) entry.getValue();
										CountPoints newCountPoints = new CountPoints((int) (countPoints.getCount() + 1),
												(float) (countPoints.getPoints() + qPointObj.getPoints()));
										entry.setValue(newCountPoints);
										break;
									}
								}
							}

						}
					}
				}
			}
		}
		// Iterate through poolCountPointsMap and add draws to quiz
		if (poolCountPointsMap != null && poolCountPointsMap.size() != 0)
		{
			if (assmt != null)
			{
				Iterator drawIt = poolCountPointsMap.entrySet().iterator();
				// Iterate through each pool count entry
				while (drawIt.hasNext())
				{
					Map.Entry drawPairs = (Map.Entry) drawIt.next();
					String drawPoolId = (String) drawPairs.getKey();
					CountPoints drawCountPoints = (CountPoints) drawPairs.getValue();
					Part randomPart = assmt.getParts().addPart();
					Pool pool = poolService.getPool(drawPoolId);
					PoolDraw poolDraw = randomPart.addDrawDetail(pool, drawCountPoints.getCount());
					poolDraw.setPoints(drawCountPoints.getPoints());
				}
			}
		}
		poolCountPointsMap = null;

		if (M_log.isDebugEnabled()) M_log.debug("Exiting buildRandomQuestions...");
		return assmt;
	}

	/**
	 * Create section from mod element of type resource. All sections are of typeCompose. If from backup file we get upload/link then we add summary to the text and URL link to the resource with target as _blank. This way we are avoiding ugly html code to
	 * show in section instructions.
	 * 
	 * @param mod_instance
	 *        MODS/MOD element
	 * @param module
	 *        Melete module where section will be added
	 * @return
	 */
	private SectionObjService buildSection(Element sec_instance, ModuleObjService module)
	{
		try
		{
			String title = "Untitled Section";
			if (sec_instance.selectSingleNode("NAME") != null) title = sec_instance.selectSingleNode("NAME").getText();

			if (sectionExists(title, module)) return null;
			SectionObjService section = buildSection(title, module);
			byte[] content_data = null;
			String content_name = new String();
			String res_mime_type = null;
			String section_content = "";

			Element findContentType = (Element) sec_instance.selectSingleNode("TYPE");
			Element findContentType_1 = (Element) sec_instance.selectSingleNode("REFERENCE");
			Element Content = (Element) sec_instance.selectSingleNode("ALLTEXT");

			// get summary text
			if (sec_instance.selectSingleNode("SUMMARY") != null)
			{
				section_content = sec_instance.selectSingleNode("SUMMARY").getText();
			}

			// typeEditor
			if (findContentType != null && (findContentType.getTextTrim().equals("text") || findContentType.getTextTrim().equals("html"))
					&& Content != null)
			{
				String s1 = Content.getText();
				section_content = section_content.concat("\n <hr/>" + s1);
			}
			// typeUpload/typeLink resource appended as <a> tag
			else if (findContentType != null && findContentType.getTextTrim().equals("file") && findContentType_1 != null)
			{
				String check_name = findContentType_1.getTextTrim();
				section_content = buildTypeLinkUploadResource(check_name, check_name, section_content, null, null);
			}
			//save section
			section = buildTypeEditorSection(section, section_content, module, null, title, null, null);
			return section;
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		return null;
	}	

	/**
	 * 
	 * @param sec_instance
	 * @param module
	 * @return
	 */
	private SectionObjService buildSectionFromBook(Element sec_instance, ModuleObjService module)
	{
		SectionObjService section = null;
		try
		{
			String title = "Untitled Section";
			if (sec_instance.selectSingleNode("NAME") != null) title = sec_instance.selectSingleNode("NAME").getText();

			if (sectionExists(title, module)) return null;
			section = buildSection(title, module);

			// add & store resource and associate with section
			String s = null;
			Element Content = (Element) sec_instance.selectSingleNode("SUMMARY");
			if (Content != null) s = Content.getText();

			// add resource to meleteCHService collection
			if (s != null)
			{
				section = buildTypeEditorSection(section, s, module, null, title, null, null);
			}

			List<Element> chapters = sec_instance.selectNodes("CHAPTERS/CHAPTER");
			if (chapters == null || chapters.size() == 0) return section;

			for (Element chapter : chapters)
			{
				String subSectionTitle = "Untitled Section";
				if (chapter.selectSingleNode("TITLE") != null) subSectionTitle = chapter.selectSingleNode("TITLE").getText();
				if (subSectionTitle.length() == 0) subSectionTitle = "Untitled Section";

				SectionObjService subSection = buildSection(subSectionTitle, module);
				Element subSectionContent = (Element) chapter.selectSingleNode("CONTENT");
				if (subSectionContent != null)
				{
					String s1 = subSectionContent.getText();
					if (s1 != null) subSection = buildTypeEditorSection(subSection, s1, module, null, title, null, null);
					moduleService.createSubSection(module, subSection.getSectionId().toString());
					// if its sub-chapter indent more
					Element subChapter = (Element) chapter.selectSingleNode("SUBCHAPTER");
					if (subChapter != null && !subChapter.getTextTrim().equals("0"))
					{
						int level = Integer.parseInt(subChapter.getTextTrim());
						if (level > 10) level = 10;
						for (int i = 0; i < level; i++)
							moduleService.createSubSection(module, subSection.getSectionId().toString());
					}
				}
			}

			return section;
		}
		catch (MeleteException me)
		{
			me.printStackTrace();
			return section;
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

		return null;
	}

	/**
	 * Create a section from MOD element of type label.
	 * 
	 * @param sec_instance
	 *        MODS/MOD element of COURSE/MODULES.
	 * @param module
	 *        Melete module where section will be added
	 * @return
	 */
	private SectionObjService buildSectionFromLabel(Element sec_instance, ModuleObjService module)
	{
		try
		{
			String title = "Untitled Section";
			if (sec_instance.selectSingleNode("NAME") != null) title = sec_instance.selectSingleNode("NAME").getText();
			title = title.concat(" - Label");
			if (sectionExists(title, module)) return null;
			SectionObjService section = buildSection(title, module);

			// add & store resource and associate with section
			String s = null;
			Element Content = (Element) sec_instance.selectSingleNode("CONTENT");
			if (Content != null) s = Content.getText();

			// add resource to meleteCHService collection
			if (s != null)
			{
				section = buildTypeEditorSection(section, s, module, null, title, null, null);
			}
			return section;
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * 
	 * @param syllabus_instance
	 */
	private void buildSyllabus(Element syllabus_instance)
	{
		try
		{
			Element findContentType = (Element) syllabus_instance.selectSingleNode("TYPE");
			Element findContentType_1 = (Element) syllabus_instance.selectSingleNode("REFERENCE");
			Element Content = (Element) syllabus_instance.selectSingleNode("ALLTEXT");
			String check_name = null;
			String asset = "";
			boolean draft = false;
			String title = "Untitled";
			String type = null;
			String[] attach = null;
			if (findContentType_1 != null) check_name = findContentType_1.getTextTrim();

			// 1. if type is text or html then add item
			// 2. if type is file and reference is doc file or something add as attachment and add item
			if ((findContentType != null && (findContentType.getTextTrim().equals("text") || findContentType.getTextTrim().equals("html")) && Content != null)
					|| (check_name != null && !check_name.startsWith("http://") && !check_name.startsWith("https://")))
			{
				type = "item";
				if (Content != null && Content.getText() != null && Content.getText().length() != 0) asset = Content.getText();
			}
			else
				type = "redirectUrl";
	
			if (syllabus_instance.selectSingleNode("NAME") != null) title = syllabus_instance.selectSingleNode("NAME").getText();
			
			if (check_name != null)
			{
				attach = new String[1];
				attach[0]= check_name;
			}
			buildSyllabus(title, asset, draft, type, attach, null, null, null, null);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	/**
	 * 
	 * @param questionEle
	 * @param taskQuestion
	 * @param pool
	 * @return
	 */
	private Question buildTaskQuestion(Element questionEle, Question taskQuestion, Pool pool)
	{
		if (M_log.isDebugEnabled()) M_log.debug("Entering buildTaskQuestion...");

		if (questionEle != null)
		{
			taskQuestion = buildCommonElements(questionEle, taskQuestion);
			// TODO: check to see if there is model answer setting
		}
		if (M_log.isDebugEnabled()) M_log.debug("Exiting buildTaskQuestion...");
		return taskQuestion;

	}

	private boolean buildTest(Element test_instance, List<String> assmtNames/* , List<String> poolNames */)
	{
		Pool pool = null;
		Assessment assmt = null;
		Question question = null;
		Part part = null;
		boolean assmtExists = false;
		if (M_log.isDebugEnabled()) M_log.debug("Entering buildTest...");
	
		questionPointList = null;

		if (test_instance.elements("NAME") != null && test_instance.elements("NAME").size() != 0)
		{
			Element titleEle = (Element) test_instance.elements("NAME").get(0);
			if (titleEle != null)
			{
				String title = titleEle.getTextTrim();
				if (title != null && title.length() != 0)
				{
					assmtExists = checkTitleExists(title, assmtNames);
					// boolean poolExists = checkTitleExists(title, poolNames);
				}
			}
		}
		if (assmtExists) return true;

		try
		{
			// create the pool
			// if (!poolExists) pool = poolService.newPool(siteId);

			if (!assmtExists)
			{
				// create test object
				assmt = assessmentService.newAssessment(siteId);
				assmt.setType(AssessmentType.test);
			}

			if (test_instance.elements("NAME") != null && test_instance.elements("NAME").size() != 0)
			{
				Element titleEle = (Element) test_instance.elements("NAME").get(0);
				if (titleEle != null)
				{
					String title = titleEle.getTextTrim();
					if (title != null && title.length() != 0)
					{
						// if (!poolExists) pool.setTitle(title);
						if (!assmtExists) assmt.setTitle(title);
					}
				}
			}

			if (!assmtExists)
			{
				if (test_instance.elements("INTRO") != null && test_instance.elements("INTRO").size() != 0)
				{
					Element instrEle = (Element) test_instance.elements("INTRO").get(0);
					if (instrEle != null)
					{
						String instr = instrEle.getTextTrim();
						if (instr != null && instr.length() != 0)
						{
							assmt.getPresentation().setText(instr);
						}
					}
				}
				if (test_instance.elements("TIMEOPEN") != null && test_instance.elements("TIMEOPEN").size() != 0)
				{
					Element timeAvEle = (Element) test_instance.elements("TIMEOPEN").get(0);
					if (timeAvEle != null)
					{
						String timeAvStr = timeAvEle.getTextTrim();
						if (timeAvStr != null && timeAvStr.length() != 0)
						{
							if (timeAvStr.equals("0"))
								assmt.getDates().setOpenDate(null);
							else
								assmt.getDates().setOpenDate(getDateTime(timeAvStr));
						}
					}
				}
				if (test_instance.elements("TIMECLOSE") != null && test_instance.elements("TIMECLOSE").size() != 0)
				{
					Element timeDueEle = (Element) test_instance.elements("TIMECLOSE").get(0);
					if (timeDueEle != null)
					{
						String timeDueStr = timeDueEle.getTextTrim();
						if (timeDueStr != null && timeDueStr.length() != 0)
						{
							if (timeDueStr.equals("0"))
								assmt.getDates().setDueDate(null);
							else
								assmt.getDates().setDueDate(getDateTime(timeDueStr));
						}
					}
				}
				if (test_instance.elements("ATTEMPTS_NUMBER") != null && test_instance.elements("ATTEMPTS_NUMBER").size() != 0)
				{
					Element triesEle = (Element) test_instance.elements("ATTEMPTS_NUMBER").get(0);
					if (triesEle != null)
					{
						String tries = triesEle.getTextTrim();
						if (tries != null && tries.length() != 0)
						{
							if (tries.equals("0"))
								assmt.setTries(null);
							else
								assmt.setTries(new Integer(tries));
						}
					}
				}

				if (test_instance.elements("TIMELIMIT") != null && test_instance.elements("TIMELIMIT").size() != 0)
				{
					Element tlimitEle = (Element) test_instance.elements("TIMELIMIT").get(0);
					if (tlimitEle != null)
					{
						String tlimitStr = tlimitEle.getTextTrim();
						if (tlimitStr != null && tlimitStr.length() != 0 && !tlimitStr.equals("0"))
						{
							assmt.setTimeLimit(Long.parseLong(tlimitStr) * 60 * 1000);
						}
					}
				}
				if (test_instance.elements("PASSWORD") != null && test_instance.elements("PASSWORD").size() != 0)
				{
					Element passEle = (Element) test_instance.elements("PASSWORD").get(0);
					if (passEle != null)
					{
						String password = passEle.getTextTrim();
						if (password != null && password.length() != 0)
						{
							assmt.getPassword().setPassword(password);
						}
					}
				}
			}
			// TODO - Ask Vivie what to do if Feedback only comes in for min and max grade.

			boolean questionExists = false;
			boolean partAdded = false;
			/*
			 * if (!poolExists) { poolService.savePool(pool);
			 */

			// list of instance elements for lookup under QUESTION_INSTANCES tag
			List instances = test_instance.selectNodes("QUESTION_INSTANCES/QUESTION_INSTANCE");

			if (instances != null && instances.size() > 0)
			{
				for (Iterator i = instances.iterator(); i.hasNext();)
				{
					Element instance = (Element) i.next();
					if (instance.elements("QUESTION") != null && instance.elements("QUESTION").size() != 0)
					{
						Element idEle = (Element) instance.elements("QUESTION").get(0);
						if (idEle != null)
						{
							String idStr = idEle.getTextTrim();
							if (idStr != null && idStr.length() != 0)
							{
								Element questionEle = findQuestionElement(idStr);
								if (questionEle != null)
								{
									boolean isRandom = processRandomQuestion(idStr, questionEle);
									if (!isRandom)
									{
										MnemeQuestionPool mqPool = (MnemeQuestionPool) mdlMnemeMap.get(idStr);
										if (mqPool != null)
										{
											String mnmQuestionId = mqPool.getMnemeQuestionId();
											if (mnmQuestionId != null)
											{
												question = questionService.getQuestion(mnmQuestionId);
												if (question != null)
												{
													questionExists = true;
													if (!partAdded)
													{
														part = assmt.getParts().addPart();
														if (test_instance.elements("SHUFFLEQUESTIONS") != null
																&& test_instance.elements("SHUFFLEQUESTIONS").size() != 0)
														{
															Element randEle = (Element) test_instance.elements("SHUFFLEQUESTIONS").get(0);
															if (randEle != null)
															{
																String randStr = randEle.getTextTrim();
																if (randStr != null && randStr.length() != 0)
																{
																	if (randStr.equals("1"))
																		part.setRandomize(Boolean.TRUE);
																	else
																		part.setRandomize(Boolean.FALSE);
																}
															}
														}
														partAdded = true;
													}
													if (!assmtExists)
													{
														QuestionPick questionPick = part.addPickDetail(question);

														if (questionEle.elements("DEFAULTGRADE") != null
																&& questionEle.elements("DEFAULTGRADE").size() != 0)
														{
															Element gradeEle = (Element) questionEle.elements("DEFAULTGRADE").get(0);
															if (gradeEle != null)
															{
																String gradeStr = gradeEle.getTextTrim();
																if (gradeStr != null && gradeStr.length() != 0)
																{
																	questionPick.setPoints(Float.parseFloat(gradeStr));
																}
															}
														}
													}
												}
											}
										}
									}
								}
							}
						}
					}
				}

				assmt = buildRandomQuestions(assmt, questionPointList);
			}
			// }
			if (!assmtExists)
			{
				try
				{
					assmt.getGrading().setGradebookIntegration(Boolean.TRUE);

					if (assmt.getParts().getTotalPoints().floatValue() <= 0)
					{
						assmt.setNeedsPoints(Boolean.FALSE);
					}
					// }
					assessmentService.saveAssessment(assmt);
				}
				catch (AssessmentPolicyException ep)
				{
					M_log.warn("buildTest policy exception: " + ep.toString());
					return false;
				}
			}
		}
		catch (AssessmentPermissionException e)
		{
			M_log.warn("buildTest permission exception: " + e.toString());
			return false;
		}

		if (M_log.isDebugEnabled()) M_log.debug("Exiting buildTest...");
		return true;

	}

	private Question buildTrueFalseQuestion(Element questionEle, TrueFalseQuestion tfQuestion, Pool pool)
	{
		if (M_log.isDebugEnabled()) M_log.debug("Entering buildTrueFalseQuestion...");

		if (questionEle != null)
		{
			tfQuestion = (TrueFalseQuestion) buildCommonElements(questionEle, (Question) tfQuestion);

			if (questionEle.selectSingleNode("ANSWERS/ANSWER[FRACTION=\'1\']") != null)
			{
				Element ansEle = (Element) questionEle.selectSingleNode("ANSWERS/ANSWER[FRACTION=\'1\']");
				if (ansEle.elements("ANSWER_TEXT") != null && ansEle.elements("ANSWER_TEXT").size() != 0)
				{
					Element ansTextEle = (Element) ansEle.elements("ANSWER_TEXT").get(0);
					if (ansTextEle != null)
					{
						String ansText = ansTextEle.getTextTrim();
						if (ansText != null && ansText.length() != 0)
						{
							if (ansText.equals("True"))
								tfQuestion.setCorrectAnswer(true);
							else
								tfQuestion.setCorrectAnswer(false);
						}
					}
				}
			}
			else
			{
				M_log.warn("True False Question's answer doesn't exist");
			}
		}
		if (M_log.isDebugEnabled()) M_log.debug("Exiting buildTrueFalseQuestion...");
		return tfQuestion;

	}

	/**
	 * Find the elements containing all contents for a tool.
	 * 
	 * @param instances
	 *        List of instance elements found in info/details
	 * @param allModsList
	 *        List of all mods elements found in course/modules
	 * @return sublist of mods element which corresponds to instances
	 */
	private List<Element> findModuleElements(List instances, List allModsList)
	{
		List<Element> moduleElements = new ArrayList<Element>(0);

		for (Iterator i = instances.iterator(); i.hasNext();)
		{
			Element instance = (Element) i.next();
			Element instance_id = instance.element("ID");
			String id = instance_id.getText();

			for (Iterator l_i = allModsList.iterator(); l_i.hasNext();)
			{
				Element sec_instance = (Element) l_i.next();
				Element sec_id = (Element) sec_instance.selectSingleNode("ID");
				if (sec_id.getText().equals(id))
				{
					moduleElements.add(sec_instance);
					break;
				}
			}
		}
		return moduleElements;
	}

	/**
	 * Find the elements containing all contents for a tool.
	 * 
	 * @param instances
	 *        List of String with instance ids
	 * @param allModsList
	 *        List of all mods elements found in course/modules
	 * @return sublist of mods element which corresponds to instances
	 */
	private List<Element> findMeleteModuleElements(List<String> instances, List<Element> allModsList)
	{
		List<Element> moduleElements = new ArrayList<Element>(0);

		for (String id : instances)
		{
			for (Iterator<Element> l_i = allModsList.iterator(); l_i.hasNext();)
			{
				Element sec_instance = (Element) l_i.next();
				Element sec_id = (Element) sec_instance.selectSingleNode("ID");
				if (sec_id.getText().equals(id))
				{
					moduleElements.add(sec_instance);
					break;
				}
			}
		}
		return moduleElements;
	}
	
	
	private Element findQuestionElement(String idStr)
	{
		Element questionEle = null;
		if (M_log.isDebugEnabled()) M_log.debug("Entering findQuestionElement...");

		if (backUpDoc.selectSingleNode("/MOODLE_BACKUP/COURSE/QUESTION_CATEGORIES/QUESTION_CATEGORY/QUESTIONS/QUESTION[ID=\'" + idStr + "\']") != null)
		{
			questionEle = (Element) backUpDoc.selectSingleNode("/MOODLE_BACKUP/COURSE/QUESTION_CATEGORIES/QUESTION_CATEGORY/QUESTIONS/QUESTION[ID=\'"
					+ idStr + "\']");
		}
		else
		{
			M_log.warn("Question doesn't exist");
		}
		if (M_log.isDebugEnabled()) M_log.debug("Exiting findQuestionElement...");
		return questionEle;
	}

	/**
	 * Find SECTIONS/SECTION/MODS/MOD element to create melete module object.
	 * 
	 * @param instance
	 *        DETAILS/MOD/INSTANCE object
	 * @param allSectionsList
	 *        all SECTIONS/SECTION/MODS/MOD elements
	 * @return
	 */
	private Element findSectionElement(Element instance, List allSectionsList)
	{
		Element sectionElement = null;

		Element instance_id = instance.element("ID");
		String id = instance_id.getText();

		if (allSectionsList == null || allSectionsList.size() == 0) return null;

		for (Iterator l_i = allSectionsList.iterator(); l_i.hasNext();)
		{
			Element sec_instance = (Element) l_i.next();
			Element sec_id = (Element) sec_instance.selectSingleNode("INSTANCE");
			if (sec_id.getText().equals(id))
			{
				return sec_instance;
			}
		}
		return sectionElement;
	}
	
	/**
	 * abstract method implemented for moodle
	 */
	protected String checkAllResourceFileTagReferenceTransferred(List<Element> embedFiles, String subFolder, String s, String title)
	{
		return s;
	}
	
	/**
	 * 
	 */
	protected String[] getEmbeddedReferencePhysicalLocation(String embeddedSrc, String subFolder, List<Element> embedFiles, List<Element> embedContentFiles, String tool)
	{
		embeddedSrc = embeddedSrc.replace("$@FILEPHP@$", "");
		embeddedSrc = embeddedSrc.replace("$@SLASH@$", "/");
		embeddedSrc = embeddedSrc.replace("../", "");
		
		String[] returnStrings = new String[2];
		// physical location
		returnStrings[0] = embeddedSrc;
		// display name
		returnStrings[1] = embeddedSrc;
		return returnStrings;
	}

	private Question processQuestionElement(Element questionEle, Pool pool) throws AssessmentPermissionException
	{
		Question question = null;
		if (M_log.isDebugEnabled()) M_log.debug("Entering processQuestionElement...");

		if (questionEle != null)
		{
			if (questionEle.elements("QTYPE") != null && questionEle.elements("QTYPE").size() != 0)
			{
				Element qtypeEle = (Element) questionEle.elements("QTYPE").get(0);
				if (qtypeEle != null)
				{
					String qtype = qtypeEle.getTextTrim();
					if (qtype != null && qtype.length() != 0)
					{
						if (qtype.equals("multichoice"))
						{
							MultipleChoiceQuestion mcQuestion = questionService.newMultipleChoiceQuestion(pool);
							question = buildMultipleChoiceQuestion(questionEle, mcQuestion, pool);
						}
						if (qtype.equals("truefalse"))
						{
							TrueFalseQuestion tfQuestion = questionService.newTrueFalseQuestion(pool);
							question = buildTrueFalseQuestion(questionEle, tfQuestion, pool);
						}
						if (qtype.equals("match"))
						{
							MatchQuestion mQuestion = questionService.newMatchQuestion(pool);
							question = buildMatchQuestion(questionEle, mQuestion, pool);
						}
						if (qtype.equals("description"))
						{
							Question taskQuestion = questionService.newTaskQuestion(pool);
							question = buildTaskQuestion(questionEle, taskQuestion, pool);
						}
						// Essay, short answer and numerical types(range is not supported) are supported, the other types here aren't
						// We bring them in as multiple choice so they get marked invalid since they have no choices
						if (qtype.equals("essay") || qtype.equals("shortanswer") || qtype.equals("numerical"))
						{
							EssayQuestion essayQuestion = questionService.newEssayQuestion(pool);
							question = buildEssayQuestion(questionEle, essayQuestion, pool);
						}
						if (qtype.equals("calculated") || qtype.equals("multianswer") || qtype.equals("randomsamatch"))
						{
							MultipleChoiceQuestion mcQuestion = questionService.newMultipleChoiceQuestion(pool);
							question = buildMultipleChoiceQuestion(questionEle, mcQuestion, pool);
						}
					}
				}
			}
		}
		else
		{
			M_log.warn("Question element doesn't exist");
		}
		if (M_log.isDebugEnabled()) M_log.debug("Exiting processQuestionElement...");
		return question;
	}

	private boolean processRandomQuestion(String idStr, Element questionEle) throws AssessmentPermissionException
	{
		if (M_log.isDebugEnabled()) M_log.debug("Entering processRandomQuestion...");

		if (questionEle != null)
		{
			if (questionEle.elements("QTYPE") != null && questionEle.elements("QTYPE").size() != 0)
			{
				Element qtypeEle = (Element) questionEle.elements("QTYPE").get(0);
				if (qtypeEle != null)
				{
					String qtype = qtypeEle.getTextTrim();
					if (qtype != null && qtype.length() != 0)
					{
						if (!qtype.equals("random"))
						{
							return false;
						}
						else
						{
							if (questionEle.elements("DEFAULTGRADE") != null && questionEle.elements("DEFAULTGRADE").size() != 0)
							{
								Element gradeEle = (Element) questionEle.elements("DEFAULTGRADE").get(0);
								if (gradeEle != null)
								{
									String gradeStr = gradeEle.getTextTrim();
									if (gradeStr != null && gradeStr.length() != 0)
									{
										QuestionPoints qpObj = new QuestionPoints(idStr, Float.parseFloat(gradeStr));
										if (questionPointList == null)
										{
											questionPointList = new ArrayList<QuestionPoints>();
										}
										questionPointList.add(qpObj);
										return true;
									}
								}
							}
						}

					}
				}
			}
		}
		else
		{
			M_log.warn("Question element doesn't exist");
			return false;
		}
		if (M_log.isDebugEnabled()) M_log.debug("Exiting processRandomQuestion...");
		return false;
	}

	

	/**
	 * 
	 * @param poolNames
	 */
	private void transferQuestionCategories(List<String> poolNames)
	{
		Pool extraPool = null;
		Question question = null;
		String title = null;
		boolean poolExists = false;
		if (M_log.isDebugEnabled()) M_log.debug("Entering tranferQuestionCategories...");

		XPath xpath = backUpDoc.createXPath("/MOODLE_BACKUP/COURSE/QUESTION_CATEGORIES");

		Element eleOrg = (Element) xpath.selectSingleNode(backUpDoc);
		if (eleOrg == null) return;
		for (Iterator<?> iter = eleOrg.elementIterator("QUESTION_CATEGORY"); iter.hasNext();)
		{
			Element qCatElement = (Element) iter.next();
			Element subEleOrg = (Element) qCatElement.element("QUESTIONS");
			if (subEleOrg == null) continue;
			if (qCatElement.elements("NAME") != null && qCatElement.elements("NAME").size() != 0)
			{
				Element titleEle = (Element) qCatElement.elements("NAME").get(0);
				if (titleEle != null)
				{
					title = titleEle.getTextTrim();
					if (title != null && title.length() != 0)
					{
						poolExists = checkTitleExists(title, poolNames);
					}
				}
			}
			if (poolExists) continue;
			if (qCatElement.elements("NAME") != null && qCatElement.elements("NAME").size() != 0)
			{
				Element titleEle = (Element) qCatElement.elements("NAME").get(0);
				if (titleEle != null)
				{
					title = titleEle.getTextTrim();
				}
			}
			for (Iterator<?> subIter = subEleOrg.elementIterator("QUESTION"); subIter.hasNext();)
			{
				Element qElement = (Element) subIter.next();
				if (qElement.elements("ID") != null && qElement.elements("ID").size() != 0)
				{
					Element idEle = (Element) qElement.elements("ID").get(0);
					if (idEle != null)
					{
						String idStr = idEle.getTextTrim();
						if (idStr != null && idStr.length() != 0)
						{

							try
							{
								if (extraPool == null)
								{
									extraPool = poolService.newPool(siteId);
									if (extraPool != null)
									{
										if (title != null && title.length() != 0)
										{
											extraPool.setTitle(title);
										}
										else
										{
											extraPool.setTitle("Uncategorized Questions");
										}
										poolService.savePool(extraPool);
									}
								}
								if (extraPool != null)
								{
									if (qElement != null)
									{
										if (qElement.elements("QTYPE") != null && qElement.elements("QTYPE").size() != 0)
										{
											Element qtypeEle = (Element) qElement.elements("QTYPE").get(0);
											if (qtypeEle != null)
											{
												String qtype = qtypeEle.getTextTrim();
												if (qtype != null && qtype.length() != 0)
												{
													if (qtype.equals("random"))
													{
														mdlMnemeMap.put(idStr, new MnemeQuestionPool(null, extraPool.getId()));
													}
													else
													{
														question = processQuestionElement(qElement, extraPool);
														if (question != null)
														{
															questionService.saveQuestion(question);
														}
														mdlMnemeMap.put(idStr, new MnemeQuestionPool(question.getId(), extraPool.getId()));
													}
												}
											}
										}
									}
								}
							}
							catch (AssessmentPermissionException e)
							{
								M_log.warn("transferQuestionCategories permission exception: " + e.toString());
								return;
							}
							// }
						}
					}
				}
			}

			qCatElement = null;
			extraPool = null;
		}
		if (M_log.isDebugEnabled()) M_log.debug("Exiting transferQuestionCategories...");
	}
}
