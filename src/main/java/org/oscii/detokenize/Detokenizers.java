package org.oscii.detokenize;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * A collection of detokenizers by language.
 */
public class Detokenizers {
    private final String path;
    private final Map<String, Detokenizer> cache = new HashMap<>();

    public Detokenizers(String path) {
        this.path = path;
    }

    public Detokenizer get(String language) throws IOException, ClassNotFoundException {
        if (!cache.containsKey(language)) {
            cache.put(language, Detokenizer.load(new File(String.format(path, language))));
        }
        return cache.get(language);
    }
}
