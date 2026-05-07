



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
 
    // ✅ FIX: فحص null قبل الإرسال
    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }
 
    @Override
    public void run() {
        try {
            // ---------- مرحلة الاتصال (CONNECT) ----------
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
 
            // ---------- الحلقة الرئيسية ----------
            String message;
            while ((message = in.readLine()) != null) {
                System.out.println("Received from " + username + ": " + message);
 
                // ****************** PHASE 1 ******************
                if (message.startsWith("PLAY")) {
                    server.addToWaitingRoom(this);
                }
                else if (message.startsWith("LEAVE")) {
                    server.removeFromWaitingRoom(this);
                    sendMessage("LEFT:You left waiting room");
                    // ✅ FIX: تحديث القائمة لجميع المتصلين
                    server.broadcastPlayerList();
                }
 
                // ****************** PHASE 2 ******************
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
                    // ✅ FIX: التحقق أن اللاعب في غرفة الانتظار قبل البدء
                    if (server.getWaitingRoomPlayers().contains(this)) {
                        server.startGame();
                    } else {
                        sendMessage("ERROR:You are not in the waiting room");
                    }
                }
                else {
                    sendMessage("ECHO: " + message);
                }
            }
 
        } catch (IOException e) {
            System.out.println("Connection lost with client: " + username);
        } finally {
            if (username != null) {
                // ✅ FIX: handlePlayerLeave تتكفل بإزالة اللاعب من كل القوائم
                // لا نستدعي removeClient هنا لتجنب الاستدعاء المزدوج
                server.handlePlayerLeave(this);
                server.removeClient(this);
                server.broadcastPlayerList();
                System.out.println("User '" + username + "' removed from list.");
            }
 
            // إغلاق الموارد
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
 


-----------------------------------
package server;
 
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
 
public class GameLogic {
 
    private Server server;
 
    private Map<String, Integer> scores = new ConcurrentHashMap<>();
    private Map<String, Boolean> hasAnswered = new ConcurrentHashMap<>();
 
    private String correctAnswer;
    private int currentRound;
    private int answeredCount;
    private int totalPlayers;
    private boolean roundActive;
 
    // تايمر غرفة الانتظار
    private Timer waitingTimer;
    private boolean gameStarted = false;
    private int waitingPlayersCount = 0;
 
    // ✅ تايمر اللعبة (5 دقائق)
    private Timer gameTimer;
    private static final int GAME_DURATION_MS = 5 * 60 * 1000;
 
    private final Map<Integer, Question> questions = new HashMap<>();
 
    public GameLogic(Server server) {
        this.server = server;
        loadQuestions();
    }
 
    private void loadQuestions() {
        questions.put(1, new Question("🕶️", "Sunglasses"));
        questions.put(2, new Question("🍎", "Apple"));
        questions.put(3, new Question("🚗", "Car"));
        questions.put(4, new Question("📚", "Book"));
        questions.put(5, new Question("📱", "Phone"));
    }
 
    // ****************** تايمر غرفة الانتظار ******************
 
    public void onPlayerJoinedWaitingRoom(int currentCount) {
        waitingPlayersCount = currentCount;
        if (waitingPlayersCount == 1 && !gameStarted) {
            startWaitingTimer();
        }
    }
 
    private void startWaitingTimer() {
        System.out.println("Waiting timer started: 30 seconds");
        waitingTimer = new Timer();
        waitingTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (!gameStarted) {
                    System.out.println("Waiting time's up! Starting game...");
                    startGameIfPossible();
                }
            }
        }, 30000);
    }
 
    public void checkAndStartGameIfFull(int currentCount) {
        waitingPlayersCount = currentCount;
        if (currentCount >= 4 && !gameStarted) {
            System.out.println("Max players reached! Starting game immediately...");
            cancelWaitingTimer();
            startGameIfPossible();
        }
    }
 
    private void cancelWaitingTimer() {
        if (waitingTimer != null) {
            waitingTimer.cancel();
            waitingTimer = null;
        }
    }
 
    private void startGameIfPossible() {
        if (gameStarted) return;
        int playerCount = server.getWaitingRoomSize();
        if (playerCount >= 2) {
            gameStarted = true;
            cancelWaitingTimer();
            server.startGame();
        } else {
            System.out.println("Not enough players (need at least 2, have " + playerCount + ")");
        }
    }
 
    // ****************** تايمر اللعبة (5 دقائق) ******************
 
    public void startGameTimer() {
        cancelGameTimer();
 
        System.out.println("Game timer started: 5 minutes");
 
        // أرسل للكلاينتات وقت اللعبة بالثواني
        server.broadcastToAll("GAME_TIMER:" + (GAME_DURATION_MS / 1000));
 
        gameTimer = new Timer();
        gameTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                System.out.println("Game time is up! Ending game...");
                endGame();
            }
        }, GAME_DURATION_MS);
    }
 
    private void cancelGameTimer() {
        if (gameTimer != null) {
            gameTimer.cancel();
            gameTimer = null;
        }
    }
 
    // ****************** إدارة الجولات ******************
 
    public void startRound(int round, int totalPlayers) {
        this.currentRound = round;
        this.totalPlayers = totalPlayers;
        this.answeredCount = 0;
        this.roundActive = true;
        this.hasAnswered.clear();
 
        Question q = questions.get(round);
        if (q != null) {
            this.correctAnswer = q.getAnswer();
            String image = q.getImage();
            System.out.println("Round " + round + " - Correct: " + correctAnswer);
            server.broadcastToGamePlayers("QUESTION:" + round + ":" + image);
        }
    }
 
    // ****************** استقبال الإجابات ******************
 
    public synchronized void submitAnswer(String username, String answer) {
        if (!roundActive) return;
 
        if (hasAnswered.getOrDefault(username, false)) {
            server.broadcastToGamePlayers("ALREADY_ANSWERED:" + username);
            return;
        }
 
        scores.putIfAbsent(username, 0);
        hasAnswered.put(username, true);
        answeredCount++;
 
        if (answer.equalsIgnoreCase(correctAnswer)) {
            scores.put(username, scores.get(username) + 1);
            server.broadcastToGamePlayers("CORRECT:" + username);
        } else {
            server.broadcastToGamePlayers("WRONG:" + username);
        }
 
        sendScores();
 
        if (answeredCount >= totalPlayers) {
            roundActive = false;
            server.nextRound();
        }
    }
 
    // ****************** إرسال النقاط ******************
 
    private void sendScores() {
        String scoresStr = server.getGamePlayers().stream()
                .map(p -> p.getUsername() + "=" + scores.getOrDefault(p.getUsername(), 0))
                .collect(Collectors.joining(","));
        server.broadcastScores(scoresStr);
    }
 
    // ****************** إنهاء اللعبة ******************
 
    public void endGame() {
        roundActive = false;
        cancelWaitingTimer();
        cancelGameTimer();
 
        String winner = null;
        int max = -1;
        List<String> tiedPlayers = new ArrayList<>();
 
        for (String user : scores.keySet()) {
            if (scores.get(user) > max) {
                max = scores.get(user);
                winner = user;
                tiedPlayers.clear();
                tiedPlayers.add(user);
            } else if (scores.get(user) == max) {
                tiedPlayers.add(user);
            }
        }
 
        if (tiedPlayers.size() > 1) {
            server.endGame(null);
        } else {
            server.endGame(winner);
        }
 
        scores.clear();
        hasAnswered.clear();
        gameStarted = false;
        waitingPlayersCount = 0;
    }
 
    // ✅ تُستدعى من Server.startGame() لتشغيل تايمر اللعبة
    public void onGameStart() {
        gameStarted = true;
        cancelWaitingTimer();
        startGameTimer();
    }
 
    public boolean isGameStarted() {
        return gameStarted;
    }
 
    private class Question {
        private String image;
        private String answer;
 
        public Question(String image, String answer) {
            this.image = image;
            this.answer = answer;
        }
 
        public String getImage() { return image; }
        public String getAnswer() { return answer; }
    }
}
 
