package com.criteo.gerrit.plugins.automerge;

import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountByEmailCache;
import com.google.gerrit.server.change.ChangesCollection;
import com.google.gerrit.server.change.GetRelated;
import com.google.gerrit.server.change.GetRelated.RelatedInfo;
import com.google.gerrit.server.change.PostReview;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.change.Submit;
import com.google.gerrit.server.data.ChangeAttribute;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.io.IOException;
import java.util.List;
import java.util.Set;

public class AtomicityHelper {

  @Inject
  private AccountByEmailCache byEmailCache;

  @Inject
  ChangeData.Factory changeDataFactory;

  @Inject
  private ChangeControl.GenericFactory changeFactory;

  @Inject
  private ChangesCollection collection;

  AutomergeConfig config;

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
  Submit submitter;

  public AtomicityHelper(final AutomergeConfig config) {
    this.config = config;
  }

  /**
   * Check if the current patchset of the specified change has dependent
   * unmerged changes.
   *
   * @param number
   * @return true or false
   * @throws IOException
   * @throws NoSuchChangeException
   * @throws OrmException
   */
  public boolean hasDependentReview(final int number) throws IOException, NoSuchChangeException, OrmException {
    final Set<Account.Id> ids = byEmailCache.get(config.getBotEmail());
    final IdentifiedUser qabot = factory.create(ids.iterator().next());
    final ChangeControl ctl = changeFactory.controlFor(new Change.Id(number), qabot);
    final ChangeData changeData = changeDataFactory.create(db.get(), new Change.Id(number));

    final RevisionResource r = new RevisionResource(collection.parse(ctl), changeData.currentPatchSet());
    final RelatedInfo related = getRelated.apply(r);

    return related.changes.size() > 0;
  }

  /**
   * Check if a change is an atomic change or not. A change is atomic if it has
   * the atomic topic prefix.
   *
   * @param change a ChangeAttribute instance
   * @return true or false
   */
  public boolean isAtomicReview(final ChangeAttribute change) {
    return change.topic != null && change.topic.startsWith(config.getTopicPrefix());
  }

  /**
   * Check if a change is submitable.
   *
   * @param change a change number
   * @return true or false
   * @throws OrmException
   */
  public boolean isSubmittable(final int change) throws OrmException {
    final ChangeData changeData = changeDataFactory.create(db.get(), new Change.Id(change));
    final List<SubmitRecord> cansubmit = changeData.changeControl().canSubmit(db.get(), changeData.currentPatchSet());

    for (final SubmitRecord submit : cansubmit) {
      if (submit.status != SubmitRecord.Status.OK) {
        return false;
      }
    }
    return true;
  }
}
