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

import com.hkkt.CentralLegitimizationAgency.CLA;
import com.hkkt.communication.ChannelSelectorCannotStartException;
import com.hkkt.communication.ClientConnectionManager;
import com.hkkt.communication.Datagram;
import com.hkkt.communication.DatagramMissingSenderReceiverException;
import com.hkkt.communication.Encryptor;
import com.hkkt.communication.KDC;
import com.hkkt.communication.ServerConnectionManager;
import com.hkkt.votingsystem.AbstractServer;
import com.hkkt.votingsystem.VotingDatagram;
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
 * @author Hassan Khan
 */
public class CTF extends AbstractServer {
  private final ConcurrentHashMap<Integer, Integer> CROSSED_OFF;
  private final KeyPair ENCRYPTION_KEYS;
  private final SecretKey KDC_COMM_KEY;
  private final String NAME;
  private final int NUM_VOTERS;
  private final ServerConnectionManager SERVER_MANAGER;
  private final List<Runnable> TASKS;
  private final List<Integer> VALIDATION_TICKETS;
  private final ConcurrentHashMap<String, List<Integer>> VOTE_RESULTS;
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
   * @throws java.security.NoSuchAlgorithmException
   * @throws javax.crypto.NoSuchPaddingException
   * @throws java.security.InvalidKeyException
   * @throws java.io.UnsupportedEncodingException
   * @throws javax.crypto.IllegalBlockSizeException
   * @throws javax.crypto.BadPaddingException
   */
  public CTF(String name, InetSocketAddress address, int numVoters, ArrayList<String> ballotOptions) throws ChannelSelectorCannotStartException, IOException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, UnsupportedEncodingException, BadPaddingException {
    this.SERVER_MANAGER = new ServerConnectionManager(address, this);
    this.NAME = name;
    this.NUM_VOTERS = numVoters;
    this.VALIDATION_TICKETS = Collections.synchronizedList(new ArrayList<Integer>());
    this.ENCRYPTION_KEYS = Encryptor.getInstance().genKeyPair();
    this.KDC_COMM_KEY = Encryptor.getInstance().registerWithKDC(name, this.ENCRYPTION_KEYS.getPublic());

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
  public void connectToCLA(String name, InetSocketAddress address) throws ChannelSelectorCannotStartException, IOException, DatagramMissingSenderReceiverException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, UnsupportedEncodingException, IllegalBlockSizeException, BadPaddingException {
    byte[] nameBytes = this.NAME.getBytes(Datagram.STRING_ENCODING);
    this.clientManager = new ClientConnectionManager(this.NAME, this.encryptData(nameBytes, name), address);
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
        byte[] dataToEncrypt, data;
        String decryptedData;

        switch (votingDatagram.getOperationType()) {
          case SEND_VALIDATION_LIST:
            decryptedData = new String(decryptData(votingDatagram.getData()), Datagram.STRING_ENCODING);
            String[] ids = decryptedData.split("\\s+");

            System.out.println(decryptedData);

            synchronized (this.VALIDATION_TICKETS) {
              for (String id : ids)
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
            decryptedData = new String(decryptData(votingDatagram.getData()), Datagram.STRING_ENCODING);

            String[] arrInfo = decryptedData.split("\\s+", 3);
            int randIdReceived = Integer.parseInt(arrInfo[0]);
            int valNumReceived = Integer.parseInt(arrInfo[1]);
            String voteReceived = arrInfo[2];

            //TODO: remove the val num check after val table is added to ctf
            if (/*checkValNum(valNumReceived) == true &&*/CROSSED_OFF.containsValue(valNumReceived) == false)
              //validation number is valid and number is not in crossed off list
              if (addVoteTally(randIdReceived, voteReceived)) {
                crossValNum(randIdReceived, valNumReceived);

                dataToEncrypt = Boolean.toString(true).getBytes(Datagram.STRING_ENCODING);
                data = encryptData(dataToEncrypt, votingDatagram.getSender());
                response = votingDatagram.flip(data);
              } else {
                dataToEncrypt = Boolean.toString(false).getBytes(Datagram.STRING_ENCODING);
                data = encryptData(dataToEncrypt, votingDatagram.getSender());
                response = votingDatagram.flip(data);
              }
            else {
              dataToEncrypt = Boolean.toString(false).getBytes(Datagram.STRING_ENCODING);
              data = encryptData(dataToEncrypt, votingDatagram.getSender());
              response = votingDatagram.flip(data);
            }

            if (CROSSED_OFF.mappingCount() == this.NUM_VOTERS && !publishingOutcome)
              publishOutcome();
            break;
          default:
            dataToEncrypt = "Unknown request. CTF cannot handle the requested operation.".getBytes(Datagram.STRING_ENCODING);
            data = encryptData(dataToEncrypt, votingDatagram.getSender());
            response = datagram.flip(data, Datagram.DATA_TYPE.ERROR);
            break;
        }

        if (response != null)
          this.SERVER_MANAGER.addDatagramToQueue(response.getReceiver(), response);
      }
    } catch (UnsupportedEncodingException | DatagramMissingSenderReceiverException | NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException ex) {
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

  @Override
  public void updateConnectionName(String name, Datagram datagram) {
    try {
      String newName = new String(this.decryptData(datagram.getData()), Datagram.STRING_ENCODING);
      this.SERVER_MANAGER.updateChannelId(name, newName);
    } catch (UnsupportedEncodingException | NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException ex) {
      Logger.getLogger(CLA.class.getName()).log(Level.SEVERE, null, ex);
    }
  }

  public void whenReady(Runnable task) {
    synchronized (this.TASKS) {
      this.TASKS.add(task);
    }
  }

  private byte[] decryptData(byte[] encryptedData) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, UnsupportedEncodingException, IllegalBlockSizeException, BadPaddingException {
    // length of encrypted data is 256, but in datagram the data (bytes) gets converted to string and length limit
    // is applied to string instead of bytes, string limit is 300 currently (trimming causes the byte data to be
    // malformed since some of those bytes at start/end may look like spaces in string format)
    byte[] data = Arrays.copyOf(encryptedData, 256);
    return Encryptor.getInstance().encryptDecryptData(Cipher.DECRYPT_MODE, data, this.ENCRYPTION_KEYS.getPrivate());
  }

  private byte[] encryptData(byte[] plainData, String receiver) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, UnsupportedEncodingException, BadPaddingException {
    byte[] encryptedKey = KDC.getInstance().getKey(this.NAME, receiver);
    PublicKey key = (PublicKey) Encryptor.getInstance().decryptKey(encryptedKey, this.KDC_COMM_KEY, "RSA", Cipher.PUBLIC_KEY);
    return Encryptor.getInstance().encryptDecryptData(Cipher.ENCRYPT_MODE, plainData, key);
  }
}
