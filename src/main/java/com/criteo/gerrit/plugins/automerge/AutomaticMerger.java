// Copyright 2014 Criteo
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.criteo.gerrit.plugins.automerge;

import com.google.gerrit.common.ChangeListener;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.events.ChangeEvent;

import org.eclipse.jgit.lib.Config;

import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;

/**
 * Starts at the same time as the gerrit server, and sets up our
 * change hook listener.
 */
public class AutomaticMerger implements ChangeListener, LifecycleListener {

  private final AutomergeConfig config;

  @Inject
  public AutomaticMerger(@GerritServerConfig Config gerritConfig){
    this.config = new AutomergeConfig(gerritConfig);
  }

  @Override
  public void start() {
  }

  @Override
  public void stop() {
  }

  @Override
  synchronized public void onChangeEvent(ChangeEvent event) {

  }
}
