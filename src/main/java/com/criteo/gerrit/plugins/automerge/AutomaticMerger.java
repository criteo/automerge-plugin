// Copyright 2014 Criteo
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.criteo.gerrit.plugins.automerge;

import com.google.common.collect.Lists;
import com.google.gerrit.common.ChangeListener;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ListChangesOption;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountByEmailCache;
import com.google.gerrit.server.change.ChangesCollection;
import com.google.gerrit.server.change.GetRelated;
import com.google.gerrit.server.change.PostReview;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.change.Submit;
import com.google.gerrit.server.data.ChangeAttribute;
import com.google.gerrit.server.events.ChangeEvent;
import com.google.gerrit.server.events.CommentAddedEvent;
import com.google.gerrit.server.events.PatchSetCreatedEvent;
import com.google.gerrit.server.events.TopicChangedEvent;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Starts at the same time as the gerrit server, and sets up our change hook
 * listener.
 */
public class AutomaticMerger implements ChangeListener, LifecycleListener {

  private final static Logger log = LoggerFactory.getLogger(AutomaticMerger.class);

  @Inject
  private GerritApi api;

  @Inject
  private AtomicityHelper atomicityHelper;

  @Inject
  private AccountByEmailCache byEmailCache;

  @Inject
  ChangeData.Factory changeDataFactory;

  @Inject
  private ChangeControl.GenericFactory changeFactory;

  @Inject
  private ChangesCollection collection;

  @Inject
  private AutomergeConfig config;

  @Inject
  Provider<ReviewDb> db;

  @Inject
  private IdentifiedUser.GenericFactory factory;

  @Inject
  GetRelated getRelated;

  @Inject
  MergeUtil.Factory mergeUtilFactory;

  @Inject
  Provider<PostReview> reviewer;

  @Inject
  private ReviewUpdater reviewUpdater;

  @Inject
  Submit submitter;

  @Override
  synchronized public void onChangeEvent(final ChangeEvent event) {
    log.info("A change has been recieved");
    try {
      if (event instanceof TopicChangedEvent) {
        final TopicChangedEvent newComment = (TopicChangedEvent) event;
        final ChangeAttribute change = newComment.change;
        final int reviewNumber = Integer.parseInt(change.number);
        log.info(String.format("Change on review %d is a topic change.", reviewNumber));
        try {
          api.changes().id(reviewNumber).get(EnumSet.of(ListChangesOption.CURRENT_REVISION));
        } catch (final RestApiException e1) {
          throw new RuntimeException(e1);
        }

        if (atomicityHelper.isAtomicReview(change)) {
          if (atomicityHelper.hasDependentReview(reviewNumber)) {
            log.info(String.format("Setting -2 on change %d, other atomic changes exists on the same repository.",
                reviewNumber));
            reviewUpdater.setMinusTwo(reviewNumber, AutomergeConfig.ATOMIC_REVIEWS_SAME_REPO_FILE);
          } else {
            log.info(String.format("Detected atomic review on change %d", reviewNumber));
            reviewUpdater.commentOnReview(reviewNumber, AutomergeConfig.ATOMIC_REVIEW_DETECTED_FILE);
          }
        }
      }
      if (event instanceof PatchSetCreatedEvent) {
        final PatchSetCreatedEvent newComment = (PatchSetCreatedEvent) event;

        final ChangeAttribute change = newComment.change;
        final int reviewNumber = Integer.parseInt(change.number);
        try {
          api.changes().id(reviewNumber).get(EnumSet.of(ListChangesOption.CURRENT_REVISION));
        } catch (final RestApiException e1) {
          throw new RuntimeException(e1);
        }
        if (atomicityHelper.isAtomicReview(change)) {
          if (atomicityHelper.hasDependentReview(reviewNumber)) {
            reviewUpdater.setMinusTwo(reviewNumber, AutomergeConfig.ATOMIC_REVIEWS_SAME_REPO_FILE);
          } else {
            reviewUpdater.commentOnReview(reviewNumber, AutomergeConfig.ATOMIC_REVIEW_DETECTED_FILE);
          }
        }
      }


    } catch (NoSuchChangeException | OrmException | IOException | AuthException | BadRequestException
        | UnprocessableEntityException e) {
      throw new RuntimeException(e);
    }
    if (event instanceof CommentAddedEvent) {
      final CommentAddedEvent newComment = (CommentAddedEvent) event;
      final ChangeAttribute change = newComment.change;
      final int reviewNumber = Integer.parseInt(change.number);
      try {
        api.changes().id(reviewNumber).get(EnumSet.of(ListChangesOption.CURRENT_REVISION));
      } catch (final RestApiException e1) {
        throw new RuntimeException(e1);
      }

      if (newComment.author.email.contains("qabot")) {
        return;
      }
      final String topic = change.topic;
      try {
        if (atomicityHelper.isSubmittable(Integer.parseInt(newComment.change.number))) {
          log.info(String.format("Change %d is submittable. Will try to merge all related changes.", reviewNumber));
          final List<ChangeInfo> related = Lists.newArrayList();
          if (topic != null) {
            related.addAll(api.changes().query("status: open AND topic: " + topic)
                .withOption(ListChangesOption.CURRENT_REVISION).get());
          } else {
            related.add(api.changes().id(change.id).get(EnumSet.of(ListChangesOption.CURRENT_REVISION)));
          }
          boolean mergeable = true;
          String why = null;
          for (final ChangeInfo info : related) {
            api.changes().id(change.id).get(EnumSet.of(ListChangesOption.CURRENT_REVISION));
            if (!info.mergeable) {
              mergeable = false;
              why = String.format("Review %s is approved but not mergeable.", info._number);
            }
            if (!atomicityHelper.isSubmittable(info._number)) {
              mergeable = false;
              // why = String.format("Review %s is not approved.",
              // info._number);
            }
          }
          if (mergeable) {
            log.debug(String.format("Change %d is mergeable", reviewNumber));
            for (final ChangeInfo info : related) {
              final SubmitInput input = new SubmitInput();
              input.waitForMerge = true;
              final Set<Account.Id> ids = byEmailCache.get(config.getBotEmail());
              final IdentifiedUser bot = factory.create(ids.iterator().next());
              final ChangeControl ctl = changeFactory.controlFor(new Change.Id(info._number), bot);
              final ChangeData changeData = changeDataFactory.create(db.get(), new Change.Id(info._number));

              final RevisionResource r = new RevisionResource(collection.parse(ctl), changeData.currentPatchSet());
              submitter.apply(r, input);
            }
          } else {
            if (why != null) {
              reviewUpdater.commentOnReview(reviewNumber, AutomergeConfig.CANT_MERGE_COMMENT_FILE);
            }
          }
        }
      } catch (final RestApiException e) {
        throw new RuntimeException(e);
      } catch (final OrmException e) {
        throw new RuntimeException(e);
      } catch (final NumberFormatException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch (final NoSuchChangeException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch (final RepositoryNotFoundException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch (final IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
  }

  @Override
  public void start() {
    log.info("Starting automatic merger plugin.");
  }

  @Override
  public void stop() {
  }
}
