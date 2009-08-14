/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005-2009, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.messaging.core.server.impl;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.util.Date;

import org.jboss.messaging.core.paging.PagingManager;
import org.jboss.messaging.core.paging.PagingStore;
import org.jboss.messaging.core.server.MessagingServer;
import org.jboss.messaging.utils.SimpleString;

/**
 * A ServerInfo
 *
 * @author <a href="mailto:jmesnil@redhat.com">Jeff Mesnil</a>
 *
 *
 */
public class ServerInfo
{
   private final MessagingServer server;

   private PagingManager pagingManager;

   // Constants -----------------------------------------------------

   // Attributes ----------------------------------------------------

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------

   public ServerInfo(final MessagingServer server, final PagingManager pagingManager)
   {
      this.server = server;
      this.pagingManager = pagingManager;
   }

   // Public --------------------------------------------------------

   public String dump()
   {
      MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
      MemoryUsage heapMemory = memory.getHeapMemoryUsage();
      MemoryUsage nonHeapMemory = memory.getHeapMemoryUsage();
      ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
      String info = "\n**** Server Dump ****\n";
      info += String.format("date:            %s\n", new Date());
      info += String.format("heap memory:     used=%s, max=%s\n",
                            sizeof(heapMemory.getUsed()),
                            sizeof(heapMemory.getMax()));
      info += String.format("non-heap memory: used=%s, max=%s\n",
                            sizeof(nonHeapMemory.getUsed()),
                            sizeof(nonHeapMemory.getMax()));
      info += appendPagingInfos();
      info += String.format("# of thread:     %d\n", threadMXBean.getThreadCount());
      info += String.format("# of conns:      %d\n", server.getConnectionCount());
      info += "********************\n";
      return info;
   }

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   // Private -------------------------------------------------------

   private String appendPagingInfos()
   {
      String info = "";
      info += String.format("total paging memory:   %s\n", sizeof(pagingManager.getTotalMemory()));
      for (SimpleString storeName : pagingManager.getStoreNames())
      {
         PagingStore pageStore;
         try
         {
            pageStore = pagingManager.getPageStore(storeName);
            info += String.format("\t%s: %s\n", storeName, sizeof(pageStore.getPageSizeBytes() * pageStore.getNumberOfPages()));         
         }
         catch (Exception e)
         {
            info += String.format("\t%s: %s\n", storeName, e.getMessage());
         }
      }
      return info;
   }

   private static long oneKiB = 1024;

   private static long oneMiB = oneKiB * 1024;

   private static long oneGiB = oneMiB * 1024;

   private static String sizeof(long size)
   {
      double s = Long.valueOf(size).doubleValue();
      String suffix = "B";
      if (s > oneGiB)
      {
         s /= oneGiB;
         suffix = "GiB";
      }
      else if (s > oneMiB)
      {
         s /= oneMiB;
         suffix = "MiB";
      }
      else if (s > oneKiB)
      {
         s /= oneKiB;
         suffix = "kiB";
      }
      return String.format("%.2f %s", s, suffix);
   }

   // Inner classes -------------------------------------------------

}
