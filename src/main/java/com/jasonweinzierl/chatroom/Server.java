package com.jasonweinzierl.chatroom;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

/**
 * chatroom
 *
 * @author JasonWeinzierl
 * @version 2019-04-18
 */
public class Server implements AutoCloseable
{
    private PrintWriter serverOut;

    private ServerSocket serverSocket;
    private final Map<Integer, Socket> clients = new HashMap<>();         // socket mapped to an id
    private final Map<String, Integer> activeLogins = new HashMap<>();    // id mapped to username
    private int counter;

    private final int maxClients;

    private final Map<String, String> logins = new HashMap<>();           // all available logins, active or not

    private PasswordAuthentication auther;

    private final PropertyChangeSupport boundProperties = new PropertyChangeSupport(this);

    public Server() {
        // default just print to console
        this(System.out);
    }

    /**
     * Makes a new server
     * Loads login information from plaintext file, or creates a new file.
     *
     * @param outputStream stream for printing server messages
     */
    public Server(OutputStream outputStream) {
        this.serverOut = new PrintWriter(outputStream, true);

        this.counter = 0;
        this.maxClients = 3;

        this.auther = new PasswordAuthentication();

        this.loadLogins();

        this.serverOut.println("Server ready to listen...");
    }

    private void loadLogins() {
        serverOut.println("Opening login information...");
        File f = new File("logins.txt");
        try (Scanner in = new Scanner(f)) {
            // match pattern
            in.findAll("\\w+:\\S+").forEach(matchResult -> {
                // split around colon and store
                String []login = matchResult.group(0).split(":");
                this.logins.put(login[0], login[1]);
            });
        } catch (FileNotFoundException e) {
            serverOut.println("No login file.");
            serverOut.println("Creating login file...");
            try {
                f.createNewFile();
            } catch (IOException ioException) {
                serverOut.println("Couldn't create login file: " + ioException.getMessage());
            }
        }

        serverOut.println(this.logins.size() + " logins loaded.");
    }

    /**
     * Start listening for connections
     *
     * @param port port to bind server to
     */
    public void listen(int port) {
        if (serverSocket != null) return;

        // don't block UI
        new Thread(() -> {
            try {
                // listen to port
                serverSocket = new ServerSocket(port);

                serverOut.println("Server listening on port " + port);

                // keep accepting new connections
                while(true) {
                    // wait for socket
                    Socket clientSocket = serverSocket.accept();

                    // enforce max clients
                    if (clients.size() + 1 > maxClients) {
                        clientSocket.getOutputStream().write("Server is full.  Goodbye.".getBytes());
                        clientSocket.getOutputStream().flush();
                        clientSocket.close();
                        continue;
                    }

                    // save connected sockets
                    clients.put(counter, clientSocket);

                    // start new client thread
                    new ClientHandler(clientSocket, counter).start();

                    counter++;
                }
            } catch (IOException ioexception) {
                serverOut.println("Server closed: " + ioexception.getMessage());
            }
        }).start();
    }

    /**
     * Handle client interactions on a separate thread
     */
    private class ClientHandler extends Thread implements AutoCloseable {
        private final Socket socket;
        private final int id;
        private PrintWriter out;
        private BufferedReader in;

        private boolean isLoggedIn;
        private String username;

        ClientHandler(Socket socket, int id) {
            this.socket = socket;
            this.id = id;
            this.isLoggedIn = false;
            this.username = null;
        }

        @Override
        public void run() {
            try {
                // set input and output
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // greet new client
                out.println("Welcome to the server.  You are Client " + id);
                out.println("Type /help for command list.");
                serverOut.println("New Client " + id + " has connected from " + socket.getRemoteSocketAddress());

                // handle data from socket
                this.handleData();

                // no more data, socket and io closed
                clients.remove(id);
                serverOut.println("Client " + id + " has disconnected.");
            } catch (IOException ioException) {
                clients.remove(id);
                serverOut.println("Client " + id + " abruptly closed: " + ioException.getMessage());
            }
        }

        /**
         * Parses incoming socket data on a loop
         *
         * @throws IOException Thrown when socket closes
         */
        private void handleData() throws IOException {
            String inputLine;
            // read input until socket closes or client causes return
            while ((inputLine = in.readLine()) != null) {
                // send chat if input is not a command
                if (!inputLine.startsWith("/")) {
                    this.send("all " + inputLine);
                    continue;
                }

                // split command and arguments
                String command = inputLine.indexOf(' ') == -1 ? inputLine : inputLine.substring(0, inputLine.indexOf(' '));
                String data = inputLine.substring(inputLine.indexOf(' ') + 1);

                // execute commands
                switch(command) {
                    case "/exit":
                        out.println("Exiting.");
                        if (this.isLoggedIn) this.logout();

                        this.close();
                        return;
                    case "/login":
                        this.login(data);
                        break;
                    case "/logout":
                        if (!this.isLoggedIn) {
                            out.println("You are not logged in.");
                            serverOut.println("Failed logout command from Client " + id);
                        } else {
                            this.logout();
                        }
                        break;
                    case "/newuser":
                        this.newUser(data);
                        break;
                    case "/say":
                        this.send(data);
                        break;
                    case "/who":
                        this.who();
                        break;
                    case "/whoami":
                        this.whoami();
                        break;
                    case "/help":
                        this.help();
                        break;
                    default:
                        out.println("Command `" + command + "` not understood.");
                        serverOut.println("Client " + id + " send unrecognized input: " + inputLine);
                        break;
                }
            }

            this.close();
        }

