package org.oscii.api;

import com.google.gson.Gson;
import no.uib.cipr.matrix.Vector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.oscii.concordance.AlignedCorpus;
import org.oscii.concordance.AlignedSentence;
import org.oscii.concordance.SentenceExample;
import org.oscii.lex.Definition;
import org.oscii.lex.Expression;
import org.oscii.lex.Lexicon;
import org.oscii.lex.Meaning;
import org.oscii.lex.Ranker;
import org.oscii.lex.Translation;
import org.oscii.morph.MorphologyManager;
import org.oscii.neural.FloatVector;
import org.oscii.neural.Word2VecManager;
import org.oscii.neural.Word2VecManager.MalformedQueryException;
import org.oscii.neural.Word2VecManager.UnsupportedLanguageException;

import java.util.ArrayList;
import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * Transmission protocol for Lexicon API
 */
public class LexiconProtocol {
    private final static Logger logger = LogManager.getLogger(LexiconProtocol.class);

    private final Lexicon lexicon;
    private final AlignedCorpus corpus;
    private final Ranker ranker;
    private final Word2VecManager embeddings;
    private final MorphologyManager morphology;

    public LexiconProtocol(Lexicon lexicon, AlignedCorpus corpus, Ranker ranker, Word2VecManager embeddings, MorphologyManager morphology) {
        this.lexicon = lexicon;
        this.corpus = corpus;
        this.ranker = ranker;
        this.embeddings = embeddings;
        this.morphology = morphology;
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
        if (request.embedding) addEmbedding(request, response);
        if (request.distance) addDistance(request, response);
        return response;
    }


    /* Aspect processing */

