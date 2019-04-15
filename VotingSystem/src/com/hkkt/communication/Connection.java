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

import com.hkkt.votingsystem.AbstractServer;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Kent Tsuenchy
 */
public class Connection implements Runnable {
  private static final Logger LOG = Logger.getLogger(Connection.class.getName());
  private final AtomicBoolean ACTIVE;
  private final AtomicBoolean CONNECTED;
  private final AtomicBoolean CREATED;
  private final AtomicBoolean LISTENING;
  private final AtomicInteger TYPE;
  private String name;
  private final ServerConnectionManager manager;
  private final AbstractServer server;

  public Connection(String name, ServerConnectionManager manager, AbstractServer server) {
    this.CREATED = new AtomicBoolean(false);
    this.CONNECTED = new AtomicBoolean(false);
    this.LISTENING = new AtomicBoolean(false);
    this.ACTIVE = new AtomicBoolean(false);
    this.TYPE = new AtomicInteger(TASK_TYPE.AVAILABLE.ordinal());
    this.name = name;
    this.manager = manager;
    this.server = server;
  }

  public String getName() {
    return this.name;
  }

  public TASK_TYPE getTaskType() {
    return TASK_TYPE.values()[this.TYPE.get()];
  }

  public boolean isActive() {
    return this.ACTIVE.get();
  }

  public boolean isConnected() {
    return this.CONNECTED.get();
  }

  public boolean isCreated() {
    return this.CREATED.get();
  }

  public boolean isListening() {
    return this.LISTENING.get();
  }

  @Override
  public void run() {
    this.ACTIVE.set(true);

    try {
      SocketChannel channel = manager.getChannel(this.name);
      ByteBuffer buffer = ByteBuffer.allocate(1024);
      ArrayList<Datagram> list;
      Datagram data;

      if (this.getTaskType() == TASK_TYPE.READ) {
        // read from client
        channel.read(buffer);
        // flip buffer for reading
        buffer.flip();

        // retrieve datagram from byte[]
        data = Datagram.fromBytes(buffer.array());

        if (data.getReceiver().equals(ServerConnectionManager.SERVER_NAME)) {
          if (data.getType() == Datagram.DATA_TYPE.UPDATE_ID)
            manager.updateChannelId(name, data.getSender());
          else
            server.handleDatagram(data);
        } else {
          // add to queue
          manager.addDatagramToQueue(data.getReceiver(), data);
        }
      } else if (this.getTaskType() == TASK_TYPE.WRITE) {
        list = manager.getDatagrams(this.name);

        if (list != null && list.size() > 0) {
          // get next in queue
          data = list.get(0);
          // remove from queue
          list.remove(0);

          // send to client
          buffer.put(data.getBytes());
          buffer.flip();
          channel.write(buffer);
        }
      }

      this.setTaskType(TASK_TYPE.AVAILABLE);
    } catch (IOException ex) {
      LOG.log(Level.SEVERE, null, ex);
      this.ACTIVE.set(false);
    }

    this.ACTIVE.set(false);
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setTaskType(TASK_TYPE type) {
    this.TYPE.set(type.ordinal());
  }

  public boolean toggleActive() {
    return this.ACTIVE.compareAndSet(this.ACTIVE.get(), !this.ACTIVE.get());
  }

  public boolean toggleConnected() {
    return this.CONNECTED.compareAndSet(this.CONNECTED.get(), !this.CONNECTED.get());
  }

  public boolean toggleCreated() {
    return this.CREATED.compareAndSet(this.CREATED.get(), !this.CREATED.get());
  }

  public boolean toggleListening() {
    return this.LISTENING.compareAndSet(this.LISTENING.get(), !this.LISTENING.get());
  }

  public static enum TASK_TYPE {
    READ, WRITE, AVAILABLE
  }
}
