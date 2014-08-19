package com.criteo.gerrit.plugins.automerge;

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

import java.io.IOException;
import java.util.Set;

public class ReviewUpdater {
  @Inject
  private AccountByEmailCache byEmailCache;

  @Inject
  ChangeData.Factory changeDataFactory;

  @Inject
  private ChangeControl.GenericFactory changeFactory;

  @Inject
  private ChangesCollection collection;

  final AutomergeConfig config;
  @Inject
  Provider<ReviewDb> db;

  @Inject
  private IdentifiedUser.GenericFactory factory;

  @Inject
  Provider<PostReview> reviewer;

  public ReviewUpdater(final AutomergeConfig config) {
    this.config = config;
  }

  public void commentOnReview(final int number, final String comment) throws NoSuchChangeException, OrmException,
      AuthException, BadRequestException, UnprocessableEntityException, IOException {
    final ReviewInput message = new ReviewInput();
    message.message = comment;
    final Set<Account.Id> ids = byEmailCache.get(config.getBotEmail());
    final IdentifiedUser bot = factory.create(ids.iterator().next());
    final ChangeControl ctl = changeFactory.controlFor(new Change.Id(number), bot);
    final ChangeData changeData = changeDataFactory.create(db.get(), new Change.Id(number));

    final RevisionResource r = new RevisionResource(collection.parse(ctl), changeData.currentPatchSet());
    reviewer.get().apply(r, message);
  }

  public void setMinusTwo(final int number, final String comment) throws NoSuchChangeException, OrmException,
      AuthException, BadRequestException, UnprocessableEntityException, IOException {
    final ReviewInput message = new ReviewInput();
    message.message = comment;
    message.label("Code-Review", -2);
    final Set<Account.Id> ids = byEmailCache.get(config.getBotEmail());
    final IdentifiedUser bot = factory.create(ids.iterator().next());
    final ChangeControl ctl = changeFactory.controlFor(new Change.Id(number), bot);
    final ChangeData changeData = changeDataFactory.create(db.get(), new Change.Id(number));

    final RevisionResource r = new RevisionResource(collection.parse(ctl), changeData.currentPatchSet());
    reviewer.get().apply(r, message);
  }

}
