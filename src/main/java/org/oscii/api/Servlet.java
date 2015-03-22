package org.oscii.api;

import com.google.gson.Gson;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

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
        return new Protocol.Request(
                request.getParameter("query"),
                request.getParameter("source"),
                request.getParameter("target"));
    }
}
