package org.oscii.morph;

import java.util.List;

/**
 * Created by denero on 7/14/16.
 */
public class RuleScored {
    Rule sub;
    List<RuleLexicalized> support;
    List<RuleLexicalized> sample;
    List<Integer> cosineRanks;
    float hitRate;


}
