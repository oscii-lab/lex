package org.oscii.morph;

/**
 * The way to transform one word into another using a substitution.
 */
public class RuleLexicalized {
    final RulePair pair;
    final Rule sub;

    public RuleLexicalized(String input, String output, Rule sub) {
        assert sub != null;
        this.pair = new RulePair(input, output);
        this.sub = sub;
    }
}
