package com.picsforyou.cloud.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TokenResponse {

    @JsonProperty("access_token") private String accessToken;
    @JsonProperty("token_type") private String tokenType;
    @JsonProperty("expires_in") private int expiresIn;
    private String scope;
    private String jti;

    public TokenResponse() {}

    public String getAccessToken() { return accessToken; }
    public String getTokenType() { return tokenType; }
    public int getExpiresIn() { return expiresIn; }
    public String getScope() { return scope; }
    public String getJti() { return jti; }

    public String bearerValue() {
        return tokenType + " " + accessToken;
    }

    @Override
    public String toString() {
        return "TokenResponse{scope='" + scope + "', expiresIn=" + expiresIn + "}";
    }
}
