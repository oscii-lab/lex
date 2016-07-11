package org.oscii.corpus;

/**
 * Tokenizer
 */
public interface Tokenizer {
  String[] tokenize(String line);

  public Tokenizer alphanumeric = new Tokenizer() {
    @Override
    public String[] tokenize(String line) {
      return line.split("[^\\w']+");
    }
  };

  public Tokenizer alphanumericLower = new Tokenizer() {
    @Override
    public String[] tokenize(String line) {
      return alphanumeric.tokenize(line.toLowerCase());
    }
  };


}
