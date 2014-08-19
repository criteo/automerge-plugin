package com.criteo.gerrit.plugins.automerge;

import com.google.gerrit.server.config.GerritServerConfig;

import org.eclipse.jgit.lib.Config;

public class AutomergeConfig {

  public final static String AUTOMERGE_SECTION = "automerge";
  public final static String BOT_EMAIL_KEY = "botEmail";
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

  public final String getTopicPrefix() {
    final String topicPrefix = config.getString(AUTOMERGE_SECTION, null, TOPIC_PREFIX_KEY);
    if (topicPrefix == null) {
      return defaultTopicPrefix;
    }
    return topicPrefix;
  }
}
