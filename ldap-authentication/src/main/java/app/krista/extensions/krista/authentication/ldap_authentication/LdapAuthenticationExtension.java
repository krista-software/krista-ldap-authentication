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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import javax.inject.Inject;
import app.krista.extension.authorization.RequestAuthenticator;
import app.krista.extension.impl.anno.*;
import app.krista.extension.request.ProtoRequest;
import app.krista.extension.request.protos.http.HttpRequest;
import app.krista.ksdk.authentication.SessionManager;
import app.krista.ksdk.context.AuthorizationContext;

@SuppressWarnings("deprecation")
@Field(name = LdapAuthenticationExtension.URL, type = "Text")
@Field(name = LdapAuthenticationExtension.BASE_DN, type = "Text")
@Field(name = LdapAuthenticationExtension.BIND_DN, type = "Text")
@Field(name = LdapAuthenticationExtension.BIND_DN_PASSWORD, type = "Text", attributes = @Attribute(name = "isSecured", value = "true"))
@Field(name = LdapAuthenticationExtension.DEFAULT_ROLE_KEY, type = "Text")
@Field(name = LdapAuthenticationExtension.EMAIL_PROPERTY, type = "Text")
@Field(name = LdapAuthenticationExtension.PHONE_PROPERTY, type = "Text")
@Java(version = Java.Version.JAVA_21)
@Extension(jaxrsId = "authn", version="3.5.7", name="Ldap Authentication")
@Domain(id = "catEntryDomain_db053e8f-a194-4dde-aa6f-701ef7a6b3a7",
        name = "Authentication",
        ecosystemId = "catEntryEcosystem_d3b05047-07b0-4b06-95a3-9fb8f7f608d9",
        ecosystemName = "Krista",
        ecosystemVersion = "3e7e09ed-688f-41fa-ab7c-ff879e750011")
public class LdapAuthenticationExtension {

    public static final String URL = "LDAP_SERVER_URL";
    public static final String BASE_DN = "BASE_DN";
    public static final String BIND_DN = "BIND_DN";
    public static final String BIND_DN_PASSWORD = "BIND_DN_PASSWORD";
    public static final String DEFAULT_ROLE_KEY = "DEFAULT_ROLE";
    public static final String EMAIL_PROPERTY = "MAPPED_EMAIL_PROPERTY";
    public static final String PHONE_PROPERTY = "MAPPED_PHONE_PROPERTY";

    private final AuthorizationContext authorizationContext;
    private final SessionManager sessionManager;

    @Inject
    public LdapAuthenticationExtension(AuthorizationContext authorizationContext, SessionManager sessionManager) {
        this.authorizationContext = authorizationContext;
        this.sessionManager = sessionManager;
    }

    // TODO:: Please implement Authenticator. From Now this will not work
    @InvokerRequest(InvokerRequest.Type.AUTHENTICATOR)
    public RequestAuthenticator getAuthenticatedAccountId() {
        return new LdapAuthenticationRequestAuthenticator(authorizationContext, sessionManager);
    }

    @InvokerRequest(InvokerRequest.Type.CUSTOM_TABS)
    public Map<String, String> customTab() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("Documentation", "authn/docs");
        return map;
    }

    private String handleLoginRequest(ProtoRequest protoRequest) {
        if (!(protoRequest instanceof HttpRequest)) {
            return null;
        }
        if (Objects.equals("/login", ((HttpRequest) protoRequest).getUri().getPath())) {
            System.out.println("Authorization Context: " + authorizationContext.getAuthorizedAccount().getAccountId());
            return authorizationContext.getAuthorizedAccount().getAccountId();
        }
        return null;
    }

}
