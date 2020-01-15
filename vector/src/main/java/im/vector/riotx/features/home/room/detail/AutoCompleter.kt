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

package im.vector.riotx.features.home.room.detail

import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.Spannable
import android.widget.EditText
import com.otaliastudios.autocomplete.Autocomplete
import com.otaliastudios.autocomplete.AutocompleteCallback
import com.otaliastudios.autocomplete.CharPolicy
import com.squareup.inject.assisted.Assisted
import com.squareup.inject.assisted.AssistedInject
import im.vector.matrix.android.api.session.group.model.GroupSummary
import im.vector.matrix.android.api.session.room.model.RoomMemberSummary
import im.vector.matrix.android.api.session.room.model.RoomSummary
import im.vector.matrix.android.api.util.MatrixItem
import im.vector.matrix.android.api.util.toMatrixItem
import im.vector.matrix.android.api.util.toRoomAliasMatrixItem
import im.vector.riotx.R
import im.vector.riotx.core.glide.GlideApp
import im.vector.riotx.features.autocomplete.command.AutocompleteCommandPresenter
import im.vector.riotx.features.autocomplete.command.CommandAutocompletePolicy
import im.vector.riotx.features.autocomplete.emoji.AutocompleteEmojiPresenter
import im.vector.riotx.features.autocomplete.group.AutocompleteGroupPresenter
import im.vector.riotx.features.autocomplete.member.AutocompleteMemberPresenter
import im.vector.riotx.features.autocomplete.room.AutocompleteRoomPresenter
import im.vector.riotx.features.command.Command
import im.vector.riotx.features.home.AvatarRenderer
import im.vector.riotx.features.html.PillImageSpan
import im.vector.riotx.features.themes.ThemeUtils

