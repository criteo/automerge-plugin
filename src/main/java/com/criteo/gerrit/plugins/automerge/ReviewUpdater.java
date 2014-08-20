package com.criteo.gerrit.plugins.automerge;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountByEmailCache;
import com.google.gerrit.server.change.ChangesCollection;
import com.google.gerrit.server.change.PostReview;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Set;

public class ReviewUpdater {
  private final static Logger log = LoggerFactory.getLogger(AutomaticMerger.class);

  @Inject
  private AccountByEmailCache byEmailCache;

  @Inject
  ChangeData.Factory changeDataFactory;

  @Inject
  private ChangeControl.GenericFactory changeFactory;

  @Inject
  private ChangesCollection collection;

  @Inject
  AutomergeConfig config;

  @Inject
  Provider<ReviewDb> db;

  @Inject
  private IdentifiedUser.GenericFactory factory;

  @Inject
  Provider<PostReview> reviewer;

  public void commentOnReview(final int number, final String commentTemplate) throws NoSuchChangeException,
      OrmException, AuthException, BadRequestException, UnprocessableEntityException, IOException {
    final ReviewInput message = new ReviewInput();
    message.message = getCommentFromFile(commentTemplate);
    final Set<Account.Id> ids = byEmailCache.get(config.getBotEmail());
    final IdentifiedUser bot = factory.create(ids.iterator().next());
    final ChangeControl ctl = changeFactory.controlFor(new Change.Id(number), bot);
    final ChangeData changeData = changeDataFactory.create(db.get(), new Change.Id(number));

    final RevisionResource r = new RevisionResource(collection.parse(ctl), changeData.currentPatchSet());
    reviewer.get().apply(r, message);
  }

  private final String getCommentFromFile(final String filename) {
    try {
      return Files.toString(new File(config.getTemplatesPath(), filename), Charsets.UTF_8);

    } catch (final IOException exc) {
      final String errmsg =
          String.format("Cannot find %s file in gerrit etc dir. Please check your gerrit configuration", filename);
      log.error(errmsg);
      return errmsg;
    }
  }

  public void setMinusTwo(final int number, final String commentTemplate) throws NoSuchChangeException, OrmException,
      AuthException, BadRequestException, UnprocessableEntityException, IOException {
    final ReviewInput message = new ReviewInput();
    message.message = getCommentFromFile(commentTemplate);
    message.label("Code-Review", -2);
    final Set<Account.Id> ids = byEmailCache.get(config.getBotEmail());
    final IdentifiedUser bot = factory.create(ids.iterator().next());
    final ChangeControl ctl = changeFactory.controlFor(new Change.Id(number), bot);
    final ChangeData changeData = changeDataFactory.create(db.get(), new Change.Id(number));

    final RevisionResource r = new RevisionResource(collection.parse(ctl), changeData.currentPatchSet());
    reviewer.get().apply(r, message);
  }

}
