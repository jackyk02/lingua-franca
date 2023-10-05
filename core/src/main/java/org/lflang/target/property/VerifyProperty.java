package org.lflang.target.property.type;

import java.util.List;
import org.lflang.Target;
import org.lflang.target.property.AbstractBooleanProperty;

/** If true, check the generated verification model. The default is false. */
public class VerifyProperty extends AbstractBooleanProperty {

  @Override
  public List<Target> supportedTargets() {
    return List.of(Target.C);
  }

  @Override
  public String name() {
    return "verify";
  }
}
