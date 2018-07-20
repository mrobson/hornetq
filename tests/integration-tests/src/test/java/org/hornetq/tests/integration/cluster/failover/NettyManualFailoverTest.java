/*
 * Copyright 2009 Red Hat, Inc.
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */

package org.hornetq.tests.integration.cluster.failover;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.Interceptor;
import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.api.core.client.ClientConsumer;
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.ClientProducer;
import org.hornetq.api.core.client.ClientSession;
import org.hornetq.api.core.client.ClientSessionFactory;
import org.hornetq.api.core.client.HornetQClient;
import org.hornetq.api.core.client.ServerLocator;
import org.hornetq.core.client.impl.ClientSessionFactoryInternal;
import org.hornetq.core.protocol.core.Packet;
import org.hornetq.core.protocol.core.impl.wireformat.SessionSendMessage;
import org.hornetq.core.remoting.impl.netty.TransportConstants;
import org.hornetq.core.server.NetworkHealthCheck;
import org.hornetq.spi.core.protocol.RemotingConnection;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

public class NettyManualFailoverTest extends FailoverTestBase
{

   // TYPE this command in Linux:
   // sudo ifconfig lo:1 192.0.2.0 netmask 255.0.0.0 up

   // 192.0.2.0 is reserved for documentation, so I'm pretty sure this won't exist on any system. (It shouldn't at least)
   private static final String LIVE_IP = "192.0.2.0";

   private static final InetAddress inetAddress;

   private final NetworkHealthCheck healthCheck = new NetworkHealthCheck(null, 100, 100);

   static
   {
      InetAddress addressLookup = null;
      try
      {
         addressLookup = InetAddress.getByName(LIVE_IP);
      }
      catch (Exception e)
      {
         e.printStackTrace(); // not supposed to happen
      }

      inetAddress = addressLookup;
   }

   @BeforeClass
   public static void validateIP()
   {
      NetworkHealthCheck healthCheck = new NetworkHealthCheck(null, 100, 100);
      if (!healthCheck.check(inetAddress))
      {
         System.err.println("Network for this test is down, type the following command:\nsudo ifconfig lo:1 192.0.2.0 netmask 255.0.0.0 up");
         Assume.assumeTrue(false);
      }
   }

   @Override
   protected TransportConfiguration getAcceptorTransportConfiguration(final boolean live)
   {
      return getNettyAcceptorTransportConfiguration(live);
   }

   @Override
   protected TransportConfiguration getConnectorTransportConfiguration(final boolean live)
   {
      return getNettyConnectorTransportConfiguration(live);
   }

   protected ClientSession createSession(ClientSessionFactory sf1,
                                         boolean autoCommitSends,
                                         boolean autoCommitAcks,
                                         int ackBatchSize) throws Exception
   {
      return addClientSession(sf1.createSession(autoCommitSends, autoCommitAcks, ackBatchSize));
   }

   protected ClientSession
   createSession(ClientSessionFactory sf1, boolean autoCommitSends, boolean autoCommitAcks) throws Exception
   {
      return addClientSession(sf1.createSession(autoCommitSends, autoCommitAcks));
   }

   protected ClientSession createSession(ClientSessionFactory sf1) throws Exception
   {
      return addClientSession(sf1.createSession());
   }

   protected ClientSession createSession(ClientSessionFactory sf1,
                                         boolean xa,
                                         boolean autoCommitSends,
                                         boolean autoCommitAcks) throws Exception
   {
      return addClientSession(sf1.createSession(xa, autoCommitSends, autoCommitAcks));
   }


   protected TransportConfiguration getNettyAcceptorTransportConfiguration(final boolean live)
   {
      Map<String, Object> server1Params = new HashMap<String, Object>();

      if (live)
      {
         server1Params.put(org.hornetq.core.remoting.impl.netty.TransportConstants.PORT_PROP_NAME, org.hornetq.core.remoting.impl.netty.TransportConstants.DEFAULT_PORT);
         server1Params.put(TransportConstants.HOST_PROP_NAME, LIVE_IP);
      }
      else
      {
         server1Params.put(org.hornetq.core.remoting.impl.netty.TransportConstants.PORT_PROP_NAME, org.hornetq.core.remoting.impl.netty.TransportConstants.DEFAULT_PORT);
         server1Params.put(TransportConstants.HOST_PROP_NAME, "localhost");
      }


      return new TransportConfiguration(NETTY_ACCEPTOR_FACTORY, server1Params);
   }

