package org.oscii.morph;

/**
 * Two words that can be transformed into one another.
 */
public class RulePair {
    final String input;
    final String output;

    public RulePair(String input, String output) {
        assert input != null && output != null;
        this.input = input;
        this.output = output;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RulePair wordPair = (RulePair) o;

        if (!input.equals(wordPair.input)) return false;
        return output.equals(wordPair.output);

    }

    @Override
    public int hashCode() {
        int result = input.hashCode();
        result = 31 * result + output.hashCode();
        return result;
    }
}
