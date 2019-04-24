package com.jasonweinzierl.chatroom;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.*;
import java.net.ConnectException;
import java.net.Socket;

/**
 * chatroom
 *
 * @author JasonWeinzierl
 * @version 2019-04-18
 */
public class Client implements AutoCloseable
{
    private Socket clientSocket;
    private PrintWriter socketOut;
    private BufferedReader in;

    private PrintWriter localOut;

    private final PropertyChangeSupport boundProperties = new PropertyChangeSupport(this);

    public Client() {
        this(System.out);
    }

    public Client(OutputStream outputStream) {
        this.localOut = new PrintWriter(outputStream, true);
    }

    public void connect(String ip, int port) {
        new Thread(() -> {
            try {
                // connect to server
                clientSocket = new Socket(ip, port);

                // set input and output
                socketOut = new PrintWriter(clientSocket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

                String inputLine;
                // read input until socket closes
                while ((inputLine = in.readLine()) != null) {
                    this.localOut.println(inputLine);
                }
            } catch (ConnectException connectException) {
                localOut.println(connectException.getMessage() + ":\t The server is probably inactive.");
            } catch (IOException ioexception) {
                localOut.println("No longer connected: " + ioexception.getMessage());
            } finally {
                this.close();
                this.boundProperties.firePropertyChange("close", false, true);
            }
        }).start();
    }

    public void write(String msg) {
        socketOut.println(msg);
    }

    @Override
    public void close() {
        try {
            if (this.clientSocket != null) clientSocket.close();
            if (this.in != null) in.close();    // close after socket closes to interrupt readLine
        } catch (IOException ioexception) {
            localOut.println("Error closing: " + ioexception.getMessage());
        }
        if (this.socketOut != null) socketOut.close();
        localOut.close();
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        this.boundProperties.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        this.boundProperties.removePropertyChangeListener(listener);
    }
}
