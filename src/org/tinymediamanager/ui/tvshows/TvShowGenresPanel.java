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
package org.tinymediamanager.ui.tvshows;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.tvshow.entities.TvShow;
import org.tinymediamanager.scraper.MediaGenres;

/**
 * The Class TvShowGenresPanel.
 * 
 * @author Manuel Laggner
 */
public class TvShowGenresPanel extends JPanel {

  /** The Constant serialVersionUID. */
  private static final long    serialVersionUID = -7111036144770559630L;

  /** The Constant LOGGER. */
  private static final Logger  LOGGER           = LoggerFactory.getLogger(TvShowGenresPanel.class);

  /** The model. */
  private TvShowSelectionModel tvShowSelectionModel;

  /**
   * Instantiates a new tv show genres panel.
   * 
   * @param model
   *          the model
   */
  public TvShowGenresPanel(TvShowSelectionModel model) {
    this.tvShowSelectionModel = model;
    setOpaque(false);

    // install the propertychangelistener
    PropertyChangeListener propertyChangeListener = new PropertyChangeListener() {
      public void propertyChange(PropertyChangeEvent propertyChangeEvent) {
        String property = propertyChangeEvent.getPropertyName();
        Object source = propertyChangeEvent.getSource();

        // react on selection of a tv show or change of genres
        if ((source.getClass() == TvShowSelectionModel.class && "selectedTvShow".equals(property))
            || (source.getClass() == TvShow.class && "genre".equals(property))) {
          buildImages();
        }
      }
    };

    tvShowSelectionModel.addPropertyChangeListener(propertyChangeListener);
  }

  /**
   * Builds the images.
   */
  private void buildImages() {
    removeAll();
    List<MediaGenres> genres = tvShowSelectionModel.getSelectedTvShow().getGenres();
    for (MediaGenres genre : genres) {
      try {
        StringBuilder sb = new StringBuilder("/images/genres/");
        sb.append(genre.name().toLowerCase());
        sb.append(".png");
        Icon image = new ImageIcon(TvShowGenresPanel.class.getResource(sb.toString()));
        JLabel lblImage = new JLabel(image);
        add(lblImage);
      }
      catch (NullPointerException e) {
        LOGGER.warn("genre image for genre " + genre.name() + " not available");
      }
      catch (Exception e) {
        LOGGER.warn(e.getMessage());
      }
    }
    // add unknown if there is no genre
    if (genres.isEmpty()) {
      try {
        Icon image = new ImageIcon(TvShowGenresPanel.class.getResource("/images/genres/unknown.png"));
        JLabel lblImage = new JLabel(image);
        add(lblImage);
      }
      catch (Exception e) {
        LOGGER.warn(e.getMessage());
      }
    }
  }
}
