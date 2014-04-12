package com.iKeirNez.packetapi.implementations;

import com.iKeirNez.packetapi.api.Connection;
import com.iKeirNez.packetapi.api.ConnectionManager;
import com.iKeirNez.packetapi.api.HookType;
import com.iKeirNez.packetapi.api.packets.Packet;
import com.iKeirNez.packetapi.api.packets.PacketHandler;
import com.iKeirNez.packetapi.api.packets.PacketListener;
import com.iKeirNez.packetapi.threads.HeartbeatThread;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Created by iKeirNez on 06/04/2014.
 */
public class IConnectionManager implements ConnectionManager {

    protected List<Connection> connections = Collections.synchronizedList(new ArrayList<>());
    private Map<Class<? extends Packet>, List<PacketListener>> listeners = new ConcurrentHashMap<>();
    private Map<HookType, List<Consumer<Connection>>> hooks = new ConcurrentHashMap<>();

    private static HeartbeatThread heartbeatThread = new HeartbeatThread();

    /**
     * Constructs a new instance
     */
    public IConnectionManager(){
        heartbeatThread.addConnectionManager(this);
    }

    public IClientConnection newClientConnection(String serverAddress, int port) throws IOException {
        IClientConnection clientConnection = new IClientConnection(this, serverAddress, port);
        connections.add(clientConnection);
        return clientConnection;
    }

    public IServerConnection newServerConnection(String clientAddress, int port) throws IOException {
        IServerConnection serverConnection = new IServerConnection(this, clientAddress, port);
        connections.add(serverConnection);
        return serverConnection;
    }

    public void registerListener(PacketListener packetListener){
        for (Method method : packetListener.getClass().getMethods()){
            if (isMethodApplicable(method)){
                Class<? extends Packet> parameter = (Class<? extends Packet>) (method.getParameterCount() == 2 ? method.getParameterTypes()[1] : method.getParameterTypes()[0]);
                List<PacketListener> list = listeners.getOrDefault(parameter, new ArrayList<>());
                list.add(packetListener);
                listeners.put(parameter, list);
            }
        }
    }

    public boolean isListenerRegistered(PacketListener packetListener){
        for (Class<? extends Packet> clazz : listeners.keySet()){
            List<PacketListener> list = listeners.get(clazz);

            if (list.contains(packetListener)){
                return true;
            }
        }

        return false;
    }

    public void unregisterListener(PacketListener packetListener){
        for (Class<? extends Packet> clazz : listeners.keySet()){
            List<PacketListener> list = listeners.get(clazz);
            list.remove(packetListener);
            listeners.put(clazz, list);
        }
    }

    public void callListeners(IConnection connection, Packet packet){
        if (listeners.containsKey(packet.getClass())){
            for (PacketListener packetListener : listeners.get(packet.getClass())){
                for (Method method : packetListener.getClass().getMethods()){
                    if (isMethodApplicable(method)){
                        try {
                            if (method.getParameterCount() == 2){
                                method.invoke(packetListener, connection, packet);
                            } else {
                                method.invoke(packetListener, packet);
                            }
                        } catch (Throwable e) {
                            System.out.println("Error whilst invoking listener\nPacket: " + packet.getClass().getName() + "\nMethod: " + packetListener.getClass().getName() + "#" + method.getName());
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    private boolean isMethodApplicable(Method method){
        Class<?>[] parameters = method.getParameterTypes();
        return method.isAnnotationPresent(PacketHandler.class) && ((parameters.length == 1 && Packet.class.isAssignableFrom(parameters[0])) || (parameters.length == 2 && IConnection.class.equals(parameters[0].getClass()) && Packet.class.isAssignableFrom(parameters[1])));
    }

    public void addHook(HookType hookType, Consumer<Connection> consumer){
        List<Consumer<Connection>> list = hooks.getOrDefault(hookType, new ArrayList<>());
        list.add(consumer);
        hooks.put(hookType, list);
    }

    public void callHook(IConnection connection, HookType hookType){
        if (hooks.containsKey(hookType)){
            for (Consumer<Connection> consumer : hooks.get(hookType)){
                consumer.accept(connection);
            }
        }
    }

    public List<Connection> getConnections(){
        return connections;
    }

    @Override
    protected void finalize() throws Throwable {
        heartbeatThread.removeConnectionManager(this);
        super.finalize();
    }

}
