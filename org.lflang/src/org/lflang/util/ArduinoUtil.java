package org.lflang.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import org.lflang.ErrorReporter;
import org.lflang.FileConfig;
import org.lflang.TargetConfig;
import org.lflang.generator.GeneratorCommandFactory;
import org.lflang.generator.LFGeneratorContext;

import org.eclipse.xtext.xbase.lib.Exceptions;

public class ArduinoUtil {

    private LFGeneratorContext context;
    private GeneratorCommandFactory commandFactory;
    private ErrorReporter errorReporter;

    public ArduinoUtil (LFGeneratorContext context, GeneratorCommandFactory commandFactory, ErrorReporter errorReporter) {
        this.context = context;
        this.commandFactory = commandFactory;
        this.errorReporter = errorReporter;
    }

    /**
     * Returns true if arduino-cli exists, false otherwise.
     */
    private boolean checkArduinoCLIExists() {
        LFCommand checkCommand = LFCommand.get("arduino-cli", List.of("version"));
        return checkCommand != null && checkCommand.run() == 0;
    }

    private LFCommand arduinoCompileCommand(FileConfig fileConfig, TargetConfig targetConfig) throws IOException {
        if (!checkArduinoCLIExists()) {
            throw new IOException("Must have arduino-cli installed to auto-compile.");
        } else {
            var srcGenPath = fileConfig.getSrcGenPath();
            try {
                // Write to a temporary file to execute since ProcessBuilder does not like spaces and double quotes in its arguments.
                File testScript = File.createTempFile("arduino", null);
                testScript.deleteOnExit();
                if (!testScript.setExecutable(true)) {
                    throw new IOException("Failed to make compile script executable");
                }
                var fileWriter = new FileWriter(testScript.getAbsoluteFile(), true);
                BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
                String board = targetConfig.platformOptions.board != null ? targetConfig.platformOptions.board : "arduino:avr:leonardo";
                bufferedWriter.write("arduino-cli compile -b " + board + " --build-property " +
                    "compiler.c.extra_flags=\"-DLF_UNTHREADED -DPLATFORM_ARDUINO -DINITIAL_EVENT_QUEUE_SIZE=10 -DINITIAL_REACT_QUEUE_SIZE=10\" " +
                    "--build-property compiler.cpp.extra_flags=\"-DLF_UNTHREADED -DPLATFORM_ARDUINO -DINITIAL_EVENT_QUEUE_SIZE=10 -DINITIAL_REACT_QUEUE_SIZE=10\" "
                    + srcGenPath.toString());
                bufferedWriter.close();
                return commandFactory.createCommand(
                    testScript.getAbsolutePath(), List.of(), null);
            } catch (IOException e) {
               e.printStackTrace();
               throw new IOException(e);
            }
        }
    }

    public void buildArduino(FileConfig fileConfig, TargetConfig targetConfig) {
        System.out.println("Retrieving Arduino Compile Script");
        try {
            LFCommand command = arduinoCompileCommand(fileConfig, targetConfig); // Use ProcessBuilder for finer control.
            int retCode = 0;
            retCode = command.run(context.getCancelIndicator());
            if (retCode != 0 && context.getMode() == LFGeneratorContext.Mode.STANDALONE) {
                errorReporter.reportError("arduino-cli failed with error code " + retCode);
                throw new IOException("arduino-cli failure");
            }
        } catch (IOException e){
            Exceptions.sneakyThrow(e);
        }
        System.out.println("SUCCESS: Compiling generated code for " + fileConfig.name + " finished with no errors.");
        if (targetConfig.platformOptions.flash) {
            if (targetConfig.platformOptions.port != null) {
                System.out.println("Invoking flash command for Arduino");
                LFCommand flash = commandFactory.createCommand(
                    "arduino-cli", List.of("upload", "-b", targetConfig.platformOptions.board, "-p", targetConfig.platformOptions.port), fileConfig.getSrcGenPath());
                if (flash == null) {
                    errorReporter.reportError(
                    "Could not create arduino-cli flash command."
                    );
                }
                int flashRet = flash.run();
                if (flashRet != 0) {
                    errorReporter.reportError("arduino-cli flash command failed with error code " + flashRet);
                } else {
                    System.out.println("SUCCESS: Flashed board using arduino-cli");
                }
            } else {
                errorReporter.reportError("Need to provide a port on which to automatically flash.");
            }
        }
    }
}
