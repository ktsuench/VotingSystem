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

import com.hkkt.util.DataObservable;
import com.hkkt.util.Hook;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Observer;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Kent Tsuenchy
 */
public class ClientConnectionManager {
  private static final String DEFAULT_RECIPIENT = "SERVER";
  private static final Logger LOG = Logger.getLogger(ClientConnectionManager.class.getName());
  private final SocketChannel CHANNEL;
  private final DataObservable HOOKS;
  private final ConcurrentHashMap<Hook, Observer> HOOKS_LIST;
  private final String NAME;
  private final Selector SELECTOR;
  private final ConcurrentLinkedDeque<Datagram> SEND_DATAGRAMS;
  private final TaskHandler TASK_HANDLER;
  private boolean channelActive;
  private boolean die;

  /**
   *
   * @param name should be less than 40 characters
   * @param port
   *
   * @throws IOException
   * @throws com.hkkt.communication.ChannelSelectorCannotStartException
   * @throws com.hkkt.communication.DatagramMissingSenderReceiverException
   */
  public ClientConnectionManager(String name, int port) throws IOException, ChannelSelectorCannotStartException, DatagramMissingSenderReceiverException {
    this.HOOKS = new DataObservable();
    this.HOOKS_LIST = new ConcurrentHashMap<>();
    this.SEND_DATAGRAMS = new ConcurrentLinkedDeque<>();
    this.TASK_HANDLER = new TaskHandler();
    this.channelActive = false;
    this.die = false;

    try {
      SELECTOR = Selector.open();
    } catch (IOException ex) {
      LOG.log(Level.SEVERE, null, ex);

      throw new ChannelSelectorCannotStartException("Failed to start up conneciton.");
    }

    this.NAME = name;
    this.CHANNEL = SocketChannel.open();

    Runnable selectorTask = () -> {
      // TODO need to check how to kill thread if required to force quit
      while (true)
        try {
          if (die)
            break;

          Thread.sleep(100);

          int readyChannels = SELECTOR.selectNow();

          if (readyChannels == 0)
            continue;

          Set<SelectionKey> selectedKeys = SELECTOR.selectedKeys();
          Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

          while (keyIterator.hasNext()) {
            SelectionKey key = keyIterator.next();
            ByteBuffer buffer;

            if (key.attachment() instanceof Connection)
              buffer = ByteBuffer.allocate(((Connection) key.attachment()).getMaxBufferSize());
            else
              buffer = ByteBuffer.allocate(Connection.DEFAULT_MAX_BUFFER_SIZE);

            if (!channelActive) {
              channelActive = true;

              if (key.isReadable()) {
                CHANNEL.read(buffer);
                buffer.flip();

                if (HOOKS.countObservers() > 0)
                  HOOKS.updateObservers(Datagram.fromBytes(buffer.array()));
              } else if (key.isWritable() && SEND_DATAGRAMS.size() > 0) {
                buffer.put(SEND_DATAGRAMS.poll().getBytes());
                buffer.flip();
                CHANNEL.write(buffer);
              }
            }

            keyIterator.remove();
            channelActive = false; // may cause issues if more than one channel is being used
          }
        } catch (IOException | DatagramMissingSenderReceiverException ex) {
          // TODO log this to log file
          LOG.log(Level.SEVERE, null, ex);
        } catch (InterruptedException ex) {
          break;
        }
    };

    this.TASK_HANDLER.startTask(selectorTask);

    this.SEND_DATAGRAMS.add(new Datagram(Datagram.DATA_TYPE.UPDATE_ID, name, ServerConnectionManager.SERVER_NAME, name));

    this.CHANNEL.configureBlocking(false);
    this.CHANNEL.connect(new InetSocketAddress("localhost", port));

    while (this.CHANNEL.isConnectionPending())
      // do nothing for now, later on do set up if there is any
      this.CHANNEL.finishConnect();

    this.CHANNEL.register(this.SELECTOR, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
  }

  public void addHook(Hook hook) {
    Observer o = (hooks, data) -> {
      hook.setHookData(data);
      hook.run();
    };
    this.HOOKS_LIST.put(hook, o);
    this.HOOKS.addObserver(o);
  }

  public void cleanup() {
    this.TASK_HANDLER.cleanup();
    this.die = true;
    // TODO notify observers that client is closing
    this.HOOKS.deleteObservers();
  }

  public void removeHook(Hook hook) {
    this.HOOKS.deleteObserver(this.HOOKS_LIST.get(hook));
  }

  /**
   * Send data
   *
   * @param data
   */
  public void sendData(Datagram data) {
    this.SEND_DATAGRAMS.add(data);
  }

  /**
   * Send message to the designated recipient
   *
   * @param recipient should be less than 40 characters
   * @param message should be less than 120 characters
   * @throws com.hkkt.communication.DatagramMissingSenderReceiverException
   */
  public void sendMessage(String recipient, String message) throws DatagramMissingSenderReceiverException {
    Datagram datagram = new Datagram(Datagram.DATA_TYPE.MESSAGE, NAME, recipient, message);

    this.SEND_DATAGRAMS.add(datagram);
  }

  /**
   * Send message to the default recipient
   *
   * @param message should be less than 120 characters
   * @throws com.hkkt.communication.DatagramMissingSenderReceiverException
   */
  public void sendMessage(String message) throws DatagramMissingSenderReceiverException {
    Datagram datagram = new Datagram(Datagram.DATA_TYPE.MESSAGE, NAME, DEFAULT_RECIPIENT, message);

    this.SEND_DATAGRAMS.add(datagram);
  }

  /**
   * Send request
   *
   * @param receiver
   * @param requestType
   * @param data
   * @throws com.hkkt.communication.DatagramMissingSenderReceiverException
   */
  public void sendRequest(String requestType, String receiver, String data) throws DatagramMissingSenderReceiverException {
    String r = receiver == null ? DEFAULT_RECIPIENT : receiver;
    Datagram datagram = new Datagram(Datagram.DATA_TYPE.OTHER, requestType, NAME, r, data);

    this.SEND_DATAGRAMS.add(datagram);
  }

  /**
   * Send request
   *
   * @param receiver
   * @param requestType
   * @throws com.hkkt.communication.DatagramMissingSenderReceiverException
   */
  public void sendRequest(String requestType, String receiver) throws DatagramMissingSenderReceiverException {
    Datagram datagram = new Datagram(requestType, NAME, receiver);

    this.SEND_DATAGRAMS.add(datagram);
  }

  /**
   * Send request
   *
   * @param requestType
   * @throws com.hkkt.communication.DatagramMissingSenderReceiverException
   */
  public void sendRequest(String requestType) throws DatagramMissingSenderReceiverException {
    Datagram datagram = new Datagram(requestType, NAME, DEFAULT_RECIPIENT);

    this.SEND_DATAGRAMS.add(datagram);
  }
}
