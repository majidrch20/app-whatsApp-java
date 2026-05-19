package authUi;

import client.NetworkClient;
import client.SocketManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ContactView {

    private final NetworkClient network;
    private final VBox convList;
    private final Map<String, HBox> contactRows = new HashMap<>();

    public interface ConversationOpenCallback {
        void open(String phone, String name, String status);
    }

    private ConversationOpenCallback openCallback;

    public ContactView(NetworkClient network, VBox convList) {
        this.network = network;
        this.convList = convList;
    }

    public void setConversationOpenCallback(ConversationOpenCallback cb) {
        this.openCallback = cb;
    }

    public void loadContacts() {
        client.SocketManager.getInstance().sendBinary("CONTACT_SIGNAL", "", "", "GET_CONTACTS".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public void updateContacts(String payload) {
        Platform.runLater(() -> {
            if (payload == null) return;

            // Gestion des notifications de groupes
            if (payload.startsWith("NOTIFY_ADDED_TO_GROUP:")) {
                String groupName = payload.substring("NOTIFY_ADDED_TO_GROUP:".length());
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
                alert.setTitle("Nouveau Groupe");
                alert.setHeaderText(null);
                alert.setContentText("Vous avez été ajouté au groupe : " + groupName);
                alert.showAndWait();
                return;
            }
            if (payload.startsWith("NOTIFY_REMOVED_FROM_GROUP:")) {
                String groupName = payload.substring("NOTIFY_REMOVED_FROM_GROUP:".length());
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
                alert.setTitle("Groupe Retiré");
                alert.setHeaderText(null);
                alert.setContentText("Vous avez été retiré du groupe : " + groupName);
                alert.showAndWait();
                return;
            }

            // Gestion des erreurs d'ajout
            if (payload.startsWith("ADD_FAIL:")) {
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
                alert.setTitle("Erreur d'ajout");
                alert.setHeaderText(null);
                if (payload.equals("ADD_FAIL:NOT_FOUND")) {
                    alert.setContentText("Ce numéro n'existe pas dans la base de données ! Impossible de l'ajouter.");
                } else if (payload.equals("ADD_FAIL:SELF")) {
                    alert.setContentText("Vous ne pouvez pas vous ajouter vous-même !");
                } else if (payload.equals("ADD_FAIL:ALREADY_EXISTS")) {
                    alert.setContentText("Ce contact est déjà dans votre liste !");
                } else {
                    alert.setContentText("Impossible d'ajouter ce contact pour le moment.");
                }
                alert.showAndWait();
                return;
            }
            if (payload.startsWith("ADD_OK:")) {
                loadContacts();
                return;
            }

            if (payload.equals("ADD_MEMBER_OK") || payload.equals("REMOVE_MEMBER_OK")) {
                javafx.application.Platform.runLater(() -> {
                    javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
                    alert.setTitle("Succès");
                    alert.setHeaderText(null);
                    alert.setContentText(payload.contains("ADD") ? "Membre ajouté avec succès." : "Membre retiré avec succès.");
                    alert.showAndWait();
                });
                return;
            }

            if (payload.startsWith("ADD_MEMBER_FAIL") || payload.startsWith("REMOVE_MEMBER_FAIL")) {
                javafx.application.Platform.runLater(() -> {
                    javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
                    alert.setTitle("Erreur");
                    alert.setHeaderText(null);
                    if (payload.contains("NOT_FOUND")) {
                        alert.setContentText("Ce numéro n'existe pas dans la base de données !");
                    } else {
                        alert.setContentText("Impossible d'effectuer cette opération.");
                    }
                    alert.showAndWait();
                });
                return;
            }

            if (payload.startsWith("STATUS:")) {
                String[] parts = payload.substring(7).split(":"); // phone:ONLINE|
                if (parts.length >= 2) {
                    String phone = parts[0];
                    String status = parts[1].replace("|", "");
                    HBox row = contactRows.get(phone);
                    if (row != null) {
                        Label statusLabel = (Label) row.getProperties().get("statusLabel");
                        if (statusLabel != null) {
                            statusLabel.setText(status.equals("ONLINE") ? "En ligne" : "Hors ligne");
                            statusLabel.setStyle("-fx-text-fill: " + (status.equals("ONLINE") ? "#25D366" : "gray") + "; -fx-font-size: 12px;");
                        }
                    }
                }
                return;
            }

            if (!payload.startsWith("CONTACTS_LIST:")) return;

            convList.getChildren().clear();
            contactRows.clear();
            String data = payload.substring("CONTACTS_LIST:".length());
            
            if (data.trim().isEmpty()) {
                Label noContacts = new Label("Aucun contact.");
                noContacts.setStyle("-fx-text-fill: gray; -fx-padding: 15px;");
                convList.getChildren().add(noContacts);
                return;
            }

            // Le serveur utilise "|" pour séparer les lignes et ":" pour séparer les colonnes
            String[] rows = data.split("\\|");
            for (String row : rows) {
                if (row.trim().isEmpty()) continue;
                if (row.startsWith("GROUP:")) {
                    String[] parts = row.substring(6).split(":");
                    if (parts.length >= 2) {
                        addContactUI("GROUP:" + parts[0], parts[1], "ONLINE");
                    }
                } else {
                    String[] parts = row.split(":");
                    if (parts.length >= 3) {
                        addContactUI(parts[0], parts[1], parts[2]);
                    }
                }
            }
        });
    }

    public void filterContacts(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        for (Map.Entry<String, HBox> entry : contactRows.entrySet()) {
            HBox row = entry.getValue();
            String searchable = String.valueOf(row.getProperties().getOrDefault("searchText", ""));
            row.setManaged(normalized.isEmpty() || searchable.contains(normalized));
            row.setVisible(normalized.isEmpty() || searchable.contains(normalized));
        }
    }

    private void addContactUI(String phone, String name, String status) {
        // Enregistrer la correspondance numéro -> nom
        SocketManager.phoneToName.put(phone, name);

        HBox item = new HBox(12);
        item.setPadding(new Insets(12, 15, 12, 15));
        item.setAlignment(Pos.CENTER_LEFT);
        item.setStyle("-fx-background-color: #161616; -fx-border-color: #282828; -fx-border-width: 0 0 1 0;");
        item.setOnMouseEntered(e -> item.setStyle("-fx-background-color: #2a2a2a; -fx-border-color: #282828; -fx-border-width: 0 0 1 0;"));
        item.setOnMouseExited(e -> item.setStyle("-fx-background-color: #161616; -fx-border-color: #282828; -fx-border-width: 0 0 1 0;"));
        
        StackPane avatar = ChatView.buildAvatar(name, 48);

        VBox info = new VBox(2);
        Label nameLbl = new Label(name);
        nameLbl.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 15px;");
        
        String subtitle = phone.startsWith("GROUP:") ? "Groupe de discussion" : phone;
        Label phoneLbl = new Label(subtitle);
        phoneLbl.setStyle("-fx-text-fill: gray; -fx-font-size: 11px;");
        
        info.getChildren().addAll(nameLbl, phoneLbl);

        Label statusLbl = new Label("ONLINE".equals(status) ? "En ligne" : "Hors ligne");
        statusLbl.setStyle("-fx-text-fill: " + ("ONLINE".equals(status) ? "#25D366" : "gray") + "; -fx-font-size: 12px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnDelete = new Button("🗑");
        btnDelete.setStyle("-fx-background-color: transparent; -fx-text-fill: #dc3c3c; -fx-font-size: 16px; -fx-cursor: hand;");
        btnDelete.setOnAction(e -> {
            e.consume();
            boolean isGrp = phone.startsWith("GROUP:");
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION);
            alert.setTitle(isGrp ? "Quitter le groupe" : "Supprimer le contact");
            alert.setHeaderText(null);
            alert.setContentText(isGrp ? "Voulez-vous vraiment quitter le groupe \"" + name + "\" ?" : "Voulez-vous vraiment supprimer le contact \"" + name + "\" ?");
            
            java.util.Optional<javafx.scene.control.ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == javafx.scene.control.ButtonType.OK) {
                SocketManager.getInstance().sendBinary("CONTACT_SIGNAL", "", "", ("REMOVE:" + phone).getBytes(StandardCharsets.UTF_8));
            }
        });

        item.getChildren().addAll(avatar, info, statusLbl, spacer, btnDelete);
        item.setOnMouseClicked(e -> {
            if (openCallback != null) openCallback.open(phone, name, status);
        });

        item.getProperties().put("statusLabel", statusLbl);
        item.getProperties().put("searchText", (phone + " " + name).toLowerCase(Locale.ROOT));
        contactRows.put(phone, item);
        convList.getChildren().add(item);
    }
}