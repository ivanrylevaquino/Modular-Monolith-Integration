package edu.cit.aquino.supplier;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

@JacksonXmlRootElement(localName = "AuthResponse")
@JsonIgnoreProperties(ignoreUnknown = true)
class AuthResponseXml {
    @JacksonXmlProperty(localName = "SessionToken")
    private String sessionToken;

    @JacksonXmlProperty(localName = "IssuedAt")
    private String issuedAt;

    AuthResponseXml() {}

    public String getSessionToken() { return sessionToken; }
    public void setSessionToken(String sessionToken) { this.sessionToken = sessionToken; }
    public String getIssuedAt() { return issuedAt; }
    public void setIssuedAt(String issuedAt) { this.issuedAt = issuedAt; }
}
