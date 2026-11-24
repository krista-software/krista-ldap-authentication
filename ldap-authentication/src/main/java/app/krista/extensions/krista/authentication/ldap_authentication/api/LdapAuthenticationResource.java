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

package app.krista.extensions.krista.authentication.ldap_authentication.api;

import java.io.InputStream;
import java.net.http.HttpHeaders;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import app.krista.extension.authorization.AuthorizationException;
import app.krista.extension.common.AuthenticationResponse;
import app.krista.extension.common.CommonUtils;
import app.krista.extension.executor.Invoker;
import app.krista.extensions.krista.authentication.ldap_authentication.LdapAuthenticationExtension;
import app.krista.extensions.krista.authentication.ldap_authentication.impl.LdapAuthenticator;
import app.krista.ksdk.accounts.Account;
import app.krista.ksdk.accounts.AccountManager;
import app.krista.ksdk.authentication.AuthenticationSettings;
import app.krista.ksdk.authentication.SessionManager;
import app.krista.ksdk.authorization.ModifiableRole;
import app.krista.ksdk.authorization.Role;
import app.krista.ksdk.authorization.RoleManager;
import app.krista.ksdk.context.RuntimeContext;
import app.krista.model.field.util.GsonJsonMapper;

@SuppressWarnings("deprecation")
@Path("/")
public class LdapAuthenticationResource {

    private final static GsonJsonMapper GSON_JSON_MAPPER = GsonJsonMapper.create();
    private final static String DEFAULT_ROLE = "Krista Client User";

    private final SessionManager sessionManager;
    private final AccountManager accountManager;
    private final RuntimeContext runtimeContext;
    private final RoleManager roleManager;

    private final Invoker invoker;
    private final AuthenticationSettings authenticationSettings;

    @Inject
    public LdapAuthenticationResource(SessionManager sessionManager, AccountManager accountManager,
            RuntimeContext runtimeContext, RoleManager roleManager, @Named("self") Invoker invoker,
            AuthenticationSettings authenticationSettings) {
        this.sessionManager = sessionManager;
        this.accountManager = accountManager;
        this.runtimeContext = runtimeContext;
        this.roleManager = roleManager;
        this.invoker = invoker;
        this.authenticationSettings = authenticationSettings;
    }

    @GET
    @Path("authenticator.js")
    public InputStream getAuthenticator() {
        return getClass().getClassLoader().getResourceAsStream("authenticator.js");
    }

    @GET
    @Path("/docs")
    public InputStream customTabs() {
        return getClass().getClassLoader().getResourceAsStream("documentation.html");
    }

    @OPTIONS
    @Path("/login")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response loginOptions(@Context HttpHeaders headers, String email) {
        return getResponseBuilderWithCORSHeaders().build();
    }

    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response login(@Context HttpHeaders headers, Map<String, String> loginInput) throws AuthorizationException {
        if (loginInput != null && loginInput.containsKey("userName")) {
            String userName = loginInput.get("userName");
            String password = loginInput.get("password");
            if (authenticateLdapUser(userName, password)) {
                String email = userName;
                if (CommonUtils.isValidEmail(userName)) {
                    Map<String, String> profile = getLdapUserProfile(userName);
                    email = profile.get("email");
                }
                addDomainToWorkspaceIfNotPresent(email);
                Account account = getAccount(userName, email, getUserAgent(headers));
                String sessionId = sessionManager.createSession(account.getAccountId());
                AuthenticationResponse authenticationResponse = createAuthenticationResponse(account, sessionId);
                return getResponseBuilderWithCORSHeaders().entity(GSON_JSON_MAPPER.toString(authenticationResponse))
                        .build();
            }
            throw new IllegalArgumentException("Invalid username or password.");
        }
        throw new IllegalArgumentException("Invalid email input.");
    }

    private void addDomainToWorkspaceIfNotPresent(String email) throws AuthorizationException {
        List<String> supportedDomainsForWorkspace = authenticationSettings.getSupportedDomains();
        String supportedDomain = String.join(",", supportedDomainsForWorkspace);
        String invokerDomains = email.substring(email.indexOf("@") + 1);
        CommonUtils.validateIfSupportedDomain(email, supportedDomain, invokerDomains);
        CommonUtils.addSupportedDomainsToWorkspace(email, supportedDomainsForWorkspace, authenticationSettings);
    }

