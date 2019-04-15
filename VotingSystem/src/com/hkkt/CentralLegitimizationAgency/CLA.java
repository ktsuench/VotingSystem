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
package com.hkkt.CentralLegitimizationAgency;

import com.hkkt.communication.ChannelSelectorCannotStartException;
import com.hkkt.communication.ClientConnectionManager;
import com.hkkt.communication.Datagram;
import com.hkkt.communication.ServerConnectionManager;
import com.hkkt.votingsystem.AbstractServer;
import java.io.IOException;

/**
 *
 * @author Hassan Khan
 */
public class CLA extends AbstractServer {
  private final ServerConnectionManager serverManager;
  private ClientConnectionManager clientManager;
  private final String name;

  public CLA(String name) throws ChannelSelectorCannotStartException, IOException {
    this.serverManager = new ServerConnectionManager(5000, this);
    this.name = name;
  }

  public void connectToCTF() throws ChannelSelectorCannotStartException, IOException {
    this.clientManager = new ClientConnectionManager("CLA", 6000);
  }

  @Override
  public void handleDatagram(Datagram datagram) {
    System.out.println(datagram.getData());
    System.out.println(datagram.getSender());

    Datagram echo = new Datagram(Datagram.DATA_TYPE.MESSAGE, this.name, datagram.getSender(), datagram.getData());

    this.serverManager.addDatagramToQueue(datagram.getSender(), echo);
  }
}
