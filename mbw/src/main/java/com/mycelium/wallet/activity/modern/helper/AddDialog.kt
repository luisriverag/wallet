package com.mycelium.wallet.activity.modern.helper

import android.app.Activity
import android.app.Dialog
import android.view.View
import com.mycelium.wallet.MbwManager
import com.mycelium.wallet.R
import com.mycelium.wallet.Utils
import com.mycelium.wallet.databinding.AddToAddressBookDialogBinding
import com.mycelium.wapi.wallet.Address

class AddDialog(activity: Activity) : Dialog(activity) {
    var clipboardClickListener: ((List<Address>) -> Unit)? = null
    var scanClickListener: (() -> Unit)? = null
    var binding: AddToAddressBookDialogBinding? = null

    init {
        setContentView(AddToAddressBookDialogBinding.inflate(layoutInflater, null, false).apply {
            binding = this
        }.root)
        setTitle(R.string.add_to_address_book_dialog_title)
        binding?.btScan?.setOnClickListener { v: View? ->
            scanClickListener?.invoke()
            dismiss()
        }
        val mbwManager = MbwManager.getInstance(activity)
        val addresses =
            mbwManager.getWalletManager(false).parseAddress(Utils.getClipboardString(activity))
        binding?.btClipboard?.isEnabled = addresses.isNotEmpty()
        binding?.btClipboard?.setOnClickListener { v: View? ->
            clipboardClickListener?.invoke(addresses)
            dismiss()
        }
    }
}