package server;

import java.util.concurrent.CopyOnWriteArrayList;

public class WaitingRoom {
    private static final int MAX_PLAYERS = 4;     
    private CopyOnWriteArrayList<ClientHandler> waitingPlayers = new CopyOnWriteArrayList<>();

    public void addPlayer(ClientHandler player) {
        
        if (waitingPlayers.size() >= MAX_PLAYERS) {
            player.sendMessage("WAITING_FULL:Room is full (max 4 players)");
            System.out.println("[WaitingRoom] Rejected " + player.getUsername() + " - room full");
            return;
        }
        waitingPlayers.add(player);
        player.sendMessage("PLAY_OK:Added to waiting room");  
        broadcastWaitingList();
        System.out.println("[WaitingRoom] " + player.getUsername() + " joined. Total: " + waitingPlayers.size());
    }

    public void removePlayer(ClientHandler player) {
        waitingPlayers.remove(player);
        broadcastWaitingList();
        System.out.println("[WaitingRoom] " + player.getUsername() + " left. Remaining: " + waitingPlayers.size());
    }

    private void broadcastWaitingList() {
        StringBuilder sb = new StringBuilder("WAITING_LIST:");
        for (ClientHandler p : waitingPlayers) {
            sb.append(p.getUsername()).append(",");
        }
        String msg = sb.toString();
        for (ClientHandler p : waitingPlayers) {
            p.sendMessage(msg);
        }
    }
}
