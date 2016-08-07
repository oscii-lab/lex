package org.oscii.morph;

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import com.google.gson.annotations.Expose;

/**
 * An orthographic substitution
 */
public abstract class Rule {
    public static final String EMPTY = "ε";
    private static Interner<Rule> interner = Interners.newWeakInterner();

    @Expose
    public final String kind;
    @Expose
    public final String from;
    @Expose
    public final String to;
    private String string;

    public Rule(String kind, String from, String to) {
        assert kind != null && from != null && to != null;
        this.kind = kind;
        this.from = from;
        this.to = to;
    }

    public abstract String apply(String input);

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Rule that = (Rule) o;

        if (!kind.equals(that.kind)) return false;
        if (!from.equals(that.from)) return false;
        return to.equals(that.to);
    }

    @Override
    public int hashCode() {
        int result = kind.hashCode();
        result = 31 * result + from.hashCode();
        result = 31 * result + to.hashCode();
        return result;
    }

    public String toString() {
        if (string == null) {
            string = kind + "/" + (from.isEmpty() ? EMPTY : from) + "/" + (to.isEmpty() ? EMPTY : to);
        }
        return string;
    }

    public Rule intern() {
        return interner.intern(this);
    }

    /* Implementations */

    /**
     * A prefix substitution.
     */
    public static class Prefix extends Rule {
        public Prefix(String from, String to) {
            super("p", from, to);
        }

        @Override
        public String apply(String input) {
            assert input.startsWith(from) : input;
            return input.replaceFirst(from, to);
        }
    }

    /**
     * A suffix substitution.
     */
    public static class Suffix extends Rule {
        public Suffix(String from, String to) {
            super("s", from, to);
        }

        @Override
        public String apply(String input) {
            assert input.endsWith(from) : input;
            return input.substring(0, input.length() - from.length()) + to;
        }
    }
}
