package org.oscii.panlex;

public interface PanLexDB {
    PanLexRecord lookup(String query, String sourceLanguage, String targetLanguage);
}
