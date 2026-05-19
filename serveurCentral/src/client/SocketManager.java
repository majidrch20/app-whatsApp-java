package client;

import java.io.*;
import java.net.Socket;

public class SocketManager {
    public static final java.util.Map<String, String> phoneToName = new java.util.concurrent.ConcurrentHashMap<>();
    private static SocketManager instance;
    private Socket socket;
    private DataOutputStream binOut;
    private DataInputStream binIn;

    private int userId;      // Ajouté pour stocker l'ID
    private String userPhone;
    private boolean authenticated = false;

    private SocketManager() {}

    public static synchronized SocketManager getInstance() {
        if (instance == null) instance = new SocketManager();
        return instance;
    }

    // --- MÉTHODES MANQUANTES À AJOUTER ---
    public void setUserPhone(String phone) {
        this.userPhone = phone;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }
    // -------------------------------------

    public void enableBinaryMode() throws IOException {
        this.binOut = new DataOutputStream(socket.getOutputStream());
        this.binIn = new DataInputStream(socket.getInputStream());
        this.authenticated = true;
    }

    public synchronized void sendBinary(String type, String receiver, String filename, byte[] data) {
        try {
            if (binOut == null) return;
            binOut.writeUTF(type);
            binOut.writeUTF(receiver);
            binOut.writeUTF(userPhone != null ? userPhone : "");
            binOut.writeUTF(filename != null ? filename : "");
            binOut.writeInt(data.length);
            binOut.write(data);
            binOut.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void startListening(MessageListener listener) {
        new Thread(() -> {
            try {
                while (true) {
                    String type     = binIn.readUTF();
                    String sender   = binIn.readUTF();
                    String msgIdStr = binIn.readUTF(); // ✅ On récupère le msgId au lieu de l'ignorer
                    String filename = binIn.readUTF(); // ✅ filename correct
                    int size        = binIn.readInt();
                    byte[] data     = new byte[size];
                    binIn.readFully(data);
                    listener.onMessage(type, sender, msgIdStr, filename, data);
                }
            } catch (Exception e) {
                listener.onDisconnect();
            }
        }).start();
    }

    public interface MessageListener {
        void onMessage(String type, String sender, String msgIdStr, String filename, byte[] data);
        void onDisconnect();
    }

    private java.net.DatagramSocket udpSocket;
    private java.net.InetAddress udpServerAddress;
    private int udpServerPort;

    public void initAuth(Socket s, int id, String p) {
        this.socket = s;
        this.userId = id;
        this.userPhone = p;
    }

    public void initUdp(String host, int port, MessageListener listener) {
        try {
            this.udpServerAddress = java.net.InetAddress.getByName(host);
            this.udpServerPort = port;
            this.udpSocket = new java.net.DatagramSocket();

            // Lancer le thread d'écoute UDP
            new Thread(() -> {
                byte[] buffer = new byte[65535];
                while (udpSocket != null && !udpSocket.isClosed()) {
                    try {
                        java.net.DatagramPacket packet = new java.net.DatagramPacket(buffer, buffer.length);
                        udpSocket.receive(packet);

                        DataInputStream in = new DataInputStream(new ByteArrayInputStream(packet.getData(), packet.getOffset(), packet.getLength()));
                        byte type = in.readByte();
                        String senderPhone = in.readUTF();
                        int dataLength = in.readInt();
                        byte[] mediaData = new byte[dataLength];
                        in.readFully(mediaData);

                        String strType = (type == 2) ? "UDP_AUDIO" : "UDP_VIDEO";
                        if (listener != null) {
                            listener.onMessage(strType, senderPhone, "", "", mediaData);
                        }
                    } catch (Exception e) {
                        break;
                    }
                }
            }).start();

            // Envoyer le paquet BIND (Type=1)
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            out.writeByte(1); // TYPE_BIND
            out.writeInt(this.userId);
            out.flush();
            byte[] bindData = baos.toByteArray();
            udpSocket.send(new java.net.DatagramPacket(bindData, bindData.length, udpServerAddress, udpServerPort));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendUdpMedia(byte type, int groupId, byte[] data) {
        if (udpSocket == null || udpServerAddress == null) return;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            out.writeByte(type);
            out.writeInt(groupId);
            out.writeUTF(userPhone != null ? userPhone : "");
            out.writeInt(data.length);
            out.write(data);
            out.flush();

            byte[] packetData = baos.toByteArray();
            udpSocket.send(new java.net.DatagramPacket(packetData, packetData.length, udpServerAddress, udpServerPort));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void reset() {
        try { if(instance != null && instance.socket != null) instance.socket.close(); } catch(Exception e){}
        try { if(instance != null && instance.udpSocket != null) instance.udpSocket.close(); } catch(Exception e){}
        instance = null;
    }
}