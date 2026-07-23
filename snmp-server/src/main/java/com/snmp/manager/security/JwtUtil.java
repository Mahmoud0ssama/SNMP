/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.snmp.manager.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import java.util.Date;

/**
 *
 * @author marwan
 */


/**
 * Utility class for handling JSON Web Tokens (JWT).
 * Responsible for generating and verifying tokens for API authentication.
 */
public class JwtUtil {
    
    // Secret key for signing the JWT (Keep this secure in production!)
    private static final String SECRET_KEY = "ITI_SNMP_GRADUATION_PROJECT_SECRET";
    private static final Algorithm ALGORITHM = Algorithm.HMAC256(SECRET_KEY);

    /**
     * Generates a JWT token upon successful login.
     * 
     * @param userId   The database ID of the authenticated user.
     * @param username The username of the authenticated user.
     * @param role     The access role (e.g., ADMIN, SUPPORT).
     * @return A signed JWT string valid for 1 hour.
     */
    public static String generateToken(Long userId, String username, String role) {
        return JWT.create()
                .withIssuer("SNMP_Manager")
                .withClaim("userId", userId) // Embedded User ID for tracking actions (e.g., resolving traps)
                .withClaim("username", username)
                .withClaim("role", role)
                .withExpiresAt(new Date(System.currentTimeMillis() + 3600000)) // Token expires in 1 hour (3600 seconds)
                .sign(ALGORITHM);
    }

    /**
     * Verifies the incoming JWT token from the client's Authorization header.
     * 
     * @param token The JWT string to verify.
     * @return The DecodedJWT object containing the claims if successful.
     * @throws JWTVerificationException if the token is invalid, manipulated, or expired.
     */
    public static DecodedJWT verifyToken(String token) throws JWTVerificationException {
        return JWT.require(ALGORITHM)
                .withIssuer("SNMP_Manager")
                .build()
                .verify(token);
    }
}
