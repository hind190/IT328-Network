package server;
 
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
 
public class ClientHandler extends Thread {
 
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String username;
    private Server server;
 
    public ClientHandler(Socket socket, Server server) {
        this.socket = socket;
        this.server = server;
 
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
 
    public String getUsername() {
        return username;
    }
 
    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }
 
    @Override
    public void run() {
        try {
            String firstLine = in.readLine();
            if (firstLine != null && firstLine.startsWith("CONNECT:")) {
                this.username = firstLine.substring(8);
                System.out.println("User '" + username + "' connected.");
                server.addClient(this);
                server.broadcastPlayerList();
                sendMessage("CONNECT_OK:" + username);
            } else {
                System.out.println("Invalid connection attempt, closing.");
                socket.close();
                return;
            }
 
            String message;
            while ((message = in.readLine()) != null) {
                System.out.println("Received from " + username + ": " + message);
 
                if (message.startsWith("PLAY")) {
                    server.addToWaitingRoom(this);
                    sendMessage("WAITING_JOINED:You have joined the waiting room");
                }
                else if (message.startsWith("LEAVE")) {

    server.handlePlayerLeave(this);

    sendMessage("LEFT:You left the game");

    server.broadcastPlayerList();
}
                else if (message.startsWith("ANSWER:")) {
                    String[] parts = message.split(":", 3);
                    if (parts.length >= 3) {
                        try {
                            int round = Integer.parseInt(parts[1]);
                            String answer = parts[2];
                            server.receiveAnswer(this, answer, round);
                        } catch (NumberFormatException e) {
                            sendMessage("ERROR:Invalid round number");
                        }
                    } else {
                        sendMessage("ERROR:Invalid answer format. Use: ANSWER:round:answer");
                    }
                }
                else if (message.startsWith("GAME_START_REQUEST")) {
                    if (server.getWaitingRoomPlayers().contains(this)) {
                        server.startGame();
                    } else {
                        sendMessage("ERROR:You are not in the waiting room");
                    }
                }
                else if (message.startsWith("GET_WAITING_ROOM")) {
                    String waitingList = server.getWaitingRoomPlayers().stream()
                            .map(ClientHandler::getUsername)
                            .collect(java.util.stream.Collectors.joining(","));
                    sendMessage("WAITING_LIST:" + waitingList);
                }
                else {
                    sendMessage("ECHO: " + message);
                }
            }
 
        } catch (IOException e) {
            System.out.println("Connection lost with client: " + username);
        } finally {
            if (username != null) {
                server.handlePlayerLeave(this);
                server.removeClient(this);
                server.broadcastPlayerList();
                System.out.println("User '" + username + "' removed from list.");
            }
 
            try {
                if (in != null) in.close();
                if (out != null) out.close();
                if (socket != null) socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
