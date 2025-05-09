/*
 * Copyright 2013, 2014 Megion Research & Development GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mrd.bitlib;

import static com.mrd.bitlib.TransactionUtils.MINIMUM_OUTPUT_VALUE;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Ordering;
import com.mrd.bitlib.crypto.BitcoinSigner;
import com.mrd.bitlib.crypto.IPrivateKeyRing;
import com.mrd.bitlib.crypto.IPublicKeyRing;
import com.mrd.bitlib.model.AddressType;
import com.mrd.bitlib.model.BitcoinAddress;
import com.mrd.bitlib.model.BitcoinTransaction;
import com.mrd.bitlib.model.InputWitness;
import com.mrd.bitlib.model.NetworkParameters;
import com.mrd.bitlib.model.OutputList;
import com.mrd.bitlib.model.ScriptInput;
import com.mrd.bitlib.model.ScriptInputP2TR;
import com.mrd.bitlib.model.ScriptInputP2WPKH;
import com.mrd.bitlib.model.ScriptInputP2WSH;
import com.mrd.bitlib.model.ScriptInputStandard;
import com.mrd.bitlib.model.ScriptOutput;
import com.mrd.bitlib.model.ScriptOutputP2PKH;
import com.mrd.bitlib.model.ScriptOutputP2SH;
import com.mrd.bitlib.model.ScriptOutputP2TR;
import com.mrd.bitlib.model.ScriptOutputP2WPKH;
import com.mrd.bitlib.model.TransactionInput;
import com.mrd.bitlib.model.TransactionOutput;
import com.mrd.bitlib.model.UnspentTransactionOutput;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import javax.annotation.Nonnull;

import kotlin.NotImplementedError;

public class StandardTransactionBuilder {
   // hash size 32 + output index size 4 + script length 1 + max. script size for compressed keys 107 + sequence number 4
   // also see https://github.com/bitcoin/bitcoin/blob/master/src/primitives/transaction.h#L190
   public static final int MAX_INPUT_SIZE = 32 + 4 + 1 + 107 + 4;
   private NetworkParameters _network;
   private List<TransactionOutput> _outputs;

   public static class InsufficientBtcException extends Exception {
      //todo consider refactoring this into a composite return value instead of an exception. it is not really "exceptional"
      private static final long serialVersionUID = 1L;

      public long sending;
      public long fee;

      public InsufficientBtcException(long sending, long fee) {
         super("Insufficient funds to send " + sending + " satoshis with fee " + fee);
         this.sending = sending;
         this.fee = fee;
      }
   }

   public static class BtcOutputTooSmallException extends Exception {
      //todo consider refactoring this into a composite return value instead of an exception. it is not really "exceptional"
      private static final long serialVersionUID = 1L;

      public long value;

      public BtcOutputTooSmallException(long value) {
         super("An output was added with a value of " + value
             + " satoshis, which is smaller than the minimum accepted by the Bitcoin network");
      }
   }

   public static class UnableToBuildTransactionException extends Exception {
      private static final long serialVersionUID = 1L;
      private final BuildError code;

      public UnableToBuildTransactionException(String msg) {
         super(msg);
         code = BuildError.OTHER;
      }

      public UnableToBuildTransactionException(BuildError errorCode) {
         super("");
         code = errorCode;
      }

      public BuildError getCode() {
         return code;
      }

      public enum BuildError {
         NO_UTXO,
         PARENT_NEEDS_NO_BOOSTING,
         OTHER
      }
   }

   public StandardTransactionBuilder(NetworkParameters network) {
      _network = network;
      _outputs = new LinkedList<>();
   }

   public void addOutput(BitcoinAddress sendTo, long value) throws BtcOutputTooSmallException {
      addOutput(createOutput(sendTo, value, _network));
   }

   public void addOutput(TransactionOutput output) throws BtcOutputTooSmallException {
      if (output.value < MINIMUM_OUTPUT_VALUE) {
         throw new BtcOutputTooSmallException(output.value);
      }
      _outputs.add(output);
   }

   public void addOutputs(OutputList outputs) throws BtcOutputTooSmallException {
      for (TransactionOutput output : outputs) {
         if (output.value > 0) {
            addOutput(output);
         }
      }
   }

   public static TransactionOutput createOutput(BitcoinAddress sendTo, long value, NetworkParameters network) {
      ScriptOutput script;
      switch (sendTo.getType()) {
         case P2SH_P2WPKH:
            script = new ScriptOutputP2SH(sendTo.getTypeSpecificBytes());
            break;
         case P2PKH:
            script = new ScriptOutputP2PKH(sendTo.getTypeSpecificBytes());
            break;
         case P2WPKH:
            script = new ScriptOutputP2WPKH(sendTo.getTypeSpecificBytes());
            break;
         case P2TR:
            script = new ScriptOutputP2TR(sendTo.getTypeSpecificBytes());
            break;
         default:
            throw new NotImplementedError();
      }
      return new TransactionOutput(value, script);
   }

   public static List<byte[]> generateSignatures(SigningRequest[] requests, IPrivateKeyRing keyRing) {
      List<byte[]> signatures = new LinkedList<>();
      for (SigningRequest request : requests) {
         BitcoinSigner signer = keyRing.findSignerByPublicKey(request.getPublicKey());
         if (signer == null) {
            // This should not happen as we only work on outputs that we have
            // keys for
            throw new RuntimeException("Private key not found");
         }
         if (request.getSignAlgo() == SignAlgorithm.Standard) {
            byte[] signature = signer.makeStandardBitcoinSignature(request.getToSign());
            signatures.add(signature);
         } else if (request.getSignAlgo() == SignAlgorithm.Schnorr) {
            byte[] signature = signer.makeSchnorrBitcoinSignature(request.getToSign());
            signatures.add(signature);
         }
      }
      return signatures;
   }

   /**
    * Create an unsigned transaction and automatically calculate the miner fee.
    * <p>
    * If null is specified as the change address the 'richest' address that is part of the funding is selected as the
    * change address. This way the change always goes to the address contributing most, and the change will be less
    * than the contribution.
    *
    * @param inventory     The list of unspent transaction outputs that can be used as
    *                      funding
    * @param changeAddress The address to send any change to, can not be null
    * @param keyRing       The public key ring matching the unspent outputs
    * @param network       The network we are working on
    * @param minerFeeToUse The miner fee in sat to pay for every kilobytes of transaction size
    * @return An unsigned transaction or null if not enough funds were available
    */
   public UnsignedTransaction createUnsignedTransaction(Collection<UnspentTransactionOutput> inventory,
                                                        @Nonnull BitcoinAddress changeAddress, IPublicKeyRing keyRing,
                                                        NetworkParameters network, long minerFeeToUse)
       throws InsufficientBtcException, UnableToBuildTransactionException {

      // Make a copy so we can mutate the list
      List<UnspentTransactionOutput> unspent = new LinkedList<>(inventory);
      CoinSelector coinSelector = new FifoCoinSelector(minerFeeToUse, unspent, changeAddress.getType());
      long fee = coinSelector.getFee();
      long outputSum = coinSelector.getOutputSum();
      List<UnspentTransactionOutput> funding = pruneRedundantOutputs(coinSelector.getFundings(), fee + outputSum);
      boolean needChangeOutputInEstimation = needChangeOutputInEstimation(funding, outputSum, minerFeeToUse);

      FeeEstimatorBuilder feeEstimatorBuilder = new FeeEstimatorBuilder().setArrayOfInputs(funding)
              .setArrayOfOutputs(_outputs)
              .setMinerFeePerKb(minerFeeToUse);
      if (needChangeOutputInEstimation) {
         feeEstimatorBuilder.addOutput(changeAddress.getType());
      }
      fee = feeEstimatorBuilder.createFeeEstimator()
              .estimateFee();

      long found = 0;
      for (UnspentTransactionOutput output : funding) {
         found += output.value;
      }
      // We have found all the funds we need
      long toSend = fee + outputSum;

      // We have our funding, calculate change
      long change = found - toSend;

      // Get a copy of all outputs
      LinkedList<TransactionOutput> outputs = new LinkedList<>(_outputs);
      if(change >= MINIMUM_OUTPUT_VALUE) {
         TransactionOutput changeOutput = createOutput(changeAddress, change, _network);
         // Select a random position for our change so it is harder to analyze our addresses in the block chain.
         // It is OK to use the weak java Random class for this purpose.
         int position = new Random().nextInt(outputs.size() + 1);
         outputs.add(position, changeOutput);
      }

      UnsignedTransaction unsignedTransaction = new UnsignedTransaction(outputs, funding, keyRing, network, 0, UnsignedTransaction.NO_SEQUENCE);

      // check if we have a reasonable Fee or throw an error otherwise
      FeeEstimator estimator = new FeeEstimatorBuilder().setArrayOfInputs(unsignedTransaction.getFundingOutputs())
              .setArrayOfOutputs(unsignedTransaction.getOutputs())
              .createFeeEstimator();
      int estimateTransactionSize = estimator.estimateTransactionSize();
      long calculatedFee = unsignedTransaction.calculateFee();
      float estimatedFeePerKb = (long) ((float) calculatedFee / ((float) estimateTransactionSize / 1000));

      // set a limit of MAX_MINER_FEE_PER_KB as absolute limit - it is very likely a bug in the fee estimator or transaction composer
      if (estimatedFeePerKb > BitcoinTransaction.MAX_MINER_FEE_PER_KB) {
         throw new UnableToBuildTransactionException(
             String.format(Locale.getDefault(),
                 "Unreasonable high transaction fee of %s sat/1000Byte on a %d Bytes tx. Fee: %d sat, Suggested fee: %d sat",
                 estimatedFeePerKb, estimateTransactionSize, calculatedFee, minerFeeToUse)
         );
      }

      return unsignedTransaction;
   }

   private boolean needChangeOutputInEstimation(List<UnspentTransactionOutput> funding,
                                                long outputSum, long minerFeeToUse) {

      FeeEstimator feeEstimator = new FeeEstimatorBuilder().setArrayOfInputs(funding)
              .setArrayOfOutputs(_outputs)
              .setMinerFeePerKb(minerFeeToUse)
              .createFeeEstimator();
      long fee = feeEstimator.estimateFee();

      long found = 0;
      for (UnspentTransactionOutput output : funding) {
         found += output.value;
      }
      // We have found all the funds we need
      long toSend = fee + outputSum;

      // We have our funding, calculate change
      long change = found - toSend;

      if (change >= MINIMUM_OUTPUT_VALUE) {
         // We need to add a change output in the estimation.
         return true;
      } else {
         // The change output would be smaller (or zero) than what the network would accept.
         // In this case we leave it be as a small increased miner fee.
         return false;
      }
   }


   /**
    * Greedy picks the biggest UTXOs until the outputSum is met.
    * @param funding UTXO list in any order
    * @param outputSum amount to spend
    * @return shuffled list of UTXOs
    */
   private List<UnspentTransactionOutput> pruneRedundantOutputs(List<UnspentTransactionOutput> funding, long outputSum) {
      List<UnspentTransactionOutput> largestToSmallest = Ordering.natural().reverse().onResultOf(new Function<UnspentTransactionOutput, Comparable>() {
         @Override
         public Comparable apply(UnspentTransactionOutput input) {
            return input.value;
         }
      }).sortedCopy(funding);

      long target = 0;
      for (int i = 0; i < largestToSmallest.size(); i++) {
         UnspentTransactionOutput output = largestToSmallest.get(i);
         target += output.value;
         if (target >= outputSum) {

            List<UnspentTransactionOutput> ret = largestToSmallest.subList(0, i + 1);
            Collections.shuffle(ret);
            return ret;
         }
      }
      return largestToSmallest;
   }

   @VisibleForTesting
   BitcoinAddress getRichest(Collection<UnspentTransactionOutput> unspent, final NetworkParameters network) {
      Preconditions.checkArgument(!unspent.isEmpty());
      Function<UnspentTransactionOutput, BitcoinAddress> txout2Address = new Function<UnspentTransactionOutput, BitcoinAddress>() {
         @Override
         public BitcoinAddress apply(UnspentTransactionOutput input) {
            return input.script.getAddress(network);
         }
      };
      Multimap<BitcoinAddress, UnspentTransactionOutput> index = Multimaps.index(unspent, txout2Address);
      BitcoinAddress ret = getRichest(index);
      return Preconditions.checkNotNull(ret);
   }

   private BitcoinAddress getRichest(Multimap<BitcoinAddress, UnspentTransactionOutput> index) {
      BitcoinAddress ret = null;
      long maxSum = 0;
      for (BitcoinAddress address : index.keys()) {
         Collection<UnspentTransactionOutput> unspentTransactionOutputs = index.get(address);
         long newSum = sum(unspentTransactionOutputs);
         if (newSum > maxSum) {
            ret = address;
            maxSum = newSum;
         }
      }
      return ret;
   }

   private long sum(Iterable<UnspentTransactionOutput> outputs) {
      long sum = 0;
      for (UnspentTransactionOutput output : outputs) {
         sum += output.value;
      }
      return sum;
   }

   public static BitcoinTransaction finalizeTransaction(UnsignedTransaction unsigned, List<byte[]> signatures) {
      // Create finalized transaction inputs
      final UnspentTransactionOutput[] funding = unsigned.getFundingOutputs();
      TransactionInput[] inputs = new TransactionInput[funding.length];
      for (int i = 0; i < funding.length; i++) {
         if (isScriptInputSegWit(unsigned, i)) {
            inputs[i] = unsigned.getInputs()[i];
            if (inputs[i].script instanceof  ScriptInputP2WPKH && !((ScriptInputP2WPKH) inputs[i].script).isNested()) {
               inputs[i].script = ScriptInput.EMPTY;
            }
            InputWitness witness = new InputWitness(2);
            witness.setStack(0, signatures.get(i));
            witness.setStack(1, unsigned.getSigningRequests()[i].getPublicKey().getPublicKeyBytes());
            inputs[i].setWitness(witness);
         } else if (isScriptInputP2TR(unsigned, i)) {
            inputs[i] = new TransactionInput(funding[i].outPoint, ScriptInput.EMPTY);
            InputWitness witness = new InputWitness(2);
            witness.setStack(0, signatures.get(i));
            witness.setStack(1, unsigned.getSigningRequests()[i].getPublicKey().getPublicKeyBytes());
            inputs[i].setWitness(witness);
         } else {
            // Create script from signature and public key
            ScriptInputStandard script = new ScriptInputStandard(signatures.get(i),
                    unsigned.getSigningRequests()[i].getPublicKey().getPublicKeyBytes());
            inputs[i] = new TransactionInput(funding[i].outPoint, script, unsigned.getDefaultSequenceNumber(), funding[i].value);
         }
      }

      // Create transaction with valid outputs and empty inputs
      return new BitcoinTransaction(1, inputs, unsigned.getOutputs(), unsigned.getLockTime());
   }

   private static boolean isScriptInputSegWit(UnsignedTransaction unsigned, int i) {
      return unsigned.getInputs()[i].script instanceof ScriptInputP2WPKH || unsigned.getInputs()[i].script instanceof ScriptInputP2WSH;
   }

   private static boolean isScriptInputP2TR(UnsignedTransaction unsigned, int i) {
      return unsigned.getInputs()[i].script instanceof ScriptInputP2TR;
   }

   private long outputSum() {
      long sum = 0;
      for (TransactionOutput output : _outputs) {
         sum += output.value;
      }
      return sum;
   }

   private interface CoinSelector {
      List<UnspentTransactionOutput> getFundings();
      long getFee();
      long getOutputSum();
   }

   private class FifoCoinSelector implements CoinSelector {
      private List<UnspentTransactionOutput> allFunding;
      private long feeSat;
      private long outputSum;

      FifoCoinSelector(long feeSatPerKb, List<UnspentTransactionOutput> unspent, AddressType changeType)
          throws InsufficientBtcException {
         // Find the funding for this transaction
         allFunding = new LinkedList<>();
         FeeEstimatorBuilder feeEstimatorBuilder = new FeeEstimatorBuilder().setArrayOfInputs(unspent)
                 .addOutput(changeType)
                 .setMinerFeePerKb(feeSatPerKb);
         FeeEstimator feeEstimator = feeEstimatorBuilder
                 .createFeeEstimator();
         feeSat = feeEstimator.estimateFee();
         outputSum = outputSum();
         long foundSat = 0;
         while (foundSat < feeSat + outputSum) {
            UnspentTransactionOutput unspentTransactionOutput = extractOldest(unspent);
            if (unspentTransactionOutput == null) {
               // We do not have enough funds
               throw new InsufficientBtcException(outputSum, feeSat);
            }
            foundSat += unspentTransactionOutput.value;
            allFunding.add(unspentTransactionOutput);

            FeeEstimatorBuilder estimatorBuilder = feeEstimatorBuilder.setArrayOfInputs(allFunding)
                    .setArrayOfOutputs(_outputs)
                    .setMinerFeePerKb(feeSatPerKb);
            if (needChangeOutputInEstimation(allFunding, outputSum, feeSatPerKb)) {
               estimatorBuilder.addOutput(changeType);
            }
            feeSat = estimatorBuilder.createFeeEstimator().estimateFee();
         }
      }

      @Override
      public List<UnspentTransactionOutput> getFundings() {
         return allFunding;
      }

      @Override
      public long getFee() {
         return feeSat;
      }

      @Override
      public long getOutputSum() {
         return outputSum;
      }

      private UnspentTransactionOutput extractOldest(Collection<UnspentTransactionOutput> unspent) {
         // find the "oldest" output
         int minHeight = Integer.MAX_VALUE;
         UnspentTransactionOutput oldest = null;
         for (UnspentTransactionOutput output : unspent) {
            if (!(output.script instanceof ScriptOutputP2PKH) && !(output.script instanceof ScriptOutputP2SH)
                    && !(output.script instanceof ScriptOutputP2WPKH) && !(output.script instanceof ScriptOutputP2TR)) {
               // only look for certain scripts
               continue;
            }

            // Unconfirmed outputs have height = -1 -> change this to Int.MAX-1, so that we
            // choose them as the last possible option
            int height = output.height > 0 ? output.height : Integer.MAX_VALUE - 1;

            if (height < minHeight) {
               minHeight = height;
               oldest = output;
            }
         }
         if (oldest == null) {
            // There were no outputs
            return null;
         }
         unspent.remove(oldest);
         return oldest;
      }
   }
}
