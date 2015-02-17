package org.oscii.panlex;

import java.util.List;

// All information for a source term and a target language.
public class PanLexRecord {
    String term;
    String sourceLanguage;
    String targetLanguage;
    List<List<String>> translations; // Clustered by meaning

    public PanLexRecord(String term, String sourceLanguage, String targetLanguage, List<List<String>> translations) {
        this.term = term;
        this.sourceLanguage = sourceLanguage;
        this.targetLanguage = targetLanguage;
        this.translations = translations;
    }
}
