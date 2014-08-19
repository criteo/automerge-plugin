package com.criteo.gerrit.plugins.automerge;

import static org.junit.Assert.assertEquals;

import org.eclipse.jgit.lib.Config;
import org.junit.Test;

public class AutomergeConfigTest {

  @Test
  public void testGetDefaultConfig() {
    final Config conf = new Config();
    final AutomergeConfig amconf = new AutomergeConfig(conf);

    assertEquals(amconf.getBotEmail(), AutomergeConfig.getDefaultBotEmail());
    assertEquals(amconf.getTopicPrefix(), AutomergeConfig.getDefaultTopicPrefix());
  }

  @Test
  public void testGetValues() {
    final Config conf = new Config();
    conf.setString(AutomergeConfig.AUTOMERGE_SECTION, null, AutomergeConfig.BOT_EMAIL_KEY, "Foo@bar.com");
    conf.setString(AutomergeConfig.AUTOMERGE_SECTION, null, AutomergeConfig.TOPIC_PREFIX_KEY, "fake_prefix");

    final AutomergeConfig amconf = new AutomergeConfig(conf);
    assertEquals(amconf.getBotEmail(), "Foo@bar.com");
    assertEquals(amconf.getTopicPrefix(), "fake_prefix");
  }
}
