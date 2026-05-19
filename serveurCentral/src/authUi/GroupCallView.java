package authUi;

import client.SocketManager;
import com.github.sarxos.webcam.Webcam;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GroupCallView {

    private final int groupId;
    private final String groupName;
    private final boolean isVideoEnabled;
    private final Runnable onLeave;

    private Stage stage;
    private FlowPane videoGrid;
    private Label statusLbl;

    // Hardware
    private Webcam webcam;
    private Thread videoThread;
    private Thread audioThread;
    private TargetDataLine audioInput;

    private boolean isActive = false;

    // Maps to track participant streams
    private final Map<String, ImageView> participantVideos = new ConcurrentHashMap<>();
    private final Map<String, VBox> participantBoxes = new ConcurrentHashMap<>();
    private final Map<String, SourceDataLine> participantAudios = new ConcurrentHashMap<>();

    public GroupCallView(int groupId, String groupName, boolean isVideoEnabled, Runnable onLeave) {
        this.groupId = groupId;
        this.groupName = groupName;
        this.isVideoEnabled = isVideoEnabled;
        this.onLeave = onLeave;
    }

    public void start(Stage stage) {
        this.stage = stage;
        stage.setTitle((isVideoEnabled ? "Réunion Vidéo" : "Réunion Audio") + " - " + groupName);

        VBox root = new VBox(15);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(15));
        root.setStyle("-fx-background-color: #111b21;");

        Label title = new Label((isVideoEnabled ? "Réunion de groupe : " : "Appel audio groupe : ") + groupName);
        title.setStyle("-fx-text-fill: white; -fx-font-size: 20px; -fx-font-weight: bold;");

        statusLbl = new Label("En ligne");
        statusLbl.setStyle("-fx-text-fill: #25D366;");

        videoGrid = new FlowPane(10, 10);
        videoGrid.setAlignment(Pos.CENTER);
        videoGrid.setPrefWrapLength(600);
        if (!isVideoEnabled) {
            videoGrid.setVisible(false);
            videoGrid.setManaged(false);
            // On peut ajouter une icône de micro à la place
            Label audioIcon = new Label("🎙️");
            audioIcon.setStyle("-fx-text-fill: gray; -fx-font-size: 80px;");
            root.getChildren().addAll(title, statusLbl, audioIcon);
        } else {
            root.getChildren().addAll(title, statusLbl, videoGrid);
        }

        Button btnLeave = new Button("Quitter la réunion");
        btnLeave.setStyle("-fx-background-color: #e53935; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px; -fx-background-radius: 20px; -fx-padding: 8px 20px; -fx-cursor: hand;");
        btnLeave.setOnAction(e -> leaveMeeting());

        root.getChildren().add(btnLeave);

        Scene scene = new Scene(root, isVideoEnabled ? 800 : 400, isVideoEnabled ? 600 : 300);
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> leaveMeeting());
        stage.show();

        isActive = true;
        activateHardware();
    }

    private void activateHardware() {
        // Audio Capture
        try {
            AudioFormat format = new AudioFormat(16000, 16, 1, true, true);
            DataLine.Info targetInfo = new DataLine.Info(TargetDataLine.class, format);

            if (AudioSystem.isLineSupported(targetInfo)) {
                audioInput = (TargetDataLine) AudioSystem.getLine(targetInfo);
                audioInput.open(format);
                audioInput.start();

                audioThread = new Thread(() -> {
                    byte[] buffer = new byte[1024];
                    while (isActive) {
                        int bytesRead = audioInput.read(buffer, 0, buffer.length);
                        if (bytesRead > 0) {
                            byte[] packet = new byte[bytesRead];
                            System.arraycopy(buffer, 0, packet, 0, bytesRead);
                            SocketManager.getInstance().sendUdpMedia((byte) 2, groupId, packet);
                        }
                    }
                });
                audioThread.start();
            }
        } catch (Exception e) {
            System.err.println("Erreur micro : " + e.getMessage());
        }

        // Video Capture
        if (isVideoEnabled) {
            new Thread(() -> {
                webcam = Webcam.getDefault();
                if (webcam != null) {
                    webcam.open();
                    
                    // Add local preview
                    ImageView localView = new ImageView();
                    localView.setFitWidth(200);
                    localView.setFitHeight(150);
                    localView.setPreserveRatio(true);
                    
                    VBox localBox = new VBox(5);
                    localBox.setAlignment(Pos.CENTER);
                    localBox.setStyle("-fx-background-color: #2b3930; -fx-padding: 10px; -fx-background-radius: 8px;");
                    Label localLbl = new Label("Moi");
                    localLbl.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
                    localBox.getChildren().addAll(localView, localLbl);
                    
                    Platform.runLater(() -> videoGrid.getChildren().add(localBox));

                    while (isActive) {
                        BufferedImage image = webcam.getImage();
                        if (image != null) {
                            WritableImage fxImage = SwingFXUtils.toFXImage(image, null);
                            Platform.runLater(() -> localView.setImage(fxImage));

                            try {
                                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                ImageIO.write(image, "jpg", baos);
                                byte[] frameData = baos.toByteArray();
                                SocketManager.getInstance().sendUdpMedia((byte) 3, groupId, frameData);
                            } catch (Exception e) {}
                        }
                        try { Thread.sleep(100); } catch (InterruptedException ignored) {} // ~10 FPS
                    }
                }
            }).start();
        }
    }

    public void receiveAudio(String senderPhone, byte[] data) {
        if (!isActive) return;
        try {
            SourceDataLine line = participantAudios.computeIfAbsent(senderPhone, k -> {
                try {
                    AudioFormat format = new AudioFormat(16000, 16, 1, true, true);
                    DataLine.Info sourceInfo = new DataLine.Info(SourceDataLine.class, format);
                    SourceDataLine newLine = (SourceDataLine) AudioSystem.getLine(sourceInfo);
                    newLine.open(format);
                    newLine.start();
                    return newLine;
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }
            });
            if (line != null) {
                line.write(data, 0, data.length);
            }
        } catch (Exception e) {}
    }

    public void receiveVideo(String senderPhone, byte[] data) {
        if (!isActive) return;
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
            if (image != null) {
                WritableImage fxImage = SwingFXUtils.toFXImage(image, null);
                
                ImageView view = participantVideos.get(senderPhone);
                if (view == null) {
                    // Create new video frame for this participant
                    ImageView newView = new ImageView();
                    newView.setFitWidth(200);
                    newView.setFitHeight(150);
                    newView.setPreserveRatio(true);
                    
                    VBox box = new VBox(5);
                    box.setAlignment(Pos.CENTER);
                    box.setStyle("-fx-background-color: #2b3930; -fx-padding: 10px; -fx-background-radius: 8px;");
                    Label nameLbl = new Label(senderPhone);
                    nameLbl.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
                    box.getChildren().addAll(newView, nameLbl);
                    
                    participantVideos.put(senderPhone, newView);
                    participantBoxes.put(senderPhone, box);
                    Platform.runLater(() -> videoGrid.getChildren().add(box));
                    view = newView;
                }
                
                final ImageView targetView = view;
                Platform.runLater(() -> targetView.setImage(fxImage));
            }
        } catch (Exception e) {}
    }

    public void removeParticipant(String senderPhone) {
        participantVideos.remove(senderPhone);
        VBox box = participantBoxes.remove(senderPhone);
        if (box != null) {
            Platform.runLater(() -> videoGrid.getChildren().remove(box));
        }
        SourceDataLine line = participantAudios.remove(senderPhone);
        if (line != null) {
            line.stop();
            line.close();
        }
    }

    public void leaveMeeting() {
        isActive = false;
        
        if (webcam != null && webcam.isOpen()) {
            webcam.close();
        }
        if (audioInput != null) {
            audioInput.stop();
            audioInput.close();
        }
        for (SourceDataLine line : participantAudios.values()) {
            line.stop();
            line.close();
        }
        participantAudios.clear();
        participantVideos.clear();
        participantBoxes.clear();

        // Signaler au serveur qu'on quitte
        String payload = "GROUP_CALL_LEAVE:" + groupId;
        SocketManager.getInstance().sendBinary("CALL_SIGNAL", "", "", payload.getBytes(StandardCharsets.UTF_8));

        Platform.runLater(() -> stage.close());
        if (onLeave != null) {
            onLeave.run();
        }
    }
}