        @Override
        public void close() throws IOException {
            if (this.out != null) this.out.close();
            if (this.in != null) this.in.close();
            if (this.socket != null) this.socket.close();
            serverOut.println("Client " + id + " exit.");
        }

        /**
         * Logs user in
         *
         * @param data username and password separated by a space
         */
        private void login(String data) {
            // can't re-login
            if (this.isLoggedIn) {
                out.println("Already logged in.");
                serverOut.println("Client " + id + " sent empty /login command.");
                return;
            }

            // two arguments
            String []args = data.split(" ");
            if (args.length != 2) {
                out.println("You cannot login with empty information.");
                serverOut.println("Client " + id + " sent empty /login command.");
                return;
            }
            String username = args[0];
            String password = args[1];

            // can't use active login
            if (activeLogins.containsKey(username)) {
                out.println(username + " is already logged in.");
                serverOut.println("Client " + id + " tried to log in to active login " + username);
                return;
            }

            // validate username
            if (!logins.containsKey(username)) {
                out.println("Username or password incorrect.");
                serverOut.println(username + " was provided as incorrect username on Client " + id);
                return;
            }

            // validate password
            if (!auther.verify(password.toCharArray(), logins.get(username))) {
                out.println("username or Password incorrect.");
                serverOut.println("Failed login attempt to " + username + " on Client " + id);
                return;
            }

            this.isLoggedIn = true;
            this.username = username;
            activeLogins.put(this.username, this.id);

            // notify
            serverOut.println("Logged in user " + this.username + " on Client " + id);
            activeLogins.forEach((loginUsername, loginClientId) -> {
                try {
                    clients.get(loginClientId).getOutputStream().write((this.username + " logged in.").getBytes());
                    clients.get(loginClientId).getOutputStream().flush();
                } catch (IOException ioException) {
                    serverOut.println("Client " + loginClientId + " was unresponsive: " + ioException);
                }
            });
        }

        /**
         * Logs user out
         */
        private void logout() {
            // notify of logout
            serverOut.println(this.username + " logged out.");
            activeLogins.forEach((loginUsername, loginClientId) -> {
                try {
                    clients.get(loginClientId).getOutputStream().write((this.username + " logged out.").getBytes());
                    clients.get(loginClientId).getOutputStream().flush();
                } catch (IOException ioException) {
                    serverOut.println("Client " + loginClientId + " was unresponsive: " + ioException);
                }
            });

            this.isLoggedIn = false;
            activeLogins.remove(this.username);
            this.username = null;
        }

