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
import java.util.stream.Collectors;

/**
 * Serve API
 */
public class Servlet extends HttpServlet {

    private final Protocol protocol;

    private final static Logger log = LogManager.getLogger(RabbitHandler.class);

    public Servlet(Protocol protocol) {
        this.protocol = protocol;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("text/json");
        Gson gson = new Gson();
        response.setStatus(HttpServletResponse.SC_OK);
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Access-Control-Allow-Methods", "GET, POST");
        Protocol.Request req = parse(request);
        log.info("Message received: " + req);
        Protocol.Response resp = protocol.respond(parse(request));
        log.info("Message response: " + resp);
        response.getWriter().println(gson.toJson(resp));
    }

    private static Protocol.Request parse(HttpServletRequest request) throws IOException {
        Gson gson = new Gson();
        // Try parsing params
        Map<String, String> params = request.getParameterMap().entrySet().stream()
                .filter(e -> e.getValue().length == 1)
                .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()[0]));
        if (params.size() != 0) {
            return gson.fromJson(gson.toJson(params), Protocol.Request.class);
        }
        // Then try parsing body as a JSON object
        return gson.fromJson(request.getReader(), Protocol.Request.class);
    }
}
