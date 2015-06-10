package org.oscii.detokenize;

import cc.mallet.pipe.Pipe;
import cc.mallet.types.Instance;

/**
 * Feature extractor for Tokens.
 */
public class FeaturePipe extends Pipe {
  @Override
  public Instance pipe(Instance inst) {
    // TODO(denero) Add features
    return inst;
  }
}
