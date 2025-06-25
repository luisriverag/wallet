package com.mycelium.wapi.wallet.manager

import com.mrd.bitlib.crypto.Bip39
import com.mrd.bitlib.crypto.BipDerivationType
import com.mrd.bitlib.crypto.HdKeyNode
import com.mrd.bitlib.crypto.RandomSource
import com.mrd.bitlib.model.NetworkParameters
import com.mycelium.wapi.wallet.AesKeyCipher
import com.mycelium.wapi.wallet.SecureKeyValueStore
import com.mycelium.wapi.wallet.btc.InMemoryBtcWalletManagerBacking
import com.mycelium.wapi.wallet.btc.bip44.HDAccountKeyManager
import com.mycelium.wapi.wallet.masterseed.MasterSeedManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock

class HDAccountKeyManagerTest {
    val seed = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

    //    val account0PublicKey = listOf(
//        "02a7451395735369f2ecdfc829c0f774e88ef1303dfe5b2f04dbaab30a535dfdd6",
//        "03589ae7c835ce76e23cf8feb32f1adf4a7f2ba0ed2ad70801802b0bcd70e99c1c",
//        "03be3878cb32ea37037b6d906ca8dfadc8bf511305194e24093379e19ea8fce04e",
//        "026ec997bdb38c9fc81c5f20b73b5fbcf6bf2406b840fc992ad00f6c12ba6156b5",
//    )
//
//
//    @Test
//    fun testPublicKey() {
//        val hdWallet = HDWallet(seed, "")
//        val keyManager = HDAccountKeyManager
//            hdWallet,
//            CoinType.Bitcoin,
//            Derivation.BitcoinTestnet,
//            0,
//            BipDerivationType.BIP44,
//            BtcAddressFactory(NetworkParameters.testNetwork, BitcoinTest)
//        )
//        account0PublicKey.forEachIndexed { i, pubKey ->
//            val gen = keyManager.getPublicKey(false, i)
//            assertEquals(pubKey, gen.data.toHex())
//        }
//    }
//
    val account0address = listOf(
        "mkpZhYtJu2r87Js3pDiWJDmPte2NRZ8bJV",
        "mzpbWabUQm1w8ijuJnAof5eiSTep27deVH",
        "mnTkxhNkgx7TsZrEdRcPti564yQTzynGJp",
        "mpW3iVi2Td1vqDK8Nfie29ddZXf9spmZkX",
    )

    @Test
    fun testLegacy() {
        val backing = InMemoryBtcWalletManagerBacking()
        val fakeRandomSource = mock<RandomSource>(RandomSource::class.java)
        val store = SecureKeyValueStore(backing, fakeRandomSource)

        val masterSeed =
            Bip39.generateSeedFromWordList(seed.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray(), "")
        val masterSeedManager = MasterSeedManager(store)
        masterSeedManager.configureBip32MasterSeed(masterSeed, AesKeyCipher.defaultKeyCipher())
        // Create the base keys for the account
        val root = HdKeyNode.fromSeed(masterSeed.bip32Seed, BipDerivationType.BIP44)
        val keyManager = HDAccountKeyManager.createNew(
            root,
            NetworkParameters.testNetwork,
            0,
            store,
            AesKeyCipher.defaultKeyCipher(),
            BipDerivationType.BIP44
        )
        account0address.forEachIndexed { i, address ->
            val gen = keyManager.getAddress(false, i)
            println("$address = $gen")
            assertEquals(address, gen.toString())
        }
    }
//
//    private val compatibleAdresses = listOf(
//        "2Mww8dCYPUpKHofjgcXcBCEGmniw9CoaiD2",
//        "2N55m54k8vr95ggehfUcNkdbUuQvaqG2GxK",
//        "2N9LKph9TKtv1WLDfaUJp4D8EKwsyASYnGX",
//    )
//
//    @Test
//    fun testCompatible() {
//        val hdWallet = HDWallet(seed, "")
//        val keyManager = HDAccountKeyManager<BtcAddress>(
//            hdWallet,
//            CoinType.Bitcoin,
//            Derivation.BitcoinTestnet,
//            0,
//            BipDerivationType.BIP49,
//            BtcAddressFactory(NetworkParameters.testNetwork, BitcoinTest)
//        )
//        compatibleAdresses.forEachIndexed { i, address ->
//            val gen = keyManager.getTypedAddress(false, i)
//            println("$address = $gen")
//            assertEquals(address, gen.toString())
//        }
//    }
//
//
//    private val segwit = listOf(
//        "tb1q6rz28mcfaxtmd6v789l9rrlrusdprr9pqcpvkl",
//        "tb1qd7spv5q28348xl4myc8zmh983w5jx32cjhkn97",
//        "tb1qxdyjf6h5d6qxap4n2dap97q4j5ps6ua8sll0ct",
//    )
//
//    @Test
//    fun testSegwit() {
//        val hdWallet = HDWallet(seed, "")
//        val keyManager = HDAccountKeyManager<BtcAddress>(
//            hdWallet,
//            CoinType.Bitcoin,
//            Derivation.BitcoinTestnet,
//            0,
//            BipDerivationType.BIP84,
//            BtcAddressFactory(NetworkParameters.testNetwork, BitcoinTest)
//        )
//        segwit.forEachIndexed { i, address ->
//            val gen = keyManager.getTypedAddress(false, i)
//            println("$address = $gen")
//            assertEquals(address, gen.toString())
//        }
//    }
//
//
//    private val segwitMainnet = listOf(
//        "bc1qcr8te4kr609gcawutmrza0j4xv80jy8z306fyu",
//        "bc1qnjg0jd8228aq7egyzacy8cys3knf9xvrerkf9g",
//        "bc1qp59yckz4ae5c4efgw2s5wfyvrz0ala7rgvuz8z",
//    )
//
//    @Test
//    fun testSegwitMainnet() {
//        val hdWallet = HDWallet(seed, "")
//        val keyManager = HDAccountKeyManager<BtcAddress>(
//            hdWallet,
//            CoinType.Bitcoin,
//            Derivation.BitcoinSegwit,
//            0,
//            BipDerivationType.BIP84,
//            BtcAddressFactory(NetworkParameters.productionNetwork, BitcoinMain)
//        )
//        segwitMainnet.forEachIndexed { i, address ->
//            val gen = keyManager.getTypedAddress(false, i)
//            println("$address = $gen")
//            assertEquals(address, gen.toString())
//        }
//    }

