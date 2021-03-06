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

    // Name of rabbitmq exchange
    public final static String REPORT_EXCHANGE_NAME = "REPORT_EXCHANGE";

    private final static XStream xstream = new XStream();

    public static void main(String[] args) throws Exception {
        new AssemblyLineSim("localhost", 10);
    }

    // queue for the next machine order for the assembly line
    Queue<MachineOrder> tasks = new ConcurrentLinkedQueue<MachineOrder>();

    // channel to connect to rabbitmq
    private final Channel channel;

    // identifies if start of assembly line is empty
    private boolean nextCanStart = true;

    // speed up factor allows to speed up simulation
    // e.g. 10 --> 10 times faster
    private final double speedUpFactor;

    /**
     *
     * @param hostname of the rabbitMq
     * @param speedUpFactor speed up factor to speed up simulation, e.g. 10 --> 10 times faster
     * @throws IOException is thrown if the connection to rabbitmq fails
     */
    private AssemblyLineSim(String hostname, double speedUpFactor) throws IOException {
        (new RabbitListener(hostname)).start();
        this.speedUpFactor = speedUpFactor;
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(hostname);
        Connection connection = factory.newConnection();
        channel = connection.createChannel();
        channel.exchangeDeclare(REPORT_EXCHANGE_NAME, "fanout");
    }

    /**
     * Called if new task for assembly line is received
     *
     * @param mo received machine order
     */
    private synchronized void receivedOrder(MachineOrder mo) {
        tasks.offer(mo);
        startNext();
    }

    /**
     * Called if simulation finished a task. Send the report to the rabbitmq
     * exchange
     *
     * @param r the generated report
     */
    private synchronized void finishedTask(Report r) {
        try {
            String message = xstream.toXML(r);
            channel.basicPublish(REPORT_EXCHANGE_NAME, "", null, message.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Called if someone left the first part of the assembly line. Sets
     * the <code>nextCanStart</code> to <code>true</code> and starts the
     * next machine order
     */
    private synchronized void nextMayStart() {
        nextCanStart = true;
        startNext();
    }

    /**
     * Starts the next machine order if possible
     */
    private synchronized void startNext() {
        if (nextCanStart && tasks.size() > 0) {
            nextCanStart = false;
            MachineOrder mo = tasks.remove();
            (new CycleSimulator(mo)).start();
        }
    }

    /**
     * Listener for new machine orders in the rabbitmq
     */
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

    /**
     * Simulates one cycle of the assembly line
     */
    class CycleSimulator extends Thread {

        private final Random rand = new Random();

        private final MachineOrder machineOrder;

        public CycleSimulator(MachineOrder machineOrder) {
            this.machineOrder = machineOrder;
        }

        @Override
        public void run() {
            super.run();
            try {
                System.out.println(machineOrder.getId() + " started manufacturing");
                Report r = new Report(machineOrder.getId());
                Thread.sleep((long) ((1000 / speedUpFactor) + getRandomTime(20 / speedUpFactor)));

                r.passedLightBarrier();
                System.out.println(machineOrder.getId() + " passed first light barrier");
                Thread.sleep((long) (4250 / speedUpFactor + getRandomTime(450 / speedUpFactor)));

                r.passedLightBarrier();
                System.out.println(machineOrder.getId() + " passed second light barrier");
                r.setSpeedDrillerRPM((int) (9200 + Math.random() * 2000));
                Thread.sleep((long) (7100 / speedUpFactor + getRandomTime(1000 / speedUpFactor)));

                r.passedLightBarrier();
                System.out.println(machineOrder.getId() + " passed third light barrier");
                r.setSpeedShaperRPM((int) (15000 + Math.random() * 1000));
                nextMayStart();
                Thread.sleep((long) (9500 / speedUpFactor + getRandomTime(3000 / speedUpFactor)));

                r.passedLightBarrier();
                System.out.println(machineOrder.getId() + " passed fourth light barrier");
                finishedTask(r);
                System.out.println(machineOrder.getId() + " finished manufacturing");
            } catch (InterruptedException e) {
                // ignore
            }
        }

        private long getRandomTime(double range) {
            double diff = rand.nextGaussian();
            if (diff < -1 ) {
                diff = -1;
            } else if (diff > 1) {
                diff = 1;
            }
            diff *= range;

            return (long) diff;
        }
    }

}
