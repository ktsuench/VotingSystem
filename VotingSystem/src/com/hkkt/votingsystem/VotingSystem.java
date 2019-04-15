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
  public static void main(String[] args) throws IOException, ChannelSelectorCannotStartException {
    // TODO code application logic here
    CLA cla = new CLA("cla");
    CTF ctf = new CTF("ctf");
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

    cla.connectToCTF();
    ctf.connectToCLA();

    ClientConnectionManager claConn = new ClientConnectionManager("voter", 5000);
    ClientConnectionManager ctfConn = new ClientConnectionManager("voter", 6000);

    claConn.addHook(echoHook);
    ctfConn.addHook(echoHook);

    claConn.sendMessage("SERVER", "HELLO CLA");
    ctfConn.sendMessage("SERVER", "HELLO CTF");

    claConn.removeHook(echoHook);
  }
}
