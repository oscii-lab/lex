package org.oscii;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.QueueingConsumer;
import org.oscii.panlex.PanLexDBFromJSON;

public class Main {

    public static void main(String[] args)
            throws java.io.IOException,
            java.lang.InterruptedException {
        // TODO Command line argument parsing
        String panLexDir = args[0];

        System.out.println("Reading PanLex");
        PanLexDBFromJSON panLex = new PanLexDBFromJSON();
        panLex.setMaxRecordsPerType(10000); // TODO(denero) Remove this limit
        panLex.read(panLexDir);

        System.out.println("Listening for requests");
        ConnectAndListen();

        System.out.println("Done");
    }

    private static void ConnectAndListen()
            throws java.io.IOException,
            java.lang.InterruptedException {
        String queueName = "lexicon";
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        String replyQueueName = channel.queueDeclare().getQueue();

        System.out.println(" [*] Waiting for messages. To exit press CTRL+C");
        QueueingConsumer consumer = new QueueingConsumer(channel);
        channel.basicConsume(replyQueueName, true, consumer);
        while (true) {
            QueueingConsumer.Delivery delivery = consumer.nextDelivery();
            String message = new String(delivery.getBody());
            System.out.println(" [x] Received '" + message + "'");
        }
    }
}
