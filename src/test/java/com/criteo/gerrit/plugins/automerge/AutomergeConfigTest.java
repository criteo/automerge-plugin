package com.criteo.gerrit.plugins.automerge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.google.gerrit.server.config.SitePaths;

import org.eclipse.jgit.lib.Config;
import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;

public class AutomergeConfigTest {

  @Test
  public void testGetDefaultConfig() {
    final Config conf = new Config();
    try {
      final SitePaths paths = new SitePaths(new File("."));

      final AutomergeConfig amconf = new AutomergeConfig(conf, paths);

      assertEquals(amconf.getBotEmail(), AutomergeConfig.getDefaultBotEmail());
      assertEquals(amconf.getTopicPrefix(), AutomergeConfig.getDefaultTopicPrefix());
    } catch (final FileNotFoundException e) {
      fail();
    }
  }

  @Test
  public void testGetValues() {
    final Config conf = new Config();
    try {
      final SitePaths paths = new SitePaths(new File("."));

      conf.setString(AutomergeConfig.AUTOMERGE_SECTION, null, AutomergeConfig.BOT_EMAIL_KEY, "Foo@bar.com");
      conf.setString(AutomergeConfig.AUTOMERGE_SECTION, null, AutomergeConfig.TOPIC_PREFIX_KEY, "fake_topic_prefix");

      final AutomergeConfig amconf = new AutomergeConfig(conf, paths);
      assertEquals(amconf.getBotEmail(), "Foo@bar.com");
      assertEquals(amconf.getTopicPrefix(), "fake_topic_prefix");
    } catch (final FileNotFoundException e) {
      fail();
    }
  }
}
