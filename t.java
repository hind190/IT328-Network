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

    private JTextField answerField;

    private JButton submitBtn;

    private int currentRound = 0;

    // ====================== CONSTRUCTOR ======================

    public GameClient() {

        createStartScreen();
    }

    // ====================== START SCREEN ======================

    private void createStartScreen() {

        startFrame = new JFrame("Game Start");

        startFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        startFrame.setExtendedState(JFrame.MAXIMIZED_BOTH);

        ImageIcon bg =
                new ImageIcon("src/image/background.png");

        Image img = bg.getImage();

        Dimension size =
                Toolkit.getDefaultToolkit().getScreenSize();

        Image scaled =
                img.getScaledInstance(
                        size.width,
                        size.height,
                        Image.SCALE_SMOOTH
                );

        JLabel background =
                new JLabel(new ImageIcon(scaled));

        background.setLayout(new GridBagLayout());

        ImageIcon startIcon =
                new ImageIcon("src/image/start.png");

        Image startImg =
                startIcon.getImage().getScaledInstance(
                        220,
                        90,
                        Image.SCALE_SMOOTH
                );

        JButton startBtn =
                new JButton(new ImageIcon(startImg));

        startBtn.setBorderPainted(false);

        startBtn.setContentAreaFilled(false);

        startBtn.setFocusPainted(false);

        startBtn.setOpaque(false);

        GridBagConstraints gbc =
                new GridBagConstraints();

        gbc.gridx = 0;
        gbc.gridy = 0;

        gbc.insets =
                new Insets(260, 0, 0, 0);

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

        JLabel bg =
                new JLabel(
                        new ImageIcon(
                                "src/image/img2.png"
                        )
                );

        bg.setLayout(new BorderLayout());

        setContentPane(bg);

        // ====================== TOP ======================

        JPanel top =
                new JPanel(new FlowLayout());

        top.setOpaque(false);

        JLabel userLabel =
                new JLabel("Username:");

        userLabel.setForeground(Color.WHITE);

        userLabel.setFont(
                new Font(
                        "Segoe UI",
                        Font.BOLD,
                        22
                )
        );

        top.add(userLabel);

        usernameField =
                new JTextField();

        usernameField.setPreferredSize(
                new Dimension(250, 35)
        );

        usernameField.setFont(
                new Font(
                        "Segoe UI",
                        Font.PLAIN,
                        18
                )
        );

        top.add(usernameField);

        connectBtn =
                new JButton("Connect");

        top.add(connectBtn);

        add(top, BorderLayout.NORTH);

        // ====================== CENTER ======================

        JPanel center =
                new JPanel(new GridBagLayout());

        center.setOpaque(false);

        GridBagConstraints gbc =
                new GridBagConstraints();

        gbc.insets =
                new Insets(20, 40, 20, 40);

        Color beige =
                new Color(230, 210, 170);

        connectedListModel =
                new DefaultListModel<>();

        connectedList =
                new JList<>(connectedListModel);

        connectedList.setBackground(beige);

        connectedList.setFont(
                new Font(
                        "Segoe UI",
                        Font.PLAIN,
                        18
                )
        );

        connectedList.setBorder(
                BorderFactory.createTitledBorder(
                        "Connected Players"
                )
        );

        JScrollPane scroll1 =
                new JScrollPane(connectedList);

        scroll1.setPreferredSize(
                new Dimension(400, 250)
        );

        waitingListModel =
                new DefaultListModel<>();

        waitingList =
                new JList<>(waitingListModel);

        waitingList.setBackground(beige);

        waitingList.setFont(
                new Font(
                        "Segoe UI",
                        Font.PLAIN,
                        18
                )
        );

        waitingList.setBorder(
                BorderFactory.createTitledBorder(
                        "Waiting Room"
                )
        );

        JScrollPane scroll2 =
                new JScrollPane(waitingList);

        scroll2.setPreferredSize(
                new Dimension(400, 250)
        );

        gbc.gridx = 0;
        gbc.gridy = 0;

        center.add(scroll1, gbc);

        gbc.gridx = 1;

        center.add(scroll2, gbc);

        add(center, BorderLayout.CENTER);

        // ====================== BOTTOM ======================

        JPanel bottom =
                new JPanel(new BorderLayout());

        bottom.setOpaque(false);

        JPanel btnPanel =
                new JPanel(new FlowLayout());

        btnPanel.setOpaque(false);

        playBtn =
                new JButton("Play");

        leaveBtn =
                new JButton("Leave");

        playBtn.setEnabled(false);

        leaveBtn.setEnabled(false);

        btnPanel.add(playBtn);

        btnPanel.add(leaveBtn);

        bottom.add(btnPanel, BorderLayout.NORTH);

        chatArea =
                new JTextArea(8, 40);

        chatArea.setEditable(false);

        bottom.add(
                new JScrollPane(chatArea),
                BorderLayout.CENTER
        );

        add(bottom, BorderLayout.SOUTH);

        // ====================== GAME PANEL ======================

        createGamePanel();

        gamePanel.setVisible(false);

        add(gamePanel, BorderLayout.EAST);

        // ====================== BUTTONS ======================

        connectBtn.addActionListener(
                e -> connectToServer()
        );

        playBtn.addActionListener(
                e -> sendPlay()
        );

        leaveBtn.addActionListener(
                e -> sendLeave()
        );

        setVisible(true);
    }

    // ====================== GAME PANEL ======================

    private void createGamePanel() {

        gamePanel =
                new JPanel();

        gamePanel.setOpaque(false);

        gamePanel.setPreferredSize(
                new Dimension(500, 700)
        );

        gamePanel.setLayout(
                new BoxLayout(
                        gamePanel,
                        BoxLayout.Y_AXIS
                )
        );

        roundLabel =
                new JLabel("Round: 0");

        roundLabel.setForeground(Color.WHITE);

        roundLabel.setFont(
                new Font(
                        "Segoe UI",
                        Font.BOLD,
                        24
                )
        );

        roundLabel.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );

        imageLabel =
                new JLabel();

        imageLabel.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );

        scoreLabel =
                new JLabel("Scores");

        scoreLabel.setForeground(Color.YELLOW);

        scoreLabel.setFont(
                new Font(
                        "Segoe UI",
                        Font.BOLD,
                        20
                )
        );

        scoreLabel.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );

        answerField =
                new JTextField();

        answerField.setMaximumSize(
                new Dimension(250, 40)
        );

        answerField.setFont(
                new Font(
                        "Segoe UI",
                        Font.PLAIN,
                        20
                )
        );

        submitBtn =
                new JButton("Submit Answer");

        submitBtn.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );

        submitBtn.addActionListener(
                e -> sendAnswer()
        );

        gamePanel.add(
                Box.createVerticalStrut(100)
        );

        gamePanel.add(roundLabel);

        gamePanel.add(
                Box.createVerticalStrut(30)
        );

        gamePanel.add(imageLabel);

        gamePanel.add(
                Box.createVerticalStrut(30)
        );

        gamePanel.add(scoreLabel);

        gamePanel.add(
                Box.createVerticalStrut(20)
        );

        gamePanel.add(answerField);

        gamePanel.add(
                Box.createVerticalStrut(20)
        );

        gamePanel.add(submitBtn);
    }

    // ====================== CONNECT ======================

    private void connectToServer() {

        if (connected) return;

        String name =
                usernameField.getText().trim();

        if (name.isEmpty()) {

            JOptionPane.showMessageDialog(
                    this,
                    "Enter username"
            );

            return;
        }

        try {

            socket =
                    new Socket("localhost", 1234);

            out =
                    new PrintWriter(
                            socket.getOutputStream(),
                            true
                    );

            in =
                    new BufferedReader(
                            new InputStreamReader(
                                    socket.getInputStream()
                            )
                    );

            username = name;

            out.println(
                    "CONNECT:" + username
            );

            connected = true;

            startListener();

            connectBtn.setEnabled(false);

            usernameField.setEnabled(false);

            playBtn.setEnabled(true);

            leaveBtn.setEnabled(true);

            JOptionPane.showMessageDialog(
                    this,
                    "Connected Successfully 🎉"
            );

        } catch (IOException e) {

            JOptionPane.showMessageDialog(
                    this,
                    "Cannot connect to server!"
            );
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

                    SwingUtilities.invokeLater(() ->
                            processMessage(finalMsg)
                    );
                }

            } catch (IOException e) {

                appendMessage("Connection lost");
            }

        }).start();
    }

    // ====================== PROCESS MESSAGE ======================

    private void processMessage(String msg) {

        System.out.println(msg);

        // ====================== PLAYER LIST ======================

        if (msg.startsWith("PLAYER_LIST:")) {

            connectedListModel.clear();

            String names =
                    msg.substring(12);

            if (!names.trim().isEmpty()) {

                HashSet<String> unique =
                        new HashSet<>();

                for (String n : names.split(",")) {

                    n = n.trim();

                    if (!n.isEmpty()
                            && !unique.contains(n)) {

                        unique.add(n);

                        connectedListModel.addElement(n);
                    }
                }
            }
        }

        // ====================== WAITING LIST ======================

        else if (msg.startsWith("WAITING_LIST:")) {

            waitingListModel.clear();

            String names =
                    msg.substring(13);

            if (!names.trim().isEmpty()) {

                HashSet<String> unique =
                        new HashSet<>();

                for (String n : names.split(",")) {

                    n = n.trim();

                    if (!n.isEmpty()
                            && !unique.contains(n)) {

                        unique.add(n);

                        waitingListModel.addElement(n);
                    }
                }
            }
        }

        // ====================== PLAY OK ======================

        else if (msg.startsWith("PLAY_OK")) {

            playBtn.setEnabled(false);

            appendMessage("Joined waiting room");
        }

        // ====================== LEFT ======================

        else if (msg.startsWith("LEFT")) {

            playBtn.setEnabled(true);

            appendMessage("You left");
        }

        // ====================== GAME START ======================

        else if (msg.startsWith("GAME_START")) {

            gamePanel.setVisible(true);

            setLevelImage(
                    "src/image/level1.png"
            );

            revalidate();

            repaint();

            appendMessage("Game Started!");
        }

        // ====================== ROUND ======================

        else if (msg.startsWith("ROUND:")) {

            String data =
                    msg.substring(6);

            roundLabel.setText(
                    "Round: " + data
            );
        }

        // ====================== QUESTION ======================

        else if (msg.startsWith("QUESTION:")) {

            String[] parts =
                    msg.split(":");

            currentRound =
                    Integer.parseInt(parts[1]);

            int round =
                    currentRound;

            switch (round) {

                case 1:

                    setLevelImage(
                            "src/image/level1.png"
                    );

                    break;

                case 2:

                    setLevelImage(
                            "src/image/level2.png"
                    );

                    break;

                case 3:

                    setLevelImage(
                            "src/image/level3.png"
                    );

                    break;

                case 4:

                    setLevelImage(
                            "src/image/level4.png"
                    );

                    break;

                case 5:

                    setLevelImage(
                            "src/image/level5.png"
                    );

                    break;
            }
        }

        // ====================== SCORE ======================

        else if (msg.startsWith("SCORE:")) {

            String scores =
                    msg.substring(6);

            scoreLabel.setText(scores);
        }

        // ====================== GAME END ======================

        else if (msg.startsWith("GAME_END:")) {

            String winner =
                    msg.substring(9);

            if (winner.equals(username)) {

                setLevelImage(
                        "src/image/winner.png"
                );

            } else {

                setLevelImage(
                        "src/image/gameover.png"
                );
            }

            answerField.setEnabled(false);

            submitBtn.setEnabled(false);

            playBtn.setEnabled(true);
        }
    }

    // ====================== SEND PLAY ======================

    private void sendPlay() {

        if (connected
                && !waitingListModel.contains(username)) {

            out.println("PLAY");
        }
    }

    // ====================== SEND LEAVE ======================

    private void sendLeave() {

        if (!connected) return;

        int choice =
                JOptionPane.showConfirmDialog(
                        this,
                        "Leave game?",
                        "Confirm",
                        JOptionPane.YES_NO_OPTION
                );

        if (choice == JOptionPane.YES_OPTION) {

            out.println("LEAVE");

            waitingListModel.removeElement(username);

            playBtn.setEnabled(true);
        }
    }

    // ====================== SEND ANSWER ======================

    private void sendAnswer() {

        String answer =
                answerField.getText().trim();

        if (answer.isEmpty()) return;

        out.println(
                "ANSWER:"
                        + currentRound
                        + ":"
                        + answer
        );

        answerField.setText("");
    }

    // ====================== SET IMAGE ======================

    private void setLevelImage(String path) {

        ImageIcon icon =
                new ImageIcon(path);

        Image img =
                icon.getImage().getScaledInstance(
                        450,
                        320,
                        Image.SCALE_SMOOTH
                );

        imageLabel.setIcon(
                new ImageIcon(img)
        );
    }

    // ====================== APPEND MESSAGE ======================

    private void appendMessage(String msg) {

        chatArea.append(msg + "\n");
    }

    // ====================== MAIN ======================

    public static void main(String[] args) {

        SwingUtilities.invokeLater(
                GameClient::new
        );
    }
}
