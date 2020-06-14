package io.onedev.server.model;

import static io.onedev.server.model.PullRequest.PROP_NO_SPACE_TITLE;
import static io.onedev.server.model.PullRequest.PROP_NUMBER;
import static io.onedev.server.model.PullRequest.PROP_SUBMIT_DATE;
import static io.onedev.server.model.PullRequest.PROP_TITLE;
import static io.onedev.server.model.PullRequest.PROP_UUID;

import java.io.IOException;
import java.io.Serializable;
import java.nio.channels.IllegalSelectorException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Stack;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.lib.ObjectDatabase;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.Restrictions;

import com.fasterxml.jackson.annotation.JsonView;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import io.onedev.server.OneDev;
import io.onedev.server.entitymanager.PullRequestManager;
import io.onedev.server.entitymanager.UserManager;
import io.onedev.server.git.GitUtils;
import io.onedev.server.infomanager.PullRequestInfoManager;
import io.onedev.server.infomanager.UserInfoManager;
import io.onedev.server.model.support.CompareContext;
import io.onedev.server.model.support.EntityWatch;
import io.onedev.server.model.support.LastUpdate;
import io.onedev.server.model.support.pullrequest.CloseInfo;
import io.onedev.server.model.support.pullrequest.MergePreview;
import io.onedev.server.model.support.pullrequest.MergeStrategy;
import io.onedev.server.security.SecurityUtils;
import io.onedev.server.storage.AttachmentStorageSupport;
import io.onedev.server.util.CollectionUtils;
import io.onedev.server.util.ComponentContext;
import io.onedev.server.util.IssueUtils;
import io.onedev.server.util.ProjectAndBranch;
import io.onedev.server.util.ProjectScopedNumber;
import io.onedev.server.util.Referenceable;
import io.onedev.server.util.diff.WhitespaceOption;
import io.onedev.server.util.jackson.DefaultView;
import io.onedev.server.util.jackson.RestView;
import io.onedev.server.web.util.PullRequestAware;
import io.onedev.server.web.util.WicketUtils;

@Entity
@Table(
		indexes={
				@Index(columnList=PROP_TITLE), @Index(columnList=PROP_UUID), 
				@Index(columnList=PROP_NO_SPACE_TITLE), @Index(columnList=PROP_NUMBER), 
				@Index(columnList="o_targetProject_id"), @Index(columnList=PROP_SUBMIT_DATE), 
				@Index(columnList=LastUpdate.COLUMN_DATE), @Index(columnList="o_sourceProject_id"), 
				@Index(columnList="o_submitter_id"), @Index(columnList=MergePreview.COLUMN_HEAD_COMMIT_HASH), 
				@Index(columnList=CloseInfo.COLUMN_DATE), @Index(columnList=CloseInfo.COLUMN_STATUS), 
				@Index(columnList=CloseInfo.COLUMN_USER), @Index(columnList=CloseInfo.COLUMN_USER_NAME), 
				@Index(columnList="o_numberScope_id")},
		uniqueConstraints={@UniqueConstraint(columnNames={"o_numberScope_id", PROP_NUMBER})})
//use dynamic update in order not to overwrite other edits while background threads change update date
@DynamicUpdate
@Cache(usage=CacheConcurrencyStrategy.READ_WRITE)
public class PullRequest extends AbstractEntity implements Referenceable, AttachmentStorageSupport {

	private static final long serialVersionUID = 1L;
	
	public static final String PROP_NUMBER_SCOPE = "numberScope";
	
	public static final String NAME_NUMBER = "Number";

	public static final String PROP_NUMBER = "number";
	
	public static final String NAME_STATUS = "Status";
	
	public static final String NAME_TARGET_PROJECT = "Target Project";
	
	public static final String PROP_TARGET_PROJECT = "targetProject";
	
	public static final String NAME_TARGET_BRANCH = "Target Branch";
	
	public static final String PROP_TARGET_BRANCH = "targetBranch";
	
	public static final String NAME_SOURCE_PROJECT = "Source Project";
	
	public static final String PROP_SOURCE_PROJECT = "sourceProject";
	
	public static final String NAME_SOURCE_BRANCH = "Source Branch";
	
	public static final String PROP_SOURCE_BRANCH = "sourceBranch";
	
