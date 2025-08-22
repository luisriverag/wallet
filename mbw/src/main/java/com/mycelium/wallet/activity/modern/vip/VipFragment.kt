package com.mycelium.wallet.activity.modern.vip

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.mycelium.wallet.MbwManager
import com.mycelium.wallet.R
import com.mycelium.wallet.activity.modern.ModernMain.Companion.TAB_VIP
import com.mycelium.wallet.databinding.FragmentVipBinding
import com.mycelium.wallet.event.PageSelectedEvent
import com.squareup.otto.Subscribe

class VipFragment : Fragment() {

    private lateinit var binding: FragmentVipBinding
    private val viewModel by viewModels<VipViewModel>()
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentVipBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupInputs()
        setupObservers()
    }

    override fun onStart() {
        super.onStart()
        MbwManager.getEventBus().register(this)
    }

    override fun onStop() {
        MbwManager.getEventBus().unregister(this)
        super.onStop()
    }

    private fun setupInputs() {
        binding.vipCodeInput.doOnTextChanged { text, _, _, _ ->
            text?.let {
                viewModel.updateVipText(it.toString())
            }
        }
    }

    private fun setupObservers() = lifecycleScope.launchWhenStarted {
        viewModel.stateFlow.collect { state ->
            binding.vipProgress.isVisible = state.progress
            updateButtons(state)
            handleError(state.error)
            handleSuccess(state.isVip)
        }
    }

    private fun updateButtons(state: VipViewModel.State) {
        binding.vipApplyButton.apply {
            isEnabled = state.text.isNotEmpty() && !state.progress && state.error == null
            text = if (state.progress) "" else getString(R.string.apply_vip_code)
            setOnClickListener { viewModel.applyCode() }
        }
    }

    private fun handleError(error: VipViewModel.ErrorType?) = binding.apply {
        when (error) {
            null -> {
                errorText.isVisible = false
                vipCodeInput.setBackgroundResource(R.drawable.bg_input_text_filled)
            }

            VipViewModel.ErrorType.BAD_REQUEST -> {
                errorText.isVisible = true
                vipCodeInput.setBackgroundResource(R.drawable.bg_input_text_filled_error)
            }

            else -> {
                hideKeyBoard()
                showViperUnexpectedErrorDialog()
            }
        }
    }

    private fun handleSuccess(success: Boolean) {
        binding.apply {
            vipApplyButton.isVisible = !success
            vipInputGroup.isVisible = !success
            vipSuccessGroup.isVisible = success
            vipTitle.setText(if (success) R.string.vip_title_success else R.string.vip_title)
            if (success) {
                vipCodeInput.apply {
                    hint = null
                    text = null
                    clearFocus()
                }
                hideKeyBoard()
            } else {
                vipCodeInput.hint = getString(R.string.vip_code_hint)
            }
        }
    }

    private fun showViperUnexpectedErrorDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.vip_unexpected_alert_title))
            .setMessage(getString(R.string.vip_unexpected_alert_message))
            .setPositiveButton(R.string.button_ok, null)
            .setOnDismissListener { viewModel.resetState() }
            .show()
    }

    private fun hideKeyBoard() {
        val imm = context?.getSystemService(Activity.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(requireView().windowToken, 0)
    }

    @Subscribe
    fun pageSelectedEvent(event: PageSelectedEvent) {
        if (event.tag == TAB_VIP) {
            binding.icon.playAnimation()
        }
    }
}
