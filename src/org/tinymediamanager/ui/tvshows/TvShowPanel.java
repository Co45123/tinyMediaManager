/*
 * Copyright 2012 - 2013 Manuel Laggner
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
package org.tinymediamanager.ui.tvshows;

import java.awt.CardLayout;
import java.awt.event.ActionEvent;
import java.util.ResourceBundle;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

import org.apache.log4j.Logger;
import org.gpl.JSplitButton.JSplitButton;
import org.gpl.JSplitButton.action.SplitButtonActionListener;
import org.tinymediamanager.core.tvshow.TvShow;
import org.tinymediamanager.core.tvshow.TvShowEpisode;
import org.tinymediamanager.core.tvshow.TvShowList;
import org.tinymediamanager.core.tvshow.TvShowSeason;
import org.tinymediamanager.ui.UTF8Control;
import org.tinymediamanager.ui.tvshows.dialogs.TvShowChooserDialog;

import com.jgoodies.forms.factories.FormFactory;
import com.jgoodies.forms.layout.ColumnSpec;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.RowSpec;

/**
 * @author Manuel Laggner
 * 
 */
public class TvShowPanel extends JPanel {

  /** The Constant BUNDLE. */
  private static final ResourceBundle BUNDLE                  = ResourceBundle.getBundle("messages", new UTF8Control()); //$NON-NLS-1$

  private static final long           serialVersionUID        = -1923811385292825136L;

  /** The logger. */
  private final static Logger         LOGGER                  = Logger.getLogger(TvShowPanel.class);

  private TvShowTreeModel             treeModel;

  private TvShowSelectionModel        tvShowSelectionModel;

  private TvShowEpisodeSelectionModel tvShowEpisodeSelectionModel;

  private TvShowList                  tvShowList              = TvShowList.getInstance();

  private JTree                       tree;
  private JPanel                      panelRight;

  private final Action                actionUpdateDatasources = new UpdateDatasourcesAction(false);
  private final Action                actionScrape            = new SingleScrapeAction(false);

  public TvShowPanel() {
    super();

    treeModel = new TvShowTreeModel(tvShowList.getTvShows());
    tvShowSelectionModel = new TvShowSelectionModel();
    tvShowEpisodeSelectionModel = new TvShowEpisodeSelectionModel();

    setLayout(new FormLayout(
        new ColumnSpec[] { FormFactory.RELATED_GAP_COLSPEC, ColumnSpec.decode("default:grow"), FormFactory.RELATED_GAP_COLSPEC, }, new RowSpec[] {
            FormFactory.RELATED_GAP_ROWSPEC, RowSpec.decode("default:grow"), }));

    JSplitPane splitPane = new JSplitPane();
    splitPane.setContinuousLayout(true);
    add(splitPane, "2, 2, fill, fill");

    JPanel panelTvShowTree = new JPanel();
    splitPane.setLeftComponent(panelTvShowTree);
    panelTvShowTree.setLayout(new FormLayout(new ColumnSpec[] { FormFactory.RELATED_GAP_COLSPEC, ColumnSpec.decode("300px:grow"), }, new RowSpec[] {
        FormFactory.LINE_GAP_ROWSPEC, FormFactory.DEFAULT_ROWSPEC, FormFactory.RELATED_GAP_ROWSPEC, RowSpec.decode("3px:grow"), }));

    JScrollPane scrollPane = new JScrollPane();
    panelTvShowTree.add(scrollPane, "2, 4, fill, fill");

    JToolBar toolBar = new JToolBar();
    toolBar.setRollover(true);
    toolBar.setFloatable(false);
    toolBar.setOpaque(false);
    panelTvShowTree.add(toolBar, "2, 2");

    toolBar.add(actionUpdateDatasources);
    JSplitButton buttonScrape = new JSplitButton(new ImageIcon(getClass().getResource("/org/tinymediamanager/ui/images/Search.png")));
    // temp fix for size of the button
    buttonScrape.setText("   ");
    buttonScrape.setHorizontalAlignment(JButton.LEFT);
    // buttonScrape.setMargin(new Insets(2, 2, 2, 24));
    buttonScrape.setSplitWidth(18);

    // register for listener
    buttonScrape.addSplitButtonActionListener(new SplitButtonActionListener() {
      public void buttonClicked(ActionEvent e) {
        actionScrape.actionPerformed(e);
      }

      public void splitButtonClicked(ActionEvent e) {
      }
    });

    // TODO create dropdown for split button
    // JPopupMenu popup = new JPopupMenu("popup");
    // JMenuItem item = new JMenuItem(actionScrape2);
    // popup.add(item);
    // item = new JMenuItem(actionScrapeUnscraped);
    // popup.add(item);
    // item = new JMenuItem(actionScrapeSelected);
    // popup.add(item);
    // buttonScrape.setPopupMenu(popup);
    toolBar.add(buttonScrape);

    tree = new JTree(treeModel);
    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);
    scrollPane.setViewportView(tree);

