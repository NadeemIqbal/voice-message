package io.github.nadeemiqbal.voicemessage

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Colour set for [VoiceRecorderInput]. Obtain an instance from
 * [VoiceRecorderDefaults.colors][VoiceMessageDefaults.recorderColors] and `.copy(...)` what you
 * need to override.
 *
 * @property micColor mic icon colour when idle.
 * @property micActiveColor mic icon colour while recording.
 * @property waveformColor colour of the live amplitude waveform shown during recording.
 * @property timerColor colour of the recording-elapsed-time chip.
 * @property hintTextColor colour of the "Slide up to lock" / "Slide to cancel" hint copy.
 * @property cancelIconColor colour of the bin / cancel icon (red-ish).
 * @property lockIconColor colour of the lock icon shown above the mic during recording.
 * @property containerColor background colour of the row that contains the mic, scrolling
 *   waveform and timer.
 * @property sendButtonColor background colour of the Send button shown in the locked state.
 * @property sendButtonContentColor icon/text colour on the Send button.
 */
@Immutable
class VoiceRecorderColors(
    val micColor: Color,
    val micActiveColor: Color,
    val waveformColor: Color,
    val timerColor: Color,
    val hintTextColor: Color,
    val cancelIconColor: Color,
    val lockIconColor: Color,
    val containerColor: Color,
    val sendButtonColor: Color,
    val sendButtonContentColor: Color,
) {
    fun copy(
        micColor: Color = this.micColor,
        micActiveColor: Color = this.micActiveColor,
        waveformColor: Color = this.waveformColor,
        timerColor: Color = this.timerColor,
        hintTextColor: Color = this.hintTextColor,
        cancelIconColor: Color = this.cancelIconColor,
        lockIconColor: Color = this.lockIconColor,
        containerColor: Color = this.containerColor,
        sendButtonColor: Color = this.sendButtonColor,
        sendButtonContentColor: Color = this.sendButtonContentColor,
    ): VoiceRecorderColors = VoiceRecorderColors(
        micColor, micActiveColor, waveformColor, timerColor, hintTextColor,
        cancelIconColor, lockIconColor, containerColor, sendButtonColor, sendButtonContentColor,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VoiceRecorderColors) return false
        return micColor == other.micColor &&
            micActiveColor == other.micActiveColor &&
            waveformColor == other.waveformColor &&
            timerColor == other.timerColor &&
            hintTextColor == other.hintTextColor &&
            cancelIconColor == other.cancelIconColor &&
            lockIconColor == other.lockIconColor &&
            containerColor == other.containerColor &&
            sendButtonColor == other.sendButtonColor &&
            sendButtonContentColor == other.sendButtonContentColor
    }

    override fun hashCode(): Int {
        var r = micColor.hashCode()
        r = 31 * r + micActiveColor.hashCode()
        r = 31 * r + waveformColor.hashCode()
        r = 31 * r + timerColor.hashCode()
        r = 31 * r + hintTextColor.hashCode()
        r = 31 * r + cancelIconColor.hashCode()
        r = 31 * r + lockIconColor.hashCode()
        r = 31 * r + containerColor.hashCode()
        r = 31 * r + sendButtonColor.hashCode()
        r = 31 * r + sendButtonContentColor.hashCode()
        return r
    }
}

/**
 * Colour set for [VoiceMessageBubble]. Sender and receiver variants share this type — the
 * defaults swap the bubble + bar colours based on [VoiceMessageRole].
 *
 * @property bubbleColor background of the chat bubble.
 * @property playedBarColor amplitude-bar colour for the section that has already played.
 * @property unplayedBarColor amplitude-bar colour for the not-yet-played section.
 * @property playIconColor play/pause button icon colour.
 * @property playIconBackgroundColor play/pause button background colour.
 * @property durationTextColor colour of the duration label.
 */
@Immutable
class VoiceMessageBubbleColors(
    val bubbleColor: Color,
    val playedBarColor: Color,
    val unplayedBarColor: Color,
    val playIconColor: Color,
    val playIconBackgroundColor: Color,
    val durationTextColor: Color,
    val speedChipColor: Color,
    val speedChipContentColor: Color,
) {
    fun copy(
        bubbleColor: Color = this.bubbleColor,
        playedBarColor: Color = this.playedBarColor,
        unplayedBarColor: Color = this.unplayedBarColor,
        playIconColor: Color = this.playIconColor,
        playIconBackgroundColor: Color = this.playIconBackgroundColor,
        durationTextColor: Color = this.durationTextColor,
        speedChipColor: Color = this.speedChipColor,
        speedChipContentColor: Color = this.speedChipContentColor,
    ): VoiceMessageBubbleColors = VoiceMessageBubbleColors(
        bubbleColor, playedBarColor, unplayedBarColor,
        playIconColor, playIconBackgroundColor, durationTextColor,
        speedChipColor, speedChipContentColor,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VoiceMessageBubbleColors) return false
        return bubbleColor == other.bubbleColor &&
            playedBarColor == other.playedBarColor &&
            unplayedBarColor == other.unplayedBarColor &&
            playIconColor == other.playIconColor &&
            playIconBackgroundColor == other.playIconBackgroundColor &&
            durationTextColor == other.durationTextColor &&
            speedChipColor == other.speedChipColor &&
            speedChipContentColor == other.speedChipContentColor
    }

    override fun hashCode(): Int {
        var r = bubbleColor.hashCode()
        r = 31 * r + playedBarColor.hashCode()
        r = 31 * r + unplayedBarColor.hashCode()
        r = 31 * r + playIconColor.hashCode()
        r = 31 * r + playIconBackgroundColor.hashCode()
        r = 31 * r + durationTextColor.hashCode()
        r = 31 * r + speedChipColor.hashCode()
        r = 31 * r + speedChipContentColor.hashCode()
        return r
    }
}
