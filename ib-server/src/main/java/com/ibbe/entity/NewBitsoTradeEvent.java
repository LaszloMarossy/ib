package com.ibbe.entity;

import org.springframework.context.ApplicationEvent;

public class NewBitsoTradeEvent extends ApplicationEvent {
    private Trade trade;

    public NewBitsoTradeEvent(Object source, Trade trade) {
        super(source);
        this.trade = trade;
    }
    public Trade getTrade() {
        return trade;
    }

}
