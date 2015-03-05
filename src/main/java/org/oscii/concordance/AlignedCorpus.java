package org.oscii.concordance;

import com.codepoetics.protonpack.StreamUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.oscii.lex.Expression;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Index and compute statistics over an aligned corpus.
 */
public class AlignedCorpus {
    private String path;
    private String sourceSuffix;
    private String targetSuffix;

    List<AlignedSentence> sentences;
    Map<String, List<Location>> sourceIndex;
    Map<String, List<Location>> targetIndex;
    Map<String, Map<String, Long>> targetsBySource;
    Map<String, Long> sumBySource;
    Map<String, Map<String, Long>> sourcesByTarget;
    Map<String, Long> sumByTarget;

    private final static Logger log = LogManager.getLogger(AlignedCorpus.class);

    public AlignedCorpus(String path, String sourceSuffix, String targetSuffix) {
        this.path = path;
        this.sourceSuffix = sourceSuffix;
        this.targetSuffix = targetSuffix;
    }

    /*
     * Read and index a parallel corpus.
     */
    public void read() throws IOException {
        log.info("Reading sentence pairs");
        Stream<String> sources = Files.lines(Paths.get(path + "." + sourceSuffix));
        Stream<String> targets = Files.lines(Paths.get(path + "." + targetSuffix));
        Stream<String> aligns = Files.lines(Paths.get(path + ".align"));
        sentences = StreamUtils.zip(sources, targets, aligns,
                (s, t, a) -> AlignedSentence.create(s, t, a))
                .collect(Collectors.toList());
        tally();
    }

    /*
     * Create indices and counts.
     */
    public void tally() {
        log.info("Indexing sentence pairs");
        sourceIndex = indexTokens(AlignedCorpus::sourceTokens);
        targetIndex = indexTokens(AlignedCorpus::targetTokens);
        log.info("Counting alignments");
        targetsBySource = countLinks(sourceIndex, AlignedCorpus::alignedTarget);
        sourcesByTarget = countLinks(targetIndex, AlignedCorpus::alignedSource);
        log.info("Normalizing");
        sumBySource = sumCounts(targetsBySource);
        sumByTarget = sumCounts(sourcesByTarget);
    }




    /*
     * Index tokens of sentences by their type.
     */
    private Map<String, List<Location>> indexTokens(
            Function<AlignedSentence, List<String>> tokens) {
        return sentences.stream().flatMap(s ->
                IntStream.range(0, tokens.apply(s).size())
                        .mapToObj((int j) -> new Location(s, j)))
                .collect(Collectors.groupingBy(loc ->
                        tokens.apply(loc.sentence).get(loc.tokenIndex)));
    }

    /*
     * Count one-to-one alignments.
     *
     * aligned: a function from positions to aligned words.
     */
    private static Map<String, Map<String, Long>> countLinks(
            Map<String, List<Location>> index,
            BiFunction<AlignedSentence, Integer, String> aligned) {
        // TODO Is there a better way to process map values?
        return index.keySet().stream().collect(Collectors.toMap(
                Function.identity(),
                k -> index.get(k).stream().map((Location loc) ->
                        aligned.apply(loc.sentence, loc.tokenIndex))
                        .filter(s -> s != null)
                        .collect(Collectors.groupingBy(
                                Function.identity(),
                                Collectors.counting()))));
    }


    /*
     * Sum counts.
     */
    private Map<String,Long> sumCounts(Map<String, Map<String, Long>> counts) {
        return counts.keySet().stream().collect(Collectors.toMap(
                Function.identity(),
                k -> counts.get(k).values().stream().collect(Collectors.summingLong(c -> c))));
    }

    /* Helper functions TODO Can these be replaced? */

    private static List<String> sourceTokens(AlignedSentence s) {
        return s.sourceTokens;
    }

    private static List<String> targetTokens(AlignedSentence s) {
        return s.targetTokens;
    }

    private static String alignedTarget(AlignedSentence s, Integer pos) {
        return s.alignedTarget(pos);
    }

    private static String alignedSource(AlignedSentence s, Integer pos) {
        return s.alignedSource(pos);
    }

    public double getFrequency(Expression source, Expression target) {
        if (source.language == sourceSuffix && target.language == targetSuffix) {
            Map<String, Long> counts = targetsBySource.get(source.text);
            if (counts != null && counts.containsKey(target.text)) {
                return 1.0 * counts.get(target.text) / sumBySource.get(source.text);
            }
            return 0.0;
        }
        if (source.language == targetSuffix && target.language == sourceSuffix) {
            Map<String, Long> counts = sourcesByTarget.get(source.text);
            if (counts != null && counts.containsKey(target.text)) {
                return 1.0 * counts.get(target.text) / sumByTarget.get(source.text);
            }
            return 0.0;
        }
        return 0.0;
    }

    private class Location {
        AlignedSentence sentence;
        int tokenIndex;

        public Location(AlignedSentence sentence, int tokenIndex) {
            this.sentence = sentence;
            this.tokenIndex = tokenIndex;
        }
    }
}
