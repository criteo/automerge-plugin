automerge-plugin
================

[![Build Status](https://travis-ci.org/criteo/automerge-plugin.svg?branch=master)](https://travis-ci.org/criteo/automerge-plugin)

A [gerrit](https://code.google.com/p/gerrit/) plugin that takes care of
automatically merging reviews when all approvals are present.

Also, it introduces the concept of cross-repository reviews.
Cross repository reviews are reviews that share a common topic, and are all
in different gerrit repositories. They will be merged at the same time,
when all approvals for all reviews are present, and all reviews are mergeable.

Requires Gerrit 2.11 or later.
