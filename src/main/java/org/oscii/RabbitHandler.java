package org.oscii;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.QueueingConsumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.oscii.lex.Translation;

import java.util.ArrayList;
import java.util.List;

public class RabbitHandler {
    private final String host;
    private final String queueName;
    private final String username;
    private final String password;
    private final Lexicon lexicon;

    private final static Logger log = LogManager.getLogger(RabbitHandler.class);

    public RabbitHandler(String host, String queueName, String username, String password, Lexicon lexicon) {
        this.host = host;
        this.queueName = queueName;
        this.username = username;
        this.password = password;
        this.lexicon = lexicon;
    }

    public void ConnectAndListen()
            throws java.io.IOException,
            java.lang.InterruptedException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(host);
        factory.setUsername(username);
        factory.setPassword(password);
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        channel.queueDeclare(queueName, false, false, false, null);

        channel.basicQos(1);

        QueueingConsumer consumer = new QueueingConsumer(channel);
        boolean autoAck = false;
        channel.basicConsume(queueName, autoAck, consumer);

        log.info("Awaiting requests on " + queueName);

        while (true) {
            QueueingConsumer.Delivery delivery = consumer.nextDelivery();

            BasicProperties props = delivery.getProperties();
            BasicProperties replyProps = new BasicProperties.Builder()
                    .correlationId(props.getCorrelationId())
                    .build();

            String message = new String(delivery.getBody(), "UTF-8");
            log.info("Message received: " + message);

            String response = respond(message);
            log.info("Message response: " + response);

            channel.basicPublish("", props.getReplyTo(), replyProps, response.getBytes("UTF-8"));
            channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
        }
    }

    // Parse message, perform a lookup, and construct a response.
    private String respond(String message) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Request request = gson.fromJson(message, Request.class);
        Response response = respond(message, request);
        return gson.toJson(response);
    }

    /*
     * Generate a response to a request parsed from requestString.
     */
    Response respond(String requestString, Request request) {
        if (request.query == null || request.source == null || request.target == null) {
            log.error("Invalid request: " + requestString);
            return new Response();
        }

        // TODO(denero) Add definitions and check for keys
        List<Translation> results;
        results = lexicon.translate(request.query, request.source, request.target);

        Response response = new Response();
        results.forEach(t -> {
                // TODO(denero) Add formatted source?
                String pos = t.pos.stream().findFirst().orElse("");
                response.translations.add(new ResponseTranslation(
                        request.query, pos, t.translation.text, t.frequency));
        });
        return response;
    }

    static class Request {
        String query;
        String source;
        String target;
        String[] keys;
        String context;
    }

    static class Response {
        List<ResponseTranslation> translations = new ArrayList<>();
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
    }
}