   protected TransportConfiguration getNettyConnectorTransportConfiguration(final boolean live)
   {
      Map<String, Object> server1Params = new HashMap<String, Object>();

      if (live)
      {
         server1Params.put(org.hornetq.core.remoting.impl.netty.TransportConstants.PORT_PROP_NAME, org.hornetq.core.remoting.impl.netty.TransportConstants.DEFAULT_PORT);
         server1Params.put(TransportConstants.HOST_PROP_NAME, LIVE_IP);
      }
      else
      {
         server1Params.put(org.hornetq.core.remoting.impl.netty.TransportConstants.PORT_PROP_NAME, org.hornetq.core.remoting.impl.netty.TransportConstants.DEFAULT_PORT);
         server1Params.put(TransportConstants.HOST_PROP_NAME, "localhost");
      }

      return new TransportConfiguration(NETTY_CONNECTOR_FACTORY, server1Params);
   }


   @Test
   public void testManualFailover() throws Exception
   {


      Assert.assertTrue(healthCheck.check(inetAddress));
      Map<String, Object> params = new HashMap<String, Object>();
      params.put(TransportConstants.HOST_PROP_NAME, LIVE_IP);
      TransportConfiguration tc = createTransportConfiguration(true, false, params);

      final AtomicInteger countSent = new AtomicInteger(0);

      liveServer.addInterceptor(new Interceptor()
      {
         @Override
         public boolean intercept(Packet packet, RemotingConnection connection) throws HornetQException
         {
            //System.out.println("Received " + packet);
            if (packet instanceof SessionSendMessage)
            {

               if (countSent.incrementAndGet() == 500)
               {
                  while (healthCheck.check(inetAddress))
                  {
                     System.out.println("Type the following command on Linux:\nsudo ifconfig lo:1 down");
                     try
                     {
                        Thread.sleep(1000);
                     }
                     catch (Exception e)
                     {
                        e.printStackTrace();
                     }
                  }

                  new Thread()
                  {
                     public void run()
                     {
                        try
                        {
                           System.err.println("Stopping server");
                           // liveServer.stop();
                           liveServer.crash(true, false);
                        }
                        catch (Exception e)
                        {
                           e.printStackTrace();
                        }
                     }
                  }.start();
               }
            }
            return true;
         }
      });

      ServerLocator locator = addServerLocator(HornetQClient.createServerLocatorWithHA(tc));

      locator.setBlockOnNonDurableSend(false);
      locator.setBlockOnDurableSend(false);
      locator.setBlockOnAcknowledge(false);
      locator.setReconnectAttempts(-1);
      locator.setConfirmationWindowSize(-1);
      locator.setProducerWindowSize(-1);
      locator.setClientFailureCheckPeriod(100);
      locator.setConnectionTTL(5000);
      ClientSessionFactoryInternal sfProducer = createSessionFactoryAndWaitForTopology(locator, 2);

      ClientSession sessionProducer = createSession(sfProducer, true, true, 0);

      sessionProducer.createQueue(FailoverTestBase.ADDRESS, FailoverTestBase.ADDRESS, null, true);

      ClientProducer producer = sessionProducer.createProducer(FailoverTestBase.ADDRESS);

      final int numMessages = 10000;
      final CountDownLatch latchReceived = new CountDownLatch(numMessages - 1000);


      ClientSessionFactoryInternal sfConsumer = createSessionFactoryAndWaitForTopology(locator, 2);

      final ClientSession sessionConsumer = createSession(sfConsumer, true, true, 0);
      final ClientConsumer consumer = sessionConsumer.createConsumer(FailoverTestBase.ADDRESS);

      sessionConsumer.start();

      final AtomicBoolean running = new AtomicBoolean(true);
      final


      Thread t = new Thread()
      {
         public void run()
         {
            int received = 0;
            int errors = 0;
            while (running.get() && received < numMessages)
            {
               try
               {
                  ClientMessage msgReceived = consumer.receive(5000);
                  if (msgReceived != null)
                  {
                     latchReceived.countDown();
                     msgReceived.acknowledge();
                     if (received++ % 100 == 0)
                     {
                        System.out.println("Received " + received);
                        sessionConsumer.commit();
                     }
                  }
               }
               catch (Throwable e)
               {
                  errors++;
                  if (errors > 10)
                  {
                     break;
                  }
                  e.printStackTrace();
               }
            }
         }
      };

      t.start();

      for (int i = 0; i < numMessages; i++)
      {
         do
         {
            try
            {
               if (i % 100 == 0)
               {
                  System.out.println("Sent " + i);
               }
               producer.send(createMessage(sessionProducer, i, true));
               break;
            }
            catch (Exception e)
            {
               new Exception("Exception on ending", e).printStackTrace();
            }
         } while (true);
      }

      Assert.assertTrue(latchReceived.await(1, TimeUnit.MINUTES));

      running.set(false);

      t.join();
   }

}
