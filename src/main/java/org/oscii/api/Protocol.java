package org.oscii.api;

import org.oscii.Lexicon;
import org.oscii.lex.Translation;

import java.util.ArrayList;
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
            return new Response();
        }

        // TODO(denero) Add definitions and check for keys
        List<Translation> results;
        results = lexicon.translate(request.query, request.source, request.target);

        Response response = new Response();
        results.forEach(t -> {
            // TODO(denero) Add formatted source?
            String pos = t.pos.stream().findFirst().orElse("");
            if (t.frequency >= minFrequency) {
                response.translations.add(new ResponseTranslation(
                        request.query, pos, t.translation.text, t.frequency));
            }
        });
        return response;
    }

    public static class Request {
        String query;
        String source;
        String target;
        String[] keys;
        String context;

        public Request(String query, String source, String target) {
            this.query = query;
            this.source = source;
            this.target = target;
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

        @Override
        public String toString() {
            return "Response{" +
                    "translations=" + translations +
                    '}';
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
}
