package org.oscii.concordance;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A sentence word-aligned to a translation.
 */
public class AlignedSentence {
    public final List<String> sourceTokens;
    public final List<String> targetTokens;
    public final List<Link> links;
    public final Map<Integer, List<Link>> sourceByTarget;
    public final Map<Integer, List<Link>> targetBySource;

    public AlignedSentence(List<String> sourceTokens, List<String> targetTokens, List<Link> links) {
        this.sourceTokens = sourceTokens;
        this.targetTokens = targetTokens;
        this.links = links;
        sourceByTarget = links.stream().collect(Collectors.groupingBy(
                link -> new Integer(link.sourcePos)));
        targetBySource = links.stream().collect(Collectors.groupingBy(
                link -> new Integer(link.targetPos)));
    }

    /*
     * Create an aligned sentence from space-delimited strings.
     */
    public static AlignedSentence create(String source, String target, String align) {
        return new AlignedSentence(
                Arrays.asList(source.split("\\s")),
                Arrays.asList(target.split("\\s")),
                Arrays.asList(align.split("\\s")).stream()
                        .map(Link::parse).collect(Collectors.toList()));
    }

    /*
     * One-to-one aligned target word for source position
     */
    public String alignedTarget(int sourceIndex) {
        List<Link> sourceLinks = targetBySource.get(sourceIndex);
        if (sourceLinks != null && sourceLinks.size() == 1) {
            int target = sourceLinks.get(0).targetPos;
            List<Link> targetLinks = sourceByTarget.get(target);
            if (targetLinks != null && targetLinks.size() == 1) {
                return targetTokens.get(target);
            }
        }
        return null;
    }

    /*
     * One-to-one aligned source word for target position
     */
    public String alignedSource(int targetIndex) {
        List<Link> targetLinks = sourceByTarget.get(targetIndex);
        if (targetLinks != null && targetLinks.size() == 1) {
            int source = targetLinks.get(0).sourcePos;
            List<Link> sourceLinks = targetBySource.get(source);
            if (sourceLinks != null && sourceLinks.size() == 1) {
                return sourceTokens.get(source);
            }
        }
        return null;
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
