package server;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class GameLogic {

    private Server server;
    private Map<String, Integer> scores = new ConcurrentHashMap<>();
    private Set<String> answeredPlayers = new HashSet<>();
    private String correctAnswer;
    private int currentRound;
    private int totalPlayers;
    private int answeredCount;
    private boolean roundActive;
    private boolean correctAnswerGiven;
    private Timer waitingTimer;
    private boolean gameStarted = false;
    private int waitingPlayersCount = 0;
    private Timer gameTimer;
    private static final int GAME_DURATION_MS = 5 * 60 * 1000;
    private final Map<Integer, Question> questions = new HashMap<>();

    public GameLogic(Server server) {
        this.server = server;
        loadQuestions();
    }

    private void loadQuestions() {
        questions.put(1, new Question("level1.png", "فقمة"));
        questions.put(2, new Question("level2.png", "عسير"));
        questions.put(3, new Question("level3.png", "الظهران مول"));
        questions.put(4, new Question("level4.png", "جدة ام الرخاء و الشدة"));
        questions.put(5, new Question("level5.png", "ضربني وبكى سبقني و اشتكى"));
    }

    public void onPlayerJoinedWaitingRoom(int currentCount) {
        waitingPlayersCount = currentCount;
        if (waitingPlayersCount == 1 && !gameStarted) {
            startWaitingTimer();
        }
    }

    private void startWaitingTimer() {
        System.out.println("Timer started: 30 seconds");
        waitingTimer = new Timer();
        waitingTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (!gameStarted) {
                    System.out.println("Time's up! Starting game...");
                    startGameIfPossible();
                }
            }
        }, 30000);
    }

    public void checkAndStartGameIfFull(int currentCount) {
        waitingPlayersCount = currentCount;
        if (currentCount >= 4 && !gameStarted) {
            System.out.println("Max players reached (4)! Starting game immediately...");
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

    public void startGameTimer() {
        cancelGameTimer();
        System.out.println("Game timer started: 5 minutes");
        
        int totalSeconds = GAME_DURATION_MS / 1000;
        System.out.println(">>> Sending GAME_TIMER:" + totalSeconds);
        server.broadcastToAll("GAME_TIMER:" + totalSeconds);

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

    public void startRound(int round, int totalPlayers) {
        this.currentRound = round;
        this.totalPlayers = totalPlayers;
        this.answeredCount = 0;
        this.roundActive = true;
        this.correctAnswerGiven = false;
        this.answeredPlayers.clear();

        Question q = questions.get(round);
        if (q != null) {
            this.correctAnswer = q.getAnswer();
            String image = q.getImage();
            System.out.println("Round " + round + " - Correct: " + correctAnswer);
            server.broadcastToGamePlayers("QUESTION:" + round + ":" + image);
        }
    }

    public synchronized void submitAnswer(String username, String answer) {
        if (!roundActive || correctAnswerGiven) {
            server.broadcastToGamePlayers("ROUND_ENDED:Round has ended!");
            return;
        }

       

        scores.putIfAbsent(username, 0);
        

        if (answer.equalsIgnoreCase(correctAnswer)) {
            scores.put(username, scores.get(username) + 1);
            correctAnswerGiven = true;
            
            server.broadcastToGamePlayers("CORRECT:" + username);
            sendScores();
            
            roundActive = false;
            server.broadcastToGamePlayers("ROUND_END:" + username);
            server.nextRound();
            
        } else {
    server.broadcastToGamePlayers("WRONG:" + username);
}
    }

    private void sendScores() {
        String scoresStr = server.getGamePlayers().stream()
                .map(p -> p.getUsername() + "=" + scores.getOrDefault(p.getUsername(), 0))
                .collect(Collectors.joining(","));
        server.broadcastScores(scoresStr);
    }
    
    public String getFormattedScores() {
        if (scores.isEmpty()) return "";
        
        return server.getGamePlayers().stream()
                .map(p -> p.getUsername() + "=" + scores.getOrDefault(p.getUsername(), 0))
                .collect(Collectors.joining(","));
    }

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
        answeredPlayers.clear();
        gameStarted = false;
        waitingPlayersCount = 0;
    }

    public void onGameStart() {
        gameStarted = true;
        cancelWaitingTimer();
        startGameTimer();
        System.out.println(">>> onGameStart called - Game timer should start");
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
