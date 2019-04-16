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
public class VotingSystemDatagram extends Datagram {
  public static Datagram fromBytes(byte[] bytes) throws UnsupportedEncodingException, DatagramMissingSenderReceiverException {
    int pad = ServerConnectionManager.MAX_NAME_LENGTH;
    String temp = new String(bytes, STRING_ENCODING);
    String sender;
    String receiver;
    DATA_TYPE type;
    ACTION_TYPE otherType;
    Instant timestamp;
    String data;

    sender = temp.substring(0, pad).trim();
    receiver = temp.substring(pad, pad * 2).trim();
    type = DATA_TYPE.valueOf(temp.substring(pad * 2, pad * 2 + MAX_TYPE_LENGTH).trim());
    pad = pad * 2 + MAX_TYPE_LENGTH;
    otherType = ACTION_TYPE.valueOf(temp.substring(pad, pad + MAX_TYPE_OTHER_LENGTH).trim());
    pad += MAX_TYPE_OTHER_LENGTH;
    timestamp = Instant.parse(temp.substring(pad, pad + MAX_TIMESTAMP_LENGTH).trim());
    pad += MAX_TIMESTAMP_LENGTH;
    data = temp.substring(pad).trim();

    return new VotingSystemDatagram(otherType, sender, receiver, data, timestamp);
  }

  protected final ACTION_TYPE OP_TYPE;

  public VotingSystemDatagram(ACTION_TYPE type, String sender, String receiver, String data, Instant timestamp) throws DatagramMissingSenderReceiverException {
    super(Datagram.DATA_TYPE.OTHER, type.toString(), sender, receiver, data, timestamp);
    this.OP_TYPE = type;
  }

  public VotingSystemDatagram(ACTION_TYPE type, String sender, String receiver, String data) throws DatagramMissingSenderReceiverException {
    this(type, sender, receiver, data, Instant.now());
  }

  public VotingSystemDatagram(ACTION_TYPE type, String sender, String receiver) throws DatagramMissingSenderReceiverException {
    this(type, sender, receiver, null, Instant.now());
  }

  @Override
  public boolean equals(Object o) {
    boolean equal = false;

    if (o instanceof VotingSystemDatagram) {
      VotingSystemDatagram v = (VotingSystemDatagram) o;

      equal = this.hashCode() == v.hashCode();
    }

    return equal;
  }

  public byte[] getBytes() throws UnsupportedEncodingException {
    int pad = ServerConnectionManager.MAX_NAME_LENGTH;
    String temp;

    temp = String.format("%-" + pad + "s%-" + pad + "s", this.SENDER_ID, this.RECEIVER_ID);
    temp += String.format("%-" + MAX_TYPE_LENGTH + "s", this.TYPE.toString());
    temp += String.format("%-" + MAX_TYPE_OTHER_LENGTH + "s", this.OP_TYPE.toString());
    temp += String.format("%-" + MAX_TIMESTAMP_LENGTH + "s", this.TIMESTAMP.toString());
    temp += String.format("%-" + MAX_DATA_LENGTH + "s", this.DATA);

    return temp.getBytes(STRING_ENCODING);
  }

  public ACTION_TYPE getOperationType() {
    return this.OP_TYPE;
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
    REQUEST_VALIDATION_NUM
  }
}