    private val taprootMainnet = listOf(
        "bc1p5cyxnuxmeuwuvkwfem96lqzszd02n6xdcjrs20cac6yqjjwudpxqkedrcr",
        "bc1p4qhjn9zdvkux4e44uhx8tc55attvtyu358kutcqkudyccelu0was9fqzwh",
    )

    @Test
    fun testTaprootMainnet() {
        val backing = InMemoryBtcWalletManagerBacking()
        val fakeRandomSource = mock<RandomSource>(RandomSource::class.java)
        val store = SecureKeyValueStore(backing, fakeRandomSource)

        val masterSeed =
            Bip39.generateSeedFromWordList(seed.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray(), "")
        val masterSeedManager = MasterSeedManager(store)
        masterSeedManager.configureBip32MasterSeed(masterSeed, AesKeyCipher.defaultKeyCipher())
        // Create the base keys for the account
        val root = HdKeyNode.fromSeed(masterSeed.bip32Seed, BipDerivationType.BIP86)
        val keyManager = HDAccountKeyManager.createNew(
            root,
            NetworkParameters.productionNetwork,
            0,
            store,
            AesKeyCipher.defaultKeyCipher(),
            BipDerivationType.BIP86
        )

        taprootMainnet.forEachIndexed { i, address ->
            val gen = keyManager.getAddress(false, i)
            println("$address = $gen")
            assertEquals(address, gen.toString())
        }

        (0..10).forEach {
            println("address $it = " + keyManager.getAddress(false, it))
        }
    }

    @Test
    fun testTaprootTestnet() {
        val backing = InMemoryBtcWalletManagerBacking()
        val fakeRandomSource = mock<RandomSource>(RandomSource::class.java)
        val store = SecureKeyValueStore(backing, fakeRandomSource)

        val masterSeed =
            Bip39.generateSeedFromWordList(seed.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray(), "")
        val masterSeedManager = MasterSeedManager(store)
        masterSeedManager.configureBip32MasterSeed(masterSeed, AesKeyCipher.defaultKeyCipher())
        // Create the base keys for the account
        val root = HdKeyNode.fromSeed(masterSeed.bip32Seed, BipDerivationType.BIP86)
        val keyManager = HDAccountKeyManager.createNew(
            root,
            NetworkParameters.testNetwork,
            0,
            store,
            AesKeyCipher.defaultKeyCipher(),
            BipDerivationType.BIP86
        )


        (0..10).forEach {
            println("address $it = " + keyManager.getAddress(false, it))
        }
    }

//    data class DecodeTestData(
//        val encoded: String,
//        val isValid: Boolean,
//        val isValidM: Boolean,
//        val hrp: String,
//        val dataHex: String,
//    )
//
//    val data = listOf(
//        DecodeTestData(
//            "bc1pw508d6qejxtdg4y5r3zarvary0c5xw7kw508d6qejxtdg4y5r3zarvary0c5xw7k7grplx",
//            true,
//            false,
//            "bc",
//            "010e140f070d1a001912060b0d081504140311021d030c1d03040f1814060e1e160e140f070d1a001912060b0d081504140311021d030c1d03040f1814060e1e16"
//        )
//    )
//
//    @Test
//    fun testsdfsd() {
//        data.forEach {
//            val address = Bech32.encode(it.hrp, it.dataHex.toBytes())
//            assertEquals(it.encoded, address)
//        }
//    }

}