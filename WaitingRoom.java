package server;

import java.util.concurrent.CopyOnWriteArrayList;

public class WaitingRoom {
    private CopyOnWriteArrayList<ClientHandler> waitingPlayers = new CopyOnWriteArrayList<>();

    public void addPlayer(ClientHandler player) {
        waitingPlayers.add(player);
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
