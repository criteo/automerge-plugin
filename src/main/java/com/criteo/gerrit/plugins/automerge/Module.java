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

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.gerrit.common.ChangeListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;

/**
 * Main automerge Guice module.
 *
 * Configures how all classes in the plugin are instantiated via Guice.
 */
public class Module extends AbstractModule {

  protected static final Supplier<Injector> injector = Suppliers.memoize(new Supplier<Injector>() {
    @Override
    public Injector get() {
      return Guice.createInjector(new Module());
    }
  });

  public static <T> T getInstance(final Class<T> type) {
    return injector.get().getInstance(type);
  }

  @Override
  protected void configure() {
    DynamicSet.bind(binder(), ChangeListener.class).to(AutomaticMerger.class);
    bind(AutomergeConfig.class).asEagerSingleton();
    bind(AtomicityHelper.class);
    bind(ReviewUpdater.class);
  }
}