	public static final String NAME_TITLE = "Title";
	
	public static final String PROP_TITLE = "title";
	
	public static final String NAME_DESCRIPTION = "Description";
	
	public static final String PROP_DESCRIPTION = "description";
	
	public static final String NAME_COMMENT = "Comment";

	public static final String PROP_COMMENTS = "comments";
	
	public static final String NAME_COMMENT_COUNT = "Comment Count";
	
	public static final String PROP_COMMENT_COUNT = "commentCount";

	public static final String NAME_SUBMITTER = "Submitter";
	
	public static final String PROP_SUBMITTER = "submitter";
	
	public static final String NAME_SUBMIT_DATE = "Submit Date";
	
	public static final String PROP_SUBMIT_DATE = "submitDate";
	
	public static final String NAME_UPDATE_DATE = "Update Date";
	
	public static final String PROP_LAST_UPDATE = "lastUpdate";
	
	public static final String NAME_CLOSE_DATE = "Close Date";
	
	public static final String NAME_MERGE_STRATEGY = "Merge Strategy";
	
	public static final String PROP_MERGE_STRATEGY = "mergeStrategy";
	
	public static final String PROP_CLOSE_INFO = "closeInfo";
	
	public static final String PROP_LAST_MERGE_PREVIEW = "lastMergePreview";
	
	public static final String PROP_ID = "id";
	
	public static final String PROP_REVIEWS = "reviews";
	
	public static final String PROP_VERIFICATIONS= "verifications";
	
	public static final String PROP_ASSIGNMENTS = "assignments";
	
	public static final String PROP_UUID = "uuid";
	
	public static final String PROP_NO_SPACE_TITLE = "noSpaceTitle";

	public static final String STATE_OPEN = "Open";

	public static final String REFS_PREFIX = "refs/pull/";

	public static final int MAX_CODE_COMMENTS = 1000;
	
	private static final int MAX_CHECK_ERROR_LEN = 1024;

	public static final List<String> QUERY_FIELDS = Lists.newArrayList(
			NAME_NUMBER, NAME_TITLE, NAME_TARGET_PROJECT, NAME_TARGET_BRANCH, 
			NAME_SOURCE_PROJECT, NAME_SOURCE_BRANCH, NAME_DESCRIPTION, 
			NAME_COMMENT, NAME_SUBMIT_DATE, NAME_UPDATE_DATE, 
			NAME_CLOSE_DATE, NAME_MERGE_STRATEGY, NAME_COMMENT_COUNT);

	public static final Map<String, String> ORDER_FIELDS = CollectionUtils.newLinkedHashMap(
			NAME_SUBMIT_DATE, PROP_SUBMIT_DATE,
			NAME_UPDATE_DATE, PROP_LAST_UPDATE + "." + LastUpdate.PROP_DATE,
			NAME_CLOSE_DATE, PROP_CLOSE_INFO + "." + CloseInfo.PROP_DATE,
			NAME_NUMBER, PROP_NUMBER,
			NAME_STATUS, PROP_CLOSE_INFO + "." + CloseInfo.PROP_STATUS,
			NAME_TARGET_PROJECT, PROP_TARGET_PROJECT,
			NAME_TARGET_BRANCH, PROP_TARGET_BRANCH,
			NAME_SOURCE_PROJECT, PROP_SOURCE_PROJECT,
			NAME_SOURCE_BRANCH, PROP_SOURCE_BRANCH,
			NAME_COMMENT_COUNT, PROP_COMMENT_COUNT);
	
	private static ThreadLocal<Stack<PullRequest>> stack =  new ThreadLocal<Stack<PullRequest>>() {

		@Override
		protected Stack<PullRequest> initialValue() {
			return new Stack<PullRequest>();
		}
	
	};
	
	@Embedded
	private CloseInfo closeInfo;

	@Column(nullable=false)
	private String title;
	
	@Column(length=16384)
	private String description;
	
	@ManyToOne(fetch=FetchType.LAZY)
	@JoinColumn
	private User submitter;
	
	private String submitterName;
	
	@ManyToOne(fetch=FetchType.LAZY)
	@JoinColumn(nullable=false)
	private Project numberScope;
	
	@ManyToOne(fetch=FetchType.LAZY)
	@JoinColumn(nullable=false)
	private Project targetProject;
	
