package io.onedev.server.buildspec.job.action;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.validation.Valid;
import javax.validation.ValidationException;

import org.hibernate.validator.constraints.NotEmpty;

import io.onedev.commons.codeassist.InputSuggestion;
import io.onedev.server.OneDev;
import io.onedev.server.buildspec.BuildSpec;
import io.onedev.server.buildspec.job.Job;
import io.onedev.server.entitymanager.BuildManager;
import io.onedev.server.entitymanager.IssueManager;
import io.onedev.server.entitymanager.SettingManager;
import io.onedev.server.issue.fieldsupply.FieldSupply;
import io.onedev.server.model.Build;
import io.onedev.server.model.Issue;
import io.onedev.server.model.Project;
import io.onedev.server.model.support.administration.GlobalIssueSetting;
import io.onedev.server.persistence.SessionManager;
import io.onedev.server.persistence.TransactionManager;
import io.onedev.server.util.SecurityUtils;
import io.onedev.server.util.script.identity.JobIdentity;
import io.onedev.server.util.script.identity.ScriptIdentity;
import io.onedev.server.web.editable.annotation.Editable;
import io.onedev.server.web.editable.annotation.FieldNamesProvider;
import io.onedev.server.web.editable.annotation.Interpolative;
import io.onedev.server.web.editable.annotation.Multiline;
import io.onedev.server.web.editable.annotation.OmitName;

@Editable(name="Create issue", order=300)
public class CreateIssueAction extends PostBuildAction {

	private static final long serialVersionUID = 1L;
	
	private String issueTitle;
	
	private String issueDescription;
	
	private List<FieldSupply> issueFields = new ArrayList<>();
	
	@Editable(order=1000, name="Title", group="Issue Detail", description="Specify title of the issue. "
			+ "<b>Note:</b> Type <tt>@</tt> to <a href='https://code.onedev.io/projects/onedev-manual/blob/master/pages/variable-substitution.md' target='_blank' tabindex='-1'>insert variable</a>, use <tt>\\</tt> to escape normal occurrences of <tt>@</tt> or <tt>\\</tt>")
	@Interpolative(variableSuggester="suggestVariables")
	@NotEmpty
	public String getIssueTitle() {
		return issueTitle;
	}

	public void setIssueTitle(String issueTitle) {
		this.issueTitle = issueTitle;
	}
	
	@Editable(order=1050, name="Description", group="Issue Detail", description="Optionally specify description of the issue. "
			+ "<b>Note:</b> Type <tt>@</tt> to <a href='https://code.onedev.io/projects/onedev-manual/blob/master/pages/variable-substitution.md' target='_blank' tabindex='-1'>insert variable</a>, use <tt>\\</tt> to escape normal occurrences of <tt>@</tt> or <tt>\\</tt>")
	@Multiline
	@Interpolative(variableSuggester="suggestVariables")
	public String getIssueDescription() {
		return issueDescription;
	}

	public void setIssueDescription(String issueDescription) {
		this.issueDescription = issueDescription;
	}

	@SuppressWarnings("unused")
	private static List<InputSuggestion> suggestVariables(String matchWith) {
		return Job.suggestVariables(matchWith);
	}
	
	@Editable(order=1100, group="Issue Detail")
	@FieldNamesProvider("getFieldNames")
	@OmitName
	@Valid
	public List<FieldSupply> getIssueFields() {
		return issueFields;
	}

	public void setIssueFields(List<FieldSupply> issueFields) {
		this.issueFields = issueFields;
	}
	
	private static Collection<String> getFieldNames() {
		return Project.get().getIssueSetting().getPromptFieldsUponIssueOpen(true);
	}
	
	@Override
	public void execute(Build build) {
		Long buildId = build.getId();

		OneDev.getInstance(TransactionManager.class).runAfterCommit(new Runnable() {

			@Override
			public void run() {
				OneDev.getInstance(SessionManager.class).runAsync(new Runnable() {

					@Override
					public void run() {
						Build build = OneDev.getInstance(BuildManager.class).load(buildId);
						Build.push(build);
						ScriptIdentity.push(new JobIdentity(build.getProject(), build.getCommitId()));
						try {
							Issue issue = new Issue();
							issue.setUUID(UUID.randomUUID().toString());
							issue.setProject(build.getProject());
							issue.setTitle(build.interpolate(getIssueTitle()));
							issue.setSubmitter(SecurityUtils.getUser());
							issue.setSubmitDate(new Date());
							SettingManager settingManager = OneDev.getInstance(SettingManager.class);
							GlobalIssueSetting issueSetting = settingManager.getIssueSetting();
							issue.setState(issueSetting.getInitialStateSpec().getName());
							
							issue.setDescription(getIssueDescription());
							for (FieldSupply supply: getIssueFields()) {
								Object fieldValue = issueSetting.getFieldSpec(supply.getName())
										.convertToObject(supply.getValueProvider().getValue());
								issue.setFieldValue(supply.getName(), fieldValue);
							}
							OneDev.getInstance(IssueManager.class).open(issue);
						} finally {
							Build.pop();
							ScriptIdentity.pop();
						}
					}
				});
			}
		});
		
	}

	@Override
	public String getDescription() {
		return "Create issue";
	}

	@Override
	public void validateWithContext(BuildSpec buildSpec, Job job) {
		super.validateWithContext(buildSpec, job);
		
		GlobalIssueSetting issueSetting = OneDev.getInstance(SettingManager.class).getIssueSetting();
		try {
			FieldSupply.validateFields(issueSetting.getFieldSpecMap(getFieldNames()), issueFields);
		} catch (ValidationException e) {
			throw new ValidationException("Error validating issue fields: " + e.getMessage());
		}
		
	}

}