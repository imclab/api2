package eu.europeana.api2.web.security.model;

import java.util.Set;

import org.codehaus.jackson.annotate.JsonIgnore;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.provider.BaseClientDetails;

import eu.europeana.corelib.utils.StringArrayUtils;

/**
 * The client details
 */
public class Api2OAuth2ClientDetails extends BaseClientDetails {
	private static final long serialVersionUID = -5687602758230210358L;

	/**
	 * The grant types for which this client is authorized. 
	 * See http://tools.ietf.org/html/draft-ietf-oauth-v2-31#section-1.3
	 */
	private Set<String> authGrantTypes = StringArrayUtils.toSet("authorization_code", "implicit");

	/**
	 * The scope of this client.
	 */
	private Set<String> scope = StringArrayUtils.toSet("read", "write");

	public Api2OAuth2ClientDetails(String apikey, String secret) {
		super();
		setClientId(apikey);
		setClientSecret(secret);
		setAuthorities(AuthorityUtils
				.commaSeparatedStringToAuthorityList("ROLE_CLIENT"));
	}

	@Override
	public boolean isSecretRequired() {
		return true;
	}

	@Override
	@JsonIgnore
	public Set<String> getAuthorizedGrantTypes() {
		return authGrantTypes;
	}

	@Override
	public Set<String> getScope() {
		return scope;
	}
}