	@Column(nullable=false)
	private String targetBranch;

	@ManyToOne(fetch=FetchType.LAZY)
	private Project sourceProject;
	
	@Column(nullable=false)
	private String sourceBranch;
	
	@Column(nullable=false)
	private String baseCommitHash;
	
	@Column(nullable=true)
	private Date lastCodeCommentActivityDate;

	// used for title search in markdown editor
	@Column(nullable=false)
	@JsonView(DefaultView.class)
	private String noSpaceTitle;
	
	@Embedded
	private MergePreview lastMergePreview;
	
	@Column(nullable=false)
	private Date submitDate = new Date();
	
	@Column(nullable=false)
	private MergeStrategy mergeStrategy;
	
	@Column(nullable=false)
	private String uuid = UUID.randomUUID().toString();
	
	private long number;
	
	private int commentCount;
	
	@Embedded
	private LastUpdate lastUpdate;
	
	@OneToMany(mappedBy="request", cascade=CascadeType.REMOVE)
	private Collection<PullRequestUpdate> updates = new ArrayList<>();

	@OneToMany(mappedBy="request", cascade=CascadeType.REMOVE)
	private Collection<PullRequestReview> reviews = new ArrayList<>();
	
	@OneToMany(mappedBy="request", cascade=CascadeType.REMOVE)
	private Collection<PullRequestAssignment> assignments = new ArrayList<>();
	
	@OneToMany(mappedBy="request", cascade=CascadeType.REMOVE)
	private Collection<PullRequestVerification> verifications = new ArrayList<>();
	
	@OneToMany(mappedBy="request", cascade=CascadeType.REMOVE)
	private Collection<PullRequestComment> comments = new ArrayList<>();

	@OneToMany(mappedBy="request", cascade=CascadeType.REMOVE)
	private Collection<PullRequestChange> changes = new ArrayList<>();
	
	@OneToMany(mappedBy="request", cascade=CascadeType.REMOVE)
	private Collection<PullRequestWatch> watches = new ArrayList<>();
	
	@OneToMany(mappedBy="request")
	private Collection<CodeComment> codeComments = new ArrayList<>();
	
	private transient Boolean mergedIntoTarget;

	private transient List<PullRequestUpdate> sortedUpdates;
	
	private transient List<PullRequestReview> sortedReviews;
	
	private transient Optional<MergePreview> mergePreviewOpt;
	
	private transient Boolean valid;
	
	private transient Collection<Long> fixedIssueNumbers;
	
	private transient Collection<User> participants;
	
	@Column(length=MAX_CHECK_ERROR_LEN)
	private String checkError;
	
