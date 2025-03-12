package com.ibbe.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ibbe.util.RandomString;

/**
 * Configuration for trading operations.
 */
public class TradeConfig {
    private String id;
    private String ups;
    private String downs;
    private String bitsoApiKey;
    private String bitsoApiSecret;

    /**
     * Default constructor.
     */
    public TradeConfig() {
        id = RandomString.getRandomString();
    }

    /**
     * Constructor with ups and downs parameters.
     *
     * @param upsIn the ups parameter
     * @param downsIn the downs parameter
     */
    public TradeConfig(String upsIn, String downsIn) {
        id = RandomString.getRandomString();
        if (upsIn != null)
            this.ups = upsIn;
        if (downsIn != null)
            this.downs = downsIn;
    }

    /**
     * Constructor with all parameters.
     *
     * @param id the ID
     * @param ups the ups parameter
     * @param downs the downs parameter
     */
    public TradeConfig(@JsonProperty("id") String id,
                     @JsonProperty("ups") String ups,
                     @JsonProperty("downs") String downs) {
        this.id = id;
        this.ups = ups;
        this.downs = downs;
    }

    /**
     * Constructor with API key and secret.
     *
     * @param bitsoApiKey the Bitso API key
     * @param bitsoApiSecret the Bitso API secret
     * @param isApiConfig flag indicating this is an API configuration
     */
    public TradeConfig(String bitsoApiKey, String bitsoApiSecret, boolean isApiConfig) {
        this.id = RandomString.getRandomString();
        this.bitsoApiKey = bitsoApiKey;
        this.bitsoApiSecret = bitsoApiSecret;
    }

    /**
     * Gets the ID.
     *
     * @return the ID
     */
    public String getId() {
        return id;
    }

    /**
     * Gets the ups parameter.
     *
     * @return the ups parameter
     */
    public String getUps() {
        return ups;
    }

    /**
     * Gets the downs parameter.
     *
     * @return the downs parameter
     */
    public String getDowns() {
        return downs;
    }

    /**
     * Gets the Bitso API key.
     *
     * @return the Bitso API key
     */
    public String getBitsoApiKey() {
        return bitsoApiKey;
    }

    /**
     * Sets the Bitso API key.
     *
     * @param bitsoApiKey the Bitso API key
     */
    public void setBitsoApiKey(String bitsoApiKey) {
        this.bitsoApiKey = bitsoApiKey;
    }

    /**
     * Gets the Bitso API secret.
     *
     * @return the Bitso API secret
     */
    public String getBitsoApiSecret() {
        return bitsoApiSecret;
    }

    /**
     * Sets the Bitso API secret.
     *
     * @param bitsoApiSecret the Bitso API secret
     */
    public void setBitsoApiSecret(String bitsoApiSecret) {
        this.bitsoApiSecret = bitsoApiSecret;
    }


    @Override
    public String toString() {
        return "TradeConfig{" +
            "id='" + id + "'" +
            ", ups='" + ups + "'" +
            ", downs='" + downs + "'" +
            "}";
    }
}
