package org.oscii.detokenize;

import com.codepoetics.protonpack.StreamUtils;
import edu.stanford.nlp.mt.process.Preprocessor;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.Sequence;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * A corpus of raw and tokenized segments.
 */
public interface TokenizedCorpus {
    public class Entry {
        private final String raw;
        private final List<String> tokens;

        public String getRaw() {
            return raw;
        }

        public List<String> getTokens() {
            return tokens;
        }

        public Entry(String raw, List<String> tokens) {

            this.raw = raw;
            this.tokens = tokens;
        }
    }

    Stream<Entry> stream();

    /*
     * Entries are generated from a preprocessor.
     */
    public class PreprocessorCorpus implements TokenizedCorpus {
        private final Preprocessor preprocessor;
        private final Stream<String> rawCorpus;

        public PreprocessorCorpus(Preprocessor preprocessor, Stream<String> rawCorpus) {
            this.preprocessor = preprocessor;
            this.rawCorpus = rawCorpus;
        }

        @Override
        public Stream<Entry> stream() {
            return rawCorpus.map(s -> new Entry(s, tokenize(preprocessor, s)));
        }
    }

    /*
     * Entries are provided as parallel streams.
     */
    public class ParallelCorpus implements TokenizedCorpus {
        private final Stream<String> rawCorpus;
        private final Stream<String> tokenizedCorpus;

        public ParallelCorpus(Stream<String> rawCorpus, Stream<String> tokenizedCorpus) {
            this.rawCorpus = rawCorpus;
            this.tokenizedCorpus = tokenizedCorpus;
        }

        @Override
        public Stream<Entry> stream() {
            return StreamUtils.zip(rawCorpus, tokenizedCorpus,
                    (raw, t) -> new Entry(raw, Arrays.asList(t.split("\\s+"))));
        }
    }

    /*
     * Convenience method for converting from CoreNLP proprietary data structures.
     */
    public static List<String> tokenize(Preprocessor preprocessor, String sentence) {
        Sequence<IString> tokenSequence = preprocessor.process(sentence);
        List<String> tokens = new ArrayList<>(tokenSequence.size());
        tokenSequence.forEach(t -> tokens.add(t.toString()));
        return tokens;
    }
}
