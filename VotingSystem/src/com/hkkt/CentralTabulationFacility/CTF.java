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
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Hassan Khan
 */
public class CTF extends AbstractServer {
  private ClientConnectionManager clientManager;
  private final String name;
  private final ServerConnectionManager serverManager;
  private final HashMap<String, Long> validationTickets;
  private final List<Long> crossedOff;
  private final List<Integer> hitlerVote;
  private final List<Integer> stalinVote;
  private final List<Integer> mussoliniVote;
  
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
    this.serverManager = new ServerConnectionManager(address, this);
    this.name = name;
    this.validationTickets = new HashMap<>();
    this.crossedOff = new ArrayList();
    this.hitlerVote = new ArrayList();
    this.stalinVote = new ArrayList();
    this.mussoliniVote = new ArrayList();
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

  /**
   * Method for CTF to handle incoming data
   *
   * @param datagram data that has been sent to the CTF
   */
  
  public boolean checkValNum(long valNum){
    boolean inHashList = this.validationTickets.containsValue(valNum);
    return inHashList;
  }
  
  public void addVoteTally(int idNum, String vote){
          
    switch(vote.toUpperCase()){
          case "HITLER":
            hitlerVote.add(idNum);
            System.out.println("Voted Hitler");
            break;
          case "STALIN":
            stalinVote.add(idNum);
            System.out.println("Voted Stalin");
            break;
          case "MUSSOLINI": 
            mussoliniVote.add(idNum);
            System.out.println("Voted Mumu");
            break;      
                  
        }
  
  }
  
  public void crossValNum(long validNum){
    crossedOff.add(validNum);
  }
  
  public void printList(List list){
    for(int i = 0; i < list.size(); i++){
      System.out.println(list.get(i).toString());
    }
  
  }
  
  public void publishOutcome(){
    String winner = "tie";
    if(hitlerVote.size() > stalinVote.size() && hitlerVote.size() > mussoliniVote.size())
      winner = "hitler";
    if(stalinVote.size() > hitlerVote.size() && stalinVote.size() > mussoliniVote.size())
      winner = "stalin";
    if(mussoliniVote.size() > hitlerVote.size() && mussoliniVote.size() > stalinVote.size())
      winner = "mussolini";
    switch(winner){
      case "hitler":
        System.out.println("Hitler has won the election, hail the Furher!");
        break;
      case "stalin":
        System.out.println("Stalin has won the election, The Man of Steel reigns!");
        break;
      case "mussolini":
        System.out.println("Mussolini has won the election, Il Duce Conduce!");
        break;
      case "tie":
        System.out.println("Democracy failed, The states will go to war!");
        break;
    }
    System.out.println("The ID's of the people who voted for Hitler is all follows: ");
    printList(hitlerVote);
    System.out.println("The ID's of the people who voted for Stalin is all follows: ");
    printList(stalinVote);
    System.out.println("The ID's of the people who voted for Mussolini is all follows: ");
    printList(mussoliniVote);
    
  }
  
  @Override
  public void handleDatagram(Datagram datagram) {
    System.out.println(this.name + " received message from " + datagram.getSender() + ": " + datagram.getData());
    
    this.validationTickets.put("bobbity", Long.parseLong("100"));
    this.validationTickets.put("joseph", Long.parseLong("200"));
    this.validationTickets.put("GerMan", Long.parseLong("400"));
    try {
      Datagram echo = new Datagram(Datagram.DATA_TYPE.MESSAGE, this.name, datagram.getSender(), datagram.getData());
      this.serverManager.addDatagramToQueue(datagram.getSender(), echo);
      String[] arrInfo = new String[3];
      arrInfo = datagram.getData().split("\\s+",3);
      int randIdRecieved = Integer.parseInt(arrInfo[0]);
      long valNumRecieved = Long.parseLong(arrInfo[1]);
      String voteRecieved = arrInfo[2];
      
      //check validation number
      if(checkValNum(valNumRecieved) == true && crossedOff.contains(valNumRecieved) == false){
        //validation number is valid and number is not in crossed off list
        addVoteTally(randIdRecieved, voteRecieved);
        crossValNum(valNumRecieved);
      }
      
      if(crossedOff.size() == 3){
        publishOutcome();
      }
      
    } catch (DatagramMissingSenderReceiverException ex) {
      Logger.getLogger(CTF.class.getName()).log(Level.SEVERE, null, ex);
    }
  }
}