    panelRight = new JPanel();
    splitPane.setRightComponent(panelRight);
    panelRight.setLayout(new CardLayout(0, 0));

    JPanel panelTvShow = new TvShowInformationPanel(tvShowSelectionModel);
    panelRight.add(panelTvShow, "tvShow");

    JPanel panelTvShowEpisode = new TvShowEpisodeInformationPanel(tvShowEpisodeSelectionModel);
    panelRight.add(panelTvShowEpisode, "tvShowEpisode");

    tree.addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
        if (node != null) {
          // click on a tv show
          if (node.getUserObject() instanceof TvShow) {
            TvShow tvShow = (TvShow) node.getUserObject();
            tvShowSelectionModel.setSelectedTvShow(tvShow);
            CardLayout cl = (CardLayout) (panelRight.getLayout());
            cl.show(panelRight, "tvShow");
          }

          // click on a season
          if (node.getUserObject() instanceof TvShowSeason) {
            TvShowSeason tvShowSeason = (TvShowSeason) node.getUserObject();
            // act as a click on a tv show if a season of an other tv show has been clicked
            if (tvShowSeason.getTvShow() != tvShowSelectionModel.getSelectedTvShow()) {
              tvShowSelectionModel.setSelectedTvShow(tvShowSeason.getTvShow());
              CardLayout cl = (CardLayout) (panelRight.getLayout());
              cl.show(panelRight, "tvShow");
            }
          }

          // click on an episode
          if (node.getUserObject() instanceof TvShowEpisode) {
            TvShowEpisode tvShowEpisode = (TvShowEpisode) node.getUserObject();
            tvShowEpisodeSelectionModel.setSelectedTvShowEpisode(tvShowEpisode);
            CardLayout cl = (CardLayout) (panelRight.getLayout());
            cl.show(panelRight, "tvShowEpisode");
          }
        }
        else {
          tvShowSelectionModel.setSelectedTvShow(null);
        }
      }
    });
  }

  private class UpdateDatasourcesAction extends AbstractAction {
    private static final long serialVersionUID = 5704371143505653741L;

    public UpdateDatasourcesAction(boolean withTitle) {
      if (withTitle) {
        putValue(NAME, BUNDLE.getString("tvshow.update.datasource")); //$NON-NLS-1$
        putValue(LARGE_ICON_KEY, "");
      }
      else {
        putValue(LARGE_ICON_KEY, new ImageIcon(getClass().getResource("/org/tinymediamanager/ui/images/Folder-Sync.png")));
      }
    }

    public void actionPerformed(ActionEvent e) {
      tvShowList.udpateDatasources();
    }
  }

  /**
   * The Class SingleScrapeAction.
   * 
   * @author Manuel Laggner
   */
  private class SingleScrapeAction extends AbstractAction {

    private static final long serialVersionUID = 641704453374845709L;

    /**
     * Instantiates a new SingleScrapeAction.
     * 
     * @param withTitle
     *          the with title
     */
    public SingleScrapeAction(boolean withTitle) {
      if (withTitle) {
        putValue(NAME, BUNDLE.getString("movie.scrape.selected")); //$NON-NLS-1$
        putValue(LARGE_ICON_KEY, "");
      }
      else {
        putValue(LARGE_ICON_KEY, new ImageIcon(getClass().getResource("/org/tinymediamanager/ui/images/Search.png")));
        putValue(SHORT_DESCRIPTION, BUNDLE.getString("movie.scrape.selected.desc")); //$NON-NLS-1$
      }
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
     */
    public void actionPerformed(ActionEvent e) {
      TreePath[] paths = tree.getSelectionPaths();
      // tree.clearSelection();

      // filter out all movie sets from the selection
      if (paths != null) {
        for (TreePath path : paths) {
          if (path.getPathCount() > 1) {

            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            if (node.getUserObject() instanceof TvShow) {
              TvShow tvShow = (TvShow) node.getUserObject();

              // display tv show chooser
              TvShowChooserDialog chooser = new TvShowChooserDialog(tvShow, paths.length > 1 ? true : false);
              chooser.setVisible(true);
            }
          }
        }
      }
    }
  }
}