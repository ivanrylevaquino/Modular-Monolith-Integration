package edu.cit.aquino.supplier;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

@JacksonXmlRootElement(localName = "AuthRequest")
class AuthRequestXml {
    @JacksonXmlProperty(localName = "ClientId")
    private String clientId;

    @JacksonXmlProperty(localName = "ApiKey")
    private String apiKey;

    AuthRequestXml() {}

    AuthRequestXml(String clientId, String apiKey) {
        this.clientId = clientId;
        this.apiKey = apiKey;
    }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
}
