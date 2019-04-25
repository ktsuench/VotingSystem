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
import com.hkkt.util.Encryptor;
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
import java.util.Arrays;
import java.util.Iterator;
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
public class CLA extends AbstractServer {
  public static final String VALIDATION_TICKET_DELIMETER = " ";
  private static final int VALIDATION_NUM_LIMIT = Integer.MAX_VALUE;
  private final KeyPair ENCRYPTION_KEYS;
  private final SecretKey KDC_COMM_KEY;
  private final String NAME;
  private final int NUM_VOTERS;
  private final ServerConnectionManager SERVER_MANAGER;
  private final ConcurrentHashMap<String, Integer> VALIDATION_TICKETS;
  private String ctfName;
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
   * @throws java.security.NoSuchAlgorithmException
   * @throws javax.crypto.NoSuchPaddingException
   * @throws java.security.InvalidKeyException
   * @throws javax.crypto.IllegalBlockSizeException
   * @throws java.io.UnsupportedEncodingException
   * @throws javax.crypto.BadPaddingException
   */
  public CLA(String name, InetSocketAddress address, int numVoters) throws ChannelSelectorCannotStartException, IOException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, UnsupportedEncodingException, BadPaddingException {
    this.SERVER_MANAGER = new ServerConnectionManager(address, this);

    this.NAME = name;
    this.NUM_VOTERS = numVoters;
    this.VALIDATION_TICKETS = new ConcurrentHashMap<>();
    this.ENCRYPTION_KEYS = Encryptor.getInstance().genKeyPair();
    this.KDC_COMM_KEY = Encryptor.getInstance().registerWithKDC(name, this.ENCRYPTION_KEYS.getPublic());
  }

  /**
   * Connect to the Central Tabulation Facility
   *
   * @param name
   * @param address the address that the CTF is located at
   * @throws ChannelSelectorCannotStartException
   * @throws IOException
   * @throws com.hkkt.communication.DatagramMissingSenderReceiverException
   * @throws java.security.NoSuchAlgorithmException
   * @throws javax.crypto.NoSuchPaddingException
   * @throws java.security.InvalidKeyException
   * @throws java.io.UnsupportedEncodingException
   * @throws javax.crypto.IllegalBlockSizeException
   * @throws javax.crypto.BadPaddingException
   */
  public void connectToCTF(String name, InetSocketAddress address) throws ChannelSelectorCannotStartException, IOException, DatagramMissingSenderReceiverException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, UnsupportedEncodingException, IllegalBlockSizeException, BadPaddingException {
    byte[] nameBytes = this.NAME.getBytes(Datagram.STRING_ENCODING);
    this.ctfName = name;
    this.clientManager = new ClientConnectionManager(this.NAME, this.encryptData(nameBytes, name), address);
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
        byte[] dataToEncrypt, data;

        switch (votingDatagram.getOperationType()) {
          case REQUEST_VALIDATION_NUM:
            String decryptedData = new String(decryptData(votingDatagram.getData()), Datagram.STRING_ENCODING);
            dataToEncrypt = Integer.toString(this.generateValidationTicket(decryptedData)).getBytes(Datagram.STRING_ENCODING);
            data = encryptData(dataToEncrypt, votingDatagram.getSender());

            System.out.println("CLA recevied request for validation number with encrypted data from " + votingDatagram.getSender() + ":\n" + new String(votingDatagram.getData(), Datagram.STRING_ENCODING));
            System.out.println("CLA " + votingDatagram.getSender() + " decrypted validation number request:\n" + decryptedData);
            System.out.println("CLA " + votingDatagram.getSender() + " validation number request:" + new String(dataToEncrypt, Datagram.STRING_ENCODING));
            System.out.println("CLA " + votingDatagram.getSender() + " encrypted validation number request:\n" + new String(data, Datagram.STRING_ENCODING));

            response = votingDatagram.flip(data);
            this.sendValidationTicketListToCTF();
            break;
          case SEND_VALIDATION_LIST:
            // do nothing
            break;
          default:
            dataToEncrypt = "Unknown request. CLA cannot handle the requested operation.".getBytes(Datagram.STRING_ENCODING);
            data = encryptData(dataToEncrypt, votingDatagram.getSender());
            response = datagram.flip(data, Datagram.DATA_TYPE.ERROR);
            break;
        }

        if (response != null)
          this.SERVER_MANAGER.addDatagramToQueue(response.getReceiver(), response);
      }
    } catch (UnsupportedEncodingException | DatagramMissingSenderReceiverException | NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException ex) {
      Logger.getLogger(CLA.class.getName()).log(Level.SEVERE, null, ex);
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

  private void sendValidationTicketListToCTF() throws UnsupportedEncodingException, DatagramMissingSenderReceiverException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
    String list = "", nextInt;
    byte[] data;
    Iterator<Integer> validationTickets;

    if (!this.sentValidationTicketsToCTF && this.VALIDATION_TICKETS.mappingCount() == this.NUM_VOTERS) {
      this.sentValidationTicketsToCTF = true;

      validationTickets = this.VALIDATION_TICKETS.values().iterator();

      while (validationTickets.hasNext()) {
        nextInt = validationTickets.next() + VALIDATION_TICKET_DELIMETER;

        // encrypted data length has to be less then 256 (some bytes used for header)
        if (list.getBytes(Datagram.STRING_ENCODING).length + nextInt.getBytes(Datagram.STRING_ENCODING).length < 245)
          list += nextInt;
        else {
          list = list.substring(0, list.length() - 1);
          data = this.encryptData(list.getBytes(Datagram.STRING_ENCODING), this.ctfName);

          System.out.println("CLA sending batch of validation numbers to CTF:\n" + list + "\nand encryped as:\n" + new String(data, Datagram.STRING_ENCODING));

          clientManager.sendRequest(VotingDatagram.ACTION_TYPE.SEND_VALIDATION_LIST.toString(), null, data);
          list = nextInt;

        }

        if (!validationTickets.hasNext()) {
          list = list.substring(0, list.length() - 1);
          data = this.encryptData(list.getBytes(Datagram.STRING_ENCODING), this.ctfName);
          clientManager.sendRequest(VotingDatagram.ACTION_TYPE.SEND_VALIDATION_LIST.toString(), null, data);

          System.out.println("CLA sending batch of validation numbers to CTF:\n" + list + "\nand encryped as:\n" + new String(data, Datagram.STRING_ENCODING));
        }
      }
    }
  }
}
