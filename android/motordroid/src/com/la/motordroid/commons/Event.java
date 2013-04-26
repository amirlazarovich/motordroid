package com.la.motordroid.commons;

/**
* @author Amir Lazarovich
*/
public enum Event {
    CONTROL("control"),
    FUNCTION("function"),
    KEEP_ALIVE("keep_alive"),
    UNKNOWN("");

    private String mValue;

    Event(String value) {
        mValue = value;
    }

    public static Event getByValue(String value) {
        Event event = UNKNOWN;
        for (Event candidate : values()) {
            if (candidate.mValue.equalsIgnoreCase(value)) {
                event = candidate;
                break;
            }
        }

        return event;
    }


    public String getValue() {
        return mValue;
    }
}
