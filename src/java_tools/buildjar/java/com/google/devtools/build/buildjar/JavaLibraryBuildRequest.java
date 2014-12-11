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

package com.google.devtools.build.buildjar;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.devtools.build.buildjar.javac.plugins.BlazeJavaCompilerPlugin;
import com.google.devtools.build.buildjar.javac.plugins.dependency.DependencyModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * All the information needed to perform a single Java library build operation.
 */
public final class JavaLibraryBuildRequest {
  private boolean compressJar;

  private final List<String> sourceFiles = new ArrayList<>();
  private final List<String> sourceJars = new ArrayList<>();
  private final List<String> messageFiles = new ArrayList<>();
  private final List<String> resourceFiles = new ArrayList<>();
  private final List<String> resourceJars = new ArrayList<>();
  /** Resource files that should be put in the root of the output jar. */
  private final List<String> rootResourceFiles = new ArrayList<>();

  private String classPath;

  private String processorPath = "";
  private List<String> processorNames = new ArrayList<>();

  // Since the default behavior of this tool with no arguments is
  // "rm -fr <classDir>", let's not default to ".", shall we?
  private String classDir = "classes";
  private String tempDir = "_tmp";

  private String outputJar;
  private String genDir;

  // Post processors
  private final List<AbstractPostProcessor> postProcessors = new ArrayList<>();

  // The originating target of this request and the associated rule kind.
  String ruleKind;
  String targetLabel;

  private List<String> javacOpts = new ArrayList<>();

  /**
   * Where to store source files generated by annotation processors.
   */
  private String sourceGenDir;

  /**
   * The path to an output jar for source files generated by annotation processors.
   */
  private String generatedSourcesOutputJar;

  /**
   * The path to the ToolContext file for outputting fixes.
   */
  String toolContextProtoFile;

  /**
   * Repository for all dependency-related information.
   */
  private final DependencyModule dependencyModule;
  
  /**
   * List of plugins that are given to javac.
   */
  private final List<BlazeJavaCompilerPlugin> plugins;

  /**
   * Constructs a build from a list of command args. Sets the same JavacRunner
   * for both compilation and annotation processing.
   *
   * @param args the list of command line args
   * @throws InvalidCommandLineException on any command line error
   */
  JavaLibraryBuildRequest(List<String> args) throws InvalidCommandLineException {
    dependencyModule = processCommandlineArgs(args);
    plugins = ImmutableList.<BlazeJavaCompilerPlugin>of(getDependencyModule().getPlugin());
  }
  
  /**
   * Constructs a build from a list of command args. Sets the same JavacRunner
   * for both compilation and annotation processing.
   *
   * @param args the list of command line args
   * @param extraPlugins extraneous plugins to use in addition to the strict dependency module.
   * @throws InvalidCommandLineException on any command line error
   */
  JavaLibraryBuildRequest(List<String> args, List<BlazeJavaCompilerPlugin> extraPlugins) 
      throws InvalidCommandLineException {
    dependencyModule = processCommandlineArgs(args);
    plugins = ImmutableList.<BlazeJavaCompilerPlugin>builder()
        .add(getDependencyModule().getPlugin())
        .addAll(extraPlugins)
        .build();
  }

  public boolean compressJar() {
    return compressJar;
  }

  public List<String> getSourceFiles() {
    // TODO(bazel-team): This is being modified after parsing to add files from source jars.
    return sourceFiles;
  }

  public List<String> getSourceJars() {
    return Collections.unmodifiableList(sourceJars);
  }

  public List<String> getMessageFiles() {
    return Collections.unmodifiableList(messageFiles);
  }

  public List<String> getResourceFiles() {
    return Collections.unmodifiableList(resourceFiles);
  }

  public List<String> getResourceJars() {
    return Collections.unmodifiableList(resourceJars);
  }

  public List<String> getRootResourceFiles() {
    return Collections.unmodifiableList(rootResourceFiles);
  }

  public String getClassPath() {
    return classPath;
  }

  public String getProcessorPath() {
    return processorPath;
  }

  public List<String> getProcessors() {
    // TODO(bazel-team): This might be modified by a JavaLibraryBuilder to enable specific
    // annotation processors.
    return processorNames;
  }

  public String getClassDir() {
    return classDir;
  }