class AutoCompleter @AssistedInject constructor(
        @Assisted val roomId: String,
        private val avatarRenderer: AvatarRenderer,
        private val commandAutocompletePolicy: CommandAutocompletePolicy,
        private val autocompleteCommandPresenter: AutocompleteCommandPresenter,
        private val autocompleteMemberPresenterFactory: AutocompleteMemberPresenter.Factory,
        private val autocompleteRoomPresenter: AutocompleteRoomPresenter,
        private val autocompleteGroupPresenter: AutocompleteGroupPresenter,
        private val autocompleteEmojiPresenter: AutocompleteEmojiPresenter
) {

    @AssistedInject.Factory
    interface Factory {
        fun create(roomId: String): AutoCompleter
    }

    private lateinit var editText: EditText

    fun enterSpecialMode() {
        commandAutocompletePolicy.enabled = false
    }

    fun exitSpecialMode() {
        commandAutocompletePolicy.enabled = true
    }

    private val glideRequests by lazy {
        GlideApp.with(editText)
    }

    fun setup(editText: EditText) {
        this.editText = editText
        val backgroundDrawable = ColorDrawable(ThemeUtils.getColor(editText.context, R.attr.riotx_background))
        setupCommands(backgroundDrawable, editText)
        setupMembers(backgroundDrawable, editText)
        setupGroups(backgroundDrawable, editText)
        setupEmojis(backgroundDrawable, editText)
        setupRooms(backgroundDrawable, editText)
    }

    private fun setupCommands(backgroundDrawable: Drawable, editText: EditText) {
        Autocomplete.on<Command>(editText)
                .with(commandAutocompletePolicy)
                .with(autocompleteCommandPresenter)
                .with(ELEVATION)
                .with(backgroundDrawable)
                .with(object : AutocompleteCallback<Command> {
                    override fun onPopupItemClicked(editable: Editable, item: Command): Boolean {
                        editable.clear()
                        editable
                                .append(item.command)
                                .append(" ")
                        return true
                    }

                    override fun onPopupVisibilityChanged(shown: Boolean) {
                    }
                })
                .build()
    }

    private fun setupMembers(backgroundDrawable: ColorDrawable, editText: EditText) {
        val autocompleteMemberPresenter = autocompleteMemberPresenterFactory.create(roomId)
        Autocomplete.on<RoomMemberSummary>(editText)
                .with(CharPolicy('@', true))
                .with(autocompleteMemberPresenter)
                .with(ELEVATION)
                .with(backgroundDrawable)
                .with(object : AutocompleteCallback<RoomMemberSummary> {
                    override fun onPopupItemClicked(editable: Editable, item: RoomMemberSummary): Boolean {
                        insertMatrixItem(editText, editable, "@", item.toMatrixItem())
                        return true
                    }

                    override fun onPopupVisibilityChanged(shown: Boolean) {
                    }
                })
                .build()
    }

    private fun setupRooms(backgroundDrawable: ColorDrawable, editText: EditText) {
        Autocomplete.on<RoomSummary>(editText)
                .with(CharPolicy('#', true))
                .with(autocompleteRoomPresenter)
                .with(ELEVATION)
                .with(backgroundDrawable)
                .with(object : AutocompleteCallback<RoomSummary> {
                    override fun onPopupItemClicked(editable: Editable, item: RoomSummary): Boolean {
                        insertMatrixItem(editText, editable, "#", item.toRoomAliasMatrixItem())
                        return true
                    }

                    override fun onPopupVisibilityChanged(shown: Boolean) {
                    }
                })
                .build()
    }

    private fun setupGroups(backgroundDrawable: ColorDrawable, editText: EditText) {
        Autocomplete.on<GroupSummary>(editText)
                .with(CharPolicy('+', true))
                .with(autocompleteGroupPresenter)
                .with(ELEVATION)
                .with(backgroundDrawable)
                .with(object : AutocompleteCallback<GroupSummary> {
                    override fun onPopupItemClicked(editable: Editable, item: GroupSummary): Boolean {
                        insertMatrixItem(editText, editable, "+", item.toMatrixItem())
                        return true
                    }

                    override fun onPopupVisibilityChanged(shown: Boolean) {
                    }
                })
                .build()
    }

    private fun setupEmojis(backgroundDrawable: Drawable, editText: EditText) {
        Autocomplete.on<String>(editText)
                .with(CharPolicy(':', false))
                .with(autocompleteEmojiPresenter)
                .with(ELEVATION)
                .with(backgroundDrawable)
                .with(object : AutocompleteCallback<String> {
                    override fun onPopupItemClicked(editable: Editable, item: String): Boolean {
                        // Detect last ":" and remove it
                        var startIndex = editable.lastIndexOf(":")
                        if (startIndex == -1) {
                            startIndex = 0
                        }

                        // Detect next word separator
                        var endIndex = editable.indexOf(" ", startIndex)
                        if (endIndex == -1) {
                            endIndex = editable.length
                        }

                        // Replace the word by its completion
                        editable.replace(startIndex, endIndex, item)
                        return true
                    }

                    override fun onPopupVisibilityChanged(shown: Boolean) {
                    }
                })
                .build()
    }

    private fun insertMatrixItem(editText: EditText, editable: Editable, firstChar: String, matrixItem: MatrixItem) {
        // Detect last firstChar and remove it
        var startIndex = editable.lastIndexOf(firstChar)
        if (startIndex == -1) {
            startIndex = 0
        }

        // Detect next word separator
        var endIndex = editable.indexOf(" ", startIndex)
        if (endIndex == -1) {
            endIndex = editable.length
        }

        // Replace the word by its completion
        val displayName = matrixItem.getBestName()

        // with a trailing space
        editable.replace(startIndex, endIndex, "$displayName ")

        // Add the span
        val span = PillImageSpan(
                glideRequests,
                avatarRenderer,
                editText.context,
                matrixItem
        )
        span.bind(editText)

        editable.setSpan(span, startIndex, startIndex + displayName.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    companion object {
        private const val ELEVATION = 6f
    }
}
