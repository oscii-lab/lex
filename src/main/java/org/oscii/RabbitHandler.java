package org.oscii;

import com.google.gson.Gson;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.QueueingConsumer;
import org.oscii.panlex.PanLexDB;
import org.oscii.panlex.PanLexRecord;

import java.util.ArrayList;
import java.util.List;

public class RabbitHandler {
    private final String host;
    private final PanLexDB panLex;
    private final String queueName;

    public RabbitHandler(String host, String queueName, PanLexDB panLex) {
        this.host = host;
        this.queueName = queueName;
        this.panLex = panLex;
    }

    public void ConnectAndListen()
            throws java.io.IOException,
            java.lang.InterruptedException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(host);
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();
        channel.basicQos(1);

        channel.queueDeclare(queueName, false, false, false, null);
        QueueingConsumer consumer = new QueueingConsumer(channel);
        channel.basicConsume(queueName, true, consumer);
        System.out.println(" [x] Awaiting requests on " + queueName);
        while (true) {
            QueueingConsumer.Delivery delivery = consumer.nextDelivery();

            BasicProperties props = delivery.getProperties();
            BasicProperties replyProps = new BasicProperties.Builder()
                    .correlationId(props.getCorrelationId())
                    .build();

            String message = new String(delivery.getBody(),"UTF-8");
            System.out.println(" [.] message received: " + message);
            
            String response = respond(message);
            System.out.println(" [.] message response: " + response);
            channel.basicPublish("", props.getReplyTo(), replyProps, response.getBytes("UTF-8"));
            channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
        }
    }

    static final String EMPTY = "";

    // Parse message, perform a lookup, and construct a response.
    // TODO(denero) Error handling
    private String respond(String message) {
        Gson gson = new Gson();
        Request request = gson.fromJson(message, Request.class);
        PanLexRecord record = panLex.lookup(request.query, request.source, request.target);
        if (record == null) {
            return EMPTY;
        }
        Response response = new Response();
        for (String translation : record.getTranslationList()) {
            // TODO(denero) POS and frequency
            response.translations.add(new Translation(record.getTerm(), "", translation, 0.0));
        }
        return gson.toJson(response);
    }

    class Request {
        String query;
        String source;
        String target;
        String[] keys;
        String context;
    }

    class Response {
        List<Definition> definitions;
        List<Translation> translations = new ArrayList<Translation>();
    }

    class Definition {
        String term;
        String pos;
        String definition;
    }

    class Translation {
        String term;
        String pos;
        String translation;
        double frequency;

        public Translation(String term, String pos, String translation, double frequency) {
            this.term = term;
            this.pos = pos;
            this.translation = translation;
            this.frequency = frequency;
        }
    }
}
