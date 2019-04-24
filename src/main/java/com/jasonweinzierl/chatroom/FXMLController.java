package com.jasonweinzierl.chatroom;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.OutputStream;
import java.net.URL;
import java.util.ResourceBundle;

/**
 * chatroom
 *
 * @author JasonWeinzierl
 * @version 2019-04-18
 */
public class FXMLController implements Initializable, Startable, PropertyChangeListener
{
    private Stage stage;

    @FXML
    private TextArea textArea;

    @FXML
    private TextField textField;

    @FXML
    private Button serverButton;

    @FXML
    private Button clientButton;

    @FXML
    private Spinner<Integer> portSpinner;

    private OutputStream out;

    private Server server;
    private Client client;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        this.out = new OutputStream() {
            @Override
            public void write(int b)
            {
                // make sure to run on JavaFX thread
                Platform.runLater(() -> textArea.appendText(String.valueOf((char) b)));
            }
        };
    }

    @Override
    public void start(Stage stage) {
        this.stage = stage;
    }

    @Override
    public void stop() {
        this.handleExit(new ActionEvent());
    }

    @FXML
    public void handleExit(ActionEvent event) {
        if (this.server != null) this.server.close();
        if (this.client != null) this.client.close();
        Platform.exit();
    }

    @FXML
    public void handleStartServer(ActionEvent event) {
        this.stage.setTitle("Chatroom: Server");

        this.serverButton.setDisable(true);
        this.clientButton.setDisable(true);

        this.server = new Server(this.out);
        this.server.addPropertyChangeListener(this);

        this.server.listen(10119);
    }

    @FXML
    public void handleStartClient(ActionEvent event) {
        this.stage.setTitle("Chatroom: Client");
        this.serverButton.setDisable(true);
        this.clientButton.setDisable(true);

        this.client = new Client(this.out);
        this.client.addPropertyChangeListener(this);
        this.client.connect("localhost", 10119);

        this.textField.setVisible(true);
        this.textField.setOnAction(actionEvent -> {
            this.client.write(this.textField.getText());
            this.textField.clear();
        });
        this.textField.setEditable(true);
    }

    /**
     * This method gets called when a bound property is changed.
     *
     * @param evt A PropertyChangeEvent object describing the event source and the property that has changed.
     */
    @Override
    public void propertyChange(PropertyChangeEvent evt)
    {
        switch (evt.getPropertyName()) {
            case "close":
                Platform.runLater(() -> {
                    this.client = null;
                    this.server = null;
                    stage.setTitle("Chatroom");
                    serverButton.setDisable(false);
                    clientButton.setDisable(false);
                    textField.setVisible(false);
                });
                break;
            default:
                break;
        }
    }
}
