/*
 * Ldap Authentication Extension for Krista
 * Copyright (C) 2025 Krista Software
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package app.krista.extensions.krista.authentication.ldap_authentication;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import com.unboundid.ldap.sdk.LDAPException;

import app.krista.extensions.krista.authentication.ldap_authentication.impl.LdapAuthenticator;

/**
 * Comprehensive unit tests for LdapAuthenticator.
 *
 * Tests cover:
 * - Authentication with valid/invalid credentials
 * - User profile retrieval
 * - Error handling and edge cases
 * - LDAP connection management
 *
 * Uses in-memory LDAP server for isolated testing.
 *
 * @author Krista Software
 */
@DisplayName("LDAP Authenticator Tests")
public class LdapAuthenticatorTest {

    private static InMemoryDirectoryServer ldapServer;
    private static String ldapUrl;
    private static final String BASE_DN = "dc=example,dc=com";
    private static final String BIND_DN = "cn=admin,dc=example,dc=com";
    private static final String BIND_PASSWORD = "adminPassword";

    // Test user credentials
    private static final String TEST_USERNAME = "testuser";
    private static final String TEST_PASSWORD = "testPassword123";
    private static final String TEST_EMAIL = "testuser@example.com";
    private static final String TEST_PHONE = "+1-555-0100";
    private static final String TEST_CN = "Test User";

    /**
     * Set up in-memory LDAP server before all tests.
     * Creates test users and organizational structure.
     */
    @BeforeAll
    public static void setupLdapServer() throws Exception {
        // Configure in-memory LDAP server
        InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig(BASE_DN);
        config.addAdditionalBindCredentials(BIND_DN, BIND_PASSWORD);

        // Create listener on random available port
        InMemoryListenerConfig listenerConfig = InMemoryListenerConfig.createLDAPConfig("default", 0);
        config.setListenerConfigs(listenerConfig);

        // Create and start server
        ldapServer = new InMemoryDirectoryServer(config);

        // Add base DN
        ldapServer.add("dn: " + BASE_DN,
                "objectClass: top",
                "objectClass: domain",
                "dc: example");

        // Add organizational unit for users
        ldapServer.add("dn: ou=users," + BASE_DN,
                "objectClass: top",
                "objectClass: organizationalUnit",
                "ou: users");

        // Add test user with complete profile
        ldapServer.add("dn: uid=" + TEST_USERNAME + ",ou=users," + BASE_DN,
                "objectClass: top",
                "objectClass: person",
                "objectClass: organizationalPerson",
                "objectClass: inetOrgPerson",
                "uid: " + TEST_USERNAME,
                "cn: " + TEST_CN,
                "sn: User",
                "givenName: Test",
                "mail: " + TEST_EMAIL,
                "telephoneNumber: " + TEST_PHONE,
                "userPassword: " + TEST_PASSWORD);

        // Add user without email (for testing missing email scenario)
        ldapServer.add("dn: uid=noemail,ou=users," + BASE_DN,
                "objectClass: top",
                "objectClass: person",
                "objectClass: organizationalPerson",
                "objectClass: inetOrgPerson",
                "uid: noemail",
                "cn: No Email User",
                "sn: User",
                "userPassword: password123");

        // Add user without phone (for testing optional phone scenario)
        ldapServer.add("dn: uid=nophone,ou=users," + BASE_DN,
                "objectClass: top",
                "objectClass: person",
                "objectClass: organizationalPerson",
                "objectClass: inetOrgPerson",
                "uid: nophone",
                "cn: No Phone User",
                "sn: User",
                "mail: nophone@example.com",
                "userPassword: password123");

        ldapServer.startListening();

        // Get the actual port assigned
        int port = ldapServer.getListenPort();
        ldapUrl = "ldap://localhost:" + port;

        System.out.println("In-memory LDAP server started on: " + ldapUrl);
    }

