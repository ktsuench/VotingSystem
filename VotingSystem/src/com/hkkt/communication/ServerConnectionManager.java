/*
 * MIT License
 *
 * Copyright (c) 2019 Hassan Khan, Kent Tsuenchy
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.hkkt.communication;

import com.hkkt.votingsystem.AbstractServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerConnectionManager {
  public static final int MAX_NAME_LENGTH = 40;
  public static final String SERVER_NAME = "SERVER";
  private static final String DEFAULT_CHANNEL = "Default_Channel_Name";
  private static final Logger LOG = Logger.getLogger(ServerConnectionManager.class.getName());
  private final ConcurrentHashMap<String, SocketChannel> CHANNELS;
  private final ConcurrentHashMap<String, Connection> CONNECTIONS;
  private final ConcurrentHashMap<String, ConcurrentLinkedDeque<Datagram>> DATAGRAMS;
  private final Selector SELECTOR;
  private final TaskHandler TASK_HANDLER;
  private int defaultChannelId = 0;
  private ServerSocketChannel server = null;

  public ServerConnectionManager(InetSocketAddress address, AbstractServer root) throws ChannelSelectorCannotStartException {
    this.CHANNELS = new ConcurrentHashMap<>();
    this.CONNECTIONS = new ConcurrentHashMap<>();
    this.DATAGRAMS = new ConcurrentHashMap<>();
    this.TASK_HANDLER = new TaskHandler();

    try {
      this.SELECTOR = Selector.open();
    } catch (IOException ex) {
      LOG.log(Level.SEVERE, null, ex);

      throw new ChannelSelectorCannotStartException("Failed to start up conneciton.");
    }

    Runnable selectorTask = () -> {
      // TODO need to check how to kill thread if required to force quit
      while (true)
        try {
          Thread.sleep(200);

          int readyChannels = SELECTOR.selectNow();

          if (readyChannels == 0)
            continue;

          Set<SelectionKey> selectedKeys = SELECTOR.selectedKeys();
          Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

          while (keyIterator.hasNext()) {
            SelectionKey key = keyIterator.next();
            Connection connection;
            boolean startTask = true;

            if (key.attachment() instanceof Connection)
              connection = (Connection) key.attachment();
            else {
              LOG.log(Level.WARNING, "Unrecognized attachment for channel.");
              continue;
            }

            if ((key.isReadable() || key.isWritable()) && connection.getTaskType() == Connection.TASK_TYPE.AVAILABLE) {
              if (key.isReadable())
                connection.setTaskType(Connection.TASK_TYPE.READ);
              else if (key.isWritable())
                connection.setTaskType(Connection.TASK_TYPE.WRITE);
            } else
              startTask = false;
            // TODO log this to log file
            // LOG.log(Level.WARNING, "Unknown event fired for a channel found in selector.");

            if (startTask)
              TASK_HANDLER.startTask(connection);

            keyIterator.remove();
          }
        } catch (IOException ex) {
          // TODO log this to log file
          LOG.log(Level.SEVERE, null, ex);
        } catch (InterruptedException ex) {
          break;
        }
    };

    try {
      this.server = ServerSocketChannel.open();
      this.server.socket().bind(address);
      this.server.configureBlocking(true);

      Runnable startListening = () -> {
        // TODO need to check how to kill thread if required to force quit
        while (true)
          try {
            SocketChannel channel = server.accept();
            String name;

            if (channel == null)
              continue;

            channel.configureBlocking(false);
            name = getNextChannelName();

            Connection connection = new Connection(name, this, root);

            while (channel.isConnectionPending())
              // do nothing for now, later on do set up if there is any
              channel.finishConnect();

            connection.toggleConnected();

            // register channel with selector to notify for the specified ready events
            channel.register(SELECTOR, SelectionKey.OP_READ | SelectionKey.OP_WRITE, connection);
            connection.toggleListening();

            // add to map for clean up later
            CHANNELS.put(name, channel);
            CONNECTIONS.put(name, connection);
          } catch (ClosedChannelException ex) {
            // break;
          } catch (IOException ex) {
            LOG.log(Level.SEVERE, null, ex);

            /*
             * if (!connection.isCreated()) throw new ConnectException("Failed to open socket channel"); else if
             * (!connection.isConnected()) throw new ConnectException("Failed to connect to " +
             * socketChannel.getRemoteAddress().toString()); else if (!connection.isListening()) throw new
             * ConnectException("Failed to register socket channel for listening");
             */
          }
      };

      this.TASK_HANDLER.startTask(startListening);
      this.TASK_HANDLER.startTask(selectorTask);
    } catch (IOException ex) {
      LOG.log(Level.SEVERE, null, ex);
    }
  }

  public void addDatagramToQueue(String name, Datagram data) {
    this.DATAGRAMS.computeIfAbsent(name, key -> new ConcurrentLinkedDeque<>()).add(data);
  }

  /**
   * Clean up connections. This includes the executor service maintaining running threads as well as the selector that
   * handles the channels. Start by cleaning up the executor service. Then deregister each channel from selector and
   * close each channel. Finally close the selector. IOExceptions may occur and are ignored as the clean up is a forced
   * operation.
   */
  public void cleanup() {
    this.TASK_HANDLER.cleanup();
    this.CHANNELS.forEach((taskName, channel) -> {
      try {
        // may return null if no key found for given selector
        SelectionKey key = channel.keyFor(this.SELECTOR);

        // deregister the channel from the selector
        if (key != null)
          key.cancel();

        channel.close();
      } catch (IOException ex) {
        // TODO log this to log file
        LOG.log(Level.WARNING, null, ex);
      }
    });
    this.CONNECTIONS.clear();
    this.DATAGRAMS.clear();

    try {
      this.SELECTOR.close();
    } catch (IOException ex) {
      // TODO log this to log file
      LOG.log(Level.WARNING, null, ex);
    }
  }

  public void clearDatagramFromQueue(String name) {
    this.DATAGRAMS.remove(name);
  }

  public SocketChannel getChannel(String name) {
    return this.CHANNELS.get(name);
  }

  public ConcurrentLinkedDeque<Datagram> getDatagrams(String name) {
    return this.DATAGRAMS.get(name);
  }

  public void removeDatagramFromQueue(String name, Datagram data) {
    this.DATAGRAMS.computeIfPresent(name, (key, list) -> {
      list.remove(data);
      return list;
    });
  }

  /**
   *
   * @param oldName
   * @param newName id of client, should not be more than 40 characters
   * <p>
   * @return
   */
  public boolean updateChannelId(String oldName, String newName) {
    if (newName.length() > MAX_NAME_LENGTH)
      return false;

    if (this.CHANNELS.containsKey(oldName) && !this.CHANNELS.containsKey(newName)) {
      this.CHANNELS.put(newName, this.CHANNELS.remove(oldName));

      this.CONNECTIONS.computeIfPresent(oldName, (key, conn) -> {
        conn.setName(newName);
        return this.CONNECTIONS.put(newName, conn);
      });

      this.DATAGRAMS.computeIfPresent(oldName, (key, data) -> {
        return this.DATAGRAMS.put(newName, data);
      });

      return true;
    }

    return false;
  }

  private String getNextChannelName() {
    String name = "";

    do {
      name = DEFAULT_CHANNEL + "[" + this.defaultChannelId + "]";
      this.defaultChannelId++; // may cause overflow if max reached
    } while (this.CHANNELS.containsKey(name));

    return name;
  }
}
