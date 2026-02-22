package org.bxwbb.UI;

import org.bxwbb.Util.FileUtil;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

public class PinButton extends JButton {

    private Window pinWindow;

    public PinButton(Window pinWindow) {
        super();
        this.pinWindow = pinWindow;
        this.setIcon(FileUtil.getImageIconToPath(Objects.requireNonNull(getClass().getResource("/SpigotCT/icon/Pin.png"))));
        this.addActionListener(e -> {
            if (!pinWindow.isAlwaysOnTop()) {
                pinWindow.setAlwaysOnTop(true);
                this.setIcon(FileUtil.getImageIconToPath(Objects.requireNonNull(getClass().getResource("/SpigotCT/icon/PinOn.png"))));
            } else {
                pinWindow.setAlwaysOnTop(false);
                this.setIcon(FileUtil.getImageIconToPath(Objects.requireNonNull(getClass().getResource("/SpigotCT/icon/Pin.png"))));
            }
        });
    }

    @Override
    public void update(Graphics g) {
        if (pinWindow.isAlwaysOnTop()) {
            this.setIcon(FileUtil.getImageIconToPath(Objects.requireNonNull(getClass().getResource("/SpigotCT/icon/PinOn.png"))));
        } else {
            this.setIcon(FileUtil.getImageIconToPath(Objects.requireNonNull(getClass().getResource("/SpigotCT/icon/Pin.png"))));
        }
        super.update(g);
    }

    public Window getPinWindow() {
        return pinWindow;
    }

    public void setPinWindow(Window pinWindow) {
        this.pinWindow = pinWindow;
    }
}
