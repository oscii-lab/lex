package org.oscii.concordance;

import gnu.trove.THashMap;
import org.oscii.lex.Expression;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;

/**
 * Interface to access corpus statistics and examples
 */
public abstract class AlignedCorpus {
    static ParallelFiles paths(String path, String sourceLanguage, String targetLanguage) {
        Function<String, Path> p = ext -> Paths.get(String.format("%s.%s-%s.%s", path, sourceLanguage, targetLanguage, ext));
        return new ParallelFiles(p.apply(sourceLanguage), p.apply(targetLanguage), p.apply("align"));
    }

    public boolean exists(String path, String sourceLanguage, String targetLanguage) {
        return paths(path, sourceLanguage, targetLanguage).stream().allMatch(Files::exists);
    }

    /*
     * Read parallel files.
     */
    public abstract void read(String path, String sourceLanguage, String targetLanguage, int max) throws IOException;

    /*
     * Return a function that takes phrases in another language and returns translation frequencies.
     */
    public abstract Function<Expression, Double> translationFrequencies(Expression source);

    // Always return 0.0
    static Double zeroFrequency(Expression e) {
        return 0.0;
    }

    // Return count / total
    Function<Expression, Double> normalizeByLanguage(final Map<String, Map<String, Long>> counts) {
        final Map<String, Long> totals = sumCounts(counts);
        return target -> {
            Map<String, Long> byTarget = counts.get(target.language);
            Long count = byTarget == null ? null : byTarget.get(target.text);
            if (count != null && count > 0) {
                long total = totals.get(target.language);
                return 1.0 * count / total;
            }
            return 0.0;
        };
    }

    /*
     * Sum counts for each word by target language.
     */
    private Map<String, Long> sumCounts(Map<String, Map<String, Long>> counts) {
        Function<Map<String, Long>, Long> sumValues =
                c -> c.values().stream().mapToLong(x -> x).sum();
        return mapValues(counts, sumValues);
    }

    /*
     * Map values of a map, maintaining keys.
     */
    static <K, T, U> Map<K, U> mapValues(Map<K, T> m, Function<T, U> f) {
        return m.entrySet().stream().collect(toMap(
                Map.Entry::getKey,
                kv -> f.apply(kv.getValue()),
                (a, b) -> a,
                THashMap::new));
    }

    /*
     * Return examples for a phrase translated into a target language.
     */
    public abstract List<SentenceExample> examples(String query, String source, String target, int max);

    public void tally() {
    }

    static class ParallelFiles {
        Path sourceSentences;
        Path targetSentences;
        Path alignments;

        ParallelFiles(Path sourceSentences, Path targetSentences, Path alignments) {
            this.sourceSentences = sourceSentences;
            this.targetSentences = targetSentences;
            this.alignments = alignments;
        }

        Stream<Path> stream() {
            return Arrays.asList(new Path[]{sourceSentences, targetSentences, alignments}).stream();
        }
    }
}
