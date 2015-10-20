package org.oscii.concordance;

import com.codepoetics.protonpack.StreamUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.oscii.lex.Expression;
import org.oscii.lex.Meaning;

import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.*;

/**
 * Index and compute statistics over an aligned corpus.
 * <p>
 * This implementation is deprecated; use the SuffixArrayCorpus instead.
 */
public class IndexedAlignedCorpus extends AlignedCorpus {
    // language -> sentences
    Map<String, List<AlignedSentence>> sentences = new HashMap<>();
    // language -> word -> locations
    Map<String, Map<String, List<Location>>> index = new HashMap<>();

    private final static Logger log = LogManager.getLogger(IndexedAlignedCorpus.class);

    @Override
    public void read(String path, String sourceLanguage, String targetLanguage, int max) throws IOException {
        log.info("Reading sentences: " + sourceLanguage + "-" + targetLanguage);
        AlignedCorpus.ParallelFiles paths = paths(path, sourceLanguage, targetLanguage);
        Stream<String> sources = Files.lines(paths.sourceSentences);
        Stream<String> targets = Files.lines(paths.targetSentences);
        Stream<String> aligns = Files.lines(paths.alignments);
        if (max > 0) {
            sources = sources.limit(max);
            targets = targets.limit(max);
            aligns = aligns.limit(max);
        }
        List<AlignedSentence> aligned = new ArrayList<>();
        StreamUtils.zip(sources, targets, aligns,
                (s, t, a) -> AlignedSentence.parse(s.split("\\s+"), t.split("\\s+"), a.split("\\s+"), sourceLanguage, targetLanguage))
                .forEach(aligned::addAll);
        log.info("Grouping by language");
        aligned.stream().collect(groupingBy(a -> a.language))
                .entrySet().stream().forEach(e -> {
            List<AlignedSentence> all = sentences.get(e.getKey());
            if (all == null) {
                all = new ArrayList<>(e.getValue().size());
                sentences.put(e.getKey(), all);
            }
            all.addAll(e.getValue());
        });
    }

    @Override
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
                .collect(groupingBy(Location::token));
    }

    /*
     * Return a function that takes words in another language and returns translation frequencies.
     */
    @Override
    public Function<Expression, Double> translationFrequencies(Expression source) {
        if (!index.containsKey(source.language)) {
            return AlignedCorpus::zeroFrequency;
        }
        List<Location> locations = index.get(source.language).get(source.text);
        if (locations == null) {
            return AlignedCorpus::zeroFrequency;
        }
        Map<String, Map<String, Long>> counts = countAll(locations);
        return normalizeByLanguage(counts);
    }

    private Map<String, Map<String, Long>> countAll(List<Location> locations) {
        return mapValues(
                locations.stream().collect(
                        groupingBy(t -> t.sentence.aligned.language)),
                IndexedAlignedCorpus::countTranslations);
    }

    /*
     * Count one-to-one aligned translations in locations.
     */
    private static Map<String, Long> countTranslations(List<Location> locations) {
        return locations.stream().map(loc -> loc.sentence.aligned(loc.tokenIndex))
                .filter(s -> s != null)
                .collect(groupingBy(Function.identity(), counting()));
    }


    @Override
    public List<SentenceExample> examples(String query, String source, String target, int max, int memoryId) {
        if (!index.containsKey(source)) {
            return Collections.EMPTY_LIST;
        }
        Map<String, List<Location>> locations = index.get(source);
        if (!locations.containsKey(query)) {
            return Collections.EMPTY_LIST;
        }
        Stream<Location> forQuery = locations.get(query).stream()
                .filter(loc -> loc.sentence.aligned.language.equals(target));
        if (max > 0) {
            forQuery = forQuery.limit(max);
        }
        return forQuery.map(loc -> new SentenceExample(loc.sentence, loc.tokenIndex, 1, 0, 0)).collect(toList());
    }

    @Override
    public void scoreMeaning(Meaning m) {}

    /* Map utilities */



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
