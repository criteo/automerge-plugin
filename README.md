automerge-plugin
================

[![Build Status](https://gerrit-ci.gerritforge.com/view/Plugins-stable-2.14/job/plugin-automerge-plugin-gh-bazel-stable-2.14/badge/icon)](https://gerrit-ci.gerritforge.com/view/Plugins-stable-2.14/job/plugin-automerge-plugin-gh-bazel-stable-2.14/)

A [gerrit](https://www.gerritcodereview.com) plugin that takes care of
automatically merging reviews when all approvals are present.

Also, it introduces the concept of cross-repository reviews.
Cross repository reviews are reviews that share a common topic, and are all
in different gerrit repositories. They will be merged at the same time,
when all approvals for all reviews are present, and all reviews are mergeable.

Requires Gerrit 2.14 or later.
