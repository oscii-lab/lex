package org.oscii.corpus;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

/**
 * Indexed corpus.
 */
public class Corpus {
    final Map<String, PostingList> postings = new HashMap<>();
    final List<String[]> lines = new ArrayList<>();
    final Tokenizer tokenizer;
    private final static Logger log = LogManager.getLogger(Corpus.class);

    public Corpus(Tokenizer tokenizer) {
        this.tokenizer = tokenizer;
    }

    public void addLines(String path) throws IOException {
        log.info("Loading corpus from {}", path);
        Stream<String> newLines = Files.lines(Paths.get(path)).parallel();
        addLines(newLines);
    }

    public void addLines(Stream<String> newLines) {
        int start = lines.size();
        newLines.map(tokenizer::tokenize).map(this::internAll).forEachOrdered(lines::add);
        for (int i = start; i < lines.size(); i++) {
            String[] tokens = lines.get(i);
            for (int j = 0; j < tokens.length; j++) {
                indexWord(tokens[j], i, j);
            }
        }
    }

    public int count(String word) {
        return postings(word).size();
    }

    public PostingList postings(String word) {
        return postings.getOrDefault(word, PostingList.EMPTY);
    }

    private String[] internAll(String[] strings) {
        for (int i = 0; i < strings.length; i++) {
            strings[i] = strings[i].intern();
        }
        return strings;
    }

    private void indexWord(String word, int line, int position) {
        PostingList p = postings.getOrDefault(word, null);
        if (p == null) {
            p = new PostingList();
            postings.put(word, p);
        }
        p.addPosting(line, position);
    }

    public Set<String> vocab() {
        return postings.keySet();
    }
}
