package org.oscii.concordance;

import com.codepoetics.protonpack.StreamUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.oscii.lex.Expression;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Index and compute statistics over an aligned corpus.
 */
public class AlignedCorpus {
    // language -> sentences
    Map<String, List<AlignedSentence>> sentences = new HashMap<>();
    // language -> word -> locations
    Map<String, Map<String, List<Location>>> index = new HashMap<>();
    // language -> word -> language -> word -> count
    Map<String, Map<String, Map<String, Map<String, Long>>>> pairCounts = new HashMap<>();
    // language -> word -> language -> count
    Map<String, Map<String, Map<String, Long>>> wordCounts = new HashMap<>();

    private final static Logger log = LogManager.getLogger(AlignedCorpus.class);

    public boolean exists(String path, String sourceLanguage, String targetLanguage) {
        return paths(path, sourceLanguage, targetLanguage).stream().allMatch(Files::exists);
    }

    private List<Path> paths(String path, String sourceLanguage, String targetLanguage) {
        return Arrays.asList(new String[]{sourceLanguage, targetLanguage, "align"}).stream()
                .map(extension -> Paths.get(String.format("%s.%s-%s.%s", path, sourceLanguage, targetLanguage, extension)))
                .collect(Collectors.toList());
    }

    /*
     * Read and index a parallel corpus.
     */
    public void read(String path, String sourceLanguage, String targetLanguage, int max) throws IOException {
        log.info("Reading sentences: " + sourceLanguage + "-" + targetLanguage);
        List<Path> paths = paths(path, sourceLanguage, targetLanguage);
        Stream<String> sources = Files.lines(paths.get(0));
        Stream<String> targets = Files.lines(paths.get(1));
        Stream<String> aligns = Files.lines(paths.get(2));
        if (max > 0) {
            sources = sources.limit(max);
            targets = targets.limit(max);
            aligns = aligns.limit(max);
        }
        List<AlignedSentence> aligned = new ArrayList<>();
        StreamUtils.zip(sources, targets, aligns,
                (s, t, a) -> AlignedSentence.parse(s, t, a, sourceLanguage, targetLanguage))
                .forEach(aligned::addAll);
        log.info("Grouping by language");
        aligned.stream().collect(Collectors.groupingBy(a -> a.language))
                .entrySet().stream().forEach(e -> {
            List<AlignedSentence> all = sentences.get(e.getKey());
            if (all == null) {
                all = new ArrayList<>(e.getValue().size());
                sentences.put(e.getKey(), all);
            }
            all.addAll(e.getValue());
        });
    }

    /*
     * Create indices and counts.
     */
    public void tally() {
        sentences.keySet().stream().forEach(language -> {
            log.info("Indexing words");
            Map<String, List<Location>> indexForLanguage = indexTokens(sentences.get(language));
            index.put(language, indexForLanguage);
            log.info("Counting aligned pairs");
            Map<String, Map<String, Map<String, Long>>> pairs = countLinks(indexForLanguage);
            pairCounts.put(language, pairs);
            wordCounts.put(language, sumCounts(pairs));
        });
    }

    /*
     * Index tokens of sentences by their type.
     */
    private Map<String, List<Location>> indexTokens(List<AlignedSentence> ss) {
        return ss.stream()
                .flatMap(s -> IntStream.range(0, s.tokens.length).mapToObj(j -> new Location(s, j)))
                .collect(Collectors.groupingBy(Location::token));
    }

    /*
     * Count all one-to-one alignments.
     *
     * index:   word -> locations
     * returns: word -> language -> word -> co-occurrence count
     */
    private static Map<String, Map<String, Map<String, Long>>> countLinks(
            Map<String, List<Location>> index) {
        return mapValues(
                groupValues(index, loc -> loc.sentence.aligned.language),
                m -> mapValues(m, AlignedCorpus::countTranslations));
    }

    /*
     * Count one-to-one aligned translations in locations.
     */
    private static Map<String, Long> countTranslations(List<Location> locations) {
        return locations.stream().map(loc -> loc.sentence.aligned(loc.tokenIndex))
                .filter(s -> s != null)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }

    /*
     * Sum counts for each word by target language.
     */
    private Map<String, Map<String, Long>> sumCounts(Map<String, Map<String, Map<String, Long>>> pairs) {
        // TODO(denero) Is there a better way to sum in Java?
        Function<Map<String, Long>, Long> sum = counts -> counts.values().stream().collect(Collectors.summingLong(c -> c));
        return mapValues(pairs, m -> mapValues(m, sum));
    }

    /*
     * Return the translation frequency of two words.
     */
    public double getFrequency(Expression source, Expression target) {
        // TODO(denero) Check for null languages.
        Map<String, Map<String, Long>> translations;
        translations = pairCounts.get(source.language).get(source.text);
        if (translations != null && translations.containsKey(target.language)) {
            Long count = translations.get(target.language).get(target.text);
            if (count != null && count > 0) {
                long total = wordCounts.get(source.language).get(source.text).get(target.language);
                return 1.0 * count / total;
            }
        }
        return 0.0;
    }

    /* Map utilities */

    /*
     * Group values of a map by a key function.
     */
    private static <K, T, U> Map<K, Map<U, List<T>>> groupValues(Map<K, List<T>> m,
                                                                 Function<T, U> key) {
        return mapValues(m, ts -> ts.stream().collect(Collectors.groupingBy(key)));
    }

    /*
     * Map values of a map, maintaining keys.
     */
    private static <K, T, U> Map<K, U> mapValues(Map<K, T> m, Function<T, U> f) {
        return m.keySet().stream().collect(Collectors.toMap(
                Function.identity(), k -> f.apply(m.get(k))));
    }

    /* Support classes */

    /*
     * A position in an aligned sentence.
     */
    private class Location {
        AlignedSentence sentence;
        int tokenIndex;

        Location(AlignedSentence sentence, int tokenIndex) {
            this.sentence = sentence;
            this.tokenIndex = tokenIndex;
        }

        String token() {
            return sentence.tokens[tokenIndex];
        }
    }
}
