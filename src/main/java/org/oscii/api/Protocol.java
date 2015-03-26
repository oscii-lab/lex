package org.oscii.api;

import org.oscii.Lexicon;
import org.oscii.lex.Translation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Transmission protocol for HTTP & Rabbit Handlers
 */
public class Protocol {
    final Lexicon lexicon;
    final double minFrequency;

    public Protocol(Lexicon lexicon, double minFrequency) {
        this.lexicon = lexicon;
        this.minFrequency = minFrequency;
    }

    /*
     * Generate a response to a request parsed from requestString.
     */
    public Response respond(Request request) {
        if (request.query == null || request.source == null || request.target == null) {
            return Response.error("Invalid request");
        }

        Response response = new Response();

        if (request.aspects.contains(Aspect.TRANSLATIONS)) {
            List<Translation> results;
            results = lexicon.translate(request.query, request.source, request.target);
            results.forEach(t -> {
                // TODO(denero) Add formatted source?
                String pos = t.pos.stream().findFirst().orElse("");
                if (t.frequency >= minFrequency) {
                    response.translations.add(new ResponseTranslation(
                            request.query, pos, t.translation.text, t.frequency));
                }
            });
        }

        if (request.aspects.contains(Aspect.DEFINITIONS)) {
            // TODO(denero) Add definitions
        }


        if (request.aspects.contains(Aspect.EXAMPLES)) {
            // TODO(denero) Add examples
        }

        if (request.aspects.contains(Aspect.EXTENSIONS)) {
            // TODO(denero) Add extensions
        }
        
        return response;
    }

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
    }

    private static class ResponseExample {
    }
}
