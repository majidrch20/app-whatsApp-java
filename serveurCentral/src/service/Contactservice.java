package service;

import dao.ContactDao;
import dao.UserDao;
import model.User;
import server.ChatServer;
import server.ClientHandler;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class Contactservice {
    private final ContactDao contactDao = new ContactDao();
    private final UserDao userDao = new UserDao();

    public void handle(int userId, String userPhone, String payload, ClientHandler handler) {
        if (payload.startsWith("ADD:")) {
            String[] parts = payload.substring(4).split(":", 2);
            String targetPhone = normalizePhone(parts[0]);
            String nickname = parts.length > 1 ? parts[1].trim() : null;
            String currentUserPhone = normalizePhone(userPhone);

            // ✅ Empêcher de s'ajouter soi-même
            if (targetPhone.equals(currentUserPhone)) {
                sendResponse(handler, "ADD_FAIL:SELF");
                return;
            }
            if (targetPhone.isEmpty()) {
                sendResponse(handler, "ADD_FAIL:NOT_FOUND");
                return;
            }

            User target = userDao.searchByPhone(targetPhone);

            System.out.println("[Contactservice] ADD demandé : " + targetPhone
                    + " → trouvé : " + (target != null ? target.getId() : "NULL")); // DEBUG

            if (target != null) {
                // ✅ Empêcher de s'ajouter soi-même par ID
                if (target.getId() == userId) {
                    sendResponse(handler, "ADD_FAIL:SELF");
                    return;
                }
                
                // ✅ Empêcher d'ajouter deux fois
                if (contactDao.contactExists(userId, target.getId())) {
                    sendResponse(handler, "ADD_FAIL:ALREADY_EXISTS");
                    return;
                }

                boolean added = contactDao.addContact(userId, target.getId(), nickname);
                System.out.println("[Contactservice] addContact résultat : " + added); // DEBUG
                if (!added) {
                    sendResponse(handler, "ADD_FAIL:DB");
                    return;
                }
                sendResponse(handler, "ADD_OK:" + targetPhone);
                handleGet(userId, handler);
            } else {
                sendResponse(handler, "ADD_FAIL:NOT_FOUND");
            }

        } else if (payload.equals("GET_CONTACTS")) {
            handleGet(userId, handler);

        } else if (payload.startsWith("REMOVE:")) {
            String targetPhone = normalizePhone(payload.substring(7));
            User target = userDao.searchByPhone(targetPhone);
            if (target != null) {
                contactDao.removeContact(userId, target.getId());
            }
            handleGet(userId, handler); // ✅ Renvoyer la liste après suppression
        } else if (payload.startsWith("CREATE_GROUP:")) {
            String[] parts = payload.substring(13).split(":", 2);
            String groupName = parts[0];
            String[] memberIdsStr = parts.length > 1 ? parts[1].split(",") : new String[0];
            java.util.List<Integer> memberIds = new java.util.ArrayList<>();
            for (String mId : memberIdsStr) {
                mId = mId.trim();
                if (mId.isEmpty()) continue;
                
                // Chercher d'abord par téléphone car l'UI envoie des numéros de téléphone
                User u = userDao.searchByPhone(normalizePhone(mId));
                if (u != null) {
                    memberIds.add(u.getId());
                } else {
                    // Fallback : essayer de parser comme ID
                    try {
                        memberIds.add(Integer.parseInt(mId));
                    } catch (NumberFormatException ignored) {}
                }
            }
            dao.GroupDao groupDao = new dao.GroupDao();
            int groupId = groupDao.createGroup(groupName, userId, memberIds);
            if (groupId != -1) {
                sendResponse(handler, "CREATE_GROUP_OK");
                handleGet(userId, handler);
                
                // Notifier immédiatement les autres membres en ligne du groupe
                for (int memberId : memberIds) {
                    if (memberId == userId) continue;
                    ClientHandler memberHandler = ChatServer.clients.get(memberId);
                    if (memberHandler != null) {
                        handleGet(memberId, memberHandler);
                        sendResponse(memberHandler, "NOTIFY_ADDED_TO_GROUP:" + groupName);
                    }
                }
            } else {
                sendResponse(handler, "CREATE_GROUP_FAIL");
            }
        } else if (payload.startsWith("ADD_GROUP_MEMBER:")) {
            String[] parts = payload.substring(17).split(":");
            if (parts.length >= 2) {
                int groupId = Integer.parseInt(parts[0]);
                String targetPhone = normalizePhone(parts[1]);
                User targetUser = userDao.searchByPhone(targetPhone);
                if (targetUser != null) {
                    dao.GroupDao groupDao = new dao.GroupDao();
                    if (groupDao.addMemberToGroup(groupId, targetUser.getId())) {
                        sendResponse(handler, "ADD_MEMBER_OK");
                        // Renvoyer les contacts mis à jour à l'expéditeur
                        handleGet(userId, handler);
                        // Renvoyer les contacts mis à jour au nouvel ajouté
                        ClientHandler targetHandler = ChatServer.clients.get(targetUser.getId());
                        if (targetHandler != null) {
                            handleGet(targetUser.getId(), targetHandler);
                            String groupName = groupDao.getGroupName(groupId);
                            sendResponse(targetHandler, "NOTIFY_ADDED_TO_GROUP:" + groupName);
                        }
                    } else {
                        sendResponse(handler, "ADD_MEMBER_FAIL");
                    }
                } else {
                    sendResponse(handler, "ADD_MEMBER_FAIL:NOT_FOUND");
                }
            }
        } else if (payload.startsWith("REMOVE_GROUP_MEMBER:")) {
            String[] parts = payload.substring(20).split(":");
            if (parts.length >= 2) {
                int groupId = Integer.parseInt(parts[0]);
                String targetPhone = normalizePhone(parts[1]);
                User targetUser = userDao.searchByPhone(targetPhone);
                if (targetUser != null) {
                    dao.GroupDao groupDao = new dao.GroupDao();
                    if (groupDao.removeMemberFromGroup(groupId, targetUser.getId())) {
                        sendResponse(handler, "REMOVE_MEMBER_OK");
                        handleGet(userId, handler);
                        ClientHandler targetHandler = ChatServer.clients.get(targetUser.getId());
                        if (targetHandler != null) {
                            handleGet(targetUser.getId(), targetHandler);
                            String groupName = groupDao.getGroupName(groupId);
                            sendResponse(targetHandler, "NOTIFY_REMOVED_FROM_GROUP:" + groupName);
                        }
                    } else {
                        sendResponse(handler, "REMOVE_MEMBER_FAIL");
                    }
                } else {
                    sendResponse(handler, "REMOVE_MEMBER_FAIL:NOT_FOUND");
                }
            }
        }
    }


    public void handleGet(int userId, ClientHandler handler) {
        List<String[]> list = contactDao.getContactsWithNickname(userId);
        StringBuilder sb = new StringBuilder("CONTACTS_LIST:");
        for (String[] c : list) {
            int contactId = Integer.parseInt(c[0]);
            String phone    = c[1];
            String username = c[2];
            String status   = ChatServer.clients.containsKey(contactId) ? "ONLINE" : "OFFLINE";
            String nickname = c[4];
            String displayName = (nickname != null && !nickname.isEmpty()) ? nickname : username;
            sb.append(phone).append(":").append(displayName).append(":").append(status).append(":").append(contactId).append("|");
        }
        
        dao.GroupDao groupDao = new dao.GroupDao();
        List<model.Group> groups = groupDao.getGroupsForUser(userId);
        for (model.Group g : groups) {
            sb.append("GROUP:").append(g.getId()).append(":").append(g.getName()).append(":ONLINE:-1|");
        }
        
        sendResponse(handler, sb.toString());
    }

    private void sendResponse(ClientHandler handler, String msg) {
        try {
            // ✅ Format binaire pour que le client reçoive correctement
            handler.send("CONTACT_SIGNAL", "SERVER", "", msg.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String normalizePhone(String input) {
        if (input == null) return "";
        String normalized = input.replaceAll("[\\s\\-()]", "");
        if (normalized.startsWith("00")) {
            normalized = "+" + normalized.substring(2);
        }
        return normalized.trim();
    }
}