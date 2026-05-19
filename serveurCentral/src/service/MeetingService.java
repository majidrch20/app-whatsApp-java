package service;

import dao.GroupDao;
import server.ChatServer;
import server.ClientHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service pour gérer les réunions de groupe (audio/vidéo).
 */
public class MeetingService {

    private static final MeetingService INSTANCE = new MeetingService();

    private MeetingService() {}

    public static MeetingService getInstance() {
        return INSTANCE;
    }

    // Map: groupId -> Set of participant userIds
    private final Map<Integer, Set<Integer>> activeMeetings = new ConcurrentHashMap<>();
    private final GroupDao groupDao = new GroupDao();

    /**
     * Démarrer une nouvelle réunion pour un groupe.
     * @param callType "audio" ou "video"
     */
    public void startMeeting(int groupId, int starterId, String starterPhone, String callType) {
        System.out.println("[MeetingService] " + starterPhone + " démarre une réunion (" + callType + ") pour le groupe " + groupId);
        
        Set<Integer> participants = activeMeetings.computeIfAbsent(groupId, k -> ConcurrentHashMap.newKeySet());
        participants.add(starterId);

        // Notifier tous les autres membres du groupe
        List<Integer> memberIds = groupDao.getGroupMemberIds(groupId);
        for (int memberId : memberIds) {
            if (memberId == starterId) continue;
            
            ClientHandler memberHandler = ChatServer.clients.get(memberId);
            if (memberHandler != null) {
                try {
                    String signal = "GROUP_CALL_INCOMING:" + groupId + ":" + starterPhone + ":" + callType;
                    memberHandler.send("CALL_SIGNAL", starterPhone, "", signal.getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    System.err.println("[MeetingService] Erreur notification GROUP_CALL_INCOMING: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Un membre rejoint la réunion.
     */
    public void joinMeeting(int groupId, int joinerId, String joinerPhone) {
        Set<Integer> participants = activeMeetings.get(groupId);
        if (participants != null) {
            participants.add(joinerId);
            System.out.println("[MeetingService] " + joinerPhone + " a rejoint la réunion du groupe " + groupId);
            
            // Notifier les autres participants que quelqu'un a rejoint
            for (int pId : participants) {
                if (pId == joinerId) continue;
                ClientHandler pHandler = ChatServer.clients.get(pId);
                if (pHandler != null) {
                    try {
                        String signal = "GROUP_CALL_JOINED:" + groupId + ":" + joinerPhone;
                        pHandler.send("CALL_SIGNAL", joinerPhone, "", signal.getBytes(StandardCharsets.UTF_8));
                    } catch (IOException e) {}
                }
            }
        } else {
            System.err.println("[MeetingService] Tentative de rejoindre une réunion inexistante: groupe " + groupId);
        }
    }

    /**
     * Un membre quitte la réunion.
     */
    public void leaveMeeting(int groupId, int leaverId, String leaverPhone) {
        Set<Integer> participants = activeMeetings.get(groupId);
        if (participants != null) {
            participants.remove(leaverId);
            System.out.println("[MeetingService] " + leaverPhone + " a quitté la réunion du groupe " + groupId);
            
            if (participants.isEmpty()) {
                System.out.println("[MeetingService] La réunion du groupe " + groupId + " est maintenant vide. Fin de la réunion.");
                activeMeetings.remove(groupId);
            } else {
                // Notifier les autres participants du départ
                for (int pId : participants) {
                    ClientHandler pHandler = ChatServer.clients.get(pId);
                    if (pHandler != null) {
                        try {
                            String signal = "GROUP_CALL_LEFT:" + groupId + ":" + leaverPhone;
                            pHandler.send("CALL_SIGNAL", leaverPhone, "", signal.getBytes(StandardCharsets.UTF_8));
                        } catch (IOException e) {}
                    }
                }
            }
        }
    }

    /**
     * Obtenir les participants actifs pour relayer les flux audio/vidéo.
     */
    public Set<Integer> getActiveParticipants(int groupId) {
        return activeMeetings.getOrDefault(groupId, new HashSet<>());
    }
}
