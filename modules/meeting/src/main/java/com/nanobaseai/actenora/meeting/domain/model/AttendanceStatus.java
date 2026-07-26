package com.nanobaseai.actenora.meeting.domain.model;

public enum AttendanceStatus {
    INVITED,
    ACCEPTED,
    DECLINED,
    TENTATIVE,
    JOINED,
    LEFT,
    /** Present on the invite list but not found in the Teams attendance report. */
    ABSENT
}
