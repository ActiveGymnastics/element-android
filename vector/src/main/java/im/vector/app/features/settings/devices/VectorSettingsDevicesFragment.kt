/*
 * Copyright 2019 New Vector Ltd
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

package im.vector.app.features.settings.devices

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.airbnb.mvrx.Async
import com.airbnb.mvrx.Loading
import com.airbnb.mvrx.fragmentViewModel
import com.airbnb.mvrx.withState
import im.vector.app.R
import im.vector.app.core.dialogs.ManuallyVerifyDialog
import im.vector.app.core.dialogs.PromptPasswordDialog
import im.vector.app.core.extensions.cleanup
import im.vector.app.core.extensions.configureWith
import im.vector.app.core.extensions.exhaustive
import im.vector.app.core.platform.VectorBaseFragment
import im.vector.app.databinding.DialogBaseEditTextBinding
import im.vector.app.databinding.FragmentGenericRecyclerBinding
import im.vector.app.features.crypto.verification.VerificationBottomSheet

import org.matrix.android.sdk.internal.crypto.model.rest.DeviceInfo
import javax.inject.Inject

/**
 * Display the list of the user's device
 */
class VectorSettingsDevicesFragment @Inject constructor(
        val devicesViewModelFactory: DevicesViewModel.Factory,
        private val devicesController: DevicesController
) : VectorBaseFragment<FragmentGenericRecyclerBinding>(),
        DevicesController.Callback {

    // used to avoid requesting to enter the password for each deletion
    // Note: Sonar does not like to use password for member name.
    private var mAccountPass: String = ""

    override fun getBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentGenericRecyclerBinding {
        return FragmentGenericRecyclerBinding.inflate(inflater, container, false)
    }

    private val viewModel: DevicesViewModel by fragmentViewModel()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        views.waitingView.waitingStatusText.setText(R.string.please_wait)
        views.waitingView.waitingStatusText.isVisible = true
        devicesController.callback = this
        views.genericRecyclerView.configureWith(devicesController, showDivider = true)
        viewModel.observeViewEvents {
            when (it) {
                is DevicesViewEvents.Loading            -> showLoading(it.message)
                is DevicesViewEvents.Failure            -> showFailure(it.throwable)
                is DevicesViewEvents.RequestPassword    -> maybeShowDeleteDeviceWithPasswordDialog()
                is DevicesViewEvents.PromptRenameDevice -> displayDeviceRenameDialog(it.deviceInfo)
                is DevicesViewEvents.ShowVerifyDevice   -> {
                    VerificationBottomSheet.withArgs(
                            roomId = null,
                            otherUserId = it.userId,
                            transactionId = it.transactionId
                    ).show(childFragmentManager, "REQPOP")
                }
                is DevicesViewEvents.SelfVerification   -> {
                    VerificationBottomSheet.forSelfVerification(it.session)
                            .show(childFragmentManager, "REQPOP")
                }
                is DevicesViewEvents.ShowManuallyVerify -> {
                    ManuallyVerifyDialog.show(requireActivity(), it.cryptoDeviceInfo) {
                        viewModel.handle(DevicesAction.MarkAsManuallyVerified(it.cryptoDeviceInfo))
                    }
                }
            }.exhaustive
        }
    }

    override fun showFailure(throwable: Throwable) {
        super.showFailure(throwable)

        // Password is maybe not good, for safety measure, reset it here
        mAccountPass = ""
    }

    override fun onDestroyView() {
        devicesController.callback = null
        views.genericRecyclerView.cleanup()
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        (activity as? AppCompatActivity)?.supportActionBar?.setTitle(R.string.settings_active_sessions_manage)
        viewModel.handle(DevicesAction.Refresh)
    }

    override fun onDeviceClicked(deviceInfo: DeviceInfo) {
        DeviceVerificationInfoBottomSheet.newInstance(deviceInfo.userId ?: "", deviceInfo.deviceId ?: "").show(
                childFragmentManager,
                "VERIF_INFO"
        )
    }

//    override fun onDeleteDevice(deviceInfo: DeviceInfo) {
//        devicesViewModel.handle(DevicesAction.Delete(deviceInfo))
//    }
//
//    override fun onRenameDevice(deviceInfo: DeviceInfo) {
//        displayDeviceRenameDialog(deviceInfo)
//    }

    override fun retry() {
        viewModel.handle(DevicesAction.Refresh)
    }

    /**
     * Display an alert dialog to rename a device
     *
     * @param deviceInfo device info
     */
    private fun displayDeviceRenameDialog(deviceInfo: DeviceInfo) {
        val inflater = requireActivity().layoutInflater
        val layout = inflater.inflate(R.layout.dialog_base_edit_text, null)
        val views = DialogBaseEditTextBinding.bind(layout)
        views.editText.setText(deviceInfo.displayName)

        AlertDialog.Builder(requireActivity())
                .setTitle(R.string.devices_details_device_name)
                .setView(layout)
                .setPositiveButton(R.string.ok) { _, _ ->
                    val newName = views.editText.text.toString()

                    viewModel.handle(DevicesAction.Rename(deviceInfo.deviceId!!, newName))
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
    }

    /**
     * Show a dialog to ask for user password, or use a previously entered password.
     */
    private fun maybeShowDeleteDeviceWithPasswordDialog() {
        if (mAccountPass.isNotEmpty()) {
            viewModel.handle(DevicesAction.Password(mAccountPass))
        } else {
            PromptPasswordDialog().show(requireActivity()) { password ->
                mAccountPass = password
                viewModel.handle(DevicesAction.Password(mAccountPass))
            }
        }
    }

    override fun invalidate() = withState(viewModel) { state ->
        devicesController.update(state)

        handleRequestStatus(state.request)
    }

    private fun handleRequestStatus(unIgnoreRequest: Async<Unit>) {
        views.waitingView.root.isVisible = when (unIgnoreRequest) {
            is Loading -> true
            else       -> false
        }
    }
}
