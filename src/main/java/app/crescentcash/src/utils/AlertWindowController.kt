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

package app.crescentcash.src.utils

import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.stage.Stage

class AlertWindowController {
    @FXML
    var messageLabel: Label? = null
    @FXML
    var detailsLabel: Label? = null
    @FXML
    var okButton: Button? = null
    @FXML
    var cancelButton: Button? = null
    @FXML
    var actionButton: Button? = null

    fun crashAlert(stage: Stage, crashMessage: String) {
        messageLabel!!.text = "Unfortunately, Crescent Cash has crashed."
        detailsLabel!!.text = crashMessage
        cancelButton!!.isVisible = false
        actionButton!!.isVisible = false
        okButton!!.setOnAction { actionEvent -> stage.close() }
    }

    fun informational(stage: Stage, message: String, details: String) {
        messageLabel!!.text = message
        detailsLabel!!.text = details
        cancelButton!!.isVisible = false
        actionButton!!.isVisible = false
        okButton!!.setOnAction { actionEvent -> stage.close() }
    }
}