----------------------------------------

package server;
 
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
 
public class WaitingRoom {
 
    private static final int MAX_PLAYERS = 4;
    private CopyOnWriteArrayList<ClientHandler> waitingPlayers = new CopyOnWriteArrayList<>();
    private Server server;
 
    public WaitingRoom(Server server) {
        this.server = server;
    }
 
    // ****************** PHASE 1 ******************
 
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
 
        // ✅ FIX: استدعاء GameLogic مرة واحدة فقط (أُزيل من Server.addToWaitingRoom)
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
 
    // ✅ FIX: ترسل لجميع المتصلين وليس فقط من في الغرفة
    // ✅ FIX: لا توجد فاصلة زائدة في نهاية القائمة
    private void broadcastWaitingList() {
        String names = waitingPlayers.stream()
                .map(ClientHandler::getUsername)
                .collect(Collectors.joining(","));
 
        String msg = "WAITING_LIST:" + names;
        server.broadcastToAll(msg);
    }
 
    // ****************** PHASE 2 ******************
 
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
}
 


-------------------------------
package server;
 
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
 
public class Server {
 
    private ServerSocket serverSocket;
    private List<ClientHandler> clients = new ArrayList<>();
    private WaitingRoom waitingRoom;
    private boolean running = true;
    private GameLogic gameLogic;
 
