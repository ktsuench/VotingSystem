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
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

/**
 *
 * @author Hassan Khan
 */
public class VotingSystem {
  private static final String CLA_NAME = "cla";
  private static final String CTF_NAME = "ctf";
  private final ArrayList<String> BALLOT_OPTIONS;
  private final InetSocketAddress CLA_ADDRESS;
  private final CLA CLA_FACILITY;
  private final InetSocketAddress CTF_ADDRESS;
  private final CTF CTF_FACILITY;
  private final int MAX_VOTERS;
  private final ArrayList<Voter> VOTERS;
  private final List<Runnable> TASKS;

  public VotingSystem(int numVoters, ArrayList<String> ballotOptions, InetSocketAddress claAddress, InetSocketAddress ctfAddress) throws ChannelSelectorCannotStartException, IOException, DatagramMissingSenderReceiverException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, UnsupportedEncodingException, BadPaddingException {
    this.MAX_VOTERS = numVoters;

    this.CLA_FACILITY = new CLA(CLA_NAME, claAddress, numVoters);
    this.CTF_FACILITY = new CTF(CTF_NAME, ctfAddress, numVoters, ballotOptions);

    this.CLA_ADDRESS = claAddress;
    this.CTF_ADDRESS = ctfAddress;

    this.CLA_FACILITY.connectToCTF(CTF_NAME, ctfAddress);
    this.CTF_FACILITY.connectToCLA(CLA_NAME, claAddress);

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

  public boolean addVoter(String name) throws IOException, ChannelSelectorCannotStartException, DatagramMissingSenderReceiverException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, UnsupportedEncodingException, BadPaddingException {
    if (this.VOTERS.size() < this.MAX_VOTERS + 1) {
      this.VOTERS.add(new Voter(name, this.CLA_ADDRESS, this.CTF_ADDRESS, CLA_NAME, CTF_NAME));
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
   * @throws java.security.NoSuchAlgorithmException
   * @throws javax.crypto.NoSuchPaddingException
   * @throws java.security.InvalidKeyException
   * @throws javax.crypto.IllegalBlockSizeException
   * @throws java.io.UnsupportedEncodingException
   * @throws javax.crypto.BadPaddingException
   */
  public static void main(String[] args) throws IOException, ChannelSelectorCannotStartException, DatagramMissingSenderReceiverException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, UnsupportedEncodingException, BadPaddingException, InterruptedException {
    // TODO code application logic here
    InetSocketAddress claAddress = new InetSocketAddress("localhost", 5000);
    InetSocketAddress ctfAddress = new InetSocketAddress("localhost", 6000);
    ArrayList<String> ballotOptions = new ArrayList<>();
    int maxVoters = 1;

    ballotOptions.add("HITLER");
    ballotOptions.add("STALIN");
    ballotOptions.add("MUSSOLINI");
    ballotOptions.add("NAPOLEAN");
    ballotOptions.add("JULIUS");

    VotingSystem system = new VotingSystem(maxVoters, ballotOptions, claAddress, ctfAddress);

    Thread.sleep(500);

    for (int i = 0; i < maxVoters; i++)
      system.addVoter("voter" + i);

    system.getVoters().forEach(v -> {
      v.whenFree(() -> {
        system.whenSystemReady(() -> {
          try {
            v.submitVote(ballotOptions.get((int) (Math.random() * ballotOptions.size())));
          } catch (DatagramMissingSenderReceiverException | UnsupportedEncodingException | NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException ex) {
            Logger.getLogger(VotingSystem.class.getName()).log(Level.SEVERE, null, ex);
          }
        });
      }, VotingDatagram.ACTION_TYPE.REQUEST_VALIDATION_NUM);
    });
  }
}