    private Map<String, String> getLdapUserProfile(String userName) {
        String ldapUrl = (String) invoker.getAttributes().get(LdapAuthenticationExtension.URL);
        String baseDN = (String) invoker.getAttributes().get(LdapAuthenticationExtension.BASE_DN);
        String bindDN = (String) invoker.getAttributes().get(LdapAuthenticationExtension.BIND_DN);
        String bindDnPassword = (String) invoker.getAttributes().get(LdapAuthenticationExtension.BIND_DN_PASSWORD);
        String emailProperty = getProperty(LdapAuthenticationExtension.EMAIL_PROPERTY, "mail");
        String phoneProperty = getProperty(LdapAuthenticationExtension.PHONE_PROPERTY, "telephoneNumber");
        return LdapAuthenticator.getLdapUserProfile(ldapUrl, baseDN, bindDN, bindDnPassword, userName, emailProperty,
                phoneProperty);
    }

    private String getProperty(String attributeName, String defaultValue) {
        String ldapAttribute = (String) invoker.getAttributes().get(attributeName);
        if (ldapAttribute == null) {
            ldapAttribute = defaultValue;
        }
        return ldapAttribute;
    }

    private boolean authenticateLdapUser(String email, String password) {
        String ldapUrl = (String) invoker.getAttributes().get(LdapAuthenticationExtension.URL);
        String baseDN = (String) invoker.getAttributes().get(LdapAuthenticationExtension.BASE_DN);
        String bindDN = (String) invoker.getAttributes().get(LdapAuthenticationExtension.BIND_DN);
        String bindDnPassword = (String) invoker.getAttributes().get(LdapAuthenticationExtension.BIND_DN_PASSWORD);
        return LdapAuthenticator.authenticate(ldapUrl, baseDN, bindDN, bindDnPassword, email, password);
    }

    private AuthenticationResponse createAuthenticationResponse(Account account, String sessionId)
            throws AuthorizationException {
        return new AuthenticationResponse(sessionId, account.getPerson().getPersonName(),
                account.getPerson().getAvatarUrl(), account.getAccountId(),
                runtimeContext.getKristaAccount().getAccountId(), account.getPerson().getPersonId(),
                getListOfRoles(account.getRoles()), account.getInboxId(), roleManager.isWorkspaceAdmin(account),
                false,
                Map.of("email", account.getPrimaryEmailAddress()),
                Map.of("creationTime", getCurrentDateTime()));
    }

    private List<String> getListOfRoles(Set<Role> roles) {
        List<String> allRoles = new ArrayList<>();
        for (Role role : roles) {
            allRoles.add(role.getRoleId());
        }
        return allRoles;
    }

    private Account getAccount(String name, String email, String userAgent)
            throws AuthorizationException {
        String defaultRole = createRoleIfNotPresent();
        Account account = accountManager.lookupAccount(email);
        if (account == null) {
            account = accountManager.createAccount(name, email,
                    Set.of(defaultRole),//Default role
                    Map.of("ORG", "KristaSoft", "KRISTA_SOURCE", "EXTENSION_OPEN_LDAP_AUTHENTICATION",
                            "KRISTA_LAST_LOGIN",
                            getCurrentDateTime(), "KRISTA_USER_AGENT_INFO", userAgent == null ? "" : userAgent));
        }
        return account;
    }

    private String createRoleIfNotPresent() throws AuthorizationException {
        String defaultRoleName = getDefaultRoleName();
        String defaultRoleId = getDefaultRoleIdIfPresent(defaultRoleName);
        if (defaultRoleId == null) {
            ModifiableRole role = roleManager.createRole(defaultRoleName);
            defaultRoleId = role.getRoleId();
        }
        return defaultRoleId;
    }

    private String getDefaultRoleIdIfPresent(String defaultRoleName) throws AuthorizationException {
        String roleId = null;
        Iterable<ModifiableRole> roles = roleManager.getRoles();
        if (roles != null) {
            for (ModifiableRole role : roles) {
                if (Objects.equals(defaultRoleName, role.getName())) {
                    roleId = role.getRoleId();
                    break;
                }
            }
        }
        return roleId;
    }

    private String getDefaultRoleName() {
        String defaultRole = DEFAULT_ROLE;
        Object defaultRoleObject = invoker.getAttributes().get(LdapAuthenticationExtension.DEFAULT_ROLE_KEY);
        if (defaultRoleObject instanceof String && !((String) defaultRoleObject).trim().isEmpty()) {
            defaultRole = (String) defaultRoleObject;
        }
        return defaultRole;
    }

    private String getCurrentDateTime() {
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss Z");
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        return dateFormat.format(Calendar.getInstance().getTime());
    }

    private Response.ResponseBuilder getResponseBuilderWithCORSHeaders() {
        return Response.status(200)
                .header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Methods", "POST, GET, OPTIONS")
                .header("Access-Control-Allow-Headers", "Content-Type");
    }

    private String getUserAgent(HttpHeaders headers) {
        String userAgent = "NOT FOUND";
        if (headers != null && headers.firstValue("User-Agent").isPresent()) {
            userAgent = headers.firstValue("User-Agent").get();
        }
        return userAgent;
    }

}
