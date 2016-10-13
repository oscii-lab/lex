package org.oscii.corpus;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

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
        addLines(path, 0);
    }

    public void addLines(String path, long maxLines) throws IOException {
        log.info("Loading corpus from {}", path);
        Stream<String> newLines;
        if (path.endsWith(".gz")) {
            // From https://erikwramner.wordpress.com/2014/05/02/lazily-read-lines-from-gzip-file-with-java-8-streams/
            InputStream fileIs = null;
            BufferedInputStream bufferedIs = null;
            GZIPInputStream gzipIs = null;
            try {
                fileIs = Files.newInputStream(Paths.get(path));
                // Even though GZIPInputStream has a buffer it reads individual bytes
                // when processing the header, better add a buffer in-between
                bufferedIs = new BufferedInputStream(fileIs, 65535);
                gzipIs = new GZIPInputStream(bufferedIs);
            } catch (IOException e) {
                closeSafely(gzipIs);
                closeSafely(bufferedIs);
                closeSafely(fileIs);
                throw new UncheckedIOException(e);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(gzipIs));
            newLines = reader.lines().onClose(() -> closeSafely(reader));
        } else {
            newLines = Files.lines(Paths.get(path)).parallel();
        }
        if (maxLines > 0) {
            newLines = newLines.limit(maxLines);
        }
        addLines(newLines);
    }

    private static void closeSafely(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                // Ignore
            }
        }
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
