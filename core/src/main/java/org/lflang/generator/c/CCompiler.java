/*************
 * Copyright (c) 2019-2021, The University of California at Berkeley.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 ***************/

package org.lflang.generator.c;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.lflang.FileConfig;
import org.lflang.MessageReporter;
import org.lflang.TargetConfig;
import org.lflang.TargetProperty;
import org.lflang.TargetProperty.Platform;
import org.lflang.generator.GeneratorBase;
import org.lflang.generator.GeneratorCommandFactory;
import org.lflang.generator.GeneratorUtils;
import org.lflang.generator.LFGeneratorContext;
import org.lflang.util.FileUtil;
import org.lflang.util.LFCommand;

/**
 * Responsible for creating and executing the necessary CMake command to compile code that is
 * generated by the CGenerator. This class uses CMake to compile.
 *
 * @author Soroush Bateni
 * @author Edward A. Lee
 * @author Marten Lohstroh
 * @author Christian Menard
 * @author Matt Weber
 * @author Peter Donovan
 */
public class CCompiler {

  FileConfig fileConfig;
  TargetConfig targetConfig;
  MessageReporter messageReporter;

  /**
   * Indicate whether the compiler is in C++ mode. In C++ mode, the compiler produces .cpp files
   * instead of .c files and uses a C++ compiler to compiler the code.
   */
  private final boolean cppMode;

  /** A factory for compiler commands. */
  GeneratorCommandFactory commandFactory;

  /**
   * Create an instance of CCompiler.
   *
   * @param targetConfig The current target configuration.
   * @param fileConfig The current file configuration.
   * @param messageReporter Used to report errors.
   * @param cppMode Whether the generated code should be compiled as if it were C++.
   */
  public CCompiler(
      TargetConfig targetConfig,
      FileConfig fileConfig,
      MessageReporter messageReporter,
      boolean cppMode) {
    this.fileConfig = fileConfig;
    this.targetConfig = targetConfig;
    this.messageReporter = messageReporter;
    this.commandFactory = new GeneratorCommandFactory(messageReporter, fileConfig);
    this.cppMode = cppMode;
  }

  /**
   * Run the C compiler by invoking cmake and make.
   *
   * @param generator An instance of GeneratorBase, only used to report error line numbers in the
   *     Eclipse IDE.
   * @return true if compilation succeeds, false otherwise.
   */
  public boolean runCCompiler(GeneratorBase generator, LFGeneratorContext context)
      throws IOException {
    // Set the build directory to be "build"
    Path buildPath = fileConfig.getSrcGenPath().resolve("build");
    // Remove the previous build directory if it exists to
    // avoid any error residue that can occur in CMake from
    // a previous build.
    // FIXME: This is slow and only needed if an error
    //  has previously occurred. Deleting the build directory
    //  if no prior errors have occurred can prolong the compilation
    //  substantially. See #1416 for discussion.
    FileUtil.deleteDirectory(buildPath);
    // Make sure the build directory exists
    Files.createDirectories(buildPath);

    LFCommand compile = compileCmakeCommand();
    if (compile == null) {
      return false;
    }

    // Use the user-specified compiler if any
    if (targetConfig.compiler != null) {
      if (cppMode) {
        // Set the CXX environment variable to change the C++ compiler.
        compile.replaceEnvironmentVariable("CXX", targetConfig.compiler);
      } else {
        // Set the CC environment variable to change the C compiler.
        compile.replaceEnvironmentVariable("CC", targetConfig.compiler);
      }
    }

    int cMakeReturnCode = compile.run(context.getCancelIndicator());

    if (cMakeReturnCode != 0
        && context.getMode() == LFGeneratorContext.Mode.STANDALONE
        && !outputContainsKnownCMakeErrors(compile.getErrors())) {
      messageReporter
          .nowhere()
          .error(targetConfig.compiler + " failed with error code " + cMakeReturnCode);
    }

    // For warnings (vs. errors), the return code is 0.
    // But we still want to mark the IDE.
    if (compile.getErrors().length() > 0
        && context.getMode() != LFGeneratorContext.Mode.STANDALONE
        && !outputContainsKnownCMakeErrors(compile.getErrors())) {
      generator.reportCommandErrors(compile.getErrors());
    }

    int makeReturnCode = 0;

    if (cMakeReturnCode == 0) {
      LFCommand build = buildCmakeCommand();

      makeReturnCode = build.run(context.getCancelIndicator());

      if (makeReturnCode != 0
          && context.getMode() == LFGeneratorContext.Mode.STANDALONE
          && !outputContainsKnownCMakeErrors(build.getErrors())) {
        messageReporter
            .nowhere()
            .error(targetConfig.compiler + " failed with error code " + makeReturnCode);
      }

      // For warnings (vs. errors), the return code is 0.
      // But we still want to mark the IDE.
      if (build.getErrors().length() > 0
          && context.getMode() != LFGeneratorContext.Mode.STANDALONE
          && !outputContainsKnownCMakeErrors(build.getErrors())) {
        generator.reportCommandErrors(build.getErrors());
      }

      if (makeReturnCode == 0 && build.getErrors().length() == 0) {
        messageReporter
            .nowhere()
            .info(
                "SUCCESS: Compiling generated code for "
                    + fileConfig.name
                    + " finished with no errors.");
      }

      if (targetConfig.platformOptions.platform == Platform.ZEPHYR
          && targetConfig.platformOptions.flash) {
        messageReporter.nowhere().info("Invoking flash command for Zephyr");
        LFCommand flash = buildWestFlashCommand();
        int flashRet = flash.run();
        if (flashRet != 0) {
          messageReporter.nowhere().error("West flash command failed with error code " + flashRet);
        } else {
          messageReporter.nowhere().info("SUCCESS: Flashed application with west");
        }
      }
    }
    return cMakeReturnCode == 0 && makeReturnCode == 0;
  }

