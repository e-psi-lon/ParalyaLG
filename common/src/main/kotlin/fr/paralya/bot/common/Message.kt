package fr.paralya.bot.common

import dev.kordex.core.components.forms.ModalForm
import dev.kordex.i18n.Key
import fr.paralya.bot.common.I18n.Modal

const val MAX_MESSAGE_LENGTH = 2000
/**
 * Message is a modal form used to ask for input from the user.
 *
 *
 * @constructor Creates a new Message instance.
 *
 * @property title The title of the modal form.
 * @property message The message field of the modal form.
 */
open class MessageForm : ModalForm() {
	override var title: Key = Modal.Message.title

	val message = paragraphText {
		label = Modal.Message.label
		placeholder = Modal.Message.placeholder
		maxLength = MAX_MESSAGE_LENGTH
	}
}
