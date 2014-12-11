// Copyright 2014 Google Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.devtools.build.lib.rules.java;

import com.google.common.base.Function;
import com.google.devtools.build.lib.concurrent.ThreadSafety.Immutable;
import com.google.devtools.build.lib.view.Runfiles;
import com.google.devtools.build.lib.view.TransitiveInfoCollection;
import com.google.devtools.build.lib.view.TransitiveInfoProvider;

/**
 * A {@link TransitiveInfoProvider} that supplies runfiles for Java dependencies.
 */
@Immutable
public final class JavaRunfilesProvider implements TransitiveInfoProvider {
  private final Runfiles runfiles;

  public JavaRunfilesProvider(Runfiles runfiles) {
    this.runfiles = runfiles;
  }

  public Runfiles getRunfiles() {
    return runfiles;
  }

  /**
   * Returns a function that gets the Java runfiles from a {@link TransitiveInfoCollection} or
   * the empty runfiles instance if it does not contain that provider.
   */
  public static final Function<TransitiveInfoCollection, Runfiles> TO_RUNFILES =
      new Function<TransitiveInfoCollection, Runfiles>() {
        @Override
        public Runfiles apply(TransitiveInfoCollection input) {
          JavaRunfilesProvider provider = input.getProvider(JavaRunfilesProvider.class);
          return provider == null
              ? Runfiles.EMPTY
              : provider.getRunfiles();
        }
      };
}
