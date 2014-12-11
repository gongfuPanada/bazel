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
package com.google.devtools.build.lib.skyframe;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.devtools.build.lib.view.ConfiguredTarget;
import com.google.devtools.build.lib.view.LabelAndConfiguration;
import com.google.devtools.build.skyframe.SkyKey;
import com.google.devtools.build.skyframe.SkyValue;

import java.util.Collection;

/**
 * A test completion value represents the completion of a test target. This includes the execution
 * of all test shards and repeated runs, if applicable.
 */
public class TestCompletionValue implements SkyValue {
  static final TestCompletionValue TEST_COMPLETION_MARKER = new TestCompletionValue();

  private TestCompletionValue() { }

  public static SkyKey key(LabelAndConfiguration lac, boolean exclusive) {
    return new SkyKey(SkyFunctions.TEST_COMPLETION, new TestCompletionKey(lac, exclusive));
  }

  public static Iterable<SkyKey> keys(Collection<ConfiguredTarget> targets,
                                      final boolean exclusive) {
    return Iterables.transform(targets, new Function<ConfiguredTarget, SkyKey>() {
      @Override
      public SkyKey apply(ConfiguredTarget ct) {
        return new SkyKey(SkyFunctions.TEST_COMPLETION, 
            new TestCompletionKey(new LabelAndConfiguration(ct), exclusive));
      }
    });
  }
  
  static class TestCompletionKey {
    private final LabelAndConfiguration lac;
    private final boolean exclusiveTesting;

    TestCompletionKey(LabelAndConfiguration lac, boolean exclusive) {
      this.lac = lac;
      this.exclusiveTesting = exclusive;
    }

    public LabelAndConfiguration getLabelAndConfiguration() {
      return lac;
    }

    public boolean isExclusiveTesting() {
      return exclusiveTesting;
    }
  }
}
