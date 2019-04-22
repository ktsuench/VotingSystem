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
import com.hkkt.communication.DatagramMissingSenderReceiverException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Hassan Khan
 */
public class VotingSystem {
  private final ArrayList<String> BALLOT_OPTIONS;
  private final InetSocketAddress CLA_ADDRESS;
  private final CLA CLA_FACILITY;
  private final InetSocketAddress CTF_ADDRESS;
  private final CTF CTF_FACILITY;
  private final int MAX_VOTERS;
  private final ArrayList<Voter> VOTERS;
  private final List<Runnable> TASKS;

  public VotingSystem(int numVoters, ArrayList<String> ballotOptions, InetSocketAddress claAddress, InetSocketAddress ctfAddress) throws ChannelSelectorCannotStartException, IOException, DatagramMissingSenderReceiverException {
    this.MAX_VOTERS = numVoters;

    this.CLA_FACILITY = new CLA("cla", claAddress, numVoters);
    this.CTF_FACILITY = new CTF("ctf", ctfAddress, numVoters, ballotOptions);

    this.CLA_ADDRESS = claAddress;
    this.CTF_ADDRESS = ctfAddress;

    this.CLA_FACILITY.connectToCTF(ctfAddress);
    this.CTF_FACILITY.connectToCLA(claAddress);

    this.VOTERS = new ArrayList<>();
    this.BALLOT_OPTIONS = ballotOptions;

    this.TASKS = Collections.synchronizedList(new ArrayList<Runnable>());

    this.CTF_FACILITY.whenReady(() -> {
      System.out.println("Voting system tasks: " + TASKS.size());
      for (Runnable task : TASKS)
        task.run();
      TASKS.clear();
    });
  }

  public boolean addVoter(String name) throws IOException, ChannelSelectorCannotStartException, DatagramMissingSenderReceiverException {
    if (this.VOTERS.size() < this.MAX_VOTERS + 1) {
      this.VOTERS.add(new Voter(name, this.CLA_ADDRESS, this.CTF_ADDRESS));
      return true;
    }

    return false;
  }

  public ArrayList<String> getBallotOptions() {
    return this.BALLOT_OPTIONS;
  }

  public ArrayList<Voter> getVoters() {
    return this.VOTERS;
  }

  public void whenSystemReady(Runnable task) {
    if (!this.CTF_FACILITY.isReady())
      synchronized (this.TASKS) {
        this.TASKS.add(task);
      }
    else
      task.run();
  }

  /**
   * @param args the command line arguments
   * @throws java.io.IOException
   * @throws com.hkkt.communication.ChannelSelectorCannotStartException
   * @throws com.hkkt.communication.DatagramMissingSenderReceiverException
   */
  public static void main(String[] args) throws IOException, ChannelSelectorCannotStartException, DatagramMissingSenderReceiverException {
    // TODO code application logic here
    InetSocketAddress claAddress = new InetSocketAddress("localhost", 5000);
    InetSocketAddress ctfAddress = new InetSocketAddress("localhost", 6000);
    ArrayList<String> ballotOptions = new ArrayList<>();
    int maxVoters = 500;

    ballotOptions.add("HITLER");
    ballotOptions.add("STALIN");
    ballotOptions.add("MUSSOLINI");
    ballotOptions.add("NAPOLEAN");
    ballotOptions.add("JULIUS");

    VotingSystem system = new VotingSystem(maxVoters, ballotOptions, claAddress, ctfAddress);

    for (int i = 0; i < maxVoters; i++)
      system.addVoter("voter" + i);

    system.getVoters().forEach(v -> {
      v.whenFree(() -> {
        system.whenSystemReady(() -> {
          try {
            v.submitVote(ballotOptions.get((int) (Math.random() * ballotOptions.size())));
          } catch (DatagramMissingSenderReceiverException ex) {
            Logger.getLogger(VotingSystem.class.getName()).log(Level.SEVERE, null, ex);
          }
        });
      }, VotingDatagram.ACTION_TYPE.REQUEST_VALIDATION_NUM);
    });
  }
}
