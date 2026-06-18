package com.qb.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.fuzzy-match")
public class FuzzyMatchProperties {

    /** Minimum score to show a duplicate suggestion on the parse preview screen. */
    private double suggestThreshold = 0.55;

    /** Minimum score required to link on commit (server-enforced). */
    private double commitLinkThreshold = 0.60;

    /** Stricter threshold when category is General (broader cross-category risk). */
    private double generalCategoryThreshold = 0.65;

    /** Skip fuzzy matching for very short question text. */
    private int minTextLength = 25;

    public double getSuggestThreshold() { return suggestThreshold; }
    public void setSuggestThreshold(double suggestThreshold) { this.suggestThreshold = suggestThreshold; }

    public double getCommitLinkThreshold() { return commitLinkThreshold; }
    public void setCommitLinkThreshold(double commitLinkThreshold) { this.commitLinkThreshold = commitLinkThreshold; }

    public double getGeneralCategoryThreshold() { return generalCategoryThreshold; }
    public void setGeneralCategoryThreshold(double generalCategoryThreshold) {
        this.generalCategoryThreshold = generalCategoryThreshold;
    }

    public int getMinTextLength() { return minTextLength; }
    public void setMinTextLength(int minTextLength) { this.minTextLength = minTextLength; }
}
