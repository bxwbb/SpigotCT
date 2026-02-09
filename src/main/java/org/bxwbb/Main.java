package org.bxwbb;

import com.formdev.flatlaf.FlatDarculaLaf;
import org.bxwbb.MiniWindow.StartPage;
import org.bxwbb.Util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowStateListener;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        setup();
        pageInit();
    }

    public static void setup() {
        FileUtil.setLang("zh_cn");
    }

    public static void pageInit() {
        FlatDarculaLaf.setup();
        JFrame jFrame = new JFrame(FileUtil.getLang("window.title") + " -BY BXWBB bilibili:1814140675 QQ:3754934636");
        jFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jFrame.setSize(1600,1000);
        jFrame.setLocation(150, 20);

        // 创建顶部菜单
        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = new JMenu(FileUtil.getLang("window.mainMenu.file"));
        JMenuItem fileMenu_Setting = new JMenuItem(FileUtil.getLang("window.mainMenu.file.setting"));
        fileMenu.add(fileMenu_Setting);
        JMenuItem fileMenu_Quit = new JMenuItem(FileUtil.getLang("window.mainMenu.file.quit"));
        fileMenu_Quit.addActionListener(e -> System.exit(0));
        fileMenu.add(fileMenu_Quit);

        menuBar.add(fileMenu);

        jFrame.setLayout(new BorderLayout());
        JPanel jPanel = new JPanel();
        BoxLayout boxLayout = new BoxLayout(jPanel, BoxLayout.X_AXIS);
        jPanel.setBorder(BorderFactory.createEmptyBorder(Setting.windowGap, Setting.windowGap, Setting.windowGap, Setting.windowGap));
        jPanel.setLayout(boxLayout);
        jPanel.add(new StartPage());
        jFrame.add(jPanel, BorderLayout.CENTER);
        jFrame.setJMenuBar(menuBar);
        jFrame.setVisible(true);

        jFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                log.info("正在关闭程序...");
                log.info("关闭文件输入输出线程池...");
                FileUtil.shutdown();
                log.info("关闭文件输入输出线程池...完成");
            }
        });

    }

}