    // ✅ FIX: gamePlayers محمية بـ synchronizedList
    private List<ClientHandler> gamePlayers = Collections.synchronizedList(new ArrayList<>());
    private int currentRound = 0;
    private boolean gameActive = false;
    private final int TOTAL_ROUNDS = 5;
 
    public Server() {
        this.gameLogic = new GameLogic(this);
        this.waitingRoom = new WaitingRoom(this);
    }
 
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
 
    public GameLogic getGameLogic() {
        return gameLogic;
    }
 
    public List<ClientHandler> getGamePlayers() {
        return gamePlayers;
    }
 
    public synchronized List<ClientHandler> getClients() {
        return clients;
    }
 
    public synchronized void addClient(ClientHandler client) {
        clients.add(client);
    }
 
    // ✅ FIX: يُزيل من gamePlayers أيضاً
    public synchronized void removeClient(ClientHandler client) {
        clients.remove(client);
        waitingRoom.removePlayer(client);
        gamePlayers.remove(client);
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
 
    // ✅ FIX: استدعاء GameLogic مرة واحدة فقط هنا (أُزيل من WaitingRoom)
    public void addToWaitingRoom(ClientHandler handler) {
        waitingRoom.addPlayer(handler);
    }
 
    public void removeFromWaitingRoom(ClientHandler handler) {
        waitingRoom.removePlayer(handler);
    }
 
    // ****************** PHASE 2 ******************
 
    public synchronized void startGame() {
        if (gameActive) {
            broadcastToAll("ERROR:Game already started");
            return;
        }
 
        if (waitingRoom.getPlayerCount() < 2) {
            broadcastToAll("ERROR:Not enough players (need at least 2)");
            return;
        }
 
        System.out.println("Starting game with " + waitingRoom.getPlayerCount() + " players");
 
        gameActive = true;
        currentRound = 0;
 
        gamePlayers.clear();
        gamePlayers.addAll(waitingRoom.getAllPlayers());
 
        waitingRoom.clear();
 
        broadcastToGamePlayers("GAME_START");
        gameLogic.onGameStart(); // ✅ يشغّل تايمر الـ 5 دقائق
        nextRound();
    }
 
    // ✅ FIX: أصبحت synchronized
    public synchronized void nextRound() {
        if (!gameActive) return;
 
        currentRound++;
 
        if (currentRound > TOTAL_ROUNDS) {
            gameLogic.endGame();
            return;
        }
 
        System.out.println("Starting round " + currentRound + "/" + TOTAL_ROUNDS);
        broadcastToGamePlayers("ROUND:" + currentRound + "/" + TOTAL_ROUNDS);
        gameLogic.startRound(currentRound, gamePlayers.size());
    }
 
    public synchronized void endGame(String winnerName) {
        if (!gameActive) return;
 
        gameActive = false;
 
        String endMsg;
        if (winnerName != null && !winnerName.isEmpty()) {
            endMsg = "GAME_END:" + winnerName;
            System.out.println("Game ended. Winner: " + winnerName);
        } else {
            endMsg = "GAME_END:NO_WINNER";
            System.out.println("Game ended. No winner");
        }
 
        broadcastToAll(endMsg);
        gamePlayers.clear();
    }
 
    public void broadcastToAll(String message) {
        for (ClientHandler c : clients) {
            c.sendMessage(message);
        }
    }
 
    public void broadcastToGamePlayers(String message) {
        synchronized (gamePlayers) {
            for (ClientHandler c : gamePlayers) {
                c.sendMessage(message);
            }
        }
    }
 
    public void broadcastScores(String scoresData) {
        broadcastToAll("SCORE:" + scoresData);
    }
 
    // ✅ FIX: لا تستدعي removeClient (يتكفل به ClientHandler في finally)
    public synchronized void handlePlayerLeave(ClientHandler player) {
        String username = player.getUsername();
        System.out.println("Player leaving: " + username);
 
        gamePlayers.remove(player);
        waitingRoom.removePlayer(player);
 
        broadcastToAll("PLAYER_LEFT:" + username);
 
        if (gameActive) {
            if (gamePlayers.size() == 1) {
                endGame(gamePlayers.get(0).getUsername());
            } else if (gamePlayers.isEmpty()) {
                endGame(null);
            }
        }
    }
 
    public void receiveAnswer(ClientHandler player, String answer, int round) {
        if (!gameActive) {
            player.sendMessage("ERROR:Game not active");
            return;
        }
        System.out.println("Answer from " + player.getUsername() + ": " + answer);
        gameLogic.submitAnswer(player.getUsername(), answer);
        player.sendMessage("ANSWER_RECEIVED:Round " + round);
    }
 
    public int getWaitingRoomSize() {
        return waitingRoom.getPlayerCount();
    }
 
    public List<ClientHandler> getWaitingRoomPlayers() {
        return waitingRoom.getAllPlayers();
    }
 
    public void clearWaitingRoom() {
        waitingRoom.clear();
    }
 
    public List<String> getGamePlayerNames() {
        List<String> names = new ArrayList<>();
        for (ClientHandler p : gamePlayers) {
            names.add(p.getUsername());
        }
        return names;
    }
 
    public boolean isGameActive() {
        return gameActive;
    }
 
    public int getCurrentRound() {
        return currentRound;
    }
 
    public static void main(String[] args) {
        new Server().start(1234);
    }
}
 



-------------------------





import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.util.HashSet;

public class GameClient extends JFrame {