	/**
	 * Get title of this merge request.
	 * 
	 * @return user specified title of this merge request, <tt>null</tt> for
	 *         auto-created merge request.
	 */
	public @Nullable String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
		noSpaceTitle = StringUtils.deleteWhitespace(title);
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}
	
	/**
	 * Get the user submitting the pull request.
	 * 
	 * @return
	 * 			the user submitting the pull request
	 */
	@Nullable
	public User getSubmitter() {
		return submitter;
	}

	public void setSubmitter(User submitter) {
		this.submitter = submitter;
	}

	@Nullable
	public String getSubmitterName() {
		return submitterName;
	}

	public Project getNumberScope() {
		return numberScope;
	}

	public void setNumberScope(Project numberScope) {
		this.numberScope = numberScope;
	}

	public Project getTargetProject() {
		return targetProject;
	}
	
	public ProjectAndBranch getTarget() {
		return new ProjectAndBranch(getTargetProject(), getTargetBranch());
	}
	
	public void setTarget(ProjectAndBranch target) {
		setTargetProject(target.getProject());
		setTargetBranch(target.getBranch());
	}
	
	public void setSource(ProjectAndBranch source) {
		setSourceProject(source.getProject());
		setSourceBranch(source.getBranch());
	}
	
	public void setTargetProject(Project targetProject) {
		this.targetProject = targetProject;
	}

	public String getTargetBranch() {
		return targetBranch;
	}

	public void setTargetBranch(String targetBranch) {
		this.targetBranch = targetBranch;
	}

	@Nullable
	public Project getSourceProject() {
		return sourceProject;
	}

	public void setSourceProject(Project sourceProject) {
		this.sourceProject = sourceProject;
	}

	public String getSourceBranch() {
		return sourceBranch;
	}

	public void setSourceBranch(String sourceBranch) {
		this.sourceBranch = sourceBranch;
	}

	@Nullable
	public ProjectAndBranch getSource() {
		Project sourceProject = getSourceProject();
		if (sourceProject != null)
			return new ProjectAndBranch(sourceProject, getSourceBranch());
		else
			return null;
	}
	
	public String getTargetRef() {
		return GitUtils.branch2ref(getTargetBranch());
	}
	
	public String getSourceRef() {
		return GitUtils.branch2ref(getSourceBranch());
	}
	
	public Project getWorkProject() {
		if (isNew()) 
			return getSourceProject();
		else
			return getTargetProject();
	}
	
	public String getBaseCommitHash() {
		return baseCommitHash;
	}

	public void setBaseCommitHash(String baseCommitHash) {
		this.baseCommitHash = baseCommitHash;
	}
	
	public RevCommit getBaseCommit() {
		return getTargetProject().getRevCommit(ObjectId.fromString(getBaseCommitHash()), true);
	}
	
	/**
	 * Get unmodifiable collection of updates of this pull request. To add update 
	 * to the pull request, call {@link this#addUpdate(PullRequestUpdate)} instead.
	 * 
	 * @return
	 * 			unmodifiable collection of updates
	 */
	public Collection<PullRequestUpdate> getUpdates() {
		return Collections.unmodifiableCollection(updates);
	}
	
	public void setUpdates(Collection<PullRequestUpdate> updates) {
		this.updates = updates;
		sortedUpdates = null;
	}

	public void addUpdate(PullRequestUpdate update) {
		updates.add(update);
		sortedUpdates = null;
	}

	public Collection<PullRequestVerification> getVerifications() {
		return verifications;
	}

	public void setVerifications(Collection<PullRequestVerification> verifications) {
		this.verifications = verifications;
	}

	public Collection<PullRequestComment> getComments() {
		return comments;
	}

	public void setComments(Collection<PullRequestComment> comments) {
		this.comments = comments;
	}

	public Collection<PullRequestChange> getChanges() {
		return changes;
	}

	public void setChanges(Collection<PullRequestChange> changes) {
		this.changes = changes;
	}

	@Override
	public Collection<PullRequestWatch> getWatches() {
		return watches;
	}

	public void setWatches(Collection<PullRequestWatch> watches) {
		this.watches = watches;
	}

	@Override
	public EntityWatch getWatch(User user, boolean createIfNotExist) {
		if (createIfNotExist) {
			PullRequestWatch watch = (PullRequestWatch) super.getWatch(user, false);
			if (watch == null) {
				watch = new PullRequestWatch();
				watch.setRequest(this);
				watch.setUser(user);
				getWatches().add(watch);
			}
			return watch;
		} else {
			return super.getWatch(user, false);
		}
	}
	
	public Collection<CodeComment> getCodeComments() {
		return codeComments;
	}

	public void setCodeComments(Collection<CodeComment> codeComments) {
		this.codeComments = codeComments;
	}

	@Nullable
	public CloseInfo getCloseInfo() {
		return closeInfo;
	}

	public void setCloseInfo(CloseInfo closeInfo) {
		this.closeInfo = closeInfo;
	}

	public boolean isOpen() {
		return closeInfo == null;
	}
	
	/**
	 * Get last merge preview of this pull request. Note that this method may return an 
	 * outdated merge preview. Refer to {@link this#getIntegrationPreview()}
	 * if you'd like to get an update-to-date merge preview
	 *  
	 * @return
	 * 			merge preview of this pull request, or <tt>null</tt> if merge 
	 * 			preview has not been calculated yet. 
	 */
	@Nullable
	public MergePreview getLastMergePreview() {
		return lastMergePreview;
	}
	
	public void setLastMergePreview(MergePreview lastIntegrationPreview) {
		this.lastMergePreview = lastIntegrationPreview;
	}

	/**
	 * Get effective merge preview of this pull request.
	 * 
	 * @return
	 * 			update to date merge preview of this pull request, or <tt>null</tt> if 
	 * 			the merge preview has not been calculated or outdated. In both cases, 
	 * 			it will trigger a re-calculation, and client should call this method later 
	 * 			to get the calculated result 
	 */
	@JsonView(RestView.class)
	@Nullable
	public MergePreview getMergePreview() {
		if (mergePreviewOpt == null)
			mergePreviewOpt = Optional.ofNullable(OneDev.getInstance(PullRequestManager.class).previewMerge(this));
		return mergePreviewOpt.orElse(null);
	}
	
	/**
	 * Get list of sorted updates.
	 * 
	 * @return 
	 * 			list of sorted updates ordered by id
	 */
	public List<PullRequestUpdate> getSortedUpdates() {
		if (sortedUpdates == null) {
			Preconditions.checkState(updates.size() >= 1);
			sortedUpdates = new ArrayList<PullRequestUpdate>(updates);
			Collections.sort(sortedUpdates);
		}
		return sortedUpdates;
	}
	
	public List<PullRequestReview> getSortedReviews() {
		if (sortedReviews == null) {
			sortedReviews = new ArrayList<>(reviews);
			
			Collections.sort(sortedReviews, new Comparator<PullRequestReview>() {

				@Override
				public int compare(PullRequestReview o1, PullRequestReview o2) {
					if (o1.getId() != null && o2.getId() != null)
						return o1.getId().compareTo(o1.getId());
					else
						return 0;
				}
				
			});
		}
		return sortedReviews;
	}

	public MergeStrategy getMergeStrategy() {
		return mergeStrategy;
	}

	public void setMergeStrategy(MergeStrategy mergeStrategy) {
		this.mergeStrategy = mergeStrategy;
	}

	@JsonView(RestView.class)
	public PullRequestUpdate getLatestUpdate() {
		return getSortedUpdates().get(getSortedUpdates().size()-1);
	}
	
	@JsonView(RestView.class)
	public String getBaseRef() {
		Preconditions.checkNotNull(getId());
		return PullRequest.REFS_PREFIX + getNumber() + "/base";
	}

	@JsonView(RestView.class)
	public String getMergeRef() {
		Preconditions.checkNotNull(getId());
		return PullRequest.REFS_PREFIX + getNumber() + "/merge";
	}

	@JsonView(RestView.class)
	public String getHeadRef() {
		Preconditions.checkNotNull(getId());
		return PullRequest.REFS_PREFIX + getNumber() + "/head";
	}
	
	/**
	 * Delete refs of this pull request, without touching refs of its updates.
	 */
	public void deleteRefs() {
		GitUtils.deleteRef(GitUtils.getRefUpdate(getTargetProject().getRepository(), getBaseRef()));
		GitUtils.deleteRef(GitUtils.getRefUpdate(getTargetProject().getRepository(), getMergeRef()));
		GitUtils.deleteRef(GitUtils.getRefUpdate(getTargetProject().getRepository(), getHeadRef()));
	}
	
	public void writeBaseRef() {
		RefUpdate refUpdate = GitUtils.getRefUpdate(getTargetProject().getRepository(), getBaseRef());
		refUpdate.setNewObjectId(ObjectId.fromString(getBaseCommitHash()));
		GitUtils.updateRef(refUpdate);
	}
	
	public void writeHeadRef() {
		RefUpdate refUpdate = GitUtils.getRefUpdate(getTargetProject().getRepository(), getHeadRef());
		refUpdate.setNewObjectId(ObjectId.fromString(getLatestUpdate().getHeadCommitHash()));
		GitUtils.updateRef(refUpdate);
	}
	
	public void writeMergeRef() {
		RefUpdate refUpdate = GitUtils.getRefUpdate(getTargetProject().getRepository(), getMergeRef());
		refUpdate.setNewObjectId(ObjectId.fromString(getLastMergePreview().getMergeCommitHash()));
		GitUtils.updateRef(refUpdate);
	}
	
	public static class CriterionHelper {
		public static Criterion ofOpen() {
			return Restrictions.isNull("closeInfo");
		}
		
		public static Criterion ofClosed() {
			return Restrictions.isNotNull("closeInfo");
		}
		
		public static Criterion ofTarget(ProjectAndBranch target) {
			return Restrictions.and(
					Restrictions.eq("targetProject", target.getProject()),
					Restrictions.eq("targetBranch", target.getBranch()));
		}

		public static Criterion ofTargetProject(Project target) {
			return Restrictions.eq("targetProject", target);
		}
		
		public static Criterion ofSource(ProjectAndBranch source) {
			return Restrictions.and(
					Restrictions.eq("sourceProject", source.getProject()),
					Restrictions.eq("sourceBranch", source.getBranch()));
		}
		
		public static Criterion ofSourceProject(Project source) {
			return Restrictions.eq("sourceProject", source);
		}
		
		public static Criterion ofSubmitter(User submitter) {
			return Restrictions.eq("submitter", submitter);
		}
		
	}

	public Date getSubmitDate() {
		return submitDate;
	}

	public void setSubmitDate(Date submitDate) {
		this.submitDate = submitDate;
	}

	public Collection<PullRequestReview> getReviews() {
		return reviews;
	}
	
	public void setReviews(Collection<PullRequestReview> reviews) {
		this.reviews = reviews;
	}

	public Collection<PullRequestAssignment> getAssignments() {
		return assignments;
	}

	public void setAssignments(Collection<PullRequestAssignment> assignments) {
		this.assignments = assignments;
	}

	public String getUUID() {
		return uuid;
	}

	public void setUUID(String uuid) {
		this.uuid = uuid;
	}
	
	@Override
	public long getNumber() {
		return number;
	}

	public void setNumber(long number) {
		this.number = number;
	}

	public int getCommentCount() {
		return commentCount;
	}

	public void setCommentCount(int commentCount) {
		this.commentCount = commentCount;
	}

	public LastUpdate getLastUpdate() {
		return lastUpdate;
	}

	public void setLastUpdate(LastUpdate lastUpdate) {
		this.lastUpdate = lastUpdate;
	}

	@Nullable
	public String getCheckError() {
		return checkError;
	}

	public void setCheckError(@Nullable String checkError) {
		if (checkError != null) 
			checkError = StringUtils.abbreviate(checkError, MAX_CHECK_ERROR_LEN);
		this.checkError = checkError;
	}

	public Date getLastCodeCommentActivityDate() {
		return lastCodeCommentActivityDate;
	}

	public void setLastCodeCommentActivityDate(Date lastCodeCommentActivityDate) {
		this.lastCodeCommentActivityDate = lastCodeCommentActivityDate;
	}

	public boolean isVisitedAfter(Date date) {
		User user = SecurityUtils.getUser();
		if (user != null) {
			Date visitDate = OneDev.getInstance(UserInfoManager.class).getPullRequestVisitDate(user, this);
			return visitDate != null && visitDate.getTime()>date.getTime();
		} else {
			return true;
		}
	}
	
	public boolean isCodeCommentsVisitedAfter(Date date) {
		User user = SecurityUtils.getUser();
		if (user != null) {
			Date visitDate = OneDev.getInstance(UserInfoManager.class).getPullRequestCodeCommentsVisitDate(user, this);
			return visitDate != null && visitDate.getTime()>date.getTime();
		} else {
			return true;
		}
	}
	
	public boolean isMerged() {
		return closeInfo != null && closeInfo.getStatus() == CloseInfo.Status.MERGED;
	}
	
	public boolean isDiscarded() {
		return closeInfo != null && closeInfo.getStatus() == CloseInfo.Status.DISCARDED;
	}
	
	public boolean isMergedIntoTarget() {
		if (mergedIntoTarget == null) { 
			mergedIntoTarget = GitUtils.isMergedInto(getTargetProject().getRepository(), null, 
					ObjectId.fromString(getLatestUpdate().getHeadCommitHash()), getTarget().getObjectId());
		}
		return mergedIntoTarget;
	}
	
	@Nullable
	public ObjectId getSourceHead() {
		ProjectAndBranch projectAndBranch = getSource();
		if (projectAndBranch != null)
			return projectAndBranch.getObjectId(false);
		else
			return null;
	}
	
	@Nullable
	public PullRequestReview getReview(User user) {
		for (PullRequestReview review: getReviews()) {
			if (review.getUser().equals(user))
				return review;
		}
		return null;
	}
	
	public boolean canCommentOnCommit(String commitHash) {
		if (commitHash.equals(baseCommitHash))
			return true;
		for (PullRequestUpdate update: getUpdates()) {
			for (RevCommit commit: update.getCommits())
				if (commit.name().equals(commitHash))
					return true;
		}
		return false;
	}
	
	@Nullable
	public ComparingInfo getRequestComparingInfo(CodeComment.ComparingInfo commentComparingInfo) {
		List<String> commitHashes = new ArrayList<>();
		commitHashes.add(getBaseCommitHash());
		for (PullRequestUpdate update: getSortedUpdates()) {
			for (RevCommit commit: update.getCommits())
				commitHashes.add(commit.name());
		}
		String commitHash = commentComparingInfo.getCommitHash();
		int commitIndex = commitHashes.indexOf(commitHash);
		if (commitIndex == -1)
			return null;
		
		CompareContext compareContext = commentComparingInfo.getCompareContext();
		int compareCommitIndex = commitHashes.indexOf(compareContext.getCompareCommitHash());
		if (compareCommitIndex == -1 || compareCommitIndex == commitIndex) {
			if (commitIndex == commitHashes.size()-1) {
				return new ComparingInfo(commitHashes.get(0), commitHash, 
						compareContext.getWhitespaceOption(), compareContext.getPathFilter());
			} else {
				return new ComparingInfo(commitHash, commitHashes.get(commitHashes.size()-1),
						compareContext.getWhitespaceOption(), compareContext.getPathFilter());
			}
		} else if (compareCommitIndex < commitIndex) {		
			return new ComparingInfo(compareContext.getCompareCommitHash(), commitHash, 
					compareContext.getWhitespaceOption(), compareContext.getPathFilter());
		} else {
			return new ComparingInfo(commitHash, compareContext.getCompareCommitHash(), 
					compareContext.getWhitespaceOption(), compareContext.getPathFilter());
		}
	}
	
	public Collection<User> getParticipants() {
		if (participants == null) {
			participants = new LinkedHashSet<>();
			if (getSubmitter() != null)
				participants.add(getSubmitter());
			for (PullRequestComment comment: getComments()) {
				if (comment.getUser() != null)
					participants.add(comment.getUser());
			}
			for (PullRequestChange change: getChanges()) {
				if (change.getUser() != null)
					participants.add(change.getUser());
			}
			participants.remove(OneDev.getInstance(UserManager.class).getSystem());
		}
		return participants;
	}
	
	public boolean isValid() {
		if (valid == null) {
			Repository repository = targetProject.getRepository();
			ObjectDatabase objDb = repository.getObjectDatabase();
			try {
				if (!objDb.has(ObjectId.fromString(baseCommitHash))) {
					valid = false;
				} else {
					for (PullRequestUpdate update: updates) {
						if (!objDb.has(ObjectId.fromString(update.getTargetHeadCommitHash()))
								|| !objDb.has(ObjectId.fromString(update.getHeadCommitHash()))) {
							valid = false;
							break;
						}
					}
					if (valid == null)
						valid = true;
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		} 
		return valid;
	}
	
	public static String getWebSocketObservable(Long requestId) {
		return PullRequest.class.getName() + ":" + requestId;
	}
	
	public Collection<Long> getFixedIssueNumbers() {
		if (fixedIssueNumbers == null) {
			fixedIssueNumbers = new HashSet<>();
			for (PullRequestUpdate update: getUpdates()) {
				for (RevCommit commit: update.getCommits())
					fixedIssueNumbers.addAll(IssueUtils.parseFixedIssueNumbers(commit.getFullMessage()));
			}
		}
		return fixedIssueNumbers;
	}
	
	public boolean isAllReviewsApproved() {
		for (PullRequestReview review: getReviews()) {
			if (review.getResult() == null || !review.getResult().isApproved()) 
				return false;
		}
		return true;
	}
	
	public boolean isRequiredBuildsSuccessful() {
		for (PullRequestVerification verification: getVerifications()) {
			if (verification.isRequired() && verification.getBuild().getStatus() != Build.Status.SUCCESSFUL)
				return false;
		}
		return true;
	}
	
	public static class ComparingInfo implements Serializable {
		
		private static final long serialVersionUID = 1L;

		private final String oldCommitHash; 
		
		private final String newCommitHash;
		
		private final String pathFilter;
		
		private final WhitespaceOption whitespaceOption;
		
		public ComparingInfo(String oldCommitHash, String newCommitHash, 
				WhitespaceOption whitespaceOption, @Nullable String pathFilter) {
			this.oldCommitHash = oldCommitHash;
			this.newCommitHash = newCommitHash;
			this.whitespaceOption = whitespaceOption;
			this.pathFilter = pathFilter;
		}

		public String getOldCommitHash() {
			return oldCommitHash;
		}

		public String getNewCommitHash() {
			return newCommitHash;
		}

		public String getPathFilter() {
			return pathFilter;
		}

		public WhitespaceOption getWhitespaceOption() {
			return whitespaceOption;
		}

	}
	
	@Override
	public String getAttachmentStorageUUID() {
		return uuid;
	}
	
	private boolean contains(ObjectId commitId) {
		if (commitId.equals(getBaseCommit()))
			return true;
		for (PullRequestUpdate update: getUpdates()) {
			for (RevCommit commit: update.getCommits()) {
				if (commit.equals(commitId))
					return true;
			}
		}
		return false;
	}
	
	public ObjectId getComparisonOrigin(ObjectId comparisonBase) {
		if (contains(comparisonBase))
			return comparisonBase;
		
		RevCommit comparisonBaseCommit = getTargetProject().getRevCommit(comparisonBase, true);
		for (RevCommit parentCommit: comparisonBaseCommit.getParents()) {
			if (contains(parentCommit))
				return parentCommit.copy();
		}
		throw new IllegalStateException();
	}
	
	public ObjectId getComparisonBase(ObjectId oldCommitId, ObjectId newCommitId) {
		if (isNew() || oldCommitId.equals(newCommitId))
			return oldCommitId;
		
		PullRequestInfoManager infoManager = OneDev.getInstance(PullRequestInfoManager.class);
		ObjectId comparisonBase = infoManager.getComparisonBase(this, oldCommitId, newCommitId);
		if (comparisonBase != null) {
			try {
				if (!getTargetProject().getRepository().getObjectDatabase().has(comparisonBase))
					comparisonBase = null;
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		if (comparisonBase == null) {
			for (PullRequestUpdate update: getSortedUpdates()) {
				if (update.getCommits().contains(newCommitId)) {
					ObjectId targetHead = ObjectId.fromString(update.getTargetHeadCommitHash());
					Repository repo = getTargetProject().getRepository();
					ObjectId mergeBase1 = GitUtils.getMergeBase(repo, targetHead, newCommitId);
					if (mergeBase1 != null) {
						ObjectId mergeBase2 = GitUtils.getMergeBase(repo, mergeBase1, oldCommitId);
						if (mergeBase2.equals(mergeBase1)) {
							comparisonBase = oldCommitId;
							break;
						} else if (mergeBase2.equals(oldCommitId)) {
							comparisonBase = mergeBase1;
							break;
						} else {
							PersonIdent person = new PersonIdent("OneDev", "");
							comparisonBase = GitUtils.merge(repo, oldCommitId, mergeBase1, false, 
									person, person, "helper commit", true);
							break;
						}
					} else {
						return oldCommitId;
					}
				}
			}
			if (comparisonBase != null)
				infoManager.cacheComparisonBase(this, oldCommitId, newCommitId, comparisonBase);
			else
				throw new IllegalSelectorException();
		}
		return comparisonBase;
	}

	@Override
	public Project getAttachmentProject() {
		return getTargetProject();
	}
	
	public ProjectScopedNumber getFQN() {
		return new ProjectScopedNumber(getTargetProject(), getNumber());
	}
	
	public static void push(PullRequest request) {
		stack.get().push(request);
	}

	public static void pop() {
		stack.get().pop();
	}
	
	@Nullable
	public static PullRequest get() {
		if (!stack.get().isEmpty()) { 
			return stack.get().peek();
		} else {
			ComponentContext componentContext = ComponentContext.get();
			if (componentContext != null) {
				PullRequestAware pullRequestAware = WicketUtils.findInnermost(
						componentContext.getComponent(), PullRequestAware.class);
				if (pullRequestAware != null) 
					return pullRequestAware.getPullRequest();
			}
			return null;
		}
	}
	
	public String getNumberAndTitle() {
		return "#" + getNumber() + " - " + getTitle();
	}
	
}
