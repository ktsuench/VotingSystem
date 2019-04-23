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
package com.hkkt.communication;

import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

/**
 *
 * @author Kent Tsuenchy
 */
public class KDC {
  public static final String DES_ENCRYPTION_STANDARD = "DES/ECB/PKCS5Padding";
  public static final String ENCODING_STANDARD = "ISO-8859-1";
  public static final String RSA_ENCRYPTION_STANDARD = "RSA/ECB/PKCS1Padding";
  public static final String RSA_SIGNATURE_STANDARD = "SHA256withRSA";
  private static Cipher cipher;
  private static KDC instance;
  private static ConcurrentHashMap<String, PublicKey> keys;
  private static PrivateKey privateKey;
  private static PublicKey publicKey;
  private static KeyGenerator secretKeyGen;
  private static ConcurrentHashMap<String, SecretKey> sharedKeys;

  public static KDC getInstance() throws NoSuchAlgorithmException {
    if (instance == null)
      instance = new KDC();

    return instance;
  }

  private KDC() throws NoSuchAlgorithmException {
    keys = new ConcurrentHashMap<>();
    sharedKeys = new ConcurrentHashMap<>();

    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);

    KeyPair pair = generator.genKeyPair();
    privateKey = pair.getPrivate();
    publicKey = pair.getPublic();

    secretKeyGen = KeyGenerator.getInstance("DES");
  }

  public void addKey(String id, byte[] encryptedKey) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException {
    if (sharedKeys.containsKey(id)) {
      PublicKey key;

      cipher = Cipher.getInstance(DES_ENCRYPTION_STANDARD);
      cipher.init(Cipher.UNWRAP_MODE, sharedKeys.get(id));
      key = (PublicKey) cipher.unwrap(encryptedKey, "RSA", Cipher.PUBLIC_KEY);

      keys.put(id, key);
    }
  }

  public byte[] getKey(String requester, String id) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException {
    byte[] encryptedKey = null;

    if (sharedKeys.containsKey(requester)) {
      PublicKey key = keys.get(id);

      if (key != null) {
        cipher = Cipher.getInstance(DES_ENCRYPTION_STANDARD);

        cipher.init(Cipher.WRAP_MODE, sharedKeys.get(requester));
        encryptedKey = cipher.wrap(key);
      }
    }

    return encryptedKey;
  }

  public PublicKey getPublicKey() {
    return publicKey;
  }

  public byte[] getSharedKey(String id) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException {
    SecretKey key = secretKeyGen.generateKey();
    byte[] encryptedKey;

    cipher = Cipher.getInstance(RSA_ENCRYPTION_STANDARD);

    cipher.init(Cipher.WRAP_MODE, privateKey);
    encryptedKey = cipher.wrap(key);

    sharedKeys.put(id, key);

    return encryptedKey;
  }

  public void removeKey(String id) {
    keys.remove(id);
    sharedKeys.remove(id);
  }
}