    private JFrame startFrame;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String username;

    private JTextField usernameField;
    private JButton connectBtn;
    private JButton playBtn;
    private JButton leaveBtn;
    private JTextArea chatArea;
    private DefaultListModel<String> connectedListModel;
    private JList<String> connectedList;
    private DefaultListModel<String> waitingListModel;
    private JList<String> waitingList;

    private boolean connected = false;
    private boolean listenerStarted = false;

    // ====================== GAME ======================
    private JPanel gamePanel;
    private JLabel roundLabel;
    private JLabel imageLabel;
    private JLabel scoreLabel;
    private JLabel timerLabel; // ✅ عداد الوقت
    private JTextField answerField;
    private JButton submitBtn;
    private int currentRound = 0;

    // ✅ تايمر العداد التنازلي
    private Timer countdownTimer;
    private int timeLeft = 0;

    // ====================== CONSTRUCTOR ======================
    public GameClient() {
        createStartScreen();
    }

    // ====================== START SCREEN ======================
    private void createStartScreen() {
        startFrame = new JFrame("Game Start");
        startFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        startFrame.setExtendedState(JFrame.MAXIMIZED_BOTH);

        ImageIcon bg = new ImageIcon("src/image/background.png");
        Image img = bg.getImage();
        Dimension size = Toolkit.getDefaultToolkit().getScreenSize();
        Image scaled = img.getScaledInstance(size.width, size.height, Image.SCALE_SMOOTH);

        JLabel background = new JLabel(new ImageIcon(scaled));
        background.setLayout(new GridBagLayout());

        ImageIcon startIcon = new ImageIcon("src/image/start.png");
        Image startImg = startIcon.getImage().getScaledInstance(220, 90, Image.SCALE_SMOOTH);
        JButton startBtn = new JButton(new ImageIcon(startImg));

        startBtn.setBorderPainted(false);
        startBtn.setContentAreaFilled(false);
        startBtn.setFocusPainted(false);
        startBtn.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(260, 0, 0, 0);
        background.add(startBtn, gbc);

        startBtn.addActionListener(e -> {
            startFrame.dispose();
            initializeMainGUI();
        });

        startFrame.add(background);
        startFrame.setVisible(true);
    }

