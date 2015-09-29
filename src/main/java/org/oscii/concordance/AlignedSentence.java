package org.oscii.concordance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

/**
 * A sentence word-aligned to a translation.
 */
public class AlignedSentence {
    public final String[] tokens;
    public final String[] delimiters;

    private int[][] alignment;
    public final String language;
    public AlignedSentence aligned;
    String source;

    private AlignedSentence(String[] tokens, String[] delimiters, int[][] alignment, String language) {
        this(tokens, delimiters, alignment, language, "");
    }

    private AlignedSentence(String[] tokens, String[] delimiters, int[][] alignment, String language, String source) {
        this.tokens = tokens;
        this.delimiters = delimiters;
        this.alignment = alignment;
        this.language = language;
        this.source = source;
        for (int i = 0; i < tokens.length; i++) {
            tokens[i] = tokens[i].intern();
        }
    }

    /**
     * Lazily populate and return alignments.
     *
     * @return
     */
    public int[][] getAlignment() {
        if (alignment == null && aligned.alignment != null) {
            alignment = reverse(aligned.alignment, tokens.length);
        }
        return alignment;
    }

    /**
     * Create aligned sentences from tokens and Moses-format alignment links.
     */
    public static List<AlignedSentence> parse(String[] sourceTokens, String[] targetTokens, String[] alignment,
                                              String sourceLanguage, String targetLanguage) {
        List<Link> links = asList(alignment).stream().map(Link::parse).collect(toList());
        int sl = sourceTokens.length, tl = targetTokens.length;
        AlignedSentence sourceToTarget = new AlignedSentence(sourceTokens, defaultDelimiters(sourceTokens.length), collectLinks(links, sl, false), sourceLanguage);
        AlignedSentence targetToSource = new AlignedSentence(targetTokens, defaultDelimiters(sourceTokens.length), collectLinks(links, tl, true), targetLanguage);
        sourceToTarget.aligned = targetToSource;
        targetToSource.aligned = sourceToTarget;
        return asList(new AlignedSentence[]{sourceToTarget, targetToSource});
    }

    private static String[] defaultDelimiters(int length) {
        String[] delimiters = new String[length+1];
        delimiters[0] = ""; // No space before first word
        delimiters[length] = ""; // No space after last word
        for (int i = 1; i < length; i++) {
            delimiters[i] = " "; // Spaces everywhere else
        }
        return delimiters;
    }

    /**
     * Create an aligned sentence from tokens and an alignment matrix.
     */
    public static AlignedSentence create(
            String[] sourceTokens,
            String[] sourceDelimiters,
            String[] targetTokens,
            String[] targetDelimiters,
            int[][] sourceToTargetLinks,
            String sourceLanguage,
            String targetLanguage) {
        AlignedSentence sourceToTarget = new AlignedSentence(sourceTokens, sourceDelimiters, sourceToTargetLinks, sourceLanguage);
        AlignedSentence targetToSource = new AlignedSentence(targetTokens, targetDelimiters, null, targetLanguage);
        sourceToTarget.aligned = targetToSource;
        targetToSource.aligned = sourceToTarget;
        return sourceToTarget;
    }

    /**
     * Reverse a source-to-target link array.
     */
    private static int[][] reverse(int[][] orig, int len) {
        // TODO(denero) Could be faster without creating Link objects.
        List<Link> links = new ArrayList<>();
        for (int i = 0; i < orig.length; i++) {
            for (int j = 0; j < orig[i].length; j++) {
                links.add(new Link(i, j));
            }
        }
        return collectLinks(links, len, true);
    }

    /*
     * Convert list of links to a directional 2-d array of link positions.
     */
    private static int[][] collectLinks(List<Link> links, int len, boolean isTarget) {
        int[][] alignment = new int[len][];
        Map<Integer, List<Link>> index = links.stream().collect(
                groupingBy(link -> link.get(isTarget)));
        for (int i = 0; i < len; i++) {
            List<Link> s = index.get(i);
            if (s == null) {
                alignment[i] = new int[0];
            } else {
                alignment[i] = new int[s.size()];
                for (int j = 0; j < s.size(); j++) {
                    alignment[i][j] = s.get(j).get(!isTarget);
                }
            }
        }
        return alignment;
    }

    /*
     * One-to-one aligned target word for source position
     */
    public String aligned(int index) {
        int linkedIndex = 0;
        if (alignment[index].length == 1) {
            linkedIndex = alignment[index][0];
            if (aligned.alignment[linkedIndex].length == 1) {
                return aligned.tokens[linkedIndex];
            }
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AlignedSentence that = (AlignedSentence) o;

        if (!Arrays.deepEquals(alignment, that.alignment)) return false;
        if (!language.equals(that.language)) return false;
        if (!source.equals(that.source)) return false;
        if (!aligned.language.equals(that.aligned.language)) return false;
        if (!aligned.source.equals(that.aligned.source)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        int result = Arrays.deepHashCode(alignment);
        result = 31 * result + language.hashCode();
        result = 31 * result + source.hashCode();
        result = 31 * result + aligned.language.hashCode();
        result = 31 * result + aligned.source.hashCode();
        return result;
    }

    /*
     * A parsed link.
     */
    private static class Link {
        int sourcePos;
        int targetPos;

        Link(int sourcePos, int targetPos) {
            this.sourcePos = sourcePos;
            this.targetPos = targetPos;
        }

        static Link parse(String link) {
            String[] parts = link.split("-");
            if (parts.length != 2) {
                throw new NumberFormatException();
            }
            return new Link(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        }

        Integer get(boolean target) {
            return target ? targetPos : sourcePos;
        }
    }
}
