package org.oscii.math;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * VectorMath unit tests.
 * 
 * @author Spence Green
 *
 */
public class VectorMathTest {

  @Test
  public void testCosineSimilarity() {
    float[] v1 = {1.0f, 2.0f, 3.0f};
    float[] v2 = {-1.0f, 3.0f, 10.0f};
    double sim = VectorMath.cosineSimilarity(v1, v2);
    assertEquals(0.8918826, sim, 1e-6);
  }
}
