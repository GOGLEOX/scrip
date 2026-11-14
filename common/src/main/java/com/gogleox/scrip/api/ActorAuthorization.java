package com.gogleox.scrip.api;
public record ActorAuthorization(boolean allowed, String reason) { public ActorAuthorization { if(reason==null) reason=""; } }
