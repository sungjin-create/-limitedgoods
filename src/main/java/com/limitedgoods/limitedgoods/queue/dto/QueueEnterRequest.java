package com.limitedgoods.limitedgoods.queue.dto;

import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;


public record QueueEnterRequest(
        @Positive
        long productId
) {

}
