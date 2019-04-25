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
import com.hkkt.communication.Encryptor;
import com.hkkt.communication.KDC;
import com.hkkt.util.Hook;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

/**
 *
 * @author Kent Tsuenchy
 */
public class Voter {
  private static final Logger LOG = Logger.getLogger(Voter.class.getName());

  private final ClientConnectionManager CLA_CONN;
  private final Hook CLA_HOOK;
  private final String CLA_NAME;
  private final ClientConnectionManager CTF_CONN;
  private final Hook CTF_HOOK;
  private final String CTF_NAME;
  private final KeyPair ENCRYPTION_KEYS;
  private final String ID;
  private final SecretKey KDC_COMM_KEY;
  private final ConcurrentHashMap<VotingDatagram.ACTION_TYPE, List<Runnable>> TASKS;
  private final int VOTING_ID;
  private boolean requestInProgress;
  private int validationNum;
  private boolean voteSubmitted = false;

  public Voter(String id, InetSocketAddress claAddress, InetSocketAddress ctfAddress, String claName, String ctfName) throws IOException, ChannelSelectorCannotStartException, DatagramMissingSenderReceiverException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, UnsupportedEncodingException, BadPaddingException {
    this.ID = id;
    this.VOTING_ID = (int) (Math.random() * Integer.MAX_VALUE);
    this.TASKS = new ConcurrentHashMap<>();

    this.ENCRYPTION_KEYS = Encryptor.getInstance().genKeyPair();
    this.KDC_COMM_KEY = Encryptor.getInstance().registerWithKDC(id, this.ENCRYPTION_KEYS.getPublic());

    this.CLA_NAME = claName;
    this.CTF_NAME = ctfName;

    byte[] encryptedId = this.encryptData(id.getBytes(Datagram.STRING_ENCODING), claName);
    this.CLA_CONN = new ClientConnectionManager(id, encryptedId, claAddress);
    encryptedId = this.encryptData(id.getBytes(Datagram.STRING_ENCODING), ctfName);
    this.CTF_CONN = new ClientConnectionManager(id, encryptedId, ctfAddress);

    this.CLA_HOOK = new Hook() {
      private VotingDatagram data;

      @Override
      public void run() {
        try {
          boolean error = false;
          VotingDatagram.ACTION_TYPE action = this.data.getOperationType();
          // System.out.println(ID + " received message from " + this.data.getSender() + ": " + this.data.getData());

          switch (action) {
            case REQUEST_VALIDATION_NUM:
              String decryptedData = new String(decryptData(this.data.getData()), Datagram.STRING_ENCODING);

              System.out.println("Received encrypted validation number from CLA for " + ID + ":\n" + new String(this.data.getData(), Datagram.STRING_ENCODING));
              System.out.println(ID + " decrypted validation number: " + decryptedData);

              validationNum = Integer.parseInt(decryptedData);
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
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException | UnsupportedEncodingException ex) {
          Logger.getLogger(Voter.class.getName()).log(Level.SEVERE, null, ex);
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
        try {
          boolean error = false;
          VotingDatagram.ACTION_TYPE action = this.data.getOperationType();
          // System.out.println(ID + " received message from " + this.data.getSender() + ": " + this.data.getData());

          switch (action) {
            case SUBMIT_VOTE:
              String decryptedData = new String(decryptData(this.data.getData()), Datagram.STRING_ENCODING);
              voteSubmitted = Boolean.parseBoolean(decryptedData);

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
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException | UnsupportedEncodingException ex) {
          Logger.getLogger(Voter.class.getName()).log(Level.SEVERE, null, ex);
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

  public void submitVote(String vote) throws DatagramMissingSenderReceiverException, UnsupportedEncodingException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
    String data = this.VOTING_ID + " " + this.validationNum + " " + vote;
    byte[] encryptedData = this.encryptData(data.getBytes(Datagram.STRING_ENCODING), this.CTF_NAME);

    System.out.println(this.ID + " submitting vote to CTF with data as:\n" + data + "\nand encrypted data as:\n" + new String(encryptedData, Datagram.STRING_ENCODING));

    this.CTF_CONN.sendRequest(VotingDatagram.ACTION_TYPE.SUBMIT_VOTE.toString(), null, encryptedData);
  }

  public void submitVote(int vote) throws DatagramMissingSenderReceiverException, UnsupportedEncodingException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
    this.submitVote(Integer.toString(vote));
  }

  public void requestForValidationNum() throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, UnsupportedEncodingException, BadPaddingException, DatagramMissingSenderReceiverException {
    byte[] encryptedId = this.encryptData(this.ID.getBytes(Datagram.STRING_ENCODING), this.CLA_NAME);

    System.out.println(this.ID + " sending request to CLA for validation number with data encrypted:\n" + new String(encryptedId, Datagram.STRING_ENCODING));

    this.CLA_CONN.sendRequest(VotingDatagram.ACTION_TYPE.REQUEST_VALIDATION_NUM.toString(), null, encryptedId);
  }

  public void whenFree(Runnable task, VotingDatagram.ACTION_TYPE action) {
    this.TASKS.computeIfAbsent(action, key -> Collections.synchronizedList(new ArrayList<>())).add(task);
  }

  private byte[] decryptData(byte[] encryptedData) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, UnsupportedEncodingException, IllegalBlockSizeException, BadPaddingException {
    // length of encrypted data is 256, but in datagram the data (bytes) gets converted to string and length limit
    // is applied to string instead of bytes, string limit is 300 currently (trimming causes the byte data to be
    // malformed since some of those bytes at start/end may look like spaces in string format)
    byte[] data = Arrays.copyOf(encryptedData, 256);
    return Encryptor.getInstance().encryptDecryptData(Cipher.DECRYPT_MODE, data, this.ENCRYPTION_KEYS.getPrivate());
  }

  private byte[] encryptData(byte[] plainData, String receiver) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, UnsupportedEncodingException, BadPaddingException {
    byte[] encryptedKey = KDC.getInstance().getKey(this.ID, receiver);
    PublicKey key = (PublicKey) Encryptor.getInstance().decryptKey(encryptedKey, this.KDC_COMM_KEY, "RSA", Cipher.PUBLIC_KEY);
    return Encryptor.getInstance().encryptDecryptData(Cipher.ENCRYPT_MODE, plainData, key);
  }
}