        /**
         * Creates a new user and saves to login file
         *
         * @param data new username and password separated by a space
         */
        private void newUser(String data) {
            // two arguments
            String []args = data.split(" ");
            if (args.length != 2) {
                out.println("You cannot create a new user with empty information.");
                serverOut.println("Client " + id + " sent empty /newuser command.");
                return;
            }
            String username = args[0];
            String password = args[1];

            // can't make user while logged in
            if (this.isLoggedIn) {
                out.println("Already logged in.");
                serverOut.println("User " + this.username + " attempted newuser.");
                return;
            }

            // can't recreate user
            if (logins.containsKey(username)) {
                out.println("User already exists.");
                serverOut.println("Client " + id + " tried to recreate `" + username + "`.");
                return;
            }

            // password policy
            int minLength = 8;
            int maxLength = 64;
            if (password.length() < minLength || maxLength < password.length()) {
                out.println("Password length must be between " + minLength + " and " + maxLength + " characters.");
                serverOut.println("Client " + id + " failed newuser password policy.");
                return;
            }

            // hash password
            password = auther.hash(password.toCharArray());

            // save to logins file
            try {
                Path file = Paths.get("logins.txt");
                Files.write(file, Collections.singletonList(username + ':' + password), Charset.forName("UTF-8"), StandardOpenOption.APPEND);
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
            serverOut.println(username + " appended to logins.txt");

            logins.put(username, password);

            // log in user
            this.isLoggedIn = true;
            this.username = username;
            activeLogins.put(this.username, this.id);

            // notify of new user and login
            serverOut.println("Created and logged in user " + this.username + " on Client " + id);
            activeLogins.forEach((loginUsername, loginClientId) -> {
                try {
                    clients.get(loginClientId).getOutputStream().write((this.username + " logged in with a new account.").getBytes());
                    clients.get(loginClientId).getOutputStream().flush();
                } catch (IOException ioException) {
                    serverOut.println("Client " + loginClientId + " was unresponsive: " + ioException);
                }
            });
        }

        /**
         * Sends text message to other users
         *
         * @param data message information
         */
        private void send(String data) {
            String intended = data.indexOf(' ') == -1 ? data : data.substring(0, data.indexOf(' '));
            String message = data.indexOf(' ') == -1 ? "" : data.substring(data.indexOf(' ') + 1);

            // check for login
            if (!this.isLoggedIn) {
                out.println("You cannot chat without logging in.");
                serverOut.println("Client " + id + " attempted to send '" + message.substring(0, Math.min(message.length(), 100)) + "' without login.");
                return;
            }

            // all
            if (intended.compareToIgnoreCase("all") == 0) {
                // broadcast message to all logged in users
                serverOut.println(this.username + ": " + data);
                activeLogins.forEach((loginUsername, loginClientId) -> {
                    try {
                        if (loginUsername.compareToIgnoreCase(this.username) == 0) {
                            // talk to yourself
                            clients.get(loginClientId).getOutputStream().write(("you: " + message).getBytes());
                            clients.get(loginClientId).getOutputStream().flush();
                        } else {
                            clients.get(loginClientId).getOutputStream().write((this.username + ": " + message).getBytes());
                            clients.get(loginClientId).getOutputStream().flush();
                        }
                    } catch (IOException ioException) {
                        serverOut.println("Client " + loginClientId + " was unresponsive: " + ioException);
                        this.out.println(loginUsername + " was unresponsive.");
                    }
                });
                return;
            }

            // talking to myself
            if (intended.compareToIgnoreCase(this.username) == 0) {
                this.out.println("you (from yourself): " + message);
                serverOut.println(this.username + " (to themself): " + message);
                return;
            }

            // find intended and write message to them
            Socket s = clients.get(activeLogins.get(intended));
            if (s != null) {
                try {
                    s.getOutputStream().write((this.username + "(to you): " + message).getBytes());
                    s.getOutputStream().flush();
                    this.out.println("you (to " + intended + "): " + message);
                    serverOut.println(this.username + "(to " + intended + "): " + message);
                } catch (IOException ioException) {
                    serverOut.println("Client " + activeLogins.get(intended) + " was unresponsive: " + ioException);
                    this.out.println(intended + " was unresponsive.");
                }
            } else {
                this.out.println(intended + " is not on this server.");
                serverOut.println(this.username + " failed to send message to " + intended + " because intended is not logged in.");
            }
        }

        /**
         * Displays all logged-in users
         */
        private void who() {
            serverOut.println("Client " + id + " sent /who command.");

            // loop over logged-in clients
            activeLogins.forEach((clientUsername, clientId) -> {
                out.println(activeLogins.get(clientUsername) + "\t\tClient " + clientId + '\t' + clients.get(clientId).getRemoteSocketAddress());
            });
            out.println(activeLogins.size() + " logged in users.");
        }

        /**
         * Display client id or logged in username
         */
        private void whoami() {
            serverOut.println("Client " + id + " sent /whoami command.");

            if (this.isLoggedIn) out.print(this.username + '\t');
            out.println("Client " + id);
        }

        private void help() {
            out.println("Command list:");
            out.println("\t/help - this message");
            out.println("\t/login [UserID] [Password] - log in to chatroom");
            out.println("\t/newuser [UserID] [Password] - create new user and log in");
            out.println("\t/say [all|UserID] [message] - send a message to a specific user");
            out.println("\t/who - list logged in users");
            out.println("\t/whoami - display current user or current client id");
            out.println("\t/logout - leave chat room");
            out.println("\t/exit - end client connection to server");
        }
    }

    /**
     * Closes all sockets and server
     */
    @Override
    public void close() {
        try {
            this.serverOut.close();
            this.activeLogins.clear();
            // loop over all entries
            for (Map.Entry<Integer, Socket> s : this.clients.entrySet()) {
                // close sockets
                s.getValue().close();
            }
            // close server
            if (this.serverSocket != null)
                serverSocket.close();
        } catch (IOException ioexception) {
            ioexception.printStackTrace();
        }
    }

    public static boolean available(int port) {
        if (port < 1024 || 49151 < port) {
//            throw new IllegalArgumentException("Invalid port: " + port);
            return false;
        }

        // try to open server.  try-with-resources will close it when done
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            // allow new sockets to use port without waiting for timeout
            serverSocket.setReuseAddress(true);

            return true;
        } catch (IOException ioException) {
            return false;
        }
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        this.boundProperties.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        this.boundProperties.removePropertyChangeListener(listener);
    }
}
