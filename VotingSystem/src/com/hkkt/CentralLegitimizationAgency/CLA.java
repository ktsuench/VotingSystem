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
import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Hassan Khan
 */
public class CLA extends AbstractServer {
  public static final String VALIDATION_TICKET_DELIMETER = " ";
  private static final int VALIDATION_NUM_LIMIT = Integer.MAX_VALUE;
  private final String NAME;
  private final int NUM_VOTERS;
  private final ServerConnectionManager SERVER_MANAGER;
  private final ConcurrentHashMap<String, Integer> VALIDATION_TICKETS;
  private ClientConnectionManager clientManager;
  private boolean sentValidationTicketsToCTF = false;

  /**
   * Constructs a Central Legitimization Agency
   *
   * @param name CLA unique id
   * @param address the address that the CLA is located at
   * @param numVoters
   * @throws ChannelSelectorCannotStartException
   * @throws IOException
   */
  public CLA(String name, InetSocketAddress address, int numVoters) throws ChannelSelectorCannotStartException, IOException {
    this.SERVER_MANAGER = new ServerConnectionManager(address, this);
    this.NAME = name;
    this.NUM_VOTERS = numVoters;
    this.VALIDATION_TICKETS = new ConcurrentHashMap<>();
  }

  /**
   * Connect to the Central Tabulation Facility
   *
   * @param address the address that the CTF is located at
   * @throws ChannelSelectorCannotStartException
   * @throws IOException
   * @throws com.hkkt.communication.DatagramMissingSenderReceiverException
   */
  public void connectToCTF(InetSocketAddress address) throws ChannelSelectorCannotStartException, IOException, DatagramMissingSenderReceiverException {
    this.clientManager = new ClientConnectionManager(this.NAME, address);
  }

  /**
   * Method for CLA to handle incoming data
   *
   * @param datagram data that has been sent to the CLA
   */
  @Override
  public void handleDatagram(Datagram datagram) {
    try {
      if (VotingDatagram.isVotingSystemDatagram(datagram)) {
        VotingDatagram votingDatagram = new VotingDatagram(datagram);
        Datagram response = null;

        switch (votingDatagram.getOperationType()) {
          case REQUEST_VALIDATION_NUM:
            String data = Integer.toString(this.generateValidationTicket(votingDatagram.getData()));
            response = votingDatagram.flip(data);
            this.sendValidationTicketListToCTF();
            break;
          case SEND_VALIDATION_LIST:
            // do nothing
            break;
          default:
            String errorMsg = "Unknown request. CLA cannot handle the requested operation.";
            response = datagram.flip(errorMsg, Datagram.DATA_TYPE.ERROR);
            break;
        }

        if (response != null)
          this.SERVER_MANAGER.addDatagramToQueue(response.getReceiver(), response);
      }
    } catch (UnsupportedEncodingException | DatagramMissingSenderReceiverException ex) {
      Logger.getLogger(CLA.class.getName()).log(Level.SEVERE, null, ex);
    }
  }

  /**
   * Generate a new validation ticket for a voter
   *
   * @param voter id
   * @return validation ticket if voter not registered, otherwise -1
   */
  private int generateValidationTicket(String voter) {
    int validationTicket = (int) (Math.random() * VALIDATION_NUM_LIMIT);
    boolean alreadyRegistered = this.VALIDATION_TICKETS.containsKey(voter);

    while (this.VALIDATION_TICKETS.containsValue(validationTicket))
      validationTicket = (int) (Math.random() * VALIDATION_NUM_LIMIT);

    if (!alreadyRegistered)
      this.VALIDATION_TICKETS.put(voter, validationTicket);

    return alreadyRegistered ? -1 : validationTicket;
  }

  private void sendValidationTicketListToCTF() throws UnsupportedEncodingException, DatagramMissingSenderReceiverException {
    String data = "", nextInt;
    Iterator<Integer> validationTickets;

    if (!this.sentValidationTicketsToCTF && this.VALIDATION_TICKETS.mappingCount() == this.NUM_VOTERS) {
      this.sentValidationTicketsToCTF = true;

      validationTickets = this.VALIDATION_TICKETS.values().iterator();

      while (validationTickets.hasNext()) {
        nextInt = validationTickets.next() + VALIDATION_TICKET_DELIMETER;

        if (data.getBytes(Datagram.STRING_ENCODING).length + nextInt.getBytes(Datagram.STRING_ENCODING).length < Datagram.MAX_DATA_LENGTH)
          data += nextInt;
        else {
          data = data.substring(0, data.length() - 1);
          clientManager.sendRequest(VotingDatagram.ACTION_TYPE.SEND_VALIDATION_LIST.toString(), null, data);
          data = nextInt;
        }

        if (!validationTickets.hasNext()) {
          data = data.substring(0, data.length() - 1);
          clientManager.sendRequest(VotingDatagram.ACTION_TYPE.SEND_VALIDATION_LIST.toString(), null, data);
        }
      }
    }
  }
}
