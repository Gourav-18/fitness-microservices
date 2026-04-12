package com.fitness.gateway.user;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Service // Marks this class as Spring service
@Slf4j   // Enables logging
public class UserService {

    // WebClient used to call USER-SERVICE
    @Autowired
    private WebClient userServiceWebClient;

    // Validate whether user exists in User Service
    public Mono<Boolean> validateUser(String userId) {

        log.info("Calling User Validation API for userId: {}", userId);

        return userServiceWebClient.get()
                .uri("/api/users/{userId}/validate", userId) // endpoint call
                .retrieve()
                .bodyToMono(Boolean.class) // expecting Boolean response
                .onErrorResume(WebClientResponseException.class, e -> {

                    // If user not found
                    if (e.getStatusCode() == HttpStatus.NOT_FOUND)
                        return Mono.error(new RuntimeException("User Not Found: " + userId));

                        // Bad request
                    else if (e.getStatusCode() == HttpStatus.BAD_REQUEST)
                        return Mono.error(new RuntimeException("Invalid Request: " + userId));

                    // Other errors
                    return Mono.error(new RuntimeException("Unexpected error: " + e.getMessage()));
                });
    }

    // Register new user in User Service
    public Mono<UserResponse> registerUser(RegisterRequest request) {

        log.info("Calling User Registration API for email: {}", request.getEmail());

        return userServiceWebClient.post()
                .uri("/api/users/register") // registration endpoint
                .bodyValue(request) // sending request body
                .retrieve()
                .bodyToMono(UserResponse.class) // expecting UserResponse
                .onErrorResume(WebClientResponseException.class, e -> {

                    if (e.getStatusCode() == HttpStatus.BAD_REQUEST)
                        return Mono.error(new RuntimeException("Bad Request: " + e.getMessage()));

                    else if (e.getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR)
                        return Mono.error(new RuntimeException("Internal Server Error: " + e.getMessage()));

                    return Mono.error(new RuntimeException("Unexpected error: " + e.getMessage()));
                });
    }
}