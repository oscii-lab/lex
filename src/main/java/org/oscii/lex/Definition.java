package org.oscii.lex;

import java.util.List;

/**
 * A definition of an expression
 */
public class Definition {
    public final String text;
    public final List<String> pos;
    public final String languageTag;

    public Definition(String text, List<String> pos, String languageTag) {
        this.text = text;
        this.pos = pos;
        this.languageTag = languageTag;
    }

    @Override
    public String toString() {
        return "Definition{" +
                "text='" + text + '\'' +
                ", pos=" + pos +
                ", languageTag='" + languageTag + '\'' +
                '}';
    }
}