    /**
     * Shut down LDAP server after all tests.
     */
    @AfterAll
    public static void tearDownLdapServer() {
        if (ldapServer != null) {
            ldapServer.shutDown(true);
            System.out.println("In-memory LDAP server stopped");
        }
    }


    // ========================================
    // Authentication Tests
    // ========================================

    @Test
    @DisplayName("Should authenticate user with valid credentials")
    public void testAuthenticateWithValidCredentials() {
        // Arrange - Given valid credentials

        // Act - When authenticating
        boolean result = LdapAuthenticator.authenticate(
                ldapUrl, BASE_DN, BIND_DN, BIND_PASSWORD, TEST_USERNAME, TEST_PASSWORD);

        // Assert - Then authentication should succeed
        assertTrue(result, "Authentication should succeed with valid credentials");
    }

    @Test
    @DisplayName("Should fail authentication with invalid password")
    public void testAuthenticateWithInvalidPassword() {
        // Arrange - Given invalid password
        String wrongPassword = "wrongPassword";

        // Act - When authenticating
        boolean result = LdapAuthenticator.authenticate(
                ldapUrl, BASE_DN, BIND_DN, BIND_PASSWORD, TEST_USERNAME, wrongPassword);

        // Assert - Then authentication should fail
        assertFalse(result, "Authentication should fail with invalid password");
    }

