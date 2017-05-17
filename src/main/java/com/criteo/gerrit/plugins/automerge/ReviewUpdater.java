package com.criteo.gerrit.plugins.automerge;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.change.PostReview;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

public class ReviewUpdater {
  private final static Logger log = LoggerFactory.getLogger(AutomaticMerger.class);

  @Inject
  ChangeData.Factory changeDataFactory;

  @Inject
  AutomergeConfig config;

  @Inject
  Provider<ReviewDb> db;

  @Inject
  Provider<PostReview> reviewer;

  @Inject
  private AtomicityHelper atomicityHelper;

  public void commentOnReview(String project, int number, String commentTemplate) throws RestApiException, OrmException, IOException, NoSuchChangeException, UpdateException {
    ReviewInput comment = createComment(commentTemplate);
    applyComment(project, number, comment);
  }

    public void setMinusOne(String project, int number, String commentTemplate) throws RestApiException, OrmException, IOException, NoSuchChangeException, UpdateException {
    ReviewInput message = createComment(commentTemplate).label("Code-Review", -1);
    applyComment(project, number, message);
  }

  private ReviewInput createComment(final String commentTemplate) {
    return new ReviewInput().message(getCommentFromFile(commentTemplate));
  }

  private String getCommentFromFile(final String filename) {
     try {
       return Files.toString(new File(config.getTemplatesPath(), filename), Charsets.UTF_8);
     } catch (final IOException exc) {
       final String errmsg =
           String.format("Cannot find %s file in gerrit etc dir. Please check your gerrit configuration", filename);
       log.error(errmsg);
       return errmsg;
     }
   }

  private void applyComment(String project, int number, ReviewInput comment) throws RestApiException, OrmException, IOException, NoSuchChangeException, UpdateException {
    RevisionResource r = atomicityHelper.getRevisionResource(project, number);
    reviewer.get().apply(r, comment);
  }
}
