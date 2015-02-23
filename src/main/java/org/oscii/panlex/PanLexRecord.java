package org.oscii.panlex;

import org.oscii.RabbitHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// All information for a source term and a target language.
public class PanLexRecord {
    String term;
    String sourceLanguage;
    String targetLanguage;
    Set<Set<String>> translations; // Clustered by meaning

    public PanLexRecord(String term, String sourceLanguage, String targetLanguage, Set<Set<String>> translations) {
        this.term = term;
        this.sourceLanguage = sourceLanguage;
        this.targetLanguage = targetLanguage;
        this.translations = translations;
    }

    public List<String> getTranslationList() {
        List<String> s = new ArrayList<String>();
        for (Set<String> meaning : translations) {
            s.addAll(meaning);
        }
        return s;
    }

    public String getTerm() {
        return term;
    }
}
