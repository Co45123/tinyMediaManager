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

import org.tinymediamanager.core.movie.entities.Movie;
import org.tinymediamanager.core.threading.TmmTaskManager;
import org.tinymediamanager.ui.UTF8Control;
import org.tinymediamanager.ui.movies.MovieUIModule;

/**
 * The MovieRewriteNfoAction - to rewrite the NFOs from all selected movies
 * 
 * @author Manuel Laggner
 */
public class MovieRewriteNfoAction extends AbstractAction {
  private static final long           serialVersionUID = 2866581962767395824L;
  private static final ResourceBundle BUNDLE           = ResourceBundle.getBundle("messages", new UTF8Control()); //$NON-NLS-1$

  public MovieRewriteNfoAction() {
    putValue(NAME, BUNDLE.getString("movie.rewritenfo")); //$NON-NLS-1$
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
   */
  @Override
  public void actionPerformed(ActionEvent e) {
    final List<Movie> selectedMovies = new ArrayList<Movie>(MovieUIModule.getInstance().getSelectionModel().getSelectedMovies());

    // rewrite selected NFOs
    TmmTaskManager.getInstance().addUnnamedTask(new Runnable() {
      @Override
      public void run() {
        for (Movie movie : selectedMovies) {
          movie.writeNFO();
        }
      }
    });
  }
}
