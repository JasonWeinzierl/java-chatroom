package com.jasonweinzierl.chatroom;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * chatroom
 *
 * @author JasonWeinzierl
 * @version 2019-04-18
 */
public class ChatApplication extends Application {
    private Startable controller;

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/View.fxml"));
        Parent root = loader.load();

        this.controller = loader.getController();

        primaryStage.setScene(new Scene(root, 800, 600));
        primaryStage.setTitle("Chatroom");
        primaryStage.sizeToScene();
        primaryStage.show();

        // start controller after controller is initialized
        controller.start(primaryStage);
    }

    @Override
    public void stop(){
        controller.stop();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
