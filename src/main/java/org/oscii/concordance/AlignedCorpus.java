package org.oscii.concordance;

import org.oscii.lex.Expression;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    /*
     * Return examples for a phrase translated into a target language.
     */
    public abstract List<AlignedSentence> examples(String query, String source, String target, int max);

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
