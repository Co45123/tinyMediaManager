/*
 * Copyright 2012 Manuel Laggner
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
package org.tinymediamanager.core.movie;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.SwingWorker;

import org.apache.log4j.Logger;
import org.tinymediamanager.scraper.IMediaArtworkProvider;
import org.tinymediamanager.scraper.IMediaTrailerProvider;
import org.tinymediamanager.scraper.MediaArtwork;
import org.tinymediamanager.scraper.MediaArtwork.MediaArtworkType;
import org.tinymediamanager.scraper.MediaMetadata;
import org.tinymediamanager.scraper.MediaScrapeOptions;
import org.tinymediamanager.scraper.MediaSearchResult;
import org.tinymediamanager.scraper.MediaTrailer;

// TODO: Auto-generated Javadoc
/**
 * The Class MovieScrapeTask.
 * 
 * @author manuel
 */
public class MovieScrapeTask extends SwingWorker<Object, Object> {

  /** The Constant LOGGER. */
  private final static Logger LOGGER = Logger.getLogger(MovieScrapeTask.class);

  /** The movies to scrape. */
  private List<Movie>         moviesToScrape;

  /** The movie count. */
  private int                 movieCount;

  /** The lbl description. */
  private JLabel              lblDescription;

  /** The progress bar. */
  private JProgressBar        progressBar;

  /** The btn cancel. */
  private JButton             btnCancel;

  /**
   * Instantiates a new movie scrape task.
   * 
   * @param moviesToScrape
   *          the movies to scrape
   * @param label
   *          the label
   * @param progressBar
   *          the progress bar
   * @param button
   *          the button
   */
  public MovieScrapeTask(List<Movie> moviesToScrape, JLabel label, JProgressBar progressBar, JButton button) {
    this.moviesToScrape = moviesToScrape;
    this.movieCount = moviesToScrape.size();
    this.lblDescription = label;
    this.progressBar = progressBar;
    this.btnCancel = button;
  }

  /*
   * (non-Javadoc)
   * 
   * @see javax.swing.SwingWorker#doInBackground()
   */
  @Override
  protected Object doInBackground() throws Exception {
    startProgressBar("scraping movies", 0);

    ExecutorService executor = Executors.newFixedThreadPool(3);

    // start 3 threads
    executor.execute(new Worker(this));
    executor.execute(new Worker(this));
    executor.execute(new Worker(this));

    executor.shutdown();

    // wait till scraping is finished
    while (true) {
      if (executor.isTerminated()) {
        break;
      }
      Thread.sleep(1000);
    }

    return null;
  }

  /**
   * Gets the next movie.
   * 
   * @return the next movie
   */
  private synchronized Movie getNextMovie() {
    // get next movie to scrape
    if (moviesToScrape.size() > 0) {
      Movie movie = moviesToScrape.get(0);
      moviesToScrape.remove(movie);
      startProgressBar("scraping movies", 100 * (movieCount - moviesToScrape.size()) / movieCount);
      return movie;
    }

    return null;
  }

  /**
   * Cancel.
   */
  public void cancel() {
    cancel(false);
    moviesToScrape.clear();
  }

  /*
   * Executed in event dispatching thread
   */
  /*
   * (non-Javadoc)
   * 
   * @see javax.swing.SwingWorker#done()
   */
  @Override
  public void done() {
    stopProgressBar();
  }

  /**
   * Start progress bar.
   * 
   * @param description
   *          the description
   * @param value
   *          the value
   */
  private void startProgressBar(String description, int value) {
    lblDescription.setText(description);
    progressBar.setVisible(true);
    progressBar.setValue(value);
    btnCancel.setVisible(true);
  }

  /**
   * Stop progress bar.
   */
  private void stopProgressBar() {
    lblDescription.setText("");
    progressBar.setVisible(false);
    btnCancel.setVisible(false);
  }

  /**
   * The Class Worker.
   */
  private class Worker implements Runnable {

    private MovieList       movieList;

    /** The scrape task. */
    private MovieScrapeTask scrapeTask;

    /**
     * Instantiates a new worker.
     * 
     * @param scrapeTask
     *          the scrape task
     */
    public Worker(MovieScrapeTask scrapeTask) {
      this.scrapeTask = scrapeTask;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Runnable#run()
     */
    @Override
    public void run() {
      movieList = MovieList.getInstance();
      while (true) {
        Movie movie = scrapeTask.getNextMovie();
        if (movie == null) {
          break;
        }

        // scrape movie
        List<MediaSearchResult> results = movieList.searchMovie(movie.getName(), movie.getImdbId(), null);
        if (results != null && !results.isEmpty()) {
          MediaSearchResult result1 = results.get(0);
          // check if there is an other result with 100% score
          if (results.size() > 1) {
            MediaSearchResult result2 = results.get(1);
            // if both results have 100% score - do not take any result
            if (result1.getScore() == 1 && result2.getScore() == 1) {
              continue;
            }
          }
          // get metadata, artwork and trailers
          try {
            MediaScrapeOptions options = new MediaScrapeOptions();
            options.setResult(result1);
            MediaMetadata md = movieList.getMetadataProvider().getMetadata(options);
            movie.setMetadata(md);
            movie.setArtwork(getArtwork(md));
            movie.setTrailers(getTrailers(md));
          }
          catch (Exception e) {
            LOGGER.error("movie.setMetadata", e);
          }
        }
      }
    }

    /**
     * Gets the artwork.
     * 
     * @return the artwork
     */
    public List<MediaArtwork> getArtwork(MediaMetadata metadata) {
      List<MediaArtwork> artwork = null;

      MediaScrapeOptions options = new MediaScrapeOptions();
      options.setArtworkType(MediaArtworkType.ALL);
      options.setMetadata(metadata);
      options.setImdbId(metadata.getImdbId());
      options.setTmdbId(metadata.getTmdbId());

      // scrape providers till one artwork has been found
      for (IMediaArtworkProvider artworkProvider : movieList.getArtworkProviders()) {
        try {
          artwork = artworkProvider.getArtwork(options);
        }
        catch (Exception e) {
          artwork = new ArrayList<MediaArtwork>();
        }
        // check if at least one artwork has been found
        if (artwork.size() > 0) {
          break;
        }
      }

      // initialize if null
      if (artwork == null) {
        artwork = new ArrayList<MediaArtwork>();
      }

      return artwork;
    }

    /**
     * Gets the trailers.
     * 
     * @return the trailers
     */
    private List<MediaTrailer> getTrailers(MediaMetadata metadata) {
      List<MediaTrailer> trailers = new ArrayList<MediaTrailer>();

      MediaScrapeOptions options = new MediaScrapeOptions();
      options.setMetadata(metadata);
      options.setImdbId(metadata.getImdbId());
      options.setTmdbId(metadata.getTmdbId());

      // scrape trailers
      for (IMediaTrailerProvider trailerProvider : movieList.getTrailerProviders()) {
        try {
          List<MediaTrailer> foundTrailers = trailerProvider.getTrailers(options);
          trailers.addAll(foundTrailers);
        }
        catch (Exception e) {
        }
      }

      return trailers;
    }
  }
}