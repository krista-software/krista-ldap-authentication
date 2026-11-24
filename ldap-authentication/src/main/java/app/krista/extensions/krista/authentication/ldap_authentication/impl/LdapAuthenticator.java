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

package app.krista.extensions.krista.authentication.ldap_authentication.impl;

import java.util.Map;
import java.util.Properties;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

public class LdapAuthenticator {

    private static final String NAME_IN_NAMESPACE = "NameInNamespace";

    public static boolean authenticate(String ldapUrl, String baseDN, String bindDN, String bindPass,
            String username, String password) {
        String userNameInNameSpace = getLdapUserNameInNameSpace(ldapUrl, baseDN, bindDN, bindPass, username);
        return authenticate(ldapUrl, password, userNameInNameSpace);
    }

    public static String getLdapUserNameInNameSpace(String ldapUrl, String baseDN, String bindDN, String bindPass,
            String username) {
        try {
            SearchResult result = getSearchResult(ldapUrl, baseDN, bindDN, bindPass, username);
            String nameInNamespace = result.getNameInNamespace();
            if (nameInNamespace == null) {
                throw new IllegalStateException("nameInNamespace property not found for username '" + username + "'");
            }
            return nameInNamespace;
        } catch (Exception exception) {
            exception.printStackTrace();
            throw new IllegalStateException("Error during getting ldap user. " + exception.getMessage(), exception);
        }
    }

    public static Map<String, String> getLdapUserProfile(String ldapUrl, String baseDN, String bindDN, String bindPass,
            String username, String emailProperty, String phoneProperty) {
        try {
            SearchResult result = getSearchResult(ldapUrl, baseDN, bindDN, bindPass, username);
            Attribute emailAttribute = result.getAttributes().get(emailProperty);
            if (emailAttribute == null) {
                throw new IllegalStateException("Email address not found for username '" + username + "'");
            }
            return Map.of(NAME_IN_NAMESPACE, result.getNameInNamespace(),
                    "email", (String) result.getAttributes().get(emailProperty).get(),
                    "phone",
                    result.getAttributes().get(phoneProperty) == null ? ""
                            : (String) result.getAttributes().get(phoneProperty).get(),
                    "Name", (String) result.getAttributes().get("cn").get());
        } catch (Exception exception) {
            exception.printStackTrace();
            throw new IllegalStateException("Error during getting ldap user." + exception.getMessage(), exception);
        }
    }

    private static boolean authenticate(String ldapUrl, String password, String user) {
        InitialDirContext initialDirContext = null;
        try {
            Properties props = new Properties();
            props.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
            props.put(Context.PROVIDER_URL, ldapUrl);
            props.put(Context.SECURITY_PRINCIPAL, user);
            props.put(Context.SECURITY_CREDENTIALS, password);
            initialDirContext = new InitialDirContext(props);
        } catch (Exception exception) {
            exception.printStackTrace();
            return false;
        } finally {
            closeContext(initialDirContext);
        }
        return true;
    }

    private static SearchResult getSearchResult(String ldapUrl, String baseDN, String bindDN, String bindPass,
            String username) {
        InitialDirContext context = null;
        try {
            context = new InitialDirContext(getAdminAuthProperties(ldapUrl, bindDN, bindPass));
            NamingEnumeration<SearchResult> answers =
                    context.search(baseDN, "(uid=" + username + ")", getSearchControls());
            if (!answers.hasMore()) {
                throw new IllegalStateException("User with username " + username + "' not found.");
            }
            return answers.nextElement();
        } catch (Exception exception) {
            exception.printStackTrace();
            throw new IllegalStateException(exception);
        } finally {
            closeContext(context);
        }
    }

    private static void closeContext(InitialDirContext context) {
        if (context != null) {
            try {
                context.close();
            } catch (NamingException namingException) {
                throw new IllegalStateException(namingException);
            }
        }
    }

    private static SearchControls getSearchControls() {
        SearchControls ctrls = new SearchControls();
        ctrls.setReturningAttributes(new String[]{"givenName", "cn", "sn", "memberOf", "mail", "telephoneNumber"});
        ctrls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        return ctrls;
    }

    private static Properties getAdminAuthProperties(String ldapUrl, String bindDN, String bindPass) {
        Properties props = new Properties();
        props.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        props.put(Context.PROVIDER_URL, ldapUrl);
        props.put(Context.SECURITY_PRINCIPAL, bindDN);//adminuser - User with special priviledge, dn user
        props.put(Context.SECURITY_CREDENTIALS, bindPass);//dn user password
        return props;
    }

}
