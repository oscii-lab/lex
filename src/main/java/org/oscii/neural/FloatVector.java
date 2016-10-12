package org.oscii.neural;

import no.uib.cipr.matrix.AbstractVector;
import no.uib.cipr.matrix.Vector;
import no.uib.cipr.matrix.io.MatrixVectorReader;
import no.uib.cipr.matrix.io.VectorInfo;
import no.uib.cipr.matrix.io.VectorSize;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;

/**
 * Wrapper around float[]
 */
public class FloatVector extends AbstractVector implements Serializable {

    /** just the private data */
    private static final long serialVersionUID = 5358813524094629362L;

    /**
     * Vector data
     */
    private final float[] data;

    /**
     * Constructor for FloatVector
     *
     * @param r
     *            Reader to get vector from
     */
    public FloatVector(MatrixVectorReader r) throws IOException {
        // Start with a zero-sized vector
        super(0);

        // Get vector information. Use the header if present, else use a safe
        // default
        VectorInfo info = null;
        if (r.hasInfo())
            info = r.readVectorInfo();
        else
            info = new VectorInfo(true, VectorInfo.VectorField.Real);
        VectorSize size = r.readVectorSize(info);

        // Resize the vector to correct size
        this.size = size.size();
        data = new float[size.size()];

        // Check that the vector is in an acceptable format
        if (info.isPattern())
            throw new UnsupportedOperationException(
                    "Pattern vectors are not supported");
        if (info.isComplex())
            throw new UnsupportedOperationException(
                    "Complex vectors are not supported");

        // Read the entries
        if (info.isCoordinate()) {

            // Read coordinate data
            int nz = size.numEntries();
            int[] index = new int[nz];
            float[] entry = new float[nz];
            r.readCoordinate(index, entry);

            // Shift indices from 1-offset to 0-offset
            r.add(-1, index);

            // Store them
            for (int i = 0; i < nz; ++i)
                set(index[i], entry[i]);

        } else
            // info.isArray()
            r.readArray(data);
    }

    /**
     * Constructor for FloatVector
     *
     * @param size
     *            Size of the vector
     */
    public FloatVector(int size) {
        super(size);
        data = new float[size];
    }

    /**
     * Constructor for FloatVector
     *
     * @param x
     *            Copies contents from this vector. A deep copy is made
     */
    public FloatVector(Vector x) {
        this(x, true);
    }

    /**
     * Constructor for FloatVector
     *
     * @param x
     *            Copies contents from this vector
     * @param deep
     *            True for a deep copy. For a shallow copy, <code>x</code>
     *            must be a <code>FloatVector</code>
     */
    public FloatVector(Vector x, boolean deep) {
        super(x);

        if (deep) {
            data = new float[size];
            set(x);
        } else
            data = ((FloatVector) x).getData();
    }

    /**
     * Constructor for FloatVector
     *
     * @param x
     *            Copies contents from this array
     * @param deep
     *            True for a deep copy. For a shallow copy, <code>x</code> is
     *            aliased with the internal storage
     */
    public FloatVector(float[] x, boolean deep) {
        super(x.length);

        if (deep)
            data = x.clone();
        else
            data = x;
    }

    /**
     * Constructor for FloatVector
     *
     * @param x
     *            Copies contents from this array in a deep copy
     */
    public FloatVector(float[] x) {
        this(x, true);
    }

    @Override
    public void set(int index, double value) {
        check(index);
        data[index] = (float) value;
    }

    @Override
    public void add(int index, double value) {
        check(index);
        data[index] += value;
    }

    @Override
    public double get(int index) {
        check(index);
        return data[index];
    }

    @Override
    public FloatVector copy() {
        return new FloatVector(this);
    }

    @Override
    public FloatVector zero() {
        Arrays.fill(data, 0);
        return this;
    }

    @Override
    public FloatVector scale(double alpha) {
        for (int i = 0; i < size; ++i)
            data[i] *= alpha;
        return this;
    }

    @Override
    public Vector set(Vector y) {
        if (!(y instanceof FloatVector))
            return super.set(y);

        checkSize(y);

        float[] yd = ((FloatVector) y).getData();
        System.arraycopy(yd, 0, data, 0, size);

        return this;
    }

    @Override
    public Vector set(double alpha, Vector y) {
        if (!(y instanceof FloatVector))
            return super.set(alpha, y);

        checkSize(y);

        if (alpha == 0)
            return zero();

        float[] yd = ((FloatVector) y).getData();

        for (int i = 0; i < size; ++i)
            data[i] = (float) alpha * yd[i];

        return this;
    }

    @Override
    public Vector add(Vector y) {
        if (!(y instanceof FloatVector))
            return super.add(y);

        checkSize(y);

        float[] yd = ((FloatVector) y).getData();

        for (int i = 0; i < size; i++)
            data[i] += yd[i];

        return this;
    }

    @Override
    public Vector add(double alpha, Vector y) {
        if (!(y instanceof FloatVector))
            return super.add(alpha, y);

        checkSize(y);

        if (alpha == 0)
            return this;

        float[] yd = ((FloatVector) y).getData();

        for (int i = 0; i < size; i++)
            data[i] += alpha * yd[i];

        return this;
    }

    @Override
    public double dot(Vector y) {
        if (!(y instanceof FloatVector))
            return super.dot(y);

        checkSize(y);

        float[] yd = ((FloatVector) y).getData();

        float dot = 0;
        for (int i = 0; i < size; ++i)
            dot += data[i] * yd[i];
        return dot;
    }

    @Override
    protected double norm1() {
        float sum = 0;
        for (int i = 0; i < size; ++i)
            sum += Math.abs(data[i]);
        return sum;
    }

    @Override
    protected double norm2() {
        float norm = 0;
        for (int i = 0; i < size; ++i)
            norm += data[i] * data[i];
        return Math.sqrt(norm);
    }

    @Override
    protected double norm2_robust() {
        float scale = 0, ssq = 1;
        for (int i = 0; i < size; ++i)
            if (data[i] != 0) {
                float absxi = Math.abs(data[i]);
                if (scale < absxi) {
                    ssq = 1 + ssq * (scale / absxi) * (scale / absxi);
                    scale = absxi;
                } else
                    ssq += (absxi / scale) * (absxi / scale);
            }
        return scale * Math.sqrt(ssq);
    }

    @Override
    protected double normInf() {
        float max = 0;
        for (int i = 0; i < size; ++i)
            max = Math.max(Math.abs(data[i]), max);
        return max;
    }

    /**
     * Returns the internal vector contents. The array indices correspond to the
     * vector indices
     */
    public float[] getData() {
        return data;
    }

}
