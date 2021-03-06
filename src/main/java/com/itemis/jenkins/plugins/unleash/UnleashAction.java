package com.itemis.jenkins.plugins.unleash;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.servlet.ServletException;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.itemis.jenkins.plugins.unleash.permalinks.LastFailedReleasePermalink;
import com.itemis.jenkins.plugins.unleash.permalinks.LastSuccessfulReleasePermalink;
import com.itemis.maven.plugins.unleash.util.MavenVersionUtil;

import hudson.maven.MavenModule;
import hudson.maven.MavenModuleSet;
import hudson.model.PermalinkProjectAction;
import hudson.model.PermalinkProjectAction.Permalink;

public class UnleashAction implements PermalinkProjectAction {
  private static final Logger LOGGER = Logger.getLogger(UnleashAction.class.getName());

  private MavenModuleSet project;
  private boolean useGlobalVersion;
  private boolean allowLocalReleaseArtifacts;
  private boolean commitBeforeTagging;
  private boolean errorLog;
  private boolean debugLog;

  public UnleashAction(MavenModuleSet project, boolean useGlobalVersion, boolean allowLocalReleaseArtifacts,
      boolean commitBeforeTagging, boolean errorLog, boolean debugLog) {
    this.project = project;
    this.useGlobalVersion = useGlobalVersion;
    this.allowLocalReleaseArtifacts = allowLocalReleaseArtifacts;
    this.commitBeforeTagging = commitBeforeTagging;
    this.errorLog = errorLog;
    this.debugLog = debugLog;
  }

  @Override
  public String getIconFileName() {
    return "/plugin/unleash/img/unleash.png";
  }

  @Override
  public String getDisplayName() {
    return "Trigger Unleash Maven Plugin";
  }

  @Override
  public String getUrlName() {
    return "unleash";
  }

  @Override
  public List<Permalink> getPermalinks() {
    return Lists.newArrayList(LastSuccessfulReleasePermalink.INSTANCE, LastFailedReleasePermalink.INSTANCE);
  }

  public String computeReleaseVersion() {
    String version = "NaN";
    final MavenModule rootModule = this.project.getRootModule();
    if (rootModule != null && StringUtils.isNotBlank(rootModule.getVersion())) {
      version = MavenVersionUtil.calculateReleaseVersion(rootModule.getVersion());
    }
    return version;
  }

  public String computeReleaseVersion(MavenModule module) {
    String version = "NaN";
    if (module != null && StringUtils.isNotBlank(module.getVersion())) {
      version = MavenVersionUtil.calculateReleaseVersion(module.getVersion());
    }
    return version;
  }

  public String computeNextDevelopmentVersion() {
    String version = "NaN";
    final MavenModule rootModule = this.project.getRootModule();
    if (rootModule != null && StringUtils.isNotBlank(rootModule.getVersion())) {
      version = MavenVersionUtil.calculateNextSnapshotVersion(rootModule.getVersion());
    }
    return version;
  }

  public String computeNextDevelopmentVersion(MavenModule module) {
    String version = "NaN";
    if (module != null && StringUtils.isNotBlank(module.getVersion())) {
      version = MavenVersionUtil.calculateNextSnapshotVersion(module.getVersion());
    }
    return version;
  }

  public boolean isUseGlobalVersion() {
    return this.useGlobalVersion;
  }

  public boolean isNotUseGlobalVersion() {
    return !this.useGlobalVersion;
  }

  public void setUseGlobalVersion(boolean useGlobalVersion) {
    this.useGlobalVersion = useGlobalVersion;
  }

  public boolean isAllowLocalReleaseArtifacts() {
    return this.allowLocalReleaseArtifacts;
  }

  public void setAllowLocalReleaseArtifacts(boolean allowLocalReleaseArtifacts) {
    this.allowLocalReleaseArtifacts = allowLocalReleaseArtifacts;
  }

  public boolean isCommitBeforeTagging() {
    return this.commitBeforeTagging;
  }

  public void setCommitBeforeTagging(boolean commitBeforeTagging) {
    this.commitBeforeTagging = commitBeforeTagging;
  }

  public boolean isErrorLog() {
    return this.errorLog;
  }

  public void setErrorLog(boolean errorLog) {
    this.errorLog = errorLog;
  }

  public boolean isDebugLog() {
    return this.debugLog;
  }

  public void setDebugLog(boolean debugLog) {
    this.debugLog = debugLog;
  }

  public List<MavenModule> getAllMavenModules() {
    List<MavenModule> modules = Lists.newArrayList();
    modules.addAll(this.project.getModules());
    return modules;
  }

  public void doSubmit(StaplerRequest req, StaplerResponse resp) throws IOException, ServletException {
    RequestWrapper requestWrapper = new RequestWrapper(req);

    UnleashArgumentsAction arguments = new UnleashArgumentsAction();
    boolean globalVersions = requestWrapper.getBoolean("useGlobalVersion");

    arguments.setUseGlobalReleaseVersion(globalVersions);
    if (globalVersions) {
      arguments.setGlobalReleaseVersion(requestWrapper.getString("releaseVersion"));
      arguments.setGlobalDevelopmentVersion(requestWrapper.getString("developmentVersion"));
    } else {
      arguments.setGlobalReleaseVersion(computeReleaseVersion());
      arguments.setGlobalDevelopmentVersion(computeNextDevelopmentVersion());
    }

    arguments.setAllowLocalReleaseArtifacts(requestWrapper.getBoolean("allowLocalReleaseArtifacts"));
    arguments.setCommitBeforeTagging(requestWrapper.getBoolean("commitBeforeTagging"));
    arguments.setErrorLog(requestWrapper.getBoolean("errorLog"));
    arguments.setDebugLog(requestWrapper.getBoolean("debugLog"));

    if (this.project.scheduleBuild(0, new UnleashCause(), arguments)) {
      resp.sendRedirect(req.getContextPath() + '/' + this.project.getUrl());
    } else {
      resp.sendRedirect(req.getContextPath() + '/' + this.project.getUrl() + '/' + getUrlName() + "/failed");
    }
  }

  static class RequestWrapper {
    private final StaplerRequest request;

    public RequestWrapper(StaplerRequest request) throws ServletException {
      this.request = request;
    }

    private String getString(String key) throws javax.servlet.ServletException, java.io.IOException {
      Map parameters = this.request.getParameterMap();
      Object o = parameters.get(key);
      if (o != null) {
        if (o instanceof String) {
          return (String) o;
        } else if (o.getClass().isArray()) {
          Object firstParam = ((Object[]) o)[0];
          if (firstParam instanceof String) {
            return (String) firstParam;
          }
        }
      }
      return null;
    }

    private boolean getBoolean(String key) {
      Map parameters = this.request.getParameterMap();
      String flag = null;

      Object o = parameters.get(key);
      if (o != null) {
        if (o instanceof String) {
          flag = (String) o;
        } else if (o.getClass().isArray()) {
          Object firstParam = ((Object[]) o)[0];
          if (firstParam instanceof String) {
            flag = (String) firstParam;
          }
        }
      }
      return Objects.equal("true", flag) || Objects.equal("on", flag);
    }
  }
}
