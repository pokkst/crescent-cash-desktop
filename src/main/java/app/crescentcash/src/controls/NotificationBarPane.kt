/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.crescentcash.src.controls

import app.crescentcash.src.utils.GuiUtils
import app.crescentcash.src.utils.easing.EasingMode
import app.crescentcash.src.utils.easing.ElasticInterpolator
import javafx.animation.Interpolator
import javafx.animation.KeyFrame
import javafx.animation.KeyValue
import javafx.animation.Timeline
import javafx.beans.property.SimpleStringProperty
import javafx.beans.value.ObservableDoubleValue
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.control.ProgressBar
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.util.Duration

/**
 * Wraps the given Node in a BorderPane and allows a thin bar to slide in from the bottom or top, squeezing the content
 * node. The API allows different "items" to be added/removed and they will be displayed one at a time, fading between
 * them when the topmost is removed. Each item is meant to be used for e.g. a background task and can contain a button
 * and/or a progress bar.
 */
class NotificationBarPane(content: Node) : BorderPane(content) {

    private val bar: HBox
    private val label: Label = Label("infobar!")
    private var barHeight: Double = 0.toDouble()
    private val progressBar: ProgressBar = ProgressBar()

    val items: ObservableList<Item>

    val isShowing: Boolean
        get() = bar.prefHeight > 0

    private var timeline: Timeline? = null

    inner class Item(label: String, val progress: ObservableDoubleValue?) {
        val label: SimpleStringProperty = SimpleStringProperty(label)

        fun cancel() {
            items.remove(this)
        }
    }

    init {
        bar = HBox(label)
        bar.minHeight = 0.0
        bar.styleClass.add("info-bar")
        bar.isFillHeight = true
        bottom = bar
        // Figure out the height of the bar based on the css. Must wait until after we've been added to the parent node.
        sceneProperty().addListener { o ->
            if (parent == null) return@addListener
            parent.applyCss()
            parent.layout()
            barHeight = bar.height
            bar.prefHeight = 0.0
        }
        items = FXCollections.observableArrayList()
    }

    private fun config() {
        if (items.isEmpty()) return
        val item = items[0]

        bar.children.clear()
        label.textProperty().bind(item.label)
        label.maxWidth = java.lang.Double.MAX_VALUE
        HBox.setHgrow(label, Priority.ALWAYS)
        bar.children.add(label)
        if (item.progress != null) {
            progressBar.minWidth = 200.0
            progressBar.progressProperty().bind(item.progress)
            bar.children.add(progressBar)
        }
    }

    private fun showOrHide() {
        if (items.isEmpty())
            animateOut()
        else
            animateIn()
    }

    private fun animateIn() {
        animate(barHeight)
    }

    private fun animateOut() {
        animate(0.0)
    }

    private fun animate(target: Number) {
        if (timeline != null) {
            timeline!!.stop()
            timeline = null
        }
        val duration: Duration
        val interpolator: Interpolator
        if (target.toInt() > 0) {
            interpolator = ElasticInterpolator(EasingMode.EASE_OUT, 1.0, 2.0)
            duration = ANIM_IN_DURATION
        } else {
            interpolator = Interpolator.EASE_OUT
            duration = ANIM_OUT_DURATION
        }
        val kf = KeyFrame(duration, KeyValue(bar.prefHeightProperty(), target, interpolator))
        timeline = Timeline(kf)
        timeline!!.setOnFinished { timeline = null }
        timeline!!.play()
    }

    fun pushItem(string: String, progress: ObservableDoubleValue): Item {
        val i = Item(string, progress)
        items.add(i)
        return i
    }

    companion object {
        val ANIM_IN_DURATION: Duration = GuiUtils.UI_ANIMATION_TIME.multiply(2.0)
        val ANIM_OUT_DURATION: Duration = GuiUtils.UI_ANIMATION_TIME
    }
}