package org.oscii;

import com.google.gson.Gson;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.QueueingConsumer;
import org.oscii.lex.Translation;

import java.util.ArrayList;
import java.util.List;

public class RabbitHandler {
    private final String host;
    private final Lexicon lexicon;
    private final String queueName;

    public RabbitHandler(String host, String queueName, Lexicon lexicon) {
        this.host = host;
        this.queueName = queueName;
        this.lexicon = lexicon;
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

            String message = new String(delivery.getBody(), "UTF-8");
            System.out.println(" [.] message received: " + message);

            String response = respond(message);
            System.out.println(" [.] message response: " + response);
            channel.basicPublish("", props.getReplyTo(), replyProps, response.getBytes("UTF-8"));
            channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
        }
    }

    // Parse message, perform a lookup, and construct a response.
    // TODO(denero) Error handling
    private String respond(String message) {
        Gson gson = new Gson();
        Request request = gson.fromJson(message, Request.class);
        List<Translation> translations =
                lexicon.translate(request.query, request.source, request.target);

        Response response = new Response();
        translations.forEach(t ->
                // TODO(denero) Add formatted source, POS, frequency
                response.translations.add(new ResponseTranslation(
                        request.query, "", t.translation.text, t.frequency)));
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
        List<ResponseTranslation> translations = new ArrayList<>();
    }

    private class ResponseTranslation {
        String source;
        String pos;
        String target;
        double frequency;

        public ResponseTranslation(String source, String pos, String target, double frequency) {
            this.source = source;
            this.pos = pos;
            this.target = target;
            this.frequency = frequency;
        }
    }
}
