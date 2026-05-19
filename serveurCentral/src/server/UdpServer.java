package server;

import service.MeetingService;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * UdpServer gère la réception et la diffusion des paquets multimédias (audio/vidéo).
 */
public class UdpServer extends Thread {

    public static final int UDP_PORT = 5001;

    // Type de paquets
    public static final byte TYPE_BIND = 1;
    public static final byte TYPE_AUDIO = 2;
    public static final byte TYPE_VIDEO = 3;

    private DatagramSocket socket;
    
    // Map: userId -> Adresse IP + Port du client (pour lui envoyer les paquets)
    private final Map<Integer, InetSocketAddress> clientAddresses = new ConcurrentHashMap<>();

    // Map: Adresse IP + Port (String) -> userId (pour identifier l'expéditeur)
    private final Map<String, Integer> addressToUserId = new ConcurrentHashMap<>();

    public UdpServer() {
        try {
            socket = new DatagramSocket(UDP_PORT);
            System.out.println("[UdpServer] Démarré sur le port UDP " + UDP_PORT);
        } catch (Exception e) {
            System.err.println("[UdpServer] Erreur de démarrage: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        if (socket == null) return;
        byte[] buffer = new byte[65535]; // Max UDP packet size

        while (true) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                InetSocketAddress senderAddress = (InetSocketAddress) packet.getSocketAddress();
                
                DataInputStream in = new DataInputStream(new ByteArrayInputStream(packet.getData(), packet.getOffset(), packet.getLength()));
                byte type = in.readByte();

                if (type == TYPE_BIND) {
                    int userId = in.readInt();
                    clientAddresses.put(userId, senderAddress);
                    addressToUserId.put(senderAddress.toString(), userId);
                    System.out.println("[UdpServer] Client lié: userId=" + userId + " -> " + senderAddress);
                } 
                else if (type == TYPE_AUDIO || type == TYPE_VIDEO) {
                    int groupId = in.readInt();
                    String senderPhone = in.readUTF();
                    
                    int dataLength = in.readInt();
                    byte[] mediaData = new byte[dataLength];
                    in.readFully(mediaData);

                    // Identifier l'expéditeur pour éviter l'écho
                    Integer senderId = addressToUserId.get(senderAddress.toString());
                    
                    // Relayer aux autres membres actifs du groupe
                    Set<Integer> participants = MeetingService.getInstance().getActiveParticipants(groupId);
                    if (participants != null) {
                        for (int pId : participants) {
                            if (senderId != null && pId == senderId.intValue()) continue; // Ne pas renvoyer à l'expéditeur
                            
                            InetSocketAddress targetAddress = clientAddresses.get(pId);
                            if (targetAddress != null) {
                                sendMediaPacket(targetAddress, type, senderPhone, mediaData);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[UdpServer] Erreur lors de la réception/relais: " + e.getMessage());
            }
        }
    }

    private void sendMediaPacket(InetSocketAddress target, byte type, String senderPhone, byte[] mediaData) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            
            out.writeByte(type);
            out.writeUTF(senderPhone);
            out.writeInt(mediaData.length);
            out.write(mediaData);
            out.flush();

            byte[] sendData = baos.toByteArray();
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, target.getAddress(), target.getPort());
            socket.send(sendPacket);
        } catch (Exception e) {
            System.err.println("[UdpServer] Erreur envoi UDP: " + e.getMessage());
        }
    }
}
