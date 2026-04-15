package com.fitness.gateway;

import com.fitness.gateway.user.RegisterRequest;
import com.fitness.gateway.user.UserService;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Slf4j
@RequiredArgsConstructor
public class KeycloakUserSyncFilter implements WebFilter {

    // Service used to call USER-SERVICE
    private final UserService userService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {

        // Extract Authorization token
        String token = exchange.getRequest().getHeaders().getFirst("Authorization");

        // Extract custom user id header
        String userId = exchange.getRequest().getHeaders().getFirst("X-User-ID");

        // Extract user details from JWT token
        RegisterRequest registerRequest = getUserDetails(token);

        // If header missing -> use keycloak id
        if (userId == null) {
            userId = registerRequest.getKeycloakId();
        }

        // Only process if token & userId available
        if (userId != null && token != null) {

            String finalUserId = userId;

            return userService.validateUser(userId)

                    .flatMap(exist -> {

                        // If user not exists
                        if (!exist) {

                            // Register user automatically
                            if (registerRequest != null) {

                                return userService.registerUser(registerRequest).then(Mono.empty());
                            } else {
                                return Mono.empty();
                            }

                        } else {

                            // Skip registration
                            log.info("User already exist, Skipping sync.");
                            return Mono.empty();
                        }
                    })

                    // Continue request processing
                    .then(Mono.defer(() -> {

                        // Add userId header for downstream services
                        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate().header("X-User-ID", finalUserId).build();

                        return chain.filter(exchange.mutate().request(mutatedRequest).build());
                    }));
        }

        return chain.filter(exchange);
    }

    private RegisterRequest getUserDetails(String token) {
        try {

            // Remove Bearer prefix
            String tokenWithoutBearer = token.replace("Bearer ", "").trim();

            // Parse JWT
            SignedJWT signedJWT = SignedJWT.parse(tokenWithoutBearer);

            // Extract claims
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

            RegisterRequest registerRequest = new RegisterRequest();

            // Extract user info from token
            registerRequest.setEmail(claims.getStringClaim("email"));
            registerRequest.setKeycloakId(claims.getStringClaim("sub"));

            // Dummy password since authentication handled by Keycloak
            registerRequest.setPassword("dummy@123123");

            registerRequest.setFirstName(claims.getStringClaim("given_name"));
            registerRequest.setLastName(claims.getStringClaim("family_name"));

            return registerRequest;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
