package org.oscii.concordance;

import com.codepoetics.protonpack.StreamUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Index and compute statistics over an aligned corpus.
 */
public class AlignedCorpus {
    // Directory containing sentence and alignment files
    private String path;
    private String sourceSuffix;
    private String targetSuffix;

    List<AlignedSentence> sentences = new ArrayList<>();
    Map<String, List<Location>> sourceIndex = new HashMap<>();
    Map<String, List<Location>> targetIndex = new HashMap<>();

    public AlignedCorpus(String path, String sourceSuffix, String targetSuffix) {
        this.path = path;
        this.sourceSuffix = sourceSuffix;
        this.targetSuffix = targetSuffix;
    }

    public void read() throws IOException {
        Stream<String> sources = Files.lines(Paths.get(path + "." + sourceSuffix));
        Stream<String> targets = Files.lines(Paths.get(path + "." + targetSuffix));
        Stream<String> aligns = Files.lines(Paths.get(path + ".align"));
        sentences = StreamUtils.zip(sources, targets, aligns,
                (s, t, a) -> AlignedSentence.create(s, t, a))
                .collect(Collectors.toList());
    }

    private class Location {
        int sentenceIndex;
        int tokenIndex;

        public Location(int sentenceIndex, int tokenIndex) {
            this.sentenceIndex = sentenceIndex;
            this.tokenIndex = tokenIndex;
        }
    }
}