  public String getTempDir() {
    return tempDir;
  }

  public String getOutputJar() {
    return outputJar;
  }

  public String getGenDir() {
    return genDir;
  }

  public List<String> getJavacOpts() {
    return Collections.unmodifiableList(javacOpts);
  }

  void setJavacOpts(List<String> javacOpts) {
    this.javacOpts = javacOpts;
  }

  public String getSourceGenDir() {
    return sourceGenDir;
  }

  public String getGeneratedSourcesOutputJar() {
    return generatedSourcesOutputJar;
  }

  /**
   * Processes the command line arguments.
   *
   * @throws InvalidCommandLineException on an invalid option being passed.
   */
  DependencyModule processCommandlineArgs(List<String> args) throws InvalidCommandLineException {
    DependencyModule.Builder builder = new DependencyModule.Builder();
    for (int i = 0; i < args.size(); i++) {
      String arg = args.get(i);
      switch (arg) {
        case "--javacopts":
          // Collect additional arguments to javac.
          // Assumes that javac options do not start with "--".
          // otherwise we have to do something like adding a "--"
          // terminator to the passed arguments.
          i = collectFlagArguments(args, i, javacOpts, "--");
          break;
        case "--direct_dependency": {
          String jar = getArgument(args, i++, arg);
          String target = getArgument(args, i++, arg);
          builder.addDirectMapping(jar, target);
          break;
        }
        case "--indirect_dependency": {
          String jar = getArgument(args, i++, arg);
          String target = getArgument(args, i++, arg);
          builder.addIndirectMapping(jar, target);
          break;
        }
        case "--strict_java_deps":
          builder.setStrictJavaDeps(getArgument(args, i++, arg));
          break;
        case "--output_deps":
          builder.setOutputDepsFile(getArgument(args, i++, arg));
          break;
        case "--output_deps_proto":
          builder.setOutputDepsProtoFile(getArgument(args, i++, arg));
          break;
        case "--tool_context_proto":
          toolContextProtoFile = getArgument(args, i++, arg);
          break;
        case "--deps_artifacts":
          List<String> depsArtifacts = new ArrayList<>();
          i = collectFlagArguments(args, i, depsArtifacts, "--");
          builder.addDepsArtifacts(depsArtifacts);
          break;
        case "--reduce_classpath":
          builder.setReduceClasspath();
          break;
        case "--sourcegendir":
          sourceGenDir = getArgument(args, i++, arg);
          break;
        case "--generated_sources_output":
          generatedSourcesOutputJar = getArgument(args, i++, arg);
          break;
        default:
          i = processArg(arg, args, i);
          if (i == -1) {
            throw new InvalidCommandLineException("unknown option : '" + arg + "'");
          }
      }
    }
    builder.setRuleKind(ruleKind);
    builder.setTargetLabel(targetLabel);
    return builder.build();
  }


  /**
   * Pre-processes an argument list, expanding options &at;filename to read in
   * the content of the file and add it to the list of arguments.
   *
   * @param args the List of arguments to pre-process.
   * @return the List of pre-processed arguments.
   * @throws IOException if one of the files containing options cannot be read.
   */
  static List<String> expandArguments(List<String> args) throws IOException {
    List<String> expanded = new ArrayList<>(args.size());
    for (String arg : args) {
      expandArgument(arg, expanded);
    }
    return expanded;
  }

  /**
   * Expands a single argument, expanding options &at;filename to read in
   * the content of the file and add it to the list of processed arguments.
   *
   * @param arg the argument to pre-process.
   * @param expanded the List of pre-processed arguments.
   * @throws IOException if one of the files containing options cannot be read.
   */
  private static void expandArgument(String arg, List<String> expanded) throws IOException {
    if (arg.startsWith("@")) {
      for (String line : Files.readAllLines(Paths.get(arg.substring(1)), UTF_8)) {
        if (line.length() > 0) {
          expandArgument(line, expanded);
        }
      }
    } else {
      expanded.add(arg);
    }
  }

