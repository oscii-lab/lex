package org.oscii.api;

import com.google.common.primitives.Doubles;
import com.google.gson.Gson;
import com.medallia.word2vec.Searcher;
import com.medallia.word2vec.Searcher.Match;
import com.medallia.word2vec.Searcher.UnknownWordException;
import com.medallia.word2vec.Word2VecModel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.oscii.concordance.AlignedCorpus;
import org.oscii.concordance.AlignedSentence;
import org.oscii.concordance.SentenceExample;
import org.oscii.lex.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * Transmission protocol for Lexicon API
 */
public class LexiconProtocol {
    private final Lexicon lexicon;
    private final AlignedCorpus corpus;
    private final Ranker ranker;
    private final Word2VecModel word2vec;

    private final static Logger logger = LogManager.getLogger(LexiconProtocol.class);

    public LexiconProtocol(Lexicon lexicon, AlignedCorpus corpus, Ranker ranker, Word2VecModel word2vec) {
        this.lexicon = lexicon;
        this.corpus = corpus;
        this.ranker = ranker;
        this.word2vec = word2vec;
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
        if (request.wordvec) addWordVector(request, response);
        if (request.distance) addDistance(request, response);
        if (request.similar) addMatches(request, response);
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
            if (t.frequency >= request.minFrequency || response.translations.isEmpty()) {
                response.translations.add(new ResponseTranslation(
                        request.query, pos, t.translation.text, t.frequency));
            }
        });
    }

    /*
     * Add distinct definitions.
     */
    private void addDefinitions(Request request, Response response) {
        List<Definition> results = lexicon.define(request.query, request.source);
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
        List<SentenceExample> results = corpus.examples(request.query, request.source, request.target, request.maxCount, request.memory);
        if (word2vec != null) {
            // retrieve and score context
            String[] context = request.context.split("\\s+");
            double[] contextMean = searcher.getMean(context);
            Searcher searcher = word2vec.forSearch();
            // iterate over concordance results
            results.forEach(ex -> {
                    String[] tokens = null;
                    // hard-coded to "en" for now, TODO(sasa): support several word2vec models
                    if (request.source.equals("en")) {
                        tokens = ex.sentence.tokens;
                    } else {
                        tokens = ex.sentence.aligned.tokens;
                    }
                    double[] tokensMean = searcher.getMean(tokens);
                    logger.debug("tokens={} context={} ({})", tokens, context, context.length);
                    logger.debug("means: tokens=[{},{},{},...] context=[{},{},{},...]",
                                 tokensMean[0], tokensMean[1], tokensMean[2],
                                 contextMean[0], contextMean[1], contextMean[2]);
                    double dist = searcher.cosineDistance(tokensMean, contextMean);
                    ex.similarity = (Double.isNaN(dist) ? -1.0 : dist);
                    logger.debug("distance: {}", ex.similarity);
            });
            // rerank according to word2vec similarities
            Collections.sort(results, Order.bySimilarity);
        }
        results.forEach(ex -> {
            AlignedSentence source = ex.sentence;
            AlignedSentence target = source.aligned;
            Span sourceSpan = new Span(ex.sourceStart, ex.sourceLength);
            Span targetSpan = new Span(ex.targetStart, ex.targetLength);
            ResponseExample example = new ResponseExample(source.tokens, source.delimiters, target.tokens, target.delimiters, source.getAlignment(), sourceSpan, targetSpan, ex.similarity);
            response.examples.add(example);
        });
    }

    private void addExtensions(Request request, Response response) {
        List<Expression> results =
                lexicon.extend(request.query, request.source, request.target, 20 * request.maxCount + 20);
        // rank results through ranker
        if (ranker != null) {
            ranker.rerank(results, request.source, request.target);
        }

        results.forEach(ex -> {
            if (response.extensions.size() >= request.maxCount) return;
            List<Translation> translations =
                    lexicon.translate(ex.text, request.source, request.target);
            if (translations.isEmpty()) return;
            Translation first = translations.get(0);
            logger.debug("ex.text={} first={}", ex.text, first);
            if (first.frequency < request.minFrequency) return;
            response.extensions.add(ResponseTranslation.create(ex, first));
        });
        logger.debug("extensions 1st: {}", response.extensions);

        if (response.extensions.isEmpty()) {
            results.forEach(ex -> {
                if (response.extensions.size() >= request.maxCount) return;
                List<Translation> translations =
                        lexicon.translate(ex.text, request.source, request.target);
                if (translations.isEmpty()) return;
                response.extensions.add(ResponseTranslation.create(ex, translations.get(0)));
            });
            logger.debug("extensions 2nd: {}", response.extensions);
        }
    }

    private void addSynonyms(Request request, Response response) {
        List<Meaning> results = lexicon.lookup(request.query, request.source);
        results.stream().forEach(r -> {
            if (r.synonyms.isEmpty()) return;
            if (r.pos.isEmpty()) {
                response.synonyms.add(new ResponseSynonymSet("", listSynonyms(r)));
            } else {
                r.pos.stream().distinct().forEach(pos ->
                    response.synonyms.add(new ResponseSynonymSet(pos, listSynonyms(r))));
            }
        });
        response.synonyms = response.synonyms.stream().distinct().collect(toList());
    }

    /**
     * Adds the raw word vector for a query to the response.
     *
     * Example:
     * http://localhost:8090/translate/lexicon?query=explain&wordvec=true
     * =>
     * {...,"wordVector":[-0.07444860785060135,8.24243638592437E-4,0.03639942629448272,
     *     0.08149381270654195,-0.15073516043655721,...],...}
     */
    private void addWordVector(Request request, Response response) {
        if (word2vec == null) {
            response.error = "no word2vec model available";
            return;
        }
        String query = request.query;
        Searcher searcher = word2vec.forSearch();
        if (!searcher.contains(query)) {
            query = Lexicon.degrade(query);
        }
        try {
            response.wordVector = searcher.getRawVector(query).asList();
        } catch (UnknownWordException e) {
            response.error = e.getMessage();
        }
    }

    /**
     * Adds cosine distance of two terms in the given request to
     * response.  The field 'query' either takes both terms separated
     * by '|||', or fields 'query' and 'context' are used.
     *
     * Examples:
     * http://localhost:8090/translate/lexicon?query=explain|||tell&distance=true
     * http://localhost:8090/translate/lexicon?query=explain&context=tell&distance=true
     * =>
     * {...,"distance":0.347985021280413}
     */
    private void addDistance(Request request, Response response) {
        if (word2vec == null) {
            response.error = "no word2vec model available";
            return;
        }
        Searcher searcher = word2vec.forSearch();
        try {
            String query1 = request.query;
            String query2 = request.context;
            if (query2.length() == 0) {
                String[] splitQuery = request.query.split("\\|\\|\\|");
                if (splitQuery.length >= 2) {
                    query1 = splitQuery[0];
                    query2 = splitQuery[1];
                } else {
                    response.error = "malformed query";
                    return;
                }
            }
            if (!searcher.contains(query1)) {
                query1 = Lexicon.degrade(query1);
            }
            if (!searcher.contains(query2)) {
                query2 = Lexicon.degrade(query2);
            }
            response.distance = searcher.cosineDistance(query1, query2);
        } catch (UnknownWordException e) {
            response.error = e.getMessage();
        }
    }

    /**
     * Adds similar terms for given request to response.
     *
     * Example:
     * http://localhost:8090/translate/lexicon?query=explain&similar=true
     * =>
     * {...,"matches":[
     *    {"match":"explain","distance":1.0000000000000002},
     *    {"match":"predict","distance":0.6387358641143756},
     *    {"match":"understand","distance":0.6370402633877622},
     *    {"match":"relate","distance":0.602837642683336},
     *    {"match":"discern","distance":0.5984616315627042},
     * ...}
     */
    private void addMatches(Request request, Response response) {
        if (word2vec == null) {
            response.error = "no word2vec model available";
            return;
        }
        String query = request.query;
        Searcher searcher = word2vec.forSearch();
        if (!searcher.contains(query)) {
            query = Lexicon.degrade(query);
        }
        try {
            List<Match> matches = searcher.getMatches(query, request.maxCount);
            matches.stream().forEach(
                m -> response.matches.add(new ResponseMatch(m.match(), m.distance()))
            );
        } catch (UnknownWordException e) {
            response.error = e.getMessage();
        }
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
        public String query = "";
        public String source = "";
        public String target = "";
        public String context = "";
        public boolean translate = false;
        public boolean define = false;
        public boolean example = false;
        public boolean extend = false;
        public boolean synonym = false;
        public boolean wordvec = false;
        public boolean similar = false;
        public boolean distance = false;
        public double minFrequency = 1e-4;
        public int maxCount = 10;
        public int memory = 0;
    }

    public static class Response extends Jsonable {
        public List<ResponseTranslation> translations = new ArrayList<>();
        public List<ResponseDefinition> definitions = new ArrayList<>();
        public List<ResponseExample> examples = new ArrayList();
        public List<ResponseTranslation> extensions = new ArrayList<>();
        public List<ResponseSynonymSet> synonyms = new ArrayList<>();
        public List<ResponseMatch> matches = new ArrayList<>();
        public List<Double> wordVector = new ArrayList<>();
        public double distance = 0.0;
        public String error;

        public static Response error(String message) {
            Response response = new Response();
            response.error = message;
            return response;
        }
    }

    public static class ResponseTranslation extends Jsonable {
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

        public static ResponseTranslation create(Expression ex, Translation first) {
            String pos = first.pos.stream().findFirst().orElse("");
            return new ResponseTranslation(ex.text, pos, first.translation.text, first.frequency);
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

    /**
     * A similar match based on Word2Vec.
     */
    static class ResponseMatch extends Jsonable {
        String match;
        double distance;

        public ResponseMatch(String match, double distance) {
            this.match = match;
            this.distance = distance;
        }
    }

    /*
     * A span of a sequence
     */
    private static class Span {
        int start;
        int length;

        public Span(int start, int length) {
            assert length >= 0;
            this.start = start;
            this.length = length;
        }

        public String[] Slice(String[] sequence) {
            String[] slice = new String[length];
            for (int i = start; i < start + length; i++) {
                slice[i - start] = sequence[i];
            }
            return slice;
        }
    }

    private static class ResponseExample {
        String[] source;
        String[] sourceDelimiters;
        String[] target;
        String[] targetDelimiters;
        int[][] sourceToTarget;
        Span sourceSpan;
        Span targetSpan;
        double similarity;

        public ResponseExample(String[] source, String[] sourceDelimiters, String[] target, String[] targetDelimiters, int[][] sourceToTarget, Span sourceSpan, Span targetSpan, double similarity) {
            this.source = source;
            this.sourceDelimiters = sourceDelimiters;
            this.target = target;
            this.targetDelimiters = targetDelimiters;
            this.sourceToTarget = sourceToTarget;
            this.sourceSpan = sourceSpan;
            this.targetSpan = targetSpan;
            this.similarity = similarity;
        }
    }
}
