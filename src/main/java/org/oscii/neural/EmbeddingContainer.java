package org.oscii.neural;

import com.eatthepath.jvptree.VPTree;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.commons.collections4.map.LRUMap;
import org.oscii.math.VectorMath;
import sun.misc.LRUCache;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.LineNumberReader;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.stream.Collectors.toList;

/**
 * A simple container for a word embedding model.
 *
 * @author Spence Green
 */
public class EmbeddingContainer {

    private final static long ONE_GB = 1024 * 1024 * 1024;

    private final String[] vocab;
    private final float[][] embeddings;
    private final Map<String, Integer> word2Index;

    private final Map<float[], Integer> embedding2Index;
    private VPTree<float[]> neighborIndex;

    /**
     * Constructor.
     *
     * @param v
     * @param e
     */
    public EmbeddingContainer(String[] v, float[][] e) {
        this.vocab = v;
        this.embeddings = e;
        this.word2Index = new HashMap<>(vocab.length);
        this.embedding2Index = new HashMap<>(vocab.length);
        for (int i = 0; i < vocab.length; i++) {
            word2Index.put(vocab[i], i);
            embedding2Index.put(embeddings[i], i);
        }
    }

    /**
     * Get the dimension of the embeddings.
     *
     * @return
     */
    public int dimension() {
        return embeddings[0].length;
    }

    /**
     * Get the vocabulary size.
     *
     * @return
     */
    public int vocabSize() {
        return vocab.length;
    }

    /**
     * Get the word embedding. Returns null if no embedding exists.
     *
     * @param query
     * @return
     */
    public float[] getRawVector(String query) {
        Integer i = word2Index.get(query);
        return i == null ? null : embeddings[i];
    }

    /**
     * Return true if the container has an embedding for this model. False otherwise.
     *
     * @param query
     * @return
     */
    public boolean contains(String query) {
        return word2Index.containsKey(query);
    }

    /**
     * Get the average vector for a tokenized sequence.
     *
     * @param tokens
     * @return
     */
    public float[] getMean(String[] tokens) {
        float[] avgVec = new float[dimension()];
        int n = 0;
        for (String token : tokens) {
            float[] v = getRawVector(token);
            if (v == null) continue;
            VectorMath.addInPlace(avgVec, v);
            ++n;
        }
        VectorMath.multiplyInPlace(avgVec, 1.0f / n);
        return avgVec;
    }

    public List<String> neighbors(String word, int k) {
        return neighbors(embeddings[word2Index.get(word)], k);
    }


    class Vectors {
        float[] a;
        float[] b;

        public Vectors(float[] a, float[] b) {
            this.a = a;
            this.b = b;
        }

        // Note: comparison on vector identity only, not contents!

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Vectors vectors = (Vectors) o;

            if (a != vectors.a) return false;
            return b == vectors.b;
        }

