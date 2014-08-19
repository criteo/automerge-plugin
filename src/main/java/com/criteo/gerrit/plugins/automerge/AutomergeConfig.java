package com.criteo.gerrit.plugins.automerge;

import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.lib.Config;

import java.io.File;
import java.io.IOException;


public class AutomergeConfig {

  public final static String ATOMIC_REVIEW_DETECTED_FILE = "atomic_review_detected.txt";
  public final static String ATOMIC_REVIEWS_SAME_REPO_FILE = "atomic_review_same_repo.txt";
  public final static String AUTOMERGE_SECTION = "automerge";
  public final static String BOT_EMAIL_KEY = "botEmail";
  public final static String CANT_MERGE_COMMENT_FILE = "cantmerge.txt";

  private final static String defaultBotEmail = "qabot@criteo.com";
  private final static String defaultTopicPrefix = "crossrepo/";
  public final static String TOPIC_PREFIX_KEY = "topicPrefix";

  public static final String getDefaultBotEmail() {
    return defaultBotEmail;

  }

  public static final String getDefaultTopicPrefix() {
    return defaultTopicPrefix;
  }

  private final Config config;

  @Inject
  private SitePaths sitePaths;


  public AutomergeConfig(@GerritServerConfig final Config config) {
    this.config = config;
  }

  public final String getBotEmail() {
    final String botEmail = config.getString(AUTOMERGE_SECTION, null, BOT_EMAIL_KEY);
    if (botEmail == null) {
      return defaultBotEmail;
    }
    return botEmail;
  }

  /**
   * Return a comment from a file located in the gerrit etc_dir
   *
   * @param filename
   * @return a string containing a comment.
   * @throws IOException
   */
  public final String getCommentFromFile(final String filename) {
    final File commentFile = new File(sitePaths.etc_dir, filename);
    try {
      return FileUtils.readFileToString(commentFile);
    } catch (final IOException exc) {
      return String.format("Cannot find %s file in gerrit etc dir. Please check your gerrit configuration", filename);
    }
  }

  public final String getTopicPrefix() {
    final String topicPrefix = config.getString(AUTOMERGE_SECTION, null, TOPIC_PREFIX_KEY);
    if (topicPrefix == null) {
      return defaultTopicPrefix;
    }
    return topicPrefix;
  }

}