    @Test
    @DisplayName("Should throw exception when user not found")
    public void testAuthenticateWithNonExistentUser() {
        // Arrange - Given non-existent username
        String nonExistentUser = "nonexistent";

        // Act & Assert - When authenticating, then should throw exception
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            LdapAuthenticator.authenticate(
                    ldapUrl, BASE_DN, BIND_DN, BIND_PASSWORD, nonExistentUser, "anyPassword");
        });

        assertTrue(exception.getMessage().contains("not found"),
                "Exception message should indicate user not found");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("Should fail authentication with null or empty username")
    public void testAuthenticateWithInvalidUsername(String invalidUsername) {
        // Arrange - Given null or empty username

        // Act & Assert - When authenticating, then should throw exception
        assertThrows(Exception.class, () -> {
            LdapAuthenticator.authenticate(
                    ldapUrl, BASE_DN, BIND_DN, BIND_PASSWORD, invalidUsername, TEST_PASSWORD);
        }, "Should throw exception for null or empty username");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("Should fail authentication with null or empty password")
    public void testAuthenticateWithInvalidPassword(String invalidPassword) {
        // Arrange - Given null or empty password

        // Act - When authenticating
        boolean result = LdapAuthenticator.authenticate(
                ldapUrl, BASE_DN, BIND_DN, BIND_PASSWORD, TEST_USERNAME, invalidPassword);

        // Assert - Then authentication should fail
        assertFalse(result, "Authentication should fail with null or empty password");
    }

    @Test
    @DisplayName("Should throw exception with invalid LDAP URL")
    public void testAuthenticateWithInvalidLdapUrl() {
        // Arrange - Given invalid LDAP URL
        String invalidUrl = "ldap://invalid-host:9999";

        // Act & Assert - When authenticating, should throw exception
        assertThrows(IllegalStateException.class, () -> {
            LdapAuthenticator.authenticate(
                    invalidUrl, BASE_DN, BIND_DN, BIND_PASSWORD, TEST_USERNAME, TEST_PASSWORD);
        }, "Should throw exception with invalid LDAP URL");
    }

    @Test
    @DisplayName("Should fail authentication with invalid bind credentials")
    public void testAuthenticateWithInvalidBindCredentials() {
        // Arrange - Given invalid bind credentials
        String wrongBindPassword = "wrongAdminPassword";

        // Act & Assert - When authenticating, then should throw exception
        assertThrows(IllegalStateException.class, () -> {
            LdapAuthenticator.authenticate(
                    ldapUrl, BASE_DN, BIND_DN, wrongBindPassword, TEST_USERNAME, TEST_PASSWORD);
        }, "Should throw exception with invalid bind credentials");
    }

    @Test
    @DisplayName("Should fail authentication with invalid base DN")
    public void testAuthenticateWithInvalidBaseDN() {
        // Arrange - Given invalid base DN
        String invalidBaseDN = "dc=invalid,dc=com";

        // Act & Assert - When authenticating, then should throw exception
        assertThrows(IllegalStateException.class, () -> {
            LdapAuthenticator.authenticate(
                    ldapUrl, invalidBaseDN, BIND_DN, BIND_PASSWORD, TEST_USERNAME, TEST_PASSWORD);
        }, "Should throw exception with invalid base DN");
    }

    // ========================================
    // User Profile Retrieval Tests
    // ========================================

    @Test
    @DisplayName("Should retrieve complete user profile")
    public void testGetLdapUserProfile() {
        // Arrange - Given valid user
        String emailProperty = "mail";
        String phoneProperty = "telephoneNumber";

        // Act - When retrieving profile
        Map<String, String> profile = LdapAuthenticator.getLdapUserProfile(
                ldapUrl, BASE_DN, BIND_DN, BIND_PASSWORD, TEST_USERNAME, emailProperty, phoneProperty);

        // Assert - Then profile should contain all expected fields
        assertNotNull(profile, "Profile should not be null");
        assertEquals(TEST_EMAIL, profile.get("email"), "Email should match");
        assertEquals(TEST_PHONE, profile.get("phone"), "Phone should match");
        assertEquals(TEST_CN, profile.get("Name"), "Name should match");
        assertNotNull(profile.get("NameInNamespace"), "NameInNamespace should be present");
        assertTrue(profile.get("NameInNamespace").contains(TEST_USERNAME),
                "NameInNamespace should contain username");
    }

    @Test
    @DisplayName("Should retrieve profile with empty phone when not present")
    public void testGetLdapUserProfileWithoutPhone() {
        // Arrange - Given user without phone
        String username = "nophone";
        String emailProperty = "mail";
        String phoneProperty = "telephoneNumber";

        // Act - When retrieving profile
        Map<String, String> profile = LdapAuthenticator.getLdapUserProfile(
                ldapUrl, BASE_DN, BIND_DN, BIND_PASSWORD, username, emailProperty, phoneProperty);

        // Assert - Then profile should have empty phone
        assertNotNull(profile, "Profile should not be null");
        assertEquals("nophone@example.com", profile.get("email"), "Email should be present");
        assertEquals("", profile.get("phone"), "Phone should be empty string when not present");
    }


    @Test
    @DisplayName("Should throw exception when email property not found")
    public void testGetLdapUserProfileWithoutEmail() {
        // Arrange - Given user without email
        String username = "noemail";
        String emailProperty = "mail";
        String phoneProperty = "telephoneNumber";

        // Act & Assert - When retrieving profile, then should throw exception
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            LdapAuthenticator.getLdapUserProfile(
                    ldapUrl, BASE_DN, BIND_DN, BIND_PASSWORD, username, emailProperty, phoneProperty);
        });

        assertTrue(exception.getMessage().contains("Email address not found"),
                "Exception message should indicate email not found");
    }

    @Test
    @DisplayName("Should throw exception when user not found for profile retrieval")
    public void testGetLdapUserProfileForNonExistentUser() {
        // Arrange - Given non-existent user
        String nonExistentUser = "nonexistent";
        String emailProperty = "mail";
        String phoneProperty = "telephoneNumber";

        // Act & Assert - When retrieving profile, then should throw exception
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            LdapAuthenticator.getLdapUserProfile(
                    ldapUrl, BASE_DN, BIND_DN, BIND_PASSWORD, nonExistentUser, emailProperty, phoneProperty);
        });

        assertTrue(exception.getMessage().contains("not found"),
                "Exception message should indicate user not found");
    }

    @Test
    @DisplayName("Should handle custom email property name")
    public void testGetLdapUserProfileWithCustomEmailProperty() {
        // Arrange - Given custom email property (using 'mail' which exists)
        String customEmailProperty = "mail";
        String phoneProperty = "telephoneNumber";

        // Act - When retrieving profile with custom property
        Map<String, String> profile = LdapAuthenticator.getLdapUserProfile(
                ldapUrl, BASE_DN, BIND_DN, BIND_PASSWORD, TEST_USERNAME, customEmailProperty, phoneProperty);

        // Assert - Then profile should be retrieved successfully
        assertNotNull(profile, "Profile should not be null");
        assertEquals(TEST_EMAIL, profile.get("email"), "Email should be retrieved from custom property");
    }

    // ========================================
    // NameInNamespace Tests
    // ========================================

    @Test
    @DisplayName("Should retrieve user NameInNamespace")
    public void testGetLdapUserNameInNameSpace() {
        // Arrange - Given valid user

        // Act - When retrieving NameInNamespace
        String nameInNamespace = LdapAuthenticator.getLdapUserNameInNameSpace(
                ldapUrl, BASE_DN, BIND_DN, BIND_PASSWORD, TEST_USERNAME);

        // Assert - Then should return valid DN
        assertNotNull(nameInNamespace, "NameInNamespace should not be null");
        assertTrue(nameInNamespace.contains("uid=" + TEST_USERNAME),
                "NameInNamespace should contain uid");
        assertTrue(nameInNamespace.contains(BASE_DN),
                "NameInNamespace should contain base DN");
    }

    @Test
    @DisplayName("Should throw exception when user not found for NameInNamespace")
    public void testGetLdapUserNameInNameSpaceForNonExistentUser() {
        // Arrange - Given non-existent user
        String nonExistentUser = "nonexistent";

        // Act & Assert - When retrieving NameInNamespace, then should throw exception
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            LdapAuthenticator.getLdapUserNameInNameSpace(
                    ldapUrl, BASE_DN, BIND_DN, BIND_PASSWORD, nonExistentUser);
        });

        assertTrue(exception.getMessage().contains("not found"),
                "Exception message should indicate user not found");
    }

    // ========================================
    // Edge Cases and Error Handling Tests
    // ========================================

    @Test
    @DisplayName("Should handle concurrent authentication requests")
    public void testConcurrentAuthentication() throws InterruptedException {
        // Arrange - Given multiple threads
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        boolean[] results = new boolean[threadCount];

        // Act - When authenticating concurrently
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                results[index] = LdapAuthenticator.authenticate(
                        ldapUrl, BASE_DN, BIND_DN, BIND_PASSWORD, TEST_USERNAME, TEST_PASSWORD);
            });
            threads[i].start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // Assert - Then all authentications should succeed
        for (int i = 0; i < threadCount; i++) {
            assertTrue(results[i], "Concurrent authentication " + i + " should succeed");
        }
    }

    @Test
    @DisplayName("Should throw exception for username with special characters")
    public void testAuthenticateWithSpecialCharactersInUsername() {
        // Arrange - Given username with special characters (that don't exist)
        String specialUsername = "user@domain.com";

        // Act & Assert - When authenticating, should throw exception (user not found)
        assertThrows(IllegalStateException.class, () -> {
            LdapAuthenticator.authenticate(
                    ldapUrl, BASE_DN, BIND_DN, BIND_PASSWORD, specialUsername, TEST_PASSWORD);
        }, "Should throw exception for non-existent user");
    }

    @Test
    @DisplayName("Should handle special characters in password")
    public void testAuthenticateWithSpecialCharactersInPassword() {
        // Arrange - Given password with special characters
        String specialPassword = "P@ssw0rd!#$%^&*()";

        // Act - When authenticating
        boolean result = LdapAuthenticator.authenticate(
                ldapUrl, BASE_DN, BIND_DN, BIND_PASSWORD, TEST_USERNAME, specialPassword);

        // Assert - Then authentication should fail (wrong password)
        assertFalse(result, "Authentication should fail with wrong password containing special characters");
    }

}