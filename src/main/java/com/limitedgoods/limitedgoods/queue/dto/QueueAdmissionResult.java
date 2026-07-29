package com.limitedgoods.limitedgoods.queue.dto;

public record QueueAdmissionResult(
        boolean admitted,
        String admissionToken,
        int position
) {

    public static QueueAdmissionResult admitted(String token) {
        return new QueueAdmissionResult(true, token, 0);
    }

    public static QueueAdmissionResult waiting(int position) {
        return new QueueAdmissionResult(false, null, position);
    }
}