  /**
   * Collects the arguments for a command line flag until it finds a flag that
   * starts with the terminatorPrefix.
   *
   * @param args
   * @param startIndex the start index in the args to collect the flag arguments
   *        from
   * @param flagArguments the collected flag arguments
   * @param terminatorPrefix the terminator prefix to stop collecting of
   *        argument flags.
   * @return the index of the first argument that started with the
   *         terminatorPrefix.
   */
  static int collectFlagArguments(
      List<String> args, int startIndex, List<String> flagArguments, String terminatorPrefix) {
    for (startIndex++; startIndex < args.size(); startIndex++) {
      String name = args.get(startIndex);
      if (name.startsWith(terminatorPrefix)) {
        return startIndex - 1;
      }
      flagArguments.add(name);
    }
    return startIndex;
  }

  /**
   * Collects the arguments for the --processors command line flag.  Delegates to
   * {@link #collectFlagArguments} and validates the arguments provided.
   *
   * @param args
   * @param startIndex the start index in the args to collect the flag arguments
   *        from
   * @param flagArguments the collected flag arguments
   * @param terminatorPrefix the terminator prefix to stop collecting of
   *        argument flags.
   * @return the index of the first argument that started with the
   *         terminatorPrefix.
   */
  private static int collectProcessorArguments(
      List<String> args, int startIndex, List<String> flagArguments, String terminatorPrefix)
      throws InvalidCommandLineException {
    int oldFlagArgumentsSize = flagArguments.size();
    int result = collectFlagArguments(args, startIndex, flagArguments, terminatorPrefix);
    for (int i = oldFlagArgumentsSize; i < flagArguments.size(); i++) {
      String arg = flagArguments.get(i);
      if (arg.contains(",")) {
        throw new InvalidCommandLineException("processor argument may not contain commas: " + arg);
      }
    }
    return result;
  }

  static String getArgument(List<String> args, int i, String arg)
      throws InvalidCommandLineException {
    if (i + 1 < args.size()) {
      return args.get(i + 1);
    }
    throw new InvalidCommandLineException(arg + ": missing argument");
  }

  protected int processArg(String arg, List<String> args, int i)
      throws InvalidCommandLineException {
    switch (arg) {
      case "--sources":
        return collectFlagArguments(args, i, sourceFiles, "-");
      case "--source_jars":
        return collectFlagArguments(args, i, sourceJars, "-");
      case "--messages":
        return collectFlagArguments(args, i, messageFiles, "-");
      case "--resources":
        return collectFlagArguments(args, i, resourceFiles, "-");
      case "--resource_jars":
        return collectFlagArguments(args, i, resourceJars, "-");
      case "--classpath_resources":
        return collectFlagArguments(args, i, rootResourceFiles, "-");
      case "--classpath":
        classPath = getArgument(args, i, arg);
        return i + 1;
      case "--processorpath":
        processorPath = getArgument(args, i, arg);
        return i + 1;
      case "--processors":
        return collectProcessorArguments(args, i, processorNames, "-");
      case "--output":
        outputJar = getArgument(args, i, arg);
        return i + 1;
      case "--classdir":
        classDir = getArgument(args, i, arg);
        return i + 1;
      case "--tempdir":
        tempDir = getArgument(args, i, arg);
        return i + 1;
      case "--gendir":
        genDir = getArgument(args, i, arg);
        return i + 1;
      case "--post_processor":
        return addExternalPostProcessor(args, i, arg);
      case "--compress_jar":
        compressJar = true;
        return i;
      case "--rule_kind":
        ruleKind = getArgument(args, i, arg);
        return i + 1;
      case "--target_label":
        targetLabel = getArgument(args, i, arg);
        return i + 1;
      default:
        return -1;
    }
  }

  private int addExternalPostProcessor(List<String> args, int i, String arg)
      throws InvalidCommandLineException {
    String processorName = getArgument(args, i++, arg);
    List<String> arguments = new ArrayList<String>();
    i = collectFlagArguments(args, i, arguments, "--");
    // TODO(bazel-team): there is no check than the same post processor is not added twice.
    //   We should either forbid multiple add of the same post processor or use a processor factory
    //   to allow multiple add of the same post processor. Anyway, this binary is invoked by Blaze
    //   and not manually.
    postProcessors.add(AbstractPostProcessor.create(processorName, arguments));
    return i;
  }

  List<AbstractPostProcessor> getPostProcessors() {
    return postProcessors;
  }

  public List<BlazeJavaCompilerPlugin> getPlugins() {
    return plugins;
  }

  public DependencyModule getDependencyModule() {
    return dependencyModule;
  }
}
