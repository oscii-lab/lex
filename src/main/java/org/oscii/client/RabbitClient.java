package org.oscii.client;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.util.UUID;

/**
 * Stub client to make a rabbitmq request.
 */
public class RabbitClient {

    public static void main(String[] args)
            throws java.io.IOException,
            java.lang.InterruptedException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        String replyQueueName = channel.queueDeclare().getQueue();
        String corrId = UUID.randomUUID().toString();

        AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                .correlationId(corrId)
                .replyTo(replyQueueName)
                .build();

        channel.queueDeclare("lexicon", false, false, false, null);
        String message = "{ query: \"adult\", source: \"en\", target: \"es\", translate: true, context: \"I am an adult.\" }";
        channel.basicPublish("", "lexicon", props, message.getBytes());
        System.out.println(" [x] Sent '" + message + "'");

        channel.close();
        connection.close();
    }
}
