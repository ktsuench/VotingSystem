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
package com.hkkt.util;

import com.hkkt.communication.KDC;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.List;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

/**
 *
 * @author Kent Tsuenchy
 */
public class Encryptor {
  private static Encryptor instance;
  private static KeyPairGenerator pairKeyGen;
  private static KeyGenerator sharedKeyGen;

  public static Encryptor getInstance() throws NoSuchAlgorithmException {
    if (instance == null)
      instance = new Encryptor();

    return instance;
  }

  private Encryptor() throws NoSuchAlgorithmException {
    sharedKeyGen = KeyGenerator.getInstance("DES");
    pairKeyGen = KeyPairGenerator.getInstance("RSA");
    pairKeyGen.initialize(2048);
  }

  public Key decryptKey(byte[] sharedKey, Key key, String algorithm, int keyType) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, UnsupportedEncodingException, IllegalBlockSizeException, BadPaddingException {
    List acceptableKeyTypes = Arrays.asList(Cipher.PRIVATE_KEY, Cipher.PUBLIC_KEY, Cipher.SECRET_KEY);
    Cipher cipher;

    if (sharedKey.length < 1 || acceptableKeyTypes.indexOf(keyType) < 0)
      return null;

    if (key instanceof SecretKey)
      cipher = Cipher.getInstance(KDC.DES_ENCRYPTION_STANDARD);
    else if (key instanceof PublicKey || key instanceof PrivateKey)
      cipher = Cipher.getInstance(KDC.RSA_ENCRYPTION_STANDARD);
    else
      return null;

    cipher.init(Cipher.UNWRAP_MODE, key);

    return cipher.unwrap(sharedKey, algorithm, keyType);
  }

  public byte[] encryptDecryptData(int opmode, byte[] data, Key key) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, UnsupportedEncodingException, IllegalBlockSizeException, BadPaddingException {
    List acceptableModes = Arrays.asList(Cipher.DECRYPT_MODE, Cipher.ENCRYPT_MODE);
    Cipher cipher;

    if (data.length < 1 || acceptableModes.indexOf(opmode) < 0)
      return null;

    if (key instanceof SecretKey)
      cipher = Cipher.getInstance(KDC.DES_ENCRYPTION_STANDARD);
    else if (key instanceof PublicKey || key instanceof PrivateKey)
      cipher = Cipher.getInstance(KDC.RSA_ENCRYPTION_STANDARD);
    else
      return null;

    cipher.init(opmode, key);

    return cipher.doFinal(data);
  }

  public byte[] encryptKey(Key sharedKey, Key key) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, UnsupportedEncodingException, IllegalBlockSizeException, BadPaddingException {
    Cipher cipher;

    if (key instanceof SecretKey)
      cipher = Cipher.getInstance(KDC.DES_ENCRYPTION_STANDARD);
    else if (key instanceof PublicKey || key instanceof PrivateKey)
      cipher = Cipher.getInstance(KDC.RSA_ENCRYPTION_STANDARD);
    else
      return null;

    cipher.init(Cipher.WRAP_MODE, key);

    return cipher.wrap(sharedKey);
  }

  public KeyPair genKeyPair() {
    return pairKeyGen.genKeyPair();
  }

  public SecretKey genSharedKey() {
    return sharedKeyGen.generateKey();
  }
}
