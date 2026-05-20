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
        if (persisted) {
            System.out.println("[DB_SAVE] message enregistré (id=" + msgId + ", expéditeur=" + m.getSenderPhone() + ", destinataire=" + receiverPhone + ")");
        } else {
            System.err.println("[MessageService] Erreur : Impossible de sauvegarder le message en base de données.");
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
                System.out.println("[SERVER_FORWARD] message transféré au destinataire connecté (id=" + m.getReceiverId() + ", phone=" + receiverPhone + ")");
            } catch (IOException e) {
                System.err.println("[SERVER_FORWARD] Erreur de transfert : " + e.getMessage());
            }
        } else {
            if (persisted) {
                System.out.println("[SERVER_FORWARD] destinataire hors ligne, message stocké en DB (id=" + msgId + ")");
            } else {
                System.err.println("[SERVER_FORWARD] Destinataire hors ligne et sauvegarde échouée.");
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
        if (persisted) {
            System.out.println("[DB_SAVE] message de groupe enregistré (id=" + msgId + ", groupe=" + groupId + ", expéditeur=" + m.getSenderPhone() + ")");
            messageDao.updateEtat(msgId, "DELIVERED"); // On considère délivré pour les groupes
        } else {
            System.err.println("[MessageService] Erreur DB pour groupe " + groupId);
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
                    System.out.println("[SERVER_FORWARD] message de groupe transféré au membre connecté (id=" + memberId + ", groupe=" + groupId + ")");
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