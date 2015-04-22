package org.oscii.concordance;

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
    public final int[][] alignment;
    public final String language;
    public AlignedSentence aligned;

    private AlignedSentence(String[] tokens, int[][] alignment, String language) {
        this.tokens = tokens;
        this.alignment = alignment;
        this.language = language;
        for (int i = 0; i < tokens.length; i++) {
            tokens[i] = tokens[i].intern();
        }
    }

    /*
     * Create an aligned sentence from space-delimited strings.
     */
    public static List<AlignedSentence> parse(String source, String target, String align, String sourceLanguage, String targetLanguage) {
        String[] sourceTokens = source.split("\\s");
        String[] targetTokens = target.split("\\s");
        List<Link> links = asList(align.split("\\s")).stream().map(Link::parse).collect(toList());
        int sl = sourceTokens.length, tl = targetTokens.length;
        AlignedSentence sourceToTarget = new AlignedSentence(sourceTokens, collectLinks(links, sl, false), sourceLanguage);
        AlignedSentence targetToSource = new AlignedSentence(targetTokens, collectLinks(links, tl, true), targetLanguage);
        sourceToTarget.aligned = targetToSource;
        targetToSource.aligned = sourceToTarget;
        return asList(new AlignedSentence[]{sourceToTarget, targetToSource});
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
