package com.gogleox.scrip.network;

import java.util.*;

/** Server-only negotiated capability, rate, and replay state; no client identity is trusted from a payload. */
public final class ProtocolSessionRegistry {
    private final Map<UUID, Session> sessions = new HashMap<>();
    public synchronized Optional<WalletProtocol.Error> negotiate(UUID player, int version, int capabilities, long now) {
        if (version != WalletProtocol.VERSION || capabilities < 0) return Optional.of(WalletProtocol.Error.INCOMPATIBLE_PROTOCOL);
        sessions.put(player, new Session(capabilities, now)); return Optional.empty();
    }
    public synchronized Optional<WalletProtocol.Error> accept(UUID player, long requestId, int bytes, long now) {
        if (bytes < 0 || bytes > WalletProtocol.MAX_PACKET_BYTES) return Optional.of(WalletProtocol.Error.OVERSIZED);
        Session session=sessions.get(player); if(session==null)return Optional.of(WalletProtocol.Error.NOT_NEGOTIATED);
        if(now-session.windowStart>=WalletProtocol.RATE_WINDOW_MILLIS){session.windowStart=now;session.count=0;}
        if (session.ids.contains(requestId)) return Optional.of(WalletProtocol.Error.REPLAYED);
        if(session.count>=WalletProtocol.MAX_REQUESTS_PER_WINDOW)return Optional.of(WalletProtocol.Error.RATE_LIMITED);
        session.count++;
        session.ids.add(requestId);
        while(session.ids.size()>256)session.ids.removeFirst(); return Optional.empty();
    }
    public synchronized boolean negotiated(UUID player){return sessions.containsKey(player);}
    public synchronized void remove(UUID player){sessions.remove(player);}
    private static final class Session { final int capabilities; long windowStart; int count; final LinkedHashSet<Long> ids=new LinkedHashSet<>(); Session(int capabilities,long now){this.capabilities=capabilities;windowStart=now;} }
}
