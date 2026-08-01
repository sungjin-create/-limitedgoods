package com.limitedgoods.limitedgoods.queue.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "queue.admission")
public class QueueAdmissionProperties {

    /**
     * 동일한 한정 상품 주문 경로에서 대기열 유무만 비교하기 위한 부하 테스트 옵션이다.
     * 운영 환경에서는 반드시 false로 유지한다.
     */
    private boolean bypassEnabled = false;

    @Min(1)
    private int activeWindow = 40;

    public boolean isBypassEnabled() {
        return bypassEnabled;
    }

    public void setBypassEnabled(boolean bypassEnabled) {
        this.bypassEnabled = bypassEnabled;
    }

    public int getActiveWindow() {
        return activeWindow;
    }

    public void setActiveWindow(int activeWindow) {
        this.activeWindow = activeWindow;
    }
}
