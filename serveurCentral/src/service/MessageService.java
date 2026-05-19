package service;

import dao.MessageDao;
import dao.UserDao;
import model.Message;
import server.ChatServer;
import server.ClientHandler;

import java.io.IOException;
import java.util.List;

/**
 * MessageService — Traitement et livraison des messages.
 */
public class MessageService {

    private final MessageDao messageDao = new MessageDao();
    private final UserDao    userDao    = new UserDao();

    /**
     * Traite un message entrant :
     * 1. Sauvegarde en DB
     * 2. Livraison si destinataire connecté → DELIVERED
     * 3. Sinon reste NOT_DELIVERED → livré plus tard
     */
    public void process(Message m, String receiverPhone, byte[] data) {
        // Sauvegarde en DB
        int msgId = messageDao.save(m, data);
        boolean persisted = msgId != -1;
        if (!persisted) {
            System.err.println("[MessageService] Erreur : Impossible de sauvegarder le message en base de données (vérifiez la taille du fichier et le type de colonne data, e.g. LONGBLOB).");
            System.err.println("[MessageService] Tentative de livraison temps réel...");
        }

        // Livraison si connecté
        ClientHandler receiver = ChatServer.clients.get(m.getReceiverId());
        if (receiver == null && receiverPhone != null && !receiverPhone.isBlank()) {
            receiver = findOnlineByPhone(receiverPhone);
        }
        if (receiver != null) {
            try {
                byte[] toSend = m.isText()
                        ? m.getContent().getBytes(java.nio.charset.StandardCharsets.UTF_8)
                        : data;
                receiver.send(m.getType(),
                        m.getSenderPhone(),
                        String.valueOf(msgId),
                        m.getFilename() != null ? m.getFilename() : "",
                        toSend);
                if (persisted) {
                    messageDao.updateEtat(msgId, "DELIVERED");
                }
                System.out.println("[MessageService] Message livré à id="
                        + m.getReceiverId());
            } catch (IOException e) {
                System.err.println("[MessageService] Erreur livraison : "
                        + e.getMessage());
            }
        } else {
            if (persisted) {
                System.out.println("[MessageService] Destinataire hors ligne, "
                        + "message sauvegardé (id=" + msgId + ")");
            } else {
                System.err.println("[MessageService] Destinataire hors ligne et sauvegarde échouée, message perdu.");
            }
        }
    }

    public void processGroupMessage(Message m, int groupId, byte[] data) {
        dao.GroupDao groupDao = new dao.GroupDao();
        List<Integer> memberIds = groupDao.getGroupMemberIds(groupId);
        
        // Vérifier si l'expéditeur fait toujours partie du groupe
        if (!memberIds.contains(m.getSenderId())) {
            System.err.println("[MessageService] Expéditeur " + m.getSenderId() + " n'est plus membre du groupe " + groupId + ". Message ignoré.");
            return;
        }

        int msgId = messageDao.save(m, data);
        boolean persisted = msgId != -1;
        if (!persisted) {
            System.err.println("[MessageService] Erreur DB pour groupe " + groupId);
        } else {
            messageDao.updateEtat(msgId, "DELIVERED"); // On considère délivré pour les groupes
        }

        for (int memberId : memberIds) {
            if (memberId == m.getSenderId()) continue;
            ClientHandler receiver = ChatServer.clients.get(memberId);
            if (receiver != null) {
                try {
                    byte[] toSend = m.isText() ? m.getContent().getBytes(java.nio.charset.StandardCharsets.UTF_8) : data;
                    // Format: type, senderPhone, filename, data
                    // senderPhone is sent as "GROUP:groupId:senderPhone" so client knows it's a group
                    String senderInfo = "GROUP:" + groupId + ":" + m.getSenderPhone();
                    receiver.send(m.getType(), senderInfo, String.valueOf(msgId), m.getFilename() != null ? m.getFilename() : "", toSend);
                } catch (Exception e) {}
            }
        }
    }

    /**
     * Livre tous les messages non délivrés lors de la reconnexion.
     */
    public void deliverOfflineMessages(int userId, String userPhone,
                                       ClientHandler handler) {
        List<Message> pending = messageDao.getUndelivered(userId);
        if (pending.isEmpty()) return;

        System.out.println("[MessageService] Livraison de "
                + pending.size() + " message(s) hors-ligne à " + userPhone);

        for (Message m : pending) {
            try {
                byte[] data;
                if (m.isText()) {
                    data = m.getContent().getBytes(
                            java.nio.charset.StandardCharsets.UTF_8);
                } else {
                    data = messageDao.getDataById(m.getId());
                }
                handler.send(m.getType(), m.getSenderPhone(), String.valueOf(m.getId()),
                        m.getFilename() != null ? m.getFilename() : "", data);
                messageDao.updateEtat(m.getId(), "DELIVERED");
            } catch (IOException e) {
                System.err.println("[MessageService] Erreur livraison offline : "
                        + e.getMessage());
            }
        }
    }

    private boolean isSamePhone(String p1, String p2) {
        if (p1 == null || p2 == null) return false;
        String d1 = p1.replaceAll("[^0-9]", "");
        String d2 = p2.replaceAll("[^0-9]", "");
        if (d1.equals(d2)) return true;
        
        int len1 = d1.length();
        int len2 = d2.length();
        if (len1 >= 9 && len2 >= 9) {
            return d1.substring(len1 - 9).equals(d2.substring(len2 - 9));
        }
        return false;
    }

    private ClientHandler findOnlineByPhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) return null;
        for (ClientHandler handler : ChatServer.clients.values()) {
            if (isSamePhone(phone, handler.getUserPhone())) {
                return handler;
            }
        }
        return null;
    }
}