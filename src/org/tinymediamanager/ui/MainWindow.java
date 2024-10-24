/*
 * Copyright 2012 - 2015 Manuel Laggner
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tinymediamanager.ui;

import ch.swingfx.twinkle.NotificationBuilder;
import ch.swingfx.twinkle.window.Positions;
import com.jgoodies.forms.factories.FormFactory;
import com.jgoodies.forms.layout.ColumnSpec;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.RowSpec;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.Globals;
import org.tinymediamanager.core.Message;
import org.tinymediamanager.core.Message.MessageLevel;
import org.tinymediamanager.core.MessageManager;
import org.tinymediamanager.core.TmmModuleManager;
import org.tinymediamanager.core.UpdaterTask;
import org.tinymediamanager.core.Utils;
import org.tinymediamanager.core.WolDevice;
import org.tinymediamanager.core.threading.TmmTaskManager;
import org.tinymediamanager.ui.actions.AboutAction;
import org.tinymediamanager.ui.actions.BugReportAction;
import org.tinymediamanager.ui.actions.ClearDatabaseAction;
import org.tinymediamanager.ui.actions.ClearImageCacheAction;
import org.tinymediamanager.ui.actions.ClearUrlCacheAction;
import org.tinymediamanager.ui.actions.DonateAction;
import org.tinymediamanager.ui.actions.ExitAction;
import org.tinymediamanager.ui.actions.FaqAction;
import org.tinymediamanager.ui.actions.FeedbackAction;
import org.tinymediamanager.ui.actions.ForumAction;
import org.tinymediamanager.ui.actions.RebuildImageCacheAction;
import org.tinymediamanager.ui.actions.RegisterDonatorVersionAction;
import org.tinymediamanager.ui.actions.SettingsAction;
import org.tinymediamanager.ui.components.LightBoxPanel;
import org.tinymediamanager.ui.components.StatusBar;
import org.tinymediamanager.ui.components.TextFieldPopupMenu;
import org.tinymediamanager.ui.components.VerticalTextIcon;
import org.tinymediamanager.ui.dialogs.LogDialog;
import org.tinymediamanager.ui.dialogs.MessageHistoryDialog;
import org.tinymediamanager.ui.dialogs.UpdateDialog;
import org.tinymediamanager.ui.movies.MoviePanel;
import org.tinymediamanager.ui.moviesets.MovieSetPanel;
import org.tinymediamanager.ui.tvshows.TvShowPanel;

import javax.swing.*;
import javax.swing.SwingWorker.StateValue;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.ResourceBundle;

/**
 * The Class MainWindow.
 * 
 * @author Manuel Laggner
 */
public class MainWindow extends JFrame {
  /** @wbp.nls.resourceBundle messages */
  private static final ResourceBundle BUNDLE           = ResourceBundle.getBundle("messages", new UTF8Control());                   //$NON-NLS-1$
  private final static Logger         LOGGER           = LoggerFactory.getLogger(MainWindow.class);
  private static final long           serialVersionUID = 1L;

  public final static Image           LOGO             = Toolkit.getDefaultToolkit().getImage(
                                                           MainWindow.class.getResource("/org/tinymediamanager/ui/images/tmm.png"));

  /** The action about. */
  private final Action                actionAbout      = new AboutAction();

  /** The action feedback. */
  private final Action                actionFeedback   = new FeedbackAction();

  /** The action bug report. */
  private final Action                actionBugReport  = new BugReportAction();

  /** The action donate. */
  private final Action                actionDonate     = new DonateAction();

  /** The instance. */
  private static MainWindow           instance;

  /** The panel movies. */
  private JPanel                      panelMovies;
  private JPanel                      panelMovieSets;
  private JPanel                      panelTvShows;

  /** The panel status bar. */
  private JPanel                      panelStatusBar;

  /** The lbl loading img. */
  private JLabel                      lblLoadingImg;

  /** The status task. */
  // private StatusbarThread statusTask;
  private List<String>                messagesList;

  private LightBoxPanel               lightBoxPanel;

  private JDialog                     settingsDialog;

