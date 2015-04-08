/*
 * Copyright (c) 2015 Tarek Auel
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions
 * of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
 * TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
 * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package de.dhbw.mannheim.assemblylinesim;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.QueueingConsumer;
import com.thoughtworks.xstream.XStream;
import de.dhbw.mannheim.assemblylinesim.model.Report;
import de.dhbw.mannheim.erpsim.ErpSimulator;
import de.dhbw.mannheim.erpsim.model.MachineOrder;

import java.io.IOException;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author Tarek Auel
 * @since 08.04.2015
 */
public class AssemblyLineSim {

    private final static XStream xstream = new XStream();

    private final static String REPORT_EXCHANGE_NAME = "REPORT_EXCHANGE";

    Queue<MachineOrder> tasks = new ConcurrentLinkedQueue<MachineOrder>();
    private final Channel channel;
    private boolean nextCanStart = true;

    public static void main(String[] args) throws Exception {
        new AssemblyLineSim("localhost");
    }

    private AssemblyLineSim(String hostname) throws IOException {
        (new RabbitListener(hostname)).start();

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(hostname);
        Connection connection = factory.newConnection();
        channel = connection.createChannel();
        channel.exchangeDeclare(REPORT_EXCHANGE_NAME, "fanout");
    }

    private synchronized void receivedOrder(MachineOrder mo) {
        tasks.offer(mo);
        startNext();
    }

    private synchronized void finishedTask(Report r) {
        try {
            String message = xstream.toXML(r);
            channel.basicPublish(REPORT_EXCHANGE_NAME, "", null, message.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private synchronized void nextMayStart() {
        nextCanStart = true;
        startNext();
    }

    private synchronized void startNext() {
        if (nextCanStart && tasks.size() > 0) {
            nextCanStart = false;
            MachineOrder mo = tasks.remove();
            (new CycleSimulator(mo)).start();
        }
    }

    class RabbitListener extends Thread {

        QueueingConsumer consumer;

        public RabbitListener(String hostname) throws IOException {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(hostname);
            Connection connection = factory.newConnection();
            Channel channel = connection.createChannel();

            channel.exchangeDeclare(ErpSimulator.MACHINE_ORDER_EXCHANGE_NAME, "fanout");
            String queueName = channel.queueDeclare().getQueue();
            channel.queueBind(queueName, ErpSimulator.MACHINE_ORDER_EXCHANGE_NAME, "");

            consumer = new QueueingConsumer(channel);
            channel.basicConsume(queueName, true, consumer);
        }

        @Override
        public void run() {
            super.run();
            while (true) {
                try {
                    System.out.println("Waiting for messages");
                    QueueingConsumer.Delivery delivery = consumer.nextDelivery();
                    String message = new String(delivery.getBody());
                    System.out.println("Received machine order");

                    receivedOrder((MachineOrder) xstream.fromXML(message));
                } catch (InterruptedException e) {
                    // ignore
                }
            }
        }
    }

    class CycleSimulator extends Thread {

        private final MachineOrder machineOrder;

        public CycleSimulator(MachineOrder machineOrder) {
            this.machineOrder = machineOrder;
        }

        @Override
        public void run() {
            super.run();
            try {
                System.out.println("Start manufacturing for " + machineOrder.getId());
                Report r = new Report();
                Random rand = new Random();
                Thread.sleep(4000 + (int) (rand.nextGaussian() * 2000));
                r.passedLightBarrier();
                Thread.sleep(4000 + (int) (rand.nextGaussian() * 2000));
                r.passedLightBarrier();

                // At this point the next can start
                nextMayStart();

                Thread.sleep(4000 + (int) (rand.nextGaussian() * 2000));
                r.passedLightBarrier();
                Thread.sleep(4000 + (int) (rand.nextGaussian() * 2000));
                r.passedLightBarrier();
                finishedTask(r);
                System.out.println("Finished manufacturing for " + machineOrder.getId());
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }

}
