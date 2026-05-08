package server;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

public class WaitingRoom {

    private static final int MAX_PLAYERS = 4;
    private CopyOnWriteArrayList<ClientHandler> waitingPlayers = new CopyOnWriteArrayList<>();
    private Server server;

    public WaitingRoom(Server server) {
        this.server = server;
    }

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

        if (server != null) {
            server.getGameLogic().onPlayerJoinedWaitingRoom(waitingPlayers.size());
            server.getGameLogic().checkAndStartGameIfFull(waitingPlayers.size());
        }
    }

    public void removePlayer(ClientHandler player) {
        waitingPlayers.remove(player);
        broadcastWaitingList();
        System.out.println("[WaitingRoom] " + player.getUsername() + " left. Remaining: " + waitingPlayers.size());
    }

    private void broadcastWaitingList() {
        String names = waitingPlayers.stream()
                .map(ClientHandler::getUsername)
                .filter(name -> name != null && !name.isEmpty())
                .collect(Collectors.joining(","));

        server.broadcastToAll("WAITING_LIST:" + names);
    }

    public int getPlayerCount() {
        return waitingPlayers.size();
    }

    public List<ClientHandler> getAllPlayers() {
        return new ArrayList<>(waitingPlayers);
    }

    public void clear() {
        waitingPlayers.clear();
        System.out.println("[WaitingRoom] Room cleared");
    }

    public int getMaxPlayers() {
        return MAX_PLAYERS;
    }

    public boolean isFull() {
        return waitingPlayers.size() >= MAX_PLAYERS;
    }

    public boolean isEmpty() {
        return waitingPlayers.isEmpty();
    }
    
    public boolean isPlayerInRoom(ClientHandler player) {
        return waitingPlayers.contains(player);
    }
}
