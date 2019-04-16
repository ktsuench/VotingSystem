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
package com.hkkt.votingsystem;

import com.hkkt.CentralLegitimizationAgency.CLA;
import com.hkkt.CentralTabulationFacility.CTF;
import com.hkkt.communication.ChannelSelectorCannotStartException;
import com.hkkt.communication.ClientConnectionManager;
import com.hkkt.communication.Datagram;
import com.hkkt.communication.DatagramMissingSenderReceiverException;
import com.hkkt.util.Hook;
import java.io.IOException;

/**
 *
 * @author Hassan Khan
 */
public class VotingSystem {

  /**
   * @param args the command line arguments
   */
  public static void main(String[] args) throws IOException, ChannelSelectorCannotStartException, DatagramMissingSenderReceiverException {
    // TODO code application logic here
    int claPort = 5000, ctfPort = 6000;
    String voterId = "voter";
    CLA cla = new CLA("cla", claPort);
    CTF ctf = new CTF("ctf", ctfPort);
    Hook echoHook = new Hook() {
      private Datagram data;

      @Override
      public void setHookData(Object data) {
        if (data instanceof Datagram)
          this.data = (Datagram) data;
      }

      @Override
      public void run() {
        System.out.println("voter received message from " + this.data.getSender() + ": " + this.data.getData());
      }
    };

    cla.connectToCTF(ctfPort);
    ctf.connectToCLA(claPort);

    ClientConnectionManager claConn = new ClientConnectionManager(voterId, claPort);
    ClientConnectionManager ctfConn = new ClientConnectionManager(voterId, ctfPort);

    claConn.addHook(echoHook);
    ctfConn.addHook(echoHook);

    claConn.sendMessage("HELLO CLA");
    ctfConn.sendMessage("HELLO CTF");

    claConn.sendRequest(VotingSystemDatagram.ACTION_TYPE.REQUEST_VALIDATION_NUM.toString());

    claConn.removeHook(echoHook);
  }
}