    // ====================== MAIN GUI ======================
    private void initializeMainGUI() {
        setTitle("Multiplayer Game");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setExtendedState(JFrame.MAXIMIZED_BOTH);

        JLabel bg = new JLabel(new ImageIcon("src/image/img2.png"));
        bg.setLayout(new BorderLayout());
        setContentPane(bg);

        // ====================== TOP ======================
        JPanel top = new JPanel(new FlowLayout());
        top.setOpaque(false);

        JLabel userLabel = new JLabel("Username:");
        userLabel.setForeground(Color.WHITE);
        userLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
        top.add(userLabel);

        usernameField = new JTextField();
        usernameField.setPreferredSize(new Dimension(250, 35));
        usernameField.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        top.add(usernameField);

        connectBtn = new JButton("Connect");
        top.add(connectBtn);
        add(top, BorderLayout.NORTH);

        // ====================== CENTER ======================
        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(20, 40, 20, 40);

        Color beige = new Color(230, 210, 170);

        connectedListModel = new DefaultListModel<>();
        connectedList = new JList<>(connectedListModel);
        connectedList.setBackground(beige);
        connectedList.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        connectedList.setBorder(BorderFactory.createTitledBorder("Connected Players"));
        JScrollPane scroll1 = new JScrollPane(connectedList);
        scroll1.setPreferredSize(new Dimension(400, 250));

        waitingListModel = new DefaultListModel<>();
        waitingList = new JList<>(waitingListModel);
        waitingList.setBackground(beige);
        waitingList.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        waitingList.setBorder(BorderFactory.createTitledBorder("Waiting Room"));
        JScrollPane scroll2 = new JScrollPane(waitingList);
        scroll2.setPreferredSize(new Dimension(400, 250));

        gbc.gridx = 0; gbc.gridy = 0;
        center.add(scroll1, gbc);
        gbc.gridx = 1;
        center.add(scroll2, gbc);
        add(center, BorderLayout.CENTER);

        // ====================== BOTTOM ======================
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);

        JPanel btnPanel = new JPanel(new FlowLayout());
        btnPanel.setOpaque(false);

        playBtn = new JButton("Play");
        leaveBtn = new JButton("Leave");
        playBtn.setEnabled(false);
        leaveBtn.setEnabled(false);
        btnPanel.add(playBtn);
        btnPanel.add(leaveBtn);
        bottom.add(btnPanel, BorderLayout.NORTH);

        chatArea = new JTextArea(8, 40);
        chatArea.setEditable(false);
        bottom.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        // ====================== GAME PANEL ======================
        createGamePanel();
        gamePanel.setVisible(false);
        add(gamePanel, BorderLayout.EAST);

        // ====================== BUTTONS ======================
        connectBtn.addActionListener(e -> connectToServer());
        playBtn.addActionListener(e -> sendPlay());
        leaveBtn.addActionListener(e -> sendLeave());

        setVisible(true);
    }

    // ====================== GAME PANEL ======================
    private void createGamePanel() {
        gamePanel = new JPanel();
        gamePanel.setOpaque(false);
        gamePanel.setPreferredSize(new Dimension(500, 700));
        gamePanel.setLayout(new BoxLayout(gamePanel, BoxLayout.Y_AXIS));

        roundLabel = new JLabel("Round: 0");
        roundLabel.setForeground(Color.WHITE);
        roundLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        roundLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // ✅ عداد الوقت
        timerLabel = new JLabel("⏱ 5:00");
        timerLabel.setForeground(Color.YELLOW);
        timerLabel.setFont(new Font("Segoe UI", Font.BOLD, 28));
        timerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        imageLabel = new JLabel();
        imageLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        scoreLabel = new JLabel("Scores");
        scoreLabel.setForeground(Color.YELLOW);
        scoreLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        scoreLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        answerField = new JTextField();
        answerField.setMaximumSize(new Dimension(250, 40));
        answerField.setFont(new Font("Segoe UI", Font.PLAIN, 20));

        // ✅ اضغط Enter لإرسال الإجابة
        answerField.addActionListener(e -> sendAnswer());

        submitBtn = new JButton("Submit Answer");
        submitBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        submitBtn.addActionListener(e -> sendAnswer());

        gamePanel.add(Box.createVerticalStrut(40));
        gamePanel.add(timerLabel);
        gamePanel.add(Box.createVerticalStrut(10));
        gamePanel.add(roundLabel);
        gamePanel.add(Box.createVerticalStrut(20));
        gamePanel.add(imageLabel);
        gamePanel.add(Box.createVerticalStrut(20));
        gamePanel.add(scoreLabel);
        gamePanel.add(Box.createVerticalStrut(20));
        gamePanel.add(answerField);
        gamePanel.add(Box.createVerticalStrut(10));
        gamePanel.add(submitBtn);
    }

