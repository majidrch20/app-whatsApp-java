package authUi;

import auth.AuthService;
import auth.SessionManager;
import client.NetworkClient;
import client.SocketManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ChatView {

    private final int userId;
    private final String phone;
    private final String username;
    private final NetworkClient network;

    private Stage stage;
    private VBox convList;
    private BorderPane mainPanel;
    private ContactView contactView;

    private ConversationView activeConversation;
    private String activeContactPhone;
    private CallView activeCallView; // Added for A/V routing
    private GroupCallView activeGroupCallView;

    private final Map<String, ConversationView> conversationCache = new HashMap<>();

    public ChatView(int userId, String phone, String username, NetworkClient network) {
        this.userId = userId;
        this.phone = phone;
        this.username = username != null ? username : "Utilisateur";
        this.network = network;
    }

    public void start(Stage stage) {
        this.stage = stage;
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #121212;");

        root.setLeft(buildSidebar());
        
        mainPanel = new BorderPane();
        mainPanel.setStyle("-fx-background-color: #0b140e;");
        showWelcomeScreen();
        root.setCenter(mainPanel);

        Scene scene = new Scene(root, 960, 620);
        stage.setTitle("WhatsApp — " + username);
        stage.setScene(scene);
        stage.setMinWidth(700);
        stage.setMinHeight(450);
        stage.show();

        // ContactView initialization (needs to be adapted to JavaFX too, assuming it exists or replacing logic)
        contactView = new ContactView(network, convList);
        contactView.setConversationOpenCallback(this::openConversation);

        startBinaryListener(contactView);
        contactView.loadContacts();
        // Retry once after listener warmup to guarantee list visibility right after login, and notify server client is ready.
        new Thread(() -> {
            try {
                Thread.sleep(100);
                SocketManager.getInstance().sendBinary("CLIENT_READY", "", "", new byte[0]);
                Thread.sleep(500);
            } catch (InterruptedException ignored) {}
            contactView.loadContacts();
        }).start();
    }

    private VBox buildSidebar() {
        VBox sidebar = new VBox();
        sidebar.setPrefWidth(300);
        sidebar.setStyle("-fx-background-color: #161616;");

        // Header
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(12, 15, 12, 15));
        header.setStyle("-fx-background-color: #1e1e1e;");
        header.setSpacing(10);

        StackPane avatar = buildAvatar(username, 44);

        VBox nameBox = new VBox();
        Label nameLbl = new Label(username);
        nameLbl.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px;");
        Label phoneLbl = new Label(phone);
        phoneLbl.setStyle("-fx-text-fill: gray; -fx-font-size: 11px;");
        nameBox.getChildren().addAll(nameLbl, phoneLbl);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnAddGroup = new Button("👥");
        btnAddGroup.setStyle("-fx-background-color: transparent; -fx-text-fill: #25D366; -fx-font-size: 20px; -fx-cursor: hand;");
        btnAddGroup.setOnAction(e -> createGroup());

        Button btnRefresh = new Button("🔄");
        btnRefresh.setStyle("-fx-background-color: transparent; -fx-text-fill: #25D366; -fx-font-size: 18px; -fx-cursor: hand;");
        btnRefresh.setOnAction(e -> contactView.loadContacts());

        Button btnAdd = new Button("+");
        btnAdd.setStyle("-fx-background-color: transparent; -fx-text-fill: #25D366; -fx-font-size: 22px; -fx-font-weight: bold;");
        btnAdd.setOnAction(e -> addContact());

        header.getChildren().addAll(avatar, nameBox, spacer, btnAddGroup, btnRefresh, btnAdd);

        // Search Bar
        TextField searchField = new TextField();
        searchField.setPromptText("Rechercher...");
        searchField.setStyle("-fx-background-color: #282828; -fx-text-fill: white; -fx-prompt-text-fill: gray;");
        searchField.textProperty().addListener((obs, oldV, newV) -> {
            if (contactView != null) {
                contactView.filterContacts(newV);
            }
        });
        VBox.setMargin(searchField, new Insets(8, 12, 8, 12));

        // Conversation List
        convList = new VBox();
        convList.setStyle("-fx-background-color: #161616;");
        ScrollPane scrollPane = new ScrollPane(convList);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: #161616; -fx-border-color: transparent;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        // Logout button
        Button btnLogout = new Button("⏻  Se déconnecter");
        btnLogout.setStyle("-fx-background-color: transparent; -fx-text-fill: #c85050; -fx-font-size: 12px;");
        btnLogout.setMaxWidth(Double.MAX_VALUE);
        btnLogout.setOnAction(e -> logout());

        sidebar.getChildren().addAll(header, searchField, scrollPane, btnLogout);
        return sidebar;
    }

    private void showWelcomeScreen() {
        VBox welcome = new VBox(12);
        welcome.setAlignment(Pos.CENTER);
        
        Label icon = new Label("💬");
        icon.setFont(Font.font("Segoe UI Emoji", 64));
        
        Label title = new Label("WhatsApp");
        title.setStyle("-fx-text-fill: white; -fx-font-size: 26px; -fx-font-weight: bold;");

        Label sub = new Label("Cliquez sur un contact pour commencer");
        sub.setStyle("-fx-text-fill: gray; -fx-font-size: 13px;");

        Label userLbl = new Label("Connecté : " + username + "  |  " + phone);
        userLbl.setStyle("-fx-text-fill: #5a5a5a; -fx-font-size: 11px;");

        welcome.getChildren().addAll(icon, title, sub, userLbl);
        mainPanel.setCenter(welcome);
    }

    private void openConversation(String contactPhone, String contactName, String contactStatus) {
        String normalizedContactPhone = normalizePhone(contactPhone);
        activeContactPhone = normalizedContactPhone;
        ConversationView conv = conversationCache.computeIfAbsent(normalizedContactPhone, k -> {
            ConversationView c = new ConversationView(userId, phone, contactPhone, contactName, contactStatus);
            c.setOnBack(() -> {
                activeContactPhone = null;
                activeConversation = null;
                showWelcomeScreen();
            });
            c.setOnAudioCall(() -> startOutgoingCall(contactPhone, contactName, "audio"));
            c.setOnVideoCall(() -> startOutgoingCall(contactPhone, contactName, "video"));
            c.setOnGroupMeeting(() -> {
                if (contactPhone.startsWith("GROUP:")) {
                    int gid = Integer.parseInt(contactPhone.substring(6));
                    startGroupCall(gid, contactName, true);
                }
            });
            c.setOnGroupAudioCall(() -> {
                if (contactPhone.startsWith("GROUP:")) {
                    int gid = Integer.parseInt(contactPhone.substring(6));
                    startGroupCall(gid, contactName, false);
                }
            });
            return c;
        });
        activeConversation = conv;
        mainPanel.setCenter(conv.getView());
    }

    private void startOutgoingCall(String targetPhone, String targetName, String type) {
        if (activeCallView != null) return;
        SocketManager.getInstance().sendBinary("CALL_SIGNAL", targetPhone, "", ("CALL_REQUEST:" + type.toUpperCase() + ":" + targetPhone).getBytes(StandardCharsets.UTF_8));
        activeCallView = new CallView(targetName, targetPhone, type, false, null, () -> {
            SocketManager.getInstance().sendBinary("CALL_SIGNAL", targetPhone, "", ("CALL_END:" + targetPhone).getBytes(StandardCharsets.UTF_8));
            activeCallView = null;
        });
        activeCallView.start(new Stage());
    }

    /** Démarre un appel de groupe (audio ou vidéo) et envoie le signal au serveur. */
    private void startGroupCall(int groupId, String groupName, boolean isVideo) {
        if (activeGroupCallView != null) return; // Déjà dans un appel
        String callMode = isVideo ? "video" : "audio";
        // Notifier le serveur pour ouvrir la salle et inviter les membres
        SocketManager.getInstance().sendBinary("CALL_SIGNAL", "", "",
            ("GROUP_CALL_START:" + groupId + ":" + callMode).getBytes(StandardCharsets.UTF_8));
        // Ouvrir la vue locale
        activeGroupCallView = new GroupCallView(groupId, groupName, isVideo, () -> activeGroupCallView = null);
        activeGroupCallView.start(new Stage());
    }


    private void startBinaryListener(ContactView contactView) {
        SocketManager.MessageListener listener = new SocketManager.MessageListener() {
            @Override
            public void onMessage(String type, String sender, String msgIdStr, String filename, byte[] data) {
                switch (type) {
                    case "CONTACT_SIGNAL":
                        String payload = new String(data, StandardCharsets.UTF_8);
                        Platform.runLater(() -> contactView.updateContacts(payload));
                        break;

                    case "text":
                    case "audio":
                    case "video":
                    case "image":
                    case "file":
                        Platform.runLater(() -> {
                            System.out.println("[CLIENT_RECEIVE] message reçu de " + sender + " : type=" + type + ", id=" + msgIdStr);
                            String actualSenderStr = sender;
                            String senderForUi = sender;
                            String realSender = sender;
                            if (sender != null && sender.startsWith("GROUP:")) {
                                String[] parts = sender.split(":");
                                if (parts.length >= 3) {
                                    senderForUi = "GROUP:" + parts[1];
                                    realSender = parts[2];
                                }
                            }
                            
                            String normalizedSender = normalizePhone(senderForUi);
                            ConversationView cachedConv = conversationCache.get(normalizedSender);
                            if (cachedConv == null && normalizedSender != null && !normalizedSender.isBlank()) {
                                String finalSenderForUi = senderForUi;
                                cachedConv = conversationCache.computeIfAbsent(normalizedSender, k -> {
                                    ConversationView c = new ConversationView(userId, phone, finalSenderForUi, finalSenderForUi, "ONLINE");
                                    c.setOnBack(() -> {
                                        activeContactPhone = null;
                                        activeConversation = null;
                                        showWelcomeScreen();
                                    });
                                    c.setOnAudioCall(() -> startOutgoingCall(finalSenderForUi, finalSenderForUi, "audio"));
                                    c.setOnVideoCall(() -> startOutgoingCall(finalSenderForUi, finalSenderForUi, "video"));
                                    c.setOnGroupMeeting(() -> {
                                        if (finalSenderForUi.startsWith("GROUP:")) {
                                            int gid = Integer.parseInt(finalSenderForUi.substring(6));
                                            startGroupCall(gid, "Groupe", true);
                                        }
                                    });
                                    c.setOnGroupAudioCall(() -> {
                                        if (finalSenderForUi.startsWith("GROUP:")) {
                                            int gid = Integer.parseInt(finalSenderForUi.substring(6));
                                            startGroupCall(gid, "Groupe", false);
                                        }
                                    });
                                    return c;
                                });
                            }
                            if (cachedConv != null) {
                                int msgId = -1;
                                try {
                                    if (msgIdStr != null && !msgIdStr.isEmpty()) {
                                        msgId = Integer.parseInt(msgIdStr);
                                    }
                                } catch (NumberFormatException ignored) {}
                                cachedConv.receiveMessage(type, filename, data, realSender, msgId);
                            }
                            if (normalizedSender == null || !normalizedSender.equals(activeContactPhone) || activeConversation == null) {
                                String msgText = "text".equals(type) ? new String(data, StandardCharsets.UTF_8) : "📎 " + (filename != null ? filename : type);
                                showNotification(realSender, msgText);
                            }
                        });
                        break;

                    case "CALL_SIGNAL":
                        String callPayload = new String(data, StandardCharsets.UTF_8);
                        Platform.runLater(() -> handleCallSignal(callPayload, sender));
                        break;
                    case "CALL_AUDIO":
                        if (activeCallView != null) {
                            activeCallView.receiveAudio(data);
                        }
                        break;
                        
                    case "UDP_AUDIO":
                        if (activeGroupCallView != null) {
                            activeGroupCallView.receiveAudio(sender, data);
                        }
                        break;
                        
                    case "UDP_VIDEO":
                        if (activeGroupCallView != null) {
                            activeGroupCallView.receiveVideo(sender, data);
                        }
                        break;

                    case "CALL_VIDEO":
                        if (activeCallView != null) {
                            activeCallView.receiveVideo(data);
                        }
                        break;
                }
            }

            @Override
            public void onDisconnect() {
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.WARNING, "Connexion perdue au serveur.");
                    alert.showAndWait();
                    logout();
                });
            }
        };
        SocketManager.getInstance().startListening(listener);
        String serverIp = "127.0.0.1";
        if (network != null && network.getSocket() != null && network.getSocket().getInetAddress() != null) {
            serverIp = network.getSocket().getInetAddress().getHostAddress();
        }
        SocketManager.getInstance().initUdp(serverIp, 5001, listener);
    }

    private void handleCallSignal(String payload, String sender) {
        if (payload.startsWith("GROUP_CALL_INCOMING:")) {
            String[] parts = payload.split(":");
            if (parts.length >= 3) {
                int groupId = Integer.parseInt(parts[1]);
                String starterPhone = parts[2];
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Le contact " + starterPhone + " a démarré une réunion de groupe. Voulez-vous la rejoindre ?", javafx.scene.control.ButtonType.YES, javafx.scene.control.ButtonType.NO);
                alert.setTitle("Réunion de Groupe");
                alert.showAndWait().ifPresent(response -> {
                    if (response == javafx.scene.control.ButtonType.YES) {
                        boolean isVideo = parts.length >= 4 && "video".equals(parts[3]);
                        SocketManager.getInstance().sendBinary("CALL_SIGNAL", "", "", ("GROUP_CALL_JOIN:" + groupId).getBytes(StandardCharsets.UTF_8));
                        startGroupCall(groupId, "Groupe", isVideo);
                    }
                });
            }
            return;
        }
        
        if (payload.startsWith("GROUP_CALL_JOINED:")) {
            showToast("Un membre a rejoint la réunion.");
            return;
        }
        
        if (payload.startsWith("GROUP_CALL_LEFT:")) {
            String[] parts = payload.split(":");
            if (parts.length >= 3 && activeGroupCallView != null) {
                activeGroupCallView.removeParticipant(parts[2]);
            }
            return;
        }

        if (payload.startsWith("CALL_INCOMING:") || payload.startsWith("CALL_REQUEST:")) {
            String[] parts = payload.split(":");
            String callType = parts.length >= 2 ? parts[1].toLowerCase() : "audio";
            String caller = parts.length >= 3 ? parts[2] : (sender != null ? sender : "Inconnu");

            activeCallView = new CallView(caller, caller, callType, true, () -> {
                SocketManager.getInstance().sendBinary("CALL_SIGNAL", caller, "", ("CALL_REJECT:" + caller).getBytes(StandardCharsets.UTF_8));
                activeCallView = null;
            }, () -> {
                SocketManager.getInstance().sendBinary("CALL_SIGNAL", caller, "", ("CALL_END:" + caller).getBytes(StandardCharsets.UTF_8));
                activeCallView = null;
            });
            activeCallView.start(new Stage());
            return;
        }

        if (payload.startsWith("CALL_ACCEPTED:")) { 
            showToast("✅ Appel accepté !"); 
            if (activeCallView != null) activeCallView.startCallSession();
            return; 
        }
        if (payload.startsWith("CALL_REJECTED:")) { 
            showToast("❌ Appel refusé."); 
            if (activeCallView != null) { activeCallView.endCall(); activeCallView = null; }
            return; 
        }
        if (payload.startsWith("CALL_ENDED:")) { 
            showToast("📵 Appel terminé."); 
            if (activeCallView != null) { activeCallView.endCall(); activeCallView = null; }
            return; 
        }
        if (payload.startsWith("CALL_MISSED:")) { showToast("📵 Appel manqué."); }
    }

    private void showNotification(String sender, String msg) {
        showToast("Message de " + sender + ": " + (msg.length() > 30 ? msg.substring(0, 30) + "..." : msg));
    }

    private void showToast(String message) {
        // Simple implementation for JavaFX
        System.out.println("[TOAST]: " + message);
    }

    private void addContact() {
        TextInputDialog phoneDialog = new TextInputDialog();
        phoneDialog.setTitle("Ajouter un contact");
        phoneDialog.setHeaderText("Entrer le numéro du contact :");
        Optional<String> phoneResult = phoneDialog.showAndWait();
        
        if (phoneResult.isPresent() && !phoneResult.get().trim().isEmpty()) {
            String phoneInput = normalizePhone(phoneResult.get());

            TextInputDialog nicknameDialog = new TextInputDialog();
            nicknameDialog.setTitle("Surnom");
            nicknameDialog.setHeaderText("Entrer un surnom (optionnel) :");
            Optional<String> nicknameResult = nicknameDialog.showAndWait();
            
            String nicknameInput = nicknameResult.orElse("");
            
            String payload = (!nicknameInput.trim().isEmpty()) ? "ADD:" + phoneInput + ":" + nicknameInput.trim() : "ADD:" + phoneInput;
            SocketManager.getInstance().sendBinary("CONTACT_SIGNAL", "", "", payload.getBytes(StandardCharsets.UTF_8));

            new Thread(() -> {
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                SocketManager.getInstance().sendBinary("CONTACT_SIGNAL", "", "", "GET_CONTACTS".getBytes(StandardCharsets.UTF_8));
            }).start();
        }
    }
    
    private void createGroup() {
        TextInputDialog groupDialog = new TextInputDialog();
        groupDialog.setTitle("Créer un groupe");
        groupDialog.setHeaderText("Entrez le nom du groupe :");
        Optional<String> groupResult = groupDialog.showAndWait();
        if (groupResult.isPresent() && !groupResult.get().trim().isEmpty()) {
            String groupName = groupResult.get().trim();
            TextInputDialog membersDialog = new TextInputDialog();
            membersDialog.setTitle("Membres du groupe");
            membersDialog.setHeaderText("Entrez les numéros des contacts à ajouter (séparés par des virgules) :");
            Optional<String> membersResult = membersDialog.showAndWait();
            
            if (membersResult.isPresent()) {
                String members = membersResult.get().trim();
                // To fetch their IDs we can just let the server handle it by phones...
                // Wait! Server expects IDs: CREATE_GROUP:name:id1,id2
                // We should change Contactservice to accept phones instead of IDs for convenience?
                // Let's just use what I wrote in Contactservice which uses IDs. I will update Contactservice to search by phone if it fails parseInt.
                String payload = "CREATE_GROUP:" + groupName + ":" + members;
                SocketManager.getInstance().sendBinary("CONTACT_SIGNAL", "", "", payload.getBytes(StandardCharsets.UTF_8));
                
                new Thread(() -> {
                    try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                    SocketManager.getInstance().sendBinary("CONTACT_SIGNAL", "", "", "GET_CONTACTS".getBytes(StandardCharsets.UTF_8));
                }).start();
            }
        }
    }

    private String normalizePhone(String input) {
        if (input == null) return "";
        if (input.startsWith("GROUP:")) return input.trim();
        String digits = input.replaceAll("[^0-9]", "");
        if (digits.length() >= 9) {
            return digits.substring(digits.length() - 9);
        }
        return digits;
    }

    private void logout() {
        SessionManager.clearSession();
        SocketManager.reset();
        stage.close();
        NetworkClient freshNetwork = new NetworkClient("100.104.161.251", 5000);
        new PhoneView(new AuthService(freshNetwork), freshNetwork).start(new Stage());
    }

    public static StackPane buildAvatar(String name, int size) {
        StackPane pane = new StackPane();
        Circle circle = new Circle(size / 2.0, Color.web("#25D366"));
        Label initials = new Label(name != null && !name.isEmpty() ? String.valueOf(name.charAt(0)).toUpperCase() : "?");
        initials.setFont(Font.font("Segoe UI", FontWeight.BOLD, size / 2.5));
        initials.setTextFill(Color.WHITE);
        pane.getChildren().addAll(circle, initials);
        return pane;
    }
}