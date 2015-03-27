package org.oscii.api;

import org.oscii.lex.Definition;
import org.oscii.lex.Expression;
import org.oscii.lex.Lexicon;
import org.oscii.lex.Translation;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * Transmission protocol for HTTP & Rabbit Handlers
 */
public class Protocol {
    private static final int MAX_EXTENSIONS = 100;
    final Lexicon lexicon;
    final double minFrequency;
    final Map<Aspect, BiConsumer<Request, Response>> aspects = new HashMap<>();

    public Protocol(Lexicon lexicon, double minFrequency) {
        this.lexicon = lexicon;
        this.minFrequency = minFrequency;
        aspects.put(Aspect.TRANSLATIONS, this::addTranslations);
        aspects.put(Aspect.DEFINITIONS, this::addDefinitions);
        aspects.put(Aspect.EXAMPLES, this::addExamples);
        aspects.put(Aspect.EXTENSIONS, this::addExtensions);
    }

    /*
     * Generate a response to a request parsed from requestString.
     */
    public Response respond(Request request) {
        if (request.query == null || request.source == null || request.target == null) {
            return Response.error("Invalid request");
        }
        Response response = new Response();
        for (Aspect aspect : request.aspects) {
            BiConsumer<Request, Response> fn = aspects.get(aspect);
            if (fn != null) {
                fn.accept(request, response);
            }
        }
        return response;
    }

    /* Aspect processing */

    /*
     * Add translations filtered by frequency.
     */
    private void addTranslations(Request request, Response response) {
        List<Translation> results =
                lexicon.translate(request.query, request.source, request.target);
        results.forEach(t -> {
            // TODO(denero) Add formatted source?
            String pos = t.pos.stream().findFirst().orElse("");
            if (t.frequency >= minFrequency) {
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
                .map(d -> {
                    String pos = d.pos.stream().findFirst().orElse("");
                    return new ResponseDefinition(request.query, pos, d.text);
                })
                .distinct()
                .forEach(rd -> response.definitions.add(rd));
    }

    private void addExamples(Request request, Response response) {

    }

    private void addExtensions(Request request, Response response) {
        List<Expression> results =
                lexicon.extend(request.query, request.source, MAX_EXTENSIONS);
        results.forEach(ex -> response.extensions.add(ex.text));
    }

    /* Support classes (serializable) */

    public static enum Aspect {
        TRANSLATIONS, DEFINITIONS, EXAMPLES, EXTENSIONS;
    }

    public static class Request {
        String query;
        String source;
        String target;
        List<Aspect> aspects;
        String context;

        public Request(String query, String source, String target) {
            this(query, source, target, Arrays.asList(new Aspect[]{Aspect.TRANSLATIONS}));
        }

        public Request(String query, String source, String target, List<Aspect> aspects) {
            this.query = query;
            this.source = source;
            this.target = target;
            this.aspects = aspects;
        }

        @Override
        public String toString() {
            return "Request{" +
                    "target='" + target + '\'' +
                    ", query='" + query + '\'' +
                    ", source='" + source + '\'' +
                    '}';
        }
    }

    static class Response {
        List<ResponseTranslation> translations = new ArrayList<>();
        List<ResponseDefinition> definitions = new ArrayList<>();
        List<ResponseExample> examples = new ArrayList();
        List<String> extensions = new ArrayList<>();
        String error;

        @Override
        public String toString() {
            return "Response{" +
                    "translations=" + translations +
                    ", definitions=" + definitions +
                    ", examples=" + examples +
                    ", extensions=" + extensions +
                    ", error='" + error + '\'' +
                    '}';
        }

        public static Response error(String message) {
            Response response = new Response();
            response.error = message;
            return response;
        }
    }

    static class ResponseTranslation {
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

        @Override
        public String toString() {
            return "ResponseTranslation{" +
                    "source='" + source + '\'' +
                    ", pos='" + pos + '\'' +
                    ", target='" + target + '\'' +
                    ", frequency=" + frequency +
                    '}';
        }
    }

    private static class ResponseDefinition {
        String source;
        String pos;
        String text;

        public ResponseDefinition(String source, String pos, String text) {
            this.source = source;
            this.pos = pos;
            this.text = text;
        }

        @Override
        public String toString() {
            return "ResponseDefinition{" +
                    "source='" + source + '\'' +
                    ", pos='" + pos + '\'' +
                    ", text='" + text + '\'' +
                    '}';
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

    private static class ResponseExample {
        String[] source;
        String[] target;
        int sourceIndex;
        int targetIndex;

        public ResponseExample(String[] source, String[] target, int sourceIndex, int targetIndex) {
            this.source = source;
            this.target = target;
            this.sourceIndex = sourceIndex;
            this.targetIndex = targetIndex;
        }

        @Override
        public String toString() {
            return "ResponseExample{" +
                    "source=" + Arrays.toString(source) +
                    ", target=" + Arrays.toString(target) +
                    ", sourceIndex=" + sourceIndex +
                    ", targetIndex=" + targetIndex +
                    '}';
        }
    }
}
