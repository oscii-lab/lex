package org.oscii.client;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.QueueingConsumer;
/**
 * Created by denero on 2/17/15.
 */
public class Client {

    public static void main(String[] args)
            throws java.io.IOException,
            java.lang.InterruptedException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        channel.queueDeclare("lexicon", false, false, false, null);
        String message = "{ query: \"adult\", source: \"eng\", target: \"spa\", keys: [\"definition\", \"translation\"], context: \"I am an adult.\" }";
        channel.basicPublish("", "lexicon", null, message.getBytes());
        System.out.println(" [x] Sent '" + message + "'");

        channel.close();
        connection.close();
    }
}
