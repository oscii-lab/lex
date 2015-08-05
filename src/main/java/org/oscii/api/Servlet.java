package org.oscii.api;

import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

import static java.util.stream.Collectors.toMap;

/**
 * Serve API
 */
public class Servlet extends HttpServlet {

    private final LexiconProtocol protocol;

    private final static Logger logger = LogManager.getLogger(Servlet.class);

    public Servlet(LexiconProtocol protocol) {
        this.protocol = protocol;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setCharacterEncoding("UTF8");
        LexiconProtocol.Request req = parse(request);
        logger.info("Message received: " + req);

        LexiconProtocol.Response resp = protocol.respond(parse(request));
        logger.info("Message response: " + resp);

        response.setContentType("text/json");
        response.setStatus(HttpServletResponse.SC_OK);
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Access-Control-Allow-Methods", "GET, POST");
        response.setCharacterEncoding("UTF8");
        Gson gson = new Gson();
        response.getWriter().println(gson.toJson(resp));
    }

    private static LexiconProtocol.Request parse(HttpServletRequest request) throws IOException {
        Gson gson = new Gson();
        // Try parsing params
        Map<String, String> params = request.getParameterMap().entrySet().stream()
                .filter(e -> e.getValue().length == 1)
                .collect(toMap(Map.Entry::getKey, e -> e.getValue()[0]));
        if (params.size() != 0) {
            return gson.fromJson(gson.toJson(params), LexiconProtocol.Request.class);
        }
        // Then try parsing body as a JSON object
        return gson.fromJson(request.getReader(), LexiconProtocol.Request.class);
    }
}
