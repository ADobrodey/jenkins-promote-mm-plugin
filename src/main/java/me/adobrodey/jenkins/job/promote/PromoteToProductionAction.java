package me.adobrodey.jenkins.job.promote;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import javax.annotation.Nonnull;
import javax.servlet.ServletException;


import hudson.Extension;
import hudson.model.Action;
import hudson.model.Job;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import jenkins.model.TransientActionFactory;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import com.cloudbees.opscenter.context.remote.RemotePath;
import com.cloudbees.opscenter.replication.ReplicationMode;
import com.cloudbees.opscenter.replication.ErrorHandling;
import com.cloudbees.opscenter.replication.ItemReplicationTask;
import com.cloudbees.opscenter.replication.ReplicationSession;
import com.cloudbees.opscenter.replication.ItemReplicationFileSet;

import org.jenkinsci.plugins.workflow.job.WorkflowJob;

import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("unused")
public class PromoteToProductionAction implements Action {
    private final WorkflowJob job;
    private static final Logger LOGGER = Logger.getLogger(PromoteToProductionAction.class.getName());

    PromoteToProductionAction(WorkflowJob job) {
        this.job = job;
    }

    public String getIconFileName() {
        if (hasPermission(job)) {
            return "/plugin/promote-mm/images/replication.png";
        }
        return null;
    }

    @SuppressWarnings("WeakerAccess")
    public boolean hasPermission(Job job) {
        return job.getACL().hasPermission(getPermission());
    }

    private void checkPermission(Job job) {
        job.getACL().checkPermission(getPermission());
    }

    private String getTargetPath(Job job) {
        String rootUrl = Jenkins.getInstance().getRootUrl();
        assert rootUrl != null;

        String assetFolderName = job.getFullName().split("/")[0];
        Boolean subFolderExists = job.getFullName().chars().filter(ch -> ch == '/').count() > 1;
        String subFolderName = "";
        if (subFolderExists) {
            subFolderName = "/" + job.getFullName().split("/")[1];
        }

        String buName = "";
        Pattern pattern = Pattern.compile("https?://(.*?)/(.*?)playground/");
        Matcher matcher = pattern.matcher(rootUrl);
        if (matcher.matches()) {
            buName = matcher.group(2);
        }

        return buName + "production/" + assetFolderName + subFolderName;

    }

    private void checkConfiguration(WorkflowJob job) {
        job.getProperty(jenkins.model.BuildDiscarderProperty.class).getStrategy();
        // TODO: add check configuration steps
    }

    public String getDisplayName() {
        return "Promote to Production";
    }

    public String getUrlName() {
        return "promote-to-prod";
    }

    public Job getJob() {
        return job;
    }

    public Permission getPermission() {
        return Job.DELETE;
    }

    public synchronized void doSubmit(StaplerRequest req, StaplerResponse resp) throws IOException, ServletException {
        checkPermission(job);
        try {
            checkConfiguration(job);

            ReplicationSession session = new ReplicationSession(job)
                    .withRemotePath(new RemotePath("cjp://" + getTargetPath(job)))
                    .withFileSet(ItemReplicationFileSet.newInstance(job, ReplicationMode.COPY));
            session.setErrorHandling(ErrorHandling.IGNORE_WARNINGS);
            ItemReplicationTask.scheduleCopyTask(session);

        } catch (Exception e) {
            throw new ServletException("Promotion is impossible", e);
        }
        resp.sendRedirect2(job.getAbsoluteUrl());
    }

    @Extension
    public static class ActionInjector extends TransientActionFactory<WorkflowJob> {
        @Nonnull
        @Override
        public Collection<PromoteToProductionAction> createFor(WorkflowJob p) {
            ArrayList<PromoteToProductionAction> list = new ArrayList<PromoteToProductionAction>();

            list.add(new PromoteToProductionAction(p));

            return list;
        }

        @Override
        public Class type() {
            return Job.class;
        }
    }
}