package org.oscii.api;

import com.google.gson.Gson;
import org.oscii.Lexicon;
import org.oscii.lex.Translation;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Serve API
 */
public class Servlet extends HttpServlet {

    private final Protocol protocol;

    public Servlet(Protocol protocol) {
        this.protocol = protocol;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("text/json");
        Gson gson = new Gson();
        response.setStatus(HttpServletResponse.SC_OK);
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, PUT");
        Protocol.Response resp = protocol.respond(parse(request));
        response.getWriter().println(gson.toJson(resp));
    }

    private static Protocol.Request parse(HttpServletRequest request) {
        Protocol.Request r = new Protocol.Request();
        r.query = request.getParameter("query");
        r.source = request.getParameter("source");
        r.target = request.getParameter("target");
        System.out.println(r);
        return r;
    }
}
