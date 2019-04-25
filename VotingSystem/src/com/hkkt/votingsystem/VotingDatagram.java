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

import com.hkkt.communication.Datagram;
import static com.hkkt.communication.Datagram.STRING_ENCODING;
import com.hkkt.communication.DatagramMissingSenderReceiverException;
import com.hkkt.communication.ServerConnectionManager;
import java.io.UnsupportedEncodingException;
import java.time.Instant;
import java.util.Objects;

/**
 *
 * @author Kent Tsuenchy
 */
public class VotingDatagram extends Datagram {
  public static VotingDatagram fromBytes(byte[] bytes) throws UnsupportedEncodingException, DatagramMissingSenderReceiverException {
    int pad = ServerConnectionManager.MAX_NAME_LENGTH;
    String temp = new String(bytes, STRING_ENCODING);
    String sender;
    String receiver;
    ACTION_TYPE otherType;
    Instant timestamp;
    byte[] data;

    sender = temp.substring(0, pad).trim();
    receiver = temp.substring(pad, pad * 2).trim();
    pad = pad * 2 + MAX_TYPE_LENGTH;
    otherType = ACTION_TYPE.valueOf(temp.substring(pad, pad + MAX_TYPE_OTHER_LENGTH).trim());
    pad += MAX_TYPE_OTHER_LENGTH;
    timestamp = Instant.parse(temp.substring(pad, pad + MAX_TIMESTAMP_LENGTH).trim());
    pad += MAX_TIMESTAMP_LENGTH;
    data = temp.substring(pad).getBytes(STRING_ENCODING);

    return new VotingDatagram(otherType, sender, receiver, data, timestamp);
  }

  public static boolean isVotingSystemDatagram(byte[] bytes) throws UnsupportedEncodingException {
    int pad = ServerConnectionManager.MAX_NAME_LENGTH * 2 + MAX_TYPE_LENGTH;
    String temp = new String(bytes, STRING_ENCODING);
    String actionType = temp.substring(pad, pad + MAX_TYPE_OTHER_LENGTH).trim();

    return ACTION_TYPE.isValid(actionType);
  }

  public static boolean isVotingSystemDatagram(Datagram datagram) throws UnsupportedEncodingException {
    return ACTION_TYPE.isValid(datagram.getTypeOther());
  }

  protected final ACTION_TYPE OP_TYPE;

  public VotingDatagram(ACTION_TYPE type, String sender, String receiver, byte[] data, Instant timestamp) throws DatagramMissingSenderReceiverException {
    super(Datagram.DATA_TYPE.OTHER, type.toString(), sender, receiver, data, timestamp);
    this.OP_TYPE = type;
  }

  public VotingDatagram(ACTION_TYPE type, String sender, String receiver, byte[] data) throws DatagramMissingSenderReceiverException {
    this(type, sender, receiver, data, Instant.now());
  }

  public VotingDatagram(ACTION_TYPE type, String sender, String receiver) throws DatagramMissingSenderReceiverException {
    this(type, sender, receiver, null, Instant.now());
  }

  public VotingDatagram(Datagram datagram) throws DatagramMissingSenderReceiverException {
    this(ACTION_TYPE.valueOf(datagram.getTypeOther()), datagram.getSender(), datagram.getReceiver(), datagram.getData(), datagram.getTimestamp());
  }

  @Override
  public boolean equals(Object o) {
    boolean equal = false;

    if (o instanceof VotingDatagram) {
      VotingDatagram v = (VotingDatagram) o;

      equal = this.hashCode() == v.hashCode();
    }

    return equal;
  }

  @Override
  public byte[] getBytes() throws UnsupportedEncodingException {
    int pad = ServerConnectionManager.MAX_NAME_LENGTH;
    String temp;

    temp = String.format("%-" + pad + "s%-" + pad + "s", this.SENDER_ID, this.RECEIVER_ID);
    temp += String.format("%-" + MAX_TYPE_LENGTH + "s", this.TYPE.toString());
    temp += String.format("%-" + MAX_TYPE_OTHER_LENGTH + "s", this.OP_TYPE.toString());
    temp += String.format("%-" + MAX_TIMESTAMP_LENGTH + "s", this.TIMESTAMP.toString());
    temp += String.format("%-" + MAX_DATA_LENGTH + "s", new String(this.DATA, STRING_ENCODING));

    return temp.getBytes(STRING_ENCODING);
  }

  public ACTION_TYPE getOperationType() {
    return this.OP_TYPE;
  }

  public VotingDatagram flip(byte[] data, ACTION_TYPE type) throws DatagramMissingSenderReceiverException {
    byte[] d = data == null ? this.DATA : data;
    return new VotingDatagram(type, this.RECEIVER_ID, this.SENDER_ID, d);
  }

  @Override
  public VotingDatagram flip(byte[] data) throws DatagramMissingSenderReceiverException {
    return this.flip(data, this.OP_TYPE);
  }

  @Override
  public int hashCode() {
    int hash = 13;
    hash = 97 * hash + Objects.hashCode(this.DATA);
    hash = 97 * hash + Objects.hashCode(this.RECEIVER_ID);
    hash = 97 * hash + Objects.hashCode(this.SENDER_ID);
    hash = 97 * hash + Objects.hashCode(this.TIMESTAMP);
    hash = 97 * hash + Objects.hashCode(this.TYPE);
    hash = 97 * hash + Objects.hashCode(this.OP_TYPE);
    return hash;
  }

  public static enum ACTION_TYPE {
    REQUEST_VALIDATION_NUM, SUBMIT_VOTE, SEND_VALIDATION_LIST;

    public static boolean isValid(String action) {
      try {
        valueOf(action);
      } catch (IllegalArgumentException ex) {
        return false;
      }

      return true;
    }
  }
}
