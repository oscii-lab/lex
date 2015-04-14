package org.oscii.concordance;

import com.codepoetics.protonpack.StreamUtils;
import gnu.trove.map.hash.THashMap;
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
public class IndexedAlignedCorpus implements AlignedCorpus {
    // language -> sentences
    Map<String, List<AlignedSentence>> sentences = new HashMap<>();
    // language -> word -> locations
    Map<String, Map<String, List<Location>>> index = new HashMap<>();

    private final static Logger log = LogManager.getLogger(IndexedAlignedCorpus.class);

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
            log.info("Indexing words for " + language);
            Map<String, List<Location>> indexForLanguage = indexTokens(sentences.get(language));
            index.put(language, indexForLanguage);
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

    // Always return 0.0
    private static Double zeroFrequency(Expression e) {
        return 0.0;
    }

    /*
     * Return a function that takes words in another language and returns translation frequencies.
     */
    @Override
    public Function<Expression, Double> translationFrequencies(Expression source) {
        if (!index.containsKey(source.language)) {
            return IndexedAlignedCorpus::zeroFrequency;
        }
        List<Location> locations = index.get(source.language).get(source.text);
        if (locations == null) {
            return IndexedAlignedCorpus::zeroFrequency;
        }
        Map<String, Map<String, Long>> counts = countAll(locations);
        Map<String, Long> totals = sumCounts(counts);
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

    private Map<String, Map<String, Long>> countAll(List<Location> locations) {
        return mapValues(
                locations.stream().collect(
                        Collectors.groupingBy(t -> t.sentence.aligned.language)),
                IndexedAlignedCorpus::countTranslations);
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
    private Map<String, Long> sumCounts(Map<String, Map<String, Long>> counts) {
        Function<Map<String, Long>, Long> sumValues =
                c -> c.values().stream().mapToLong(x -> x).sum();
        return mapValues(counts, sumValues);
    }

    @Override
    public List<AlignedSentence> examples(String query, String source, String target, int max) {
        if (!index.containsKey(source)) {
            return Collections.EMPTY_LIST;
        }
        Map<String, List<Location>> locations = index.get(source);
        if (!locations.containsKey(query))  {
            return Collections.EMPTY_LIST;
        }
        Stream<Location> forQuery = locations.get(query).stream()
                .filter(loc -> loc.sentence.aligned.language.equals(target));
        if (max > 0) {
            forQuery = forQuery.limit(max);
        }
        return forQuery.map(loc -> loc.sentence).collect(Collectors.toList());
    }

    /* Map utilities */

    /*
     * Map values of a map, maintaining keys.
     */
    private static <K, T, U> Map<K, U> mapValues(Map<K, T> m, Function<T, U> f) {
        return m.keySet().stream().collect(Collectors.toMap(
                Function.identity(),
                k -> f.apply(m.get(k)),
                (a, b) -> a,
                THashMap::new));
    }

    /* Support classes */

    /*
     * A position in an aligned sentence.
     */
    private static class Location {
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
