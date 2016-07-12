package org.oscii.morph;

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;

/**
 * An orthographic substitution
 */
public abstract class Substitution {
    public static final String EMPTY = "ε";
    private static Interner<Substitution> interner = Interners.newWeakInterner();

    public final String id;
    public final String from;
    public final String to;

    public Substitution(String id, String from, String to) {
        assert id != null && from != null && to != null;
        this.id = id;
        this.from = from;
        this.to = to;
    }

    public abstract String apply(String input);

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Substitution that = (Substitution) o;

        if (!id.equals(that.id)) return false;
        if (!from.equals(that.from)) return false;
        return to.equals(that.to);
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + from.hashCode();
        result = 31 * result + to.hashCode();
        return result;
    }

    public String toString() {
        return id + "/" + (from.isEmpty() ? EMPTY : from) + "/" + (to.isEmpty() ? EMPTY : to);
    }

    public Substitution intern() {
        return interner.intern(this);
    }

  /* Implementations */

    public static class Prefix extends Substitution {
        public Prefix(String from, String to) {
            super("p", from, to);
        }

        @Override
        public String apply(String input) {
            assert input.startsWith(from);
            return input.replaceFirst(from, to);
        }
    }

    public static class Suffix extends Substitution {
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
