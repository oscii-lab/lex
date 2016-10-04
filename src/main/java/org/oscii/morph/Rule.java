package org.oscii.morph;

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import com.google.gson.annotations.Expose;

import java.util.HashMap;
import java.util.Map;

/**
 * An orthographic substitution
 */
public class Rule {
    public static final String EMPTY = "ε";
    private static Interner<Rule> interner = Interners.newWeakInterner();

    @Expose
    public String kind;
    @Expose
    public String from;
    @Expose
    public String to;
    private String string;

    private static Map<String, RuleKind> kinds = new HashMap<>();

    private interface RuleKind {
        String apply(String input, String from, String to);

        boolean applies(String input, String from);
    }

    static {
        kinds.put("p", new RuleKind() {
            public String apply(String input, String from, String to) {
                return input.replaceFirst(from, to);
            }

            public boolean applies(String input, String from) {
                return input.startsWith(from);
            }
        });
        kinds.put("s", new RuleKind() {
            public String apply(String input, String from, String to) {
                return input.substring(0, input.length() - from.length()) + to;
            }

            public boolean applies(String input, String from) {
                return input.endsWith(from);
            }
        });
    }

    public static Rule makePrefix(String from, String to) {
        return new Rule("p", from, to);
    }

    public static Rule makeSuffix(String from, String to) {
        return new Rule("s", from, to);
    }

    public Rule() { }

    public Rule(String kind, String from, String to) {
        assert kind != null && from != null && to != null;
        this.kind = kind;
        this.from = from;
        this.to = to;
    }

    // Not abstract because of serialization.
    public String apply(String input) {
        assert applies(input) : input;
        return kinds.get(kind).apply(input, from, to);
    }

    public boolean applies(String input) {
        return kinds.get(kind).applies(input, from);
    }

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
}
