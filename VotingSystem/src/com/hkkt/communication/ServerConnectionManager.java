/*
 * MIT License
 *
 * Copyright (c) 2019 Nailah Azeez, Jaskiran Lamba, Sandeep Suri, Kent Tsuenchy
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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerConnectionManager {
  public static final int MAX_NAME_LENGTH = 40;
  public static final String SERVER_NAME = "SERVER";
  private static final String DEFAULT_CHANNEL = "Default_Channel_Name";
  private static final Logger LOG = Logger.getLogger(ServerConnectionManager.class.getName());
  private static HashMap<String, SocketChannel> channels;
  private static HashMap<String, Connection> connections;
  private static HashMap<String, ArrayList<Datagram>> datagrams;
  private static int defaultChannelId = 0;
  private static ServerConnectionManager manager = null;
  private static Selector selector;
  private static ServerSocketChannel server = null;
  private static TaskHandler taskHandler;

  public static ServerConnectionManager getInstance() throws ChannelSelectorCannotStartException {
    if (manager == null)
      manager = new ServerConnectionManager();

    return manager;
  }

  private static String getNextChannelName() {
    String name = "";

    if (manager != null) {
      defaultChannelId = 0;

      do {
        name = DEFAULT_CHANNEL + "[" + defaultChannelId + "]";
        defaultChannelId++; // may cause overflow if max reached

      } while (channels.containsKey(name));
    }

    return name;
  }

  private ServerConnectionManager() throws ChannelSelectorCannotStartException {
    if (manager == null) {
      channels = new HashMap<>();
      connections = new HashMap<>();
      datagrams = new HashMap<>();
      taskHandler = new TaskHandler();

      try {
        selector = Selector.open();
      } catch (IOException ex) {
        LOG.log(Level.SEVERE, null, ex);

        throw new ChannelSelectorCannotStartException("Failed to start up conneciton.");
      }

      Runnable selectorTask = () -> {
        // TODO need to check how to kill thread if required to force quit
        while (true)
          try {
            Thread.sleep(200);

            int readyChannels = selector.selectNow();

            if (readyChannels == 0)
              continue;

            Set<SelectionKey> selectedKeys = selector.selectedKeys();
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
                taskHandler.startTask(connection);

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
        server = ServerSocketChannel.open();
        server.socket().bind(new InetSocketAddress("localhost", 6000));
        server.configureBlocking(true);

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

              Connection connection = new Connection(name);

              while (channel.isConnectionPending())
                // do nothing for now, later on do set up if there is any
                channel.finishConnect();

              connection.toggleConnected();

              // register channel with selector to notify for the specified ready events
              channel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE, connection);
              connection.toggleListening();

              // add to map for clean up later
              channels.put(name, channel);
              connections.put(name, connection);
            } catch (ClosedChannelException ex) {
              break;
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

        taskHandler.startTask(startListening);
        taskHandler.startTask(selectorTask);
      } catch (IOException ex) {
        LOG.log(Level.SEVERE, null, ex);
      }
    }
  }

  public void addDatagramToQueue(String name, Datagram data) {
    ArrayList<Datagram> list = datagrams.get(name);

    if (list == null) {
      list = new ArrayList<>();
      datagrams.put(name, list);
    }

    list.add(data);
  }

  /**
   * Clean up connections. This includes the executor service maintaining running threads as well as the selector that
   * handles the channels. Start by cleaning up the executor service. Then deregister each channel from selector and
   * close each channel. Finally close the selector. IOExceptions may occur and are ignored as the clean up is a forced
   * operation.
   */
  public void cleanup() {
    if (manager != null) {
      taskHandler.cleanup();
      channels.forEach((taskName, channel) -> {
        try {
          // may return null if no key found for given selector
          SelectionKey key = channel.keyFor(selector);

          // deregister the channel from the selector
          if (key != null)
            key.cancel();

          channel.close();
        } catch (IOException ex) {
          // TODO log this to log file
          LOG.log(Level.WARNING, null, ex);
        }
      });
      connections.clear();
      datagrams.clear();

      try {
        selector.close();
      } catch (IOException ex) {
        // TODO log this to log file
        LOG.log(Level.WARNING, null, ex);
      }

      manager = null;
    }
  }

  public void clearDatagramFromQueue(String name) {
    datagrams.remove(name);
  }

  public SocketChannel getChannel(String name) {
    return channels.get(name);
  }

  public ArrayList<Datagram> getDatagrams(String name) {
    return datagrams.get(name);
  }

  public void removeDatagramFromQueue(String name, Datagram data) {
    ArrayList<Datagram> list = datagrams.get(name);

    if (list != null)
      list.remove(data);
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

    if (channels.containsKey(oldName) && !channels.containsKey(newName)) {
      SocketChannel channel = channels.get(oldName);
      Connection connection = connections.get(oldName);
      ArrayList<Datagram> datagramQueue = datagrams.get(oldName);

      channels.remove(oldName);
      channels.put(newName, channel);

      if (connection != null) {
        connections.remove(oldName);
        connections.put(newName, connection);

        connection.setName(newName);
      }

      if (datagramQueue != null) {
        datagrams.remove(oldName);
        datagrams.put(newName, datagramQueue);
      }

      return true;
    }

    return false;
  }
}
