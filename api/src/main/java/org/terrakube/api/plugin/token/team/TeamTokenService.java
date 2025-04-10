package org.terrakube.api.plugin.token.team;

import com.nimbusds.jose.shaded.json.JSONArray;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.terrakube.api.repository.TeamTokenRepository;
import org.terrakube.api.rs.token.group.Group;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class TeamTokenService {

    @Value("${org.terrakube.token.pat}")
    private String base64Key;

    @Autowired
    private TeamTokenRepository teamTokenRepository;
    private static final String ISSUER = "Terrakube";

    public String createTeamToken(String group, int days, int hours, int minutes, String description, JwtAuthenticationToken principalJwt) {
        List<String> currentGroups = getCurrentGroups(principalJwt);

        if (currentGroups.indexOf(group) > -1) {
            return createToken(days, hours, minutes, description, group, (String) principalJwt.getTokenAttributes().get("email"));
        }

        return "";
    }

    public String createToken(int days, int hours, int minutes, String description, String groupName, String ownerEmail) {

        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64URL.decode(this.base64Key));
        UUID keyId = UUID.randomUUID();

        log.info("Generated Team Token {}", keyId);

        JSONArray groupArray = new JSONArray();
        groupArray.add(groupName);

        Date expiration = Date.from(Instant.now().plus(days, ChronoUnit.DAYS).plus(hours, ChronoUnit.HOURS).plus(minutes, ChronoUnit.MINUTES));

        log.info("Team token will expire: {}", expiration);

        String jws = Jwts.builder()
                .setIssuer(ISSUER)
                .setSubject(String.format("%s (Team Token)", groupName))
                .setAudience(ISSUER)
                .setId(keyId.toString())
                .claim("email", ownerEmail)
                .claim("description", description)
                .claim("email_verified", true)
                .claim("name", String.format("%s (Token)", groupName))
                .claim("groups", groupArray)
                .setIssuedAt(Date.from(Instant.now()))
                .setExpiration(expiration)
                .signWith(key)
                .compact();

        Group groupToken = new Group();
        groupToken.setId(keyId);
        groupToken.setDays(days);
        groupToken.setHours(hours);
        groupToken.setMinutes(minutes);
        groupToken.setGroup(groupName);
        groupToken.setDescription(description);

        teamTokenRepository.save(groupToken);

        return jws;
    }

    public List<Group> searchToken(JwtAuthenticationToken principalJwt){
        return teamTokenRepository.findByGroupIn(getCurrentGroups(principalJwt));
    }

    public List<String> getCurrentGroups(JwtAuthenticationToken principalJwt) {
        Object groups = principalJwt.getTokenAttributes().get("groups");
        JSONArray array = (JSONArray) groups;
        List<String> list = new ArrayList();
        for (int i = 0; i < array.size(); i++) {
            list.add(array.get(i).toString());
        }
        return list;
    }
}