    // ====================== CONNECT ======================
    private void connectToServer() {
        if (connected) return;

        String name = usernameField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter username");
            return;
        }

        // ✅ IP قابل للتعديل
        String ip = JOptionPane.showInputDialog(this, "Enter server IP:", "localhost");
        if (ip == null || ip.trim().isEmpty()) ip = "localhost";

        try {
            socket = new Socket(ip.trim(), 1234);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            username = name;
            out.println("CONNECT:" + username);
            connected = true;
            startListener();

            connectBtn.setEnabled(false);
            usernameField.setEnabled(false);
            playBtn.setEnabled(true);
            leaveBtn.setEnabled(true);

            JOptionPane.showMessageDialog(this, "Connected Successfully 🎉");

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Cannot connect to server!");
        }
    }

    // ====================== LISTENER ======================
    private void startListener() {
        if (listenerStarted) return;
        listenerStarted = true;

        new Thread(() -> {
            try {
                String msg;
                while ((msg = in.readLine()) != null) {
                    String finalMsg = msg;
                    SwingUtilities.invokeLater(() -> processMessage(finalMsg));
                }
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> appendMessage("Connection lost"));
            }
        }).start();
    }

    // ====================== PROCESS MESSAGE ======================
    private void processMessage(String msg) {
        System.out.println("MSG: " + msg);

        // ====================== PLAYER LIST ======================
        if (msg.startsWith("PLAYER_LIST:")) {
            connectedListModel.clear();
            String names = msg.substring(12);
            if (!names.trim().isEmpty()) {
                HashSet<String> unique = new HashSet<>();
                for (String n : names.split(",")) {
                    n = n.trim();
                    if (!n.isEmpty() && unique.add(n)) {
                        connectedListModel.addElement(n);
                    }
                }
            }
        }

        // ====================== WAITING LIST ======================
        else if (msg.startsWith("WAITING_LIST:")) {
            waitingListModel.clear();
            String names = msg.substring(13);
            if (!names.trim().isEmpty()) {
                HashSet<String> unique = new HashSet<>();
                for (String n : names.split(",")) {
                    n = n.trim();
                    if (!n.isEmpty() && unique.add(n)) {
                        waitingListModel.addElement(n);
                    }
                }
            }
        }

        // ====================== WAITING FULL ======================
        else if (msg.startsWith("WAITING_FULL:")) {
            JOptionPane.showMessageDialog(this, msg.substring(13));
            playBtn.setEnabled(true);
        }

        // ====================== PLAY OK ======================
        else if (msg.startsWith("PLAY_OK")) {
            playBtn.setEnabled(false);
            appendMessage("✅ Joined waiting room");
        }

        // ====================== LEFT ======================
        else if (msg.startsWith("LEFT")) {
            playBtn.setEnabled(true);
            appendMessage("🚪 You left the waiting room");
        }

        // ====================== PLAYER LEFT ======================
        else if (msg.startsWith("PLAYER_LEFT:")) {
            String who = msg.substring(12);
            appendMessage("⚠️ " + who + " left the game");
            connectedListModel.removeElement(who);
            waitingListModel.removeElement(who);
        }

        // ====================== GAME START ======================
        else if (msg.startsWith("GAME_START")) {
            gamePanel.setVisible(true);
            answerField.setEnabled(true);
            submitBtn.setEnabled(true);
            setLevelImage("src/image/level1.png");
            revalidate();
            repaint();
            appendMessage("🎮 Game Started!");
        }

        // ====================== GAME TIMER (5 دقائق) ======================
        else if (msg.startsWith("GAME_TIMER:")) {
            int seconds = Integer.parseInt(msg.substring(11));
            startCountdown(seconds);
        }

        // ====================== ROUND ======================
        else if (msg.startsWith("ROUND:")) {
            roundLabel.setText("Round: " + msg.substring(6));
        }

        // ====================== QUESTION ======================
        else if (msg.startsWith("QUESTION:")) {
            String[] parts = msg.split(":");
            currentRound = Integer.parseInt(parts[1]);
            answerField.setEnabled(true);
            submitBtn.setEnabled(true);
            answerField.setText("");
            answerField.requestFocus();

            String imagePath;
            switch (currentRound) {
                case 1: imagePath = "src/image/level1.png"; break;
                case 2: imagePath = "src/image/level2.png"; break;
                case 3: imagePath = "src/image/level3.png"; break;
                case 4: imagePath = "src/image/level4.png"; break;
                case 5: imagePath = "src/image/level5.png"; break;
                default: imagePath = "src/image/level1.png";
            }
            setLevelImage(imagePath);
        }

        // ====================== CORRECT ======================
        else if (msg.startsWith("CORRECT:")) {
            String who = msg.substring(8);
            appendMessage("✅ " + who + " answered correctly!");
            answerField.setEnabled(false);
            submitBtn.setEnabled(false);
        }

        // ====================== WRONG ======================
        else if (msg.startsWith("WRONG:")) {
            String who = msg.substring(6);
            appendMessage("❌ " + who + " answered wrong");
        }

        // ====================== ALREADY ANSWERED ======================
        else if (msg.startsWith("ALREADY_ANSWERED:")) {
            appendMessage("⚠️ You already answered this round");
        }

        // ====================== SCORE ======================
        else if (msg.startsWith("SCORE:")) {
            scoreLabel.setText("<html>" + msg.substring(6).replace(",", "<br>") + "</html>");
        }

        // ====================== GAME END ======================
        else if (msg.startsWith("GAME_END:")) {
            stopCountdown();
            String winner = msg.substring(9);

            answerField.setEnabled(false);
            submitBtn.setEnabled(false);
            playBtn.setEnabled(true);

            // ✅ التحقق من NO_WINNER
            if (winner.equals("NO_WINNER")) {
                setLevelImage("src/image/gameover.png");
                JOptionPane.showMessageDialog(this, "⏱ Time's up! No winner this round.");
                appendMessage("⏱ Game ended - No winner");
            } else if (winner.equals(username)) {
                setLevelImage("src/image/winner.png");
                JOptionPane.showMessageDialog(this, "🏆 You won!");
                appendMessage("🏆 You are the winner!");
            } else {
                setLevelImage("src/image/gameover.png");
                JOptionPane.showMessageDialog(this, "Game Over! Winner: " + winner);
                appendMessage("🏆 Winner: " + winner);
            }
        }
    }

    // ====================== COUNTDOWN TIMER ======================
    private void startCountdown(int seconds) {
        stopCountdown();
        timeLeft = seconds;
        updateTimerLabel();

        countdownTimer = new Timer(1000, e -> {
            timeLeft--;
            updateTimerLabel();

            // تحذير عندما يتبقى 30 ثانية
            if (timeLeft == 30) {
                timerLabel.setForeground(Color.RED);
                appendMessage("⚠️ 30 seconds left!");
            }

            if (timeLeft <= 0) {
                stopCountdown();
            }
        });
        countdownTimer.start();
    }

    private void stopCountdown() {
        if (countdownTimer != null) {
            countdownTimer.stop();
            countdownTimer = null;
        }
    }

    private void updateTimerLabel() {
        int mins = timeLeft / 60;
        int secs = timeLeft % 60;
        timerLabel.setText(String.format("⏱ %d:%02d", mins, secs));
    }

    // ====================== SEND PLAY ======================
    private void sendPlay() {
        if (connected && !waitingListModel.contains(username)) {
            out.println("PLAY");
        }
    }

    // ====================== SEND LEAVE ======================
    private void sendLeave() {
        if (!connected) return;

        int choice = JOptionPane.showConfirmDialog(
                this, "Leave game?", "Confirm", JOptionPane.YES_NO_OPTION);

        if (choice == JOptionPane.YES_OPTION) {
            out.println("LEAVE");
            waitingListModel.removeElement(username);
            playBtn.setEnabled(true);
        }
    }

    // ====================== SEND ANSWER ======================
    private void sendAnswer() {
        String answer = answerField.getText().trim();
        if (answer.isEmpty()) return;
        out.println("ANSWER:" + currentRound + ":" + answer);
        answerField.setText("");
    }

    // ====================== SET IMAGE ======================
    private void setLevelImage(String path) {
        ImageIcon icon = new ImageIcon(path);
        Image img = icon.getImage().getScaledInstance(450, 320, Image.SCALE_SMOOTH);
        imageLabel.setIcon(new ImageIcon(img));
    }

    // ====================== APPEND MESSAGE ======================
    private void appendMessage(String msg) {
        chatArea.append(msg + "\n");
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    // ====================== MAIN ======================
    public static void main(String[] args) {
        SwingUtilities.invokeLater(GameClient::new);
    }
}
