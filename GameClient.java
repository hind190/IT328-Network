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

    public GameClient() {
        createStartScreen();
    }


    private void createStartScreen() {

        startFrame = new JFrame("Game Start");
        startFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        startFrame.setExtendedState(JFrame.MAXIMIZED_BOTH);

        ImageIcon bg = new ImageIcon("src/client/image/background.png");

        Image img = bg.getImage();
        Dimension size = Toolkit.getDefaultToolkit().getScreenSize();

        Image scaled = img.getScaledInstance(size.width, size.height, Image.SCALE_SMOOTH);

        JLabel background = new JLabel(new ImageIcon(scaled));
        background.setLayout(null);

        JButton startBtn = new JButton("");
        startBtn.setBounds(650, 560, 220, 70);

        startBtn.setContentAreaFilled(false);
        startBtn.setBorderPainted(false);
        startBtn.setOpaque(false);

        startBtn.addActionListener(e -> {
            startFrame.dispose();
            initializeMainGUI();
        });

        background.add(startBtn);

        startFrame.add(background);
        startFrame.setVisible(true);
    }


    private void initializeMainGUI() {

        setTitle("Multiplayer Game - Phase 1");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);


        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setLocationRelativeTo(null);

        JLabel bg = new JLabel(new ImageIcon("src/client/image/img2.png"));
        bg.setLayout(new BorderLayout());
        setContentPane(bg);

        JPanel top = new JPanel(new FlowLayout());
        top.setOpaque(false);

        JLabel userLabel = new JLabel("Username:");
        userLabel.setForeground(Color.WHITE); // 🔥 أبيض
        top.add(userLabel);

        usernameField = new JTextField(15);
        top.add(usernameField);

        connectBtn = new JButton("Connect");
        top.add(connectBtn);

        add(top, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridLayout(1, 2));
        center.setOpaque(false);

        connectedListModel = new DefaultListModel<>();
        connectedList = new JList<>(connectedListModel);
        connectedList.setBorder(BorderFactory.createTitledBorder("Connected Players"));
        center.add(new JScrollPane(connectedList));

        waitingListModel = new DefaultListModel<>();
        waitingList = new JList<>(waitingListModel);
        waitingList.setBorder(BorderFactory.createTitledBorder("Waiting Room"));
        center.add(new JScrollPane(waitingList));

        add(center, BorderLayout.CENTER);

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

        connectBtn.addActionListener(e -> connectToServer());
        playBtn.addActionListener(e -> sendPlay());
        leaveBtn.addActionListener(e -> sendLeave());

        setVisible(true);
    }


    private void connectToServer() {

        if (connected) return;

        String name = usernameField.getText().trim();

        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter username");
            return;
        }

        try {
            socket = new Socket("localhost", 1234);

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

            JOptionPane.showMessageDialog(this, "Connected successfully 🎉");

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Cannot connect to server");
        }
    }

 
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
                appendMessage("Connection lost");
            }
        }).start();
    }


    private void processMessage(String msg) {

        if (msg.startsWith("PLAYER_LIST:")) {

            connectedListModel.clear();

            String names = msg.substring(12);

            if (!names.trim().isEmpty()) {

                HashSet<String> unique = new HashSet<>();

                for (String n : names.split(",")) {
                    n = n.trim();

                    if (!n.isEmpty() && !unique.contains(n)) {
                        unique.add(n);
                        connectedListModel.addElement(n);
                    }
                }
            }
        }

        else if (msg.startsWith("WAITING_LIST:")) {

            waitingListModel.clear();

            String names = msg.substring(13);

            if (!names.trim().isEmpty()) {

                HashSet<String> unique = new HashSet<>();

                for (String n : names.split(",")) {
                    n = n.trim();

                    if (!n.isEmpty() && !unique.contains(n)) {
                        unique.add(n);
                        waitingListModel.addElement(n);
                    }
                }
            }
        }

        else if (msg.startsWith("PLAY_OK")) {
            playBtn.setEnabled(false);
            JOptionPane.showMessageDialog(this, "You joined the game 🎮");
        }

        else if (msg.startsWith("LEFT")) 
        {
            playBtn.setEnabled(true);
            JOptionPane.showMessageDialog(this, "You left the game 🚪");
        } 
        else if (msg.startsWith("WAITING_FULL:"))
        {
            JOptionPane.showMessageDialog(this, msg.substring(13));
            playBtn.setEnabled(true);  // إعادة تمكين زر Play
        }
    }

    private void sendPlay() {
        if (connected && !waitingListModel.contains(username)) {
            out.println("PLAY");
        }
    }


    private void sendLeave() {

        if (!connected) return;

        int choice = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to leave?",
                "Confirm Leave",
                JOptionPane.YES_NO_OPTION
        );

        if (choice == JOptionPane.YES_OPTION) {
            out.println("LEAVE");


            waitingListModel.removeElement(username);

            playBtn.setEnabled(true);
        }
    }

    private void appendMessage(String msg) {
        chatArea.append(msg + "\n");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(GameClient::new);
    }
}
