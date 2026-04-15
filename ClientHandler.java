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
        out.println(message);
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
                    // لا ترسل PLAY_OK هنا
                }
                else if (message.startsWith("LEAVE")) {
                    server.removeFromWaitingRoom(this);
                    sendMessage("LEFT:You left waiting room");
                }
                else {
                    sendMessage("ECHO: " + message);
                }
            }
            
        } catch (IOException e) {
            System.out.println("Connection lost with client: " + username);
        } finally {
            if (username != null) {
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
