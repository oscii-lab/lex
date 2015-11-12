package org.oscii.lex;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.Reader;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ranks a set of query extensions according to a list of ranked
 * queries.
 */
public class Ranker {
  private final static Logger logger = LogManager.getLogger(Ranker.class);

  // language code -> query -> rank
  private Map<String, Map<String, Integer>> ranks = new HashMap<>();

  public Ranker(File infile) throws IOException {
    this(new LineNumberReader(new BufferedReader(new FileReader(infile))));
  }

  public Ranker(Reader reader) throws IOException {
    read(reader);
  }

  private void read(Reader reader) throws IOException {
    CSVParser parser = new CSVParser(reader, CSVFormat.DEFAULT);
    parseCsv(parser);
  }

  private void parseCsv(CSVParser parser) {
    int numEntries = 0;
    for (CSVRecord record : parser) {
      if (record.size() != 4) {
        logger.warn("skipped record of size {}", record.size());
        continue;
      }
      String srcLang = record.get(0);
      String trgLang = record.get(1);
      int rank = Integer.parseInt(record.get(2));
      String query = record.get(3);
      String key = makeKey(srcLang, trgLang);

      if (!ranks.containsKey(key)) {
        ranks.put(key, new HashMap<>());
      }
      ranks.get(key).put(query, rank);
      ++numEntries;
    }
    logger.info("read {} ranked queries", numEntries);
  }

  private static String makeKey(String srcLang, String trgLang) {
    return srcLang + "_" + trgLang;
  }

  public void rerank(List<Expression> list, String srcLang, String trgLang) {
    if (list.isEmpty()) return;
    String lang = list.get(0).language;
    String otherLang = (lang.equals(srcLang) ? trgLang : srcLang);
    String key = makeKey(lang, otherLang);
    logger.debug("language key: {}", key);
    if (!ranks.containsKey(key)) {
      logger.warn("unknown language key: {}", key);
      return;
    }
    Map<String, Integer> ranker = ranks.get(key);
    logger.debug("before rerank: {}", list);
    Collections.sort(list, (Expression e1, Expression e2) -> {
      String t1 = e1.text.toLowerCase();
      String t2 = e2.text.toLowerCase();
      int r1 = ranker.getOrDefault(t1, Integer.MAX_VALUE);
      int r2 = ranker.getOrDefault(t2, Integer.MAX_VALUE);
      return r1 - r2;
    });
    logger.debug("after rerank: {}", list);
  }

}
