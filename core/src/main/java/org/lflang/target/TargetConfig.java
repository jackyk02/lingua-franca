/*************
 * Copyright (c) 2019, The University of California at Berkeley.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
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
package org.lflang.target;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import org.lflang.AbstractTargetProperty;
import org.lflang.MessageReporter;
import org.lflang.Target;
import org.lflang.lf.KeyValuePair;
import org.lflang.lf.TargetDecl;
import org.lflang.target.property.AuthProperty;
import org.lflang.target.property.BuildCommandsProperty;
import org.lflang.target.property.BuildTypeProperty;
import org.lflang.target.property.ClockSyncModeProperty;
import org.lflang.target.property.ClockSyncOptionsProperty;
import org.lflang.target.property.CmakeIncludeProperty;
import org.lflang.target.property.CompileDefinitionsProperty;
import org.lflang.target.property.CompilerFlagsProperty;
import org.lflang.target.property.CompilerProperty;
import org.lflang.target.property.CoordinationOptionsProperty;
import org.lflang.target.property.CoordinationProperty;
import org.lflang.target.property.DockerProperty;
import org.lflang.target.property.ExportDependencyGraphProperty;
import org.lflang.target.property.ExportToYamlProperty;
import org.lflang.target.property.ExternalRuntimePathProperty;
import org.lflang.target.property.FastProperty;
import org.lflang.target.property.FedSetupProperty;
import org.lflang.target.property.FilesProperty;
import org.lflang.target.property.KeepaliveProperty;
import org.lflang.target.property.LoggingProperty;
import org.lflang.target.property.NoCompileProperty;
import org.lflang.target.property.NoRuntimeValidationProperty;
import org.lflang.target.property.PlatformProperty;
import org.lflang.target.property.PrintStatisticsProperty;
import org.lflang.target.property.ProtobufsProperty;
import org.lflang.target.property.Ros2DependenciesProperty;
import org.lflang.target.property.Ros2Property;
import org.lflang.target.property.RuntimeVersionProperty;
import org.lflang.target.property.SchedulerProperty;
import org.lflang.target.property.SingleFileProjectProperty;
import org.lflang.target.property.ThreadingProperty;
import org.lflang.target.property.TimeOutProperty;
import org.lflang.target.property.TracingProperty;
import org.lflang.target.property.WorkersProperty;
import org.lflang.target.property.type.TargetPropertyType;
import org.lflang.target.property.type.VerifyProperty;

/**
 * A class for keeping the current target configuration.
 *
 * <p>Class members of type String are initialized as empty strings, unless otherwise stated.
 *
 * @author Marten Lohstroh
 */
public class TargetConfig {

  /** The target of this configuration (e.g., C, TypeScript, Python). */
  public final Target target;

  /**
   * Create a new target configuration based on the given target declaration AST node only.
   *
   * @param target AST node of a target declaration.
   */
  public TargetConfig(Target target) {
    this.target = target;

    this.register(
        new AuthProperty(),
        new BuildCommandsProperty(),
        new BuildTypeProperty(),
        new ClockSyncModeProperty(),
        new ClockSyncOptionsProperty(),
        new CmakeIncludeProperty(),
        new CompileDefinitionsProperty(),
        new CompilerFlagsProperty(),
        new CompilerProperty(),
        new CoordinationOptionsProperty(),
        new CoordinationProperty(),
        new DockerProperty(),
        new ExportDependencyGraphProperty(),
        new ExportToYamlProperty(),
        new ExternalRuntimePathProperty(),
        new FastProperty(),
        new FilesProperty(),
        new KeepaliveProperty(),
        new LoggingProperty(),
        new NoCompileProperty(),
        new NoRuntimeValidationProperty(),
        new PlatformProperty(),
        new PrintStatisticsProperty(),
        new ProtobufsProperty(),
        new Ros2DependenciesProperty(),
        new Ros2Property(),
        new RuntimeVersionProperty(),
        new SchedulerProperty(),
        new SingleFileProjectProperty(),
        new ThreadingProperty(),
        new TimeOutProperty(),
        new TracingProperty(),
        new VerifyProperty(),
        new WorkersProperty());

    this.register(new FedSetupProperty());
  }

  /**
   * Create a new target configuration based on the given commandline arguments and target
   * declaration AST node.
   *
   * @param cliArgs Arguments passed on the commandline.
   * @param target AST node of a target declaration.
   * @param messageReporter An error reporter to report problems.
   */
  public TargetConfig(Properties cliArgs, TargetDecl target, MessageReporter messageReporter) {
    this(Target.fromDecl(target));
    if (target.getConfig() != null) {
      List<KeyValuePair> pairs = target.getConfig().getPairs();
      TargetProperty.load(this, pairs, messageReporter);
    }

    if (cliArgs != null) {
      TargetProperty.load(this, cliArgs, messageReporter);
    }
  }

  /** Additional sources to add to the compile command if appropriate. */
  public final List<String> compileAdditionalSources = new ArrayList<>();

  /** Flags to pass to the linker, unless a build command has been specified. */
  public String linkerFlags = "";

  private final Map<AbstractTargetProperty<?, ?>, Object> properties = new HashMap<>();

  private final Set<AbstractTargetProperty<?, ?>> setProperties = new HashSet<>();

  public void register(AbstractTargetProperty<?, ?>... properties) {
    Arrays.stream(properties)
        .forEach(property -> this.properties.put(property, property.initialValue()));
  }

  public <T, S extends TargetPropertyType> void override(
      AbstractTargetProperty<T, S> property, T value) {
    this.setProperties.add(property);
    this.properties.put(property, value);
  }

  public void reset(AbstractTargetProperty property) {
    this.properties.remove(property);
    this.setProperties.remove(property);
  }

  @SuppressWarnings("unchecked")
  public <T, S extends TargetPropertyType> T get(AbstractTargetProperty<T, S> property) {
    return (T) properties.get(property);
  }

  public boolean isSet(AbstractTargetProperty<?, ?> property) {
    return this.setProperties.contains(property);
  }

  public String listOfRegisteredProperties() {
    return getRegisteredProperties().stream()
        .map(p -> p.toString())
        .filter(s -> !s.startsWith("_"))
        .collect(Collectors.joining(", "));
  }

  public List<AbstractTargetProperty> getRegisteredProperties() {
    return this.properties.keySet().stream()
        .sorted((p1, p2) -> p1.getClass().getName().compareTo(p2.getClass().getName()))
        .collect(Collectors.toList());
  }

  /**
   * Return the target property in this target config that matches the given string.
   *
   * @param name The string to match against.
   */
  public Optional<AbstractTargetProperty> forName(String name) {
    return this.getRegisteredProperties().stream()
        .filter(c -> c.name().equalsIgnoreCase(name))
        .findFirst();
  }
}
