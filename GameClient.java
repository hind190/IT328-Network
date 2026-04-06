package client;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.Socket;

public class GameClient extends JFrame {
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

    public GameClient() {
        setTitle("Multiplayer Game - Phase 1");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(550, 450);
        setLayout(new BorderLayout());

        JPanel topPanel = new JPanel(new FlowLayout());
        topPanel.add(new JLabel("Username:"));
        usernameField = new JTextField(15);
        topPanel.add(usernameField);
        connectBtn = new JButton("Connect");
        topPanel.add(connectBtn);
        add(topPanel, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new GridLayout(1, 2));
        connectedListModel = new DefaultListModel<>();
        connectedList = new JList<>(connectedListModel);
        connectedList.setBorder(BorderFactory.createTitledBorder("Connected Players"));
        centerPanel.add(new JScrollPane(connectedList));

        waitingListModel = new DefaultListModel<>();
        waitingList = new JList<>(waitingListModel);
        waitingList.setBorder(BorderFactory.createTitledBorder("Waiting Room"));
        centerPanel.add(new JScrollPane(waitingList));
        add(centerPanel, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        JPanel buttonPanel = new JPanel(new FlowLayout());
        playBtn = new JButton("Play");
        leaveBtn = new JButton("Leave");
        playBtn.setEnabled(false);
        leaveBtn.setEnabled(false);
        buttonPanel.add(playBtn);
        buttonPanel.add(leaveBtn);
        bottomPanel.add(buttonPanel, BorderLayout.NORTH);
        chatArea = new JTextArea(8, 40);
        chatArea.setEditable(false);
        bottomPanel.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        connectBtn.addActionListener(e -> connectToServer());
        playBtn.addActionListener(e -> sendPlay());
        leaveBtn.addActionListener(e -> sendLeave());

        setVisible(true);
    }

    private void connectToServer() {
        String name = usernameField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter username");
            return;
        }
        try {
            socket = new Socket("localhost", 1234);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.username = name;
            out.println("CONNECT:" + username);
            startListener();
            connected = true;
            connectBtn.setEnabled(false);
            usernameField.setEnabled(false);
            playBtn.setEnabled(true);
            leaveBtn.setEnabled(true);
            appendMessage("Connected as " + username);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Cannot connect to server");
        }
    }

    private void startListener() {
        new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    final String msg = line;
                    SwingUtilities.invokeLater(() -> processMessage(msg));
                }
            } catch (IOException e) {
                appendMessage("Connection lost");
            }
        }).start();
    }

    private void processMessage(String msg) {
        System.out.println("Received: " + msg);
        if (msg.startsWith("CONNECT_OK:")) {
            appendMessage("Server: " + msg);
        } 
        else if (msg.startsWith("PLAYER_LIST:")) {
            String names = msg.substring(12);
            String[] list = names.split(",");
            connectedListModel.clear();
            for (String n : list) {
                if (!n.isEmpty()) connectedListModel.addElement(n);
            }
        }
        else if (msg.startsWith("WAITING_LIST:")) {
            String names = msg.substring(13);
            String[] list = names.split(",");
            waitingListModel.clear();
            for (String n : list) {
                if (!n.isEmpty()) waitingListModel.addElement(n);
            }
        }
        else if (msg.startsWith("PLAY_OK:")) {
            appendMessage("Server: " + msg);
        }
        else if (msg.startsWith("LEFT:")) {
            appendMessage("Server: " + msg);
        }
        else {
            appendMessage("Server: " + msg);
        }
    }

    private void sendPlay() {
        if (connected) {
            out.println("PLAY");
            appendMessage("You requested to play. Waiting...");
        }
    }

    private void sendLeave() {
        if (connected) {
            out.println("LEAVE");
            appendMessage("You left waiting room.");
        }
    }

    private void appendMessage(String msg) {
        chatArea.append(msg + "\n");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(GameClient::new);
    }
}
