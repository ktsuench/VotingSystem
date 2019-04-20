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
import com.hkkt.communication.DatagramMissingSenderReceiverException;
import com.hkkt.communication.ServerConnectionManager;
import com.hkkt.votingsystem.AbstractServer;
import com.hkkt.votingsystem.VotingDatagram;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Hassan Khan
 */
public class CLA extends AbstractServer {
  private static final int VALIDATION_NUM_LIMIT = Integer.MAX_VALUE;
  private ClientConnectionManager clientManager;
  private final String name;
  private final ServerConnectionManager serverManager;
  private final HashMap<String, Integer> validationTickets;

  /**
   * Constructs a Central Legitimization Agency
   *
   * @param name CLA unique id
   * @param port the port that the CLA is listening on
   * @throws ChannelSelectorCannotStartException
   * @throws IOException
   */
  public CLA(String name, int port) throws ChannelSelectorCannotStartException, IOException {
    this.serverManager = new ServerConnectionManager(port, this);
    this.name = name;
    this.validationTickets = new HashMap<>();
  }

  /**
   * Connect to the Central Tabulation Facility
   *
   * @param port the port that the CTF is listening on
   * @throws ChannelSelectorCannotStartException
   * @throws IOException
   * @throws com.hkkt.communication.DatagramMissingSenderReceiverException
   */
  public void connectToCTF(int port) throws ChannelSelectorCannotStartException, IOException, DatagramMissingSenderReceiverException {
    this.clientManager = new ClientConnectionManager(this.name, port);
  }

  /**
   * Generate a new validation ticket for a voter
   *
   * @param voter id
   * @return validation ticket if voter not registered, otherwise -1
   */
  private int generateValidationTicket(String voter) {
    int validationTicket = (int) (Math.random() * VALIDATION_NUM_LIMIT);
    boolean alreadyRegistered = this.validationTickets.containsKey(voter);

    if (!alreadyRegistered)
      this.validationTickets.put(voter, validationTicket);

    return alreadyRegistered ? -1 : validationTicket;
  }

  /**
   * Method for CLA to handle incoming data
   *
   * @param datagram data that has been sent to the CLA
   */
  @Override
  public void handleDatagram(Datagram datagram) {
    System.out.println(this.name + " received message from " + datagram.getSender() + ": " + datagram.getData());

    try {
      if (VotingDatagram.isVotingSystemDatagram(datagram)) {
        VotingDatagram votingDatagram = new VotingDatagram(datagram);
        Datagram response;

        switch(votingDatagram.getOperationType()) {
          case REQUEST_VALIDATION_NUM:
            String data = Integer.toString(this.generateValidationTicket(datagram.getData()));
            response = votingDatagram.flip(data);
            break;
          default:
            String errorMsg = "Unknown request. CLA cannot handle the requested operation.";
            response = datagram.flip(errorMsg, Datagram.DATA_TYPE.ERROR);
            break;
        }

        this.serverManager.addDatagramToQueue(response.getReceiver(), response);
      }
    } catch (UnsupportedEncodingException | DatagramMissingSenderReceiverException ex) {
      Logger.getLogger(CLA.class.getName()).log(Level.SEVERE, null, ex);
    }

    try {
      Datagram echo = datagram.flip(datagram.getData());
      this.serverManager.addDatagramToQueue(echo.getReceiver(), echo);
    } catch (DatagramMissingSenderReceiverException ex) {
      Logger.getLogger(CLA.class.getName()).log(Level.SEVERE, null, ex);
    }
  }
}
