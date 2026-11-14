package com.gogleox.scrip.api;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
/** Explicit server registry for optional integrations; unregistered sources cannot issue funds. */
public final class TrustedRewardSourceRegistry {
    private final Map<String,TrustedRewardSource> sources=new ConcurrentHashMap<>();
    public boolean register(TrustedRewardSource source){Objects.requireNonNull(source);return sources.putIfAbsent(source.id(),source)==null;}
    public Optional<TrustedRewardSource> find(String id){return Optional.ofNullable(sources.get(id));}
}