  /**
   * Return a command to compile the specified C file using CMake. This produces a C-specific
   * compile command.
   */
  public LFCommand compileCmakeCommand() {
    // Set the build directory to be "build"
    Path buildPath = fileConfig.getSrcGenPath().resolve("build");

    LFCommand command =
        commandFactory.createCommand("cmake", cmakeOptions(targetConfig, fileConfig), buildPath);
    if (command == null) {
      messageReporter
          .nowhere()
          .error(
              "The C/CCpp target requires CMAKE >= "
                  + CCmakeGenerator.MIN_CMAKE_VERSION
                  + " to compile the generated code. Auto-compiling can be disabled using the"
                  + " \"no-compile: true\" target property.");
    }
    return command;
  }

  static Stream<String> cmakeCompileDefinitions(TargetConfig targetConfig) {
    return targetConfig.compileDefinitions.entrySet().stream()
        .map(entry -> "-D" + entry.getKey() + "=" + entry.getValue());
  }

  private static List<String> cmakeOptions(TargetConfig targetConfig, FileConfig fileConfig) {
    List<String> arguments = new ArrayList<>();
    cmakeCompileDefinitions(targetConfig).forEachOrdered(arguments::add);
    String separator = File.separator;
    String maybeQuote = ""; // Windows seems to require extra level of quoting.
    String srcPath = fileConfig.srcPath.toString(); // Windows requires escaping the backslashes.
    String rootPath = fileConfig.srcPkgPath.toString();
    if (separator.equals("\\")) {
      separator = "\\\\\\\\";
      maybeQuote = "\\\"";
      srcPath = srcPath.replaceAll("\\\\", "\\\\\\\\");
      rootPath = rootPath.replaceAll("\\\\", "\\\\\\\\");
    }
    arguments.addAll(
        List.of(
            "-DCMAKE_BUILD_TYPE="
                + ((targetConfig.cmakeBuildType != null)
                    ? targetConfig.cmakeBuildType.toString()
                    : "Release"),
            "-DCMAKE_INSTALL_PREFIX=" + FileUtil.toUnixString(fileConfig.getOutPath()),
            "-DCMAKE_INSTALL_BINDIR="
                + FileUtil.toUnixString(fileConfig.getOutPath().relativize(fileConfig.binPath)),
            "-DLF_FILE_SEPARATOR=\"" + maybeQuote + separator + maybeQuote + "\""));
    // Add #define for source file directory.
    // Do not do this for federated programs because for those, the definition is put
    // into the cmake file (and fileConfig.srcPath is the wrong directory anyway).
    if (!fileConfig.srcPath.toString().contains("fed-gen")) {
      // Do not convert to Unix path
      arguments.add("-DLF_SOURCE_DIRECTORY=\"" + maybeQuote + srcPath + maybeQuote + "\"");
      arguments.add("-DLF_PACKAGE_DIRECTORY=\"" + maybeQuote + rootPath + maybeQuote + "\"");
    }
    arguments.add(FileUtil.toUnixString(fileConfig.getSrcGenPath()));

    if (GeneratorUtils.isHostWindows()) {
      arguments.add("-DCMAKE_SYSTEM_VERSION=\"10.0\"");
    }
    return arguments;
  }