  /**
   * Create the application.
   * 
   * @param name
   *          the name
   */
  public MainWindow(String name) {
    super(name);
    setName("mainWindow");
    setMinimumSize(new Dimension(1000, 700));

    instance = this;
    lightBoxPanel = new LightBoxPanel();

    JMenuBar menuBar = new JMenuBar();
    setJMenuBar(menuBar);

    JMenu mnTmm = new JMenu("tinyMediaManager");
    mnTmm.setMnemonic(KeyEvent.VK_T);
    menuBar.add(mnTmm);

    if (!Globals.isDonator()) {
      mnTmm.add(new RegisterDonatorVersionAction());
    }
    mnTmm.add(new SettingsAction());
    mnTmm.addSeparator();
    mnTmm.add(new ExitAction());
    initialize();

    // tools menu
    JMenu tools = new JMenu(BUNDLE.getString("tmm.tools")); //$NON-NLS-1$
    tools.setMnemonic(KeyEvent.VK_O);
    tools.add(new ClearDatabaseAction());

    JMenu cache = new JMenu(BUNDLE.getString("tmm.cache")); //$NON-NLS-1$
    cache.setMnemonic(KeyEvent.VK_C);
    tools.add(cache);

    JMenuItem clearUrlCache = new JMenuItem(new ClearUrlCacheAction());
    clearUrlCache.setMnemonic(KeyEvent.VK_U);
    cache.add(clearUrlCache);
    cache.addSeparator();
    JMenuItem clearImageCache = new JMenuItem(new ClearImageCacheAction());
    clearImageCache.setMnemonic(KeyEvent.VK_I);
    cache.add(clearImageCache);

    JMenuItem rebuildImageCache = new JMenuItem(new RebuildImageCacheAction());
    rebuildImageCache.setMnemonic(KeyEvent.VK_R);
    cache.add(rebuildImageCache);

    JMenuItem tmmFolder = new JMenuItem(BUNDLE.getString("tmm.gotoinstalldir")); //$NON-NLS-1$
    tmmFolder.setMnemonic(KeyEvent.VK_I);
    tools.add(tmmFolder);
    tmmFolder.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent arg0) {
        File path = new File(System.getProperty("user.dir"));
        try {
          // check whether this location exists
          if (path.exists()) {
            TmmUIHelper.openFile(path);
          }
        }
        catch (Exception ex) {
          LOGGER.error("open filemanager", ex);
          MessageManager.instance.pushMessage(new Message(MessageLevel.ERROR, path, "message.erroropenfolder", new String[] { ":",
              ex.getLocalizedMessage() }));
        }
      }
    });

    JMenuItem tmmLogs = new JMenuItem(BUNDLE.getString("tmm.errorlogs")); //$NON-NLS-1$
    tmmLogs.setMnemonic(KeyEvent.VK_L);
    tools.add(tmmLogs);
    tmmLogs.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent arg0) {
        JDialog logDialog = new LogDialog();
        logDialog.setLocationRelativeTo(MainWindow.getActiveInstance());
        logDialog.setVisible(true);
      }
    });

    JMenuItem tmmMessages = new JMenuItem(BUNDLE.getString("tmm.messages")); //$NON-NLS-1$
    tmmMessages.setMnemonic(KeyEvent.VK_L);
    tools.add(tmmMessages);
    tmmMessages.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent arg0) {
        JDialog messageDialog = new MessageHistoryDialog();
        messageDialog.setLocationRelativeTo(MainWindow.getActiveInstance());
        messageDialog.setVisible(true);
      }
    });

    tools.addSeparator();
    final JMenu menuWakeOnLan = new JMenu(BUNDLE.getString("tmm.wakeonlan")); //$NON-NLS-1$
    menuWakeOnLan.setMnemonic(KeyEvent.VK_W);
    menuWakeOnLan.addMenuListener(new MenuListener() {
      @Override
      public void menuCanceled(MenuEvent arg0) {
      }

      @Override
      public void menuDeselected(MenuEvent arg0) {
      }

      @Override
      public void menuSelected(MenuEvent arg0) {
        menuWakeOnLan.removeAll();
        for (final WolDevice device : Globals.settings.getWolDevices()) {
          JMenuItem item = new JMenuItem(device.getName());
          item.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
              Utils.sendWakeOnLanPacket(device.getMacAddress());
            }
          });
          menuWakeOnLan.add(item);
        }
      }
    });
    tools.add(menuWakeOnLan);

    menuBar.add(tools);

    mnTmm = new JMenu(BUNDLE.getString("tmm.contact")); //$NON-NLS-1$
    mnTmm.setMnemonic(KeyEvent.VK_C);
    JMenuItem mntmFeedback = mnTmm.add(actionFeedback);
    mntmFeedback.setMnemonic(KeyEvent.VK_F);

    JMenuItem mntmBugReport = mnTmm.add(actionBugReport);
    mntmBugReport.setText(BUNDLE.getString("BugReport")); //$NON-NLS-1$
    mntmBugReport.setMnemonic(KeyEvent.VK_B);
    menuBar.add(mnTmm);

    mnTmm = new JMenu(BUNDLE.getString("tmm.help")); //$NON-NLS-1$
    mnTmm.setMnemonic(KeyEvent.VK_H);
    menuBar.add(mnTmm);

    JMenuItem mntmFaq = mnTmm.add(new FaqAction());
    mntmFaq.setMnemonic(KeyEvent.VK_F);

    JMenuItem mntmForum = mnTmm.add(new ForumAction());
    mntmForum.setMnemonic(KeyEvent.VK_O);

    mnTmm.addSeparator();
    JMenuItem mntmAbout = mnTmm.add(actionAbout);
    mntmAbout.setMnemonic(KeyEvent.VK_A);

    menuBar.add(Box.createGlue());

    JButton btnDonate = new JButton(actionDonate);
    btnDonate.setBorderPainted(false);
    btnDonate.setFocusPainted(false);
    btnDonate.setContentAreaFilled(false);
    menuBar.add(btnDonate);

    // Globals.executor.execute(new MyStatusbarThread());
    // use a Future to be able to cancel it
    // statusTask.execute();
    checkForUpdate();
  }

  private void checkForUpdate() {
    try {
      final UpdaterTask updateWorker = new UpdaterTask();

      updateWorker.addPropertyChangeListener(new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
          if ("state".equals(evt.getPropertyName()) && evt.getNewValue() == StateValue.DONE) {
            try {
              boolean update = updateWorker.get();
              LOGGER.debug("update result was: " + update);
              if (update) {
                // show whatsnewdialog with the option to update
                if (StringUtils.isNotBlank(updateWorker.getChangelog())) {
                  UpdateDialog dialog = new UpdateDialog(updateWorker.getChangelog());
                  dialog.setVisible(true);
                }
                else {
                  // do the update without changelog popup

                  int answer = JOptionPane.showConfirmDialog(null, BUNDLE.getString("tmm.update.message"), BUNDLE.getString("tmm.update.title"),
                      JOptionPane.YES_NO_OPTION);
                  if (answer == JOptionPane.OK_OPTION) {
                    LOGGER.info("Updating...");

                    // spawn getdown and exit TMM
                    closeTmmAndStart(Utils.getPBforTMMupdate());
                  }
                }
              }
            }
            catch (Exception e) {
              LOGGER.error("Update task failed!" + e.getMessage());
            }
          }
        }
      });

      // update task start a few secs after GUI...
      Timer timer = new Timer(5000, new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          updateWorker.execute();
        }
      });
      timer.setRepeats(false);
      timer.start();
    }
    catch (Exception e) {
      LOGGER.error("Update task failed!" + e.getMessage());
    }
  }

  /**
   * Initialize the contents of the frame.
   */
  private void initialize() {
    // set the logo
    setIconImage(LOGO);
    setBounds(5, 5, 1100, 727);
    // do nothing, we have our own windowClosing() listener
    // setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

    getContentPane().setLayout(
        new FormLayout(new ColumnSpec[] { ColumnSpec.decode("default:grow"), ColumnSpec.decode("1dlu"), }, new RowSpec[] {
            FormFactory.RELATED_GAP_ROWSPEC, RowSpec.decode("fill:max(500px;default):grow"), FormFactory.NARROW_LINE_GAP_ROWSPEC,
            FormFactory.DEFAULT_ROWSPEC, }));

    JLayeredPane content = new JLayeredPane();
    content.setLayout(new FormLayout(new ColumnSpec[] { ColumnSpec.decode("default:grow"), FormFactory.RELATED_GAP_COLSPEC,
        ColumnSpec.decode("right:270px"), }, new RowSpec[] { RowSpec.decode("fill:max(500px;default):grow"), }));
    getContentPane().add(content, "1, 2, fill, fill");

    JPanel mainPanel = new JPanel();
    mainPanel.setLayout(new FormLayout(new ColumnSpec[] { ColumnSpec.decode("default:grow") }, new RowSpec[] { RowSpec
        .decode("fill:max(500px;default):grow") }));
    content.add(mainPanel, "1, 1, 3, 1, fill, fill");
    content.setLayer(mainPanel, 1);

    JTabbedPane tabbedPane = VerticalTextIcon.createTabbedPane(JTabbedPane.LEFT);
    tabbedPane.setTabPlacement(JTabbedPane.LEFT);
    mainPanel.add(tabbedPane, "1, 1, fill, fill");
    // getContentPane().add(tabbedPane, "1, 2, fill, fill");

    panelStatusBar = new StatusBar();
    getContentPane().add(panelStatusBar, "1, 4");

    panelMovies = new MoviePanel();
    VerticalTextIcon.addTab(tabbedPane, BUNDLE.getString("tmm.movies"), panelMovies); //$NON-NLS-1$

    panelMovieSets = new MovieSetPanel();
    VerticalTextIcon.addTab(tabbedPane, BUNDLE.getString("tmm.moviesets"), panelMovieSets); //$NON-NLS-1$

    panelTvShows = new TvShowPanel();
    VerticalTextIcon.addTab(tabbedPane, BUNDLE.getString("tmm.tvshows"), panelTvShows); //$NON-NLS-1$

    // shutdown listener - to clean database connections safely
    addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        closeTmm();
      }
    });
    MessageManager.instance.addListener(new UIMessageListener());
    MessageManager.instance.addListener(TmmUIMessageCollector.instance);

    // mouse event listener for context menu
    Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
      @Override
      public void eventDispatched(AWTEvent arg0) {
        if (arg0 instanceof MouseEvent && MouseEvent.MOUSE_RELEASED == arg0.getID() && arg0.getSource() instanceof JTextComponent) {
          MouseEvent me = (MouseEvent) arg0;
          JTextComponent tc = (JTextComponent) arg0.getSource();
          if (me.isPopupTrigger() && tc.getComponentPopupMenu() == null) {
            TextFieldPopupMenu.buildCutCopyPaste().show(tc, me.getX(), me.getY());
          }
        }
      }
    }, AWTEvent.MOUSE_EVENT_MASK);

    // temp info for users using Java 6
    if (SystemUtils.IS_JAVA_1_6){
      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          JOptionPane.showMessageDialog(MainWindow.this, BUNDLE.getString("tmm.java6")); //$NON-NLS-1$
        }
      });
    }
  }

  public void closeTmm() {
    closeTmmAndStart(null);
  }

  public void closeTmmAndStart(ProcessBuilder pb) {
    int confirm = JOptionPane.YES_OPTION;
    // if there are some threads running, display exit confirmation
    if (TmmTaskManager.getInstance().poolRunning()) {
      confirm = JOptionPane.showOptionDialog(null, BUNDLE.getString("tmm.exit.runningtasks"), BUNDLE.getString("tmm.exit.confirmation"),
          JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, null, null); //$NON-NLS-1$
    }
    if (confirm == JOptionPane.YES_OPTION) {
      LOGGER.info("bye bye");
      try {
        Utils.trackEvent("shutdown");
        // send shutdown signal
        TmmTaskManager.getInstance().shutdown();
        // save unsaved settings
        Globals.settings.saveSettings();
        // hard kill
        TmmTaskManager.getInstance().shutdownNow();
        // close database connection
        TmmModuleManager.getInstance().shutDown();
        // clear cache directory
        if (Globals.settings.isClearCacheShutdown()) {
          File cache = new File("cache" + File.separator + "url");
          if (cache.exists()) {
            FileUtils.deleteDirectory(cache);
          }
        }
      }
      catch (Exception ex) {
        LOGGER.warn("", ex);
      }
      dispose();

      // spawn our process
      if (pb != null) {
        try {
          LOGGER.info("Going to execute: " + pb.command());
          pb.start();
        }
        catch (IOException e) {
          LOGGER.error("Cannot spawn process:", e);
        }
      }

      System.exit(0); // calling the method is a must
    }
  }

  /**
   * Gets the active instance.
   * 
   * @return the active instance
   */
  public static MainWindow getActiveInstance() {
    return instance;
  }

  /**
   * Gets the movie panel.
   * 
   * @return the movie panel
   */
  public MoviePanel getMoviePanel() {
    return (MoviePanel) panelMovies;
  }

  public MovieSetPanel getMovieSetPanel() {
    return (MovieSetPanel) panelMovieSets;
  }

  public TvShowPanel getTvShowPanel() {
    return (TvShowPanel) panelTvShows;
  }

  /**
   * Gets the frame.
   * 
   * @return the frame
   */
  public static JFrame getFrame() {
    return instance;
  }

  public void addMessage(String title, String message) {
    if (Globals.settings.isShowNotifications()) {
      NotificationBuilder builder = new NotificationBuilder().withMessage(message).withTitle(title).withStyle(new TmmNotificationStyle())
          .withPosition(Positions.SOUTH_EAST);
      builder.showNotification();
    }
  }

  public void addMessage(MessageLevel level, String title, String message) {
    if (Globals.settings.isShowNotifications()) {
      NotificationBuilder builder = new NotificationBuilder().withMessage(message).withTitle(title).withStyle(new TmmNotificationStyle())
          .withPosition(Positions.SOUTH_EAST).withIcon(IconManager.ERROR);
      builder.showNotification();
    }

    if (messagesList != null) {
      messagesList.add(message + ": " + title);
    }
  }

  public void createLightbox(String pathToFile, String urlToFile) {
    lightBoxPanel.setImageLocation(pathToFile, urlToFile);
    lightBoxPanel.showLightBox(instance);
  }
}
