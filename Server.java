package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class Server {
    
    private ServerSocket serverSocket;
    private List<ClientHandler> clients = new ArrayList<>();
    private WaitingRoom waitingRoom = new WaitingRoom();
    private boolean running = true;

    public void start(int port) {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Server is running on port " + port);

            while (running) {
                Socket socket = serverSocket.accept();
                System.out.println("New client connected: " + socket.getInetAddress());

                ClientHandler clientHandler = new ClientHandler(socket, this);
                clientHandler.start();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized void addClient(ClientHandler client) {
        clients.add(client);
    }

    public synchronized void removeClient(ClientHandler client) {
        clients.remove(client);
        waitingRoom.removePlayer(client);
    }

    public synchronized void broadcastPlayerList() {
        if (clients.isEmpty()) return;
        
        StringBuilder sb = new StringBuilder("PLAYER_LIST:");
        for (int i = 0; i < clients.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(clients.get(i).getUsername());
        }
        String msg = sb.toString();
        for (ClientHandler c : clients) {
            c.sendMessage(msg);
        }
    }

    public void addToWaitingRoom(ClientHandler handler) {
        waitingRoom.addPlayer(handler);
    }

    public void removeFromWaitingRoom(ClientHandler handler) {
        waitingRoom.removePlayer(handler);
    }

    public static void main(String[] args) {
        new Server().start(1234);
    }
}

