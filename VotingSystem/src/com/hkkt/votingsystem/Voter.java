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

import com.hkkt.communication.ChannelSelectorCannotStartException;
import com.hkkt.communication.ClientConnectionManager;
import com.hkkt.communication.Datagram;
import com.hkkt.communication.DatagramMissingSenderReceiverException;
import com.hkkt.util.Hook;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Kent Tsuenchy
 */
public class Voter {
  private static final Logger LOG = Logger.getLogger(Voter.class.getName());

  private final ClientConnectionManager CLA_CONN;
  private final Hook CLA_HOOK;
  private final ClientConnectionManager CTF_CONN;
  private final Hook CTF_HOOK;
  private final String ID;
  private final int VOTING_ID;
  private boolean requestInProgress;
  private int validationNum;
  private boolean voteSubmitted = false;
  private final ConcurrentHashMap<VotingDatagram.ACTION_TYPE, ArrayList<Runnable>> TASKS;

  public Voter(String id, InetSocketAddress claAddress, InetSocketAddress ctfAddress) throws IOException, ChannelSelectorCannotStartException, DatagramMissingSenderReceiverException {
    this.ID = id;
    this.VOTING_ID = (int) (Math.random() * Integer.MAX_VALUE);

    this.TASKS = new ConcurrentHashMap<>();

    this.CLA_CONN = new ClientConnectionManager(id, claAddress);
    this.CTF_CONN = new ClientConnectionManager(id, ctfAddress);

    this.CLA_HOOK = new Hook() {
      private VotingDatagram data;

      @Override
      public void run() {
        boolean error = false;
        VotingDatagram.ACTION_TYPE action = this.data.getOperationType();
        // System.out.println(ID + " received message from " + this.data.getSender() + ": " + this.data.getData());

        switch (action) {
          case REQUEST_VALIDATION_NUM:
            validationNum = Integer.parseInt(this.data.getData());
            CLA_CONN.cleanup();
            break;
          default:
            String errorMsg = "Unknown response. " + ID + " cannot handle the response obtained from CLA.";
            error = true;
            LOG.log(Level.SEVERE, errorMsg);
            break;
        }

        if (!error && TASKS.containsKey(action)) {
          TASKS.get(action).forEach(task -> task.run());
          TASKS.remove(action);
        }

        requestInProgress = false;
      }

      @Override
      public void setHookData(Object data) {
        if (data instanceof Datagram)
          try {
            this.data = new VotingDatagram((Datagram) data);
          } catch (DatagramMissingSenderReceiverException ex) {
            LOG.log(Level.SEVERE, null, ex);
          }
      }
    };

    this.CTF_HOOK = new Hook() {
      private VotingDatagram data;

      @Override
      public void run() {
        boolean error = false;
        VotingDatagram.ACTION_TYPE action = this.data.getOperationType();
        // System.out.println(ID + " received message from " + this.data.getSender() + ": " + this.data.getData());

        switch (action) {
          case SUBMIT_VOTE:
            voteSubmitted = Boolean.parseBoolean(this.data.getData());

            if (voteSubmitted)
              CTF_CONN.cleanup();
            break;
          default:
            String errorMsg = "Unknown response. " + ID + " cannot handle the response obtained from CTF.";
            error = true;
            LOG.log(Level.SEVERE, errorMsg);
            break;
        }

        if (!error && TASKS.containsKey(action)) {
          TASKS.get(action).forEach(task -> task.run());
          TASKS.remove(action);
        }
        requestInProgress = false;
      }

      @Override
      public void setHookData(Object data) {
        if (data instanceof Datagram)
          try {
            this.data = new VotingDatagram((Datagram) data);
          } catch (DatagramMissingSenderReceiverException ex) {
            LOG.log(Level.SEVERE, null, ex);
          }
      }
    };

    this.CLA_CONN.addHook(CLA_HOOK);
    this.CTF_CONN.addHook(CTF_HOOK);

    this.requestInProgress = true;
    this.CLA_CONN.sendRequest(VotingDatagram.ACTION_TYPE.REQUEST_VALIDATION_NUM.toString(), null, this.ID);
  }

  public void cleanup() {
    this.CLA_CONN.cleanup();
    this.CTF_CONN.cleanup();
  }

  @Override
  public boolean equals(Object o) {
    boolean equal = false;

    if (o instanceof Voter) {
      Voter v = (Voter) o;

      equal = this.hashCode() == v.hashCode();
    }

    return equal;
  }

  @Override
  public int hashCode() {
    int hash = 17;
    hash = 67 * hash + Objects.hashCode(this.CLA_CONN);
    hash = 67 * hash + Objects.hashCode(this.CLA_HOOK);
    hash = 67 * hash + Objects.hashCode(this.CTF_CONN);
    hash = 67 * hash + Objects.hashCode(this.CTF_HOOK);
    hash = 67 * hash + Objects.hashCode(this.ID);
    hash = 67 * hash + Objects.hashCode(this.VOTING_ID);
    hash = 67 * hash + Objects.hashCode(this.validationNum);
    return hash;
  }

  public boolean isBusy() {
    return this.requestInProgress;
  }

  public boolean isVoteSubmitted() {
    return this.voteSubmitted;
  }

  public void whenFree(Runnable task, VotingDatagram.ACTION_TYPE action) {
    this.TASKS.computeIfAbsent(action, key -> new ArrayList<>()).add(task);
  }

  public void submitVote(String vote) throws DatagramMissingSenderReceiverException {
    String data = this.VOTING_ID + " " + this.validationNum + " " + vote;
    this.CTF_CONN.sendRequest(VotingDatagram.ACTION_TYPE.SUBMIT_VOTE.toString(), null, data);
  }

  public void submitVote(int vote) throws DatagramMissingSenderReceiverException {
    this.submitVote(Integer.toString(vote));
  }
}
