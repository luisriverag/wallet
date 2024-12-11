package com.mycelium.bequant.kyc.steps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewModelScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.mycelium.bequant.BequantPreference
import com.mycelium.bequant.BequantConstants
import com.mycelium.bequant.BequantConstants.COUNTRY_MODEL_KEY
import com.mycelium.bequant.common.ErrorHandler
import com.mycelium.bequant.common.loader
import com.mycelium.bequant.kyc.inputPhone.coutrySelector.CountriesSource
import com.mycelium.bequant.kyc.steps.adapter.ItemStep
import com.mycelium.bequant.kyc.steps.adapter.StepAdapter
import com.mycelium.bequant.kyc.steps.adapter.StepState
import com.mycelium.bequant.kyc.steps.viewmodel.HeaderViewModel
import com.mycelium.bequant.kyc.steps.viewmodel.InputPhoneViewModel
import com.mycelium.bequant.remote.model.KYCApplicant
import com.mycelium.bequant.remote.model.KYCRequest
import com.mycelium.bequant.remote.repositories.Api
import com.mycelium.wallet.R
import com.mycelium.wallet.databinding.FragmentBequantKycStep3Binding

class Step3Fragment : Fragment() {

    val headerViewModel: HeaderViewModel by viewModels()
    val viewModel: InputPhoneViewModel by viewModels()
    lateinit var kycRequest: KYCRequest

    val args: Step3FragmentArgs by navArgs()
    var binding: FragmentBequantKycStep3Binding? = null

    private val countrySelectedReceiver = object : BroadcastReceiver() {
        override fun onReceive(p0: Context?, intent: Intent?) {
            viewModel.countryModel.value = intent?.getParcelableExtra(COUNTRY_MODEL_KEY)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        kycRequest = args.kycRequest ?: KYCRequest()
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
                countrySelectedReceiver,
                IntentFilter(BequantConstants.ACTION_COUNTRY_SELECTED))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
            FragmentBequantKycStep3Binding.inflate(inflater, container, false)
                    .apply {
                        binding = this
                        viewModel = this@Step3Fragment.viewModel
                        headerViewModel = this@Step3Fragment.headerViewModel
                        lifecycleOwner = this@Step3Fragment
                    }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as AppCompatActivity?)?.supportActionBar?.title = getString(R.string.identity_auth)
        binding?.stepHeader?.step?.text = getString(R.string.step_n, 3)
        binding?.stepHeader?.stepProgress?.progress = 3
        val stepAdapter = StepAdapter()
        binding?.body?.stepper?.adapter = stepAdapter
        stepAdapter.submitList(listOf(
                ItemStep(1, getString(R.string.personal_info), StepState.COMPLETE_EDITABLE)
                , ItemStep(2, getString(R.string.residential_address), StepState.COMPLETE_EDITABLE)
                , ItemStep(3, getString(R.string.phone_number), StepState.CURRENT)
                , ItemStep(4, getString(R.string.doc_selfie), StepState.FUTURE)))

        stepAdapter.clickListener = {
            when (it) {
                1 -> findNavController().navigate(Step3FragmentDirections.actionEditStep1(kycRequest))
                2 -> findNavController().navigate(Step3FragmentDirections.actionEditStep2(kycRequest))
            }
        }
        binding?.btGetCode?.setOnClickListener {
            sendCode()
        }
        binding?.tvCountry?.setOnClickListener {
            findNavController().navigate(Step3FragmentDirections.actionChooseCountry())
        }
        if (viewModel.countryModel.value == null) {
            viewModel.countryModel.value = CountriesSource.countryModels.firstOrNull { it.acronym3 == kycRequest.country }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_bequant_kyc_step, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
            when (item.itemId) {
                android.R.id.home -> {
                    activity?.onBackPressed()
                    true
                }
                R.id.stepper -> {
                    item.icon = resources.getDrawable(if (binding?.body?.stepperLayout?.visibility == View.VISIBLE) R.drawable.ic_chevron_down else R.drawable.ic_chevron_up)
                    binding?.body?.stepperLayout?.visibility = if (binding?.body?.stepperLayout?.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                    true
                }
                else -> super.onOptionsItemSelected(item)
            }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(countrySelectedReceiver)
        super.onDestroy()
    }

    private fun sendCode() {
        binding?.tvErrorCode?.visibility = View.GONE
        viewModel.getRequest()?.let { request ->
            BequantPreference.setPhone("+${request.mobilePhoneCountryCode}${request.mobilePhone}")
            loader(true)
            Api.signRepository.accountOnceToken(viewModel.viewModelScope, {
                it?.token?.let { onceToken ->
                    val applicant = KYCApplicant(BequantPreference.getEmail(), BequantPreference.getPhone())
                    applicant.userId = onceToken
                    BequantPreference.setKYCRequest(kycRequest)
                    Api.kycRepository.create(viewModel.viewModelScope, kycRequest.toModel(applicant), {
                        Api.kycRepository.mobileVerification(viewModel.viewModelScope, {
                            findNavController().navigate(Step3FragmentDirections.actionNext(kycRequest))
                        }, { _, error ->
                            ErrorHandler(requireContext()).handle(error)
                        }, { loader(false) })
                    }, { _, msg ->
                        loader(false)
                        ErrorHandler(requireContext()).handle(msg)
                    })
                }
            }, { _, msg ->
                loader(false)
                ErrorHandler(requireContext()).handle(msg)
            })
        } ?: run {
            binding?.tvErrorCode?.visibility = View.VISIBLE
        }
    }
}