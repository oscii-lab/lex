package org.oscii.math;

/**
 * Operations on raw vectors (arrays).
 * 
 * @author Spence Green
 *
 */
public final class VectorMath {

  private VectorMath() {}

  /**
   * Cosine similarity. Undefined for zero vectors.
   * 
   * @param v1
   * @param v2
   * @return
   */
  public static double cosineSimilarity(float[] v1, float[] v2) {
    if (v1.length != v2.length) throw new IllegalArgumentException();
    double v1Dotv2 = 0.0;
    double v1SS = 0.0;
    double v2SS = 0.0;
    for (int i = 0; i < v1.length; i++) {
      v1Dotv2 += v1[i] * v2[i];
      v1SS += v1[i] * v1[i];
      v2SS += v2[i] * v2[i];
    }
    if (v1SS == 0.0f || v2SS == 0.0f) throw new IllegalArgumentException("Cosine similarity undefined for zero vectors");
    return v1Dotv2 / (Math.sqrt(v1SS) * Math.sqrt(v2SS));
  }

  /**
   * 
   * @param dest
   * @param v1
   */
  public static void addInPlace(float[] dest, float[] v1) {
    if (dest.length != v1.length) throw new IllegalArgumentException();
    for (int i = 0; i < dest.length; ++i) dest[i] += v1[i];
  }

  /**
   * 
   * @param v1
   * @param scalar
   */
  public static void multiplyInPlace(float[] v1, float scalar) {
    if (scalar == 0.0f) throw new IllegalArgumentException();
    for (int i = 0; i < v1.length; ++i) v1[i] *= scalar;
  }
}
