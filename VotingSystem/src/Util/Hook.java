/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Util;

/**
 *
 * @author Kent Tsuenchy
 */
public interface Hook extends Runnable {
  public void setHookData(Object data);
}
