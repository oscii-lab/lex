package org.oscii.lex;

/**
 * A written form of a lexeme in a language.
 */
public class Expression {
    public String text;
    String degraded_text; // Lowercased, etc.
    public String language; // ISO-639-1 code (2-letter), e.g., "zh"

    @Override
    public String toString() {
        return "Expression{" +
                "text='" + text + '\'' +
                "language='" + language + '\'' +
                '}';
    }

    // Tag inventory: http://www.iana.org/assignments/language-subtag-registry/language-subtag-registry
    String language_tag; // RFC5654 language tag, e.g., "zh-cmn-Hans-CN"

    public Expression(String text, String language_tag) {
        this.text = text;
        this.language_tag = language_tag;
        // TODO(denero) Split language tag to get language
        this.language = language_tag;
        // TODO(denero) Generate degraded text
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Expression that = (Expression) o;

        if (!language.equals(that.language)) return false;
        if (!text.equals(that.text)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = text.hashCode();
        result = 31 * result + language.hashCode();
        return result;
    }
}
