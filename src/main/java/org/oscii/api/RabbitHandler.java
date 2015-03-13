package org.oscii.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.QueueingConsumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.oscii.Lexicon;
import org.oscii.lex.Translation;

import java.util.ArrayList;
import java.util.List;

public class RabbitHandler {
    private final String host;
    private final String queueName;
    private final String username;
    private final String password;
    private final Protocol protocol;

    private final static Logger log = LogManager.getLogger(RabbitHandler.class);

    public RabbitHandler(String host, String queueName, String username, String password, Protocol protocol) {
        this.host = host;
        this.queueName = queueName;
        this.username = username;
        this.password = password;
        this.protocol = protocol;
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

    private String respond(String message) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Protocol.Request request = gson.fromJson(message, Protocol.Request.class);
        Protocol.Response response = protocol.respond(request);
        return gson.toJson(response);
    }
}
