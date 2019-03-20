package com.mycelium.wapi.wallet;

import com.mrd.bitlib.model.Address;
import com.mrd.bitlib.model.AddressType;
import com.mycelium.wapi.wallet.btc.BtcAddress;
import com.mycelium.wapi.wallet.btc.coins.BitcoinMain;
import com.mycelium.wapi.wallet.btc.coins.BitcoinTest;
import com.mycelium.wapi.wallet.coins.CryptoCurrency;
import com.mycelium.wapi.wallet.colu.coins.ColuMain;
import com.mycelium.wapi.wallet.eth.EthAddress;
import com.mycelium.wapi.wallet.eth.coins.EthMain;

public class AddressUtils {

    public static GenericAddress from(CryptoCurrency currencyType, String address) {
        if (address.length() == 0) {
            return null;
        }
        if (currencyType instanceof BitcoinMain || currencyType instanceof BitcoinTest) {
            Address addr = Address.fromString(address);
            if (addr != null) {
                return fromAddress(addr);
            } else {
                return null;
            }
        } else if (currencyType instanceof ColuMain) {
            Address addr = Address.fromString(address);
            if (addr != null) {
                return new BtcAddress(currencyType, addr);
            } else {
                return null;
            }
        } else if (currencyType instanceof EthMain) {
            return new EthAddress(address);
        } else {
            return null;
        }
    }

    //Use only for bitcoin address
    public static GenericAddress fromAddress(Address address) {
        CryptoCurrency currency = address.getNetwork().isProdnet() ? BitcoinMain.get() : BitcoinTest.get();
        GenericAddress res = new BtcAddress(currency, address);
        return res;
    }

    public static boolean addressValidation(GenericAddress address) {
        if (Address.fromString(address.toString()) != null) {
            return true;
        }
        return address.getCoinType() == EthMain.INSTANCE;
    }

    public static String toMultiLineString(String address) {
        int length = address.length();
        if (length <= 12) {
            return address;
        } else if (length <= 24) {
            return toDoubleLineString(address);
        } else {
            int i = 0;
            StringBuilder result = new StringBuilder();
            while (i + 12 < address.length()) {
                result.append(address.substring(i, i + 12)).append("\r\n");
                i = i + 12;
            }
            return result.append(address.substring(i)).toString();
        }
    }

    public static String toDoubleLineString(String address) {
        int splitIndex = address.length() / 2;
        return address.substring(0, splitIndex) + "\r\n" + address.substring(splitIndex);
    }

    public static String toShortString(String address) {
        int showChars = 3;
        return address.substring(0, showChars) + "..." + address.substring(address.length() - showChars);
    }
}