    /*
     * Add translations filtered by frequency.
     */
    private void addTranslations(Request request, Response response) {
        String sourceTerm = request.query;
        List<Translation> results = lexicon.translate(sourceTerm, request.source, request.target);

        if (results.isEmpty() && morphology != null) {
            String stem = morphology.getKnownStem(request.query, request.source);
            if (!stem.equals(sourceTerm)) {
                sourceTerm = stem;
                results = lexicon.translate(stem, request.source, request.target);
            }
        }

        final String s = sourceTerm;
        results.stream().limit(request.maxCount).forEach(t -> {
            String pos = t.pos.stream().findFirst().orElse("");
            if (t.frequency >= request.minFrequency || response.translations.isEmpty()) {
                response.translations.add(new ResponseTranslation(s, pos, t.translation.text, t.frequency, -1));
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
        long startTime = System.nanoTime(); // - startTime) / 1e9;
        boolean bHasEmbeddings = (embeddings != null && embeddings.hasModels());
        List<SentenceExample> results = corpus.examples(request.query, request.source, request.target, request.systemId, request.maxCount, request.memory, !bHasEmbeddings);
        long endTime = System.nanoTime();
        logger.debug("TIMING examples: {}", (endTime - startTime) / 1e9);
        if (bHasEmbeddings) {
            startTime = endTime;
            boolean bSuccess = embeddings.rankConcordances(request.source, request.context, results, request.memory);
            endTime = System.nanoTime();
            logger.debug("TIMING embeddings: {} ({})", (endTime - startTime) / 1e9, results.size());
            if (!bSuccess) {
                logger.warn("word2vec found no matches");
            }
        }
        results.forEach(ex -> {
            AlignedSentence source = ex.sentence;
            AlignedSentence target = source.aligned;
            if (request.translate && ex.memoryId >= 0 && exactQueryMatch(ex)) {
                String sourceTerm = joinSegment(source.tokens, source.delimiters);
                String targetTerm = joinSegment(target.tokens, target.delimiters);
                response.translations.add(
                        new ResponseTranslation(sourceTerm, "", targetTerm, 0.0, ex.memoryId));
            } else {
                Span sourceSpan = new Span(ex.sourceStart, ex.sourceLength);
                Span targetSpan = new Span(ex.targetStart, ex.targetLength);
                ResponseExample example = new ResponseExample(
                        source.tokens,
                        source.delimiters,
                        target.tokens,
                        target.delimiters,
                        source.getAlignment(),
                        sourceSpan,
                        targetSpan,
                        ex.similarity,
                        ex.memoryId);
                response.examples.add(example);
            }
        });
    }

    // Interleave tokens and delimiters into a rendered string.
    private String joinSegment(String[] tokens, String[] delimiters) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.length; i++) {
            if (i < delimiters.length) {
                sb.append(delimiters[i]);
            } else {
                sb.append(" ");
            }
            sb.append(tokens[i]);
        }
        if (delimiters.length > tokens.length) {
            sb.append(delimiters[tokens.length]);
        }
        return sb.toString();
    }

    // The query of the request is the complete example.
    private boolean exactQueryMatch(SentenceExample ex) {
        // Check that the number of aligned words in the source covers the entire source.
        return ex.sourceLength == ex.sentence.tokens.length;
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
     * <p>
     * Example:
     * http://localhost:8090/translate/lexicon?query=explain&embedding=true
     * =>
     * {...,"embedding":[-0.07444860785060135,8.24243638592437E-4,0.03639942629448272,
     * 0.08149381270654195,-0.15073516043655721,...],...}
     */
    private void addEmbedding(Request request, Response response) {
        try {
            Vector v = embeddings.getRawVector(request.source, request.query);
            if (v instanceof FloatVector) {
                response.embedding = ((FloatVector) v).getData();
            } else {
                float[] f = new float[v.size()];
                for (int i = 0; i < v.size(); i++) {
                    f[i] = (float) v.get(i);
                }
                response.embedding = f;
            }
        } catch (UnsupportedLanguageException e) {
            response.error = e.getMessage();
        }
    }

    /**
     * Adds cosine distance of two terms in the given request to
     * response.  The field 'query' either takes both terms separated
     * by '|||', or fields 'query' and 'context' are used.
     * <p>
     * Examples:
     * http://localhost:8090/translate/lexicon?query=explain|||tell&distance=true
     * http://localhost:8090/translate/lexicon?query=explain&context=tell&distance=true
     * =>
     * {...,"distance":0.347985021280413}
     */
    private void addDistance(Request request, Response response) {
        try {
            response.distance = embeddings.getSimilarity(request.source, request.query, request.context);
        } catch (UnsupportedLanguageException | MalformedQueryException e) {
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
        public String systemId = "";
        public String context = "";
        public boolean translate = false;
        public boolean define = false;
        public boolean example = false;
        public boolean extend = false;
        public boolean synonym = false;
        public boolean embedding = false;
        public boolean distance = false;
        public double minFrequency = 1e-4;
        public int maxCount = 10;
        public int memory = 0;
    }

    public static class Response extends Jsonable {
        public List<ResponseTranslation> translations = new ArrayList<>();
        public List<ResponseDefinition> definitions = new ArrayList<>();
        public List<ResponseExample> examples = new ArrayList<>();
        public List<ResponseTranslation> extensions = new ArrayList<>();
        public List<ResponseSynonymSet> synonyms = new ArrayList<>();
        public float[] embedding;
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
        int memoryId; // -1: background TM, >=0: foregroundTM

        ResponseTranslation(String source, String pos, String target, double frequency, int memoryId) {
            this.source = source;
            this.pos = pos;
            this.target = target;
            this.frequency = frequency;
            this.memoryId = memoryId;
        }

        public static ResponseTranslation create(Expression ex, Translation first) {
            String pos = first.pos.stream().findFirst().orElse("");
            return new ResponseTranslation(ex.text, pos, first.translation.text, first.frequency, -1);
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
        int memoryId; // -1: background TM, >=0: foregroundTM

        public ResponseExample(String[] source, String[] sourceDelimiters, String[] target, String[] targetDelimiters, int[][] sourceToTarget, Span sourceSpan, Span targetSpan, double similarity, int memoryId) {
            this.source = source;
            this.sourceDelimiters = sourceDelimiters;
            this.target = target;
            this.targetDelimiters = targetDelimiters;
            this.sourceToTarget = sourceToTarget;
            this.sourceSpan = sourceSpan;
            this.targetSpan = targetSpan;
            this.similarity = similarity;
            this.memoryId = memoryId;
        }
    }
}