  /** Return the cmake config name correspnding to a given build type. */
  private String buildTypeToCmakeConfig(TargetProperty.BuildType type) {
    if (type == null) {
      return "Release";
    }
    switch (type) {
      case TEST:
        return "Debug";
      default:
        return type.toString();
    }
  }

  /**
   * Return a command to build the specified C file using CMake. This produces a C-specific build
   * command.
   *
   * <p>Note: It appears that configuration and build cannot happen in one command. Therefore, this
   * is separated into a compile command and a build command.
   */
  public LFCommand buildCmakeCommand() {
    // Set the build directory to be "build"
    Path buildPath = fileConfig.getSrcGenPath().resolve("build");
    String cores = String.valueOf(Runtime.getRuntime().availableProcessors());
    LFCommand command =
        commandFactory.createCommand(
            "cmake",
            List.of(
                "--build",
                ".",
                "--target",
                "install",
                "--parallel",
                cores,
                "--config",
                buildTypeToCmakeConfig(targetConfig.cmakeBuildType)),
            buildPath);
    if (command == null) {
      messageReporter
          .nowhere()
          .error(
              "The C/CCpp target requires CMAKE >= 3.5 to compile the generated code."
                  + " Auto-compiling can be disabled using the \"no-compile: true\" target"
                  + " property.");
    }
    return command;
  }

  /**
   * Return a flash/emulate command using west. If board is null (defaults to qemu_cortex_m3) or
   * qemu_* Return a flash command which runs the target as an emulation If ordinary target, return
   * {@code west flash}
   */
  public LFCommand buildWestFlashCommand() {
    // Set the build directory to be "build"
    Path buildPath = fileConfig.getSrcGenPath().resolve("build");
    String board = targetConfig.platformOptions.board;
    LFCommand cmd;
    if (board == null || board.startsWith("qemu") || board.equals("native_posix")) {
      cmd = commandFactory.createCommand("west", List.of("build", "-t", "run"), buildPath);
    } else {
      cmd = commandFactory.createCommand("west", List.of("flash"), buildPath);
    }
    if (cmd == null) {
      messageReporter.nowhere().error("Could not create west flash command.");
    }

    return cmd;
  }

  /**
   * Check if the output produced by CMake has any known and common errors. If a known error is
   * detected, a specialized, more informative message is shown.
   *
   * <p>Errors currently detected:
   *
   * <ul>
   *   <li>C++ compiler used to compile C files: This error shows up as &#39;#error &quot;The
   *       CMAKE_C_COMPILER is set to a C++ compiler&quot;&#39; in the &#39;CMakeOutput&#39; string.
   * </ul>
   *
   * @param CMakeOutput The captured output from CMake.
   * @return true if the provided 'CMakeOutput' contains a known error. false otherwise.
   */
  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  private boolean outputContainsKnownCMakeErrors(String CMakeOutput) {
    // Check if the error thrown is due to the wrong compiler
    if (CMakeOutput.contains("The CMAKE_C_COMPILER is set to a C++ compiler")) {
      // If so, print an appropriate error message
      if (targetConfig.compiler != null) {
        messageReporter
            .nowhere()
            .error(
                "A C++ compiler was requested in the compiler target property."
                    + " Use the CCpp or the Cpp target instead.");
      } else {
        messageReporter
            .nowhere()
            .error(
                "\"A C++ compiler was detected."
                    + " The C target works best with a C compiler."
                    + " Use the CCpp or the Cpp target instead.\"");
      }
      return true;
    }
    return false;
  }

