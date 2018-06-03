package com.orbitz.consul.config;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;

import java.time.Duration;

public class CacheConfig {

    @VisibleForTesting
    static final Duration DEFAULT_WATCH_DURATION = Duration.ofSeconds(10);
    @VisibleForTesting
    static final Duration DEFAULT_BACKOFF_DELAY = Duration.ofSeconds(10);
    @VisibleForTesting
    static final Duration DEFAULT_MIN_DELAY_BETWEEN_REQUESTS = Duration.ZERO;
    @VisibleForTesting
    static final boolean DEFAULT_TIMEOUT_AUTO_ADJUSTMENT_ENABLED = true;
    @VisibleForTesting
    static final Duration DEFAULT_TIMEOUT_AUTO_ADJUSTMENT_MARGIN = Duration.ofSeconds(2);
    @VisibleForTesting
    static final RefreshErrorLogConsumer DEFAULT_REFRESH_ERROR_LOG_CONSUMER = Logger::error;

    private final Duration backOffDelay;
    private final Duration minDelayBetweenRequests;
    private final Duration timeoutAutoAdjustmentMargin;
    private final boolean timeoutAutoAdjustmentEnabled;
    private final RefreshErrorLogConsumer refreshErrorLogConsumer;

    private CacheConfig(Duration backOffDelay, Duration minDelayBetweenRequests,
                        boolean timeoutAutoAdjustmentEnabled, Duration timeoutAutoAdjustmentMargin,
                        RefreshErrorLogConsumer refreshErrorLogConsumer) {
        this.backOffDelay = backOffDelay;
        this.minDelayBetweenRequests = minDelayBetweenRequests;
        this.timeoutAutoAdjustmentEnabled = timeoutAutoAdjustmentEnabled;
        this.timeoutAutoAdjustmentMargin = timeoutAutoAdjustmentMargin;
        this.refreshErrorLogConsumer = refreshErrorLogConsumer;
    }

    /**
     * Gets the default watch duration for caches.
     */
    public Duration getWatchDuration() {
        return DEFAULT_WATCH_DURATION;
    }

    /**
     * Gets the back-off delay used in caches.
     */
    public Duration getBackOffDelay() {
        return backOffDelay;
    }

    /**
     * Is the automatic adjustment of read timeout enabled?
     */
    public boolean isTimeoutAutoAdjustmentEnabled() {
       return timeoutAutoAdjustmentEnabled;
    }

    /**
     * Gets the margin of the read timeout for caches.
     * The margin represents the additional amount of time given to the read timeout, in addition to the wait duration.
     */
    public Duration getTimeoutAutoAdjustmentMargin() {
        return timeoutAutoAdjustmentMargin;
    }

    /**
     * Gets the minimum time between two requests for caches.
     */
    public Duration getMinimumDurationBetweenRequests() {
        return minDelayBetweenRequests;
    }

    /**
     * Gets the function that will be called in case of error.
     */
    public RefreshErrorLogConsumer getRefreshErrorLoggingConsumer() {
        return refreshErrorLogConsumer;
    }

    /**
     * Creates a new {@link CacheConfig.Builder} object.
     *
     * @return A new Consul builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Duration backOffDelay = DEFAULT_BACKOFF_DELAY;
        private Duration minDelayBetweenRequests = DEFAULT_MIN_DELAY_BETWEEN_REQUESTS;
        private Duration timeoutAutoAdjustmentMargin = DEFAULT_TIMEOUT_AUTO_ADJUSTMENT_MARGIN;
        private boolean timeoutAutoAdjustmentEnabled = DEFAULT_TIMEOUT_AUTO_ADJUSTMENT_ENABLED;
        private RefreshErrorLogConsumer refreshErrorLogConsumer = DEFAULT_REFRESH_ERROR_LOG_CONSUMER;

        private Builder() {

        }

        /**
         * Sets the back-off delay used in caches.
         */
        public Builder withBackOffDelay(Duration delay) {
            this.backOffDelay = Preconditions.checkNotNull(delay, "Delay cannot be null");
            return this;
        }

        /**
         * Sets the minimum time between two requests for caches.
         */
        public Builder withMinDelayBetweenRequests(Duration delay) {
            this.minDelayBetweenRequests = Preconditions.checkNotNull(delay, "Delay cannot be null");
            return this;
        }

        /**
         * Enable/Disable the automatic adjustment of read timeout
         */
        public Builder withTimeoutAutoAdjustmentEnabled(boolean enabled) {
            this.timeoutAutoAdjustmentEnabled = enabled;
            return this;
        }

        /**
         * Sets the margin of the read timeout for caches.
         * The margin represents the additional amount of time given to the read timeout, in addition to the wait duration.
         */
        public Builder withTimeoutAutoAdjustmentMargin(Duration margin) {
            this.timeoutAutoAdjustmentMargin = Preconditions.checkNotNull(margin, "Margin cannot be null");
            return this;
        }

        /**
         * Log refresh errors as warning
         */
        public Builder withRefreshErrorLoggedAsWarning() {
            this.refreshErrorLogConsumer = Logger::warn;
            return this;
        }

        /**
         * Log refresh errors as error
         */
        public Builder withRefreshErrorLoggedAsError() {
            this.refreshErrorLogConsumer = Logger::error;
            return this;
        }

        /**
         * Log refresh errors using custom function
         */
        public Builder withRefreshErrorLoggedAs(RefreshErrorLogConsumer fn) {
            this.refreshErrorLogConsumer = fn;
            return this;
        }

        public CacheConfig build() {
            return new CacheConfig(backOffDelay, minDelayBetweenRequests,
                    timeoutAutoAdjustmentEnabled, timeoutAutoAdjustmentMargin,
                    refreshErrorLogConsumer);
        }
    }

    public interface RefreshErrorLogConsumer {
        void accept(Logger logger, String message, Throwable error);
    }
}
