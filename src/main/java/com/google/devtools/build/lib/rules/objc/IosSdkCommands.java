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

package com.google.devtools.build.lib.rules.objc;

import static com.google.devtools.build.lib.rules.objc.ObjcProvider.FRAMEWORK_DIR;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.devtools.build.lib.vfs.PathFragment;
import com.google.devtools.build.xcode.common.Platform;
import com.google.devtools.build.xcode.common.TargetDeviceFamily;
import com.google.devtools.build.xcode.util.Interspersing;
import com.google.devtools.build.xcode.xcodegen.proto.XcodeGenProtos.XcodeprojBuildSetting;

import java.util.List;

/**
 * Utility code for use when generating iOS SDK commands.
 */
public class IosSdkCommands {
  // TODO(bazel-team): Make these settings parameterizeable.
  public static final String MINIMUM_OS_VERSION = "7.0";
  public static final ImmutableList<TargetDeviceFamily> TARGET_DEVICE_FAMILIES =
      ImmutableList.of(TargetDeviceFamily.IPHONE);

  public static final String DEVELOPER_DIR = "/Applications/Xcode.app/Contents/Developer";
  public static final String BIN_DIR =
      DEVELOPER_DIR + "/Toolchains/XcodeDefault.xctoolchain/usr/bin";
  public static final String ACTOOL_PATH = DEVELOPER_DIR + "/usr/bin/actool";
  public static final String IBTOOL_PATH = DEVELOPER_DIR + "/usr/bin/ibtool";
  public static final String MOMC_PATH = DEVELOPER_DIR + "/usr/bin/momc";

  // There is a handy reference to many clang warning flags at
  // http://nshipster.com/clang-diagnostics/
  // There is also a useful narrative for many Xcode settings at
  // http://www.xs-labs.com/en/blog/2011/02/04/xcode-build-settings/
  @VisibleForTesting
  static final ImmutableMap<String, String> DEFAULT_WARNINGS =
      new ImmutableMap.Builder<String, String>()
          .put("GCC_WARN_64_TO_32_BIT_CONVERSION", "-Wshorten-64-to-32")
          .put("CLANG_WARN_BOOL_CONVERSION", "-Wbool-conversion")
          .put("CLANG_WARN_CONSTANT_CONVERSION", "-Wconstant-conversion")
          // Double-underscores are intentional - thanks Xcode.
          .put("CLANG_WARN__DUPLICATE_METHOD_MATCH", "-Wduplicate-method-match")
          .put("CLANG_WARN_EMPTY_BODY", "-Wempty-body")
          .put("CLANG_WARN_ENUM_CONVERSION", "-Wenum-conversion")
          .put("CLANG_WARN_INT_CONVERSION", "-Wint-conversion")
          .put("CLANG_WARN_UNREACHABLE_CODE", "-Wunreachable-code")
          .put("GCC_WARN_ABOUT_RETURN_TYPE", "-Wmismatched-return-types")
          .put("GCC_WARN_UNDECLARED_SELECTOR", "-Wundeclared-selector")
          .put("GCC_WARN_UNINITIALIZED_AUTOS", "-Wuninitialized")
          .put("GCC_WARN_UNUSED_FUNCTION", "-Wunused-function")
          .put("GCC_WARN_UNUSED_VARIABLE", "-Wunused-variable")
          .build();

  /**
   * Clang's default optimization level is -O0, so use this build setting to make Xcode match this.
   * If a different -O? flag is given after this, it (the latter flag) will take precedence.
   */
  static final ImmutableList<String> DEFAULT_COMPILER_FLAGS = ImmutableList.of("-O0");

  static final ImmutableList<String> DEFAULT_LINKER_FLAGS = ImmutableList.of("-ObjC");

  private IosSdkCommands() {
    throw new UnsupportedOperationException("static-only");
  }

  private static String platformDir(ObjcConfiguration configuration) {
    return DEVELOPER_DIR + "/Platforms/" + configuration.getPlatform().getNameInPlist()
        + ".platform";
  }

  public static String sdkDir(ObjcConfiguration configuration) {
    return platformDir(configuration) + "/Developer/SDKs/"
        + configuration.getPlatform().getNameInPlist() + configuration.getIosSdkVersion() + ".sdk";
  }

  private static Iterable<PathFragment> uniqueParentDirectories(Iterable<PathFragment> paths) {
    ImmutableSet.Builder<PathFragment> parents = new ImmutableSet.Builder<>();
    for (PathFragment path : paths) {
      parents.add(path.getParentDirectory());
    }
    return parents.build();
  }

  public static List<String> commonLinkAndCompileArgsForClang(
      ObjcProvider provider, ObjcConfiguration configuration) {
    ImmutableList.Builder<String> builder = new ImmutableList.Builder<>();
    if (configuration.getPlatform() == Platform.SIMULATOR) {
      builder.add("-mios-simulator-version-min=" + MINIMUM_OS_VERSION);
    }

    if (configuration.generateDebugSymbols()) {
      builder.add("-g");
    }

    return builder
        .add("-arch", configuration.getIosCpu())
        .add("-isysroot", sdkDir(configuration))
        // TODO(bazel-team): Pass framework search paths to Xcodegen.
        .add("-F", sdkDir(configuration) + "/Developer/Library/Frameworks")
        // Add custom (non-SDK) framework search paths. For each framework foo/bar.framework,
        // include "foo" as a search path.
        .addAll(Interspersing.beforeEach(
            "-F",
            PathFragment.safePathStrings(uniqueParentDirectories(provider.get(FRAMEWORK_DIR)))))
        .build();
  }

  public static Iterable<String> compileArgsForClang(ObjcConfiguration configuration) {
    return Iterables.concat(
        DEFAULT_WARNINGS.values(),
        DEFAULT_COMPILER_FLAGS,
        platformSpecificCompileArgsForClang(configuration)
    );
  }

  private static List<String> platformSpecificCompileArgsForClang(ObjcConfiguration configuration) {
    switch (configuration.getPlatform()) {
      case DEVICE:
        return ImmutableList.of();
      case SIMULATOR:
        // These are added by Xcode when building, because the simulator is built on OSX
        // frameworks so we aim compile to match the OSX objc runtime.
        return ImmutableList.of(
          "-fexceptions",
          "-fasm-blocks",
          "-fobjc-abi-version=2",
          "-fobjc-legacy-dispatch");
      default:
        throw new IllegalStateException("Unknown configuration type: "
            + configuration.getPlatform());
    }
  }

  public static Iterable<? extends XcodeprojBuildSetting> defaultWarningsForXcode() {
    return Iterables.transform(DEFAULT_WARNINGS.keySet(),
        new Function<String, XcodeprojBuildSetting>() {
      @Override
      public XcodeprojBuildSetting apply(String key) {
        return XcodeprojBuildSetting.newBuilder().setName(key).setValue("YES").build();
      }
    });
  }
}
