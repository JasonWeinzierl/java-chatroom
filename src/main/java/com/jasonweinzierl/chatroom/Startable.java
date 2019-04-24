package com.jasonweinzierl.chatroom;

import javafx.stage.Stage;

/**
 * chatroom
 *
 * @author JasonWeinzierl
 * @version 2019-04-18
 */
public interface Startable
{
    public void start(Stage stage);

    public void stop();
}
