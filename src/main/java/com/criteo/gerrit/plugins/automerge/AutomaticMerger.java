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
import com.google.gerrit.common.EventListener;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.change.GetRelated;
import com.google.gerrit.server.change.PostReview;
import com.google.gerrit.server.change.Submit;
import com.google.gerrit.server.data.ApprovalAttribute;
import com.google.gerrit.server.data.ChangeAttribute;
import com.google.gerrit.server.events.CommentAddedEvent;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.PatchSetCreatedEvent;
import com.google.gerrit.server.events.TopicChangedEvent;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.EnumSet;
import java.util.List;

/**
 * Starts at the same time as the gerrit server, and sets up our change hook
 * listener.
 */
public class AutomaticMerger implements EventListener, LifecycleListener {

  private final static Logger log = LoggerFactory.getLogger(AutomaticMerger.class);

  @Inject
  private GerritApi api;

  @Inject
  private AtomicityHelper atomicityHelper;

  @Inject
  ChangeData.Factory changeDataFactory;

  @Inject
  private AutomergeConfig config;

  @Inject
  Provider<ReviewDb> db;

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
  synchronized public void onEvent(final Event event) {
    if (event instanceof TopicChangedEvent) {
      onTopicChanged((TopicChangedEvent)event);
    }
    else if (event instanceof PatchSetCreatedEvent) {
      onPatchSetCreated((PatchSetCreatedEvent)event);
    }
    else if (event instanceof CommentAddedEvent) {
      onCommendAdded((CommentAddedEvent)event);
    }
  }

  private void onTopicChanged(final TopicChangedEvent event) {
    if (!atomicityHelper.isAtomicReview(event.change)) {
      return;
    }
    processNewAtomicPatchSet(event.change);
  }

  private void onPatchSetCreated(final PatchSetCreatedEvent event) {
    if (!atomicityHelper.isAtomicReview(event.change)) {
      return;
    }
    processNewAtomicPatchSet(event.change);
  }

  private void onCommendAdded(final CommentAddedEvent newComment) {
    if (!shouldProcessCommentEvent(newComment)) {
      return;
    }

    final ChangeAttribute change = newComment.change;
    final int reviewNumber = Integer.parseInt(change.number);
    try {
      checkReviewExists(reviewNumber);
      if (atomicityHelper.isSubmittable(reviewNumber)) {
        log.info(String.format("Change %d is submittable. Will try to merge all related changes.", reviewNumber));
        attemptToMerge(change);
      }
    } catch (final RestApiException | NoSuchChangeException | OrmException | IOException e) {
      log.error("An exception occured while trying to atomic merge a change.", e);
      throw new RuntimeException(e);
    }
  }

  /**
   * Returns true if the plugin must handle this comment, i.e. if we are sure it does not come
   * from this plugin (to avoid infinite loop).
   *
   * @param comment
   * @return a boolean
   */
  private boolean shouldProcessCommentEvent(CommentAddedEvent comment) {
    if (!config.getBotEmail().equals(comment.author.email)) {
      return true;
    }
    if (comment.approvals != null) {
      for (ApprovalAttribute approval : comment.approvals) {
        // See ReviewUpdate#setMinusOne
        if (!("Code-Review".equals(approval.type) && "-1".equals(approval.value))) {
          return true;
        }
      }
    }
    return false;
  }

  private void attemptToMerge(ChangeAttribute change) throws RestApiException, OrmException, NoSuchChangeException, IOException {
    final List<ChangeInfo> related = Lists.newArrayList();
    if (atomicityHelper.isAtomicReview(change)) {
      related.addAll(api.changes().query("status: open AND topic: " + change.topic)
          .withOption(ListChangesOption.CURRENT_REVISION).get());
    } else {
      ChangeApi changeApi = api.changes().id(change.project, change.branch, change.id);
      related.add(changeApi.get(EnumSet.of(ListChangesOption.CURRENT_REVISION)));
    }
    boolean submittable = true;
    boolean mergeable = true;
    for (final ChangeInfo info : related) {
      if (!info.mergeable) {
        mergeable = false;
      }
      if (!atomicityHelper.isSubmittable(info._number)) {
        submittable = false;
      }
    }
    final int reviewNumber = Integer.parseInt(change.number);

    if (submittable) {
      if (mergeable) {
        log.debug(String.format("Change %d is mergeable", reviewNumber));
        for (final ChangeInfo info : related) {
          atomicityHelper.mergeReview(info);
        }
      } else {
        reviewUpdater.commentOnReview(reviewNumber, AutomergeConfig.CANT_MERGE_COMMENT_FILE);
      }
    }
  }

  private void processNewAtomicPatchSet(final ChangeAttribute change) {
    final int reviewNumber = Integer.parseInt(change.number);
    try {
      checkReviewExists(reviewNumber);
      if (atomicityHelper.hasDependentReview(reviewNumber)) {
        log.info(String.format("Warn the user by setting -1 on change %d, as other atomic changes exists on the same repository.",
            reviewNumber));
        reviewUpdater.setMinusOne(reviewNumber, AutomergeConfig.ATOMIC_REVIEWS_SAME_REPO_FILE);
      } else {
        log.info(String.format("Detected atomic review on change %d.", reviewNumber));
        reviewUpdater.commentOnReview(reviewNumber, AutomergeConfig.ATOMIC_REVIEW_DETECTED_FILE);
      }
    } catch (RestApiException | IOException | NoSuchChangeException | OrmException e) {
      throw new RuntimeException(e);
    }
  }

  private void checkReviewExists(int reviewNumber) throws RestApiException {
    api.changes().id(reviewNumber).get(EnumSet.of(ListChangesOption.CURRENT_REVISION));
  }

  @Override
  public void start() {
    log.info("Starting automatic merger plugin.");
  }

  @Override
  public void stop() {
  }
}