  /**
   * Return a command to compile the specified C file using a native compiler (generally gcc unless
   * overridden by the user). This produces a C specific compile command.
   *
   * @param fileToCompile The C filename without the .c extension.
   * @param noBinary If true, the compiler will create a .o output instead of a binary. If false,
   *     the compile command will produce a binary.
   */
  public LFCommand compileCCommand(String fileToCompile, boolean noBinary) {
    String cFilename = getTargetFileName(fileToCompile, cppMode, targetConfig);

    Path relativeSrcPath =
        fileConfig
            .getOutPath()
            .relativize(fileConfig.getSrcGenPath().resolve(Paths.get(cFilename)));
    Path relativeBinPath =
        fileConfig.getOutPath().relativize(fileConfig.binPath.resolve(Paths.get(fileToCompile)));

    // NOTE: we assume that any C compiler takes Unix paths as arguments.
    String relSrcPathString = FileUtil.toUnixString(relativeSrcPath);
    String relBinPathString = FileUtil.toUnixString(relativeBinPath);

    // If there is no main reactor, then generate a .o file not an executable.
    if (noBinary) {
      relBinPathString += ".o";
    }

    ArrayList<String> compileArgs = new ArrayList<>();
    compileArgs.add(relSrcPathString);
    for (String file : targetConfig.compileAdditionalSources) {
      var relativePath =
          fileConfig.getOutPath().relativize(fileConfig.getSrcGenPath().resolve(Paths.get(file)));
      compileArgs.add(FileUtil.toUnixString(relativePath));
    }
    compileArgs.addAll(targetConfig.compileLibraries);

    // Add compile definitions
    targetConfig.compileDefinitions.forEach(
        (key, value) -> compileArgs.add("-D" + key + "=" + value));

    // Finally, add the compiler flags in target parameters (if any)
    compileArgs.addAll(targetConfig.compilerFlags);

    // Only set the output file name if it hasn't already been set
    // using a target property or Args line flag.
    if (!compileArgs.contains("-o")) {
      compileArgs.add("-o");
      compileArgs.add(relBinPathString);
    }

    // If there is no main reactor, then use the -c flag to prevent linking from occurring.
    // FIXME: we could add a {@code -c} flag to {@code lfc} to make this explicit in stand-alone
    // mode.
    //  Then again, I think this only makes sense when we can do linking.
    if (noBinary) {
      compileArgs.add("-c"); // FIXME: revisit
    }

    LFCommand command =
        commandFactory.createCommand(targetConfig.compiler, compileArgs, fileConfig.getOutPath());
    if (command == null) {
      messageReporter
          .nowhere()
          .error(
              "The C/CCpp target requires GCC >= 7 to compile the generated code. Auto-compiling"
                  + " can be disabled using the \"no-compile: true\" target property.");
    }
    return command;
  }

  /**
   * Produces the filename including the target-specific extension
   *
   * @param fileName The base name of the file without any extensions
   * @param cppMode Indicate whether the compiler is in C++ mode In C++ mode, the compiler produces
   *     .cpp files instead of .c files and uses a C++ compiler to compiler the code.
   */
  static String getTargetFileName(String fileName, boolean cppMode, TargetConfig targetConfig) {
    if (targetConfig.platformOptions.platform == Platform.ARDUINO) {
      return fileName + ".ino";
    }
    if (cppMode) {
      // If the C++ mode is enabled, use a .cpp extension
      return fileName + ".cpp";
    }
    return fileName + ".c";
  }
}