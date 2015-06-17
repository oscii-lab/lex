package org.oscii.detokenize;

import com.google.gson.Gson;
import com.ibm.icu.lang.UCharacter;

import java.util.List;

/**
 * A label sequence parameterizes rendering a token sequence as a string.
 */
public class TokenLabel {
  boolean capitalize = false;
  String following = " ";
  String replace = "";

  public TokenLabel(boolean capitalize, String following, String replace) {
    this.capitalize = capitalize;
    this.following = following;
    this.replace = replace;
  }

  public String toString() {
    return new Gson().toJson(this, this.getClass());
  }

  public static TokenLabel interpret(String json) {
    TokenLabel label = new Gson().fromJson(json, TokenLabel.class);
    assert label.following != null;
    assert label.replace != null;
    return label;
  }

  public String renderToken(String token) {
    if (!replace.isEmpty()) {
      token = replace;
    }
    if (capitalize) {
      // TODO(denero) Casing should require a locale.
      token = UCharacter.toTitleCase(token, null);
    }
    return token;
  }

  /*
   * Render a token sequence as a string.
   */
  public static String render(List<String> tokens, List<TokenLabel> labels) {
    StringBuilder sb = new StringBuilder();
    assert (tokens.size() == labels.size());
    for (int i = 0; i < tokens.size(); i++) {
      TokenLabel label = labels.get(i);
      sb.append(label.renderToken(tokens.get(i)));
      sb.append(label.following);
    }
    return sb.toString();
  }
}
