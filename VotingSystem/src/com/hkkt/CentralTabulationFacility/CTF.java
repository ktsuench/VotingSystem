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
package com.hkkt.CentralTabulationFacility;

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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Hassan Khan
 */
public class CTF extends AbstractServer {
  private final ConcurrentHashMap<Integer, Integer> CROSSED_OFF;
  private final String NAME;
  private final int NUM_VOTERS;
  private final ServerConnectionManager SERVER_MANAGER;
  private final List<Integer> VALIDATION_TICKETS;
  private final ConcurrentHashMap<String, List<Integer>> VOTE_RESULTS;
  private final List<Runnable> TASKS;
  private ClientConnectionManager clientManager;
  private boolean publishingOutcome = false;

  /**
   * Constructs a Central Tabulation Facility
   *
   * @param name CTF unique id
   * @param address the address that the CTF is located at
   * @param numVoters
   * @param ballotOptions
   * @throws ChannelSelectorCannotStartException
   * @throws IOException
   */
  public CTF(String name, InetSocketAddress address, int numVoters, ArrayList<String> ballotOptions) throws ChannelSelectorCannotStartException, IOException {
    this.SERVER_MANAGER = new ServerConnectionManager(address, this);
    this.NAME = name;
    this.NUM_VOTERS = numVoters;
    this.VALIDATION_TICKETS = Collections.synchronizedList(new ArrayList<Integer>());

    this.CROSSED_OFF = new ConcurrentHashMap<>();
    this.VOTE_RESULTS = new ConcurrentHashMap<>();
    this.TASKS = Collections.synchronizedList(new ArrayList<Runnable>());

    ballotOptions.forEach(option -> VOTE_RESULTS.put(option.toUpperCase(), Collections.synchronizedList(new ArrayList<Integer>())));
  }

  public boolean addVoteTally(int idNum, String vote) {
    // System.out.println("Voted for " + vote.toUpperCase());
    if (this.VOTE_RESULTS.containsKey(vote.toUpperCase())) {
      List<Integer> list;
      synchronized (list = this.VOTE_RESULTS.get(vote.toUpperCase())) {
        list.add(idNum);
      }
      return true;
    }

    return false;
  }

  public boolean checkValNum(int valNum) {
    synchronized (this.VALIDATION_TICKETS) {
      return this.VALIDATION_TICKETS.contains(valNum);
    }
  }

  /**
   * Connect to the Central Legitimization
   *
   * @param address the address that the CLA is located at
   * @throws ChannelSelectorCannotStartException
   * @throws IOException
   * @throws com.hkkt.communication.DatagramMissingSenderReceiverException
   */
  public void connectToCLA(InetSocketAddress address) throws ChannelSelectorCannotStartException, IOException, DatagramMissingSenderReceiverException {
    this.clientManager = new ClientConnectionManager(this.NAME, address);
  }

  public void crossValNum(int id, int validNum) {
    this.CROSSED_OFF.put(id, validNum);
    // System.out.println(this.CROSSED_OFF.mappingCount());
  }

  /**
   * Method for CTF to handle incoming data
   *
   * @param datagram data that has been sent to the CTF
   */
  @Override
  public void handleDatagram(Datagram datagram) {
    try {
      if (VotingDatagram.isVotingSystemDatagram(datagram)) {
        VotingDatagram votingDatagram = new VotingDatagram(datagram);
        Datagram response = null;

        switch (votingDatagram.getOperationType()) {
          case SEND_VALIDATION_LIST:
            System.out.println(votingDatagram.getData());

            String[] data = {""};//votingDatagram.getData().split("\\s+");

            synchronized (this.VALIDATION_TICKETS) {
              for (String id : data)
                if (!this.VALIDATION_TICKETS.contains(Integer.parseInt(id)))
                  this.VALIDATION_TICKETS.add(Integer.parseInt(id));

              if (this.VALIDATION_TICKETS.size() == NUM_VOTERS) {
                for (Runnable task : this.TASKS)
                  task.run();
                this.TASKS.clear();
              }
            }
            // response = votingDatagram.flip(data);
            break;
          case SUBMIT_VOTE:
            //String[] arrInfo = votingDatagram.getData().split("\\s+", 3);
            int randIdReceived = 1;//Integer.parseInt(arrInfo[0]);
            int valNumReceived = 1;//Integer.parseInt(arrInfo[1]);
            String voteReceived = "";//arrInfo[2];

            //TODO: remove the val num check after val table is added to ctf
            if (/*checkValNum(valNumReceived) == true &&*/ CROSSED_OFF.containsValue(valNumReceived) == false)
              //validation number is valid and number is not in crossed off list
              if (addVoteTally(randIdReceived, voteReceived)) {
                crossValNum(randIdReceived, valNumReceived);
                // response = votingDatagram.flip(Boolean.toString(true));
              } else
                // response = votingDatagram.flip(Boolean.toString(false));
            // else
              // response = votingDatagram.flip(Boolean.toString(false));

            if (CROSSED_OFF.mappingCount() == this.NUM_VOTERS && !publishingOutcome)
              publishOutcome();
            break;
          default:
            String errorMsg = "Unknown request. CTF cannot handle the requested operation.";
            // response = datagram.flip(errorMsg, Datagram.DATA_TYPE.ERROR);
            break;
        }

        if (response != null)
          this.SERVER_MANAGER.addDatagramToQueue(response.getReceiver(), response);
      }
    } catch (UnsupportedEncodingException | DatagramMissingSenderReceiverException ex) {
      Logger.getLogger(CTF.class.getName()).log(Level.SEVERE, null, ex);
    }
  }

  public boolean isReady() {
    synchronized (this.VALIDATION_TICKETS) {
      return this.VALIDATION_TICKETS.size() == NUM_VOTERS;
    }
  }

  public void printList(List list) {
    for (Object list1 : list)
      System.out.println(list1.toString());
  }

  public void publishOutcome() {
    ArrayList<String> winner = new ArrayList<>();
    int greatestVote = 0;

    publishingOutcome = true;

    for (Map.Entry<String, List<Integer>> entry : this.VOTE_RESULTS.entrySet())
      if (entry.getValue().size() > greatestVote) {
        winner.clear();
        winner.add(entry.getKey());
        greatestVote = entry.getValue().size();
      } else if (entry.getValue().size() == greatestVote)
        winner.add(entry.getKey());

    if (winner.size() == 1)
      System.out.println(winner.get(0) + " has won the election with " + greatestVote + " votes.");
    else {
      Iterator<String> it = winner.iterator();
      String name;

      while (it.hasNext()) {
        name = it.next();

        if (it.hasNext())
          System.out.print(name + ", ");
        else
          System.out.print("and " + name + " ");
      }

      System.out.println("has tied in the election, each with " + greatestVote + " votes.");
    }

    for (Map.Entry<String, List<Integer>> entry : this.VOTE_RESULTS.entrySet()) {
      System.out.println("The ID's of the " + entry.getValue().size() + " people who voted for " + entry.getKey() + " is all follows: ");
      printList(entry.getValue());
    }
  }

  public void whenReady(Runnable task) {
    synchronized (this.TASKS) {
      this.TASKS.add(task);
    }
  }
}