        @Override
        public int hashCode() {
            int result = a.hashCode();
            result = 31 * result + b.hashCode();
            return result;
        }
    }

    Cache<Vectors, Double> distanceCache = Caffeine.newBuilder().maximumSize(1_000_000).softValues().build();
    double angularDistance(float[] a, float[] b) {
        return distanceCache.get(
                new Vectors(a, b),
                vs -> Math.acos(VectorMath.cosineSimilarity(vs.a, vs.b)) / Math.PI);
    }

    public List<String> neighbors(float[] embedding, int k) {
        if (neighborIndex == null) {
            neighborIndex = new VPTree<>(this::angularDistance, Arrays.asList(embeddings));
        }
        return neighborIndex.getNearestNeighbors(embedding, k)
                .stream().map(e -> vocab[embedding2Index.get(e)]).collect(toList());
    }

    /* File Input */

    /**
     * Read file with default byte order.
     *
     * @param file
     * @return
     * @throws IOException
     */
    public static EmbeddingContainer fromBinFile(File file) throws IOException {
        return fromBinFile(file, ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Read file with default byte order and restricted vocabulary.
     *
     * @param file
     * @return
     * @throws IOException
     */
    public static EmbeddingContainer fromBinFile(File file, Set<String> vocab) throws IOException {
        return fromBinFile(file, ByteOrder.LITTLE_ENDIAN, vocab);
    }

    /**
     * Read the binary output format of the word2vec C reference implementation.
     *
     * @param file
     * @param byteOrder
     * @return
     * @throws IOException
     */
    public static EmbeddingContainer fromBinFile(File file, ByteOrder byteOrder)
            throws IOException {
        return fromBinFile(file, byteOrder, null);
    }

    /**
     * Read the binary output format but restrict to a fixed vocabulary.
     *
     * @param file
     * @param byteOrder
     * @return
     * @throws IOException
     */
    public static EmbeddingContainer fromBinFile(File file, ByteOrder byteOrder, Set<String> vocab)
            throws IOException {
        try (final FileInputStream fis = new FileInputStream(file)) {
            final FileChannel channel = fis.getChannel();
            // TODO(spenceg) Simply open a binary file stream?
            MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0,
                    Math.min(channel.size(), Integer.MAX_VALUE));
            buffer.order(byteOrder);
            int bufferCount = 1;
            // Java's NIO only allows memory-mapping up to 2GB. To work around this problem, we re-map
            // every gigabyte. To calculate offsets correctly, we have to keep track how many gigabytes
            // we've already skipped. That's what this is for.

            StringBuilder sb = new StringBuilder();
            char c = (char) buffer.get();
            while (c != '\n') {
                sb.append(c);
                c = (char) buffer.get();
            }
            String firstLine = sb.toString();
            int index = firstLine.indexOf(' ');
            final int vocabSize = Integer.parseInt(firstLine.substring(0, index));
            final int layerSize = Integer.parseInt(firstLine.substring(index + 1));

            String[] words = new String[vocabSize];
            float[][] vectors = new float[vocabSize][];
            int wordindex = 0;
            for (int lineno = 0; lineno < vocabSize; lineno++) {
                // read vocab
                sb.setLength(0);
                c = (char) buffer.get();
                while (c != ' ') {
                    // ignore newlines in front of words (some binary files have newline,
                    // some don't)
                    if (c != '\n') {
                        sb.append(c);
                    }
                    c = (char) buffer.get();
                }
                String word = sb.toString();

                // read vector
                final FloatBuffer floatBuffer = buffer.asFloatBuffer();
                float[] vector = new float[layerSize];
                floatBuffer.get(vector);
                buffer.position(buffer.position() + 4 * layerSize);

                if (vocab == null || vocab.contains(word)) {
                    words[wordindex] = word;
                    vectors[wordindex] = vector;
                    wordindex++;
                }

                // remap file
                if (buffer.position() > ONE_GB) {
                    final int newPosition = (int) (buffer.position() - ONE_GB);
                    final long size = Math.min(channel.size() - ONE_GB * bufferCount, Integer.MAX_VALUE);
                    buffer = channel.map(
                            FileChannel.MapMode.READ_ONLY,
                            ONE_GB * bufferCount,
                            size);
                    buffer.order(byteOrder);
                    buffer.position(newPosition);
                    bufferCount += 1;
                }
            }

            if (wordindex < vocabSize) {
                words = Arrays.copyOfRange(words, 0, wordindex);
                vectors = Arrays.copyOfRange(vectors, 0, wordindex);
            }
            return new EmbeddingContainer(words, vectors);
        }
    }

    /**
     * Read embeddings in text format.
     *
     * @param filename
     * @return
     * @throws IOException
     */
    public static EmbeddingContainer fromTextFile(String filename) throws IOException {
        try (LineNumberReader reader = new LineNumberReader(Files.newBufferedReader(Paths.get(filename)))) {
            String firstLine = reader.readLine();
            if (firstLine == null) return null;
            String[] fields = firstLine.trim().split(" ");
            if (fields.length != 2) throw new RuntimeException("Invalid first line: " + firstLine);
            int vocabSize = Integer.parseInt(fields[0]);
            int layerSize = Integer.parseInt(fields[1]);
            String[] vocab = new String[vocabSize];
            float[][] vectors = new float[vocabSize][layerSize];
            for (String line; (line = reader.readLine()) != null; ) {
                int i = reader.getLineNumber() - 2; // one-indexed
                String[] values = line.trim().split(" ");
                vocab[i] = values[0].trim();

                for (int d = 1; d < values.length; d++) {
                    vectors[i][d - 1] = Float.parseFloat(values[d]);
                }
            }
            return new EmbeddingContainer(vocab, vectors);
        }
    }

    /**
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.printf("Usage: java %s bin_file%n", EmbeddingContainer.class.getName());
            System.exit(-1);
        }

        final String fileName = args[0];
        System.out.println("Loading embeddings from: " + fileName + " ...");
        EmbeddingContainer container = EmbeddingContainer.fromBinFile(new File(fileName));
        System.out.printf("Embedding dimension: %d%n", container.dimension());
        for (String wordType : container.vocab) {
            float[] emb = container.getRawVector(wordType);
            System.out.printf("%s\t%s%n", wordType, Arrays.toString(emb));
        }
    }
}
