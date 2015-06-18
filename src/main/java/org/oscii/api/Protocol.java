package org.oscii.api;

import com.google.gson.Gson;
import org.oscii.concordance.AlignedCorpus;
import org.oscii.concordance.AlignedSentence;
import org.oscii.lex.*;

import java.util.ArrayList;
import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * Transmission protocol for Lexicon API
 */
public class Protocol {
  final Lexicon lexicon;
  final AlignedCorpus corpus;

  public Protocol(Lexicon lexicon, AlignedCorpus corpus) {
    this.lexicon = lexicon;
    this.corpus = corpus;
  }

  /*
       * Generate a response to a request parsed from requestString.
       */
  public Response respond(Request request) {
    if (request.query == null || request.source == null || request.target == null) {
      return Response.error("Invalid request");
    }
    Response response = new Response();
    if (request.translate) addTranslations(request, response);
    if (request.define) addDefinitions(request, response);
    if (request.example) addExamples(request, response);
    if (request.extend) addExtensions(request, response);
    if (request.synonym) addSynonyms(request, response);
    return response;
  }


    /* Aspect processing */

  /*
   * Add translations filtered by frequency.
   */
  private void addTranslations(Request request, Response response) {
    List<Translation> results =
            lexicon.translate(request.query, request.source, request.target);
    results.stream().limit(request.maxCount).forEach(t -> {
      // TODO(denero) Add formatted source?
      String pos = t.pos.stream().findFirst().orElse("");
      if (t.frequency >= request.minFrequency) {
        response.translations.add(new ResponseTranslation(
                request.query, pos, t.translation.text, t.frequency));
      }
    });
  }

  /*
   * Add distinct definitions.
   */
  private void addDefinitions(Request request, Response response) {
    List<Definition> results =
            lexicon.define(request.query, request.source);
    results.stream()
            .limit(request.maxCount)
            .map(d -> {
              String pos = d.pos.stream().findFirst().orElse("");
              return new ResponseDefinition(request.query, pos, d.text);
            })
            .distinct()
            .forEach(response.definitions::add);
  }

  private void addExamples(Request request, Response response) {
    List<AlignedSentence> results =
            corpus.examples(request.query, request.source, request.target, request.maxCount);
    results.forEach(r -> {
      // TODO(denero) Encode and set span correctly
      Span span = new Span(0, 1);
      ResponseExample example = new ResponseExample(r.tokens, r.aligned.tokens, r.alignment, span);
      response.examples.add(example);
    });

  }

  private void addExtensions(Request request, Response response) {
    List<Expression> results =
            lexicon.extend(request.query, request.source, request.target, request.maxCount);
    results.forEach(ex -> {
      List<Translation> translations =
              lexicon.translate(ex.text, request.source, request.target);
      if (translations.isEmpty()) return;
      Translation first = translations.get(0);
      if (first.frequency < request.minFrequency) return;
      String pos = first.pos.stream().findFirst().orElse("");
      ResponseTranslation entry = new ResponseTranslation(ex.text, pos, first.translation.text, first.frequency);
      response.extensions.add(entry);
    });
  }

  private void addSynonyms(Request request, Response response) {
    List<Meaning> results = lexicon.lookup(request.query, request.source);
    results.stream().forEach(r -> {
      if (r.pos.isEmpty()) {
        response.synonyms.add(new ResponseSynonymSet("", listSynonyms(r)));
      } else {
        r.pos.stream().distinct().forEach(pos -> {
          response.synonyms.add(new ResponseSynonymSet("", listSynonyms(r)));
        });
      }
    });
    response.synonyms = response.synonyms.stream().distinct().collect(toList());
  }

  private List<String> listSynonyms(Meaning r) {
    return r.synonyms.stream().map(e -> e.text).collect(toList());
  }


    /* API classes to define JSON serialization */

  private static abstract class Jsonable {
    @Override
    public String toString() {
      return new Gson().toJson(this, this.getClass());
    }
  }

  public static class Request extends Jsonable {
    String query = "";
    String source = "";
    String target = "";
    String context = "";
    boolean translate = false;
    boolean define = false;
    boolean example = false;
    boolean extend = false;
    boolean synonym = false;
    double minFrequency = 1e-4;
    int maxCount = 10;
  }

  public static class Response extends Jsonable {
    List<ResponseTranslation> translations = new ArrayList<>();
    List<ResponseDefinition> definitions = new ArrayList<>();
    List<ResponseExample> examples = new ArrayList();
    List<ResponseTranslation> extensions = new ArrayList<>();
    List<ResponseSynonymSet> synonyms = new ArrayList<>();
    String error;

    public static Response error(String message) {
      Response response = new Response();
      response.error = message;
      return response;
    }
  }

  static class ResponseTranslation extends Jsonable {
    String source;
    String pos;
    String target;
    double frequency;

    ResponseTranslation(String source, String pos, String target, double frequency) {
      this.source = source;
      this.pos = pos;
      this.target = target;
      this.frequency = frequency;
    }
  }

  private static class ResponseDefinition extends Jsonable {
    String source;
    String pos;
    String text;

    public ResponseDefinition(String source, String pos, String text) {
      this.source = source;
      this.pos = pos;
      this.text = text;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ResponseDefinition that = (ResponseDefinition) o;

      if (pos != null ? !pos.equals(that.pos) : that.pos != null)
        return false;
      if (source != null ? !source.equals(that.source) : that.source != null)
        return false;
      if (text != null ? !text.equals(that.text) : that.text != null)
        return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = source != null ? source.hashCode() : 0;
      result = 31 * result + (pos != null ? pos.hashCode() : 0);
      result = 31 * result + (text != null ? text.hashCode() : 0);
      return result;
    }
  }

  static class ResponseSynonymSet extends Jsonable {
    String pos;
    List<String> synonyms;

    public ResponseSynonymSet(String pos, List<String> synonyms) {
      this.pos = pos;
      this.synonyms = synonyms;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ResponseSynonymSet that = (ResponseSynonymSet) o;

      if (!pos.equals(that.pos)) return false;
      return synonyms.equals(that.synonyms);

    }

    @Override
    public int hashCode() {
      int result = pos.hashCode();
      result = 31 * result + synonyms.hashCode();
      return result;
    }
  }

  /*
   * A span of a sequence; start is inclusive and end is exclusive.
   */
  private static class Span {
    int start;
    int end;

    public Span(int start, int end) {
      assert end > start;
      this.start = start;
      this.end = end;
    }

    public String[] Slice(String[] sequence) {
      String[] slice = new String[end - start];
      for (int i = start; i < end; i++) {
        slice[i - start] = sequence[i];
      }
      return slice;
    }
  }

  private static class ResponseExample {
    String[] source;
    String[] target;
    int[][] sourceToTarget;
    Span sourceSpan;

    public ResponseExample(String[] source, String[] target, int[][] sourceToTarget, Span sourceSpan) {
      this.source = source;
      this.target = target;
      this.sourceToTarget = sourceToTarget;
      this.sourceSpan = sourceSpan;
    }
  }
}
