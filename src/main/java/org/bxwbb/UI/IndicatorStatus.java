package org.bxwbb.UI;

import java.awt.*;

public enum IndicatorStatus {
    IDLE(Color.GRAY, "闲置"),
    RUNNING(new Color(76, 175, 80), "运行中"),
    WAITING_SELF(new Color(33, 150, 243), "等待自身"),
    WARNING(new Color(255, 193, 7), "警告"),
    ERROR(new Color(244, 67, 54), "错误"),
    WAITING_OTHER(new Color(156, 39, 176), "等待其他进程"),
    PAUSED(new Color(120, 120, 120), "暂停中"); // 新增暂停状态

    private final Color color;
    private final String desc;

    IndicatorStatus(Color color, String desc) {
        this.color = color;
        this.desc = desc;
    }

    public Color getColor() {
        return color;
    }

    public String getDesc() {
        return desc;
    }
}