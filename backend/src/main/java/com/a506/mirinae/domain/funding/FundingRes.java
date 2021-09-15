package com.a506.mirinae.domain.funding;

import lombok.Getter;

@Getter
public class FundingRes {
    String title;
    String category;
    Long balance;
    Long goal;
    String thumbnail;

    public FundingRes(Funding funding, Long balance) {
        this.title = funding.getTitle();
        this.category = funding.getCategory().getName();
        this.balance = balance;
        this.goal = funding.getGoal();
        this.thumbnail = funding.getThumbnail();
    }
}
