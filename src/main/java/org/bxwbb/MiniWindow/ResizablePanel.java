package org.bxwbb.MiniWindow;

import org.bxwbb.Setting;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.EventListener;
import java.util.HashSet;
import java.util.Set;

public class ResizablePanel extends JPanel {
    private int resizeBorderWidth = 8;
    private boolean isResizing = false;
    private DragPosition dragStartPos;
    private int startX, startY;
    private int startW, startH;
    private int startPanelX, startPanelY;

    public enum DragPosition {
        BORDER_TOP, BORDER_BOTTOM, BORDER_LEFT, BORDER_RIGHT,
        CORNER_TOP_LEFT, CORNER_TOP_RIGHT, CORNER_BOTTOM_LEFT, CORNER_BOTTOM_RIGHT,
        NONE
    }

    public static class ResizeEvent extends java.util.EventObject {
        private final DragPosition dragPosition;
        private final int dx;
        private final int dy;
        private final int currentW;
        private final int currentH;

        public ResizeEvent(ResizablePanel source, DragPosition dragPosition, int dx, int dy, int currentW, int currentH) {
            super(source);
            this.dragPosition = dragPosition;
            this.dx = dx;
            this.dy = dy;
            this.currentW = currentW;
            this.currentH = currentH;
        }

        public DragPosition getDragPosition() {
            return dragPosition;
        }

        public int getDx() {
            return dx;
        }

        public int getDy() {
            return dy;
        }

        public int getCurrentW() {
            return currentW;
        }

        public int getCurrentH() {
            return currentH;
        }
    }

    public interface ResizeListener extends EventListener {
        default void resizeStarted(ResizeEvent e) {
        }

        default void resizing(ResizeEvent e) {
        }

        default void resizeEnded(ResizeEvent e) {
        }
    }

    private final Set<ResizeListener> resizeListeners = new HashSet<>();

    public ResizablePanel() {
        initMouseListener();
        setCursor(Cursor.getDefaultCursor());
    }

    private void initMouseListener() {
        MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                DragPosition pos = getDragPosition(e.getX(), e.getY());
                if (pos == DragPosition.NONE) return;

                isResizing = true;
                dragStartPos = pos;
                startX = e.getXOnScreen();
                startY = e.getYOnScreen();
                startW = getWidth();
                startH = getHeight();
                startPanelX = getX();
                startPanelY = getY();

                fireResizeStarted(new ResizeEvent(ResizablePanel.this, pos, 0, 0, startW, startH));
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (!isResizing || dragStartPos == DragPosition.NONE) return;

                int dx = e.getXOnScreen() - startX;
                int dy = e.getYOnScreen() - startY;
                fireResizing(new ResizeEvent(ResizablePanel.this, dragStartPos, dx, dy, getWidth(), getHeight()));
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (!isResizing || dragStartPos == DragPosition.NONE) return;

                fireResizeEnded(new ResizeEvent(ResizablePanel.this, dragStartPos,
                        e.getXOnScreen() - startX, e.getYOnScreen() - startY,
                        getWidth(), getHeight()));

                isResizing = false;
                dragStartPos = DragPosition.NONE;
                setCursor(Cursor.getDefaultCursor());
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                if (isResizing) return;
                DragPosition pos = getDragPosition(e.getX(), e.getY());
                setCursor(getCursorByPosition(pos));
            }
        };
        addMouseListener(mouseAdapter);
        addMouseMotionListener(mouseAdapter);
    }

    private DragPosition getDragPosition(int mouseX, int mouseY) {
        int panelW = getWidth();
        int panelH = getHeight();
        int border = resizeBorderWidth;

        boolean isTop = mouseY <= border;
        boolean isBottom = mouseY >= panelH - border;
        boolean isLeft = mouseX <= border;
        boolean isRight = mouseX >= panelW - border;

        if (isTop && isLeft) return DragPosition.CORNER_TOP_LEFT;
        if (isTop && isRight) return DragPosition.CORNER_TOP_RIGHT;
        if (isBottom && isLeft) return DragPosition.CORNER_BOTTOM_LEFT;
        if (isBottom && isRight) return DragPosition.CORNER_BOTTOM_RIGHT;

        if (isTop) return DragPosition.BORDER_TOP;
        if (isBottom) return DragPosition.BORDER_BOTTOM;
        if (isLeft) return DragPosition.BORDER_LEFT;
        if (isRight) return DragPosition.BORDER_RIGHT;

        return DragPosition.NONE;
    }

    public Cursor getCursorByPosition(DragPosition pos) {
        return switch (pos) {
            case CORNER_TOP_LEFT, CORNER_BOTTOM_RIGHT -> Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR);
            case CORNER_TOP_RIGHT, CORNER_BOTTOM_LEFT -> Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR);
            case BORDER_TOP, BORDER_BOTTOM -> Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR);
            case BORDER_LEFT, BORDER_RIGHT -> Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR);
            default -> Cursor.getDefaultCursor();
        };
    }

    private void fireResizeStarted(ResizeEvent e) {
        for (ResizeListener listener : new HashSet<>(resizeListeners)) {
            listener.resizeStarted(e);
        }
    }

    private void fireResizing(ResizeEvent e) {
        for (ResizeListener listener : new HashSet<>(resizeListeners)) {
            listener.resizing(e);
        }
    }

    private void fireResizeEnded(ResizeEvent e) {
        for (ResizeListener listener : new HashSet<>(resizeListeners)) {
            listener.resizeEnded(e);
        }
    }

    // ---------------------- 对外API：注册/移除拖动监听器 ----------------------
    public void addResizeListener(ResizeListener listener) {
        resizeListeners.add(listener);
    }

    public void removeResizeListener(ResizeListener listener) {
        resizeListeners.remove(listener);
    }

    public void setResizeBorderWidth(int width) {
        if (width > 0) {
            this.resizeBorderWidth = width;
        }
    }

    public int getResizeBorderWidth() {
        return resizeBorderWidth;
    }

}
