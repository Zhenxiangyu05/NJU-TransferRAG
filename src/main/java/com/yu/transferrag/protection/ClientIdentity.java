package com.yu.transferrag.protection;

import jakarta.servlet.http.HttpServletRequest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

public final class ClientIdentity {
    private ClientIdentity() {}
    static String canonical(String address) {
        if(address==null || address.length()>45 || !address.matches("[0-9a-fA-F:.]+")
                || (!address.contains(":") && !address.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}"))) throw new IllegalArgumentException("Invalid numeric address");
        try {return InetAddress.getByName(address).getHostAddress();}
        catch(Exception e){throw new IllegalArgumentException("Invalid numeric address");}
    }
    public static String hash(HttpServletRequest request,RateLimitProperties p) {
        String peer=canonical(request.getRemoteAddr());String address=peer;
        // Never use XFF. Only an explicitly trusted direct peer may supply a single X-Real-IP.
        if(p.trustedProxies().stream().map(ClientIdentity::canonical).anyMatch(peer::equals)) {
            try { address=canonical(request.getHeader("X-Real-IP")); }
            catch(IllegalArgumentException ignored) { address=peer; }
        }
        try {
            Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(p.hmacSecret().getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(address.getBytes(StandardCharsets.UTF_8)));
        }catch(Exception e){throw new IllegalStateException("Client identity unavailable");}
    }
}
