package org.oscii.concordance;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A sentence word-aligned to a translation.
 */
public class AlignedSentence {
    public final List<String> sourceTokens;
    public final List<String> targetTokens;
    public final List<Link> links;

    public AlignedSentence(List<String> sourceTokens, List<String> targetTokens, List<Link> links) {
        this.sourceTokens = sourceTokens;
        this.targetTokens = targetTokens;
        this.links = links;
    }

    public static AlignedSentence create(String source, String target, String align) {
        return new AlignedSentence(
                Arrays.asList(source.split("\\s")),
                Arrays.asList(target.split("\\s")),
                Arrays.asList(align.split("\\s")).stream()
                        .map(Link::parse).collect(Collectors.toList()));
    }

    private static class Link {
        int sourcePos;
        int targetPos;

        public Link(int sourcePos, int targetPos) {
            this.sourcePos = sourcePos;
            this.targetPos = targetPos;
        }

        public static Link parse(String link) {
            String[] parts = link.split("-");
            if (parts.length != 2) {
                throw new NumberFormatException();
            }
            return new Link(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        }
    }
}
