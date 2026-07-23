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

public class JwtUtil {
    
    // Secret key for signing the JWT (Keep this secure in production!)
    private static final String SECRET_KEY = "ITI_SNMP_GRADUATION_PROJECT_SECRET";
    private static final Algorithm ALGORITHM = Algorithm.HMAC256(SECRET_KEY);

    // Generates a JWT token upon successful login
    public static String generateToken(String username, String role) {
        return JWT.create()
                .withIssuer("SNMP_Manager")
                .withClaim("username", username)
                .withClaim("role", role)
                .withExpiresAt(new Date(System.currentTimeMillis() + 3600000)) // Token expires in 1 hour (3600 seconds)
                .sign(ALGORITHM);
    }

    // Verifies the incoming JWT token from the client
    public static DecodedJWT verifyToken(String token) throws JWTVerificationException {
        return JWT.require(ALGORITHM)
                .withIssuer("SNMP_Manager")
                .build()
                .verify(token);
    }
}
