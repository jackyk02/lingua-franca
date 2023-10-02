package org.lflang.target.property;

import java.util.Arrays;
import java.util.List;
import org.lflang.Target;

public class AuthProperty extends AbstractBooleanProperty {

  @Override
  public List<Target> supportedTargets() {
    return Arrays.asList(Target.C, Target.CCPP);
  }

  @Override
  public String name() {
    return "auth";
  }
}
