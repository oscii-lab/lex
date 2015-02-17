package org.oscii.panlex;

import com.google.gson.Gson;
import org.oscii.panlex.PanLexRecord;

/**
 * In-memory representation of PanLex data.
 */
public interface PanLexDB {
    PanLexRecord lookup(String query, String sourceLanguage, String targetLanguage);


}
