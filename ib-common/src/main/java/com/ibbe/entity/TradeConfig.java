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
    // New boolean fields for trading criteria
    private boolean useAvgBidVsAvgAsk;
    private boolean useShortVsLongMovAvg;
    private boolean useSumAmtUpVsDown;
    private boolean useTradePriceCloserToAskVsBuy;

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
     * Full constructor with all parameters including criteria fields.
     *
     * @param id the ID
     * @param ups the ups parameter
     * @param downs the downs parameter
     * @param useAvgBidVsAvgAsk whether to use average bid vs average ask
     * @param useShortVsLongMovAvg whether to use short-term vs long-term moving average
     * @param useSumAmtUpVsDown whether to use sum amount up vs down
     * @param useTradePriceCloserToAskVsBuy whether to use trade price closer to ask vs buy
     */
    public TradeConfig(@JsonProperty("id") String id,
                     @JsonProperty("ups") String ups,
                     @JsonProperty("downs") String downs,
                     @JsonProperty("useAvgBidVsAvgAsk") boolean useAvgBidVsAvgAsk,
                     @JsonProperty("useShortVsLongMovAvg") boolean useShortVsLongMovAvg,
                     @JsonProperty("useSumAmtUpVsDown") boolean useSumAmtUpVsDown,
                     @JsonProperty("useTradePriceCloserToAskVsBuy") boolean useTradePriceCloserToAskVsBuy) {
        this.id = id == null ? RandomString.getRandomString() : id;
        this.ups = ups;
        this.downs = downs;
        this.useAvgBidVsAvgAsk = useAvgBidVsAvgAsk;
        this.useShortVsLongMovAvg = useShortVsLongMovAvg;
        this.useSumAmtUpVsDown = useSumAmtUpVsDown;
        this.useTradePriceCloserToAskVsBuy = useTradePriceCloserToAskVsBuy;
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
     * Gets whether to use average bid vs average ask.
     *
     * @return whether to use average bid vs average ask
     */
    public boolean isUseAvgBidVsAvgAsk() {
        return useAvgBidVsAvgAsk;
    }

    /**
     * Sets whether to use average bid vs average ask.
     *
     * @param useAvgBidVsAvgAsk whether to use average bid vs average ask
     */
    public void setUseAvgBidVsAvgAsk(boolean useAvgBidVsAvgAsk) {
        this.useAvgBidVsAvgAsk = useAvgBidVsAvgAsk;
    }

    /**
     * Gets whether to use short-term vs long-term moving average.
     *
     * @return whether to use short-term vs long-term moving average
     */
    public boolean isUseShortVsLongMovAvg() {
        return useShortVsLongMovAvg;
    }

    /**
     * Sets whether to use short-term vs long-term moving average.
     *
     * @param useShortVsLongMovAvg whether to use short-term vs long-term moving average
     */
    public void setUseShortVsLongMovAvg(boolean useShortVsLongMovAvg) {
        this.useShortVsLongMovAvg = useShortVsLongMovAvg;
    }

    /**
     * Gets whether to use sum amount up vs down.
     *
     * @return whether to use sum amount up vs down
     */
    public boolean isUseSumAmtUpVsDown() {
        return useSumAmtUpVsDown;
    }

    /**
     * Sets whether to use sum amount up vs down.
     *
     * @param useSumAmtUpVsDown whether to use sum amount up vs down
     */
    public void setUseSumAmtUpVsDown(boolean useSumAmtUpVsDown) {
        this.useSumAmtUpVsDown = useSumAmtUpVsDown;
    }

    /**
     * Gets whether to use trade price closer to ask vs buy.
     *
     * @return whether to use trade price closer to ask vs buy
     */
    public boolean isUseTradePriceCloserToAskVsBuy() {
        return useTradePriceCloserToAskVsBuy;
    }

    /**
     * Sets whether to use trade price closer to ask vs buy.
     *
     * @param useTradePriceCloserToAskVsBuy whether to use trade price closer to ask vs buy
     */
    public void setUseTradePriceCloserToAskVsBuy(boolean useTradePriceCloserToAskVsBuy) {
        this.useTradePriceCloserToAskVsBuy = useTradePriceCloserToAskVsBuy;
    }

    @Override
    public String toString() {
        return "TradeConfig{" +
            "id='" + id + "'" +
            ", ups='" + ups + "'" +
            ", downs='" + downs + "'" +
            ", useAvgBidVsAvgAsk=" + useAvgBidVsAvgAsk +
            ", useShortVsLongMovAvg=" + useShortVsLongMovAvg +
            ", useSumAmtUpVsDown=" + useSumAmtUpVsDown +
            ", useTradePriceCloserToAskVsBuy=" + useTradePriceCloserToAskVsBuy +
            "}";
    }
}
