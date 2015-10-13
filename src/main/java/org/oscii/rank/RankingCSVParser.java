package org.oscii.rank;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

public class RankingCSVParser {
  private final String path;
  private final Map<String, Integer> ranks;

  public RankingCSVParser(String path) {
    this.path = path;
    ranks = new HashMap<>();
  }

  public void readAndIndex() throws IOException {
    CSVParser csv = CSVParser.parse(new File(path), Charset.forName("utf-8"), CSVFormat.EXCEL);
    csv.iterator().forEachRemaining(row -> {
      String src = row.get(0);
      String trg = row.get(1);
      int rank = Integer.parseInt(row.get(2));
      String term = row.get(3);
      String key = genKey(src, trg, term);
      ranks.put(key, rank);
    });
  }



  private String genKey(String src, String trg, String term) {
    return String.format("%s-%s-%s", src, trg, term);
  }
}
