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
package org.tinymediamanager.ui.movies.actions;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import javax.swing.AbstractAction;
import javax.swing.JOptionPane;

import org.tinymediamanager.core.movie.MovieSearchAndScrapeOptions;
import org.tinymediamanager.core.movie.entities.Movie;
import org.tinymediamanager.core.movie.tasks.MovieScrapeTask;
import org.tinymediamanager.core.threading.TmmTaskManager;
import org.tinymediamanager.core.threading.TmmThreadPool;
import org.tinymediamanager.ui.IconManager;
import org.tinymediamanager.ui.MainWindow;
import org.tinymediamanager.ui.UTF8Control;
import org.tinymediamanager.ui.movies.MovieUIModule;
import org.tinymediamanager.ui.movies.dialogs.MovieScrapeMetadataDialog;

/**
 * The MovieSelectedScrapeAction - scrape seletected movie - force best match
 * 
 * @author Manuel Laggner
 */
public class MovieSelectedScrapeAction extends AbstractAction {
  private static final long           serialVersionUID = -6006932819900795735L;
  private static final ResourceBundle BUNDLE           = ResourceBundle.getBundle("messages", new UTF8Control()); //$NON-NLS-1$

  public MovieSelectedScrapeAction() {
    putValue(NAME, BUNDLE.getString("movie.scrape.selected.force")); //$NON-NLS-1$
    putValue(SHORT_DESCRIPTION, BUNDLE.getString("movie.scrape.selected.force.desc")); //$NON-NLS-1$
    putValue(SMALL_ICON, IconManager.SEARCH);
    putValue(LARGE_ICON_KEY, IconManager.SEARCH);
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    List<Movie> selectedMovies = new ArrayList<Movie>(MovieUIModule.getInstance().getSelectionModel().getSelectedMovies());

    if (selectedMovies.size() > 0) {
      MovieScrapeMetadataDialog dialog = new MovieScrapeMetadataDialog(BUNDLE.getString("movie.scrape.selected.force")); //$NON-NLS-1$
      dialog.setLocationRelativeTo(MainWindow.getActiveInstance());
      dialog.setVisible(true);
      // get options from dialog
      MovieSearchAndScrapeOptions options = dialog.getMovieSearchAndScrapeConfig();
      // do we want to scrape?
      if (dialog.shouldStartScrape()) {
        // scrape
        TmmThreadPool scrapeTask = new MovieScrapeTask(selectedMovies, true, options);
        if (TmmTaskManager.getInstance().addMainTask(scrapeTask)) {
          JOptionPane.showMessageDialog(null, BUNDLE.getString("onlyoneoperation")); //$NON-NLS-1$
        }
      }
    }
  }
}
